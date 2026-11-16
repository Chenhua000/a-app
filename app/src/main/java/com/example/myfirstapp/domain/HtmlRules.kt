package com.example.myfirstapp.domain

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 极小的抽取引擎：CSS 选择器 + @属性后缀 + 备用表达式（||）+ 正则替换。
 * 不执行任何 JavaScript。
 */
object HtmlRules {

    /** 默认的广告/无关元素，正文抽取时统一清掉 */
    private val DEFAULT_REMOVE = listOf(
        "script", "style", "noscript", "iframe", "ins", "object", "embed",
        "[class*=ad]", "[class*=Ad]", "[class*=AD]", "[id*=ad]", "[id*=Ad]",
        "[class*=banner]", "[class*=google]", "[class*=share]", "[class*=comment]",
        "[class*=foot]", "[class*=nav]", "[class*=toolbar]", "[class*=app]"
    )

    fun parse(html: String, baseUrl: String): Document {
        val doc = Jsoup.parse(html, baseUrl)
        doc.outputSettings().prettyPrint(false)
        return doc
    }

    /**
     * 按规则选出一组元素。
     * 同时支持标准 CSS 和「阅读 / Legado」风格写法（`id.x@tag.div!0`、`a.2@href` 等），
     * 多个备选用 `||` 分隔，取第一个有结果的。
     */
    fun selectList(doc: Element, rule: String): List<Element> = LegadoSelector.select(doc, rule)

    /** 把相对地址补成绝对地址 */
    fun resolveUrl(value: String, baseUrl: String): String {
        if (value.isBlank()) return ""
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        if (value.startsWith("//")) return "https:$value"
        // 页内锚点（#chap01）：拼回当前页面，变成「页面地址#锚点」
        if (value.startsWith("#")) return baseUrl.substringBefore('#') + value
        val base = baseUrl.trimEnd('/')
        return if (value.startsWith("/")) {
            val host = runCatching {
                val uri = java.net.URI(baseUrl)
                "${uri.scheme}://${uri.authority}"
            }.getOrDefault(base)
            host + value
        } else {
            "$base/$value"
        }
    }

    /** 取第一个非空的值：文本 / 属性 / html / textNodes 都可以 */
    fun firstText(doc: Element, rule: String, baseUrl: String): String =
        LegadoSelector.value(doc, rule, baseUrl)

    /** 取一组值（列表页用） */
    fun listText(doc: Element, rule: String, baseUrl: String): List<String> =
        LegadoSelector.values(doc, rule, baseUrl)

    /** 章节标题特征：第N章/回/卷/篇/话、纯序号、Chapter N、序章等 */
    private val CHAPTER_TITLE = Regex(
        "(第[0-9零一二三四五六七八九十百千万两]+[章节回卷篇话集部幕].*|" +
            "^[0-9]{1,4}[.、:：\\s].*|" +
            "chapter\\s*\\d+.*|" +
            "序章.*|楔子.*|引子.*|尾声.*|番外.*)",
        RegexOption.IGNORE_CASE
    )

    /** 章节链接特征：/read/123、/chapter/1、/123.html */
    private val CHAPTER_PATH = Regex(
        "/(chapter|read|content|cr|xs|txt)[/_.-]|/\\d{2,}\\.html?|/\\d{3,}/?",
        RegexOption.IGNORE_CASE
    )

    private val NAV_LINK_TITLES = setOf(
        "首页", "书库", "书城", "书架", "目录", "加入书架", "返回", "上一页", "下一页",
        "登录", "注册", "排行榜", "分类", "更多", "最新章节", "全文阅读", "下载", "举报"
    )

    /** 书详情页上的「章节目录」入口，兜底时先进这个页面再抽章节 */
    private val DIRECTORY_LINK_WORDS = listOf(
        "目录", "章节", "阅读", "开始读", "立刻阅读", "查看全部", "全集", "正文"
    )

