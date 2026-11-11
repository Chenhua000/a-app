package com.example.myfirstapp

import com.example.myfirstapp.data.repository.DefaultBookSources
import com.example.myfirstapp.domain.BookRulesCodec
import com.example.myfirstapp.domain.HtmlRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用真实抓取的古腾堡页面（fixtures）离线验证内置规则的解析正确性：
 * 搜索页能解析出书名/作者/链接，书籍页能解析出整书在线阅读地址。
 */
class GutenbergFixtureTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

    private fun gutenbergRules() = BookRulesCodec.fromJson(
        DefaultBookSources.sources().first { it.id == "gutenberg" }.rules
    )!!

    @Test
    fun searchPageParsesBookEntries() {
        val rules = gutenbergRules()
        val doc = HtmlRules.parse(fixture("gutenberg_search.html"), "https://www.gutenberg.org")
        val items = HtmlRules.selectList(doc, rules.search.list)
        assertTrue("搜索页应解析出多条结果", items.size >= 5)

        val first = items.first()
        val title = HtmlRules.firstText(first, rules.search.name, doc.location()).trim()
        val author = HtmlRules.firstText(first, rules.search.author, doc.location()).trim()
        val url = HtmlRules.firstText(first, rules.search.url, doc.location())
        val cover = HtmlRules.firstText(first, rules.search.cover, doc.location())

        assertEquals("Alice's Adventures in Wonderland", title)
        assertEquals("Lewis Carroll", author)
        assertTrue("书籍链接应指向 /ebooks/N，实际：$url", url.startsWith("https://www.gutenberg.org/ebooks/"))
        assertTrue("封面链接应为绝对地址，实际：$cover", cover.startsWith("https://"))
    }

    @Test
    fun bookPagePointsToOnlineEdition() {
        val rules = gutenbergRules()
        val doc = HtmlRules.parse(fixture("gutenberg_book.html"), "https://www.gutenberg.org")
        // 3.6.1 起目录规则改成两步：书页 → 在线阅读全本（pageUrl），章节在那一页里按锚点切
        val entry = HtmlRules.firstText(doc, rules.toc.pageUrl, doc.location())
        assertTrue("书籍页应能定位在线阅读全本，实际：$entry", entry.contains("/cache/epub/11/"))
        assertTrue("应指向 HTML 全本，实际：$entry", entry.endsWith("pg11-images.html"))
    }

    @Test
    fun onlineEditionSplitsByAnchors() {
        val rules = gutenbergRules()
        val pageUrl = "https://www.gutenberg.org/cache/epub/11/pg11-images.html"
        val doc = HtmlRules.parse(fixture("gutenberg_book_html.html"), pageUrl)

        val chapters = HtmlRules.selectList(doc, rules.toc.list).mapNotNull { element ->
            val title = HtmlRules.firstText(element, rules.toc.name, pageUrl).trim()
            val url = HtmlRules.firstText(element, rules.toc.url, pageUrl)
            if (title.isEmpty() || url.isEmpty()) null
            else if (rules.toc.contains.isNotBlank() && !url.contains(rules.toc.contains)) null
            else title to url
        }.distinctBy { it.second }

        assertEquals("目录表格里应有 12 章，实际：${chapters.size}", 12, chapters.size)
        assertEquals("CHAPTER I. Down the Rabbit-Hole", chapters.first().first)
        assertEquals("$pageUrl#chap01", chapters.first().second)

        // 按锚点只取第一章，不能把第二章一起塞进来
        val anchors = chapters.map { it.second.substringAfter('#') }.toSet()
        val content = HtmlRules.extractContent(doc, rules.content, "chap01", anchors)
        val plain = content.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")
        assertTrue("第一章正文不该为空", plain.length > 200)
        assertTrue("第一章不该混进第二章内容", !plain.contains("Pool of Tears"))
        assertTrue("第一章应包含开头段落", plain.contains("Alice was beginning"))
    }
}
