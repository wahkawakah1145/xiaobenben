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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.BudgetSettings
import com.example.model.WebDavConfig
import com.example.service.NotificationBridge
import com.example.ui.components.formatDateTimeFull
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.WebDavActionState
import kotlin.math.roundToLong

@Composable
fun SettingsScreen(
    settings: BudgetSettings,
    isListenerEnabled: Boolean,
    viewModel: LedgerViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val webDavConfig by viewModel.webDavConfigState.collectAsStateWithLifecycle()
    val webDavAction by viewModel.webDavActionState.collectAsStateWithLifecycle()

    var budgetInput by remember(settings.monthlyBudgetCents) {
        mutableStateOf("%.0f".format(settings.monthlyBudgetCents / 100.0))
    }

    var serverUrlInput by remember(webDavConfig.serverUrl) {
        mutableStateOf(webDavConfig.serverUrl)
    }
    var usernameInput by remember(webDavConfig.username) {
        mutableStateOf(webDavConfig.username)
    }
    var passwordInput by remember(webDavConfig.password) {
        mutableStateOf(webDavConfig.password)
    }
    var remoteFolderInput by remember(webDavConfig.remoteFolder) {
        mutableStateOf(webDavConfig.remoteFolder.ifBlank { "ZhiJiZhang" })
    }
    var showPassword by remember { mutableStateOf(false) }

    var hasPostNotifPermission by remember {
        mutableStateOf(NotificationBridge.hasPostNotificationPermission(context))
    }

    val postNotifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPostNotifPermission = granted
        onShowMessage(if (granted) "已授予通知发送权限" else "未授予通知发送权限")
    }

    val currentWebDavConfig = WebDavConfig(
        serverUrl = serverUrlInput.trim(),
        username = usernameInput.trim(),
        password = passwordInput,
        remoteFolder = remoteFolderInput.trim().ifBlank { "ZhiJiZhang" },
        lastSyncMillis = webDavConfig.lastSyncMillis,
        lastSyncStatus = webDavConfig.lastSyncStatus
    )
    val isWebDavWorking = webDavAction is WebDavActionState.Working

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Offline Privacy Card
            item(key = "offline_privacy_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Storage,
                            contentDescription = "纯本地存储",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "纯本地离线运行 · 隐私优先",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "无需登录账号，通知解析、账单存储与月度洞察全部在设备本地 Room 数据库完成；仅在您手动点击 WebDAV 备份或恢复时发起网络同步。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. WebDAV Backup & Restore Card
            item(key = "webdav_sync_card") {
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
                            Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("WebDAV 云盘备份与同步", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "支持坚果云、Nextcloud、AList 或自建 WebDAV 服务",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = {
                                    serverUrlInput = "https://dav.jianguoyun.com/dav/"
                                    if (remoteFolderInput.isBlank()) remoteFolderInput = "ZhiJiZhang"
                                },
                                label = { Text("填入坚果云地址") }
                            )
                            AssistChip(
                                onClick = {
                                    if (remoteFolderInput.isBlank()) remoteFolderInput = "ZhiJiZhang"
                                    onShowMessage("备份文件名固定为 zhijizhang_backup.json")
                                },
                                label = { Text("目录: ${remoteFolderInput.ifBlank { "ZhiJiZhang" }}") }
                            )
                        }

                        OutlinedTextField(
                            value = serverUrlInput,
                            onValueChange = { serverUrlInput = it },
                            label = { Text("WebDAV 服务器地址") },
                            placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("webdav_server_url_input")
                        )

                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            label = { Text("账号 / 邮箱") },
                            placeholder = { Text("例如：name@example.com") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("webdav_username_input")
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("应用密码 / 授权码") },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (showPassword) "隐藏密码" else "显示密码"
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("webdav_password_input")
                        )

                        OutlinedTextField(
                            value = remoteFolderInput,
                            onValueChange = { remoteFolderInput = it },
                            label = { Text("远程备份文件夹名称") },
                            placeholder = { Text("ZhiJiZhang") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("webdav_folder_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                enabled = !isWebDavWorking,
                                onClick = { viewModel.testWebDavConnection(currentWebDavConfig) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("webdav_test_button")
                            ) {
                                Icon(Icons.Rounded.WifiFind, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("测试连接")
                            }

                            OutlinedButton(
                                enabled = !isWebDavWorking,
                                onClick = { viewModel.saveWebDavConfig(currentWebDavConfig) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("webdav_save_button")
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("保存配置")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                enabled = !isWebDavWorking,
                                onClick = { viewModel.backupToWebDav(currentWebDavConfig) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("webdav_backup_button")
                            ) {
                                Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("备份到 WebDAV")
                            }

                            OutlinedButton(
                                enabled = !isWebDavWorking,
                                onClick = { viewModel.restoreFromWebDav(currentWebDavConfig) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("webdav_restore_button")
                            ) {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("恢复并合并")
                            }
                        }

                        when (val action = webDavAction) {
                            is WebDavActionState.Idle -> Unit
                            is WebDavActionState.Working -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text(action.label, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            is WebDavActionState.Success -> {
                                Surface(
                                    color = IncomeJade.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = action.message,
                                        color = IncomeJade,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            is WebDavActionState.Error -> {
                                Surface(
                                    color = ExpenseCoral.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = action.message,
                                        color = ExpenseCoral,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }

                        if (webDavConfig.lastSyncMillis > 0L && webDavConfig.lastSyncStatus.isNotBlank()) {
                            Text(
                                text = "最近同步 (${formatDateTimeFull(webDavConfig.lastSyncMillis)}): ${webDavConfig.lastSyncStatus}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. Permissions Card
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
                            Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("系统权限配置", style = MaterialTheme.typography.titleMedium)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("通知使用权 (NotificationListenerService)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (isListenerEnabled) "已授权 · 实时捕捉支付宝/微信/银行通知" else "未授权 · 点击前往系统设置开启",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isListenerEnabled) IncomeJade else ExpenseCoral
                                )
                            }
                            Icon(
                                imageVector = if (isListenerEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
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
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isListenerEnabled) "管理通知使用权" else "开启通知读取权限")
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("入账确认通知权限 (Android 13+)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = if (hasPostNotifPermission) "已允许发送自动记账成功提醒" else "未允许 · 建议开启以接收记账反馈",
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

            // 4. Automation & Budget Preferences
            item(key = "preferences_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("自动记账与预算偏好", style = MaterialTheme.typography.titleMedium)

                        SettingToggleRow(
                            title = "启用后台通知自动记账",
                            subtitle = "收到中国大陆支付或银行通知时自动在本地解析并保存",
                            checked = settings.autoRecordEnabled,
                            onCheckedChange = { checked ->
                                viewModel.saveSettings(settings.copy(autoRecordEnabled = checked))
                            }
                        )

                        SettingToggleRow(
                            title = "本地语义增强解析",
                            subtitle = "当常规银行模板未提取到精确商户或分类时，启用本地启发式语义切分",
                            checked = settings.smartSemanticEnabled,
                            onCheckedChange = { checked ->
                                viewModel.saveSettings(settings.copy(smartSemanticEnabled = checked))
                            }
                        )

                        SettingToggleRow(
                            title = "自动入账后发送确认通知",
                            subtitle = "在状态栏提示已识别金额与分类，方便快速核对",
                            checked = settings.notifyOnRecorded,
                            onCheckedChange = { checked ->
                                viewModel.saveSettings(settings.copy(notifyOnRecorded = checked))
                            }
                        )

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = budgetInput,
                                onValueChange = { budgetInput = it },
                                label = { Text("每月支出预算 (元)") },
                                prefix = { Text("¥") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_budget_input")
                            )
                            Button(
                                onClick = {
                                    val yuan = budgetInput.trim().toDoubleOrNull()
                                    if (yuan == null || yuan < 0.0 || yuan > 10_000_000.0) {
                                        onShowMessage("请输入 0 至 10,000,000 之间的有效预算金额")
                                    } else {
                                        viewModel.saveSettings(
                                            settings.copy(monthlyBudgetCents = (yuan * 100.0).roundToLong())
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("settings_save_budget_button")
                            ) {
                                Text("保存预算")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
