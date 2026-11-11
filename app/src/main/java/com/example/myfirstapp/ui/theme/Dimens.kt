package com.example.myfirstapp.ui.theme

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * 全 App 共用的尺寸令牌。
 * 以前各页散落 2.5 / 5 / 7 / 10 / 13 / 14 / 18 / 22 / 26 / 27.dp，
 * 视觉节奏无法对齐；改版后统一走 4dp 网格。
 */
object AppDimens {
    val Space1 = 4.dp
    val Space2 = 8.dp
    val Space3 = 12.dp
    val Space4 = 16.dp
    val Space5 = 20.dp
    val Space6 = 24.dp
    val Space8 = 32.dp

    /** 无障碍最小触摸目标，Material 建议 48dp */
    val MinTouch = 48.dp

    val CardRadius = 16.dp
    val ChipRadius = 12.dp
    val SheetRadius = 24.dp
    val DialogRadius = 20.dp

    val IconSmall = 18.dp
    val IconMedium = 20.dp
    val IconLarge = 24.dp

    val CardElevation = 1.dp
}

/** 保证任意可点击元素至少 48dp 触摸区，且不改变其视觉尺寸。 */
fun Modifier.minTouchTarget(): Modifier =
    this.sizeIn(minWidth = AppDimens.MinTouch, minHeight = AppDimens.MinTouch)

/**
 * 按底色亮度取可读前景色，替代各处写死的 Color.White。
 * 浅色粉彩底（封面、头像、渐变卡）上白字几乎不可读，这是本次改版修复的一类对比度 bug。
 */
fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.48f) Color(0xFF18202E) else Color.White

/** 主题是否处于深色，统一由 colorScheme 推导，避免各页各自 isSystemInDarkTheme()。 */
fun isDarkColorScheme(surface: Color): Boolean = surface.luminance() < 0.5f
