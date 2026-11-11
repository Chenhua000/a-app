package com.example.myfirstapp.data.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 回归：中文小说站常见的 meta refresh / JS 跳壳，导致「能搜到书却打不开」 */
class HttpFetcherRedirectTest {

    private val base = "http://m.sfacg.com/b/210467"

    @Test
    fun metaRefreshIsFollowed() {
        val html = """<html><head>
            <meta http-equiv="refresh" content="0;url=/b/210467/12345">
            </head><body>跳转中</body></html>
        """.trimIndent()
        assertEquals(
            "http://m.sfacg.com/b/210467/12345",
            HttpFetcher.findPageRedirect(html, base)
        )
    }

    @Test
    fun metaRefreshWithAbsoluteUrlIsFollowed() {
        val html = """<meta http-equiv='refresh' content='3; URL=https://www.example.com/read/1.html'>"""
        assertEquals(
            "https://www.example.com/read/1.html",
            HttpFetcher.findPageRedirect(html, base)
        )
    }

    @Test
    fun jsLocationRedirectIsFollowed() {
        val html = """<html><body><script>window.location.href="https://m.example.com/ch/9";</script></body></html>"""
        assertEquals(
            "https://m.example.com/ch/9",
            HttpFetcher.findPageRedirect(html, base)
        )
    }

    @Test
    fun normalLongPageIsNotTreatedAsRedirect() {
        val html = "<html><body><meta http-equiv=\"refresh\" content=\"0;url=/other\">" +
            "<div>" + "正文".repeat(5000) + "</div></body></html>"
        assertNull(HttpFetcher.findPageRedirect(html, base))
    }

    @Test
    fun javascriptAndHashAreIgnored() {
        assertNull(
            HttpFetcher.findPageRedirect(
                "<script>location.href='javascript:void(0)'</script>", base
            )
        )
        assertNull(
            HttpFetcher.findPageRedirect(
                "<script>location.href='#chapter1'</script>", base
            )
        )
    }

    @Test
    fun plainShortPageWithoutRedirectIsIgnored() {
        assertNull(HttpFetcher.findPageRedirect("<html><body>404 Not Found</body></html>", base))
    }
}
