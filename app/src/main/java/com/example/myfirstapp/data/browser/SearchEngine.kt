package com.example.myfirstapp.data.browser

/** 一个搜索入口：首页地址 + 带 {query} 占位符的搜索模板 */
data class SearchEngine(
    val id: String,
    val name: String,
    val homeUrl: String,
    val searchTemplate: String,
    val builtIn: Boolean = true
) {
    /** 用关键词拼出搜索地址 */
    fun searchUrl(query: String): String {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return if (searchTemplate.contains("{query}")) {
            searchTemplate.replace("{query}", encoded)
        } else {
            // 没有占位符就当作普通网址，追加查询参数（兼容用户自定义时忘写占位符）
            val sep = if (searchTemplate.contains("?")) "&" else "?"
            "$searchTemplate${sep}q=$encoded"
        }
    }
}

/** 常见的搜索网址（内置，用户还可以自己添加） */
object BuiltInEngines {
    val list: List<SearchEngine> = listOf(
        SearchEngine("baidu", "百度", "https://www.baidu.com", "https://www.baidu.com/s?wd={query}"),
        SearchEngine("bing", "必应", "https://www.bing.com", "https://www.bing.com/search?q={query}"),
        SearchEngine("google", "谷歌", "https://www.google.com", "https://www.google.com/search?q={query}"),
        SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com", "https://duckduckgo.com/?q={query}"),
        SearchEngine("sogou", "搜狗", "https://www.sogou.com", "https://www.sogou.com/web?query={query}"),
        SearchEngine("so360", "360搜索", "https://www.so.com", "https://www.so.com/s?q={query}"),
        SearchEngine("yandex", "Yandex", "https://yandex.com", "https://yandex.com/search/?text={query}"),
        SearchEngine("yahoo", "Yahoo", "https://search.yahoo.com", "https://search.yahoo.com/search?p={query}"),
        SearchEngine("ecosia", "Ecosia", "https://www.ecosia.org", "https://www.ecosia.org/search?q={query}"),
        SearchEngine("wikipedia", "维基百科", "https://zh.wikipedia.org", "https://zh.wikipedia.org/w/index.php?search={query}"),
        SearchEngine("zhihu", "知乎", "https://www.zhihu.com", "https://www.zhihu.com/search?type=content&q={query}"),
        SearchEngine("bilibili", "哔哩哔哩", "https://www.bilibili.com", "https://search.bilibili.com/all?keyword={query}"),
        SearchEngine("github", "GitHub", "https://github.com", "https://github.com/search?q={query}"),
        SearchEngine("stackoverflow", "Stack Overflow", "https://stackoverflow.com", "https://stackoverflow.com/search?q={query}"),
        SearchEngine("taobao", "淘宝", "https://www.taobao.com", "https://s.taobao.com/search?q={query}"),
        SearchEngine("jd", "京东", "https://www.jd.com", "https://search.jd.com/Search?keyword={query}"),
        SearchEngine("douban", "豆瓣", "https://www.douban.com", "https://search.douban.com/book/subject_search?search_text={query}"),
        SearchEngine("youtube", "YouTube", "https://www.youtube.com", "https://www.youtube.com/results?search_query={query}"),
        SearchEngine("weibo", "微博", "https://weibo.com", "https://s.weibo.com/weibo?q={query}"),
        SearchEngine("xiaohongshu", "小红书", "https://www.xiaohongshu.com", "https://www.xiaohongshu.com/search_result?keyword={query}")
    )

    val defaultId: String = "bing"
}

/** 自定义引擎的纯 Kotlin 编解码（不依赖 Android，方便单测） */
object SearchEngineCodec {
    private const val SEP = '\u001F'
    private const val LINE = '\n'

    fun encode(list: List<SearchEngine>): String =
        list.filterNot { it.builtIn }
            .joinToString(LINE.toString()) { e ->
                listOf(
                    escape(e.id), escape(e.name), escape(e.homeUrl), escape(e.searchTemplate)
                ).joinToString(SEP.toString())
            }

    fun decode(raw: String?): List<SearchEngine> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(LINE).mapNotNull { line ->
            if (line.isEmpty()) return@mapNotNull null
            val p = line.split(SEP)
            if (p.size < 4) return@mapNotNull null
            val id = unescape(p[0])
            val template = unescape(p[3])
            if (id.isEmpty() || template.isEmpty()) return@mapNotNull null
            SearchEngine(
                id = id,
                name = unescape(p[1]).ifEmpty { "自定义" },
                homeUrl = unescape(p[2]).ifEmpty { template.substringBefore("?") },
                searchTemplate = template,
                builtIn = false
            )
        }
    }

    private fun escape(v: String): String = buildString {
        v.forEach { c ->
            when (c) {
                '%' -> append("%25")
                SEP -> append("%1F")
                LINE -> append("%0A")
                else -> append(c)
            }
        }
    }

    private fun unescape(v: String): String = buildString {
        var i = 0
        while (i < v.length) {
            if (i + 2 < v.length && v[i] == '%') {
                when (v.substring(i, i + 3)) {
                    "%25" -> { append('%'); i += 3; continue }
                    "%1F" -> { append(SEP); i += 3; continue }
                    "%0A" -> { append(LINE); i += 3; continue }
                }
            }
            append(v[i]); i++
        }
    }
}
