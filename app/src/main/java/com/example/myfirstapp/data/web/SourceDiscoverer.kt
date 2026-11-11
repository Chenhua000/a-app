package com.example.myfirstapp.data.web

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONObject
import com.example.myfirstapp.domain.BookRules
import com.example.myfirstapp.domain.BookRulesCodec
import com.example.myfirstapp.domain.ContentRule
import com.example.myfirstapp.domain.HtmlRules
import com.example.myfirstapp.domain.SearchRule
import com.example.myfirstapp.domain.TocRule
import java.net.URLEncoder

/**
 * 书源自动发现。
 *
 * 背景：之前尝试「离线批处理」从几百个纯网址逆推规则，结果 0/97 —— 因为本机（非国内网络）抓不到
 * 国内小说站内容，且候选站多是 POST / JS 搜索，而 App 内置搜索是 GET-only。
 * 所以把「推断规则」这一步搬到**手机端**（有真实网络、能跑 POST），作为 App 内「发现书源」功能。
 *
 * 设计要点：
 *  - 核心的「矿工」（`findRepeatedAnchorList` / `mineBookList` / `mineToc` / `mineContent`）都是
 *    **纯函数**，吃一个 jsoup 的 Document 吐出 Legado 风格选择器。这样不依赖网络就能在 JVM 上
 *    用合成 HTML 做单元测试（模拟器没外网，没法真机联调）。
 *  - `discover(...)` 是唯一会联网的编排函数，由 Repository 在手机上调用。
 *  - 产出的 searchUrl 兼容两种写法：GET 的 `...{{key}}...`，以及 POST 的
 *    `post::地址||字段={{key}}`（被 BookRepository.fetchSearchHtml 识别）。
 */
object SourceDiscoverer {

    /** 发现结果。ok=false 时 rulesJson 为空，message 说明原因。 */
    data class DiscoveredSource(
        val ok: Boolean,
        val name: String,
        val baseUrl: String,
        val searchUrl: String,
        val charset: String,
        val rulesJson: String,
        val quality: String, // "full" | "search+toc" | "search-only" | "fail"
        val message: String
    )

    // region ---------- 对外入口（联网） ----------

