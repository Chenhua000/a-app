package com.example.myfirstapp

import com.example.myfirstapp.data.web.HttpFetcher
import com.example.myfirstapp.domain.BookRulesCodec
import com.example.myfirstapp.domain.HtmlRules
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 内置书源包的「体检器」——不是门禁测试，默认跳过；设 VERIFY_SOURCES=1 才跑。
 *
 * 用和 App 一模一样的管线（HttpFetcher + HtmlRules + BookRulesCodec），
 * 对每个源走「搜索（多个热门词，任一命中即算能搜到书）→ 目录（含 pageUrl 跳转）→ 正文」全链路，
 * 并发 16 路。把结果写到 build/ 下的 json：
 *   - verified_search.json：能搜到书的源（用户要的「保留」集合）
 *   - verified_full.json  ：搜索 + 目录 + 正文都能跑通的源（最稳）
 *   - failed_sources.json ：被淘汰的源及原因
 *
 * 社区书源失效极快，跑一遍就能筛出真正能用的，重新生成内置包。
 */
class VerifyBundledSourcesTest {

    /** 多个热门关键词，覆盖不同题材/年代，提高「能搜到书」的命中率 */
    private val keywords = listOf(
        "斗破苍穹", "红楼梦", "完美世界", "诛仙",
        "斗罗大陆", "三体", "雪中悍刀行", "庆余年",
        "平凡的世界", "围城", "神墓", "遮天",
        "诡秘之主", "凡人修仙传", "全职高手", "庶得容易"
    )

    private data class Verdict(
        val json: JSONObject,
        val searchOk: Boolean,
        val tocOk: Boolean,
        val contentOk: Boolean,
        val reason: String
    )

    private fun fetch(url: String, charset: String): String? =
        runCatching { HttpFetcher.get(url, charset) }.getOrNull()

    /**
     * 更顽强的抓取：https 失败时再试 http（很多小说站只挂了 https 证书 / 强制跳 http），
     * 每种协议重试多次并稍作退避，减少慢站点被误判为「打不开」。
     */
    private fun fetchRobust(url: String, charset: String, tries: Int = 2): String? {
        val variants = if (url.startsWith("https://")) {
            listOf(url, "http://" + url.removePrefix("https://"))
        } else listOf(url)
        for (v in variants) {
            for (i in 0 until tries) {
                val r = fetch(v, charset)
                if (r != null) return r
                if (i < tries - 1) Thread.sleep(500)
            }
        }
        return null
    }

    /** 跟随 301/302，拿到搬站后的最终地址（社区小说站换域名是常态） */
    private fun resolveFinalUrl(url: String): String? {
        var current = url
        repeat(5) {
            val conn = runCatching { URL(current).openConnection() as java.net.HttpURLConnection }
                .getOrNull() ?: return null
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", HttpFetcher.DEFAULT_USER_AGENT)
            val code = runCatching { conn.responseCode }.getOrNull() ?: run { conn.disconnect(); return null }
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (code in 300..399 && !location.isNullOrBlank()) {
                current = URL(URL(current), location).toString()
            } else return current
        }
        return current
    }

