package com.example.myfirstapp.domain

/**
 * 搜索结果的相关度排序。
 *
 * 各书源是并发搜的，结果天然是「按书源一段一段」拼起来的：同一书源的一堆结果挤在一起，
 * 关键词命中度低的还可能排在真正同名的书前面。这里按「和关键词的匹配度」整体重排：
 *
 * 1. 书名就是关键词（去掉标点 / 站点尾巴后完全一致）——
 * 2. 书名以关键词开头 ——「斗破苍穹（精校版）」
 * 3. 书名包含关键词 ——「[连载]斗破苍穹」
 * 4. 关键词的字按顺序出现在书名里 ——「斗破之苍穹」
 * 5. 只在作者 / 简介里命中的（按作者搜的时候有用）
 *
 * 同一档里，书名越贴近关键词越靠前（同名书压过「XX之最强系统」这种）。
 * 分数相同的保持原来的先后（稳定排序），所以同一本书在哪个源先搜到就用哪个源，不会乱跳。
 */
object BookMatchRanking {

    /** 站点标题里常见的尾巴：判断「是不是同一本书」之前要先摘掉。只放明确是站点水印的词，
     *  像「连载」「全文」这种本身也可能是正常书名的，不能摘 */
    private val NOISE = listOf(
        "最新章节", "全文免费阅读", "全文阅读", "在线阅读", "免费阅读",
        "无弹窗", "无广告", "txt全集下载", "txt下载", "全集下载"
    )

    fun rank(results: List<BookSearchResult>, query: String): List<BookSearchResult> {
        if (results.size < 2) return results
        val q = normalize(query)
        if (q.isEmpty()) return results
        return results.sortedByDescending { score(it.title, it.author, it.summary, q) }
    }

    fun score(result: BookSearchResult, query: String): Int =
        score(result.title, result.author, result.summary, query)

    fun score(title: String, author: String, summary: String, query: String): Int {
        val q = normalize(query)
        if (q.isEmpty()) return 0

        var clean = title
        NOISE.forEach { clean = clean.replace(it, "") }
        val t = normalize(clean).ifEmpty { normalize(title) }
        if (t.isEmpty()) return 0
        val a = normalize(author)
        val s = normalize(summary)

        val base = when {
            t == q -> 1000
            t.startsWith(q) -> 820
            t.contains(q) -> 660
            containsInOrder(q, t) -> 340
            a.contains(q) -> 260 // 按作者搜：书名不含关键词，但作者对得上
            s.contains(q) -> 120
            else -> 0
        }
        if (base == 0) return 0

        // 书名越贴近关键词越好：多出来的字越多，说明越可能是续作 / 合集 / 无关条目
        val extra = (t.length - q.length).coerceAtLeast(0)
        val compact = when {
            extra == 0 -> 120
            extra <= 2 -> 90
            extra <= 5 -> 60
            extra <= 10 -> 30
            else -> 10
        }
        val aux = (if (a.contains(q)) 40 else 0) + (if (s.contains(q)) 15 else 0)
        return base + compact + aux
    }

    /** 只保留汉字 / 字母 / 数字，去掉空格和标点：「《斗破 苍穹》」和「斗破苍穹」要当成同一个词 */
    private fun normalize(raw: String): String = buildString {
        for (ch in raw.lowercase()) {
            if (ch.isLetterOrDigit()) append(ch)
        }
    }

    /** q 的每个字是否按顺序出现（允许中间夹别的字），「斗破」能命中「斗破苍穹」 */
    private fun containsInOrder(q: String, t: String): Boolean {
        if (q.isEmpty()) return false
        var i = 0
        for (c in t) {
            if (c == q[i]) {
                i++
                if (i == q.length) return true
            }
        }
        return false
    }
}
