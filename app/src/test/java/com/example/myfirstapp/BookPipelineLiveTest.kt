package com.example.myfirstapp

import com.example.myfirstapp.data.repository.DefaultBookSources
import com.example.myfirstapp.domain.BookRulesCodec
import com.example.myfirstapp.domain.HtmlRules
import org.jsoup.Jsoup
import org.junit.Assume
import org.junit.Test

/**
 * 真机/主机联网诊断：完整走一遍「搜索 → 目录 → 正文」，
 * 把每一环的结果打印出来，定位「能搜到但读不了」到底卡在哪一步。
 */
class BookPipelineLiveTest {

    private fun fetch(url: String): org.jsoup.nodes.Document? = runCatching {
        Jsoup.connect(url)
            .userAgent(HttpAgent)
            .timeout(25_000)
            .maxBodySize(0)
            .get()
    }.getOrNull()

    @Test
    fun pipelineWikisource() {
        val source = DefaultBookSources.sources().first { it.id == "wikisource-cn" }
        val rules = BookRulesCodec.fromJson(source.rules)!!
        val keyword = "紅樓夢"
        val searchUrl = source.searchUrl.replace("{{key}}", java.net.URLEncoder.encode(keyword, "UTF-8"))
        println("[WS] searchUrl=$searchUrl")
        val doc = fetch(searchUrl) ?: run { Assume.assumeTrue("无网络，跳过", false); return }
        val items = HtmlRules.selectList(doc, rules.search.list)
        println("[WS] 搜索条目数=${items.size}")
        if (items.isEmpty()) { println("[WS] 搜索无结果，可能站点结构变化或网络受限"); return }
        val title = HtmlRules.firstText(items.first(), rules.search.name, source.baseUrl)
        val bookUrl = HtmlRules.firstText(items.first(), rules.search.url, source.baseUrl)
        println("[WS] 首条 title=$title url=$bookUrl")

        // 目录
        val tocDoc = fetch(bookUrl)
        if (tocDoc == null) { println("[WS] 目录页抓取失败 url=$bookUrl"); return }
        val chapters = HtmlRules.selectList(tocDoc, rules.toc.list)
            .map { HtmlRules.firstText(it, rules.toc.url, source.baseUrl) to HtmlRules.firstText(it, rules.toc.name, source.baseUrl) }
            .filter { it.first.contains(rules.toc.contains) }
        println("[WS] 目录章节数=${chapters.size}")
        chapters.take(5).forEachIndexed { i, c -> println("[WS]   章$i url=${c.first} name=${c.second}") }
        if (chapters.isEmpty()) { println("[WS] 目录为空！TOC 规则可能已失效: list=${rules.toc.list}"); return }

        // 正文（取第一个章节）
        val (chapterUrl, chapterName) = chapters.first()
        val contentDoc = fetch(chapterUrl)
        if (contentDoc == null) { println("[WS] 正文页抓取失败 url=$chapterUrl"); return }
        val content = HtmlRules.extractContent(contentDoc, rules.content)
        println("[WS] 正文长度=${content.length} 章名=$chapterName")
        println("[WS] 正文前200字=${content.take(200)}")
        println("[WS] 含<script=${content.contains("<script")}")
        if (content.length < 200) println("[WS] ⚠ 正文过短，content 规则可能失效: selector=${rules.content.selector}")
    }

    @Test
    fun pipelineGutenberg() {
        val source = DefaultBookSources.sources().first { it.id == "gutenberg" }
        val rules = BookRulesCodec.fromJson(source.rules)!!
        val keyword = "alice"
        val searchUrl = source.searchUrl.replace("{{key}}", keyword)
        println("[GB] searchUrl=$searchUrl")
        val doc = fetch(searchUrl) ?: run { Assume.assumeTrue("无网络，跳过", false); return }
        val items = HtmlRules.selectList(doc, rules.search.list)
        println("[GB] 搜索条目数=${items.size}")
        if (items.isEmpty()) { println("[GB] 搜索无结果"); return }
        val bookUrl = HtmlRules.firstText(items.first(), rules.search.url, source.baseUrl)
        println("[GB] 首条 url=$bookUrl")

        val tocDoc = fetch(bookUrl)
        if (tocDoc == null) { println("[GB] 目录页抓取失败 url=$bookUrl"); return }
        val chapters = HtmlRules.selectList(tocDoc, rules.toc.list)
            .map { HtmlRules.firstText(it, rules.toc.url, source.baseUrl) to HtmlRules.firstText(it, rules.toc.name, source.baseUrl) }
            .filter { it.first.contains(rules.toc.contains) }
        println("[GB] 目录章节数=${chapters.size}")
        chapters.take(5).forEachIndexed { i, c -> println("[GB]   章$i url=${c.first} name=${c.second}") }
        if (chapters.isEmpty()) { println("[GB] 目录为空！TOC 规则可能失效: list=${rules.toc.list}"); return }

        val (chapterUrl, chapterName) = chapters.first()
        val contentDoc = fetch(chapterUrl)
        if (contentDoc == null) { println("[GB] 正文页抓取失败 url=$chapterUrl"); return }
        val content = HtmlRules.extractContent(contentDoc, rules.content)
        println("[GB] 正文长度=${content.length} 章名=$chapterName")
        println("[GB] 正文前200字=${content.take(200)}")
        println("[GB] 含<script=${content.contains("<script")}")
        if (content.length < 200) println("[GB] ⚠ 正文过短，content 规则可能失效: selector=${rules.content.selector}")
    }

    companion object {
        const val HttpAgent = "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
    }
}
