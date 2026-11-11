package com.example.myfirstapp.data.browser

/** 一次访问记录（最近访问） */
data class BrowserVisit(
    val url: String,
    val title: String,
    val at: Long = System.currentTimeMillis()
)

/** 一条收藏 */
data class BrowserBookmark(
    val url: String,
    val title: String,
    val at: Long = System.currentTimeMillis()
)

/** 访问历史 / 收藏的纯 Kotlin 编解码，方便单测 */
object BrowserExtrasCodec {
    private const val SEP = '\u001F'
    private const val LINE = '\n'

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

    private fun encode(items: List<Triple<String, String, Long>>): String =
        items.joinToString(LINE.toString()) { (a, b, c) ->
            listOf(escape(a), escape(b), c.toString()).joinToString(SEP.toString())
        }

    private fun decode(raw: String?): List<Triple<String, String, Long>> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(LINE).mapNotNull { line ->
            if (line.isEmpty()) return@mapNotNull null
            val p = line.split(SEP)
            if (p.size < 3) return@mapNotNull null
            val url = unescape(p[0])
            val at = p[2].toLongOrNull() ?: return@mapNotNull null
            if (url.isEmpty()) return@mapNotNull null
            Triple(url, unescape(p[1]), at)
        }
    }

    fun encodeHistory(list: List<BrowserVisit>): String =
        encode(list.map { Triple(it.url, it.title, it.at) })

    fun decodeHistory(raw: String?): List<BrowserVisit> =
        decode(raw).map { BrowserVisit(it.first, it.second, it.third) }

    fun encodeBookmarks(list: List<BrowserBookmark>): String =
        encode(list.map { Triple(it.url, it.title, it.at) })

    fun decodeBookmarks(raw: String?): List<BrowserBookmark> =
        decode(raw).map { BrowserBookmark(it.first, it.second, it.third) }
}
