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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import com.example.ui.components.formatDateTimeFull
import com.example.ui.theme.AppIcons
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
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val capturedLogs by NotificationBridge.capturedLogs.collectAsStateWithLifecycle()
    val activeCategories = remember(settings) { CategoryCatalog.allActiveCategories(settings) }
    val numberFont = LocalNumberFontFamily.current

    var titleInput by remember { mutableStateOf("") }
    var bodyInput by remember { mutableStateOf("") }
    var selectedPackage by remember { mutableStateOf("") }
    var editingRule by remember { mutableStateOf<CustomParsingRule?>(null) }
    var showCreateRuleDialog by remember { mutableStateOf(false) }

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

    val listState = rememberLazyListState()
    SettingsAutoScrollEffect(
        listState = listState,
        route = ROUTE_SETTINGS_PARSER,
        sectionKeysInOrder = listOf("parser_arrival_banner", "test_bench", "custom_rules_header")
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "parser_arrival_banner") {
                SettingsSearchArrivalBanner(
                    route = ROUTE_SETTINGS_PARSER,
                    onDismiss = { viewModel.clearSettingsHighlight() }
                )
            }

            // 1. Streamlined Notification Test Bench
            item(key = "test_bench") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_PARSER, "test_bench")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = settingsHighlightedText("通知解析测试", ROUTE_SETTINGS_PARSER),
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
                            placeholder = { Text("例如：95555 招商银行（或点击上方渠道芯片填入）") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bench_title_input")
                        )

                        OutlinedTextField(
                            value = bodyInput,
                            onValueChange = { bodyInput = it },
                            label = { Text("通知正文") },
                            placeholder = { Text("粘贴需要测试的银行短信或支付通知内容") },
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
                                Icon(AppIcons.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("规则匹配")
                            }

                            Button(
                                onClick = {
                                    viewModel.analyzeWithConfiguredAi(
                                        context = context,
                                        packageName = selectedPackage,
                                        title = titleInput,
                                        content = bodyInput
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("bench_ai_parse_button")
                            ) {
                                Icon(AppIcons.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("AI理解")
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
                            Icon(AppIcons.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("系统通知测试")
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
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = state.message,
                                                color = ExpenseCoral,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.enqueueInquiryFromTestBench(
                                                        packageName = selectedPackage,
                                                        title = titleInput,
                                                        content = bodyInput,
                                                        parsed = null
                                                    )
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(AppIcons.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("加入首页「待询问」并学习规则")
                                            }
                                        }
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
                                            result.occurredAtMillis?.let { ts ->
                                                Text(
                                                    text = "识别时间：${formatDateTimeFull(ts)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (result.needsInquiry) {
                                                Text(
                                                    text = "置信度较低（${result.inquiryReason}），后台收到同类通知将主动向你询问",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (result.needsInquiry) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            viewModel.enqueueInquiryFromTestBench(
                                                                packageName = selectedPackage,
                                                                title = state.rawTitle,
                                                                content = state.rawContent,
                                                                parsed = result
                                                            )
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("转为询问学习")
                                                    }
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
                                                        .weight(1f)
                                                        .testTag("bench_save_parsed_button")
                                                ) {
                                                    Icon(AppIcons.Save, contentDescription = null, modifier = Modifier.size(18.dp))
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
            }

            // 2. Editable Parsing Rules
            item(key = "custom_rules_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_PARSER, "custom_rules_header")
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(AppIcons.Rule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = settingsHighlightedText("识别规则（点击可编辑）", ROUTE_SETTINGS_PARSER),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { viewModel.resetDefaultRules() }) {
                            Text("恢复默认")
                        }
                        TextButton(
                            onClick = { showCreateRuleDialog = true },
                            modifier = Modifier.testTag("add_custom_rule_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("新建")
                        }
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
                                    text = "当前没有规则，可点击右上角「新建」或「恢复默认」。",
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
                                onEdit = { editingRule = rule },
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

    if (showCreateRuleDialog) {
        CustomRuleEditorDialog(
            initialRule = null,
            activeCategories = activeCategories,
            onDismiss = { showCreateRuleDialog = false },
            onSave = { rule ->
                viewModel.saveOrUpdateCustomRule(rule)
                showCreateRuleDialog = false
            }
        )
    }

    editingRule?.let { targetRule ->
        CustomRuleEditorDialog(
            initialRule = targetRule,
            activeCategories = activeCategories,
            onDismiss = { editingRule = null },
            onSave = { updated ->
                viewModel.saveOrUpdateCustomRule(updated)
                editingRule = null
            }
        )
    }
}

@Composable
private fun CustomRuleCard(
    rule: CustomParsingRule,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val typeLabel = when {
        rule.isIgnoreRule -> "忽略非账单"
        rule.defaultType.equals("AUTO", ignoreCase = true) -> "自动识别"
        else -> TransactionType.fromRaw(rule.defaultType).label
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .testTag("rule_card_${rule.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(rule.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                    if (rule.isIgnoreRule) {
                        Surface(
                            color = ExpenseCoral.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "忽略规则",
                                style = MaterialTheme.typography.labelSmall,
                                color = ExpenseCoral,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (rule.isTemplateRule) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "编辑模版",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = if (rule.isIgnoreRule) {
                        "屏蔽关键词: ${rule.keywordPattern} → 自动过滤不询问"
                    } else {
                        "关键词: ${rule.keywordPattern} → ${rule.channelName} · ${rule.defaultCategory} ($typeLabel)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.testTag("edit_rule_button_${rule.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "编辑规则",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = AppIcons.DeleteOutline,
                    contentDescription = "删除规则",
                    tint = ExpenseCoral
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomRuleEditorDialog(
    initialRule: CustomParsingRule?,
    activeCategories: List<String> = CategoryCatalog.allCategories,
    onDismiss: () -> Unit,
    onSave: (CustomParsingRule) -> Unit
) {
    var name by remember { mutableStateOf(initialRule?.name ?: "") }
    var keyword by remember { mutableStateOf(initialRule?.keywordPattern ?: "") }
    var channel by remember { mutableStateOf(initialRule?.channelName ?: "") }
    var packageFilter by remember { mutableStateOf(initialRule?.packageFilter ?: "") }
    var merchantOverride by remember { mutableStateOf(initialRule?.merchantOverride ?: "") }
    var category by remember { mutableStateOf(initialRule?.defaultCategory ?: "自动") }
    var defaultType by remember { mutableStateOf(initialRule?.defaultType ?: "AUTO") }
    var numberStyle by remember {
        mutableStateOf(com.example.model.NumberFormatStyle.fromRaw(initialRule?.numberFormatPattern))
    }
    var errorText by remember { mutableStateOf<String?>(null) }

    val typeChoices = listOf(
        "AUTO" to "自动判断",
        TransactionType.EXPENSE.name to TransactionType.EXPENSE.label,
        TransactionType.INCOME.name to TransactionType.INCOME.label,
        TransactionType.TRANSFER.name to TransactionType.TRANSFER.label,
        "IGNORE" to "忽略非账单"
    )
    val categoryChoices = remember(activeCategories, initialRule?.defaultCategory) {
        (listOf("自动") + activeCategories + listOfNotNull(initialRule?.defaultCategory?.takeIf { it.isNotBlank() }))
            .distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialRule == null) "新建通知识别规则" else "编辑识别规则") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text("规则名称") },
                    placeholder = { Text("例如：公司食堂") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rule_editor_name_input")
                )
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it; errorText = null },
                    label = { Text("匹配关键词 / 正则") },
                    placeholder = { Text("例如：员工食堂|北京银行") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rule_editor_keyword_input")
                )
                OutlinedTextField(
                    value = channel,
                    onValueChange = { channel = it },
                    label = { Text("归属渠道 / 银行") },
                    placeholder = { Text("例如：招商银行、支付宝") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = merchantOverride,
                    onValueChange = { merchantOverride = it },
                    label = { Text("固定商户名（可选）") },
                    placeholder = { Text("留空则自动从通知提取") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = packageFilter,
                    onValueChange = { packageFilter = it },
                    label = { Text("限定包名（可选）") },
                    placeholder = { Text("留空或填写 * 表示不限应用") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("数字金额格式", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.example.model.NumberFormatStyle.entries.forEach { style ->
                        FilterChip(
                            selected = numberStyle == style,
                            onClick = { numberStyle = style },
                            label = { Text(style.label) }
                        )
                    }
                }
                Text("收支类型", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    typeChoices.forEach { (code, label) ->
                        FilterChip(
                            selected = defaultType.equals(code, ignoreCase = true),
                            onClick = { defaultType = code },
                            label = { Text(label) }
                        )
                    }
                }
                HorizontalDivider()
                Text("默认分类", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categoryChoices.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                if (errorText != null) {
                    Text(errorText!!, color = ExpenseCoral, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || keyword.isBlank() || channel.isBlank()) {
                        errorText = "请完整填写规则名称、关键词和渠道"
                        return@Button
                    }
                    val updated = (initialRule ?: CustomParsingRule(priority = 10)).copy(
                        name = name.trim(),
                        packageFilter = packageFilter.trim().ifBlank { "*" },
                        keywordPattern = keyword.trim(),
                        channelName = channel.trim(),
                        merchantOverride = merchantOverride.trim(),
                        numberFormatPattern = numberStyle.name,
                        defaultCategory = category,
                        defaultType = defaultType
                    )
                    onSave(updated)
                },
                modifier = Modifier.testTag("rule_editor_save_button")
            ) {
                Text("保存规则")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
