package com.example.myfirstapp

import com.example.myfirstapp.domain.LocalBookParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 本地 TXT / EPUB 解析（不依赖 Android，可直接在 JVM 上跑） */
class LocalBookParserTest {

    @Test
    fun txtSplitsByChineseChapterHeadings() {
        val text = """
            序章 起点
            一切从这里开始。
            第一章 下山
            他背起行囊，走下山去。
            第二章 入城
            城里人来人往。
        """.trimIndent()
        val book = LocalBookParser.parseTxt(text, "测试小说.txt")
        assertEquals("测试小说", book.title)
        assertEquals("TXT", book.format)
        assertEquals(3, book.chapters.size)
        assertEquals("序章 起点", book.chapters[0].title)
        assertEquals("第一章 下山", book.chapters[1].title)
        assertTrue(book.chapters[1].content.contains("他背起行囊"))
        assertTrue(book.chapters[2].content.contains("城里人来人往"))
        assertTrue("章节之间不应串内容", !book.chapters[1].content.contains("城里人来人往"))
    }

    @Test
    fun txtKeepsPreambleAndHandlesNoHeadings() {
        val preamble = "作者：某人\n这是一本没有分章的书。\n只有正文内容。"
        val book = LocalBookParser.parseTxt(preamble, "随便.txt")
        assertEquals(1, book.chapters.size)
        assertEquals("全文", book.chapters.first().title)
        assertTrue(book.chapters.first().content.contains("只有正文内容"))

        val withPreamble = LocalBookParser.parseTxt("写在前面的话。\n第一章 开始\n正文", "b.txt")
        assertEquals("正文前", withPreamble.chapters.first().title)
        assertEquals(2, withPreamble.chapters.size)
    }

    @Test
    fun txtKeepsProseThatStartsWithChapterWord() {
        // 回归：「第二章的正文。」是以「第二章」开头的正文句，不是标题，
        // 一旦误判就会把真正的「第二章 发展」整章吞掉
        val text = "第一章 开始\n这是第一章的正文内容。\n第二章 发展\n第二章的正文。\n第三章 结局\n全文完。"
        val book = LocalBookParser.parseTxt(text, "t.txt")
        assertEquals(3, book.chapters.size)
        assertEquals("第二章 发展", book.chapters[1].title)
        assertTrue(book.chapters[1].content.contains("第二章的正文"))
    }

    @Test
    fun txtSupportsEnglishChapterHeadings() {
        val book = LocalBookParser.parseTxt(
            "Chapter 1\nhello world\nChapter 2\nsecond part",
            "story.txt"
        )
        assertEquals(2, book.chapters.size)
        assertEquals("Chapter 1", book.chapters[0].title)
        assertTrue(book.chapters[1].content.contains("second part"))
    }

    @Test
    fun epubReadsSpineAndNcxTitles() {
        val epub = zipOf(
            "META-INF/container.xml" to """
                <?xml version="1.0"?>
                <container><rootfiles>
                  <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles></container>
            """.trimIndent(),
            "OEBPS/content.opf" to """
                <?xml version="1.0"?>
                <package xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <metadata>
                    <dc:title>本地测试书</dc:title>
                    <dc:creator>测试作者</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="c2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                  </manifest>
                  <spine toc="ncx">
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                  </spine>
                </package>
            """.trimIndent(),
            "OEBPS/toc.ncx" to """
                <?xml version="1.0"?>
                <ncx><navMap>
                  <navPoint id="n1"><navLabel><text>第一章 起</text></navLabel><content src="c1.xhtml"/></navPoint>
                  <navPoint id="n2"><navLabel><text>第二章 承</text></navLabel><content src="c2.xhtml"/></navPoint>
                </navMap></ncx>
            """.trimIndent(),
            "OEBPS/c1.xhtml" to "<html><body><h1>第一章 起</h1><p>正文一</p><script>bad()</script></body></html>",
            "OEBPS/c2.xhtml" to "<html><body><h1>第二章 承</h1><p>正文二</p></body></html>"
        )
        val book = LocalBookParser.parseEpub(epub, "book.epub")
        assertEquals("本地测试书", book.title)
        assertEquals("测试作者", book.author)
        assertEquals("EPUB", book.format)
        assertEquals(2, book.chapters.size)
        assertEquals("第一章 起", book.chapters[0].title)
        assertEquals("第二章 承", book.chapters[1].title)
        assertTrue(book.chapters[0].content.contains("正文一"))
        assertTrue("脚本应被剔除", !book.chapters[0].content.contains("bad()"))
        assertTrue(book.chapters[1].content.contains("正文二"))
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
