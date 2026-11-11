package com.example.myfirstapp.data.browser

/**
 * 判断/规范化地址栏输入：
 * - 已是 http(s) 网址 → 原样
 * - 看起来像域名（含点、无空格、非中文）→ 补 https://
 * - 其余 → 当作关键词，走搜索引擎模板
 */
object BrowserUrl {

    private val domainLike = Regex("^[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)+(:[0-9]{1,5})?(/[^\\s]*)?$")

    fun normalize(input: String, engine: SearchEngine): String? {
        val raw = input.trim()
        if (raw.isEmpty()) return null
        return when {
            raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) -> raw
            raw.startsWith("about:", ignoreCase = true) || raw.startsWith("file:", ignoreCase = true) -> raw
            domainLike.matches(raw) -> "https://$raw"
            else -> engine.searchUrl(raw)
        }
    }

    /** 自定义引擎模板是否合法：必须是 http(s) 网址 */
    fun validTemplate(template: String): Boolean {
        val t = template.trim()
        return (t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)) &&
            t.contains("{query}")
    }

    /** 从搜索模板推一个首页地址出来 */
    fun homeFromTemplate(template: String): String {
        val t = template.trim()
        val beforeQuery = t.substringBefore("?")
        val schemeEnd = beforeQuery.indexOf("://")
        if (schemeEnd < 0) return t
        val rest = beforeQuery.substring(schemeEnd + 3)
        val host = rest.substringBefore("/")
        return beforeQuery.substring(0, schemeEnd + 3) + host
    }
}
