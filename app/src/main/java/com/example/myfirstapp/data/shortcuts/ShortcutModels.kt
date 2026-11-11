package com.example.myfirstapp.data.shortcuts

/** 一条软链接：自定义名称 + 内置图标 + 目标应用包名 */
data class AppShortcut(
    val id: Long,
    val name: String,
    val iconIndex: Int,
    val packageName: String
)

/** 手机上一个可启动的应用 */
data class InstalledApp(
    val label: String,
    val packageName: String
)

/**
 * 软链接列表的纯 Kotlin 编解码（不依赖 Android，方便单测）。
 * 用控制字符分隔字段，用户输入的特殊字符做百分号转义。
 */
object ShortcutCodec {
    private const val SEP = '\u001F'
    private const val LINE = '\n'

    fun encode(list: List<AppShortcut>): String =
        list.joinToString(LINE.toString()) { item ->
            listOf(
                item.id.toString(),
                escape(item.name),
                item.iconIndex.toString(),
                escape(item.packageName)
            ).joinToString(SEP.toString())
        }

    fun decode(raw: String?): List<AppShortcut> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(LINE).mapNotNull { line ->
            if (line.isEmpty()) return@mapNotNull null
            val parts = line.split(SEP)
            if (parts.size < 4) return@mapNotNull null
            val id = parts[0].toLongOrNull() ?: return@mapNotNull null
            val icon = parts[2].toIntOrNull() ?: return@mapNotNull null
            val pkg = unescape(parts[3])
            if (pkg.isEmpty()) return@mapNotNull null
            AppShortcut(
                id = id,
                name = unescape(parts[1]),
                iconIndex = icon.coerceIn(0, 29),
                packageName = pkg
            )
        }
    }

    private fun escape(value: String): String = buildString {
        value.forEach { ch ->
            when (ch) {
                '%' -> append("%25")
                SEP -> append("%1F")
                LINE -> append("%0A")
                else -> append(ch)
            }
        }
    }

    private fun unescape(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            if (i + 2 < value.length && value[i] == '%') {
                when (value.substring(i, i + 3)) {
                    "%25" -> { append('%'); i += 3; continue }
                    "%1F" -> { append(SEP); i += 3; continue }
                    "%0A" -> { append(LINE); i += 3; continue }
                }
            }
            append(value[i])
            i++
        }
    }
}
