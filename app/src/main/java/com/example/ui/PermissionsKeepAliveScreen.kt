package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import com.example.ui.theme.AppIcons
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.BudgetSettings
import com.example.service.NotificationBridge
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.viewmodel.LedgerViewModel

@Composable
fun PermissionsKeepAliveScreen(
    settings: BudgetSettings,
    isListenerEnabled: Boolean,
    isAccessibilityEnabled: Boolean,
    isIgnoringBattery: Boolean,
    viewModel: LedgerViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rootStatus by viewModel.rootStatusState.collectAsStateWithLifecycle()
    var hasPostNotifPermission by remember {
        mutableStateOf(NotificationBridge.hasPostNotificationPermission(context))
    }

    val postNotifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPostNotifPermission = granted
        onShowMessage(if (granted) "已授予通知发送权限" else "未授予通知发送权限")
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "permissions_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(AppIcons.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("系统权限与进程保活", style = MaterialTheme.typography.titleMedium)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("通知使用权", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (isListenerEnabled) "已授权" else "未授权 · 点击前往系统设置开启",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isListenerEnabled) IncomeJade else ExpenseCoral
                                )
                            }
                            Icon(
                                imageVector = if (isListenerEnabled) Icons.Rounded.CheckCircle else AppIcons.WarningAmber,
                                contentDescription = null,
                                tint = if (isListenerEnabled) IncomeJade else ExpenseCoral
                            )
                        }

                        Button(
                            onClick = { NotificationBridge.openNotificationListenerSettings(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("settings_open_listener_button")
                        ) {
                            Icon(AppIcons.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isListenerEnabled) "管理通知使用权" else "开启通知读取权限")
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("无障碍保活守护", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (isAccessibilityEnabled) "运行中 · 断开时自动重绑监听" else "未开启 · 可辅助防止后台被杀",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isAccessibilityEnabled) IncomeJade else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { NotificationBridge.openAccessibilitySettings(context) },
                                modifier = Modifier.testTag("settings_accessibility_button")
                            ) {
                                Icon(AppIcons.AccessibilityNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isAccessibilityEnabled) "已开启" else "去开启")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("忽略电池优化", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (isIgnoringBattery) "已加入电池白名单" else "建议设为不限制后台耗电",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isIgnoringBattery) IncomeJade else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!isIgnoringBattery) {
                                OutlinedButton(
                                    onClick = { NotificationBridge.requestIgnoreBatteryOptimizations(context) },
                                    modifier = Modifier.testTag("settings_battery_button")
                                ) {
                                    Icon(AppIcons.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("去设置")
                                }
                            } else {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = "已优化", tint = IncomeJade)
                            }
                        }

                        HorizontalDivider()

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Root 进程保活", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = if (rootStatus.lastResultMessage.isNotBlank()) {
                                            rootStatus.lastResultMessage
                                        } else {
                                            "通过 su 设置 Doze 白名单、后台常驻与进程优先级"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (rootStatus.isRootAvailable) IncomeJade else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = settings.rootKeepAliveEnabled,
                                    onCheckedChange = { checked ->
                                        viewModel.saveSettings(settings.copy(rootKeepAliveEnabled = checked))
                                        if (checked) {
                                            viewModel.applyRootKeepAlive(context)
                                        }
                                    },
                                    modifier = Modifier.testTag("toggle_root_keepalive")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.checkRootStatus() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("检测 su 权限")
                                }
                                Button(
                                    onClick = { viewModel.applyRootKeepAlive(context) },
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .testTag("apply_root_keepalive_button")
                                ) {
                                    Icon(AppIcons.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("执行 Root 保活")
                                }
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("通知发送权限", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = if (hasPostNotifPermission) "已允许发送通知" else "未允许",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!hasPostNotifPermission) {
                                    OutlinedButton(
                                        onClick = { postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                        modifier = Modifier.testTag("request_post_notif_button")
                                    ) {
                                        Text("授权")
                                    }
                                } else {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = "已授权", tint = IncomeJade)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
