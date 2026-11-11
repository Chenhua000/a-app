package com.example.myfirstapp.data.web

import com.example.myfirstapp.domain.HtmlRules
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 离线单元测试：只验证「纯矿工」（findRepeatedAnchorList / mineBookList / mineToc / mineContent /
 * cssForElement）。它们吃 jsoup Document、吐选择器，不联网，所以能在 JVM 上直接跑。
 * 真机联调（POST / JS 站点）放在手机上，这里用合成 HTML 覆盖典型结构。
 */
class SourceDiscovererTest {

    private val base = "https://www.example.com"

    private fun doc(html: String) = HtmlRules.parse(html, base)

    @Test
    fun `mineBookList 从 ul-li-a 结构里识别出书列表`() {
        val html = """
            <html><body>
              <div id="search-result">
                <ul class="result-list">
                  <li><a href="/book/101.html">斗破苍穹</a> <span class="author">天蚕土豆</span></li>
                  <li><a href="/book/102.html">完美世界</a> <span class="author">辰东</span></li>
                  <li><a href="/book/103.html">诡秘之主</a> <span class="author">爱潜水的乌贼</span></li>
                  <li><a href="/book/104.html">三体</a> <span class="author">刘慈欣</span></li>
                </ul>
              </div>
              <div id="sidebar"><a href="/book/999.html">热门</a></div>
            </body></html>
        """.trimIndent()
        val d = doc(html)
        val rule = SourceDiscoverer.mineBookList(d, base, "斗破苍穹")
        assertNotNull("应该识别到书列表", rule)
        rule!!
        assertTrue("list 选择器应针对 li 条目", rule.list.contains("li"))
        assertEquals("条目下的书名取 a 文本", "a@text", rule.name)
        assertEquals("条目下的链接取 a href", "a@href", rule.url)

        val items = HtmlRules.selectList(d, rule.list)
        assertEquals("应只选中 4 个书条目（排除侧边栏热门）", 4, items.size)
        val first = items.first()
        assertEquals("斗破苍穹", HtmlRules.firstText(first, rule.name, base))
        assertEquals(
            "https://www.example.com/book/101.html",
            HtmlRules.firstText(first, rule.url, base)
        )
    }

    @Test
    fun `mineBookList 从 div-item-a 结构里识别出书列表`() {
        val html = """
            <html><body>
              <div class="list">
                <div class="item"><a href="/book/1001.html">书一</a></div>
                <div class="item"><a href="/book/1002.html">书二</a></div>
                <div class="item"><a href="/book/1003.html">书三</a></div>
              </div>
            </body></html>
        """.trimIndent()
        val d = doc(html)
        val rule = SourceDiscoverer.mineBookList(d, base, "书一")
        assertNotNull(rule)
        rule!!
        val items = HtmlRules.selectList(d, rule.list)
        assertEquals(3, items.size)
        assertEquals("书一", HtmlRules.firstText(items.first(), rule.name, base))
    }

    @Test
    fun `mineToc 从章节列表里识别出目录并算出 contains 过滤串`() {
        val html = """
            <html><body>
              <div id="chapter-list">
                <ul>
                  <li><a href="/book/101/1.html">第一章 退婚</a></li>
                  <li><a href="/book/101/2.html">第二章 修炼</a></li>
                  <li><a href="/book/101/3.html">第三章 突破</a></li>
                  <li><a href="/book/101/4.html">第四章 危机</a></li>
                </ul>
              </div>
            </body></html>
        """.trimIndent()
        val d = doc(html)
        val toc = SourceDiscoverer.mineToc(d, base, "utf-8")
        assertNotNull(toc)
        toc!!
        assertTrue(toc.list.contains("li"))
        // 所有章节链接共享 /book/101/ 前缀，应被提炼成 contains 过滤串
        assertEquals("/book/101/", toc.contains)
        val items = HtmlRules.selectList(d, toc.list)
        assertEquals(4, items.size)
        assertEquals("第一章 退婚", HtmlRules.firstText(items.first(), toc.name, base))
    }

    @Test
    fun `mineContent 选中正文容器并避开页脚`() {
        val para = "这是一段很长的中文正文内容，" +
                "用来凑够长度阈值以便被识别为内容容器而不是导航或页脚元素。" +
                "继续重复以增加文本长度确保超过二百字符的限制条件成立。" +
                "再写一些内容填充段落让正文的文本长度显著大于其它无关区块。" +
                "这一段必须足够长以保证算法把正文容器排在候选第一位。"
        val html = """
            <html><body>
              <nav><a href="/">首页</a><a href="/shelf">书架</a></nav>
              <div id="content">
                <p>$para</p>
                <p>$para</p>
                <p>$para</p>
              </div>
              <div id="footer">页脚广告与版权声明</div>
            </body></html>
        """.trimIndent()
        val d = doc(html)
        val content = SourceDiscoverer.mineContent(d, base)
        assertNotNull(content)
        // 应该选中正文容器 #content，而不是 nav / footer
        assertEquals("#content", content!!.selector)
    }

    @Test
    fun `cssForElement 优先返回 id 选择器`() {
        val d = Jsoup.parse("<div id=\"main\"><p>hi</p></div>", base)
        val el = d.getElementById("main")!!
        assertEquals("#main", SourceDiscoverer.cssForElement(el))
    }

    @Test
    fun `findRepeatedAnchorList 要求至少 3 个同类链接否则返回 null`() {
        val html = """
            <html><body>
              <ul><li><a href="/book/1.html">甲</a></li><li><a href="/x/2.html">乙</a></li></ul>
            </body></html>
        """.trimIndent()
        // 只有 2 个 book 锚点，应判定为不可靠而返回 null
        val d = doc(html)
        val pair = SourceDiscoverer.findRepeatedAnchorList(d) { a ->
            val h = a.attr("href").lowercase()
            h.contains("/book/") || Regex("/\\d{3,}/?$").containsMatchIn(h)
        }
        assertEquals(null, pair)
    }
}
