package com.example.myfirstapp.ui.idea

import androidx.compose.ui.graphics.Color
import com.example.myfirstapp.ui.theme.onColorFor

/**
 * 灵感便签的 6 色调色板，借鉴 Google Keep 的彩色卡片：一眼区分、随手分类。
 * 这是刻意固定的设计配色，与 MaterialTheme 的主题色解耦（改主题不会改动便签色）；
 * 浅色用柔和粉彩，深色模式用降饱和的深色，文字色则按底色亮度自适应，保证两种主题下都清晰。
 */
private val LIGHT_COLORS = listOf(
    0xFFFFFFFF.toInt(), // 0 默认白
    0xFFFDE68A.toInt(), // 1 黄
    0xFFC8E6C9.toInt(), // 2 绿
    0xFFB3E5FC.toInt(), // 3 蓝
    0xFFD1C4E9.toInt(), // 4 紫
    0xFFF8BBD0.toInt()  // 5 粉
)

private val DARK_COLORS = listOf(
    0xFF2B2B2B.toInt(), // 0
    0xFF5D5424.toInt(), // 1
    0xFF2F4A23.toInt(), // 2
    0xFF214049.toInt(), // 3
    0xFF392F4D.toInt(), // 4
    0xFF4A2F3D.toInt()  // 5
)

/** 便签卡片背景色 */
fun ideaNoteColor(index: Int, dark: Boolean): Color {
    val palette = if (dark) DARK_COLORS else LIGHT_COLORS
    return Color(palette[index.coerceIn(0, palette.lastIndex)])
}

/**
 * 便签上的文字颜色。
 *
 * 这 6 组配色是刻意选定的设计色（借鉴 Keep 的彩色卡片），与 MaterialTheme 的主题色解耦；
 * 但文字色不写死，统一按便签底色亮度用 onColorFor 推导：浅底用深字、深底用亮字，
 * 这样以后调色板改了颜色也不会出现白字压浅底这类对比度问题。
 * 签名沿用 (dark) -> Color 的历史约定：同一主题下调色板各色亮度一致（浅色主题全浅底、
 * 深色主题全深底），故用该主题的默认便签色作代表即可推导出统一且可读的前景色。
 */
fun ideaTextColor(dark: Boolean): Color =
    onColorFor(ideaNoteColor(0, dark))

val IDEA_COLOR_COUNT = LIGHT_COLORS.size