    /**
     * @param inputUrl 站点首页或任意网址
     * @param keyword 用于试探搜索的一个书名（默认调用方传热门书，比如「斗破苍穹」）
     */
    suspend fun discover(inputUrl: String, keyword: String): DiscoveredSource {
        val baseUrl = normalizeBaseUrl(inputUrl)
            ?: return DiscoveredSource(false, "", "", "", "utf-8", "", "fail", "网址无法解析，请检查格式")

        val homeBytes = safeGetBytes(baseUrl) ?: return DiscoveredSource(
            false, "", baseUrl, "", "utf-8", "", "fail", "首页打不开（连不上或超时）"
        )
        val cs = HttpFetcher.detectCharset(homeBytes, "utf-8")
        val homeHtml = String(homeBytes, cs)
        val homeDoc = HtmlRules.parse(homeHtml, baseUrl)
        val charset = cs.name()

        // 1) 找搜索入口并试搜，直到某个候选能挖出书列表
        val candidates = buildSearchCandidates(homeDoc, baseUrl, charset, keyword)
        var searchRule: SearchRule? = null
        var searchUrlTemplate = ""
        var resultsDoc: Document? = null
        var resultsBase = baseUrl
        for (cand in candidates) {
            val html = runCatching { cand.fetch() }.getOrNull() ?: continue
            if (html.length < 200) continue
            val doc = HtmlRules.parse(html, cand.baseUrl)
            val rule = mineBookList(doc, cand.baseUrl, keyword)
            if (rule != null) {
                searchRule = rule
                searchUrlTemplate = cand.searchUrlTemplate
                resultsDoc = doc
                resultsBase = cand.baseUrl
                break
            }
        }
        if (searchRule == null) {
            return DiscoveredSource(
                false, "", baseUrl, "", charset, "", "fail",
                "没能从搜索结果里识别出书籍列表（站点可能需登录 / 纯 JS 搜索 / 无搜索）"
            )
        }

        // 2) 取第一本书，试着挖目录和正文
        val items = HtmlRules.selectList(resultsDoc!!, searchRule.list)
        val sampleUrl = items.firstNotNullOfOrNull {
            HtmlRules.firstText(it, searchRule.url, resultsBase).takeIf { u -> u.startsWith("http") }
        }
        var toc: TocRule? = null
        var content: ContentRule? = null
        if (sampleUrl != null) {
            val bookHtml = safeGet(sampleUrl, charset)
            if (bookHtml != null) {
                val bookDoc = HtmlRules.parse(bookHtml, sampleUrl)
                toc = mineToc(bookDoc, sampleUrl, charset)
                val chapUrl = firstChapterUrl(bookDoc, toc, sampleUrl, charset)
                if (chapUrl != null) {
                    val chapHtml = safeGet(chapUrl, charset)
                    if (chapHtml != null) {
                        content = mineContent(HtmlRules.parse(chapHtml, chapUrl), chapUrl)
                    }
                }
            }
        }

        // 3) 组装书源 JSON
        val rules = BookRules(
            search = searchRule,
            toc = toc ?: TocRule(),
            content = content ?: ContentRule()
        )
        val host = baseUrl.replaceFirst(Regex("^https?://"), "")
        val quality = when {
            content != null && toc != null -> "full"
            toc != null -> "search+toc"
            else -> "search-only"
        }
        val obj = JSONObject().apply {
            put("name", "自动发现：$host")
            put("baseUrl", baseUrl)
            put("searchUrl", searchUrlTemplate)
            put("charset", charset)
            put("rules", JSONObject(BookRulesCodec.toJson(rules)))
            put("group", "自动发现")
            put("enabled", true)
        }
        val msg = buildString {
            append("发现成功：能搜索")
            append(
                when (quality) {
                    "full" -> " + 有目录 + 有正文（可完整阅读）"
                    "search+toc" -> " + 有目录（正文规则可能不准，可手动补）"
                    else -> "（仅搜索可用，目录/正文需手动补）"
                }
            )
        }
        return DiscoveredSource(
            ok = true, name = "自动发现：$host", baseUrl = baseUrl,
            searchUrl = searchUrlTemplate, charset = charset,
            rulesJson = obj.toString(), quality = quality, message = msg
        )
    }

    // endregion

    // region ---------- 搜索入口候选 ----------

    private data class SearchCandidate(
        val searchUrlTemplate: String,
        val baseUrl: String,
        val fetch: suspend () -> String?
    )

