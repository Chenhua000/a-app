package com.example.myfirstapp.ui.settings

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.data.settings.RowHeightMode
import com.example.myfirstapp.ui.navigation.AppTab
import com.example.myfirstapp.ui.navigation.defaultModuleOrder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MyFirstApp
    private val settingsRepository = app.container.settingsRepository

    val notificationsEnabled: StateFlow<Boolean> = settingsRepository.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val defaultReminderMinutes: StateFlow<Int> = settingsRepository.defaultReminderMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 15)

    val showWeekend: StateFlow<Boolean> = settingsRepository.showWeekend
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val rowHeightMode: StateFlow<RowHeightMode> = settingsRepository.rowHeightMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RowHeightMode.STANDARD)

    val moduleOrder: StateFlow<List<AppTab>> = settingsRepository.moduleOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), defaultModuleOrder)

    val hiddenModules: StateFlow<Set<AppTab>> = settingsRepository.hiddenModules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** 显示/隐藏某个模块 */
    fun setModuleVisible(tab: AppTab, visible: Boolean) {
        viewModelScope.launch { settingsRepository.setModuleHidden(tab, !visible) }
    }

    /** 上移 / 下移某个模块 */
    fun moveModule(tab: AppTab, up: Boolean) {
        viewModelScope.launch {
            val order = settingsRepository.currentModuleOrder().toMutableList()
            val from = order.indexOf(tab)
            if (from < 0) return@launch
            val to = if (up) from - 1 else from + 1
            if (to !in order.indices) return@launch
            val tmp = order[from]
            order[from] = order[to]
            order[to] = tmp
            settingsRepository.setModuleOrder(order)
        }
    }

    fun systemNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(app.applicationContext).areNotificationsEnabled()

    fun exactAlarmAllowed(): Boolean = app.container.canScheduleExact

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationsEnabled(enabled)
            app.container.reminderCoordinator.refresh()
        }
    }

    fun setDefaultReminderMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setDefaultReminderMinutes(minutes)
            app.container.reminderCoordinator.refresh()
        }
    }

    fun setShowWeekend(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowWeekend(enabled) }
    }

    fun setRowHeightMode(mode: RowHeightMode) {
        viewModelScope.launch { settingsRepository.setRowHeightMode(mode) }
    }

    fun sendTestNotification() {
        app.container.publishTestNotification(
            "这是一条测试提醒",
            "如果你在手机顶部看到这条通知，说明通知权限已经开启。课程和待办提醒会走同一个通道。"
        )
    }
}
