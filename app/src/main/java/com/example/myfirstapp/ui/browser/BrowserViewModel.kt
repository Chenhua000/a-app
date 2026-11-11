package com.example.myfirstapp.ui.browser

import android.app.Application
import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.data.browser.BuiltInEngines
import com.example.myfirstapp.data.browser.BrowserBookmark
import com.example.myfirstapp.data.browser.BrowserRepository
import com.example.myfirstapp.data.browser.BrowserUrl
import com.example.myfirstapp.data.browser.BrowserVisit
import com.example.myfirstapp.data.browser.SearchEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: BrowserRepository = (application as MyFirstApp).container.browserRepository

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val selectedId: StateFlow<String> = repository.selectedEngineId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuiltInEngines.defaultId)

    private val custom: StateFlow<List<SearchEngine>> = repository.customEngines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val engines: StateFlow<List<SearchEngine>> = combine(custom, selectedId) { list, _ ->
        BuiltInEngines.list + list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuiltInEngines.list)

    val currentEngine: StateFlow<SearchEngine> = combine(engines, selectedId) { all, id ->
        all.firstOrNull { it.id == id } ?: BuiltInEngines.list.first()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BuiltInEngines.list.first { it.id == BuiltInEngines.defaultId }
    )

    fun selectEngine(id: String) {
        viewModelScope.launch { repository.setSelected(id) }
    }

    fun addCustomEngine(name: String, template: String): Boolean {
        val t = template.trim()
        if (name.trim().isEmpty()) {
            _message.value = "请填写名称"
            return false
        }
        if (!BrowserUrl.validTemplate(t)) {
            _message.value = "网址要以 http(s) 开头，并包含 {query} 占位符"
            return false
        }
        viewModelScope.launch {
            repository.addCustom(
                SearchEngine(
                    id = "custom_${System.currentTimeMillis()}",
                    name = name.trim(),
                    homeUrl = BrowserUrl.homeFromTemplate(t),
                    searchTemplate = t,
                    builtIn = false
                )
            )
            _message.value = "已添加自定义搜索"
        }
        return true
    }

    fun removeCustomEngine(id: String) {
        viewModelScope.launch {
            repository.removeCustom(id)
            if (selectedId.value == id) repository.setSelected(BuiltInEngines.defaultId)
            _message.value = "已删除"
        }
    }

    val history: StateFlow<List<BrowserVisit>> = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bookmarks: StateFlow<List<BrowserBookmark>> = repository.bookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun recordVisit(url: String, title: String) {
        viewModelScope.launch { repository.recordVisit(url, title) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _message.value = "已清空访问记录"
        }
    }

    fun addBookmark(url: String, title: String) {
        viewModelScope.launch {
            repository.addBookmark(url, title)
            _message.value = "已收藏"
        }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch { repository.removeBookmark(url) }
    }

    fun notify(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    @Volatile
    private var webViewCache: WebView? = null

    /**
     * 复用同一个 WebView 实例。
     *
     * WebView 首次构造会在主线程初始化 Chromium 渲染内核（常见 1~3 秒），
     * 如果每次进入浏览器模块（甚至只是看主页）都新建一个，就会明显卡顿。
     * 这里用 applicationContext 创建并跨页面切换保留，只有 ViewModel 真正销毁时才 destroy。
     */
    /** 只取已缓存实例，绝不触发创建（主页态 / 按钮回调里用） */
    fun cachedWebView(): WebView? = webViewCache

    /** 创建或复用；只允许在真正要把 WebView attach 到视图树时调用 */
    fun webView(context: Context): WebView {
        webViewCache?.let { return it }
        return synchronized(this) {
            webViewCache ?: WebView(context.applicationContext).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
            }.also { webViewCache = it }
        }
    }

    override fun onCleared() {
        super.onCleared()
        webViewCache?.let { view ->
            runCatching {
                view.stopLoading()
                view.destroy()
            }
        }
        webViewCache = null
    }
}
