package com.example.myfirstapp.data.repository

import androidx.room.withTransaction
import com.example.myfirstapp.data.local.AppDatabase
import com.example.myfirstapp.data.local.BookSourceEntity
import com.example.myfirstapp.data.local.ChapterCacheEntity
import com.example.myfirstapp.data.local.ChapterIndexEntity
import com.example.myfirstapp.data.local.ShelfBookEntity
import com.example.myfirstapp.data.web.HttpFetcher
import com.example.myfirstapp.data.web.JsSandbox
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

/** 本地导入书籍的地址前缀（不是网址，只是用来区分「不需要联网的书」） */
const val LOCAL_BOOK_PREFIX = "local://"

class BookRepository(
    private val database: AppDatabase,
    private val appContext: android.content.Context? = null
) {

    private val io = Dispatchers.IO

    /** 解析书源自定义请求头（Legado 的 header JSON 字符串），失败返回空 */
    private fun sourceHeaders(source: BookSourceEntity): Map<String, String> {
        val raw = source.header?.trim().orEmpty()
        if (raw.isEmpty()) return emptyMap()
        val obj = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        obj.keys().forEach { k ->
            // 去掉换行，否则 HttpURLConnection.setRequestProperty 会抛 IllegalArgumentException
            val v = obj.optString(k).replace("\r", "").replace("\n", "").trim()
            if (v.isNotBlank()) out[k] = v
        }
        return out
    }

    fun observeSources(): Flow<List<BookSourceEntity>> = database.bookSourceDao().observeSources()

    fun observeShelf(): Flow<List<ShelfBookEntity>> = database.shelfBookDao().observeBooks()

    suspend fun ensureDefaultSources() = withContext(io) {
        // 用户要求「只用笔趣阁的书源」：只保留 id 前缀 legado_ 的 1744 条内置源，
        // 清掉本地导入的其它源（旧 3 条内置、56书库等推荐包）。每次启动执行，代价极小。
        database.bookSourceDao().deleteNotByPrefix("legado_", 7)
        database.bookSourceDao().markAllBuiltIn()
    }

    /**
     * 在所有启用书源里并发搜索，失败的书源自动跳过。
     *
     * 默认取 60 个源（原来是 20，用户反馈「书源一堆却搜不到」——20 个根本覆盖不到）。
     * 用信号量把并发压到 24，单个源 12s 超时，既铺得开又不会把结果页拖到几十秒。
     */
    suspend fun search(
        query: String,
        sourceLimit: Int = 320,
        windowMs: Long = 14_000,
        onPartial: ((List<BookSearchResult>) -> Unit)? = null
    ): List<BookSearchResult> =
        withContext(io) {
            val keyword = query.trim()
            if (keyword.isEmpty()) return@withContext emptyList()
            // 只在**已启用**的源里搜（现在只启用笔趣阁原本开启的 654 条，跟笔趣阁一致）。
            // 「可用」组（纯 CSS、能真正解析）优先，其余已启用源垫后。
            val enabledSources = database.bookSourceDao().listSources().filter { it.enabled }
            // 不再按「笔趣阁·可用」分组筛源：那个标签是按「规则是否纯 CSS」打的，
            // 把 239 个明明能正常发 GET 的启用源误杀了（笔趣阁是全都搜的）。
            // 改按 searchUrl 形态判断：http + 含 {{key}} + 不含 JS 片段 → 约 445 个源都可搜。
            val searchable = enabledSources.filter { s ->
                val u = s.searchUrl
                u.startsWith("http", true) && u.contains("{{key}}") &&
                    !u.contains("@js") && !u.contains("<js>") && !u.contains("{{java.")
            }
            val sources = searchable.take(sourceLimit)

            fun clean(list: List<BookSearchResult>): List<BookSearchResult> {
                val ranked = list
                    // 规则过宽的书源会把站点导航栏当书返回，先统一挡掉
                    .filterNot { BookResultFilter.isNavigation(it.title, it.bookUrl) }
                    // 只按「源+URL」去重（同一条结果别重复）。
                    // 千万不要按 title|author 去重：那会把同一本书在几十个源里的条目全删成 1 条，
                    // 笔趣阁正是把多源都列出来供挑源，尘剑之前「才搜到几个、还全是坏源」就栽在这里。
                    .distinctBy { it.sourceId + "|" + it.bookUrl }
                    .let { BookMatchRanking.rank(it, keyword) }
                    .let { BookResultFilter.keepRelevant(it, keyword) }
                // 同一本书保留多个源（最多 12 个，避免刷屏），按书名分组展示
                return ranked.groupBy { it.dedupeKey }.values.flatMap { it.take(12) }
            }

            // 不用 supervisorScope 干等全部源（阻塞 IO 取消不掉）：边搜边回调，
            // 第一条结果出来几百毫秒就能显示；并发 64 + 搜索短超时，让 206 个源都能轮到。
            val semaphore = Semaphore(32)
            val channel = Channel<List<BookSearchResult>>(Channel.UNLIMITED)
            val scope = CoroutineScope(io + SupervisorJob())
            // +1 是云端聚合搜索任务；它不用 semaphore（只有一个请求）
            val pending = java.util.concurrent.atomic.AtomicInteger(sources.size + 1)
            val acc = mutableListOf<BookSearchResult>()
            try {
                // 笔趣阁「书城网络」用的阅文官方搜索接口：稳定、秒回、带作者和简介。
                // 2023 年那批书源大量失效，这条云端兜底保证「随便搜都有结果」。
                scope.launch {
                    try {
                        val got = cloudSearch(keyword)
                        if (got.isNotEmpty()) {
                            // 云端结果一返回就立刻显示，别等 20s 的书源搜索窗口结束
                            runCatching { onPartial?.invoke(clean(got)) }
                            channel.trySend(got)
                        }
                    } finally {
                        if (pending.decrementAndGet() == 0) channel.close()
                    }
                }
                sources.forEach { source ->
                    scope.launch {
                        semaphore.acquire()
                        try {
                            val got = runCatching { searchInSource(source, keyword) }
                                .onFailure { if (it is CancellationException) throw it }
                                .getOrDefault(emptyList())
                            if (got.isNotEmpty()) channel.trySend(got)
                        } finally {
                            semaphore.release()
                            if (pending.decrementAndGet() == 0) channel.close()
                        }
                    }
                }
                var lastEmit = 0L
                withTimeoutOrNull(windowMs) {
                    while (true) {
                        // 全部源都结束（channel 被关闭）也会跳出，不必等满窗口
                        val res = channel.receiveCatching()
                        if (res.isClosed) break
                        val r = res.getOrNull() ?: continue
                        acc.addAll(r)
                        val now = System.currentTimeMillis()
                        if (onPartial != null && now - lastEmit >= 800) {
                            lastEmit = now
                            onPartial(clean(acc))
                        }
                    }
                }
            } finally {
                scope.cancel()
                channel.close()
            }
            // 注意：这里**不补简介**。补简介要额外抓 4~6 个书页（最多 3s），
            // 会拖慢「搜索结果出现」的时间。改由 VM 在列表渲染之后再后台补。
            val finalResult = clean(acc)
            onPartial?.invoke(finalResult)
            finalResult
        }

    /**
     * 云端聚合搜索兜底：笔趣阁内置的「书城网络」源用的就是阅文官方搜索接口，
     * 稳定、秒回、每本都带作者和简介。2023 年那批书源大量失效，靠它保证「随便搜都有」。
     * 结果用虚拟源 id `cloud_qq`；点开后若读不出正文，会自动换源去找能读的同名书。
     */
    private suspend fun cloudSearch(keyword: String): List<BookSearchResult> = withContext(io) {
        val url = "https://newopensearch.reader.qq.com/wechat?keyword=" +
            URLEncoder.encode(keyword, "utf-8") + "&start=0&end=20"
        val raw = runCatching {
            HttpFetcher.withTimeouts(4_000, 6_000) { HttpFetcher.get(url, "utf-8") }
        }.getOrDefault("")
        if (raw.isBlank()) return@withContext emptyList()
        val arr = runCatching { org.json.JSONObject(raw).optJSONArray("booklist") }
            .getOrNull() ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").trim()
            if (title.isBlank()) return@mapNotNull null
            val bid = o.optString("bid")
            val coverTail = bid.takeLast(3).trimStart('0').ifEmpty { "0" }
            BookSearchResult(
                sourceId = "cloud_qq",
                sourceName = "阅文书城·云端",
                title = title,
                author = o.optString("author").trim(),
                coverUrl = "http://wfqqreader-1252317822.image.myqcloud.com/cover/" +
                    "$coverTail/$bid/b_$bid.jpg",
                summary = o.optString("intro").trim(),
                bookUrl = "http://pin.qq.com/lb/intro.html?bid=$bid&g_f=100021"
            )
        }
    }

    /**
     * 给搜索结果补简介：规则没给简介的条目，去书页抓 meta description / 简介容器补上。
     * 只补前几条、且有 6s 整体上限，避免把搜索拖太久。
     */
    suspend fun enrichSummaries(list: List<BookSearchResult>): List<BookSearchResult> {
        val targets = list.filterIndexed { i, r -> i < 12 && r.summary.isBlank() }
            .distinctBy { it.bookUrl }
            .take(6)
        if (targets.isEmpty()) return list
        val map = java.util.concurrent.ConcurrentHashMap<String, String>()
        withTimeoutOrNull(3_000) {
            coroutineScope {
                targets.forEach { r ->
                    launch {
                        val s = runCatching { fetchIntro(r.bookUrl) }.getOrDefault("")
                        if (s.isNotBlank()) map[r.bookUrl] = s
                    }
                }
            }
        }
        if (map.isEmpty()) return list
        return list.map { r -> map[r.bookUrl]?.let { r.copy(summary = it) } ?: r }
    }

    private suspend fun fetchIntro(bookUrl: String): String = withContext(io) {
        val html = runCatching { HttpFetcher.get(bookUrl, "utf-8") }.getOrDefault("")
        extractIntro(html)
    }

    /** 从书页里抽简介：优先 meta description，其次常见简介容器 */
    private fun extractIntro(html: String): String {
        if (html.isBlank()) return ""
        val doc = runCatching { HtmlRules.parse(html, "") }.getOrNull() ?: return ""
        val meta = runCatching {
            doc.selectFirst("meta[name=description]")?.attr("content")?.trim().orEmpty()
        }.getOrDefault("")
        if (meta.length in 20..600 && !meta.contains("访问") && !meta.contains("验证")) return meta
        val selectors = listOf(
            "#intro", ".intro", "#bookIntro", ".book-intro", ".bookintro", "#intro1",
            ".book_desc", ".book-desc", ".description", ".desc", "#desc", ".summary",
            ".book-summary", "#content", ".content", "[class*=intro]", "[id*=intro]",
            "[class*=desc]", "[id*=desc]"
        )
        for (s in selectors) {
            val t = runCatching { doc.selectFirst(s)?.text()?.trim().orEmpty() }.getOrDefault("")
            if (t.length in 20..800) return t.take(300)
        }
        return ""
    }

    /**
     * 「立即阅读」专用搜索：不依赖用户是否手动启用书源。
     *
     * 笔趣阁能做到「点开介绍页就直接读」，是因为它的书源默认就是开着的、且搜到即用。
     * 尘剑默认把 1744 个源全停用（避免拖慢普通搜索），所以这里单独走一条路径：
     * 直接从「笔趣阁·可用」分组里取源（没有就退回启用源 / 任何有搜索规则的源），
     * 并发跑、**第一个能搜到书的源返回即停**，拿到结果就直接去读——
     * 这样既不用用户先去逐个启用书源，也不会像普通搜索那样把几千个源全跑一遍。
     */
    suspend fun quickSearch(query: String, sourceLimit: Int = 1200): List<BookSearchResult> =
        withContext(io) {
            val keyword = query.trim()
            if (keyword.isEmpty()) return@withContext emptyList()
            val all = database.bookSourceDao().listSources()
            // 只搜**已启用**的源（笔趣阁原本开启的 654 条），「可用」精选优先
            val enabled = all.filter { it.enabled }
            val usable = enabled.filter { it.groupName == "笔趣阁·可用" }
            var sources = usable + enabled.filterNot { it.groupName == "笔趣阁·可用" }
            if (sources.isEmpty()) sources = all.filter { it.groupName == "笔趣阁·可用" }
            if (sources.isEmpty()) sources = all.filter { it.searchUrl.isNotBlank() }
            sources = sources.take(sourceLimit)
            if (sources.isEmpty()) return@withContext emptyList()

            val semaphore = Semaphore(32)
            val channel = Channel<List<BookSearchResult>>(Channel.UNLIMITED)
            // 关键：**不用 supervisorScope**。HttpFetcher 是阻塞 IO，协程取消打断不了正在读的 socket，
            // supervisorScope 会一直等这些子任务（1117 个源 → 几十秒~几分钟）。这里用独立 scope，
            // 命中或到 20s 立刻返回，其余请求在后台自行超时收尾，前端「点阅读」不会被拖住。
            val scope = CoroutineScope(io + SupervisorJob())
            val firstHit: List<BookSearchResult> = try {
                sources.forEach { source ->
                    scope.launch {
                        semaphore.acquire()
                        try {
                            val got = runCatching { searchInSource(source, keyword) }
                                .onFailure { if (it is CancellationException) throw it }
                                .getOrDefault(emptyList())
                            if (got.isNotEmpty()) channel.trySend(got)
                        } finally {
                            semaphore.release()
                        }
                    }
                }
                withTimeoutOrNull(12_000) {
                    var hit: List<BookSearchResult>? = null
                    while (hit == null) {
                        val r = channel.receive()
                        if (r.isNotEmpty()) hit = r
                    }
                    hit
                } ?: emptyList()
            } finally {
                scope.cancel()
                channel.close()
            }

            val cleaned = firstHit
                .filterNot { BookResultFilter.isNavigation(it.title, it.bookUrl) }
                .distinctBy { it.sourceId + "|" + it.bookUrl }
                .let { BookMatchRanking.rank(it, keyword) }
                .let { BookResultFilter.keepRelevant(it, keyword) }
            // 源既然按书名返回过结果，就算过滤器判「不够像」也别丢——立即阅读优先命中
            if (cleaned.isEmpty()) firstHit else cleaned
        }

    private suspend fun searchInSource(source: BookSourceEntity, keyword: String): List<BookSearchResult> {
        if (source.searchUrl.isBlank()) return emptyList()
        val rules = BookRulesCodec.fromJson(source.rules)
        val encoded = URLEncoder.encode(keyword, source.charset)
        val html = fetchSearchHtml(source, keyword, encoded)
        val doc = HtmlRules.parse(html, source.baseUrl)

        val results = mutableListOf<BookSearchResult>()
        if (rules != null && rules.search.list.isNotBlank()) {
            HtmlRules.selectList(doc, rules.search.list).forEach { element ->
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
        }
        // 规则陈旧/缺失导致解析不出时：直接在结果页抓「文字含关键词的链接」当结果。
        // 网上这些 2023 年的转换规则大量已失效，但页面本身还正常，靠这一层兜底能救回来。
        if (results.isNotEmpty()) return results
        return genericSearchFallback(doc, source, keyword)
    }

    /** 通用搜索兜底：不依赖书源选择器，直接把页面上「文字含关键词」的链接当搜索结果 */
    private fun genericSearchFallback(
        doc: org.jsoup.nodes.Document,
        source: BookSourceEntity,
        keyword: String
    ): List<BookSearchResult> {
        val kw = keyword.trim()
        if (kw.length < 2) return emptyList()
        val out = ArrayList<BookSearchResult>()
        val seen = HashSet<String>()
        // 明显不是书的链接（新闻/动画/下载页等）直接过滤，减少噪音
        val noise = listOf(
            "动画", "开播", "B站", "视频", "漫画", "新闻", "资讯", "排行", "推荐",
            "下载", "txt", "最新章节", "首页", "登录", "注册", "章节列表"
        )
        runCatching { doc.select("a[href]") }.getOrNull()?.forEach { a ->
            if (out.size >= 20) return@forEach
            val text = a.text().trim()
            if (text.length !in 2..32) return@forEach
            if (!text.contains(kw)) return@forEach
            if (noise.any { text.contains(it, ignoreCase = true) }) return@forEach
            val href = a.attr("abs:href").ifBlank { HtmlRules.resolveUrl(a.attr("href"), source.baseUrl) }
            if (!href.startsWith("http", ignoreCase = true)) return@forEach
            val low = href.lowercase()
            if (low.contains("/search") || low.contains("search.") || low.contains("javascript")) return@forEach
            if (!seen.add(href)) return@forEach
            out.add(
                BookSearchResult(
                    sourceId = source.id,
                    sourceName = source.name,
                    title = text,
                    author = "",
                    coverUrl = "",
                    summary = "",
                    bookUrl = href
                )
            )
        }
        return out
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
            return@withContext HttpFetcher.withTimeouts(6_000, 8_000) {
                HttpFetcher.post(url.trim(), form, source.charset, sourceHeaders(source))
            }
        }
        // 兼容历史数据里的三花括号 {{{key}}}（会变成 {关键词} 导致服务器搜不到）；{{page}} 取第 1 页
        val url = raw
            .replace("{{{key}}}", "{{key}}")
            .replace("{{{key}}", "{{key}}")
            .replace("{{page-1}}", "0")
            .replace("{{page}}", "1")
            .replace("{{key}}", encoded)
            .replace("{key}", encoded)
        return@withContext HttpFetcher.withTimeouts(6_000, 8_000) {
            HttpFetcher.get(url, source.charset, sourceHeaders(source))
        }
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

    /** 把章节地址补成绝对地址；看起来根本不是链接（公告标题、纯文本）的返回 null */
    private fun resolveChapterUrl(raw: String, pageUrl: String): String? {
        val v = raw.trim()
        if (v.isEmpty() || v.startsWith("javascript", true)) return null
        if (v.startsWith("http", true) || v.startsWith("local://")) return v
        if (v.startsWith("#")) return HtmlRules.resolveUrl(v, pageUrl)
        // 纯标题常带书名号/中文标点，且没有路径特征 —— 直接丢
        if (v.contains("《") || v.contains("》") || v.contains("！") || v.contains("，")) return null
        val looksLikePath = v.contains("/") || v.contains(".htm") || v.contains("=") ||
            v.all { it.isDigit() }
        if (!looksLikePath) return null
        return HtmlRules.resolveUrl(v, pageUrl).takeIf { it.startsWith("http", true) }
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
            // 旧版还会把「章节标题」当成地址缓存（如「第一章 床底。」），抓的时候报 no protocol。
            // 只保留合法地址；一条都不合法就清掉重抓。
            val valid = cached.filter {
                it.url.startsWith("http", true) || it.url.startsWith("local://")
            }
            // 旧版本兜底可能把「书详情页自身」当成唯一一章缓存，升级后自动失效重抓
            if (valid.isNotEmpty() &&
                (bookUrl.startsWith(LOCAL_BOOK_PREFIX) || !cachedIsBookPage(valid, bookUrl))
            ) {
                return@withContext valid.map { BookChapter(it.position, it.title, it.url) }
            }
            if (cached.isNotEmpty() && valid.size != cached.size) {
                database.chapterIndexDao().clear(bookUrl)
            }
        }
        if (bookUrl.startsWith(LOCAL_BOOK_PREFIX)) {
            val restored = database.chapterCacheDao().localChapters(bookUrl)
                .sortedBy { it.url.substringAfterLast('#').toIntOrNull() ?: 0 }
                .mapIndexed { i, c -> ChapterIndexEntity(bookUrl, i, c.title, c.url) }
            database.chapterIndexDao().upsertAll(restored)
            return@withContext restored.map { BookChapter(it.position, it.title, it.url) }
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
            HtmlRules.parse(
                HttpFetcher.withTimeouts(4_000, 6_000) {
                    HttpFetcher.get(bookUrl, source.charset, sourceHeaders(source))
                },
                bookUrl
            )
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
                    HtmlRules.parse(
                        HttpFetcher.withTimeouts(4_000, 6_000) {
                            HttpFetcher.get(entry, source.charset, sourceHeaders(source))
                        },
                        entry
                    )
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
            val rawUrl = HtmlRules.firstText(element, toc.url, pageUrl)
            // 规则失效时 toc.url 常抽出「站点公告标题」而不是链接（如「新书《捞尸人》，已发布！」），
            // 拿去 HttpFetcher 就会报 no protocol。只接受像链接的地址。
            val url = resolveChapterUrl(rawUrl, pageUrl) ?: return@forEach
            if (title.isEmpty() || filter.isNotBlank() && !url.contains(filter)) return@forEach
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
                    HtmlRules.parse(
                        HttpFetcher.withTimeouts(4_000, 6_000) {
                            HttpFetcher.get(directoryLink, source.charset, sourceHeaders(source))
                        },
                        directoryLink
                    )
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
        // 旧版本曾把 301 / nginx 错误页、或抽取失败的空残片（如 `<div id="content_tip"><b>`）
        // 当正文缓存过；这些都要自动失效重抓，否则修好规则后用户还是看到空白。
        if (!forceRefresh && cached != null && !isDirtyCachedContent(cached.content)) {
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

        val headers = sourceHeaders(source)
        suspend fun fetchPage(url: String): String = HttpFetcher.withTimeouts(4_000, 6_000) {
            HttpFetcher.get(url, source.charset, headers)
        }
        val siblings = if (anchorId.isNullOrBlank()) emptySet()
        else database.chapterIndexDao().listChapters(bookUrl)
            .map { it.url.substringAfter('#', "") }
            .filter { it.isNotBlank() }
            .toSet()
        // 一份 HTML 只解析一次（Jsoup 解析百 KB 页面要几百毫秒，之前一页被解析了 2~4 次）
        fun extractFromDoc(d: org.jsoup.nodes.Document): String =
            if (anchorId.isNullOrBlank()) HtmlRules.extractContent(d, rules.content)
            else HtmlRules.extractContent(d, rules.content, anchorId, siblings)
        fun visibleLength(s: String) = s.replace(Regex("<[^>]+>"), "").length

        val t0 = System.currentTimeMillis()
        var html = fetchPage(baseUrl)
        var doc = HtmlRules.parse(html, baseUrl)
        android.util.Log.d("BookRepository", "正文页 $baseUrl html=${html.length}字 抓取+解析 ${System.currentTimeMillis() - t0}ms")

        // 抽取：CSS（extractContent 内部已含选择器 + 通用兜底，不用重复调用）
        val jsRule = rules.content.jsContent
        val jsUsable = jsRule.isNotBlank() && JsSandbox.isExecutable(jsRule) && appContext != null
        var content = extractFromDoc(doc)
        // 只有「完全抽不出正文」才动 JS 沙箱（WebView 启动就要几百毫秒，别为「不太可信」白跑）
        if (content.isBlank() && jsUsable) {
            val script = JsSandbox.extractScript(jsRule)
            val out = runCatching { JsSandbox.eval(appContext!!, script, html, 2_500) }.getOrNull()
            if (!out.isNullOrBlank()) {
                val wrapped = if (out.contains("<")) out else "<div>" + out.replace("\n", "<br>") + "</div>"
                if (HtmlRules.isPlausibleContent(wrapped)) content = out
            }
        }

        // 正文分页：笔趣阁（Legado）靠 nextContentUrl / ruleContentUrlNext 把一章的多页拼起来，
        // 尘剑此前只抓第一页，所以「内容缺一大半、排版不对」。现在补上自动翻页拼接。
        // 仅在首段偏短时翻（分页站第一页通常短），最多 3 页、地址去重，避免把下一章拼进来。
        val nextRule = rules.content.nextContentUrl
        if (nextRule.isNotBlank() && anchorId.isNullOrBlank() && visibleLength(content) < 1500) {
            val visited = hashSetOf(baseUrl)
            var pageUrl = baseUrl
            var pages = 1
            while (pages < 3) {
                val next = runCatching { HtmlRules.firstText(doc, nextRule, pageUrl).trim() }
                    .getOrDefault("")
                if (!next.startsWith("http", true) || !visited.add(next) || next == pageUrl) break
                val nextHtml = runCatching { fetchPage(next) }.getOrDefault("")
                if (nextHtml.isBlank()) break
                val nextDoc = HtmlRules.parse(nextHtml, next)
                val part = extractFromDoc(nextDoc)
                if (part.isBlank() || !HtmlRules.isPlausibleContent(part)) break
                content = content + "\n" + part
                html = nextHtml
                doc = nextDoc
                pageUrl = next
                pages++
            }
            if (pages > 1) android.util.Log.d("BookRepository", "正文分页拼接 $pages 页，共 ${visibleLength(content)} 字")
        }

        // 抽出来的东西必须像正文：目录页 / 书详情页 / 反爬空壳一律判失败，
        // 既不缓存（否则一直显示错内容），也让阅读器的「自动换源」接手去找能读的源。
        if (content.isBlank() || !HtmlRules.isPlausibleContent(content)) throw IllegalStateException(
            "这个源没抓到可信正文（页面约 ${html.length} 字节，多半是反爬空壳或抽成了目录/详情页）"
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

    /** 识别被误缓存的错误页 / 抽取失败的空残片（正常正文不会这么短，也不会出现 nginx 的字样） */
    private fun isDirtyCachedContent(content: String): Boolean {
        val plain = content.lowercase()
        if (content.length <= 4000 && listOf(
                "moved permanently", "nginx", "access denied", "forbidden",
                "bad gateway", "service unavailable"
            ).any { plain.contains(it) }
        ) return true
        // 可见文字几乎没有、总长又很短：基本是抽取失败留下的 HTML 残片（`<div id="content_tip"><b>`）
        val visible = content
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), "")
        if (visible.length < 24 && content.length < 220) return true
        // 旧版把「最新章节列表 / 书详情页」当正文缓存过：中短内容做一次可信度判定，
        // 长正文（>4000）直接放行，避免误伤正常章节。
        return content.length < 4000 && !HtmlRules.isPlausibleContent(content)
    }

    suspend fun clearOldCache(olderThanMillis: Long = System.currentTimeMillis() - 7 * 24 * 3600_000L) =
        withContext(io) { database.chapterCacheDao().deleteOlderThan(olderThanMillis) }

    /**
     * 加入书架（收藏）。已存在则复用并置为已收藏。
     * favorited=false 时只建一条「临时阅读」记录（书架列表不显示）。
     */
    suspend fun addToShelf(result: BookSearchResult, favorited: Boolean = true): Long = withContext(io) {
        val existing = database.shelfBookDao().getByUrl(result.bookUrl)
        if (existing != null) {
            if (favorited && !existing.favorited) {
                database.shelfBookDao().setFavorited(existing.id, true)
            }
            return@withContext existing.id
        }
        database.shelfBookDao().upsert(
            ShelfBookEntity(
                title = result.title,
                author = result.author.takeIf { it.isNotBlank() },
                coverUrl = result.coverUrl.takeIf { it.isNotBlank() },
                summary = result.summary.takeIf { it.isNotBlank() },
                sourceId = result.sourceId,
                sourceName = result.sourceName,
                bookUrl = result.bookUrl,
                favorited = favorited
            )
        )
    }

    /** 「开始阅读 / 查看目录」用：确保有一条可读记录但不进书架（favorited=false，已收藏的不动） */
    suspend fun ensureReadingBook(result: BookSearchResult): Long =
        addToShelf(result, favorited = false)

    /** 把某条记录标记为已收藏（进书架） */
    suspend fun markFavorited(id: Long) = withContext(io) {
        database.shelfBookDao().setFavorited(id, true)
    }

    /** 某本书是否已收藏进书架（按 bookUrl 查） */
    suspend fun isFavorited(bookUrl: String): Boolean = withContext(io) {
        database.shelfBookDao().getByUrl(bookUrl)?.favorited == true
    }

    /**
     * 自动换源：当前源读不出正文时，按书名去其它源找同一本，**验证能拿到正文**后切过去。
     * 成功返回 true（书架条目已改源，旧目录/正文缓存已清）。
     */
    suspend fun autoSwitchSource(book: ShelfBookEntity): Boolean = withContext(io) {
        val kw = book.title.trim().removeSurrounding("《", "》").trim()
        if (kw.isEmpty()) return@withContext false
        // 换源只是想快速找到「另一本同名书」，不用把 206 个源全跑一遍：60 个源 + 8s 窗口
        val candidates = runCatching { search(kw, sourceLimit = 60, windowMs = 8_000) }
            .getOrDefault(emptyList())
            .filter { it.sourceId != book.sourceId }
            .distinctBy { it.sourceId }
        // 并行验证候选源：旧实现 6 个源串行 × 2 次请求，最坏要等一分钟。
        // 现在同时验证，总耗时约等于「最慢的那一个」（短超时下 ~10s），先通过的先用。
        val winner = coroutineScope {
            candidates.take(6).map { c ->
                async {
                    val ok = runCatching {
                        // 只认「章节地址是合法 http」的源；有些源把标题当成链接，直接排除
                        val chs = chapters(c.sourceId, c.bookUrl).filter { it.url.startsWith("http", true) }
                        if (chs.size < 2) return@runCatching false
                        val cc = chapterContent(c.sourceId, c.bookUrl, chs.first(), forceRefresh = false)
                        HtmlRules.isPlausibleContent(cc.html)
                    }.getOrDefault(false)
                    if (ok) c else null
                }
            }.mapNotNull { it.await() }.firstOrNull()
        } ?: return@withContext false
        runCatching { database.chapterIndexDao().clear(book.bookUrl) }
        runCatching { database.chapterCacheDao().clearBook(book.bookUrl) }
        database.shelfBookDao().updateSource(
            book.id, winner.sourceId, winner.sourceName, winner.bookUrl,
            winner.coverUrl.takeIf { it.isNotBlank() }
        )
        true
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
    suspend fun importLocalBook(parsed: ParsedLocalBook): Long = withContext(io) { database.withTransaction {
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
    } }

    suspend fun updateOffset(bookId: Long, index: Int, offset: Int) = withContext(io) {
        database.shelfBookDao().updateOffset(bookId, index, offset.coerceAtLeast(0))
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

    /** 当前库里内置笔趣阁书源（id 前缀 `legado_`）的条数，用于校验导入是否完整 */
    suspend fun countBundledLegadoSources(): Int = withContext(io) {
        database.bookSourceDao().countByPrefix("legado_", 7)
    }

    /**
     * 一次性把内置的笔趣阁书源（id 前缀 `legado_`）全部启用。
     * 用于把老版本「导入时默认停用」的存量书源迁移成「默认启用」，返回启用数量。
     */
    suspend fun enableAllBundledLegadoSources(): Int = withContext(io) {
        val ids = database.bookSourceDao().listSources()
            .asSequence()
            .filter { it.id.startsWith("legado_") }
            .map { it.id }
            .toList()
        if (ids.isEmpty()) return@withContext 0
        ids.chunked(400).forEach { database.bookSourceDao().setEnabled(it, true) }
        ids.size
    }

    suspend fun deleteSources(ids: List<String>) = withContext(io) {
        ids.chunked(400).forEach { database.bookSourceDao().deleteByIds(it) }
    }

    suspend fun resetDefaultSources() = withContext(io) {
        // 内置书源现在就是 APK 里的 1744 条（MyFirstApp 首次启动导入），这里清掉其它非内置源
        database.bookSourceDao().deleteNotByPrefix("legado_", 7)
        database.bookSourceDao().markAllBuiltIn()
    }

    /**
     * 导入书源。支持两种格式：
     * 1. 单个对象：{ name, baseUrl, searchUrl, charset, rules:{...} }
     * 2. 数组（可以一次导入几十上百个）
     */
    suspend fun importSources(text: String, builtIn: Boolean = false): Int = withContext(io) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext 0
        var imported = 0
        val array = runCatching { JSONArray(trimmed) }.getOrNull()
        if (array != null) {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (importOne(item, builtIn)) imported++
            }
        } else {
            val single = runCatching { JSONObject(trimmed) }.getOrNull() ?: return@withContext 0
            if (importOne(single, builtIn)) imported = 1
        }
        imported
    }

    /** 从一个网址直接拉取书源 JSON（例如书源集合的链接），再导入 */
    suspend fun importFromUrl(url: String): Int = withContext(io) {
        val text = runCatching { HttpFetcher.get(url.trim(), "utf-8") }.getOrNull() ?: return@withContext 0
        importSources(text)
    }

    private suspend fun importOne(json: JSONObject, builtIn: Boolean = false): Boolean {
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
            builtIn = builtIn,
            sortOrder = json.optInt("sortOrder", 100),
            groupName = json.optString("group").trim(),
            header = json.optString("header").takeIf { it.isNotBlank() }
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
                if (importOne(item, builtIn = true)) imported++
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