    private fun buildSearchCandidates(
        homeDoc: Document,
        baseUrl: String,
        charset: String,
        keyword: String
    ): List<SearchCandidate> {
        val list = mutableListOf<SearchCandidate>()

        // 优先用首页里真正的 <form> 搜索框
        homeDoc.select("form").forEach { form ->
            val kwInput = form.select("input").firstOrNull { inp ->
                val t = inp.attr("type").lowercase()
                val n = inp.attr("name").lowercase()
                val id = inp.attr("id").lowercase()
                val ph = inp.attr("placeholder").lowercase()
                (t == "search" || t == "text") &&
                        (n in listOf("q", "s", "wd", "keyword", "key", "search", "query", "kw", "name")
                                || id in listOf("q", "s", "wd", "keyword", "key", "search", "query", "kw")
                                || listOf("搜索", "书", "小说", "找").any { ph.contains(it) })
            } ?: return@forEach
            val action = HtmlRules.resolveUrl(form.attr("action").ifBlank { baseUrl }, baseUrl)
            if (!action.startsWith("http")) return@forEach
            val method = form.attr("method").takeIf { it.isNotBlank() }?.uppercase() ?: "GET"
            val kwName = kwInput.attr("name")
            if (kwName.isBlank()) return@forEach
            val extra = mutableMapOf<String, String>()
            form.select("input").forEach { inp ->
                val name = inp.attr("name")
                val t = inp.attr("type").lowercase()
                if (name.isNotBlank() && name != kwName &&
                    (t == "hidden" || inp.attr("value").isNotBlank())
                ) extra[name] = inp.attr("value")
            }

            if (method == "POST") {
                // POST：字段保持原始值（fetcher 会编码），{{key}} 留给 App 搜索时替换
                val body = buildString {
                    append("$kwName={{key}}")
                    extra.forEach { (k, v) -> append("&$k=$v") }
                }
                list.add(
                    SearchCandidate(
                        searchUrlTemplate = "post::$action||$body",
                        baseUrl = action
                    ) {
                        HttpFetcher.post(action, extra + (kwName to keyword), charset)
                    }
                )
            } else {
                // GET：常量字段先编码后写进模板（App 搜索时只替换 {{key}}）
                val const = extra.map { (k, v) -> "${enc(k, charset)}=${enc(v, charset)}" }
                val tmpl = buildString {
                    append(action)
                    append(if (action.contains("?")) "&" else "?")
                    if (const.isNotEmpty()) append(const.joinToString("&") + "&")
                    append("${enc(kwName, charset)}={{key}}")
                }
                list.add(
                    SearchCandidate(
                        searchUrlTemplate = tmpl,
                        baseUrl = action
                    ) {
                        val params = (extra + (kwName to keyword)).map { (k, v) ->
                            "${enc(k, charset)}=${enc(v, charset)}"
                        }
                        val url = "$action${if (action.contains("?")) "&" else "?"}${params.joinToString("&")}"
                        HttpFetcher.get(url, charset)
                    }
                )
            }
        }

        // 没有可用表单时，试探几个常见的搜索接口（GET）
        if (list.isEmpty()) {
            val probes = listOf(
                "/search?q={{key}}",
                "/?s={{key}}",
                "/search.php?q={{key}}",
                "/index.php?keyword={{key}}",
                "/search?keyword={{key}}",
                "/plus/search.php?q={{key}}",
                "/modules/article/search.php?searchkey={{key}}"
            )
            probes.forEach { path ->
                val url = baseUrl.trimEnd('/') + path
                list.add(
                    SearchCandidate(
                        searchUrlTemplate = url,
                        baseUrl = baseUrl
                    ) {
                        val real = baseUrl.trimEnd('/') + path.replace("{{key}}", enc(keyword, charset))
                        HttpFetcher.get(real, charset)
                    }
                )
            }
        }
        return list
    }

    // endregion

    // region ---------- 纯矿工：列表 / 目录 / 正文 ----------

    /**
     * 在文档里找出「一组重复出现的同类链接」的公共容器，返回 list 选择器 + item 标签。
     * 这是列表页和目录页共用的核心：书列表找 book 锚点，目录找 chapter 锚点。
     */
    fun findRepeatedAnchorList(
        doc: Document,
        isItem: (Element) -> Boolean
    ): Pair<String, String>? {
        val anchors = doc.select("a").filter { isItem(it) }
        if (anchors.size < 3) return null

        // 标记所有「锚点或其祖先」，用来判断「某元素是否包含书/章锚点」
        val marked = mutableSetOf<Element>()
        anchors.forEach { a -> var e: Element? = a; while (e != null) { marked.add(e); e = e.parent() } }

        // 选出最深的、直接包含 >=3 个 marked 子节点 的容器（最紧凑的列表容器）
        var root: Element? = null
        var bestDepth = -1
        marked.forEach { el ->
            val direct = el.children().count { it in marked }
            if (direct >= 3) {
                val depth = depthOf(el)
                if (depth > bestDepth) { bestDepth = depth; root = el }
            }
        }
        root ?: return null

        val items = root!!.children().filter { it in marked }
        if (items.size < 3) return null
        val itemTag = items.groupBy { it.tagName().lowercase() }
            .maxByOrNull { it.value.size }!!.key
        // 条目就是 root 的直接子节点，用 > 只选直接子节点，避免把嵌套 wrapper 也算进来
        val listSel = "${cssForElement(root!!)} > $itemTag"
        return listSel to itemTag
    }

