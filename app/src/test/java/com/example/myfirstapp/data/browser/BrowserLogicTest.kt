package com.example.myfirstapp.data.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserLogicTest {

    private val bing = BuiltInEngines.list.first { it.id == "bing" }

    @Test
    fun builtInCatalogHasCommonEngines() {
        val ids = BuiltInEngines.list.map { it.id }
        assertTrue(ids.contains("baidu"))
        assertTrue(ids.contains("bing"))
        assertTrue(ids.contains("google"))
        assertTrue(ids.contains("duckduckgo"))
        assertTrue("至少 15 个常见搜索网址", BuiltInEngines.list.size >= 15)
        assertTrue(BuiltInEngines.list.all { it.searchTemplate.contains("{query}") })
        assertTrue(BuiltInEngines.list.all { it.homeUrl.startsWith("https://") })
    }

    @Test
    fun searchUrlEncodesQuery() {
        val url = bing.searchUrl("安卓 开发&技巧")
        assertTrue(url.startsWith("https://www.bing.com/search?q="))
        assertFalse(url.contains(" "))
        assertFalse(url.contains("&") && url.contains("技巧"))
        assertTrue(url.contains("android") || url.contains("%E5%AE%89%E5%8D%93"))
    }

    @Test
    fun templateWithoutPlaceholderFallsBackToQueryParam() {
        val e = SearchEngine("x", "X", "https://x.com", "https://x.com/find", builtIn = false)
        assertTrue(e.searchUrl("abc").contains("q=abc"))
    }

    @Test
    fun normalizeKeepsHttpUrls() {
        assertEquals("http://example.com/a?b=1", BrowserUrl.normalize(" http://example.com/a?b=1 ", bing))
        assertEquals("https://www.google.com", BrowserUrl.normalize("https://www.google.com", bing))
    }

    @Test
    fun normalizeAddsHttpsToDomains() {
        assertEquals("https://example.com", BrowserUrl.normalize("example.com", bing))
        assertEquals("https://sub.example.cn/path?q=1", BrowserUrl.normalize("sub.example.cn/path?q=1", bing))
    }

    @Test
    fun normalizeTreatsFreeTextAsSearch() {
        val url = BrowserUrl.normalize("今天天气", bing)!!
        assertTrue(url.startsWith("https://www.bing.com/search?q="))
        assertEquals(null, BrowserUrl.normalize("   ", bing))
    }

    @Test
    fun chineseWordsAreSearchedNotTreatedAsDomain() {
        val url = BrowserUrl.normalize("必应.com 好用吗", bing)!!
        assertTrue(url.startsWith("https://www.bing.com/search?q="))
    }

    @Test
    fun validTemplateRequiresHttpAndPlaceholder() {
        assertTrue(BrowserUrl.validTemplate("https://a.com/s?q={query}"))
        assertTrue(BrowserUrl.validTemplate("http://a.com/s?q={query}"))
        assertFalse(BrowserUrl.validTemplate("a.com/s?q={query}"))
        assertFalse(BrowserUrl.validTemplate("https://a.com/s?q=abc"))
    }

    @Test
    fun homeFromTemplateExtractsOrigin() {
        assertEquals("https://a.com", BrowserUrl.homeFromTemplate("https://a.com/s?q={query}&p=1"))
        assertEquals("https://zh.wikipedia.org", BrowserUrl.homeFromTemplate("https://zh.wikipedia.org/w/index.php?search={query}"))
    }

    @Test
    fun customEngineCodecRoundTrip() {
        val list = listOf(
            SearchEngine("c1", "我的搜索", "https://a.com", "https://a.com/s?q={query}", false),
            SearchEngine("c2", "换行|名字%", "https://b.com", "https://b.com/x?q={query}&y=1", false)
        )
        assertEquals(list, SearchEngineCodec.decode(SearchEngineCodec.encode(list)))
    }

    @Test
    fun codecSkipsBuiltInAndBadLines() {
        val raw = "c1\u001F名字\u001Fhttps://a.com\u001Fhttps://a.com/s?q={query}\nbroken\n\n"
        val decoded = SearchEngineCodec.decode(raw)
        assertEquals(1, decoded.size)
        assertEquals("c1", decoded.first().id)
        assertFalse(decoded.first().builtIn)
        assertTrue(SearchEngineCodec.decode(null).isEmpty())
    }
}