    /** 找出书详情页上指向章节列表页的链接（已转绝对地址、去重） */
    fun findDirectoryLinks(doc: Document, baseUrl: String): List<String> =
        runCatching { doc.select("a[href]") }.getOrNull()?.mapNotNull { a ->
            val title = a.text().trim()
            if (title.isEmpty() || title.length > 24) return@mapNotNull null
            if (DIRECTORY_LINK_WORDS.none { title.contains(it) }) return@mapNotNull null
            val href = a.attr("abs:href").ifBlank { resolveUrl(a.attr("href"), baseUrl) }
            href.takeIf { it.startsWith("http", ignoreCase = true) }
        }?.distinct() ?: emptyList()

    /** 明显的错误页，兜底时不能当正文塞给读者 */
    private val ERROR_PAGE_WORDS = listOf(
        "moved permanently", "nginx", "forbidden", "not found", "access denied",
        "bad gateway", "service unavailable", "too many requests", "error 403", "error 404"
    )

    private val FALLBACK_CONTENT_SELECTORS = listOf(
        "#content", "#booktext", "#chaptercontent", "#chapterContent", "#TextContent",
        "#htmlContent", "#BookText", ".booktext", ".chapter-content", ".read-content",
        ".content", "article", "[id*=content]", "[class*=content]"
    )

    /**
     * 书源 toc 规则失效时的通用目录兜底：从页面所有链接里挑出像章节的条目。
     * 返回 (章节名, 绝对地址)，按文档顺序、URL 去重。
     */
    fun fallbackChapters(doc: Document, baseUrl: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val seen = HashSet<String>()
        runCatching { doc.select("a[href]") }.getOrNull()?.forEach { a ->
            val title = a.text().trim()
            if (title.length < 2 || title.length > 80) return@forEach
            if (title in NAV_LINK_TITLES) return@forEach
            val href = a.attr("abs:href").ifBlank { resolveUrl(a.attr("href"), baseUrl) }
            if (!href.startsWith("http", ignoreCase = true)) return@forEach
            if (href.startsWith("javascript", ignoreCase = true)) return@forEach
            // 指向书页自身的链接不是章节，别再把它当成唯一一章缓存下来
            if (href.substringBefore('#').trimEnd('/') ==
                baseUrl.substringBefore('#').trimEnd('/')
            ) return@forEach
            val looksChapter = CHAPTER_TITLE.containsMatchIn(title) ||
                CHAPTER_PATH.containsMatchIn(href)
            if (!looksChapter) return@forEach
            if (!seen.add(href)) return@forEach
            out += title to href
        }
        return out
    }

    /** class/id 里出现这些词，基本可以断定不是正文容器（目录 / 推荐 / 排行榜 / 导航） */
    private val NON_CONTENT_HINT = listOf(
        "nav", "header", "footer", "sidebar", "comment", "menu", "toolbar",
        "list", "rank", "recommend", "update", "catalog", "directory", "related",
        "hot", "banner", "share", "tag", "bookinfo", "meta", "tip"
    )

    /** class/id 里出现这些词，说明它很可能就是正文容器，给加分 */
    private val CONTENT_HINT = listOf(
        "content", "text", "chapter", "read", "article", "novel", "booktxt", "showtxt", "booktext"
    )

    /**
     * 给一个容器打「像正文」的分：纯文字越多、段落(<p>/<br>)越多越好；
     * 链接越多越可能是目录/推荐列表，扣分；命中非正文关键词直接淘汰。
     */
    private fun contentScore(element: Element): Int {
        val key = (element.id() + " " + element.className()).lowercase()
        if (NON_CONTENT_HINT.any { key.contains(it) }) return -1
        val links = element.select("a")
        if (links.size > 30) return -1
        val text = element.text().trim()
        if (text.length < 60) return -1
        // 整页外壳（含导航+目录+正文）文字极长，排除掉，逼着选更内层的容器
        if (text.length > 20_000) return -1
        val linkLen = links.sumOf { it.text().length }
        val breaks = element.select("br").size + element.select("p").size
        var score = (text.length - linkLen * 2).coerceAtLeast(0)
        score += breaks * 60
        if (CONTENT_HINT.any { key.contains(it) }) score += text.length / 2
        return score
    }

