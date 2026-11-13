package com.example.myfirstapp.domain

import com.example.myfirstapp.data.local.ShelfBookEntity
import org.junit.Assert.*
import org.junit.Test

class ShelfQueriesTest {
    private val books=listOf(
        ShelfBookEntity(id=1,title="Beta",author="作者甲",coverUrl=null,summary=null,sourceId="local",sourceName="本地",bookUrl="local://one",lastReadAtMillis=200),
        ShelfBookEntity(id=2,title="Alpha",author="作者乙",coverUrl=null,summary=null,sourceId="online",sourceName="网络",bookUrl="https://example.org/book",lastReadAtMillis=100)
    )
    @Test fun sourceFilterAndAuthorSearchCombine() {
        assertEquals(listOf(1L),filterShelfBooks(books,"作者甲",ShelfFilter.LOCAL,ShelfSort.RECENT).map { it.id })
        assertTrue(filterShelfBooks(books,"作者甲",ShelfFilter.ONLINE,ShelfSort.RECENT).isEmpty())
    }
    @Test fun titleAndRecentSortAreIndependent() {
        assertEquals(listOf(1L,2L),filterShelfBooks(books,"",ShelfFilter.ALL,ShelfSort.RECENT).map { it.id })
        assertEquals(listOf(2L,1L),filterShelfBooks(books,"",ShelfFilter.ALL,ShelfSort.TITLE).map { it.id })
    }
}
