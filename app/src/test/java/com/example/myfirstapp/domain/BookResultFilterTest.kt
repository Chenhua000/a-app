package com.example.myfirstapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 回归：站点导航被当成搜索结果、以及无关结果混进列表 */
class BookResultFilterTest {

    private fun r(title: String, url: String = "https://x.com/book/123.html") =
        BookSearchResult("s1", "测试源", title, "", "", "", url)

    @Test
    fun navigationTitlesAreFiltered() {
        listOf("首页", "永久书架", "玄幻", "奇幻", "武侠", "排行榜", "登录", "分类", "完本")
            .forEach {
                assertTrue(
                    "$it 应判为导航",
                    BookResultFilter.isNavigation(it, "https://x.com/rank/")
                )
            }
    }

    @Test
    fun rootAndRankPathsAreFiltered() {
        assertTrue(BookResultFilter.isNavigation("本站推荐", "https://x.com/"))
        assertTrue(BookResultFilter.isNavigation("本站推荐", "https://x.com/rank/"))
        assertTrue(BookResultFilter.isNavigation("本站推荐", "https://x.com/all.html"))
        assertTrue(BookResultFilter.isNavigation("坏链接", "javascript:void(0)"))
    }

    @Test
    fun realBookIsKept() {
        assertFalse(BookResultFilter.isNavigation("原始法则", "https://x.com/book/123.html"))
        assertFalse(BookResultFilter.isNavigation("斗破苍穹", "https://x.com/novel/456"))
    }

    @Test
    fun keepRelevantDropsUnrelatedResults() {
        val input = listOf(
            r("原始法则"),
            r("完美世界"),
            r("首页", "https://x.com/")
        )
        assertEquals(listOf("原始法则"), BookResultFilter.keepRelevant(input, "原始法则").map { it.title })
    }

    @Test
    fun keepRelevantReturnsEmptyWhenNothingMatches() {
        // 书源规则坏掉时返回整页无关内容，宁可显示「没找到」，也不要塞一堆垃圾
        assertTrue(
            BookResultFilter.keepRelevant(listOf(r("完美世界"), r("斗破苍穹")), "原始法则").isEmpty()
        )
    }

    @Test
    fun blankQueryKeepsAll() {
        val input = listOf(r("完美世界"), r("斗破苍穹"))
        assertEquals(input, BookResultFilter.keepRelevant(input, "   "))
    }
}