    /** 列表页挖书：返回搜索规则；验证不通过返回 null */
    fun mineBookList(doc: Document, baseUrl: String, keyword: String): SearchRule? {
        val pair = findRepeatedAnchorList(doc, ::isBookAnchor) ?: return null
        val (listSel, itemTag) = pair
        val items = HtmlRules.selectList(doc, listSel)
        if (items.size < 3) return null

        var valid = 0
        var cover: String? = null
        items.forEach { item ->
            val name = HtmlRules.firstText(item, if (itemTag == "a") "@text" else "a@text", baseUrl)
            val url = HtmlRules.firstText(item, if (itemTag == "a") "@href" else "a@href", baseUrl)
            if (name.isNotBlank() && url.startsWith("http")) valid++
            if (cover == null) cover = findCover(item)
        }
        if (valid < 2) return null

        return SearchRule(
            list = listSel,
            name = if (itemTag == "a") "@text" else "a@text",
            author = "",
            cover = cover ?: "",
            summary = "",
            url = if (itemTag == "a") "@href" else "a@href"
        )
    }

    /** 目录页挖章节；若详情页没有目录，会顺带尝试跳到「目录/全书」入口页 */
    fun mineToc(doc: Document, baseUrl: String, charset: String): TocRule? {
        findRepeatedAnchorList(doc, ::isChapterAnchor)?.let { (listSel, _) ->
            val contains = computeContains(doc, listSel, baseUrl)
            return TocRule(list = listSel, name = "@text", url = "@href", contains = contains)
        }
        // 详情页上找「目录 / 全书 / 阅读」入口，跳过去再挖
        val cat = doc.select("a").firstOrNull { a ->
            val t = a.text()
            val h = a.attr("href").lowercase()
            t.contains("目录") || t.contains("章节") || t.contains("全书") || t.contains("阅读") ||
                    h.contains("mulu") || h.contains("chapterlist") || h.contains("/index") || h.contains("all")
        }
        if (cat != null) {
            val catUrl = HtmlRules.resolveUrl(cat.attr("href"), baseUrl)
            val catHtml = safeGet(catUrl, charset) ?: return null
            val catDoc = HtmlRules.parse(catHtml, catUrl)
            findRepeatedAnchorList(catDoc, ::isChapterAnchor)?.let { (listSel, _) ->
                val contains = computeContains(catDoc, listSel, catUrl)
                // pageUrl 用选择器从详情页取目录入口（与 BookRepository.chapters 的约定一致）
                return TocRule(
                    pageUrl = "a:contains(目录)@href", list = listSel,
                    name = "@text", url = "@href", contains = contains
                )
            }
        }
        return null
    }

    /** 正文页挖内容容器 */
    fun mineContent(doc: Document, baseUrl: String): ContentRule? {
        val meaningful = listOf(
            "content", "chapter", "read", "text", "article", "book", "body",
            "nr", "novel", "txt", "view", "main", "container", "con", "font"
        )
        var best: Element? = null
        var bestScore = 0
        doc.select("div, article, section, td").forEach { el ->
            val cls = el.className().lowercase()
            val id = el.id().lowercase()
            val scoreMeaningful = meaningful.any { cls.contains(it) || id.contains(it) }
            val pCount = el.select("p").size
            val textLen = el.text().length
            if (textLen < 200) return@forEach
            val score = textLen + pCount * 60 + if (scoreMeaningful) 6000 else 0
            if (score > bestScore) { bestScore = score; best = el }
        }
        // 兜底：含 <p> 文本最多的元素
        if (best == null) {
            doc.select("div, article, section, td").forEach { el ->
                val pText = el.select("p").sumOf { it.text().length }
                if (pText > bestScore) { bestScore = pText; best = el }
            }
        }
        best ?: return null
        return ContentRule(selector = cssForElement(best!!), remove = emptyList(), replace = emptyList())
    }

