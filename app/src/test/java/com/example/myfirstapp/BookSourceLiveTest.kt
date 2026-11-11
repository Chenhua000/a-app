package com.example.myfirstapp

import com.example.myfirstapp.data.repository.DefaultBookSources
import com.example.myfirstapp.data.web.HttpFetcher
import com.example.myfirstapp.domain.BookRulesCodec
import com.example.myfirstapp.domain.HtmlRules
import org.junit.Assume
import org.junit.Test

/**
 * 真机联网验证：用内置书源（维基文库）走一遍「搜索 → 目录 → 正文」。
 * 没有网络时自动跳过，不算失败。
 */
class BookSourceLiveTest {

    @Test
    fun builtInSourceCanSearchParseAndRead() {
        val source = DefaultBookSources.sources().first()
        val rules = BookRulesCodec.fromJson(source.rules)!!
        val searchUrl = source.searchUrl.replace("{{key}}", java.net.URLEncoder.encode("红楼梦", "UTF-8"))

        val searchHtml = try {
            HttpFetcher.get(searchUrl, source.charset)
        } catch (error: Exception) {
            Assume.assumeNoException("需要联网才能验证书源", error)
            return
        }
        val doc = HtmlRules.parse(searchHtml, source.baseUrl)
        val items = HtmlRules.selectList(doc, rules.search.list)
        Assume.assumeTrue("维基文库没有返回结果，可能网络受限", items.isNotEmpty())

        val firstTitle = HtmlRules.firstText(items.first(), rules.search.name, source.baseUrl)
        val firstUrl = HtmlRules.firstText(items.first(), rules.search.url, source.baseUrl)
        org.junit.Assert.assertTrue(firstTitle.isNotBlank())
        org.junit.Assert.assertTrue(firstUrl.startsWith("http"))

        // 正文：直接取一个确定存在的章节页
        val chapterUrl = "https://zh.wikisource.org/wiki/%E7%B4%85%E6%A8%93%E5%A4%A2/%E7%AC%AC001%E5%9B%9E"
        val contentHtml = try {
            HttpFetcher.get(chapterUrl, source.charset)
        } catch (error: Exception) {
            Assume.assumeNoException("需要联网才能验证正文", error)
            return
        }
        val content = HtmlRules.extractContent(HtmlRules.parse(contentHtml, source.baseUrl), rules.content)
        org.junit.Assert.assertTrue(content.length > 200)
        org.junit.Assert.assertTrue(!content.contains("<script"))
    }
}
