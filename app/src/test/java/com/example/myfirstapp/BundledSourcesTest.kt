package com.example.myfirstapp

import com.example.myfirstapp.domain.BookRulesCodec
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 内置书源包的**离线**结构校验（不联网、不跳过的常规测试）：
 * 每条都必须能被 BookRulesCodec 解析、都有名字和搜索地址、搜索/目录规则不为空。
 * 联网的「能不能真的搜到书」由 VerifyBundledSourcesTest 负责（默认跳过）。
 */
class BundledSourcesTest {

    private val array = JSONArray(File("src/main/assets/book_sources.json").readText())

    @Test
    fun everySourceIsWellFormed() {
        assertTrue("内置书源包不能为空", array.length() > 0)
        val keys = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            val json = array.getJSONObject(i)
            val name = json.optString("name").trim()
            val searchUrl = json.optString("searchUrl").trim()
            val baseUrl = json.optString("baseUrl").trim()
            assertTrue("第 $i 条没有 name", name.isNotEmpty())
            assertTrue("$name 没有 searchUrl", searchUrl.isNotEmpty())
            assertTrue("$name 没有 baseUrl", baseUrl.isNotEmpty())
            assertTrue("$name 的 searchUrl 必须是 http(s) 且带 {{key}}",
                (searchUrl.contains("{{key}}") || searchUrl.startsWith("post::")))
            assertTrue("$name 的 baseUrl 不像网址：$baseUrl", baseUrl.startsWith("http"))
            // 同名同地址的两条会在导入后互相覆盖，纯属噪音
            assertTrue("$name 与 $searchUrl 重复出现了两次", keys.add("$name|$searchUrl"))

            val rules = BookRulesCodec.fromJson(json.optJSONObject("rules")?.toString() ?: "{}")
            assertTrue("$name 的 rules 解析失败", rules != null)
            assertTrue("$name 没有搜索列表规则", rules!!.search.list.isNotBlank())
            assertTrue("$name 没有目录规则", rules.toc.list.isNotBlank())
            assertTrue("$name 没有正文规则", rules.content.selector.isNotBlank())
        }
    }

    @Test
    fun hotSourcesAreSearchableFirst() {
        val hot = (0 until array.length()).map { array.getJSONObject(it) }
            .filter { it.optString("group") == "热门" }
        assertTrue("热门分组不能为空", hot.size >= 4)
        // 搜索只取 sortOrder 最靠前的 20 个源，热门源必须排在里面，否则用户启用也搜不到
        hot.forEach {
            val order = it.optInt("sortOrder", 100)
            assertTrue("${it.optString("name")} 的 sortOrder=$order 太靠后，会掉出搜索范围", order < 20)
        }
    }
}
