package com.example.myfirstapp

import com.example.myfirstapp.domain.HtmlRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「阅读 / Legado」风格选择器的解析与求值。
 * 用例里的规则全部来自真实书源配置，确保网上下载的源能被正确执行。
 */
class LegadoSelectorTest {

    private fun doc(html: String) = HtmlRules.parse(html, "https://example.com")

    private val listHtml = """
        <html><body>
          <div id="searchmain">
            <div class="ad">广告条</div>
            <div class="c_row">
              <a class="name" href="/book/1.html">斗破苍穹</a>
              <span class="author">天蚕土豆</span>
            </div>
            <div class="c_row">
              <a class="name" href="/book/2.html">武动乾坤</a>
              <span class="author">天蚕土豆</span>
            </div>
            <div class="c_row">
              <a class="name" href="/book/3.html">大主宰</a>
              <span class="author">天蚕土豆</span>
            </div>
          </div>
        </body></html>
    """.trimIndent()

    @Test
    fun idAndTagKeywordSelectorsWork() {
        val root = doc(listHtml)
        // id.searchmain@tag.div → 先找 #searchmain，再取里面的 div
        val divs = HtmlRules.selectList(root, "id.searchmain@tag.div")
        assertEquals(4, divs.size) // 广告条 + 3 条结果

        // !0 排除第一个（去掉广告条）
        val rows = HtmlRules.selectList(root, "id.searchmain@tag.div!0")
        assertEquals(3, rows.size)
        assertEquals("斗破苍穹", rows.first().text().take(4))
    }

    @Test
    fun classKeywordAndIndexWork() {
        val root = doc(listHtml)
        // class.c_row@tag.a.0 → 第一条结果里的第一个 a
        val first = HtmlRules.firstText(root, "class.c_row@tag.a.0@text", "https://example.com")
        assertEquals("斗破苍穹", first)

        // 取链接
        val href = HtmlRules.firstText(root, "class.c_row@tag.a.0@href", "https://example.com")
        assertEquals("https://example.com/book/1.html", href)

        // 负下标：最后一个
        val last = HtmlRules.firstText(root, "class.c_row@tag.a.-1@text", "https://example.com")
        assertEquals("大主宰", last)

        // 区间：前两个（下标直接挂在选择器后面）
        assertEquals(2, HtmlRules.selectList(root, "class.c_row.0:1").size)
    }

    @Test
    fun standardCssStillWorks() {
        val root = doc(listHtml)
        assertEquals(3, HtmlRules.selectList(root, ".c_row").size)
        assertEquals("斗破苍穹", HtmlRules.firstText(root, ".c_row a@text", "https://example.com"))
        assertEquals("天蚕土豆", HtmlRules.firstText(root, ".c_row .author@text", "https://example.com"))
    }

    @Test
    fun fallbackAndAttributeOnlyRulesWork() {
        val root = doc("""<div class="x"><a href="/b/9.html">书名</a></div>""")
        // || 备用：第一条找不到就退到第二条
        assertEquals("书名", HtmlRules.firstText(root, ".not-here a@text||.x a@text", "https://example.com"))
        // 只有属性、没有选择器：取当前元素自己
        val anchor = HtmlRules.selectList(root, ".x a").first()
        assertEquals("https://example.com/b/9.html", HtmlRules.firstText(anchor, "@href", "https://example.com"))
        assertEquals("", HtmlRules.firstText(root, "", "https://example.com"))
    }

    @Test
    fun textNodesAndHtmlAttributesWork() {
        val html = """
            <div id="content">
              <p>第一段正文。</p>
              <p>第二段正文。</p>
              <script>bad()</script>
            </div>
        """.trimIndent()
        val root = doc(html)
        val textNodes = HtmlRules.firstText(root, "id.content@textNodes", "https://example.com")
        assertTrue(textNodes.contains("第一段正文"))
        assertTrue(textNodes.contains("第二段正文"))
        assertTrue("脚本不应出现在 textNodes 里", !textNodes.contains("bad()"))

        val inner = HtmlRules.firstText(root, "id.content@html", "https://example.com")
        assertTrue(inner.contains("<p>第一段正文。</p>"))
    }

    @Test
    fun exclusionDoesNotBreakCssNotSelector() {
        // [href!=x] 里的 ! 不能被当成 Legado 的排除语法
        val root = doc("""<a href="/keep.html">留着</a><a href="/drop.html">丢掉</a>""")
        assertEquals(1, HtmlRules.selectList(root, "a[href!=/drop.html]").size)
        assertEquals("留着", HtmlRules.firstText(root, "a[href!=/drop.html]@text", "https://example.com"))
    }

    @Test
    fun contentRuleWithRegexSuffixAndNestedTipWorks() {
        // 真实场景：<div id="content"> 里先有个 <div id="content_tip"> 提示，再跟正文；
        // 正文规则写成 `id.content@textNodes##正则`（Legado 的 ## 替换语法）。
        // 旧实现不认 ##，会把「正则」当成一段 CSS → 整条规则失效 → 正文空白。
        val html = """
            <div class="content">
              <div id="content" class="showtxt">
                <div id="content_tip"><b>最新网址：www.4020xs.com</b></div>
                “嘶这究竟是怎么回事？！”李火旺死死咬着牙。这里是一段足够长的真实正文，用来确认抽取正确无误。
              </div>
            </div>
        """.trimIndent()
        val root = doc(html)
        val rule = "id.content@textNodes##最新网址：.*"
        assertEquals("带 ## 的规则应选中 #content（而不是 #content_tip）", 1, HtmlRules.selectList(root, rule).size)
        val text = HtmlRules.extractContent(
            root,
            com.example.myfirstapp.domain.ContentRule(selector = rule)
        )
        assertTrue("应抽到真实正文", text.contains("李火旺"))
        assertTrue("提示行应被 ## 正则清掉", !text.contains("最新网址"))
    }

    @Test
    fun contentExtractionUsesLegadoSelectors() {
        val html = """
            <html><body>
              <div class="sidebar">目录</div>
              <div id="content"><p>正文第一段。</p><p>正文第二段。</p></div>
            </body></html>
        """.trimIndent()
        val root = doc(html)
        val content = HtmlRules.extractContent(
            root,
            com.example.myfirstapp.domain.ContentRule(selector = "id.content@html")
        )
        assertTrue(content.contains("正文第一段"))
        assertTrue("不该取到侧边栏", !content.contains("目录"))
    }
}
