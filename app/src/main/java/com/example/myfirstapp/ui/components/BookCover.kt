package com.example.myfirstapp.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import com.example.myfirstapp.ui.theme.onColorFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 书封面。
 *
 * 没引入第三方图片库（一是包体积，二是国内镜像源时常拉不动依赖），
 * 这里用一个极小的加载器：内存缓存 + 后台解码，加载失败或没有封面时
 * 退回「按书名生成的底色 + 首字」，看起来是设计过的，而不是缺图。
 */
object BookCoverCache {
    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun load(url: String): Bitmap? {
        if (url.isBlank()) return null
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
                )
                connection.inputStream.use { stream ->
                    val bytes = stream.readBytes()
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) cache.put(url, bitmap)
                    bitmap
                }
            }.getOrNull()
        }
    }
}

/** 柔和的书脊配色：同一本书永远是同一个颜色 */
private val COVER_COLORS = listOf(
    Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8), Color(0xFF9575CD),
    Color(0xFF7986CB), Color(0xFF4FC3F7), Color(0xFF4DD0E1), Color(0xFF4DB6AC),
    Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFFB74D), Color(0xFFFF8A65)
)

private fun coverColor(key: String): Color {
    if (key.isEmpty()) return COVER_COLORS.first()
    var hash = 0
    key.forEach { hash = hash * 31 + it.code }
    return COVER_COLORS[Math.floorMod(hash, COVER_COLORS.size)]
}

/**
 * 封面图：有网络封面就显示封面，没有就显示书名首字的色块。
 * 书本比例 3:4，放在列表行里刚好。
 */
@Composable
fun BookCover(
    title: String,
    coverUrl: String?,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(coverUrl) { mutableStateOf<Bitmap?>(null) }

    if (!coverUrl.isNullOrBlank()) {
        androidx.compose.runtime.LaunchedEffect(coverUrl) {
            bitmap = BookCoverCache.load(coverUrl)
        }
    }

    val shape = MaterialTheme.shapes.extraSmall
    val background = coverColor(title)
    // 封面候选色里有 0xFFAED581 浅绿、0xFFFFB74D 浅橙等粉彩，
    // 固定白字几乎不可读；按底色亮度取前景色。
    val foreground = onColorFor(background)
    Box(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .clip(shape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "$title 封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 书名常带《》【】等包裹符，首字应取真正的书名文字而不是「《」
            val initial = title.trim()
                .trimStart('《', '〈', '【', '[', '(', '「', '"')
                .firstOrNull()?.toString().orEmpty()
            if (initial.isNotEmpty()) {
                Text(
                    text = initial,
                    color = foreground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            } else {
                // 装饰性图标：书名已在列表文字里播报，避免读屏重复
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = foreground.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxSize(0.55f)
                )
            }
        }
    }
}