    /**
     * 书源 content 规则失效时的通用正文兜底。
     * 旧实现取「文字最多的 div」，小说站的「最新章节列表 / 相关推荐」往往字更多，
     * 结果把目录当正文塞给读者。现在改成全页打分挑最像正文的容器。
     */
    fun fallbackContent(doc: Document, rule: ContentRule): String {
        val best = runCatching {
            doc.select("div, article, section, main, td")
                .map { it to contentScore(it) }
                .filter { it.second > 0 }
                .maxByOrNull { it.second }
                ?.first
        }.getOrNull()
        val candidate = best
        // 打分没结果时，退回常见正文选择器（并对候选也做一次校验，别把 content_tip 之类选进来）
            ?: FALLBACK_CONTENT_SELECTORS.firstNotNullOfOrNull { selector ->
                runCatching { doc.select(selector) }.getOrNull()
                    ?.firstOrNull { it.text().length > 60 && !looksLikeShell(it) && contentScore(it) > 0 }
            }
            // 最后一层：站点改版成没见过的结构时，至少把页面上可见的文字给出来。
            // 但 301 / 403 / 404 这类错误页绝不能当正文显示
            ?: runCatching { doc.body() }.getOrNull()?.takeIf {
                val text = it.text()
                text.length > 20 && ERROR_PAGE_WORDS.none { w -> text.contains(w, ignoreCase = true) }
            }
        return candidate?.let { cleanContainer(it, rule) } ?: ""
    }

    /**
     * 校验抽出来的「正文」是否可信。目录页 / 书详情页 / 推荐列表会被挡掉，
     * 避免把垃圾缓存起来一直显示错的内容（显示错比加载慢更糟）。
     */
    fun isPlausibleContent(html: String): Boolean {
        if (html.isBlank()) return false
        val body = runCatching { parse(html, "").body() }.getOrNull() ?: return false
        val text = body.text().trim()
        if (text.length < 40) return false
        val linkLen = runCatching { body.select("a").sumOf { it.text().length } }.getOrDefault(0)
        if (linkLen > text.length * 0.5) return false           // 链接太多 = 目录 / 推荐
        val noise = listOf("章节目录", "最新章节", "本书目录", "加入书架", "上一页", "下一页", "排行榜", "推荐票")
        if (noise.count { text.contains(it) } >= 3) return false
        // 「如何下载小说 / RAR格式下载 / 全本TXT」这类下载说明页也不是正文
        val download = listOf("如何下载小说", "格式下载", "全本TXT", "WINRAR", "下载地址", "请下载")
        if (download.count { text.contains(it, ignoreCase = true) } >= 2) return false
        // 「XX小说 相关 / 本站提供 / 免责声明」这类站点说明页：字数够、链接也不多，
        // 但内容是①②③④式的版权/投稿须知，不是章节正文。搜到能读、正文却是这种页的源
        // 必须判假，否则自动换源不会触发、质量反馈还会把坏源往上顶。
        val notice = listOf(
            "本站提供", "与全站立场无关", "网友更新", "请及时通知我们", "作品版权",
            "相关小说", "小说 相关", "全本阅读的所有章节", "所有章节均为", "如有侵权",
            "免责声明", "版权声明", "章节有错误", "请勿上传", "本站所有小说"
        )
        if (notice.count { text.contains(it) } >= 2) return false
        if (text.contains("相关") && Regex("[①②③④⑤⑥]").findAll(text).count() >= 3) return false
        return true
    }

    private fun looksLikeShell(element: Element): Boolean {
        val key = (element.id() + " " + element.className()).lowercase()
        return NON_CONTENT_HINT.take(7).any { key.contains(it) }
    }

