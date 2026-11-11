package com.example.myfirstapp.data.repo

/** 仓库左侧分类 */
enum class RepoCategory(val label: String, val short: String) {
    RECENT("常用", "常用"),
    UTILS("实用工具", "工具"),
    MEDIA("影音娱乐", "影音"),
    GAME("游戏娱乐", "游戏"),
    STUDY("学习资源", "学习"),
    BOOKS("电子书", "电子书"),
    BLOG("博客论坛", "博客"),
    DEV("编程开发", "开发"),
    SOFTWARE("软件下载", "软件"),
    LIFE("生活服务", "生活"),
    MY_APK("我的 APK", "我的APK");

    companion object {
        fun fromId(id: String?): RepoCategory? = entries.firstOrNull { it.name == id }
    }
}

/** 一条网址/软件条目 */
data class RepoSite(
    val id: String,
    val name: String,
    val url: String,
    val desc: String,
    val category: RepoCategory,
    val tags: List<String> = emptyList(),
    val builtIn: Boolean = true,
    /** 新收录，卡片上打「新」角标，热门页单独分组 */
    val isNew: Boolean = false
)

/**
 * 网格里的一行：要么是分组标题（跨整行），要么是一张站点卡片。
 * 每一行都有全局唯一的 key，避免同一个站点在不同分组重复出现时触发
 * LazyVerticalGrid 的 "Key was already used" 崩溃。
 */
data class RepoGridRow(
    val key: String,
    val title: String? = null,
    val moreCategory: RepoCategory? = null,
    val site: RepoSite? = null,
    val removable: Boolean = false,
    /** 非空表示这一行是「编辑精选」横滑大卡（整行内部自己管理 key） */
    val heroSites: List<RepoSite> = emptyList(),
    /** >=0 表示这一行是「常用」页的收藏槽位（0..4）；isPlaceholder=true 时是空槽 */
    val favoriteSlotIndex: Int = -1,
    /** true 表示这是一个空占位卡（收藏空槽 / 暂无最近使用） */
    val isPlaceholder: Boolean = false
) {
    val isHeader: Boolean get() = title != null
    val isHero: Boolean get() = heroSites.isNotEmpty()
}

/** 纯 Kotlin 的网格行构造，方便单测校验 key 唯一性 */
object RepoGridBuilder {

    fun build(
        sites: List<RepoSite>,
        category: RepoCategory,
        query: String,
        favoriteIds: List<String> = emptyList(),
        recentIds: List<String> = emptyList()
    ): List<RepoGridRow> =
        buildRows(sites, category, query, favoriteIds, recentIds).distinctBy { it.key }

    private fun buildRows(
        sites: List<RepoSite>,
        category: RepoCategory,
        query: String,
        favoriteIds: List<String>,
        recentIds: List<String>
    ): List<RepoGridRow> {
        val rows = mutableListOf<RepoGridRow>()

        if (query.isNotBlank()) {
            val filtered = sites.filter { matches(it, query) }
            rows += RepoGridRow(key = "search_header", title = "搜索结果 · ${filtered.size}")
            filtered.forEach { rows += siteRow("search", it, removable = true) }
            return rows
        }

        if (category == RepoCategory.RECENT) {
            // 「常用」页：只显示真正收藏的站点，不再补「收藏位 N」空槽。
            // 收藏 id 指向已下架站点时也直接跳过；若一个都找不到，整段收藏区隐藏。
            val favorites = favoriteIds.take(5).mapNotNull { id ->
                sites.firstOrNull { it.id == id }
            }
            if (favorites.isNotEmpty()) {
                rows += RepoGridRow(key = "recent_fav_header", title = "收藏 · 最多 5 个")
                favorites.forEachIndexed { index, site ->
                    rows += siteRow("recent_fav", site).copy(favoriteSlotIndex = index)
                }
            }

            rows += RepoGridRow(key = "recent_used_header", title = "最近使用")
            val recent = recentIds.mapNotNull { rid -> sites.firstOrNull { it.id == rid } }
            if (recent.isEmpty()) {
                rows += RepoGridRow(key = "recent_used_empty", isPlaceholder = true)
            } else {
                recent.forEach { rows += siteRow("recent_used", it) }
            }
            return rows
        }

        val list = sites.filter { it.category == category }
        rows += RepoGridRow(
            key = "${category.name}_header",
            title = "${category.label} · ${list.size}"
        )
        list.forEach { rows += siteRow(category.name, it, removable = true) }
        return rows
    }

    private fun siteRow(prefix: String, site: RepoSite, removable: Boolean = false) =
        RepoGridRow(
            key = "${prefix}_${site.id}",
            site = site,
            removable = removable
        )

    private fun matches(site: RepoSite, query: String): Boolean =
        site.name.contains(query, ignoreCase = true) ||
            site.desc.contains(query, ignoreCase = true) ||
            site.url.contains(query, ignoreCase = true) ||
            site.tags.any { it.contains(query, ignoreCase = true) }
}

/** 用户自定义条目的纯 Kotlin 编解码（控制字符分隔 + 百分号转义，方便单测） */
object RepoSiteCodec {
    private const val SEP = '\u001F'
    private const val LINE = '\n'

    fun encode(list: List<RepoSite>): String =
        list.filterNot { it.builtIn }
            .joinToString(LINE.toString()) { s ->
                listOf(
                    escape(s.id), escape(s.name), escape(s.url), escape(s.desc),
                    s.category.name, escape(s.tags.joinToString(","))
                ).joinToString(SEP.toString())
            }

    fun decode(raw: String?): List<RepoSite> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(LINE).mapNotNull { line ->
            if (line.isEmpty()) return@mapNotNull null
            val p = line.split(SEP)
            if (p.size < 6) return@mapNotNull null
            val id = unescape(p[0])
            val url = unescape(p[2])
            if (id.isEmpty() || url.isEmpty()) return@mapNotNull null
            RepoSite(
                id = id,
                name = unescape(p[1]).ifEmpty { "未命名" },
                url = url,
                desc = unescape(p[3]),
                category = RepoCategory.fromId(p[4]) ?: RepoCategory.UTILS,
                tags = unescape(p[5]).split(",").filter { it.isNotBlank() },
                builtIn = false
            )
        }
    }

    private fun escape(v: String): String = buildString {
        v.forEach { c ->
            when (c) {
                '%' -> append("%25")
                SEP -> append("%1F")
                LINE -> append("%0A")
                else -> append(c)
            }
        }
    }

    private fun unescape(v: String): String = buildString {
        var i = 0
        while (i < v.length) {
            if (i + 2 < v.length && v[i] == '%') {
                when (v.substring(i, i + 3)) {
                    "%25" -> { append('%'); i += 3; continue }
                    "%1F" -> { append(SEP); i += 3; continue }
                    "%0A" -> { append(LINE); i += 3; continue }
                }
            }
            append(v[i]); i++
        }
    }
}
