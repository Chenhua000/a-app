package com.example.myfirstapp.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfirstapp.data.settings.RowHeightMode
import com.example.myfirstapp.ui.components.AppInlineMessage
import com.example.myfirstapp.ui.components.SectionCard
import com.example.myfirstapp.ui.navigation.AppTab
import com.example.myfirstapp.ui.theme.AppDimens

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val defaultMinutes by viewModel.defaultReminderMinutes.collectAsStateWithLifecycle()
    val showWeekend by viewModel.showWeekend.collectAsStateWithLifecycle()
    val rowHeightMode by viewModel.rowHeightMode.collectAsStateWithLifecycle()
    val moduleOrder by viewModel.moduleOrder.collectAsStateWithLifecycle()
    val hiddenModules by viewModel.hiddenModules.collectAsStateWithLifecycle()

    var minutesText by remember(defaultMinutes) { mutableStateOf(defaultMinutes.toString()) }
    var message by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshKey++
        message = if (granted) "通知权限已开启" else "通知权限被拒绝，App 仍可正常使用，但不会弹系统通知"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space3)
    ) {
        val systemAllowed = remember(refreshKey) { viewModel.systemNotificationsEnabled() }
        val exactAllowed = remember(refreshKey) { viewModel.exactAlarmAllowed() }

        SectionCard(
            title = "手机通知提醒",
            icon = Icons.Default.Notifications
        ) {
            SettingToggle(
                title = "提醒总开关",
                subtitle = "关闭后不再安排课程或待办提醒，数据仍然可用",
                checked = notificationsEnabled,
                onCheckedChange = { viewModel.setNotificationsEnabled(it) }
            )
            OutlinedTextField(
                value = minutesText,
                onValueChange = { minutesText = it },
                label = { Text("默认提前提醒（分钟）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val value = minutesText.trim().toIntOrNull()
                    if (value == null || value !in 0..240) {
                        message = "请填写 0 到 240 之间的整数"
                    } else {
                        viewModel.setDefaultReminderMinutes(value)
                        message = "默认提前提醒已更新为 $value 分钟"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存默认值") }
            Text(
                text = "某一节课或某个待办单独设置过提前时间时，会覆盖这个默认值。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard(title = "权限自检", icon = Icons.Default.Shield) {
            StatusRow(label = "系统通知权限", status = if (systemAllowed) "已开启" else "未开启")
            StatusRow(
                label = "精确闹钟权限",
                status = if (exactAllowed) "已开启" else "未开启，提醒可能延迟几分钟"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            message = "这台手机不需要申请通知权限，去系统设置确认即可"
                            openNotificationSettings(context)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("申请通知权限") }
                OutlinedButton(
                    onClick = { openNotificationSettings(context) },
                    modifier = Modifier.weight(1f)
                ) { Text("通知设置") }
            }
            OutlinedButton(
                onClick = { openExactAlarmSettings(context) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("打开闹钟和提醒设置") }
            Button(
                onClick = { viewModel.sendTestNotification() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("发送一条测试提醒") }
        }

        SectionCard(
            title = "后台保活（重点）",
            subtitle = "国产手机默认会杀后台，这一步不做提醒可能被延迟",
            icon = Icons.Default.BatteryFull
        ) {
            RequirementItem(index = 1, text = "在系统设置里允许本 App「自启动」")
            RequirementItem(index = 2, text = "把本 App 加入电池优化白名单（不限制后台）")
            RequirementItem(index = 3, text = "锁定最近任务：在后台任务列表里下拉锁定本 App")
            OutlinedButton(
                onClick = { openBatteryOptimization(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.BatteryFull, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("申请忽略电池优化")
            }
        }

        SectionCard(title = "课表显示", icon = Icons.Default.GridView) {
            SettingToggle(
                title = "显示周六周日",
                subtitle = "关掉后表格只显示周一到周五，每列更宽",
                checked = showWeekend,
                onCheckedChange = { viewModel.setShowWeekend(it) }
            )
            Text(text = "每行的高度", style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                RowHeightMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = rowHeightMode == mode,
                        onClick = { viewModel.setRowHeightMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = RowHeightMode.entries.size
                        )
                    ) { Text(mode.label) }
                }
            }
        }

        SectionCard(
            title = "模块管理",
            subtitle = "隐藏不需要的模块，或用箭头调整它们在底部导航里的位置（至少保留一个）",
            icon = Icons.Default.Tune
        ) {
            val visibleCount = moduleOrder.count { it !in hiddenModules }
            moduleOrder.forEachIndexed { index, tab ->
                val visible = tab !in hiddenModules
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.moveModule(tab, up = true) },
                        enabled = index > 0
                    ) { Icon(Icons.Default.ArrowUpward, contentDescription = "上移") }
                    IconButton(
                        onClick = { viewModel.moveModule(tab, up = false) },
                        enabled = index < moduleOrder.lastIndex
                    ) { Icon(Icons.Default.ArrowDownward, contentDescription = "下移") }
                    Switch(
                        checked = visible,
                        onCheckedChange = { viewModel.setModuleVisible(tab, it) },
                        // 不允许把最后一个模块也隐藏
                        enabled = !(visible && visibleCount <= 1)
                    )
                }
            }
            Text(
                text = "被隐藏的模块不会出现在底部导航里；隐藏当前正在看的模块时会自动切到第一个可见模块。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        message?.let { text ->
            AppInlineMessage(
                text = text,
                onDismiss = { message = null }
            )
        }

        SectionCard(title = "关于", icon = Icons.Default.Info) {
            Text(
                text = "版本 ${com.example.myfirstapp.BuildConfig.VERSION_NAME} · 数据只保存在本机\n" +
                    "卸载 App 或清除数据会丢失课表、账目和待办，请先记录重要内容。\n" +
                    "手机重启、更新 App、修改日期时间或时区后，提醒会自动重新计算。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.Space5))
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatusRow(label: String, status: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = status,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RequirementItem(index: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$index.",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        return
    }
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun openBatteryOptimization(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val intent = Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
