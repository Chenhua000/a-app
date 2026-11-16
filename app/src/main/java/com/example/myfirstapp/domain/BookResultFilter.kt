package com.example.myfirstapp.domain

/**
 * 搜索结果的清洗。
 *
 * 有些书源（尤其自动发现出来的）search.list 选择器过宽，会把站点导航栏
 * 「首页 / 永久书架 / 玄幻 / 奇幻 / 武侠 / 排行榜」整排 <a> 当成书返回。
 * 这些条目还会共用同一个 /rank/ 链接，导致 LazyColumn 的 key 重复直接闪退。
 * 这里在合并结果时统一挡掉，并只保留和关键词相关的条目。
 */
object BookResultFilter {

    /** 明确的站点导航 / 分类 / 榜单标题，不可能是书名 */
    private val NAV_TITLES = setOf(
        "首页", "主页", "书库", "书城", "书架", "永久书架", "我的书架", "加入书架",
        "排行榜", "排行", "榜单", "总榜", "月榜", "周榜", "日榜", "分类", "分类列表",
        "完本", "全本", "完结", "连载", "最新", "最新更新", "推荐", "热门", "强推",
        "注册", "登录", "退出", "会员", "充值", "帮助", "联系我们", "留言", "论坛", "社区",
        "下载", "客户端", "app", "app下载", "专题", "标签", "更多", "更多分类",
        "下一页", "上一页", "返回", "搜索", "书友", "作家专区", "投稿", "关于我们",
        "玄幻", "奇幻", "武侠", "仙侠", "都市", "言情", "历史", "军事", "科幻",
        "游戏", "竞技", "灵异", "同人", "女生", "男生", "耽美", "轻小说", "其它"
    )

    /** 只有一段、且明显不是书籍详情的路径：/rank/ /sort/ /all/ 等 */
    private val NAV_PATHS = setOf(
        "", "index", "index.html", "home", "rank", "ranking", "top", "top.html",
        "sort", "category", "cate", "class", "list", "all", "all.html", "tag", "tags",
        "booklist", "quanben", "wanben", "search", "login", "reg", "register"
    )

    /**
     * 该条目是否其实是站点导航而不是一本书。
     * 只看标题黑名单 + 明显的分类/榜单路径，避免误杀正常的书籍详情页。
     */
    fun isNavigation(title: String, bookUrl: String): Boolean {
        val t = title.trim()
            .trim('《', '》', '[', ']', '【', '】', ' ', '\u3000')
            .lowercase()
        if (t.isEmpty()) return true
        if (t in NAV_TITLES) return true
        if (!bookUrl.startsWith("http", ignoreCase = true)) return true
        val path = runCatching { java.net.URI(bookUrl).path }.getOrNull().orEmpty()
            .trim('/').lowercase()
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return true
        return segments.size == 1 && segments[0] in NAV_PATHS
    }

    /**
     * 只保留和关键词沾边的结果。
     * 书源规则坏掉时返回的往往是一整页推荐书/导航，和搜索词完全无关；
     * 与其把这些垃圾塞给用户，不如返回空，让 UI 显示「没找到」并提示检查书源。
     */
    fun keepRelevant(results: List<BookSearchResult>, query: String): List<BookSearchResult> {
        if (results.isEmpty() || query.isBlank()) return results
        return results.filter { BookMatchRanking.score(it, query) > 0 }
    }

    /**
     * 列表展示用：按「书名 + 作者」合并成一条（跟笔趣阁一致，同一本书不在列表里重复刷屏），
     * 多出的源留在详情页里切换。
     *
     * 合并规则：
     *  - 主条目优先取「非云端源」（云端结果的地址是 QQ 介绍页，点开还要换源）；
     *  - 简介 / 封面 / 作者 谁有就用谁的（云端结果通常带简介，可以借来用）。
     * 返回顺序 = 首次出现顺序，不打断原有相关性排序。
     */
    /** 合并用的书名键：去掉空格 / 书名号 / 各种括号并转小写。
     *  不能带作者——很多源搜出来的 author 是空的，带作者会导致同一本书合并不了、列表重复。 */
    private fun titleKey(title: String): String =
        title.replace(Regex("[\\s《》()（）\\[\\]【】:：·.,，。!！?？]"), "").lowercase()

    fun mergeByBook(results: List<BookSearchResult>): List<BookSearchResult> {
        if (results.isEmpty()) return emptyList()
        val groups = LinkedHashMap<String, MutableList<BookSearchResult>>()
        results.forEach { r ->
            val key = titleKey(r.title).ifEmpty { r.dedupeKey }
            groups.getOrPut(key) { mutableListOf() }.add(r)
        }
        return groups.values.map { group ->
            val base = group.firstOrNull { it.sourceId != "cloud_qq" } ?: group.first()
            val summary = base.summary.ifBlank {
                group.firstOrNull { it.summary.isNotBlank() }?.summary.orEmpty()
            }
            val cover = base.coverUrl.ifBlank {
                group.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl.orEmpty()
            }
            val author = base.author.ifBlank {
                group.firstOrNull { it.author.isNotBlank() }?.author.orEmpty()
            }
            base.copy(summary = summary, coverUrl = cover, author = author)
        }
    }
}
