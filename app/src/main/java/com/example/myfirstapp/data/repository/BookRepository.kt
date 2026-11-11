package com.example.myfirstapp.data.repository

import com.example.myfirstapp.data.local.AppDatabase
import com.example.myfirstapp.data.local.BookSourceEntity
import com.example.myfirstapp.data.local.ChapterCacheEntity
import com.example.myfirstapp.data.local.ChapterIndexEntity
import com.example.myfirstapp.data.local.ShelfBookEntity
import com.example.myfirstapp.data.web.HttpFetcher
import com.example.myfirstapp.data.web.SourceDiscoverer
import com.example.myfirstapp.domain.BookChapter
import com.example.myfirstapp.domain.BookMatchRanking
import com.example.myfirstapp.domain.BookResultFilter
import com.example.myfirstapp.domain.BookRules
import com.example.myfirstapp.domain.BookRulesCodec
import com.example.myfirstapp.domain.BookSearchResult
import com.example.myfirstapp.domain.ChapterContent
import com.example.myfirstapp.domain.HtmlRules
import com.example.myfirstapp.domain.ParsedLocalBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

/** 本地导入书籍的地址前缀（不是网址，只是用来区分「不需要联网的书」） */
const val LOCAL_BOOK_PREFIX = "local://"

class BookRepository(private val database: AppDatabase) {

    private val io = Dispatchers.IO

    fun observeSources(): Flow<List<BookSourceEntity>> = database.bookSourceDao().observeSources()

    fun observeShelf(): Flow<List<ShelfBookEntity>> = database.shelfBookDao().observeBooks()

    suspend fun ensureDefaultSources() = withContext(io) {
        // 内置书源的规则由 App 维护：缺失就补，规则有更新就刷新（保留用户自己开关的状态）
        val existing = database.bookSourceDao().listSources().associateBy { it.id }
        val toWrite = mutableListOf<BookSourceEntity>()
        var rulesChanged = false
        DefaultBookSources.sources().forEach { def ->
            val old = existing[def.id]
            when {
                old == null -> toWrite.add(def)
                old.rules != def.rules || old.searchUrl != def.searchUrl || old.baseUrl != def.baseUrl -> {
                    toWrite.add(def.copy(enabled = old.enabled, sortOrder = old.sortOrder))
                    rulesChanged = true
                }
            }
        }
        if (toWrite.isNotEmpty()) {
            database.bookSourceDao().upsertAll(toWrite)
        }
        // 规则升级后旧目录不可信（例如古腾堡以前整本只解析出 1 章），清掉让它们重新抓
        if (rulesChanged) {
            database.chapterIndexDao().clearAll()
        }
    }

    /**
     * 在所有启用书源里并发搜索，失败的书源自动跳过。
     */
    suspend fun search(query: String, sourceLimit: Int = 20): List<BookSearchResult> =
        withContext(io) {
            val keyword = query.trim()
            if (keyword.isEmpty()) return@withContext emptyList()
            val sources = database.bookSourceDao().listSources()
                .filter { it.enabled }
                .take(sourceLimit)
            if (sources.isEmpty()) return@withContext emptyList()

            supervisorScope {
                val tasks = sources.map { source ->
                    async {
                        // 挂代理或网络慢时单个书源可能需要十几秒，超时只跳过该书源
                        withTimeoutOrNull(25_000) {
                            runCatching { searchInSource(source, keyword) }
                                .onFailure {
                                    android.util.Log.w(
                                        "BookRepository",
                                        "书源 ${source.name} 搜索失败：${it.message}"
                                    )
                                }
                                .onSuccess {
                                    android.util.Log.d(
                                        "BookRepository",
                                        "书源 ${source.name} 返回 ${it.size} 条结果"
                                    )
                                }
                                .getOrDefault(emptyList())
                        } ?: run {
                            android.util.Log.w("BookRepository", "书源 ${source.name} 搜索超时")
                            emptyList()
                        }
                    }
                }
                tasks.map { it.await() }
                    .flatten()
                    // 规则过宽的书源会把站点导航栏当书返回，先统一挡掉
                    .filterNot { BookResultFilter.isNavigation(it.title, it.bookUrl) }
                    .distinctBy { it.dedupeKey }
                    // 只按 title|author 去重挡不住「同一书源多个导航项指向同一个 /rank/」，
                    // 必须再按 源+URL 去重，否则 LazyColumn 的 key 重复会直接闪退
                    .distinctBy { it.sourceId + "|" + it.bookUrl }
                    // 各源结果是「一段一段」拼起来的，按和关键词的匹配度整体重排：
                    // 同名的书永远排最前，其次是关键词打头的
                    .let { BookMatchRanking.rank(it, keyword) }
                    // 书源规则失效时返回的是整页无关内容，只留和关键词沾边的结果
                    .let { BookResultFilter.keepRelevant(it, keyword) }
            }
        }

