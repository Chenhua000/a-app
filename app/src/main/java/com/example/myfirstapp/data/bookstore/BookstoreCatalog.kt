package com.example.myfirstapp.data.bookstore

import android.content.Context
import org.json.JSONObject

data class StoreBook(
    val title: String,
    val author: String,
    val cover: String,
    val intro: String
)

data class StoreCategory(
    val name: String,
    val subtitle: String,
    val books: List<StoreBook>
)

data class StoreRank(
    val title: String,
    val author: String,
    val tag: String,
    val desc: String,
    val cover: String = "",
    val score: String = ""
)

data class StoreChannel(
    val categories: List<StoreCategory>,
    val ranks: Map<String, List<StoreRank>>,
    val hot: List<String>
)

data class BookstoreData(
    val male: StoreChannel,
    val female: StoreChannel
)

/**
 * 书库首页的静态展示数据（分类卡 / 榜单 / 热搜词）。
 * 数据来自 assets/bookstore_data.json，解析失败时返回空数据，首页仍可正常显示。
 */
object BookstoreCatalog {

    fun load(context: Context): BookstoreData = runCatching {
        val text = context.assets.open("bookstore_data.json")
            .bufferedReader().use { it.readText() }
        parse(text)
    }.getOrDefault(BookstoreData(emptyChannel(), emptyChannel()))

    /** 纯 JVM 解析，方便单元测试直接校验 assets 文件 */
    fun parse(text: String): BookstoreData = runCatching {
        val root = JSONObject(text)
        BookstoreData(
            male = channel(root.optJSONObject("male")),
            female = channel(root.optJSONObject("female"))
        )
    }.getOrDefault(BookstoreData(emptyChannel(), emptyChannel()))

    private fun emptyChannel() = StoreChannel(emptyList(), emptyMap(), emptyList())

    private fun channel(node: JSONObject?): StoreChannel {
        if (node == null) return emptyChannel()

        val categories = mutableListOf<StoreCategory>()
        node.optJSONArray("categories")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val books = mutableListOf<StoreBook>()
                c.optJSONArray("books")?.let { list ->
                    for (j in 0 until list.length()) {
                        val b = list.optJSONObject(j) ?: continue
                        books += StoreBook(
                            title = b.optString("title"),
                            author = b.optString("author"),
                            cover = b.optString("cover"),
                            intro = b.optString("intro")
                        )
                    }
                }
                categories += StoreCategory(
                    name = c.optString("name"),
                    subtitle = c.optString("subtitle"),
                    books = books
                )
            }
        }

        val ranks = mutableMapOf<String, List<StoreRank>>()
        node.optJSONObject("ranks")?.let { r ->
            listOf("day", "week", "month").forEach { period ->
                val items = mutableListOf<StoreRank>()
                r.optJSONArray(period)?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val b = arr.optJSONObject(i) ?: continue
                        items += StoreRank(
                            title = b.optString("title"),
                            author = b.optString("author"),
                            tag = b.optString("tag"),
                            desc = b.optString("desc"),
                            cover = b.optString("cover"),
                            score = b.optString("score")
                        )
                    }
                }
                ranks[period] = items
            }
        }

        val hot = mutableListOf<String>()
        node.optJSONArray("hot")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let(hot::add)
            }
        }
        return StoreChannel(categories, ranks, hot)
    }
}
