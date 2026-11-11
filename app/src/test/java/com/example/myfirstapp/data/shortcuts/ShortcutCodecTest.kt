package com.example.myfirstapp.data.shortcuts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutCodecTest {

    @Test
    fun emptyAndNull() {
        assertTrue(ShortcutCodec.decode(null).isEmpty())
        assertTrue(ShortcutCodec.decode("").isEmpty())
    }

    @Test
    fun roundTripKeepsAllFields() {
        val list = listOf(
            AppShortcut(1L, "刷视频", 0, "com.example.video"),
            AppShortcut(2L, "听歌", 29, "com.example.music")
        )
        assertEquals(list, ShortcutCodec.decode(ShortcutCodec.encode(list)))
    }

    @Test
    fun specialCharactersInNameArePreserved() {
        // 名称里出现分隔符、换行、百分号，不能把编码搞坏
        val list = listOf(
            AppShortcut(7L, "第一行\n第二行|带竖线%100%", 3, "com.example.app"),
            AppShortcut(8L, "普通名称", 4, "com.example.other")
        )
        assertEquals(list, ShortcutCodec.decode(ShortcutCodec.encode(list)))
    }

    @Test
    fun malformedLinesAreSkipped() {
        val raw = "1\t名字\t0\tcom.ok.app\nbroken\n2\t名字2\t5\t"
        val decoded = ShortcutCodec.decode(raw)
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun iconIndexIsClamped() {
        val list = listOf(AppShortcut(1L, "x", 999, "com.example.x"))
        val decoded = ShortcutCodec.decode(ShortcutCodec.encode(list))
        assertEquals(1, decoded.size)
        assertEquals(29, decoded.first().iconIndex)
    }

    @Test
    fun thirtyShortcutsRoundTrip() {
        // 内置 30 个图标，逐个作为一条软链接也能完整存取
        val list = (0 until 30).map { i ->
            AppShortcut(i.toLong(), "图标$i", i, "com.example.app$i")
        }
        val decoded = ShortcutCodec.decode(ShortcutCodec.encode(list))
        assertEquals(30, decoded.size)
        assertEquals(list, decoded)
        assertEquals((0..29).toList(), decoded.map { it.iconIndex })
    }
}
