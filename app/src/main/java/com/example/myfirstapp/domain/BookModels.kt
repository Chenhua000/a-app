package com.example.myfirstapp.domain

/** 一次搜索命中的一本书 */
data class BookSearchResult(
    val sourceId: String,
    val sourceName: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val summary: String,
    val bookUrl: String
) {
    /** 用于去重：同一本书在不同书源里算一条 */
    val dedupeKey: String get() = (title + "|" + author).trim()
}

/** 目录里的一章 */
data class BookChapter(
    val position: Int,
    val title: String,
    val url: String
)

/** 正文，可能是本地缓存也可能是刚抓下来的 */
data class ChapterContent(
    val title: String,
    val url: String,
    val html: String,
    val fromCache: Boolean
)
