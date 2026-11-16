package com.example.myfirstapp

import com.example.myfirstapp.domain.BookRules
import com.example.myfirstapp.domain.BookRulesCodec
import com.example.myfirstapp.domain.ContentRule
import com.example.myfirstapp.domain.HtmlRules
import com.example.myfirstapp.domain.ReplacePair
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

    @Test
    fun contentFallbackPrefersRealTextOverLongChapterList() {
        // 小说站常见结构：「最新章节列表」字比正文还多。旧实现取文字最多的 div 会选错。
        val list = (1..40).joinToString("") { "<li><a href='/c/$it.html'>第${it}章 标题</a></li>" }
        val body = (1..12).joinToString("") { "<p>这是正文第${it}段，李火旺在清风观里慢慢回过神来，四周静得可怕。</p>" }
        val html = """
            <html><body>
              <div class="update-list"><ul>$list</ul></div>
              <div id="content" class="showtxt">$body</div>
            </body></html>
        """.trimIndent()
        val root = doc(html)
        val content = HtmlRules.fallbackContent(
            root,
            com.example.myfirstapp.domain.ContentRule(selector = "")
        )
        assertTrue("应抽到真实正文", content.contains("李火旺"))
        assertTrue("不该把最新章节列表当正文", !content.contains("第40章"))
        assertTrue("抽出的内容应可信", HtmlRules.isPlausibleContent(content))
    }

    @Test
    fun plausibleContentRejectsDirectoryAndShellPages() {
        val realBody = (1..10).joinToString("") { "<p>正文段落$it，这是一段足够长的小说内容描写。</p>" }
        assertTrue("正常正文应通过", HtmlRules.isPlausibleContent("<div id='content'>$realBody</div>"))
        val directory = (1..30).joinToString("") { "<a href='/c/$it'>第${it}章 标题</a>" }
        org.junit.Assert.assertFalse(
            "章节目录页不该被当正文",
            HtmlRules.isPlausibleContent("<div class='chapter-list'>$directory</div>")
        )
        org.junit.Assert.assertFalse("反爬空壳不该通过", HtmlRules.isPlausibleContent("<div>请稍后</div>"))
        // 站点说明/相关推荐页：字数不少、链接不多，但根本不是章节正文
        val notice = """
            <div id='content'>从武当派开始肝经验小说 相关
            ① 《从武当派开始肝经验》是佛手金卷所写的一本完结全本奇幻玄幻类的小说。
            ② 本站提供全本阅读的所有章节均为网友更新，属发布者个人行为，与全站立场无关。
            ③ 如果您发现章节有错误，请及时通知我们。您的热心是对我们最大的支持。
            ④ 如果您对全集的作品版权、内容等方有疑问请联系站长处理。</div>
        """.trimIndent()
        org.junit.Assert.assertFalse(
            "相关小说/免责声明页不该被当正文",
            HtmlRules.isPlausibleContent(notice)
        )
    }

    @Test
    fun contentRulesKeepPagingAndReplacePairs() {
        val rules = BookRules(
            content = ContentRule(
                selector = "#content@html",
                replace = listOf(ReplacePair("一秒记住.*免费阅读！", "")),
                nextContentUrl = "#pb_next@href"
            )
        )
        val back = BookRulesCodec.fromJson(BookRulesCodec.toJson(rules))!!
        assertEquals("#pb_next@href", back.content.nextContentUrl)
        assertEquals(1, back.content.replace.size)
        assertEquals("", back.content.replace[0].to)
    }

    @Test
    fun imageStyleTurnsImagesIntoAltText() {
        val html = "<div id='content'><p>正文第一段。</p>" +
            "<img alt='插图一' src='a.jpg'><img src='b.jpg'></div>"
        val text = HtmlRules.extractContent(
            doc(html),
            ContentRule(selector = "id.content@html", imageStyle = "FULL")
        )
        assertTrue("有 alt 的图片要保留文字", text.contains("插图一"))
        assertTrue("正文不该残留 img 标签", !text.contains("<img"))
    }

    @Test
    fun contentCodecKeepsJsAndImageStyle() {
        val rule = ContentRule(selector = "#c@html", jsContent = "@js:result", imageStyle = "TEXT")
        val back = BookRulesCodec.fromJson(BookRulesCodec.toJson(BookRules(content = rule)))!!
        assertEquals("@js:result", back.content.jsContent)
        assertEquals("TEXT", back.content.imageStyle)
    }

    @Test
    fun contentReplaceRegexRemovesSiteAds() {
        val html = "<div id='content'><p>正文第一段很长很长，李火旺慢慢回过神来。</p>" +
            "<p>一秒记住某某网免费阅读！</p></div>"
        val out = HtmlRules.extractContent(
            doc(html),
            ContentRule(
                selector = "id.content@html",
                replace = listOf(ReplacePair("一秒记住.*免费阅读！", ""))
            )
        )
        assertTrue("正文要保留", out.contains("李火旺"))
        assertTrue("站点广告要被清掉", !out.contains("一秒记住"))
    }
}
