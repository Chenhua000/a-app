package com.example.myfirstapp.domain

import org.jsoup.nodes.Element

/**
 * 「阅读 / Legado」风格选择器的解析与求值。
 *
 * 网络上流传的书源几乎都是给 Legado 写的，用的是它自己的一套写法，和 CSS 不完全一样：
 *
 * ```
 * id.main@tag.div!0@text        # 取 #main 下的 div，去掉第 0 个，再取文本
 * class.book-list@tag.a.2@href  # 取 .book-list 下第 2 个 a 的链接
 * tbody tr!0                    # 表格里除第一行以外的行
 * #content@textNodes            # 元素下所有文本节点（正文最常用）
 * ```
 *
 * 这里把它们翻译成「jsoup CSS + 下标运算」，全程不执行任何 JS。
 */
object LegadoSelector {

    /** 出现在 `@` 最后一段且表示「取什么值」的关键词 */
    private val ATTRIBUTES = setOf(
        "text", "owntext", "html", "textnodes", "href", "src", "title", "alt",
        "value", "content", "name", "data-src", "data-original", "data-url",
        "data-cover", "data-echo", "data-lazy-src", "style", "rel", "type"
    )

    /** 下标区间。-1 这类负数在运行时按「倒数第 N 个」换算 */
    internal data class IndexSpec(val from: Int, val to: Int)

    /** 一段（@ 分隔的）选择器 */
    internal data class Step(val css: String, val index: IndexSpec?, val exclude: IndexSpec?)

    /** 一条完整规则（不含 || 备用）。regexes 是 `##正则##替换` 部分，取到值后逐一替换 */
    internal data class Rule(
        val steps: List<Step>,
        val attr: String,
        val regexes: List<Pair<String, String>> = emptyList()
    )

    private val INDEX_TAIL = Regex("^(.*)\\.(-?\\d+(?::-?\\d+)?)$")
    private val KEYWORD_PREFIX = Regex("^(id|class|tag)\\.(.+)$")
    /** 只认结尾处的 `!0` / `!0:2`，避免误伤 CSS 的 `[attr!=value]` */
    private val EXCLUDE_TAIL = Regex("^(.*)!(-?\\d+(?::-?\\d+)?)$")

    /** 把 `||` 备用规则拆成多条，逐条解析 */
    internal fun parse(raw: String): List<Rule> {
        if (raw.isBlank()) return emptyList()
        return raw.split("||")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { parseOne(it) }
    }

    private fun parseOne(partRaw: String): Rule? {
        // Legado 的 `##正则##替换` 写在选择器后面。它必须**先摘出来**，否则会被当成一段 CSS
        // 塞进步骤里 → jsoup 解析失败 → 整条规则失效（正文源最常见：`id.content@textNodes##请记住本书.*`）。
        val pieces = partRaw.split("##")
        val part = pieces[0].trim()
        val regexes = parseRegexes(pieces.drop(1))
        val segments = part.split("@")
        val last = segments.last().trim().lowercase()
        // 最后一段是属性关键词 → 它是取值方式；否则整段都是选择器链
        val attr = if (segments.size > 1 && last in ATTRIBUTES) last else "text"
        val selectorParts = if (attr == "text" && last !in ATTRIBUTES) segments else segments.dropLast(1)
        // 允许 steps 为空：像 `@href` 这种规则表示「就取当前元素自己的属性」
        val steps = selectorParts.map { it.trim() }.filter { it.isNotBlank() }.map { parseStep(it) }
        return Rule(steps, attr, regexes)
    }

    /** 从 `##` 之后的部分解析出 (正则, 替换) 列表；`replace` 缺省为空串（即删除匹配内容） */
    private fun parseRegexes(rest: List<String>): List<Pair<String, String>> {
        if (rest.isEmpty()) return emptyList()
        val out = ArrayList<Pair<String, String>>()
        var i = 0
        while (i < rest.size) {
            val rx = rest[i]
            val rep = if (i + 1 < rest.size) rest[i + 1] else ""
            if (rx.isNotBlank()) out += rx to rep
            i += 2
        }
        return out
    }

    /** 供正文抽取复用的 `##正则##替换`（取第一条备选规则里的）。 */
    fun regexReplacements(raw: String): List<Pair<String, String>> {
        val first = raw.split("||").firstOrNull()?.trim() ?: return emptyList()
        return parseRegexes(first.split("##").drop(1))
    }

    private fun applyRegexes(text: String, regexes: List<Pair<String, String>>): String {
        if (regexes.isEmpty()) return text
        var r = text
        regexes.forEach { (rx, rep) -> r = runCatching { r.replace(Regex(rx), rep) }.getOrDefault(r) }
        return r
    }

