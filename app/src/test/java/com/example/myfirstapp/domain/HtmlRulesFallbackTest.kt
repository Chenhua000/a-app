package com.example.myfirstapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 回归：书源目录/正文规则失效时，通用兜底仍能读出章节和正文 */
class HtmlRulesFallbackTest {

    private val base = "https://x.com/book/1/"

    @Test
    fun fallbackChaptersExtractsChapterLinksOnly() {
        val html = """
            <html><body>
              <div class="nav"><a href="/">首页</a><a href="/rank/">排行榜</a></div>
              <div class="list">
                <a href="/book/1/1.html">第一章 起始</a>
                <a href="/book/1/2.html">第二章 远行</a>
                <a href="/book/1/3.html">序章</a>
                <a href="/about.html">关于我们</a>
              </div>
            </body></html>
        """.trimIndent()
        val chapters = HtmlRules.fallbackChapters(HtmlRules.parse(html, base), base)
        assertEquals(3, chapters.size)
        assertEquals("第一章 起始", chapters[0].first)
        assertTrue(chapters.all { it.second.startsWith("https://x.com/") })
        assertTrue(chapters.none { it.first == "首页" || it.first == "排行榜" })
    }

    @Test
    fun fallbackChaptersDeduplicatesUrls() {
        val html = """
            <html><body>
              <a href="/book/1/1.html">第一章 起始</a>
              <a href="https://x.com/book/1/1.html">第一章 起始</a>
              <a href="/book/1/2.html">第二章 远行</a>
            </body></html>
        """.trimIndent()
        val chapters = HtmlRules.fallbackChapters(HtmlRules.parse(html, base), base)
        assertEquals(2, chapters.size)
    }

    @Test
    fun fallbackContentUsedWhenSelectorMissing() {
        val body = "正文内容".repeat(100)
        val html = """
            <html><body>
              <div class="nav">导航导航导航</div>
              <div id="content"><p>$body</p></div>
            </body></html>
        """.trimIndent()
        val content = HtmlRules.extractContent(
            HtmlRules.parse(html, base),
            ContentRule(selector = "#not-exist")
        )
        assertTrue(content.contains("正文内容"))
        assertTrue(!content.contains("导航导航"))
    }

    @Test
    fun fallbackContentUsedWhenSelectorBlank() {
        val body = "章节正文".repeat(100)
        val html = "<html><body><article><p>$body</p></article></body></html>"
        val content = HtmlRules.extractContent(
            HtmlRules.parse(html, base),
            ContentRule() // selector 为空：模拟自动发现只推断了搜索规则
        )
        assertTrue(content.contains("章节正文"))
    }

    @Test
    fun directoryLinksAreFound() {
        val html = """
            <html><body>
              <a href="/">首页</a>
              <a href="/b/210467/catalog">点击阅读目录列表</a>
              <a href="/b/210467/about">关于我们</a>
            </body></html>
        """.trimIndent()
        val links = HtmlRules.findDirectoryLinks(HtmlRules.parse(html, base), base)
        assertEquals(1, links.size)
        assertTrue(links[0].endsWith("/catalog"))
    }

    @Test
    fun fallbackChaptersSkipsLinkToBookPageItself() {
        val html = """
            <html><body>
              <a href="https://x.com/book/1/">第一章 起始</a>
              <a href="/book/1/2.html">第二章 远行</a>
            </body></html>
        """.trimIndent()
        val chapters = HtmlRules.fallbackChapters(HtmlRules.parse(html, base), base)
        assertEquals(1, chapters.size)
        assertEquals("第二章 远行", chapters[0].first)
    }

    @Test
    fun errorPageIsNotTreatedAsContent() {
        // http→https 的 301 响应体曾被当成小说正文显示
        val html = "<html><body><h1>301 Moved Permanently</h1><p>nginx</p></body></html>"
        val content = HtmlRules.extractContent(HtmlRules.parse(html, base), ContentRule())
        assertTrue(content.isBlank())
    }
}
