package com.example.myfirstapp.data.web

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

object HttpFetcher {
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"

    private const val CONNECT_TIMEOUT = 12_000
    private const val READ_TIMEOUT = 20_000

    /** 直接拿到解码后的字符串，自动识别 GBK / UTF-8 等中文站点常用编码 */
    fun get(
        url: String,
        charsetHint: String = "utf-8",
        headers: Map<String, String> = emptyMap()
    ): String = getFollowingPageRedirect(url, charsetHint, headers, 0)

    private fun getFollowingPageRedirect(
        url: String,
        charsetHint: String,
        headers: Map<String, String>,
        depth: Int
    ): String {
        val bytes = getBytes(url, headers)
        val charset = detectCharset(bytes, charsetHint)
        val html = String(bytes, charset)
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        // 不少中文小说站对移动端返回一个 100~300 字节的 meta refresh / JS 跳壳。
        // HttpURLConnection 只会跟 HTTP 3xx，不会跟页面内跳转，结果 App 拿到一个
        // 「空页面」当作正文，表现就是「能搜到书却打不开」。这里手动跟最多 3 跳。
        if (depth < 3) {
            val next = findPageRedirect(html, url)
            if (!next.isNullOrBlank() && next != url) {
                return getFollowingPageRedirect(next, charsetHint, headers, depth + 1)
            }
        }
        return html
    }

