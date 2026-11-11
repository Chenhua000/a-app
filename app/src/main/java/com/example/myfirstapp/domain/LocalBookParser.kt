package com.example.myfirstapp.domain

import org.jsoup.Jsoup
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** 本地导入的一章 */
data class LocalChapter(val title: String, val content: String)

/** 本地导入的一本书 */
data class ParsedLocalBook(
    val title: String,
    val author: String?,
    val format: String,
    val chapters: List<LocalChapter>
)

/**
 * TXT / EPUB 本地小说解析。只做解析、不碰 Android 的 Uri 和数据库，
 * 所以可以直接在 JVM 单元测试里跑。
 */
object LocalBookParser {

    /**
     * 章节标题。只匹配「一行很短、像标题」的行，避免把正文里的「第三章节」误判成新章。
     * 覆盖中文（第1章/第一回/第二卷/番外）、英文（Chapter 3 / CHAPTER IV）与序跋。
     */
    private val headingPattern = Regex(
        """^((第[0-9零〇一二三四五六七八九十百千万两]+[章节回卷集部篇])|""" +
            """([Cc][Hh][Aa][Pp][Tt][Ee][Rr]\s+[0-9IVXLCDM]+)|""" +
            """(序章|楔子|序言|前言|引子|后记|尾声|终章|番外[一二三四五六七八九十0-9]*))"""
    )

    /** 句子收尾标点：标题行不会以这些符号结尾 */
    private val sentenceEnders = charArrayOf('。', '！', '？', '；', '，', '、', '…')

    fun fileTitle(fileName: String): String =
        fileName.substringAfterLast('/').substringBeforeLast('.').trim().ifBlank { "未命名书籍" }

    fun parseTxt(text: String, fileName: String): ParsedLocalBook {
        val normalized = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u0000', ' ')
        val lines = normalized.split('\n')

        // 找出所有像章节标题的行。
        // 两个防误判条件：
        // 1. 标题行都很短（40 字以内）；
        // 2. 标题不会以句号/问号等句子标点结尾——像「第二章的正文。」这种
        //    以「第 N 章」开头的正文句，必须留在正文里，不能切成新章节。
        val marks = ArrayList<Pair<Int, String>>()
        lines.forEachIndexed { i, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.length > 40) return@forEachIndexed
            if (line.last() in sentenceEnders) return@forEachIndexed
            if (headingPattern.containsMatchIn(line)) marks.add(i to line)
        }

