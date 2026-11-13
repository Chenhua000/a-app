package com.example.myfirstapp.data.bookstore

import com.example.myfirstapp.domain.BookRulesCodec
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 回归测试：内置笔趣阁书源包必须能被 BookRepository.importSources 完整导入。
 * 校验条目数、稳定 id、必填字段、默认启用、规则可被 BookRulesCodec 解析。
 */
class LegadoSourcesTest {

    private fun sources(): JSONArray {
        val file = File("src/main/assets/legado_sources.json")
        assertTrue("书源包不存在：${file.absolutePath}", file.exists())
        return JSONArray(file.readText())
    }

    @Test
    fun allSourcesAreImportable() {
        val array = sources()
        assertEquals("笔趣阁书源应全量导入（已移除 56 书库 3 条）", 1741, array.length())
        val ids = mutableSetOf<String>()
        var complete = 0
        var enabledCount = 0
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val id = item.optString("id")
            assertTrue("第 $i 条缺 id", id.isNotBlank())
            assertTrue("id 重复：$id", ids.add(id))
            assertTrue("$id 缺名称", item.optString("name").isNotBlank())
            assertTrue("$id 缺搜索地址", item.optString("searchUrl").isNotBlank())
            // 只启用笔趣阁原本开启的 654 条（其余 1090 条默认关闭，避免搜索时混入坏源）
            if (item.optBoolean("enabled", false)) enabledCount++
            assertTrue("$id 缺分组", item.optString("group").isNotBlank())

            val rules = BookRulesCodec.fromJson(item.optJSONObject("rules")?.toString().orEmpty())
            assertNotNull("$id 的规则无法解析", rules)
            if (rules!!.search.list.isNotBlank() &&
                rules.toc.list.isNotBlank() &&
                rules.content.selector.isNotBlank()
            ) complete++
        }
        assertTrue("规则齐全的书源应超过 1000 条，实际 $complete", complete > 1000)
        assertEquals("应只启用笔趣阁原本开启的 653 条", 653, enabledCount)
        assertTrue("不能全启用（否则会搜到坏源导致一本都搜不到）", enabledCount < array.length())
    }

    @Test
    fun availabilityGroupsArePresent() {
        val array = sources()
        val groups = mutableMapOf<String, Int>()
        for (i in 0 until array.length()) {
            val group = array.getJSONObject(i).optString("group")
            groups[group] = (groups[group] ?: 0) + 1
        }
        assertEquals(1741, groups.values.sum())
        assertTrue("应标记可用源", (groups["笔趣阁·可用"] ?: 0) > 1000)
        assertTrue("应标记含 JS 规则的源", (groups["笔趣阁·JS规则"] ?: 0) > 100)
        assertTrue("应标记 POST 搜索的源", (groups["笔趣阁·POST搜索"] ?: 0) > 0)
    }

    @Test
    fun bookstoreDataIsWellFormed() {
        val file = File("src/main/assets/bookstore_data.json")
        assertTrue("书城数据不存在：${file.absolutePath}", file.exists())
        val parsed = BookstoreCatalog.parse(file.readText())
        assertEquals(8, parsed.male.categories.size)
        assertEquals(8, parsed.female.categories.size)
        assertTrue(parsed.male.categories.all { it.books.isNotEmpty() })
        assertTrue(parsed.male.ranks["day"].orEmpty().isNotEmpty())
        assertTrue(parsed.female.ranks["month"].orEmpty().isNotEmpty())
    }
}