    // endregion

    // region ---------- 锚点判定 ----------

    private fun isBookAnchor(a: Element): Boolean {
        val href = a.attr("href").trim()
        if (href.isBlank() || href.startsWith("javascript:") || href.startsWith("#") ||
            href.startsWith("mailto:") || href.startsWith("tel:")
        ) return false
        val text = a.text().trim()
        if (text.length < 2 || text.length > 40) return false
        if (listOf("首页", "上一页", "下一页", "上一章", "下一章", "目录", "返回", "登录", "注册",
                "更多", "简介", "排行", "分类", "最新", "热门", "搜索", "首页").any { text.contains(it) } &&
            text.length < 6
        ) return false
        val lower = href.lowercase()
        val detailish = lower.contains(Regex("(/book/|/novel/|/read/|/info/|/article/|/show/|/html/|/bookinfo/|/xiaoshuo/|/book/)"))
                || lower.contains(Regex("[?&]id=\\d+"))
                || Regex("/[^/]*\\d{3,}[^/]*\\.[a-z0-9]+$").containsMatchIn(lower)
                || Regex("/\\d{3,}/?$").containsMatchIn(lower)
        return detailish
    }

    private fun isChapterAnchor(a: Element): Boolean {
        val href = a.attr("href").trim()
        if (href.isBlank() || href.startsWith("javascript:") || href.startsWith("#")) return false
        val text = a.text().trim()
        if (text.isBlank()) return false
        if (listOf("上一章", "下一章", "上一页", "下一页", "目录", "返回", "首页",
                "加入书架", "投票", "举报").any { text.contains(it) }) return false
        val chapterish = text.matches(Regex(".*第\\s*\\d+\\s*[章回卷节].*")) ||
                text.lowercase().matches(Regex(".*chapter\\s*\\d+.*")) ||
                text.contains("序章") || text.contains("引子") || text.contains("尾声") || text.contains("后记") ||
                (text.matches(Regex("\\d+")) && href.contains(Regex("\\d{2,}")))
        if (chapterish) return true
        // 序号链接（短文本 + 带数字路径）
        return href.contains(Regex("\\d{2,}")) && text.length <= 12
    }

    private fun findCover(item: Element): String? {
        val imgs = item.select("img")
        if (imgs.isEmpty()) return null
        val hint = imgs.firstOrNull { img ->
            val cls = img.className().lowercase()
            cls.contains("cover") || cls.contains("img") || cls.contains("pic") || cls.contains("book")
        }
        return if (hint != null || imgs.size == 1) "img@src" else null
    }

    // endregion

    // region ---------- 选择器生成 / 工具 ----------

    /** 给一个元素生成尽量稳定的 CSS 选择器（优先 id → 有意义的 class → 带 id 的祖先） */
    fun cssForElement(el: Element): String {
        val id = el.id()
        if (id.isNotBlank()) return "#$id"
        val cls = el.className().trim().split(Regex("\\s+")).firstOrNull { it.isNotBlank() }
        if (cls != null) {
            val meaningful = listOf(
                "content", "chapter", "read", "text", "article", "book", "body", "list",
                "item", "result", "nr", "novel", "txt", "view", "main", "container", "con"
            )
            if (meaningful.any { cls.lowercase().contains(it) }) return ".$cls"
        }
        var p = el.parent()
        while (p != null) {
            if (p.id().isNotBlank()) return "#${p.id()} ${el.tagName().lowercase()}"
            p = p.parent()
        }
        return if (cls != null) ".$cls" else el.tagName().lowercase()
    }

