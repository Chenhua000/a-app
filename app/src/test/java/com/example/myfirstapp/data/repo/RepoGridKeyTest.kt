package com.example.myfirstapp.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归测试：仓库「常用」页（以及其它分类页）的网格行 key 必须全局唯一，
 * 否则同一个站点在不同分组重复出现时会触发 LazyVerticalGrid 的
 * "Key was already used" 直接闪退。
 */
class RepoGridKeyTest {

    private val all = RepoCatalog.sites

    @Test
    fun recentPageKeysAreUnique() {
        val rows = RepoGridBuilder.build(all, RepoCategory.RECENT, "")
        val keys = rows.map { it.key }
        assertEquals("常用页出现重复 key: ${duplicates(keys)}", keys.size, keys.toSet().size)
        assertTrue(rows.isNotEmpty())
    }

    @Test
    fun everyCategoryKeysAreUnique() {
        RepoCategory.entries.filter { it != RepoCategory.MY_APK }.forEach { c ->
            val keys = RepoGridBuilder.build(all, c, "").map { it.key }
            assertEquals("$c 出现重复 key: ${duplicates(keys)}", keys.size, keys.toSet().size)
        }
    }

    @Test
    fun searchKeysAreUnique() {
        listOf("AI", "https", "搜索", "工具", "a").forEach { q ->
            val keys = RepoGridBuilder.build(all, RepoCategory.RECENT, q).map { it.key }
            assertEquals("搜索 $q 出现重复 key", keys.size, keys.toSet().size)
        }
    }

    @Test
    fun customSitesDoNotCreateDuplicateKeys() {
        // 用户添加一条与内置同名的自定义站点，id 不同，key 也不能撞
        val custom = RepoSite(
            "custom_1", "哔哩哔哩", "https://mirror.example.com", "镜像",
            RepoCategory.RECENT, listOf("自定义"), false
        )
        val keys = RepoGridBuilder.build(all + custom, RepoCategory.RECENT, "")
            .map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun recentPageSlotsHaveUniqueKeys() {
        // 5 个收藏槽 + 最近使用，即便同一个站点同时出现在两区，key 也得唯一
        val fav = listOf("ut_convertio", "blog_cnblogs", "dev_leetcode")
        val recent = listOf("dev_leetcode", "ut_convertio", "soft_ossnav")
        val rows = RepoGridBuilder.build(all, RepoCategory.RECENT, "", fav, recent)
        val keys = rows.map { it.key }
        assertEquals("常用页出现重复 key: ${duplicates(keys)}", keys.size, keys.toSet().size)
        // 只渲染实际收藏的 3 个站点，不再补「收藏位 N」空槽
        assertEquals(3, rows.count { it.favoriteSlotIndex >= 0 })
        assertEquals(0, rows.count { it.isPlaceholder && it.favoriteSlotIndex >= 0 })
        assertTrue(rows.any { it.key == "recent_fav_header" })
        assertTrue(rows.any { it.key == "recent_used_header" })
    }

    @Test
    fun headersSpanFullLineAndHaveUniqueKeys() {
        // 没收藏时：常用页只显示「最近使用」一个标题区，不显示「收藏」区
        val noFav = RepoGridBuilder.build(all, RepoCategory.RECENT, "")
        val noFavHeaders = noFav.filter { it.isHeader }
        assertEquals("无收藏时只应有一个标题区", 1, noFavHeaders.size)
        assertTrue(noFavHeaders.any { it.key == "recent_used_header" })
        assertTrue(noFavHeaders.none { it.key == "recent_fav_header" })

        // 有收藏时：显示「收藏 · 最多 5 个」+「最近使用」两个标题区
        val withFav = RepoGridBuilder.build(
            all, RepoCategory.RECENT, "",
            listOf("ut_convertio", "blog_cnblogs"),
            listOf("dev_leetcode")
        )
        val withFavHeaders = withFav.filter { it.isHeader }
        assertEquals(2, withFavHeaders.size)
        assertEquals(withFavHeaders.size, withFavHeaders.map { it.key }.toSet().size)
        withFavHeaders.forEach { assertTrue(it.site == null) }
    }

    @Test
    fun nonPlaceholderRowsAlwaysHaveSite() {
        RepoCategory.entries.filter { it != RepoCategory.MY_APK }.forEach { c ->
            RepoGridBuilder.build(all, c, "").filterNot { it.isHeader || it.isPlaceholder }
                .forEach {
                    assertTrue("${it.key} 缺少 site", it.site != null)
                }
        }
    }

    private fun <T> duplicates(list: List<T>): List<T> =
        list.groupBy { it }.filter { it.value.size > 1 }.map { it.key }
}
