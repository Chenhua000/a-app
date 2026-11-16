package com.example.myfirstapp

import com.example.myfirstapp.domain.BookResultFilter
import com.example.myfirstapp.domain.BookSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索结果列表合并：同一本书（书名+作者相同）在列表里只占一条，
 * 其它源留给详情页换源；简介/封面可以从任意一条借过来。
 */
class BookResultMergeTest {

    private fun r(
        sourceId: String,
        title: String,
        author: String = "作者",
        cover: String = "",
        summary: String = "",
        url: String = "http://x/$sourceId"
    ) = BookSearchResult(sourceId, "源$sourceId", title, author, cover, summary, url)

    @Test
    fun sameBookFromManySourcesBecomesOneRow() {
        val list = listOf(
            r("s1", "明克街13号"),
            r("s2", "明克街13号", cover = "http://c/1.jpg", summary = "简介来自 s2"),
            r("s3", "明克街13号"),
            r("s4", "另一本书", author = "别的作者")
        )
        val merged = BookResultFilter.mergeByBook(list)
        assertEquals("同书只留一条，另一本书另算", 2, merged.size)
        assertEquals("明克街13号", merged[0].title)
        assertEquals("另一本书", merged[1].title)
    }

    @Test
    fun mergedRowBorrowsSummaryAndCoverFromSiblings() {
        val list = listOf(
            r("s1", "深空彼岸", cover = "", summary = ""),
            r("s2", "深空彼岸", cover = "http://c/9.jpg", summary = "浩瀚的宇宙中……")
        )
        val merged = BookResultFilter.mergeByBook(list)
        assertEquals(1, merged.size)
        assertEquals("http://c/9.jpg", merged[0].coverUrl)
        assertEquals("浩瀚的宇宙中……", merged[0].summary)
        assertTrue("主条目仍用第一个非云端源（点开就能读）", merged[0].bookUrl.endsWith("/s1"))
    }

    @Test
    fun cloudResultIsNotUsedAsPrimaryButCanProvideSummary() {
        val list = listOf(
            BookSearchResult("cloud_qq", "阅文书城·云端", "道诡异仙", "狐尾的笔", "http://q.jpg", "云端简介", "http://qq/intro"),
            r("s1", "道诡异仙", author = "狐尾的笔"),
            r("s2", "道诡异仙", author = "狐尾的笔")
        )
        val merged = BookResultFilter.mergeByBook(list)
        assertEquals(1, merged.size)
        assertEquals("s1", merged[0].sourceId)
        assertEquals("云端简介", merged[0].summary)
    }

    @Test
    fun sameTitleWithMissingOrDifferentAuthorStillMerges() {
        // 很多源的 author 是空的；只要书名相同就应合并成列表里的一条（多源留到详情页切换）
        val list = listOf(
            r("s1", "同名书", author = "作者甲"),
            r("s2", "同名书", author = ""),
            r("s3", "《同名书》", author = "作者乙")
        )
        assertEquals(1, BookResultFilter.mergeByBook(list).size)
    }

    @Test
    fun titlesWithPunctuationAreMerged() {
        val list = listOf(
            r("s1", "斗破苍穹"),
            r("s2", "《斗破苍穹》")
        )
        // 书名号会被归一化掉，同一本书
        assertEquals(1, BookResultFilter.mergeByBook(list).size)
    }
}