    private fun parseStep(raw: String): Step {
        var text = raw.trim()

        // !0 / !0:2 —— 排除下标
        var exclude: IndexSpec? = null
        EXCLUDE_TAIL.find(text)?.let { match ->
            exclude = parseIndex(match.groupValues[2])
            text = match.groupValues[1]
        }

        // .2 / .-1 / .1:3 —— 取第 N 个（或区间）
        var index: IndexSpec? = null
        INDEX_TAIL.find(text)?.let { match ->
            index = parseIndex(match.groupValues[2])
            text = match.groupValues[1]
        }

        // id.x → #x，class.x → .x，tag.x → x
        KEYWORD_PREFIX.find(text)?.let { match ->
            text = when (match.groupValues[1]) {
                "id" -> "#" + match.groupValues[2]
                "class" -> "." + match.groupValues[2]
                else -> match.groupValues[2]
            }
        }
        return Step(text.trim(), index, exclude)
    }

    private fun parseIndex(value: String): IndexSpec? {
        val parts = value.split(":")
        val first = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        val second = parts.getOrNull(1)?.trim()?.toIntOrNull()
        return if (second == null) IndexSpec(first, first) else IndexSpec(first, second)
    }

    /** 按规则选出元素：先走第一段，再在结果里逐段往下找 */
    fun select(root: Element, raw: String): List<Element> {
        parse(raw).forEach { rule ->
            val found = selectRule(root, rule)
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    private fun selectRule(root: Element, rule: Rule): List<Element> {
        var current: List<Element> = listOf(root)
        for (step in rule.steps) {
            if (step.css.isBlank()) continue
            // 先把这一层所有父节点下的结果收集到一起，再统一算下标。
            // （逐个父节点各自算下标是错的：`a.-1` 会变成「每行的最后一个」而不是「整页的最后一个」）
            val collected = LinkedHashSet<Element>()
            for (node in current) {
                val found = runCatching { node.select(step.css) }.getOrNull() ?: continue
                // jsoup 的 Element.select 会把元素自身也算进去，Legado 的 @ 链只找下级，要排除掉
                found.forEach { if (it !== node) collected.add(it) }
            }
            current = applyIndex(collected.toList(), step.index, step.exclude)
            if (current.isEmpty()) return emptyList()
        }
        return current
    }

    private fun applyIndex(list: List<Element>, index: IndexSpec?, exclude: IndexSpec?): List<Element> {
        var result = list
        if (index != null) {
            val (from, to) = resolveRange(index, list.size)
            result = result.filterIndexed { i, _ -> i in from..to }
        }
        if (exclude != null) {
            val (from, to) = resolveRange(exclude, result.size)
            result = result.filterIndexed { i, _ -> i !in from..to }
        }
        return result
    }

    private fun resolveRange(spec: IndexSpec, size: Int): Pair<Int, Int> {
        if (size <= 0) return 0 to -1
        val from = if (spec.from < 0) (size + spec.from).coerceAtLeast(0) else spec.from.coerceAtMost(size - 1)
        val to = if (spec.to < 0) (size + spec.to).coerceAtLeast(0) else spec.to.coerceAtMost(size - 1)
        return if (from <= to) from to to else to to from
    }

    /**
     * 取一个值。规则为空时默认取文本；正文规则常用 `@html` / `@textNodes`。
     * 网址类属性会自动补成绝对地址。
     */
    fun value(root: Element, raw: String, baseUrl: String, defaultAttr: String = "text"): String {
        parse(raw).forEach { rule ->
            val elements = selectRule(root, rule)
            val attr = rule.attr.takeIf { it != "text" } ?: defaultAttr
            elements.forEach { element ->
                val text = applyRegexes(readValue(element, attr, baseUrl), rule.regexes)
                if (text.isNotBlank()) return text
            }
        }
        return ""
    }

    /** 取一组值（列表页用，只认第一条命中的规则） */
    fun values(root: Element, raw: String, baseUrl: String, defaultAttr: String = "text"): List<String> {
        val rule = parse(raw).firstOrNull() ?: return emptyList()
        val attr = rule.attr.takeIf { it != "text" } ?: defaultAttr
        return selectRule(root, rule).map { applyRegexes(readValue(it, attr, baseUrl), rule.regexes) }
    }

    private fun collectTextNodes(element: Element, out: MutableList<org.jsoup.nodes.TextNode>) {
        out.addAll(element.textNodes())
        element.children().forEach { collectTextNodes(it, out) }
    }

    private fun readValue(element: Element, attr: String, baseUrl: String): String = when (attr) {
        "text", "" -> element.text().trim()
        "owntext" -> element.ownText().trim()
        "html" -> element.html()
        // 正文通常嵌在 <p> 里，所以要取整个子树里的文本节点，只取直接子节点会全是空白
        "textnodes" -> ArrayList<org.jsoup.nodes.TextNode>()
            .also { collectTextNodes(element, it) }
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        else -> HtmlRules.resolveUrl(element.attr(attr).trim(), baseUrl)
    }
}
