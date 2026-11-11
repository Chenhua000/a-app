package com.example.myfirstapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.myfirstapp.ui.AppRoot
import com.example.myfirstapp.ui.theme.MyFirstAppTheme

class MainActivity : ComponentActivity() {

    private var notificationTarget: String? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationTarget = intent?.getStringExtra(EXTRA_NOTIFICATION_TARGET)
        enableEdgeToEdge()
        setContent {
            MyFirstAppTheme {
                AppRoot(pendingTarget = notificationTarget)
            }
        }
    }

    /**
     * Activity 已经是 singleTop，App 在前台时点通知会走这里。
     * 必须同步到这里的状态中，Compose 才会跳转到对应页面。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationTarget = intent.getStringExtra(EXTRA_NOTIFICATION_TARGET)
    }

    companion object {
        const val EXTRA_NOTIFICATION_TARGET = "extra_notification_target"
    }
}