    /**
     * 抽取正文：先定位容器，再删除广告等无关元素，最后按正则替换做一遍清洗。
     *
     * @param anchorId 章节锚点（形如 chap01）。整本书都在一个页面、靠 #锚点 分章的书源
     *   （古腾堡 HTML 版就是这种）靠它只取这一章，而不是整本书。
     * @param siblingAnchors 同一本书里所有章节的锚点集合，用来判断「往上爬到哪一层刚好只包含本章」
     */
    fun extractContent(
        doc: Document,
        rule: ContentRule,
        anchorId: String? = null,
        siblingAnchors: Set<String> = emptySet()
    ): String {
        // 正文容器走同一套规则引擎：`#content@html`、`id.view_content_txt@textNodes` 都支持
        val container = if (rule.selector.isBlank()) null
        else LegadoSelector.select(doc, rule.selector).firstOrNull()
        // 规则没配 / 已失效：改用通用兜底，别让「能搜到却读不了」
        if (container == null) {
            return if (anchorId.isNullOrBlank()) applySelectorRegex(fallbackContent(doc, rule), rule.selector) else ""
        }

        if (anchorId.isNullOrBlank()) return applySelectorRegex(cleanContainer(container, rule), rule.selector)

        val anchor = runCatching { doc.getElementById(anchorId) }.getOrNull()
            ?: runCatching { doc.selectFirst("a[name=$anchorId]") }.getOrNull()
            ?: return ""
        val anchors = siblingAnchors + anchorId

        // 从锚点往上爬：只要这一层还「只包含本章」，就再往上一层。
        // 停下来的那一层通常正好是 <div class="chapter"> 这种章节包裹元素。
        var node: Element? = anchor
        var section: Element = anchor
        while (node != null && node !== container) {
            if (countAnchors(node, anchors) > 1) break
            section = node
            node = node.parent()
        }
        if (section === anchor) {
            section.parent()?.takeIf { it !== container }?.let { section = it }
        }
        return applySelectorRegex(cleanContainer(section, rule), rule.selector)
    }

    /** 正文规则尾部可能带 `##正则##替换`（Legado 语法），抽完正文再按它清洗一遍 */
    private fun applySelectorRegex(text: String, selector: String): String {
        val rs = LegadoSelector.regexReplacements(selector)
        if (rs.isEmpty()) return text
        var r = text
        rs.forEach { (rx, rep) -> r = runCatching { r.replace(Regex(rx), rep) }.getOrDefault(r) }
        return r
    }

    private fun countAnchors(element: Element, anchors: Set<String>): Int {
        var count = 0
        runCatching { element.select("[id]") }.getOrNull()?.forEach {
            if (it.id() in anchors) count++
        }
        runCatching { element.select("a[name]") }.getOrNull()?.forEach {
            if (it.attr("name") in anchors) count++
        }
        return count
    }

    private fun cleanContainer(container: Element, rule: ContentRule): String {
        container.clone().let { copy ->
            (DEFAULT_REMOVE + rule.remove).forEach { selector ->
                runCatching { copy.select(selector).remove() }
            }
            copy.select("[style]").forEach { it.removeAttr("style") }
            copy.select("[onclick]").forEach { it.removeAttr("onclick") }
            applyImageStyle(copy, rule.imageStyle)
            var html = copy.html()
            rule.replace.forEach { pair ->
                html = runCatching { html.replace(Regex(pair.from), pair.to) }.getOrDefault(html)
            }
            return cleanText(html)
        }
    }

    /**
     * 正文图片处理（对应 Legado 的 imageStyle）。
     * 阅读器目前按纯文本分页排版，所以图片统一转成它的 alt/title 文字，避免出现 [obj] 占位：
     *  - FULL        → 独占一行（<p>）
     *  - TEXT / 其他 → 内联文字（<span>）
     *  - 没有 alt    → 直接去掉
     */
    private fun applyImageStyle(root: Element, style: String) {
        val imgs = runCatching { root.select("img") }.getOrNull() ?: return
        imgs.forEach { img ->
            val alt = img.attr("alt").ifBlank { img.attr("title") }.trim()
            if (alt.isEmpty()) {
                img.remove()
            } else if (style.equals("FULL", ignoreCase = true)) {
                img.replaceWith(Element("p").also { it.appendText(alt) })
            } else {
                img.replaceWith(Element("span").also { it.appendText(alt) })
            }
        }
    }

    /** 去掉正文里常见的推广话术 */
    fun cleanText(html: String): String {
        var result = html
        val noise = listOf(
            "本章未完，请点击下一页继续阅读",
            "请记住本站网址",
            "手机阅读请访问",
            "免费小说尽在",
            "笔趣阁",
            "小说网",
            "章节内容错误.*",
            "最新网址：.*"
        )
        noise.forEach { result = result.replace(Regex(it), "") }
        return result
            .replace(Regex("(<br>\\s*){3,}"), "<br><br>")
            .trim()
    }
}
