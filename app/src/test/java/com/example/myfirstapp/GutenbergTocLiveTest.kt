package com.example.myfirstapp

import com.example.myfirstapp.data.repository.DefaultBookSources
import com.example.myfirstapp.data.web.HttpFetcher
import com.example.myfirstapp.domain.BookRulesCodec
import com.example.myfirstapp.domain.HtmlRules
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * 古腾堡目录解析实测：书页 → 在线阅读全本 → 目录锚点 → 单章正文。
 * 没网时自动跳过（Assume），不会让 CI 变红。
 */
class GutenbergTocLiveTest {

    private val bookPage = "https://www.gutenberg.org/ebooks/11" // Alice in Wonderland

    @Test
    fun tocAndChapterSectionsWork() {
        val source = DefaultBookSources.sources().first { it.id == "gutenberg" }
        val rules = BookRulesCodec.fromJson(source.rules)!!

        val bookHtml = try {
            HttpFetcher.get(bookPage, source.charset)
        } catch (error: Exception) {
            Assume.assumeNoException("无网络，跳过", error)
            return
        }
        val bookDoc = HtmlRules.parse(bookHtml, bookPage)

        // 1) 书页 → 在线阅读全本
        val entry = HtmlRules.firstText(bookDoc, rules.toc.pageUrl, bookPage)
        println("== 目录入口: $entry")
        assertTrue("书页上应该能找到在线阅读入口", entry.contains("/cache/epub/"))

        val htmlDoc = HtmlRules.parse(HttpFetcher.get(entry, source.charset), entry)

        // 2) 全本页 → 章节列表（页内锚点）
        val chapters = mutableListOf<Pair<String, String>>()
        HtmlRules.selectList(htmlDoc, rules.toc.list).forEach { element ->
            val title = HtmlRules.firstText(element, rules.toc.name, entry).trim()
            val url = HtmlRules.firstText(element, rules.toc.url, entry)
            if (title.isEmpty() || url.isEmpty()) return@forEach
            if (!url.contains("#")) return@forEach
            if (chapters.any { it.second == url }) return@forEach
            chapters.add(title to url)
        }
        println("== 章节数: ${chapters.size}")
        chapters.take(3).forEach { println("==   ${it.first} -> ${it.second}") }
        assertTrue("目录至少要有 5 章，实际 ${chapters.size}", chapters.size >= 5)
        assertTrue("第一章地址应带锚点", chapters.first().second.contains("#chap01"))

        // 3) 按锚点只取这一章
        val anchors = chapters.map { it.second.substringAfter('#') }.toSet()
        val chapterTwo = anchors.elementAt(1)
        val content = HtmlRules.extractContent(htmlDoc, rules.content, chapterTwo, anchors)
        val plain = content.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
        println("== 第 2 章（$chapterTwo）正文长度: ${plain.length}")
        println("== 开头: ${plain.take(160)}")
        assertTrue("第 2 章正文不该为空", plain.length > 500)
        assertTrue("第 2 章不该混进第 4 章内容", !plain.contains("CHAPTER IV"))
        assertTrue("整本书（>200000 字符）不该被当成一章返回", plain.length < 40000)
    }
}
