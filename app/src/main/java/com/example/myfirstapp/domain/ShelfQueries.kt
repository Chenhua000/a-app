package com.example.myfirstapp.domain

import com.example.myfirstapp.data.local.ShelfBookEntity

enum class ShelfFilter(val label: String) { ALL("全部"), LOCAL("本地"), ONLINE("在线") }
enum class ShelfSort(val label: String) { RECENT("最近阅读"), TITLE("按书名") }

fun filterShelfBooks(
    books: List<ShelfBookEntity>, query: String, filter: ShelfFilter, sort: ShelfSort
): List<ShelfBookEntity> {
    val term = query.trim()
    val filtered = books.filter { book ->
        val local = book.bookUrl.startsWith("local://")
        (filter == ShelfFilter.ALL || (filter == ShelfFilter.LOCAL) == local) &&
            (term.isEmpty() || book.title.contains(term, true) || book.author.orEmpty().contains(term, true))
    }
    return when (sort) {
        ShelfSort.RECENT -> filtered.sortedWith(compareByDescending<ShelfBookEntity> { it.lastReadAtMillis }.thenBy { it.id })
        ShelfSort.TITLE -> filtered.sortedWith(compareBy<ShelfBookEntity> { it.title.lowercase() }.thenBy { it.id })
    }
}
