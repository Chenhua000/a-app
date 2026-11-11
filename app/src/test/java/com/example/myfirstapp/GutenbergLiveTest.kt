package com.example.myfirstapp

import com.example.myfirstapp.data.repository.DefaultBookSources
import com.example.myfirstapp.data.web.HttpFetcher
import com.example.myfirstapp.domain.BookRulesCodec
import com.example.myfirstapp.domain.HtmlRules
import org.junit.Assume
import org.junit.Test

/** 临时诊断：用 App 同款 UA 实抓古腾堡搜索页，打印响应与解析数量。 */
class GutenbergLiveTest {

    @Test
    fun diagnoseGutenbergSearch() {
        val source = DefaultBookSources.sources().first { it.id == "gutenberg" }
        val rules = BookRulesCodec.fromJson(source.rules)!!
        val url = source.searchUrl.replace("{{key}}", "alice")
        val html = try {
            HttpFetcher.get(url, source.charset)
        } catch (error: Exception) {
            Assume.assumeNoException("无网络，跳过", error)
            return
        }
        println("== 响应长度: ${html.length}")
        println("== 是否含 booklink: ${html.contains("booklink")}")
        println("== 前 400 字符: ${html.take(400)}")
        val doc = HtmlRules.parse(html, source.baseUrl)
        val items = HtmlRules.selectList(doc, rules.search.list)
        println("== 解析出条目数: ${items.size}")
        items.take(3).forEach {
            println("== 条目: ${HtmlRules.firstText(it, rules.search.name, source.baseUrl)}")
        }
    }
}