    private suspend fun searchInSource(source: BookSourceEntity, keyword: String): List<BookSearchResult> {
        val rules = BookRulesCodec.fromJson(source.rules) ?: return emptyList()
        if (rules.search.list.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(keyword, source.charset)
        val html = fetchSearchHtml(source, keyword, encoded)
        val doc = HtmlRules.parse(html, source.baseUrl)
        val items = HtmlRules.selectList(doc, rules.search.list)
        val results = mutableListOf<BookSearchResult>()
        items.forEach { element ->
            val title = HtmlRules.firstText(element, rules.search.name, source.baseUrl).trim()
            if (title.isEmpty()) return@forEach
            val bookUrl = HtmlRules.firstText(element, rules.search.url, source.baseUrl)
            if (bookUrl.isEmpty()) return@forEach
            if (BookResultFilter.isNavigation(title, bookUrl)) return@forEach
            results.add(
                BookSearchResult(
                    sourceId = source.id,
                    sourceName = source.name,
                    title = title,
                    author = HtmlRules.firstText(element, rules.search.author, source.baseUrl),
                    coverUrl = HtmlRules.firstText(element, rules.search.cover, source.baseUrl),
                    summary = HtmlRules.firstText(element, rules.search.summary, source.baseUrl)
                        .take(120),
                    bookUrl = bookUrl
                )
            )
        }
        return results
    }

    /**
     * 按书源的 searchUrl 拉搜索结果页。
     * 支持两种写法（自动发现功能会产出第二种）：
     *  - GET：`https://x/search?q={{key}}` —— 把 {{key}} 替成编码后的关键词即可。
     *  - POST：`post::https://x/search||q={{key}}` —— `post::` 之后、第一个 `||` 之前是地址，
     *    之后是表单字段（{{key}} 替成**原始**关键词，由 fetcher 负责编码）。
     */
    private suspend fun fetchSearchHtml(
        source: BookSourceEntity,
        keyword: String,
        encoded: String
    ): String = withContext(io) {
        val raw = source.searchUrl
        if (raw.startsWith("post::")) {
            val rest = raw.removePrefix("post::")
            val sep = rest.indexOf("||")
            val url = if (sep >= 0) rest.substring(0, sep) else rest
            val body = if (sep >= 0) rest.substring(sep + 2) else ""
            val form = body.split("&").mapNotNull { seg ->
                val eq = seg.indexOf("=")
                if (eq < 0) null else seg.substring(0, eq) to
                    seg.substring(eq + 1).replace("{{key}}", keyword)
            }.toMap()
            return@withContext HttpFetcher.post(url.trim(), form, source.charset)
        }
        val url = raw.replace("{{key}}", encoded).replace("{key}", encoded)
        return@withContext HttpFetcher.get(url, source.charset)
    }

    /**
     * 书源自动发现：给一个站点首页网址，手机端（有真实网络、能跑 POST/JS 站点）尝试
     * 逆推出搜索 / 目录 / 正文规则，返回可直接导入的书源 JSON。
     * 这是「离线批处理生成规则」行不通之后的替代方案——把推断放到真机上跑。
     */
    suspend fun discoverSource(inputUrl: String, keyword: String): SourceDiscoverer.DiscoveredSource =
        withContext(io) {
            SourceDiscoverer.discover(inputUrl.trim(), keyword)
        }

    /** 目录：优先用缓存，forceRefresh 时重新抓 */
    suspend fun chapters(
        sourceId: String,
        bookUrl: String,
        forceRefresh: Boolean = false
    ): List<BookChapter> = withContext(io) {
        // 本地导入的书目录就在库里，不存在「联网重抓」这件事
        if (!forceRefresh || bookUrl.startsWith(LOCAL_BOOK_PREFIX)) {
            val cached = database.chapterIndexDao().listChapters(bookUrl)
            // 旧版本兜底可能把「书详情页自身」当成唯一一章缓存，升级后自动失效重抓
            if (cached.isNotEmpty() && !cachedIsBookPage(cached, bookUrl)) {
                return@withContext cached.map { BookChapter(it.position, it.title, it.url) }
            }
        }
        val source = database.bookSourceDao().getSource(sourceId) ?: return@withContext emptyList()
        val rules = BookRulesCodec.fromJson(source.rules) ?: return@withContext emptyList()
        val toc = rules.toc
        // toc.list 为空不再直接返回空目录：下面还有按章节特征的通用兜底

        // 有些书源的书页只是个「详情页」，目录在另一个页面里（古腾堡的 HTML 版就是：
        // 书页上只有一个「在线阅读」按钮，真正的章节是 HTML 版里的 #chap01 这类锚点）。
        var pageUrl = bookUrl
        // 书页抓取失败（站点打不开 / 超时 / 规则失效）时直接返回空目录，交由上层给出友好提示，
        // 不能把异常抛出去——上层 viewModelScope.launch 没兜住会直接闪退。
        var doc = try {
            HtmlRules.parse(HttpFetcher.get(bookUrl, source.charset), bookUrl)
        } catch (e: Exception) {
            android.util.Log.w("BookRepository", "目录页抓取失败 ${source.name}: ${e.message}")
            return@withContext emptyList()
        }
        if (toc.pageUrl.isNotBlank()) {
            val entry = HtmlRules.firstText(doc, toc.pageUrl, bookUrl)
            if (entry.isNotBlank() && entry != bookUrl) {
                pageUrl = entry
                // 二级目录页抓不到时退回用第一页继续解析，而不是把异常抛出去
                doc = try {
                    HtmlRules.parse(HttpFetcher.get(entry, source.charset), entry)
                } catch (e: Exception) {
                    android.util.Log.w("BookRepository", "目录二级页抓取失败 ${source.name}: ${e.message}")
                    doc
                }
            }
        }

        val elements = if (toc.list.isBlank()) emptyList() else HtmlRules.selectList(doc, toc.list)
        val filter = toc.contains
        val chapters = mutableListOf<BookChapter>()
        elements.forEach { element ->
            val title = HtmlRules.firstText(element, toc.name, pageUrl).trim()
            // 链接相对「当前这一页」解析，页内锚点才会正确变成 页面地址#锚点
            val url = HtmlRules.firstText(element, toc.url, pageUrl)
            if (title.isEmpty() || url.isEmpty()) return@forEach
            if (filter.isNotBlank() && !url.contains(filter)) return@forEach
            if (chapters.any { it.url == url }) return@forEach // 同页重复链接只保留一条
            chapters.add(BookChapter(chapters.size, title, url))
        }
        if (chapters.isEmpty()) {
            // 书源目录规则没配或已失效：从页面所有链接里按章节特征兜底抽取。
            // 「能搜到书却读不了」多半就是卡在这一步。
            var targetDoc = doc
            var targetUrl = pageUrl
            // 书详情页常常只给一个「点击阅读目录列表」入口，先跟进目录页再抽章节
            val directoryLink = HtmlRules.findDirectoryLinks(doc, pageUrl)
                .firstOrNull { it != bookUrl && it != pageUrl }
            if (directoryLink != null) {
                runCatching {
                    HtmlRules.parse(HttpFetcher.get(directoryLink, source.charset), directoryLink)
                }.getOrNull()?.let {
                    targetDoc = it
                    targetUrl = directoryLink
                }
            }
            HtmlRules.fallbackChapters(targetDoc, targetUrl).forEach { (title, url) ->
                if (chapters.none { it.url == url }) {
                    chapters.add(BookChapter(chapters.size, title, url))
                }
            }
            if (chapters.isNotEmpty()) {
                android.util.Log.d(
                    "BookRepository",
                    "目录规则失效，兜底抽到 ${chapters.size} 章，首章=${chapters.first().url}"
                )
            }
        }
        if (chapters.isNotEmpty()) {
            database.chapterIndexDao().clear(bookUrl)
            database.chapterIndexDao().upsertAll(
                chapters.map {
                    ChapterIndexEntity(bookUrl = bookUrl, position = it.position, title = it.title, url = it.url)
                }
            )
        }
        chapters
    }

    /** 正文：优先读缓存，没有再去抓，抓到后存起来以便离线重复阅读 */
    suspend fun chapterContent(
        sourceId: String,
        bookUrl: String,
        chapter: BookChapter,
        forceRefresh: Boolean = false
    ): ChapterContent = withContext(io) {
        val cached = database.chapterCacheDao().get(chapter.url)
        // 旧版本曾把 301 / nginx 错误页当正文缓存过，升级后要让它自动失效重抓
        if (!forceRefresh && cached != null && !isErrorPageCached(cached.content)) {
            return@withContext ChapterContent(cached.title, cached.url, cached.content, true)
        }
        // 本地导入的书正文只存在本机，没有可以重抓的网址
        if (chapter.url.startsWith(LOCAL_BOOK_PREFIX) || bookUrl.startsWith(LOCAL_BOOK_PREFIX)) {
            if (cached != null) {
                return@withContext ChapterContent(cached.title, cached.url, cached.content, true)
            }
            throw IllegalStateException("本地章节内容缺失，请删除后重新导入这本书")
        }
        val source = database.bookSourceDao().getSource(sourceId) ?: throw IllegalStateException("书源不存在")
        val rules = BookRulesCodec.fromJson(source.rules) ?: throw IllegalStateException("书源规则无法解析")

        // 章节地址带 #锚点时，说明整本书在一个页面里：抓这一页，但只取锚点对应的那一章
        val hash = chapter.url.indexOf('#')
        val baseUrl = if (hash >= 0) chapter.url.substring(0, hash) else chapter.url
        val anchorId = if (hash >= 0) chapter.url.substring(hash + 1) else null

        val html = HttpFetcher.get(baseUrl, source.charset)
        val doc = HtmlRules.parse(html, baseUrl)
        android.util.Log.d(
            "BookRepository",
            "正文页 $baseUrl html=${html.length}字 可见文本=${doc.text().length}字"
        )
        val content = if (anchorId.isNullOrBlank()) {
            HtmlRules.extractContent(doc, rules.content)
        } else {
            val siblings = database.chapterIndexDao().listChapters(bookUrl)
                .map { it.url.substringAfter('#', "") }
                .filter { it.isNotBlank() }
                .toSet()
            HtmlRules.extractContent(doc, rules.content, anchorId, siblings)
        }
        if (content.isBlank()) throw IllegalStateException(
            "源站返回的页面里没有正文（页面约 ${html.length} 字节），" +
                "多半是站点反爬、需要浏览器验证，或书源规则已失效"
        )
        database.chapterCacheDao().upsert(
            ChapterCacheEntity(
                url = chapter.url,
                bookUrl = bookUrl,
                title = chapter.title,
                content = content,
                savedAtMillis = System.currentTimeMillis()
            )
        )
        ChapterContent(chapter.title, chapter.url, content, false)
    }

    /** 缓存里只有 1~2 章、且指向书页自身，说明是旧版兜底写进去的脏数据 */
    private fun cachedIsBookPage(cached: List<ChapterIndexEntity>, bookUrl: String): Boolean {
        if (cached.size > 2) return false
        val self = bookUrl.substringBefore('#').trimEnd('/')
        return cached.any { it.url.substringBefore('#').trimEnd('/') == self }
    }

    /** 识别被误缓存的错误页（正常正文不会这么短，也不会出现 nginx 的字样） */
    private fun isErrorPageCached(content: String): Boolean {
        if (content.length > 4000) return false
        val plain = content.lowercase()
        return listOf(
            "moved permanently", "nginx", "access denied", "forbidden",
            "bad gateway", "service unavailable"
        ).any { plain.contains(it) }
    }

    suspend fun clearOldCache(olderThanMillis: Long = System.currentTimeMillis() - 7 * 24 * 3600_000L) =
        withContext(io) { database.chapterCacheDao().deleteOlderThan(olderThanMillis) }

    suspend fun addToShelf(result: BookSearchResult): Long = withContext(io) {
        val existing = database.shelfBookDao().getByUrl(result.bookUrl)
        if (existing != null) return@withContext existing.id
        database.shelfBookDao().upsert(
            ShelfBookEntity(
                title = result.title,
                author = result.author.takeIf { it.isNotBlank() },
                coverUrl = result.coverUrl.takeIf { it.isNotBlank() },
                summary = result.summary.takeIf { it.isNotBlank() },
                sourceId = result.sourceId,
                sourceName = result.sourceName,
                bookUrl = result.bookUrl
            )
        )
    }

    suspend fun book(id: Long): ShelfBookEntity? = withContext(io) { database.shelfBookDao().getBook(id) }

    suspend fun removeFromShelf(book: ShelfBookEntity) = withContext(io) {
        database.shelfBookDao().delete(book)
        // 本地导入的书，目录和正文只服务于这一本书，移出书架时一起删掉
        if (book.bookUrl.startsWith(LOCAL_BOOK_PREFIX)) {
            database.chapterIndexDao().clear(book.bookUrl)
            database.chapterCacheDao().clearBook(book.bookUrl)
        }
    }

    /**
     * 本地 TXT / EPUB 导入：把整本书的目录和正文直接写进本地库。
     * 之后目录页、阅读页、进度记忆走的都是和联网书完全相同的流程。
     */
    suspend fun importLocalBook(parsed: ParsedLocalBook): Long = withContext(io) {
        require(parsed.chapters.isNotEmpty()) { "这本书没有解析到正文" }
        val bookUrl = LOCAL_BOOK_PREFIX + java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val shelfId = database.shelfBookDao().upsert(
            ShelfBookEntity(
                title = parsed.title,
                author = parsed.author,
                summary = "本地 ${parsed.format} 导入 · 共 ${parsed.chapters.size} 章",
                sourceId = "local",
                sourceName = "本地文件",
                bookUrl = bookUrl
            )
        )
        database.chapterIndexDao().upsertAll(
            parsed.chapters.mapIndexed { index, chapter ->
                ChapterIndexEntity(
                    bookUrl = bookUrl,
                    position = index,
                    title = chapter.title,
                    url = "$bookUrl#$index"
                )
            }
        )
        database.chapterCacheDao().upsertAll(
            parsed.chapters.mapIndexed { index, chapter ->
                ChapterCacheEntity(
                    url = "$bookUrl#$index",
                    bookUrl = bookUrl,
                    title = chapter.title,
                    content = chapter.content,
                    savedAtMillis = now
                )
            }
        )
        shelfId
    }

    suspend fun updateProgress(bookId: Long, index: Int, title: String?) = withContext(io) {
        database.shelfBookDao().updateProgress(bookId, index, title, System.currentTimeMillis())
    }

    suspend fun setSourceEnabled(id: String, enabled: Boolean) = withContext(io) {
        database.bookSourceDao().setEnabled(listOf(id), enabled)
    }

    suspend fun deleteSource(id: String) = withContext(io) { database.bookSourceDao().deleteById(id) }

    /** 批量启用 / 停用：配合书源管理器里的分组筛选使用 */
    suspend fun setSourcesEnabled(ids: List<String>, enabled: Boolean) = withContext(io) {
        ids.chunked(400).forEach { database.bookSourceDao().setEnabled(it, enabled) }
    }

    suspend fun deleteSources(ids: List<String>) = withContext(io) {
        ids.chunked(400).forEach { database.bookSourceDao().deleteByIds(it) }
    }

    suspend fun resetDefaultSources() = withContext(io) {
        database.bookSourceDao().upsertAll(DefaultBookSources.sources())
    }

    /**
     * 导入书源。支持两种格式：
     * 1. 单个对象：{ name, baseUrl, searchUrl, charset, rules:{...} }
     * 2. 数组（可以一次导入几十上百个）
     */
    suspend fun importSources(text: String): Int = withContext(io) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext 0
        var imported = 0
        val array = runCatching { JSONArray(trimmed) }.getOrNull()
        if (array != null) {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (importOne(item)) imported++
            }
        } else {
            val single = runCatching { JSONObject(trimmed) }.getOrNull() ?: return@withContext 0
            if (importOne(single)) imported = 1
        }
        imported
    }

    /** 从一个网址直接拉取书源 JSON（例如书源集合的链接），再导入 */
    suspend fun importFromUrl(url: String): Int = withContext(io) {
        val text = runCatching { HttpFetcher.get(url.trim(), "utf-8") }.getOrNull() ?: return@withContext 0
        importSources(text)
    }

    private suspend fun importOne(json: JSONObject): Boolean {
        val name = json.optString("name").trim()
        val searchUrl = json.optString("searchUrl").trim()
        val baseUrl = json.optString("baseUrl").trim().ifEmpty { hostOf(searchUrl) }
        if (name.isEmpty() || searchUrl.isEmpty()) return false
        val rulesJson = json.opt("rules")?.toString() ?: "{}"
        val parsedRules = BookRulesCodec.fromJson(rulesJson) ?: BookRules()
        val entity = BookSourceEntity(
            id = sourceIdOf(json),
            name = name,
            baseUrl = baseUrl,
            searchUrl = searchUrl,
            charset = json.optString("charset").takeIf { it.isNotBlank() } ?: "utf-8",
            rules = BookRulesCodec.toJson(parsedRules),
            enabled = json.optBoolean("enabled", true),
            builtIn = false,
            sortOrder = json.optInt("sortOrder", 100),
            groupName = json.optString("group").trim()
        )
        database.bookSourceDao().upsert(entity)
        return true
    }

    /** 书源的稳定 id：JSON 里显式给了就用，否则用「名字 + 搜索地址」的哈希 */
    private fun sourceIdOf(json: JSONObject): String {
        val explicit = json.optString("id").trim()
        if (explicit.isNotEmpty()) return explicit
        val name = json.optString("name").trim()
        val searchUrl = json.optString("searchUrl").trim()
        return name + "_" + searchUrl.hashCode()
    }

    /**
     * 导入 App 内置的推荐书源包（assets/book_sources.json）。
     *
     * 这些源是从社区书源配置里筛出来的：只保留不依赖 JavaScript、能用关键词搜索的站点。
     * 默认**停用**——一次启用几百个源只会让搜索变慢、结果变杂，
     * 用户在书源管理里按分组启用自己常用的那几个就好。
     *
     * 两个例外（都是为了避免「导入一次，把用户之前的设置冲掉」）：
     *  - 已经存在的源：保留用户原来的启用状态，只更新规则；
     *  - JSON 里显式写了 `"enabled"` 的源（目前只有「热门」分组）：听 JSON 的。
     */
    suspend fun importBundledSources(context: android.content.Context, enabled: Boolean = false): Int =
        withContext(io) {
            val text = runCatching {
                context.assets.open("book_sources.json").bufferedReader().use { it.readText() }
            }.getOrNull() ?: return@withContext 0
            val array = runCatching { JSONArray(text) }.getOrNull() ?: return@withContext 0
            val existing = database.bookSourceDao().listSources().associateBy { it.id }
            var imported = 0
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val old = existing[sourceIdOf(item)]
                when {
                    old != null -> item.put("enabled", old.enabled)
                    !enabled && !item.has("enabled") -> item.put("enabled", false)
                }
                if (importOne(item)) imported++
            }
            imported
        }

    private fun hostOf(url: String): String = runCatching {
        val uri = java.net.URI(url)
        "${uri.scheme}://${uri.authority}"
    }.getOrDefault(url)

    /**
     * 书源自检结果：在手机（真实网络）上对单个源跑一次搜索，判断它到底能不能用。
     * - OK：能搜到书（status="ok"）
     * - FAIL：站点能打开，但搜不到书（多半规则失效 / 换个词才行）—— status="fail"
     * - TIMEOUT：连不上（超时 / 报错）—— status="timeout"
     */
    data class SourceTestResult(val status: String, val count: Int, val message: String) {
        companion object {
            const val OK = "ok"
            const val FAIL = "fail"
            const val TIMEOUT = "timeout"
        }
    }

    /** 在手机上对单个书源做一次自检，返回结果（不落库，由调用方决定要不要保存） */
    suspend fun testSource(entity: BookSourceEntity): SourceTestResult = withContext(io) {
        val keywords = listOf("斗破苍穹", "完美世界", "红楼梦", "诡秘之主", "三体")
        var bestCount = 0
        var reachable = false
        for (kw in keywords) {
            val result = withTimeoutOrNull(15_000) {
                runCatching { searchInSource(entity, kw) }.getOrNull()
            }
            if (result != null) {
                reachable = true
                if (result.size > bestCount) bestCount = result.size
                if (bestCount > 0) break
            }
        }
        val status = when {
            bestCount > 0 -> SourceTestResult.OK
            reachable -> SourceTestResult.FAIL
            else -> SourceTestResult.TIMEOUT
        }
        val message = when (status) {
            SourceTestResult.OK -> "可搜到书（${bestCount} 条）"
            SourceTestResult.FAIL -> "能打开但搜不到书"
            else -> "连不上"
        }
        SourceTestResult(status, bestCount, message)
    }

    /** 把自检结果写回数据库（书源列表会实时刷新出状态） */
    suspend fun saveSourceStatus(id: String, status: String?, testedAt: Long) = withContext(io) {
        database.bookSourceDao().updateStatus(id, status, testedAt)
    }

    /**
     * 批量自检：对给定 id 的书源逐个测试，最多 8 个并发；可随时取消。
     * 每测完一个就把结果写库，因此界面上的状态会一点点亮起来。
     */
    suspend fun runSourceTest(
        ids: List<String>,
        cancel: () -> Boolean,
        onProgress: (done: Int, total: Int) -> Unit
    ) = withContext(io) {
        val all = database.bookSourceDao().listSources().associateBy { it.id }
        val targets = ids.mapNotNull { all[it] }
        val total = targets.size
        if (total == 0) return@withContext
        val done = AtomicInteger(0)
        val semaphore = Semaphore(8)
        kotlinx.coroutines.coroutineScope {
            targets.forEach { source ->
                if (cancel()) return@coroutineScope
                semaphore.acquire()
                launch {
                    try {
                        val r = testSource(source)
                        database.bookSourceDao()
                            .updateStatus(source.id, r.status, System.currentTimeMillis())
                    } finally {
                        semaphore.release()
                        onProgress(done.incrementAndGet(), total)
                    }
                }
            }
        }
    }

    /** 统计各状态的书源数量，用于自检结束后给用户一个结论 */
    data class SourceStatusCounts(
        val ok: Int = 0,
        val fail: Int = 0,
        val timeout: Int = 0,
        val untested: Int = 0
    )

    suspend fun countStatuses(): SourceStatusCounts = withContext(io) {
        val list = database.bookSourceDao().listSources()
        var ok = 0
        var fail = 0
        var timeout = 0
        var untested = 0
        list.forEach {
            when (it.lastStatus) {
                SourceTestResult.OK -> ok++
                SourceTestResult.FAIL -> fail++
                SourceTestResult.TIMEOUT -> timeout++
                else -> untested++
            }
        }
        SourceStatusCounts(ok, fail, timeout, untested)
    }
}
