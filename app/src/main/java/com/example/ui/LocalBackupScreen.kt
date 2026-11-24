package com.example.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.example.ui.theme.AppIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LocalBackupManager
import com.example.model.BudgetSettings
import com.example.service.BackupReminderScheduler
import com.example.service.NotificationBridge
import com.example.ui.components.formatDateTimeFull
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.LocalBackupActionState
import com.example.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalBackupScreen(
    settings: BudgetSettings,
    viewModel: LedgerViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val backupState by viewModel.localBackupActionState.collectAsStateWithLifecycle()
    val txState by viewModel.transactionsState.collectAsStateWithLifecycle()
    val rulesState by viewModel.rulesState.collectAsStateWithLifecycle()
    val books by viewModel.booksState.collectAsStateWithLifecycle()
    val bookLinks by viewModel.bookLinksState.collectAsStateWithLifecycle()
    val exchangeRates by viewModel.exchangeRateState.collectAsStateWithLifecycle()

    val allTransactions = (txState as? UiState.Success)?.data ?: emptyList()
    val txCount = allTransactions.size
    val ruleCount = (rulesState as? UiState.Success)?.data?.size ?: 0
    val isWorking = backupState is LocalBackupActionState.Working
    var selectedExportBookId by remember { mutableStateOf<String?>(null) }
    var showFileExportDialog by remember { mutableStateOf(false) }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    SettingsAutoScrollEffect(
        listState = listState,
        route = ROUTE_SETTINGS_LOCAL_BACKUP,
        sectionKeysInOrder = listOf("local_export_restore_card", "local_export_spreadsheet_card", "backup_reminder_card")
    )

    var hasPostNotifPermission by remember {
        mutableStateOf(NotificationBridge.hasPostNotificationPermission(context))
    }

    val initialUseHoursUnit = settings.backupReminderIntervalHours < 24 || settings.backupReminderIntervalHours % 24 != 0
    var useHoursUnit by remember(settings.backupReminderIntervalHours) {
        mutableStateOf(initialUseHoursUnit)
    }
    var customIntervalInput by remember(settings.backupReminderIntervalHours, useHoursUnit) {
        val displayVal = if (useHoursUnit) {
            settings.backupReminderIntervalHours
        } else {
            (settings.backupReminderIntervalHours / 24).coerceAtLeast(1)
        }
        mutableStateOf(displayVal.toString())
    }

    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        val pwd = pendingExportPassword
        pendingExportPassword = null
        if (uri != null) {
            viewModel.exportLocalBackup(context, uri, password = pwd)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.restoreLocalBackup(context, uri)
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPostNotifPermission = granted
        onShowMessage(if (granted) "已授予备份通知权限" else "未授予通知权限，无法弹出提醒")
    }

    val presetHours = listOf(
        12 to "12 小时",
        24 to "1 天",
        72 to "3 天",
        168 to "7 天",
        336 to "14 天",
        720 to "30 天"
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Export & Restore Local Backup Card
            item(key = "local_export_restore_card") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsSearchArrivalBanner(
                        route = ROUTE_SETTINGS_LOCAL_BACKUP,
                        onDismiss = { viewModel.clearSettingsHighlight() }
                    )
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingsHighlightSection(ROUTE_SETTINGS_LOCAL_BACKUP, "local_export_restore_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = settingsHighlightedText("本地数据导出与恢复", ROUTE_SETTINGS_LOCAL_BACKUP),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = settingsHighlightedText(
                                    "当前账本共有 $txCount 笔账单、$ruleCount 条识别规则。导出为 AES-256-GCM 加密的 ZIP 备份包（可选自定义密码），恢复时自动兼容旧版明文 JSON 备份并智能去重合并。",
                                    ROUTE_SETTINGS_LOCAL_BACKUP
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val lastBackupText = if (settings.lastLocalBackupMillis > 0L) {
                                "上次本地备份：${formatDateTimeFull(settings.lastLocalBackupMillis)}"
                            } else {
                                "尚未执行过本地文件备份"
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = lastBackupText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    enabled = !isWorking,
                                    onClick = { showExportPasswordDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("local_export_backup_button")
                                ) {
                                    Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("加密导出备份")
                                }

                                OutlinedButton(
                                    enabled = !isWorking,
                                    onClick = {
                                        restoreLauncher.launch(
                                            arrayOf(
                                                "application/zip",
                                                "application/json",
                                                "application/octet-stream",
                                                "text/plain",
                                                "*/*"
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("local_restore_backup_button")
                                ) {
                                    Icon(AppIcons.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("从备份恢复")
                                }
                            }

                            when (val state = backupState) {
                                is LocalBackupActionState.Idle -> Unit
                                is LocalBackupActionState.Working -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text(state.label, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                is LocalBackupActionState.Success -> {
                                    Surface(
                                        color = IncomeJade.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = state.message,
                                            color = IncomeJade,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                                is LocalBackupActionState.Error -> {
                                    Surface(
                                        color = ExpenseCoral.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = state.message,
                                            color = ExpenseCoral,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                                is LocalBackupActionState.PasswordRequired -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = state.message,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1.5 Export Bills / Books to CSV / XLSX / JSON with optional analysis text
            item(key = "local_export_spreadsheet_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_LOCAL_BACKUP, "local_export_spreadsheet_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = settingsHighlightedText("导出账单 / 账本文件 (CSV / XLSX / JSON)", ROUTE_SETTINGS_LOCAL_BACKUP),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = settingsHighlightedText(
                                "支持导出全部账单或指定账本为 CSV、Excel XLSX 表格或 JSON 文件，可选附带完整收支分析文本。",
                                ROUTE_SETTINGS_LOCAL_BACKUP
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (books.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = selectedExportBookId == null,
                                    onClick = { selectedExportBookId = null },
                                    label = { Text("全部账单") }
                                )
                                books.forEach { book ->
                                    FilterChip(
                                        selected = selectedExportBookId == book.id,
                                        onClick = { selectedExportBookId = book.id },
                                        label = { Text(book.name) }
                                    )
                                }
                            }
                        }
                        Button(
                            enabled = allTransactions.isNotEmpty(),
                            onClick = { showFileExportDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("local_export_spreadsheet_button")
                        ) {
                            Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("选择格式并导出（CSV / XLSX / JSON）")
                        }
                    }
                }
            }

            // 2. Periodic Urgent Notification Reminder Card
            item(key = "backup_reminder_card") {
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
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.WarningAmber,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("定期备份通知", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    text = "到达设定周期后发送横幅与响铃震动通知（默认高优先级）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.backupReminderEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotifPermission) {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    viewModel.updateBackupReminder(
                                        context = context,
                                        enabled = enabled,
                                        intervalHours = settings.backupReminderIntervalHours
                                    )
                                },
                                modifier = Modifier.testTag("toggle_backup_reminder")
                            )
                        }

                        if (!hasPostNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            OutlinedButton(
                                onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("授予通知权限以接收备份通知")
                            }
                        }

                        HorizontalDivider()

                        Text(
                            text = "提醒周期（当前：每 ${BackupReminderScheduler.formatIntervalLabel(settings.backupReminderIntervalHours)}）",
                            style = MaterialTheme.typography.labelLarge
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presetHours.forEach { (hours, label) ->
                                FilterChip(
                                    selected = settings.backupReminderIntervalHours == hours,
                                    onClick = {
                                        viewModel.updateBackupReminder(
                                            context = context,
                                            enabled = settings.backupReminderEnabled,
                                            intervalHours = hours
                                        )
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("backup_interval_chip_$hours")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customIntervalInput,
                                onValueChange = { next ->
                                    customIntervalInput = next.filter { it.isDigit() }.take(4)
                                },
                                label = { Text("自定义间隔 (${if (useHoursUnit) "小时" else "天"})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("custom_backup_interval_input")
                            )

                            FilterChip(
                                selected = !useHoursUnit,
                                onClick = { useHoursUnit = false },
                                label = { Text("天") }
                            )
                            FilterChip(
                                selected = useHoursUnit,
                                onClick = { useHoursUnit = true },
                                label = { Text("小时") }
                            )

                            Button(
                                onClick = {
                                    val raw = customIntervalInput.toIntOrNull()
                                    if (raw == null || raw <= 0) {
                                        onShowMessage("请输入大于 0 的整数间隔")
                                        return@Button
                                    }
                                    val hours = if (useHoursUnit) raw else raw * 24
                                    viewModel.updateBackupReminder(
                                        context = context,
                                        enabled = true,
                                        intervalHours = hours
                                    )
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("apply_custom_backup_interval_button")
                            ) {
                                Text("应用")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotifPermission) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.sendTestUrgentBackupNotification(context)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("test_urgent_backup_notification_button")
                        ) {
                            Icon(AppIcons.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("立即发送测试通知")
                        }
                    }
                }
            }
        }
    }

    if (showFileExportDialog) {
        val chosenBook = selectedExportBookId?.let { id -> books.firstOrNull { it.id == id } }
        val exportTxs = remember(chosenBook, allTransactions, bookLinks) {
            if (chosenBook == null) {
                allTransactions
            } else {
                val ids = bookLinks.filter { it.bookId == chosenBook.id }.mapTo(HashSet()) { it.transactionId }
                allTransactions.filter { it.id in ids }
            }
        }
        com.example.ui.components.LedgerExportDialog(
            scopeTitle = chosenBook?.let { "账本「${it.name}」" } ?: "全部账单",
            transactions = exportTxs,
            baseCurrencyCode = settings.baseCurrencyCode,
            ratesToCny = exchangeRates.ratesToCny,
            bookName = chosenBook?.name,
            onDismiss = { showFileExportDialog = false },
            onExportFeedback = { msg -> onShowMessage(msg) }
        )
    }

    if (showExportPasswordDialog) {
        var customPwd by remember { mutableStateOf("") }
        var confirmPwd by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showExportPasswordDialog = false },
            icon = { Icon(AppIcons.Security, contentDescription = null) },
            title = { Text("加密备份设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "备份文件将默认使用 AES-256-GCM 加密并封装为 ZIP 压缩包。你也可以额外设置自定义密码加强保护（留空则使用应用内置加密，换机仍可直接恢复）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customPwd,
                        onValueChange = { customPwd = it; errorMsg = null },
                        label = { Text("自定义加密密码（可选）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("local_backup_export_password_input")
                    )
                    if (customPwd.isNotEmpty()) {
                        OutlinedTextField(
                            value = confirmPwd,
                            onValueChange = { confirmPwd = it; errorMsg = null },
                            label = { Text("确认密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("local_backup_export_password_confirm")
                        )
                    }
                    if (errorMsg != null) {
                        Text(errorMsg!!, color = ExpenseCoral, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportPasswordDialog = false }) { Text("取消") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customPwd.isNotEmpty() && customPwd != confirmPwd) {
                            errorMsg = "两次输入的密码不一致"
                            return@Button
                        }
                        pendingExportPassword = customPwd.takeIf { it.isNotEmpty() }
                        showExportPasswordDialog = false
                        exportLauncher.launch(LocalBackupManager.buildDefaultBackupFileName())
                    },
                    modifier = Modifier.testTag("local_backup_export_confirm_button")
                ) {
                    Text(if (customPwd.isEmpty()) "默认加密并导出" else "使用密码加密导出")
                }
            }
        )
    }

    val passwordPromptState = backupState as? LocalBackupActionState.PasswordRequired
    if (passwordPromptState != null) {
        var restorePwd by remember(passwordPromptState.uri, passwordPromptState.message) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.clearLocalBackupActionState() },
            icon = { Icon(AppIcons.Security, contentDescription = null) },
            title = { Text("输入备份解密密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = passwordPromptState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = restorePwd,
                        onValueChange = { restorePwd = it },
                        label = { Text("备份密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("local_backup_restore_password_input")
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearLocalBackupActionState() }) {
                    Text("取消")
                }
            },
            confirmButton = {
                Button(
                    enabled = restorePwd.isNotEmpty(),
                    onClick = {
                        viewModel.restoreLocalBackup(
                            context = context,
                            uri = passwordPromptState.uri,
                            password = restorePwd
                        )
                    },
                    modifier = Modifier.testTag("local_backup_restore_password_confirm")
                ) {
                    Text("解密并恢复")
                }
            }
        )
    }
}
