package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CategoryCatalog
import com.example.model.CustomParsingRule
import com.example.model.TransactionType
import com.example.parser.ChinaPaymentCatalog
import com.example.service.NotificationBridge
import com.example.ui.components.ChannelBadge
import com.example.ui.components.ParseMethodBadge
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatTimeShort
import com.example.ui.components.transactionTypeColor
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.LocalNumberFontFamily
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.ParseBenchState
import com.example.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParserHubScreen(
    viewModel: LedgerViewModel,
    isListenerEnabled: Boolean,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val parseState by viewModel.parseBenchState.collectAsStateWithLifecycle()
    val rulesState by viewModel.rulesState.collectAsStateWithLifecycle()
    val capturedLogs by NotificationBridge.capturedLogs.collectAsStateWithLifecycle()
    val numberFont = LocalNumberFontFamily.current

    var titleInput by remember { mutableStateOf("95555 招商银行") }
    var bodyInput by remember {
        mutableStateOf("【招商银行】您账户8812于11月18日08:30在星巴克(静安寺店)消费人民币42.00元，可用余额3,210.50元。")
    }
    var selectedPackage by remember { mutableStateOf("com.cmbchina.pb") }
    var showAddRuleDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            NotificationBridge.dispatchTestPaymentNotification(context, titleInput, bodyInput)
            onShowMessage("已发送测试通知")
        } else {
            onShowMessage("需要通知权限才能发送测试通知")
        }
    }

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
            // 1. Streamlined Notification Test Bench
            item(key = "test_bench") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "通知解析测试",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ChinaPaymentCatalog.channels.take(8).forEach { sample ->
                                AssistChip(
                                    onClick = {
                                        titleInput = sample.sampleNotificationTitle
                                        bodyInput = sample.sampleNotificationBody
                                        selectedPackage = sample.packageNames.firstOrNull() ?: "com.android.mms"
                                        viewModel.resetParseBench()
                                    },
                                    label = { Text(sample.name) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = titleInput,
                            onValueChange = { titleInput = it },
                            label = { Text("通知标题 / 银行号码") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bench_title_input")
                        )

                        OutlinedTextField(
                            value = bodyInput,
                            onValueChange = { bodyInput = it },
                            label = { Text("通知正文") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bench_body_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.analyzeNotificationText(
                                        packageName = selectedPackage,
                                        title = titleInput,
                                        content = bodyInput,
                                        enableSemanticPass = false
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("bench_rule_parse_button")
                            ) {
                                Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("规则匹配")
                            }

                            Button(
                                onClick = {
                                    viewModel.analyzeNotificationText(
                                        packageName = selectedPackage,
                                        title = titleInput,
                                        content = bodyInput,
                                        enableSemanticPass = true
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("bench_semantic_parse_button")
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("语义解析")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                if (!isListenerEnabled) {
                                    onShowMessage("请先开启通知读取权限")
                                    NotificationBridge.openNotificationListenerSettings(context)
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !NotificationBridge.hasPostNotificationPermission(context)
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    NotificationBridge.dispatchTestPaymentNotification(
                                        context = context,
                                        title = titleInput,
                                        body = bodyInput
                                    )
                                    onShowMessage("已发送测试系统通知")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("bench_send_system_notification_button")
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("发送真实系统通知测试")
                        }

                        AnimatedVisibility(visible = parseState !is ParseBenchState.Idle) {
                            when (val state = parseState) {
                                is ParseBenchState.Idle -> Unit
                                is ParseBenchState.Error -> {
                                    Surface(
                                        color = ExpenseCoral.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = state.message,
                                            color = ExpenseCoral,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }

                                is ParseBenchState.Parsed -> {
                                    val result = state.result
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = result.merchant,
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                                Text(
                                                    text = formatCurrency(
                                                        cents = result.amountCents,
                                                        currencyCode = result.currencyCode,
                                                        showSign = true,
                                                        type = result.type
                                                    ),
                                                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = numberFont),
                                                    color = transactionTypeColor(result.type),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                ChannelBadge(channel = result.channel)
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surface,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = result.currencyCode,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surface,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = result.category,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                ParseMethodBadge(method = result.parseMethod)
                                            }
                                            Button(
                                                onClick = {
                                                    viewModel.saveParsedResult(
                                                        parsed = result,
                                                        rawTitle = state.rawTitle,
                                                        rawContent = state.rawContent
                                                    )
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("bench_save_parsed_button")
                                            ) {
                                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("确认入账")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Custom Parsing Rules
            item(key = "custom_rules_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Rule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("自定义匹配规则", style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(
                        onClick = { showAddRuleDialog = true },
                        modifier = Modifier.testTag("add_custom_rule_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新建规则")
                    }
                }
            }

            when (val rState = rulesState) {
                is UiState.Loading -> {
                    item(key = "rules_loading") {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                is UiState.Error -> {
                    item(key = "rules_error") {
                        Text(rState.message, color = ExpenseCoral, style = MaterialTheme.typography.bodySmall)
                    }
                }
                is UiState.Success -> {
                    if (rState.data.isEmpty()) {
                        item(key = "rules_empty") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "尚未添加自定义规则。已内置支付宝、微信支付、云闪付及 ${ChinaPaymentCatalog.channels.size} 家主流渠道与银行识别。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    } else {
                        items(rState.data, key = { it.id }) { rule ->
                            CustomRuleCard(
                                rule = rule,
                                onToggle = { enabled -> viewModel.toggleCustomRule(rule.id, enabled) },
                                onDelete = { viewModel.deleteCustomRule(rule.id) }
                            )
                        }
                    }
                }
            }

            // 3. Recent Captured Logs (compact)
            if (capturedLogs.isNotEmpty()) {
                item(key = "live_capture_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("最近捕捉日志", style = MaterialTheme.typography.titleMedium)
                        capturedLogs.take(4).forEach { log ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${log.appLabel} · ${log.status}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = formatTimeShort(log.capturedAtMillis),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(log.summary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Compact Supported Channels Chips
            item(key = "china_catalog_compact") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "已内置渠道 (${ChinaPaymentCatalog.channels.size} 家，点击填入样例)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ChinaPaymentCatalog.channels.forEach { ch ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.clickable {
                                    titleInput = ch.sampleNotificationTitle
                                    bodyInput = ch.sampleNotificationBody
                                    selectedPackage = ch.packageNames.firstOrNull() ?: "com.android.mms"
                                    onShowMessage("已填入「${ch.name}」样例")
                                }
                            ) {
                                Text(
                                    text = ch.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddRuleDialog) {
        AddCustomRuleDialog(
            onDismiss = { showAddRuleDialog = false },
            onConfirm = { name, pkg, keyword, channel, category, type ->
                viewModel.addCustomRule(name, pkg, keyword, channel, category, type)
                showAddRuleDialog = false
            }
        )
    }
}

@Composable
private fun CustomRuleCard(
    rule: CustomParsingRule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "关键词: ${rule.keywordPattern} → ${rule.channelName} · ${rule.defaultCategory}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "删除规则",
                    tint = ExpenseCoral
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddCustomRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        packageFilter: String,
        keywordPattern: String,
        channelName: String,
        defaultCategory: String,
        defaultType: TransactionType
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("北京银行") }
    var packageFilter by remember { mutableStateOf("*") }
    var category by remember { mutableStateOf("餐饮美食") }
    var txType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建通知识别规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text("规则名称") },
                    placeholder = { Text("例如：公司食堂") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it; errorText = null },
                    label = { Text("匹配关键词 / 正则") },
                    placeholder = { Text("例如：员工食堂|北京银行") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = channel,
                    onValueChange = { channel = it },
                    label = { Text("归属渠道 / 银行") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = packageFilter,
                    onValueChange = { packageFilter = it },
                    label = { Text("限定包名 (* 表示不限)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("默认分类", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryCatalog.expenseCategories.take(6).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionType.entries.forEach { t ->
                        FilterChip(
                            selected = txType == t,
                            onClick = { txType = t },
                            label = { Text(t.label) }
                        )
                    }
                }
                if (errorText != null) {
                    Text(errorText!!, color = ExpenseCoral, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank() || keyword.isBlank() || channel.isBlank()) {
                    errorText = "请完整填写规则名称、关键词和渠道"
                    return@Button
                }
                onConfirm(name, packageFilter, keyword, channel, category, txType)
            }) {
                Text("保存规则")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
