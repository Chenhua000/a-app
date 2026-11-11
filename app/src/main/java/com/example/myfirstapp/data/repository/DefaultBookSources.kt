package com.example.myfirstapp.data.repository

import com.example.myfirstapp.data.local.BookSourceEntity
import com.example.myfirstapp.domain.BookRules
import com.example.myfirstapp.domain.BookRulesCodec
import com.example.myfirstapp.domain.ContentRule
import com.example.myfirstapp.domain.ReplacePair
import com.example.myfirstapp.domain.SearchRule
import com.example.myfirstapp.domain.TocRule
import org.json.JSONArray
import org.json.JSONObject

/**
 * 内置书源。这里只放**公共领域 / 明确允许转载**的站点（例如维基文库收录的四大小说、古典著作）。
 *
 * 想加更多书源：在 App 里点「小说 → 书源 → 导入内置书源包 / 导入」，
 * 粘贴任意书源 JSON 即可，格式与下面完全一致（也兼容大部分「阅读 / Legado」风格的规则写法）。
 */
object DefaultBookSources {

    /**
     * assets/book_sources.json 里的书源条数，仅用于界面上的提示文案。
     * 重新生成书源文件后记得同步这个数字（导入结果以实际导入条数为准）。
     */
    const val bundledCountHint = 58

    private fun wikisource(id: String, name: String, variant: String, order: Int) =
        BookSourceEntity(
            id = id,
            name = name,
            baseUrl = "https://zh.wikisource.org",
            searchUrl = "https://zh.wikisource.org/w/index.php?search={{key}}&fulltext=1&ns0=1&limit=50&variant=$variant",
            charset = "utf-8",
            builtIn = true,
            sortOrder = order,
            rules = BookRulesCodec.toJson(
                BookRules(
                    search = SearchRule(
                        list = ".mw-search-results li",
                        name = ".mw-search-result-heading a@text",
                        author = "",
                        cover = "",
                        summary = ".searchresult@text",
                        url = ".mw-search-result-heading a@href"
                    ),
                    toc = TocRule(
                        list = "#mw-content-text .mw-parser-output ol li a||#mw-content-text li a[href*=/wiki/]",
                        name = "@text",
                        url = "@href",
                        contains = "/wiki/"
                    ),
                    content = ContentRule(
                        selector = "#mw-content-text .mw-parser-output",
                        remove = listOf(
                            ".mw-editsection",
                            "#toc",
                            "table.infobox",
                            ".reference",
                            ".mw-empty-elt",
                            ".printfooter"
                        )
                    )
                )
            )
        )

    /**
     * 古腾堡计划（Project Gutenberg）：7 万+ 公共领域电子书，含部分中文古籍译本。
     *
     * 结构要点：书页（/ebooks/11）本身没有章节，只有一个「在线阅读」按钮，
     * 指向 HTML 全本（/cache/epub/11/pg11-images.html）；真正的章节在这个全本里，
     * 靠 `<div class="chapter">` + `<a id="chap01">` 锚点分段，目录是 `<tr><a class="pginternal" href="#chap01">`。
     * 所以这里先用 pageUrl 跳到全本，再按锚点切章，正文也按锚点只取本章。
     */
    private fun gutenberg() = BookSourceEntity(
        id = "gutenberg",
        name = "古腾堡计划（公共领域·实测可达）",
        baseUrl = "https://www.gutenberg.org",
        searchUrl = "https://www.gutenberg.org/ebooks/search/?query={{key}}",
        charset = "utf-8",
        builtIn = true,
        sortOrder = 0,
        rules = BookRulesCodec.toJson(
            BookRules(
                search = SearchRule(
                    list = "li.booklink",
                    name = "span.title@text",
                    author = "span.subtitle@text",
                    cover = "img.cover-thumb@src",
                    summary = "",
                    url = "a.link@href"
                ),
                toc = TocRule(
                    // 书页 → 在线阅读（HTML 全本）
                    pageUrl = "a.read-online-button@href||a[href*=cache/epub]@href||a[href$=-images.html]@href",
                    // 目录表格里的每一行；拿不到表格时退回所有页内锚点链接
                    list = "tr:has(a.pginternal)||a.pginternal[href^=#]",
                    name = "@text",
                    url = "a[href]@href||@href",
                    contains = "#"
                ),
                content = ContentRule(
                    selector = "body",
                    remove = listOf(
                        "script", "style", "nav", "header",
                        ".pg-boilerplate", "#pg-start-separator", ".toc", "table"
                    )
                )
            )
        )
    )

    fun sources(): List<BookSourceEntity> = listOf(
        gutenberg(),
        wikisource("wikisource-cn", "维基文库·简体（境外站点，需网络可达）", "zh-cn", 1),
        wikisource("wikisource-tw", "维基文库·繁体（境外站点，需网络可达）", "zh-tw", 2)
    )

    /**
     * 给用户的完整示例：复制出去改掉 name / searchUrl 和 rules 里的选择器即可，
     * 也可以把多个这样的对象放进一个数组里一次导入几百个。
     */
    fun templateJson(): String {
        val rules = BookRules(
            search = SearchRule(
                list = ".result-list li",
                name = ".title a@text",
                author = ".author@text",
                cover = ".cover img@src",
                summary = ".desc@text",
                url = ".title a@href"
            ),
            toc = TocRule(
                list = ".chapter-list li a",
                name = "@text",
                url = "@href"
            ),
            content = ContentRule(
                selector = "#content",
                remove = listOf(".ad", "script"),
                replace = listOf(
                    ReplacePair("本章未完.*", ""),
                    ReplacePair("请记住本站网址.*", "")
                )
            )
        )
        val source = JSONObject().apply {
            put("name", "示例书源")
            put("baseUrl", "https://example.com")
            put("searchUrl", "https://example.com/search?q={{key}}")
            put("charset", "utf-8")
            put("rules", JSONObject(BookRulesCodec.toJson(rules)))
        }
        return JSONArray().apply { put(source) }.toString(2)
    }
}