        val chapters = ArrayList<LocalChapter>()
        if (marks.isEmpty()) {
            val body = normalized.trim()
            if (body.isNotEmpty()) chapters.add(LocalChapter("全文", "<p>${escape(body)}</p>"))
        } else {
            // 第一章之前的引子/作者的话，保留下来别丢
            val preamble = lines.take(marks.first().first).joinToString("\n").trim()
            if (preamble.isNotBlank()) {
                chapters.add(LocalChapter("正文前", "<p>${escape(preamble)}</p>"))
            }
            marks.forEachIndexed { index, mark ->
                val start = mark.first + 1
                val end = marks.getOrNull(index + 1)?.first ?: lines.size
                val body = lines.subList(start, end).joinToString("\n").trim()
                if (body.isBlank() && chapters.isNotEmpty()) return@forEachIndexed
                chapters.add(
                    LocalChapter(
                        title = mark.second,
                        content = "<p>${escape(body.ifBlank { "（本章无正文）" })}</p>"
                    )
                )
            }
        }
        return ParsedLocalBook(
            title = fileTitle(fileName),
            author = null,
            format = "TXT",
            chapters = chapters
        )
    }

    fun parseEpub(bytes: ByteArray, fileName: String): ParsedLocalBook {
        val entries = readZip(bytes)
        val container = entries["META-INF/container.xml"]
            ?: throw IllegalArgumentException("不是有效的 EPUB（缺少 container.xml）")
        val rootfile = Regex("full-path=\"([^\"]+)\"").find(container)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("EPUB 里找不到 OPF 主文件")

        val opfText = entries[rootfile]
            ?: throw IllegalArgumentException("EPUB 主文件缺失：$rootfile")
        val opfDir = rootfile.substringBeforeLast('/', "")
        val opf = parseXml(opfText)

        val title = firstTagText(opf, "dc:title") ?: firstTagText(opf, "title") ?: fileTitle(fileName)
        val author = firstTagText(opf, "dc:creator") ?: firstTagText(opf, "creator")

        data class Item(val href: String, val properties: String)
        val manifest = HashMap<String, Item>()
        val manifestElements = opf.getElementsByTagName("item")
        for (i in 0 until manifestElements.length) {
            val node = manifestElements.item(i) as? org.w3c.dom.Element ?: continue
            val id = node.getAttribute("id")
            val href = node.getAttribute("href")
            if (id.isNotBlank() && href.isNotBlank()) {
                manifest[id] = Item(href, node.getAttribute("properties"))
            }
        }

        val spineIds = ArrayList<String>()
        val spineElements = opf.getElementsByTagName("itemref")
        for (i in 0 until spineElements.length) {
            val node = spineElements.item(i) as? org.w3c.dom.Element ?: continue
            val idref = node.getAttribute("idref")
            if (idref.isNotBlank() && node.getAttribute("linear") != "no") spineIds.add(idref)
        }

        // 目录标题：优先 NCX（epub2），退回 nav.xhtml（epub3）
        val titles = HashMap<String, String>()
        val ncxHref = manifest.entries
            .firstOrNull { it.value.href.endsWith(".ncx", ignoreCase = true) }?.value?.href
        if (ncxHref != null) {
            val ncxPath = resolvePath(opfDir, ncxHref)
            val ncxDir = ncxPath.substringBeforeLast('/', "")
            entries[ncxPath]?.let { ncxText ->
                runCatching {
                    val ncx = parseXml(ncxText)
                    val points = ncx.getElementsByTagName("navPoint")
                    for (i in 0 until points.length) {
                        val point = points.item(i) as? org.w3c.dom.Element ?: continue
                        val label = point.getElementsByTagName("text").item(0)?.textContent?.trim()
                        val src = (point.getElementsByTagName("content").item(0) as? org.w3c.dom.Element)
                            ?.getAttribute("src")
                        if (!label.isNullOrBlank() && !src.isNullOrBlank()) {
                            titles[resolvePath(ncxDir, src).substringBefore('#')] = label
                        }
                    }
                }
            }
        }
        if (titles.isEmpty()) {
            val navItem = manifest.entries.firstOrNull { it.value.properties.contains("nav") }
            if (navItem != null) {
                val navPath = resolvePath(opfDir, navItem.value.href)
                val navDir = navPath.substringBeforeLast('/', "")
                entries[navPath]?.let { navHtml ->
                    runCatching {
                        val navDoc = Jsoup.parse(navHtml)
                        navDoc.select("a[href]").forEach { a ->
                            val label = a.text().trim()
                            if (label.isNotBlank()) {
                                titles[resolvePath(navDir, a.attr("href")).substringBefore('#')] = label
                            }
                        }
                    }
                }
            }
        }

        val chapters = ArrayList<LocalChapter>()
        spineIds.forEach { idref ->
            val item = manifest[idref] ?: return@forEach
            val path = resolvePath(opfDir, item.href)
            val html = entries[path] ?: entries[decode(path)] ?: return@forEach
            val cleaned = cleanHtml(html)
            if (cleaned.isBlank()) return@forEach
            val chapterTitle = titles[path]
                ?: titles[decode(path)]
                ?: "第 ${chapters.size + 1} 章"
            chapters.add(LocalChapter(chapterTitle, cleaned))
        }
        if (chapters.isEmpty()) throw IllegalArgumentException("EPUB 里没有解析到正文")
        return ParsedLocalBook(title, author, "EPUB", chapters)
    }

    /* -------------------- 内部工具 -------------------- */

    private fun cleanHtml(html: String): String {
        val doc = Jsoup.parse(html)
        doc.select("script,style,noscript,iframe,svg").remove()
        return doc.body()?.html().orEmpty()
    }

    private fun escape(text: String): String = buildString(text.length + 16) {
        text.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '\n' -> append("<br>")
                else -> append(ch)
            }
        }
    }

    private fun readZip(bytes: ByteArray): Map<String, String> {
        val entries = HashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (isTextEntry(name)) {
                    runCatching {
                        entries[name] = String(zip.readBytes(), Charsets.UTF_8)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun isTextEntry(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".opf") || lower.endsWith(".ncx") ||
            lower.endsWith(".xml") || lower.endsWith(".html") ||
            lower.endsWith(".xhtml") || lower.endsWith(".htm")
    }

    private fun parseXml(text: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
        }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(text)))
    }

    private fun firstTagText(doc: Document, tag: String): String? =
        doc.getElementsByTagName(tag).item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }

    /** EPUB 里的 href 是相对 OPF / NCX 所在目录的，要拼回 zip 内的完整路径 */
    private fun resolvePath(baseDir: String, href: String): String {
        val clean = href.trim().replace('\\', '/')
        val joined = when {
            clean.startsWith("/") -> clean.removePrefix("/")
            baseDir.isBlank() -> clean
            else -> "$baseDir/$clean"
        }
        val parts = ArrayList<String>()
        joined.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(part)
            }
        }
        return parts.joinToString("/")
    }

    private fun decode(value: String): String = runCatching {
        java.net.URLDecoder.decode(value, "UTF-8")
    }.getOrDefault(value)
}
