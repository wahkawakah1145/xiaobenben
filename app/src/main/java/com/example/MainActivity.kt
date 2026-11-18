package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.service.NotificationBridge
import com.example.ui.AppNavigation

class MainActivity : ComponentActivity() {
    private var openLocalBackupOnStart by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationBridge.ensureNotificationChannels(this)
        openLocalBackupOnStart = intent?.getBooleanExtra(NotificationBridge.EXTRA_OPEN_LOCAL_BACKUP, false) == true
        setContent {
            AppNavigation(
                openLocalBackupRequest = openLocalBackupOnStart,
                onConsumedLocalBackupRequest = { openLocalBackupOnStart = false }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(NotificationBridge.EXTRA_OPEN_LOCAL_BACKUP, false)) {
            openLocalBackupOnStart = true
        }
    }
}
