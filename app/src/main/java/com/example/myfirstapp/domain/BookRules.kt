package com.example.myfirstapp.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * 书源规则。JSON 结构与「阅读 / Legado」系书源的理念一致，但只用 CSS 选择器 + 正则替换，
 * 不执行网站 JS，避免安全风险。
 *
 * 规则里的 @ 后缀：
 *  - `css@text`  取文本内容
 *  - `css@href` / `@src` / `@data-src` 取属性值
 *  - 什么都不写时默认取文本（正文规则除外，正文默认取整段 HTML）
 *  - 多个备选用 `||` 分隔
 */
data class ReplacePair(val from: String, val to: String)

data class SearchRule(
    val list: String = "",
    val name: String = "@text",
    val author: String = "",
    val cover: String = "",
    val summary: String = "",
    val url: String = "@href"
)

data class TocRule(
    /** 书页上的「目录入口」链接（可选）。填了就先跳到这个页面，再在它上面找章节 */
    val pageUrl: String = "",
    val list: String = "",
    val name: String = "@text",
    val url: String = "@href",
    /** 只保留链接里包含该子串的条目，用于过滤掉无关链接；为空表示不过滤 */
    val contains: String = "",
    val remove: List<String> = emptyList()
)

data class ContentRule(
    val selector: String = "",
    val remove: List<String> = emptyList(),
    val replace: List<ReplacePair> = emptyList(),
    /** 正文分页「下一页」规则（Legado 的 nextContentUrl / ruleContentUrlNext）。
     *  很多站一章拆成 2~3 页，没有它就只能读到第一页。 */
    val nextContentUrl: String = ""
)

data class BookRules(
    val search: SearchRule = SearchRule(),
    val toc: TocRule = TocRule(),
    val content: ContentRule = ContentRule()
)

object BookRulesCodec {

    fun toJson(rules: BookRules): String {
        val root = JSONObject()
        root.put("search", JSONObject().apply {
            put("list", rules.search.list)
            put("name", rules.search.name)
            put("author", rules.search.author)
            put("cover", rules.search.cover)
            put("summary", rules.search.summary)
            put("url", rules.search.url)
        })
        root.put("toc", JSONObject().apply {
            put("pageUrl", rules.toc.pageUrl)
            put("list", rules.toc.list)
            put("name", rules.toc.name)
            put("url", rules.toc.url)
            put("contains", rules.toc.contains)
            put("remove", JSONArray(rules.toc.remove))
        })
        root.put("content", JSONObject().apply {
            put("selector", rules.content.selector)
            put("nextContentUrl", rules.content.nextContentUrl)
            put("remove", JSONArray(rules.content.remove))
            put(
                "replace",
                JSONArray().apply {
                    rules.content.replace.forEach {
                        put(JSONObject().apply { put("from", it.from); put("to", it.to) })
                    }
                }
            )
        })
        return root.toString()
    }

    fun fromJson(text: String): BookRules? = runCatching {
        if (text.isBlank()) return null
        val root = JSONObject(text)
        BookRules(
            search = root.optJSONObject("search")?.let { search ->
                SearchRule(
                    list = search.optString("list", ""),
                    name = search.optString("name", "@text"),
                    author = search.optString("author", ""),
                    cover = search.optString("cover", ""),
                    summary = search.optString("summary", ""),
                    url = search.optString("url", "@href")
                )
            } ?: SearchRule(),
            toc = root.optJSONObject("toc")?.let { toc ->
                TocRule(
                    pageUrl = toc.optString("pageUrl", ""),
                    list = toc.optString("list", ""),
                    name = toc.optString("name", "@text"),
                    url = toc.optString("url", "@href"),
                    contains = toc.optString("contains", ""),
                    remove = stringList(toc.optJSONArray("remove"))
                )
            } ?: TocRule(),
            content = root.optJSONObject("content")?.let { content ->
                ContentRule(
                    selector = content.optString("selector", ""),
                    remove = stringList(content.optJSONArray("remove")),
                    replace = replaceList(content.optJSONArray("replace")),
                    nextContentUrl = content.optString("nextContentUrl", "")
                )
            } ?: ContentRule()
        )
    }.getOrNull()

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i)
            if (value.isNotBlank()) result.add(value)
        }
        return result
    }

    private fun replaceList(array: JSONArray?): List<ReplacePair> {
        if (array == null) return emptyList()
        val result = mutableListOf<ReplacePair>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(ReplacePair(item.optString("from", ""), item.optString("to", "")))
        }
        return result.filter { it.from.isNotBlank() }
    }
}
