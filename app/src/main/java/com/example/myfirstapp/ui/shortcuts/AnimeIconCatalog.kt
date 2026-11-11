package com.example.myfirstapp.ui.shortcuts

import androidx.annotation.DrawableRes
import com.example.myfirstapp.R

/** 内置 30 个动漫风图标，供软链接自定义图标时挑选 */
object AnimeIconCatalog {
    @DrawableRes
    val icons: List<Int> = listOf(
        R.drawable.ic_anime_01, R.drawable.ic_anime_02, R.drawable.ic_anime_03,
        R.drawable.ic_anime_04, R.drawable.ic_anime_05, R.drawable.ic_anime_06,
        R.drawable.ic_anime_07, R.drawable.ic_anime_08, R.drawable.ic_anime_09,
        R.drawable.ic_anime_10, R.drawable.ic_anime_11, R.drawable.ic_anime_12,
        R.drawable.ic_anime_13, R.drawable.ic_anime_14, R.drawable.ic_anime_15,
        R.drawable.ic_anime_16, R.drawable.ic_anime_17, R.drawable.ic_anime_18,
        R.drawable.ic_anime_19, R.drawable.ic_anime_20, R.drawable.ic_anime_21,
        R.drawable.ic_anime_22, R.drawable.ic_anime_23, R.drawable.ic_anime_24,
        R.drawable.ic_anime_25, R.drawable.ic_anime_26, R.drawable.ic_anime_27,
        R.drawable.ic_anime_28, R.drawable.ic_anime_29, R.drawable.ic_anime_30
    )

    val count: Int get() = icons.size

    @DrawableRes
    fun resOf(index: Int): Int = icons[index.coerceIn(0, icons.lastIndex)]
}
