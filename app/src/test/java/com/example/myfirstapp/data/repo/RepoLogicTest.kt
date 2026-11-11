package com.example.myfirstapp.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepoLogicTest {

    @Test
    fun catalogUrlsAreWellFormed() {
        // 陆续重新开放影音 / 电子书，v3.47 批量 7 条，v3.48 新增游戏模块 2 条，共 36 条
        assertTrue("内置网址不少于 36 条", RepoCatalog.sites.size >= 36)
        RepoCatalog.sites.forEach {
            assertTrue("${it.id} 必须是 https 网址", it.url.startsWith("https://"))
            assertFalse("${it.id} 网址不能含空格", it.url.contains(" "))
            assertTrue("${it.id} 得有名称", it.name.isNotBlank())
            assertTrue("${it.id} 得有简介", it.desc.isNotBlank())
        }
    }

    @Test
    fun catalogIdsAreUnique() {
        val ids = RepoCatalog.sites.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it.contains(" ") })
    }

    @Test
    fun everyCategoryHasSites() {
        // 影音、电子书重新开放，v3.48 新增游戏模块
        assertTrue("影音至少 1 条", RepoCatalog.sites.count { it.category == RepoCategory.MEDIA } >= 1)
        assertTrue("游戏至少 1 条", RepoCatalog.sites.count { it.category == RepoCategory.GAME } >= 1)
        assertTrue("电子书至少 1 条", RepoCatalog.sites.count { it.category == RepoCategory.BOOKS } >= 1)
        // 生活、学习按需求精简（生活 3 条、学习 3 条）
        assertTrue("生活至少 2 条", RepoCatalog.sites.count { it.category == RepoCategory.LIFE } >= 2)
        assertTrue("学习至少 3 条", RepoCatalog.sites.count { it.category == RepoCategory.STUDY } >= 3)
        // 工具、开发仍要够用；博客与软件按需求大精简，只保证模块还有内容
        listOf(RepoCategory.UTILS, RepoCategory.DEV).forEach { c ->
            assertTrue("$c 至少要有 4 个网址", RepoCatalog.sites.count { it.category == c } >= 4)
        }
        listOf(RepoCategory.BLOG, RepoCategory.SOFTWARE).forEach { c ->
            assertTrue("$c 至少 1 条", RepoCatalog.sites.count { it.category == c } >= 1)
        }
    }

    @Test
    fun newSitesAreFlaggedAndSpread() {
        val fresh = RepoCatalog.sites.filter { it.isNew }
        // 工具/博客下架时一并删掉若干带「新」角标的条目，阈值相应下调
        assertTrue("新收录至少 6 条", fresh.size >= 6)
        // 新收录不能全都是同一个分类，否则「新收录」分组会显得单薄
        assertTrue("新收录应覆盖多个分类", fresh.map { it.category }.toSet().size >= 4)
        // 内置站点不归属「常用」（常用是动态生成的页），不放「新」角标
        assertTrue(fresh.none { it.category == RepoCategory.RECENT })
    }

    @Test
    fun mediaCategoryContainsClicli() {
        val site = RepoCatalog.sites.firstOrNull { it.id == "media_clicli" }
        assertTrue("影音分类应收录 CLICLI 动漫", site != null)
        assertEquals("https://www.cliclidm.com/", site!!.url)
        assertEquals(RepoCategory.MEDIA, site.category)
        assertTrue(site.builtIn)
        assertTrue(site.isNew)
    }

    @Test
    fun booksCategoryContainsZLibrary() {
        val site = RepoCatalog.sites.firstOrNull { it.id == "book_zlibrary" }
        assertTrue("电子书分类应收录 Z-Library", site != null)
        assertEquals("https://zh.vbh101.ru/", site!!.url)
        assertEquals(RepoCategory.BOOKS, site.category)
        assertTrue(site.desc.contains("电子书"))
        assertTrue(site.builtIn)
        assertTrue(site.isNew)
    }

    @Test
    fun v347BatchSitesAreCategorized() {
        // 用户一次性给的 7 个网址，按用途划分；无法单一归类的按约定放生活服务
        // v3.48 新增游戏模块后，Baoku One 从生活迁入游戏（id 保持不变）
        val expected = listOf(
            Triple("life_bird", "https://b.oe.do/", RepoCategory.LIFE),
            Triple("soft_plainapp", "https://plainapp.app/zh", RepoCategory.SOFTWARE),
            Triple("book_yuancang", "https://yuancang.cc/", RepoCategory.BOOKS),
            Triple("soft_webtorrent", "https://webtorrent.io/", RepoCategory.SOFTWARE),
            Triple("life_baoku", "https://baoku.one/", RepoCategory.GAME),
            Triple("book_ixdzs", "https://ixdzs8.com", RepoCategory.BOOKS),
            Triple("ut_sendwang", "https://send.wang/", RepoCategory.UTILS)
        )
        expected.forEach { (id, url, category) ->
            val site = RepoCatalog.sites.firstOrNull { it.id == id }
            assertTrue("$id 应已收录", site != null)
            assertEquals("$id 网址", url, site!!.url)
            assertEquals("$id 分类", category, site.category)
            assertTrue("$id 应为内置站点", site.builtIn)
            assertTrue("$id 应带新角标", site.isNew)
        }
        // 原始清单里 webtorrent.io 带末尾 # 片段、send.wang 带开头多余斜杠，收录时已规范
        assertTrue("网址不能残留 # 片段", RepoCatalog.sites.none { it.url.contains("#") })
        assertTrue("网址不能以 / 开头", RepoCatalog.sites.none { it.url.startsWith("/") })
    }

    @Test
    fun v348GameModuleHasSites() {
        assertEquals("游戏娱乐", RepoCategory.GAME.label)
        assertEquals("游戏", RepoCategory.GAME.short)
        assertTrue(RepoCategory.fromId("GAME") == RepoCategory.GAME)

        val games = RepoCatalog.sites.filter { it.category == RepoCategory.GAME }
        assertEquals("游戏模块应有 3 条", 3, games.size)
        listOf(
            Triple("game_20find", "https://20find.com/", "摸鱼解谜"),
            Triple("game_heeee", "https://heeee.com/", "资源网"),
            Triple("life_baoku", "https://baoku.one/", "Baoku One")
        ).forEach { (id, url, name) ->
            val site = games.firstOrNull { it.id == id }
            assertTrue("$id 应在游戏模块", site != null)
            assertEquals("$id 网址", url, site!!.url)
            assertEquals("$id 名称", name, site.name)
            assertTrue(site.builtIn)
            assertTrue(site.isNew)
        }
        // Baoku One 迁走后生活只剩 3 条，id 不变所以已有收藏不会丢
        assertEquals(3, RepoCatalog.sites.count { it.category == RepoCategory.LIFE })
        assertEquals("本版共 36 条", 36, RepoCatalog.sites.size)
    }

    @Test
    fun catalogUrlsAreDistinct() {
        val urls = RepoCatalog.sites.map { it.url.lowercase().trimEnd('/') }
        val dup = urls.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue("存在重复网址：$dup", dup.isEmpty())
    }

    @Test
    fun catalogCoversNavModules() {
        // 保留且有内容的模块都要达标；影音、电子书、游戏都已开放
        listOf(RepoCategory.UTILS, RepoCategory.DEV).forEach { c ->
            assertTrue("$c 必须有内容", RepoCatalog.sites.count { it.category == c } >= 4)
        }
        listOf(RepoCategory.BLOG, RepoCategory.SOFTWARE).forEach { c ->
            assertTrue("$c 模块仍要有内容", RepoCatalog.sites.count { it.category == c } >= 1)
        }
        assertTrue("影音模块仍要有内容", RepoCatalog.sites.count { it.category == RepoCategory.MEDIA } >= 1)
        assertTrue("游戏模块仍要有内容", RepoCatalog.sites.count { it.category == RepoCategory.GAME } >= 1)
        assertTrue("电子书模块仍要有内容", RepoCatalog.sites.count { it.category == RepoCategory.BOOKS } >= 1)
        // 已删除的模块不应再出现在枚举里
        listOf("AI_CHAT", "AI_TOOLS", "DESIGN").forEach { name ->
            assertFalse("$name 应已删除", RepoCategory.entries.any { it.name == name })
        }
    }

    @Test
    fun customSiteRoundTrip() {
        val list = listOf(
            RepoSite(
                "c1", "我的站点", "https://a.com/x?q=1", "说明|带竖线%",
                RepoCategory.DEV, listOf("自定义", "开发"), false
            ),
            RepoSite(
                "c2", "多行\n名称", "https://b.com", "第二行\n说明",
                RepoCategory.LIFE, listOf("标签A"), false
            )
        )
        assertEquals(list, RepoSiteCodec.decode(RepoSiteCodec.encode(list)))
    }

    @Test
    fun builtInSitesAreNotPersisted() {
        val mixed = listOf(
            RepoSite("b1", "内置", "https://x.com", "", RepoCategory.RECENT),
            RepoSite("c1", "自定义", "https://y.com", "", RepoCategory.UTILS, builtIn = false)
        )
        val decoded = RepoSiteCodec.decode(RepoSiteCodec.encode(mixed))
        assertEquals(1, decoded.size)
        assertEquals("c1", decoded.first().id)
        assertFalse(decoded.first().builtIn)
    }

    @Test
    fun malformedLinesAreSkipped() {
        val raw = "c1\u001FN\u001Fhttps://a.com\u001FD\u001FUTILS\u001Fx\nbroken\n\n"
        val decoded = RepoSiteCodec.decode(raw)
        assertEquals(1, decoded.size)
        assertEquals(RepoCategory.UTILS, decoded.first().category)
        assertTrue(RepoSiteCodec.decode(null).isEmpty())
    }

    @Test
    fun unknownCategoryFallsBackToUtils() {
        val raw = "c1\u001FN\u001Fhttps://a.com\u001FD\u001FNO_SUCH\u001Fx"
        assertEquals(RepoCategory.UTILS, RepoSiteCodec.decode(raw).first().category)
    }
}
