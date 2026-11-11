package com.example.myfirstapp.data.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserExtrasCodecTest {

    @Test
    fun emptyAndNull() {
        assertTrue(BrowserExtrasCodec.decodeHistory(null).isEmpty())
        assertTrue(BrowserExtrasCodec.decodeHistory("").isEmpty())
        assertTrue(BrowserExtrasCodec.decodeBookmarks(null).isEmpty())
    }

    @Test
    fun historyRoundTrip() {
        val list = listOf(
            BrowserVisit("https://a.com", "页面A", 100L),
            BrowserVisit("https://b.com/path?q=1", "页面B", 200L)
        )
        assertEquals(list, BrowserExtrasCodec.decodeHistory(BrowserExtrasCodec.encodeHistory(list)))
    }

    @Test
    fun bookmarksRoundTripWithSpecialCharacters() {
        val list = listOf(
            BrowserBookmark("https://a.com/x?a=1|b=2", "标题|百分号%", 10L),
            BrowserBookmark("https://b.com", "第一行\n第二行", 20L)
        )
        assertEquals(list, BrowserExtrasCodec.decodeBookmarks(BrowserExtrasCodec.encodeBookmarks(list)))
    }

    @Test
    fun malformedLinesAreSkipped() {
        val raw = "https://ok.com\u001F标题\u001F1\nbroken\n\u001F\u001FnotANumber"
        val decoded = BrowserExtrasCodec.decodeHistory(raw)
        assertEquals(1, decoded.size)
        assertEquals("https://ok.com", decoded.first().url)
    }

    @Test
    fun thirtyVisitsStayIntact() {
        val list = (0 until 30).map { BrowserVisit("https://s$it.com", "第${it}页", it.toLong()) }
        val decoded = BrowserExtrasCodec.decodeHistory(BrowserExtrasCodec.encodeHistory(list))
        assertEquals(30, decoded.size)
        assertEquals(list, decoded)
    }
}
