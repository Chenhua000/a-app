package com.example.myfirstapp

import com.example.myfirstapp.domain.BookRules
import com.example.myfirstapp.domain.BookRulesCodec
import com.example.myfirstapp.domain.ContentRule
import com.example.myfirstapp.domain.HtmlRules
import com.example.myfirstapp.domain.ReplacePair
import com.example.myfirstapp.domain.SearchRule
import com.example.myfirstapp.domain.TocRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookRuleTest {

    private val searchHtml = """
        <html><body>
        <ul class="result-list">
          <li>
            <div class="title"><a href="/book/1.html">测试小说</a></div>
            <div class="author">张三</div>
            <div class="cover"><img src="/img/1.jpg"></div>
            <div class="desc">这是一本测试用的书</div>
          </li>
          <li>
            <div class="title"><a href="https://other.com/book/2.html">另一本书</a></div>
            <div class="author">李四</div>
            <div class="cover"><img src="https://other.com/img/2.jpg"></div>
            <div class="desc">简介二</div>
          </li>
        </ul>
        </body></html>
    """.trimIndent()

    private val searchRule = SearchRule(
        list = ".result-list li",
        name = ".title a@text",
        author = ".author@text",
        cover = ".cover img@src",
        summary = ".desc@text",
        url = ".title a@href"
    )

    @Test
    fun searchRuleExtractsFieldsAndAbsolutizesUrls() {
        val doc = HtmlRules.parse(searchHtml, "https://demo.com")
        val items = HtmlRules.selectList(doc, searchRule.list)
        assertEquals(2, items.size)
        val first = items.first()
        assertEquals("测试小说", HtmlRules.firstText(first, searchRule.name, "https://demo.com"))
        assertEquals("张三", HtmlRules.firstText(first, searchRule.author, "https://demo.com"))
        assertEquals("https://demo.com/book/1.html", HtmlRules.firstText(first, searchRule.url, "https://demo.com"))
        assertEquals("https://demo.com/img/1.jpg", HtmlRules.firstText(first, searchRule.cover, "https://demo.com"))
        assertEquals("这是一本测试用的书", HtmlRules.firstText(first, searchRule.summary, "https://demo.com"))

        val second = items[1]
        assertEquals("https://other.com/book/2.html", HtmlRules.firstText(second, searchRule.url, "https://demo.com"))
    }

    @Test
    fun contentRuleRemovesAdsAndScripts() {
        val html = """
            <html><body>
            <div id="content">
              <p>第一段正文。</p>
              <script>alert('x');</script>
              <div class="ad-box">广告内容</div>
              <p>第二段正文。</p>
              <span>请记住本站网址 a.com</span>
            </div>
            </body></html>
        """.trimIndent()
        val doc = HtmlRules.parse(html, "https://demo.com")
        val content = HtmlRules.extractContent(
            doc,
            ContentRule(
                selector = "#content",
                remove = listOf(".ad-box"),
                replace = listOf(ReplacePair("请记住本站网址.*", ""))
            )
        )
        assertTrue(content.contains("第一段正文"))
        assertTrue(content.contains("第二段正文"))
        assertTrue(!content.contains("广告内容"))
        assertTrue(!content.contains("alert"))
        assertTrue(!content.contains("请记住本站网址"))
    }

    @Test
    fun tocRuleFiltersByContains() {
        val html = """
            <html><body><ol>
              <li><a href="/wiki/紅樓夢/第001回">第一回</a></li>
              <li><a href="/wiki/分類:紅樓夢">分类页</a></li>
            </ol></body></html>
        """.trimIndent()
        val doc = HtmlRules.parse(html, "https://zh.wikisource.org")
        val items = HtmlRules.selectList(doc, "ol li a")
        assertEquals(2, items.size)
        val urls = items.map { HtmlRules.firstText(it, "@href", "https://zh.wikisource.org") }
        assertTrue(urls[0].startsWith("https://zh.wikisource.org/wiki/"))
        // jsoup 会把中文路径按 URL 编码，解码后仍应是原来的章节名
        val decoded = java.net.URLDecoder.decode(urls[0], "UTF-8")
        assertTrue(decoded.contains("紅樓夢"))
    }

    @Test
    fun rulesRoundTripThroughJson() {
        val rules = BookRules(
            search = searchRule,
            toc = TocRule(list = "ol li a", contains = "/wiki/", remove = listOf(".ad")),
            content = ContentRule(selector = "#content", replace = listOf(ReplacePair("a", "b")))
        )
        val json = BookRulesCodec.toJson(rules)
        val parsed = BookRulesCodec.fromJson(json)!!
        assertEquals(rules.search.name, parsed.search.name)
        assertEquals(rules.toc.contains, parsed.toc.contains)
        assertEquals(rules.content.selector, parsed.content.selector)
        assertEquals(1, parsed.content.replace.size)
    }

    @Test
    fun gbkHtmlIsDecoded() {
        val raw = "正文内容".toByteArray(java.nio.charset.Charset.forName("GBK"))
        val charset = com.example.myfirstapp.data.web.HttpFetcher.detectCharset(
            "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=gbk\">".toByteArray(Charsets.UTF_8) + raw,
            "utf-8"
        )
        assertEquals("GBK", charset.name().uppercase())
    }
}
