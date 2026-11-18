package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import com.example.service.NotificationBridge
import com.example.ui.AppNavigation
import com.example.ui.theme.SmartLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        NotificationBridge.ensureNotificationChannels(this)

        setContent {
            SmartLedgerTheme {
                Surface {
                    AppNavigation()
                }
            }
        }
    }
}