    /** 计算章节链接的公共路径前缀，用作 TocRule.contains 过滤无关链接 */
    private fun computeContains(doc: Document, listSel: String, baseUrl: String): String {
        val items = HtmlRules.selectList(doc, listSel)
        // 列表条目可能是 <a> 本身，也可能是 <li>/<div> 包裹着 <a>；都按「条目取链接」的方式拿 href
        val hrefs = items.mapNotNull { item ->
            val u = if (item.tagName().equals("a", true)) {
                HtmlRules.firstText(item, "@href", baseUrl)
            } else {
                HtmlRules.firstText(item, "a@href", baseUrl)
            }
            u.takeIf { it.startsWith("http") }
        }
        if (hrefs.size < 2) return ""
        val host = runCatching { java.net.URI(baseUrl).authority }.getOrNull() ?: ""
        val paths = hrefs.map { it.removePrefix("https://").removePrefix("http://").removePrefix(host) }
        val common = longestCommonSubstring(paths)
        return if (common.length >= 6) common else ""
    }

    private fun longestCommonSubstring(strings: List<String>): String {
        if (strings.isEmpty()) return ""
        var shortest = strings.minByOrNull { it.length } ?: return ""
        var result = ""
        for (i in shortest.indices) {
            for (j in (i + 1)..shortest.length) {
                val sub = shortest.substring(i, j)
                if (sub.length > result.length && strings.all { it.contains(sub) }) {
                    result = sub
                }
            }
        }
        return result
    }

    private fun depthOf(el: Element): Int {
        var d = 0
        var e = el.parent()
        while (e != null) { d++; e = e.parent() }
        return d
    }

    private fun firstChapterUrl(
        bookDoc: Document,
        toc: TocRule?,
        sampleUrl: String,
        charset: String
    ): String? {
        if (toc == null) return null
        val pageDoc = if (toc.pageUrl.isNotBlank()) {
            val entry = HtmlRules.firstText(bookDoc, toc.pageUrl, sampleUrl)
            if (entry.isNotBlank()) {
                val catUrl = HtmlRules.resolveUrl(entry, sampleUrl)
                safeGet(catUrl, charset)?.let { HtmlRules.parse(it, catUrl) } ?: bookDoc
            } else bookDoc
        } else bookDoc
        val base = if (toc.pageUrl.isNotBlank()) {
            val entry = HtmlRules.firstText(bookDoc, toc.pageUrl, sampleUrl)
            if (entry.isNotBlank()) HtmlRules.resolveUrl(entry, sampleUrl) else sampleUrl
        } else sampleUrl
        val items = HtmlRules.selectList(pageDoc, toc.list)
        val first = items.firstOrNull() ?: return null
        val u = HtmlRules.firstText(first, toc.url, base)
        return if (u.startsWith("http")) u else null
    }

    // endregion

    // region ---------- 网络 / 通用工具 ----------

    private fun normalizeBaseUrl(url: String): String? {
        var u = url.trim()
        if (!u.matches(Regex("^https?://", RegexOption.IGNORE_CASE))) u = "https://$u"
        return runCatching {
            val uri = java.net.URI(u)
            val scheme = uri.scheme ?: "https"
            val authority = uri.authority ?: return@runCatching null
            "$scheme://$authority"
        }.getOrNull()
    }

    private fun enc(value: String, charset: String): String =
        URLEncoder.encode(value, charset)

    private fun safeGet(url: String, charset: String): String? =
        runCatching { HttpFetcher.get(url, charset) }.getOrNull()
            ?: runCatching { HttpFetcher.get(url.replace("https://", "http://"), charset) }.getOrNull()
            ?: runCatching { HttpFetcher.get(url.replace("http://", "https://"), charset) }.getOrNull()

    private fun safeGetBytes(url: String): ByteArray? =
        runCatching { HttpFetcher.getBytes(url) }.getOrNull()
            ?: runCatching { HttpFetcher.getBytes(url.replace("https://", "http://")) }.getOrNull()
            ?: runCatching { HttpFetcher.getBytes(url.replace("http://", "https://")) }.getOrNull()

    // endregion
}
