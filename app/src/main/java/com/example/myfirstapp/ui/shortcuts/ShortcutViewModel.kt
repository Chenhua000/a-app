package com.example.myfirstapp.ui.shortcuts

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.data.shortcuts.AppShortcut
import com.example.myfirstapp.data.shortcuts.InstalledApp
import com.example.myfirstapp.data.shortcuts.ShortcutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShortcutViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MyFirstApp
    private val repository: ShortcutRepository = app.container.shortcutRepository

    val shortcuts: StateFlow<List<AppShortcut>> = repository.shortcuts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        refreshInstalledApps()
    }

    /** 列出手机上所有可启动的应用，按名称排序 */
    fun refreshInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = withContext(Dispatchers.IO) {
                val pm = app.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                resolved.mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == app.packageName) return@mapNotNull null // 不链接自己
                    val label = try {
                        info.loadLabel(pm).toString()
                    } catch (e: Exception) {
                        pkg
                    }
                    InstalledApp(label = label, packageName = pkg)
                }
                    .distinctBy { it.packageName }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            }
        }
    }

    fun save(shortcut: AppShortcut) {
        viewModelScope.launch {
            val current = shortcuts.value.toMutableList()
            val index = current.indexOfFirst { it.id == shortcut.id }
            if (index >= 0) current[index] = shortcut else current.add(shortcut)
            repository.replaceAll(current)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.replaceAll(shortcuts.value.filterNot { it.id == id })
        }
    }

    fun nextId(): Long = (shortcuts.value.maxOfOrNull { it.id } ?: 0L) + 1L

    /** 点击软链接：启动对应应用，失败给出提示 */
    fun launch(packageName: String) {
        val pm = app.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            _message.value = "找不到这个应用，可能已被卸载"
            return
        }
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        } catch (e: Exception) {
            _message.value = "无法打开应用：${e.localizedMessage ?: "未知错误"}"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
