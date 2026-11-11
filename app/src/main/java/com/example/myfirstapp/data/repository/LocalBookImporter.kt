package com.example.myfirstapp.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.myfirstapp.domain.LocalBookParser
import com.example.myfirstapp.domain.ParsedLocalBook
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** 把用户选中的文件读进来，识别格式/编码，再交给纯解析器 */
object LocalBookImporter {

    suspend fun parse(context: Context, uri: Uri): ParsedLocalBook {
        val name = displayName(context, uri) ?: "未命名书籍"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("读不到这个文件，请重新选择")
        if (bytes.isEmpty()) throw IllegalArgumentException("文件是空的")

        val lower = name.lowercase()
        return when {
            lower.endsWith(".epub") || looksLikeZip(bytes) ->
                LocalBookParser.parseEpub(bytes, name)

            lower.endsWith(".txt") || lower.endsWith(".text") || lower.endsWith(".md") ->
                LocalBookParser.parseTxt(decodeText(bytes), name)

            else -> throw IllegalArgumentException("暂时只支持 TXT 和 EPUB，当前文件：$name")
        }
    }

    private fun displayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()

    private fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /** 网文 TXT 常见 UTF-8 / GBK / UTF-16，按 BOM 和严格解码自动判断 */
    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        if (canDecode(bytes, Charsets.UTF_8)) return String(bytes, Charsets.UTF_8)
        // GB18030 兼容 GBK，是中文 TXT 最常见的另一种编码
        return String(bytes, charset("GB18030"))
    }

    private fun canDecode(bytes: ByteArray, charset: java.nio.charset.Charset): Boolean = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        true
    } catch (error: CharacterCodingException) {
        false
    }
}
