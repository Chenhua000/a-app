package com.example.myfirstapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookMatchRankingTest {

    private fun r(
        title: String,
        author: String = "",
        source: String = "s1",
        summary: String = ""
    ) = BookSearchResult(
        sourceId = source,
        sourceName = source,
        title = title,
        author = author,
        coverUrl = "",
        summary = summary,
        bookUrl = "https://example.com/$title"
    )

    /** 用户的核心抱怨：某个书源的一串结果不能一直霸在前面，同名书要排最前 */
    @Test
    fun sameSourceBlockDoesNotBeatExactTitleFromLaterSource() {
        val input = listOf(
            r("斗破苍穹之最强系统", source = "s1"),
            r("斗破苍穹外传", source = "s1"),
            r("斗破苍穹", source = "s2")
        )
        val ranked = BookMatchRanking.rank(input, "斗破苍穹")
        assertEquals("斗破苍穹", ranked.first().title)
        assertEquals("s2", ranked.first().sourceId)
    }

    @Test
    fun prefixBeatsContains() {
        val input = listOf(
            r("[连载]斗破苍穹", source = "s1"),
            r("斗破苍穹（精校版）", source = "s2")
        )
        val ranked = BookMatchRanking.rank(input, "斗破苍穹")
        assertEquals("斗破苍穹（精校版）", ranked.first().title)
    }

    @Test
    fun exactTitleBeatsLongerTitleEvenFromEarlierSource() {
        val input = listOf(
            r("斗破苍穹之最强系统", source = "s1"),
            r("斗破苍穹", source = "s3")
        )
        assertEquals("斗破苍穹", BookMatchRanking.rank(input, "斗破苍穹").first().title)
    }

    @Test
    fun siteNoiseSuffixIsIgnored() {
        val plain = BookMatchRanking.score(r("斗破苍穹"), "斗破苍穹")
        val noisy = BookMatchRanking.score(r("斗破苍穹最新章节"), "斗破苍穹")
        assertEquals(plain, noisy)
    }

    @Test
    fun punctuationAndSpacesAreNormalized() {
        val plain = BookMatchRanking.score(r("斗破苍穹"), "斗破苍穹")
        val messy = BookMatchRanking.score(r("《斗破 苍穹》"), "斗破苍穹")
        assertEquals(plain, messy)
    }

    @Test
    fun unrelatedResultGoesLastAndScoresZero() {
        val unrelated = r("完美世界")
        assertEquals(0, BookMatchRanking.score(unrelated, "斗破苍穹"))
        val ranked = BookMatchRanking.rank(listOf(unrelated, r("斗破苍穹")), "斗破苍穹")
        assertEquals("斗破苍穹", ranked.first().title)
        assertEquals("完美世界", ranked.last().title)
    }

    @Test
    fun partialQueryMatchesWholeTitle() {
        // 「斗破」应当能命中「斗破苍穹」，并且排在只沾边的东西前面
        val whole = BookMatchRanking.score(r("斗破苍穹"), "斗破")
        val loose = BookMatchRanking.score(r("苍穹之斗破重生"), "斗破")
        assertTrue(whole > loose)
    }

    @Test
    fun authorMatchRanksBelowTitleMatch() {
        val byAuthor = BookMatchRanking.score(r("某本书", author = "斗破苍穹"), "斗破苍穹")
        val byTitle = BookMatchRanking.score(r("斗破苍穹", author = "天蚕土豆"), "斗破苍穹")
        assertTrue(byAuthor > 0)
        assertTrue(byTitle > byAuthor)
    }

    @Test
    fun equalScoresKeepOriginalOrder() {
        val input = listOf(
            r("斗破苍穹（甲）", source = "s1"),
            r("斗破苍穹（乙）", source = "s2"),
            r("斗破苍穹（丙）", source = "s3")
        )
        val ranked = BookMatchRanking.rank(input, "斗破苍穹")
        assertEquals(listOf("斗破苍穹（甲）", "斗破苍穹（乙）", "斗破苍穹（丙）"), ranked.map { it.title })
    }

    @Test
    fun emptyQueryKeepsOrder() {
        val input = listOf(r("完美世界", source = "s1"), r("斗破苍穹", source = "s2"))
        assertEquals(input, BookMatchRanking.rank(input, "   "))
    }
}
