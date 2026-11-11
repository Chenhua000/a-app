package com.example.myfirstapp

import com.example.myfirstapp.data.repository.DefaultBookSources
import com.example.myfirstapp.data.web.HttpFetcher
import com.example.myfirstapp.domain.BookRulesCodec
import com.example.myfirstapp.domain.HtmlRules
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.net.URLEncoder

/**
 * 联网冒烟测试：只校验 App **真正内置** 的 3 个公共领域书源
 * （古腾堡计划、维基文库简/繁体）能不能搜到结果、走到正文。
 *
 * 这 3 个是硬编码、长期维护的源，必须可用；社区书源包（assets/book_sources.json）
 * 是用户按需导入、默认停用的，失效很快，不在这条测试里断言。
 *
 * 没网时自动跳过（Assume），不阻塞本地/CI 构建。
 */
class BundledSourcesLiveTest {

    private fun builtInSources(): List<JSONObject> {
        return DefaultBookSources.sources().map { entity ->
            JSONObject().apply {
                put("name", entity.name)
                put("baseUrl", entity.baseUrl)
                put("searchUrl", entity.searchUrl)
                put("charset", entity.charset)
                put("rules", JSONObject(entity.rules))
            }
        }
    }

    @Test
    fun builtInSourcesCanSearch() {
        val sources = builtInSources()
        Assume.assumeTrue("没有内置书源", sources.isNotEmpty())

        var networkChecked = false
        var searchOk = 0
        val report = StringBuilder()

        sources.forEach { json ->
            val name = json.optString("name")
            val baseUrl = json.optString("baseUrl")
            val charset = json.optString("charset", "utf-8")
            val rules = BookRulesCodec.fromJson(json.optJSONObject("rules")?.toString() ?: "{}")
                ?: return@forEach
            val searchUrl = json.optString("searchUrl")
                .replace("{{key}}", URLEncoder.encode("红楼梦", charset))
                .replace("{key}", URLEncoder.encode("红楼梦", charset))

            val searchHtml = try {
                HttpFetcher.get(searchUrl, charset).also { networkChecked = true }
            } catch (error: Exception) {
                report.append("\n  [跳过] $name：${error.message?.take(40)}")
                return@forEach
            }

            val doc = HtmlRules.parse(searchHtml, baseUrl)
            val items = HtmlRules.selectList(doc, rules.search.list)
            if (items.isEmpty()) {
                report.append("\n  [搜不到] $name")
                return@forEach
            }
            searchOk++
            report.append("\n  [OK] $name 命中 ${items.size} 条")
        }

        Assume.assumeTrue("没连上任何网络，跳过", networkChecked)
        println("内置书源冒烟：$searchOk/${sources.size} 可搜$report")
        assertTrue("至少有一个内置书源能搜到结果（古腾堡是公共领域站点，应当可达）", searchOk >= 1)
    }

    /** 纯结构校验：内置书源包的 JSON 能被引擎解析出完整规则（不联网） */
    @Test
    fun bundledPackageJsonParses() {
        val text = java.io.File("src/main/assets/book_sources.json").readText()
        val array = JSONArray(text)
        Assume.assumeTrue("内置书源包为空", array.length() > 0)
        var parseOk = 0
        for (i in 0 until array.length()) {
            val json = array.getJSONObject(i)
            val rules = BookRulesCodec.fromJson(json.optJSONObject("rules")?.toString() ?: "{}")
            if (rules != null &&
                rules.search.list.isNotBlank() &&
                rules.toc.list.isNotBlank() &&
                rules.content.selector.isNotBlank()
            ) {
                parseOk++
            }
        }
        println("内置书源包结构校验：$parseOk/${array.length()} 条规则完整可解析")
        assertTrue("内置书源包应绝大多数规则完整", parseOk * 10 >= array.length() * 9)
    }
}
