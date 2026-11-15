package com.example.myfirstapp.data.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * 极简 JS 沙箱：用系统 WebView 的 JS 引擎跑书源里的「纯字符串处理」规则。
 *
 * 支撑的是 Legado 书源里最常见的形态：
 * ```
 * @js:result.replace(/(&nbsp;)+/g, " ")
 * <js>result.replace(/<br\s*\/?>/g, "\n")</js>
 * ```
 * 其中 `result` 是被注入的原始内容（HTML/文本）。依赖 `java.ajax` / `source.*` / JSONPath 的
 * 复杂规则**跑不了**（那是完整 Legado 运行时才有的能力），会返回 null，由调用方降级到 CSS 抽取。
 *
 * 全程只在主线程碰 WebView；超时 / 异常一律返回 null，绝不影响正文主流程。
 */
object JsSandbox {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var webView: WebView? = null

    /** 能否尝试执行：含 java.* / source.* / JSONPath 的直接放弃，避免白跑一趟 */
    fun isExecutable(script: String): Boolean {
        if (script.isBlank()) return false
        val s = script.trim()
        if (s.startsWith("@JSon", ignoreCase = true)) return false
        if (s.contains("java.") || s.contains("source.") || s.contains("@JSon", true)) return false
        return true
    }

    /** 把 `@js:xxx` / `<js>xxx</js>` 剥成纯 JS 代码 */
    fun extractScript(raw: String): String {
        var s = raw.trim()
        when {
            s.startsWith("<js>", true) -> {
                s = s.substring(4)
                val end = s.indexOf("</js>", ignoreCase = true)
                if (end >= 0) s = s.substring(0, end)
            }
            s.startsWith("@js:", true) -> s = s.substring(4)
            s.startsWith("@js", true) -> s = s.substring(3)
        }
        return s.trim()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensure(context: Context): WebView {
        webView?.let { return it }
        val wv = WebView(context.applicationContext)
        wv.settings.apply {
            javaScriptEnabled = true
            blockNetworkImage = true
            loadsImagesAutomatically = false
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
        }
        // 不 attach 到窗口也能 evaluateJavascript，但需要先加载一个空文档
        wv.loadDataWithBaseURL(null, "<html><body></body></html>", "text/html", "utf-8", null)
        webView = wv
        return wv
    }

    /**
     * 执行一段 JS，`result` 变量为注入的输入，返回脚本求值结果。
     * 失败/超时返回 null。
     */
    suspend fun eval(
        context: Context,
        script: String,
        input: String,
        timeoutMs: Long = 6_000
    ): String? = suspendCancellableCoroutine { cont ->
        mainHandler.post {
            try {
                val wv = ensure(context)
                val code = buildString {
                    append("(function(){try{var result=")
                    append(JSONObject.quote(input))
                    append(";var v=eval(")
                    append(JSONObject.quote(script))
                    append(");if(v===undefined||v===null)return \"\";return String(v);}")
                    append("catch(e){return \"__JS_ERR__\";}})()")
                }
                wv.evaluateJavascript(code) { raw ->
                    if (cont.isActive) cont.resume(decode(raw))
                }
                mainHandler.postDelayed({
                    if (cont.isActive) cont.resume(null)
                }, timeoutMs)
            } catch (t: Throwable) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    /** evaluateJavascript 返回的是 JSON 编码字符串（带引号、转义），解回原文 */
    private fun decode(raw: String?): String? {
        if (raw == null) return null
        val v = runCatching { JSONTokener(raw).nextValue() }.getOrNull() ?: return null
        val s = v as? String ?: return null
        if (s == "__JS_ERR__" || s.isBlank()) return null
        return s
    }
}