    private fun check(json: JSONObject): Verdict {
        val name = json.optString("name")
        var baseUrl = json.optString("baseUrl")
        val rules = BookRulesCodec.fromJson(json.optJSONObject("rules")?.toString() ?: "{}")
            ?: return Verdict(json, false, false, false, "规则解析失败")
        val charset = json.optString("charset", "utf-8")

        // 先探活并跟随搬站，把 baseUrl / searchUrl 一起改写
        val finalUrl = resolveFinalUrl(baseUrl) ?: return Verdict(json, false, false, false, "站点打不开")
        val oldHost = runCatching { URI(baseUrl).authority }.getOrNull()
        val newHost = runCatching { URI(finalUrl).authority }.getOrNull()
        if (oldHost != null && newHost != null && oldHost != newHost) {
            baseUrl = "${URI(finalUrl).scheme}://$newHost"
            json.put("baseUrl", baseUrl)
            json.put("searchUrl", json.optString("searchUrl").replace(oldHost, newHost))
        }

        // 1) 搜索：多个关键词，任一能返回结果即算「能搜到书」
        var items = emptyList<org.jsoup.nodes.Element>()
        var searchBase = baseUrl
        for (kw in keywords) {
            val encoded = URLEncoder.encode(kw, charset)
            val url = json.optString("searchUrl")
                .replace("{{key}}", encoded).replace("{key}", encoded)
            val html = fetchRobust(url, charset) ?: continue
            val doc = HtmlRules.parse(html, baseUrl)
            val found = HtmlRules.selectList(doc, rules.search.list)
            if (found.isNotEmpty()) { items = found; searchBase = baseUrl; break }
        }
        if (items.isEmpty()) {
            return Verdict(json, false, false, false, "搜索页无结果（站点可能已失效/改版）")
        }

        val bookUrl = HtmlRules.firstText(items.first(), rules.search.url, searchBase)
            .let { if (it.isBlank()) "" else it }
        if (bookUrl.isBlank()) return Verdict(json, true, false, false, "搜到结果但没书籍链接")

        // 2) 目录（含 pageUrl 跳转到正文页，和 App 管线一致）
        val toc = rules.toc
        var pageUrl = bookUrl
        val bookHtml = fetchRobust(bookUrl, charset) ?: return Verdict(json, true, false, false, "书籍页打不开")
        var tocDoc = HtmlRules.parse(bookHtml, bookUrl)
        if (toc.pageUrl.isNotBlank()) {
            val entry = HtmlRules.firstText(tocDoc, toc.pageUrl, bookUrl)
            if (entry.isNotBlank() && entry != bookUrl) {
                pageUrl = entry
                val entryHtml = fetchRobust(entry, charset) ?: return Verdict(json, true, false, false, "目录页打不开")
                tocDoc = HtmlRules.parse(entryHtml, entry)
            }
        }
        val chapters = HtmlRules.selectList(tocDoc, toc.list)
        if (chapters.isEmpty()) return Verdict(json, true, false, false, "目录页没解析出章节")
        val chapterUrl = HtmlRules.firstText(chapters.first(), toc.url, pageUrl)
        if (chapterUrl.isBlank()) return Verdict(json, true, false, false, "章节没链接")

        // 3) 正文
        val contentHtml = fetchRobust(chapterUrl, charset)
            ?: return Verdict(json, true, true, false, "章节页打不开")
        val content = HtmlRules.extractContent(HtmlRules.parse(contentHtml, chapterUrl), rules.content)
        val contentOk = content.length >= 100
        return Verdict(json, true, true, contentOk, if (contentOk) "可用" else "正文太短")
    }

    @Test
    fun verifyAllBundledSources() {
        Assume.assumeTrue("体检模式默认关闭（设 VERIFY_SOURCES=1 再跑）",
            System.getenv("VERIFY_SOURCES") == "1")

        val array = JSONArray(File("src/main/assets/book_sources.json").readText())
        val sources = (0 until array.length()).map { array.getJSONObject(it) }
        val keyword = "斗破苍穹"

        val pool = Executors.newFixedThreadPool(16)
        val futures = sources.map { json ->
            pool.submit(Callable {
                runCatching { check(json) }
                    .getOrElse { Verdict(json, false, false, false, "异常：${it.message}") }
            })
        }
        pool.shutdown()
        pool.awaitTermination(20, TimeUnit.MINUTES)

        val verdicts = futures.map { it.get() }
        val searchOk = verdicts.filter { it.searchOk }
        val fullOk = verdicts.filter { it.tocOk && it.contentOk }

        fun write(name: String, list: List<Verdict>) {
            val arr = JSONArray()
            list.forEach { v ->
                val o = JSONObject(v.json.toString())
                o.put("_reason", v.reason)
                o.put("_readable", v.tocOk && v.contentOk)
                arr.put(o)
            }
            File("build/$name").writeText(arr.toString(2))
        }
        write("verified_search.json", searchOk)
        write("verified_full.json", fullOk)
        val failed = JSONArray()
        verdicts.filter { !it.searchOk }.forEach { v ->
            failed.put(JSONObject()
                .put("name", v.json.optString("name"))
                .put("baseUrl", v.json.optString("baseUrl"))
                .put("group", v.json.optString("group"))
                .put("reason", v.reason))
        }
        File("build/failed_sources.json").writeText(failed.toString(2))

        println("体检完成：能搜到书 ${searchOk.size}/${sources.size} 个，其中全链路可读 ${fullOk.size} 个")
        searchOk.groupBy { it.reason }.forEach { (r, l) ->
            println("  搜到但读不畅：${l.size} 个（$r）")
        }
    }
}