    private val META_REFRESH = Regex(
        "<meta[^>]+http-equiv\\s*=\\s*[\"']?refresh[\"']?[^>]*>",
        RegexOption.IGNORE_CASE
    )
    private val META_URL = Regex("(?i)(?:^|;)\\s*url\\s*=\\s*['\"]?([^'\"\\s;>]+)")
    private val JS_REDIRECTS = listOf(
        Regex("location\\.href\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE),
        Regex("location\\.replace\\(\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE),
        Regex("location\\.assign\\(\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE),
        Regex("window\\.location\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
    )

    /** 只在很短的页面里找跳转，避免误把正常正文页里的 JS 当跳转 */
    internal fun findPageRedirect(html: String, baseUrl: String): String? {
        if (html.length > 8192) return null
        val raw = META_REFRESH.findAll(html)
            .mapNotNull { META_URL.find(it.value)?.groupValues?.getOrNull(1) }
            .firstOrNull()
            ?: JS_REDIRECTS.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }
            ?: return null
        val target = raw.trim().trim('\'', '"')
        if (target.isEmpty() || target.startsWith("#") ||
            target.startsWith("javascript", ignoreCase = true)
        ) return null
        return runCatching { java.net.URI(baseUrl).resolve(target).toString() }.getOrNull()
    }

    /**
     * 表单 POST（application/x-www-form-urlencoded）。
     * 书源自动发现时，很多站点的搜索框是 POST 表单（或走 JS/接口），App 内置的搜索是 GET-only，
     * 所以在 fetcher 这层补一个 POST，让「书源 → 搜索规则」也能覆盖 POST 站点。
     *
     * @param form 表单字段（**原始值**，本方法内部会按 UTF-8 做 URL 编码，调用方不要先编码）
     */
    fun post(
        url: String,
        form: Map<String, String>,
        charsetHint: String = "utf-8",
        headers: Map<String, String> = emptyMap()
    ): String {
        val bytes = postBytes(url, form, headers)
        val charset = detectCharset(bytes, charsetHint)
        return String(bytes, charset)
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    }

    /**
     * 读取 Android 全局代理设置（设置 → WLAN 里的代理，或 adb 设置的 global http_proxy）。
     * 注意：App 进程里 System.getProperty("http.proxyHost") 通常为 null，
     * 必须用 android.net.Proxy 才能拿到系统代理；HttpURLConnection 默认不会自己用它。
     */
    private fun systemProxy(): Proxy? {
        val host = runCatching { android.net.Proxy.getDefaultHost() }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: return null
        val port = runCatching { android.net.Proxy.getDefaultPort() }.getOrNull()
            ?.takeIf { it > 0 } ?: return null
        return Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
    }

    // ── 简易 CookieJar：模仿 Legado 的 enabledCookieJar，按 host 存/回传 Cookie ──
    private val cookieStore =
        java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, String>>()

    private fun hostKey(url: String): String =
        runCatching { java.net.URI(url).host ?: "" }.getOrDefault("").lowercase()

    private fun cookieFor(host: String): String? {
        if (host.isBlank()) return null
        val jar = cookieStore[host] ?: return null
        if (jar.isEmpty()) return null
        return jar.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun captureCookies(connection: HttpURLConnection, host: String) {
        if (host.isBlank()) return
        val sets = runCatching {
            connection.headerFields.entries
                .filter { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
                .flatMap { it.value ?: emptyList() }
        }.getOrDefault(emptyList())
        if (sets.isEmpty()) return
        val jar = cookieStore.getOrPut(host) { java.util.concurrent.ConcurrentHashMap() }
        sets.forEach { raw ->
            val nv = raw.substringBefore(';').trim()
            val eq = nv.indexOf('=')
            if (eq > 0) runCatching { jar[nv.substring(0, eq).trim()] = nv.substring(eq + 1).trim() }
        }
    }

    fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray =
        getBytesFollowingRedirect(url, headers, 0)

    /**
     * 自己处理 3xx：HttpURLConnection 的 instanceFollowRedirects 不会跟随
     * **协议切换**（http → https），中文小说站大量用 301 把 http 跳到 https，
     * 结果 App 拿到的是 nginx 的「301 Moved Permanently」响应体，被误当成正文。
     */
    private fun getBytesFollowingRedirect(
        url: String,
        headers: Map<String, String>,
        depth: Int
    ): ByteArray {
        var connection: HttpURLConnection? = null
        return try {
            val target = URL(url)
            val proxy = systemProxy()
            connection = (if (proxy != null) target.openConnection(proxy)
            else target.openConnection()) as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.9")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
                setRequestProperty("Accept-Encoding", "gzip, deflate")
                setRequestProperty("Referer", url)
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
            }
            val host = hostKey(url)
            if (headers.keys.none { it.equals("Cookie", ignoreCase = true) }) {
                cookieFor(host)?.let { connection.setRequestProperty("Cookie", it) }
            }
            val code = connection.responseCode
            captureCookies(connection, host)
            if (code in 300..399 && depth < 5) {
                val location = connection.getHeaderField("Location")
                if (!location.isNullOrBlank()) {
                    val next = runCatching {
                        java.net.URI(url).resolve(location).toString()
                    }.getOrNull()
                    if (!next.isNullOrBlank() && next != url) {
                        return getBytesFollowingRedirect(next, headers, depth + 1)
                    }
                }
            }
            readBody(connection)
        } finally {
            connection?.disconnect()
        }
    }

    fun postBytes(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap()
    ): ByteArray {
        var connection: HttpURLConnection? = null
        return try {
            val target = URL(url)
            val proxy = systemProxy()
            connection = (if (proxy != null) target.openConnection(proxy)
            else target.openConnection()) as HttpURLConnection
            val body = form.entries.joinToString("&") { (k, v) ->
                "${urlEncode(k)}=${urlEncode(v)}"
            }
            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            connection.apply {
                requestMethod = "POST"
                instanceFollowRedirects = true
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                doOutput = true
                setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.9")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
                setRequestProperty("Accept-Encoding", "gzip, deflate")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Content-Length", bodyBytes.size.toString())
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
            }
            val host = hostKey(url)
            if (headers.keys.none { it.equals("Cookie", ignoreCase = true) }) {
                cookieFor(host)?.let { connection.setRequestProperty("Cookie", it) }
            }
            connection.outputStream.use { it.write(bodyBytes) }
            val respBody = readBody(connection)
            captureCookies(connection, host)
            respBody
        } finally {
            connection?.disconnect()
        }
    }

    /** GET / POST 共用的响应读取：处理 gzip/deflate，返回未解码的字节 */
    private fun readBody(connection: HttpURLConnection): ByteArray {
        val code = connection.responseCode
        val stream = if (code in 200..399) {
            connection.inputStream
        } else {
            connection.errorStream ?: throw java.io.IOException("HTTP $code")
        }
        return stream.use { input ->
            val wrapped = when (connection.contentEncoding?.lowercase()) {
                "gzip" -> GZIPInputStream(input)
                "deflate" -> InflaterInputStream(input)
                else -> input
            }
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val read = wrapped.read(chunk)
                if (read <= 0) break
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        }
    }

    fun detectCharset(bytes: ByteArray, hint: String): java.nio.charset.Charset {
        val header = String(bytes, 0, minOf(2048, bytes.size), Charsets.ISO_8859_1)
        val patterns = listOf(
            Regex("charset\\s*=\\s*\"?([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE),
            Regex("encoding\\s*=\\s*\"?([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)
        )
        patterns.forEach { pattern ->
            val found = pattern.find(header)?.groupValues?.getOrNull(1)
            val charset = runCatching { java.nio.charset.Charset.forName(found!!) }.getOrNull()
            if (charset != null) return charset
        }
        return runCatching { java.nio.charset.Charset.forName(hint) }
            .getOrElse { Charsets.UTF_8 }
    }

    /** 表单字段做 URL 编码（UTF-8），供 POST 使用 */
    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, "UTF-8")
}
