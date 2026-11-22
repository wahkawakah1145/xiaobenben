package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import com.example.ui.theme.AppIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ChannelCatalog
import com.example.model.ChannelSplitter
import com.example.model.FeatureTipsCatalog
import com.example.ui.components.FeatureTipBanner
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.UiState

@Composable
fun ChannelManagementScreen(
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val appearance by viewModel.appearanceState.collectAsStateWithLifecycle()
    val transactionsState by viewModel.transactionsState.collectAsStateWithLifecycle()
    val rulesState by viewModel.rulesState.collectAsStateWithLifecycle()

    val allTransactions = (transactionsState as? UiState.Success)?.data.orEmpty()
    val allRules = (rulesState as? UiState.Success)?.data.orEmpty()

    val configuredSet = remember(settings.customChannels) {
        ChannelCatalog.activeChannels(settings).toHashSet()
    }

    val allChannels = remember(settings.customChannels, allTransactions, allRules) {
        ChannelCatalog.allExistingChannels(
            settings = settings,
            transactions = allTransactions,
            rules = allRules
        )
    }

    val txCountByChannel = remember(allTransactions) {
        val map = LinkedHashMap<String, Int>()
        allTransactions.forEach { tx ->
            ChannelSplitter.splitChannels(tx.channel).forEach { token ->
                map[token] = (map[token] ?: 0) + 1
            }
        }
        map
    }

    val ruleCountByChannel = remember(allRules) {
        val map = LinkedHashMap<String, Int>()
        allRules.filterNot { it.isIgnoreRule }.forEach { rule ->
            ChannelSplitter.splitChannels(rule.channelName).forEach { token ->
                map[token] = (map[token] ?: 0) + 1
            }
        }
        map
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    SettingsAutoScrollEffect(
        listState = listState,
        route = ROUTE_SETTINGS_CHANNELS,
        sectionKeysInOrder = listOf("channel_header_info", "channel_actions_bar", "channel_count_label")
    )

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("channel_management_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "channel_header_info") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsSearchArrivalBanner(
                    route = ROUTE_SETTINGS_CHANNELS,
                    onDismiss = { viewModel.clearSettingsHighlight() }
                )
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_CHANNELS, "channel_header_info")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                AppIcons.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = settingsHighlightedText("自定义渠道识别与同步", ROUTE_SETTINGS_CHANNELS),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = settingsHighlightedText(
                                "管理全部支付渠道，改名或删除时可同步账单、规则与 AI 提示词。",
                                ROUTE_SETTINGS_CHANNELS
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                        )
                        FeatureTipBanner(
                            tipId = FeatureTipsCatalog.CHANNEL_ATTRIBUTION,
                            text = "智能归属：花呗 / 余额宝自动归属「支付宝」，零钱 / 零钱通自动归属「微信支付」。",
                            appearance = appearance,
                            onDismiss = { viewModel.dismissFeatureTip(it) }
                        )
                    }
                }
            }
        }

        item(key = "channel_actions_bar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("channel_add_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("新增渠道")
                }
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.testTag("channel_reset_button")
                ) {
                    Icon(AppIcons.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("恢复默认")
                }
            }
        }

        item(key = "channel_count_label") {
            Text(
                text = "当前全部渠道（共 ${allChannels.size} 个）",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        items(allChannels, key = { it }) { channel ->
            val txCount = txCountByChannel[channel] ?: 0
            val ruleCount = ruleCountByChannel[channel] ?: 0
            val isFromHistoryOnly = channel !in configuredSet
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("channel_item_$channel")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = channel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (isFromHistoryOnly) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = "来自历史",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "关联 $txCount 笔账单 · $ruleCount 条规则",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { renameTarget = channel },
                        modifier = Modifier.testTag("channel_edit_$channel")
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "编辑渠道名称",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { deleteTarget = channel },
                        enabled = allChannels.size > 1,
                        modifier = Modifier.testTag("channel_delete_$channel")
                    ) {
                        Icon(
                            AppIcons.DeleteOutline,
                            contentDescription = "删除渠道",
                            tint = if (allChannels.size > 1) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        var syncAi by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新增渠道") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(28) },
                        label = { Text("渠道名称（如：京东白条、美团月付）") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("channel_add_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("同步到 AI 理解提示词", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = syncAi, onCheckedChange = { syncAi = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addCustomChannel(name = newName, syncAiPrompt = syncAi)
                        showAddDialog = false
                    },
                    enabled = newName.trim().isNotEmpty(),
                    modifier = Modifier.testTag("channel_add_confirm")
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            }
        )
    }

    renameTarget?.let { oldName ->
        var editedName by remember(oldName) { mutableStateOf(oldName) }
        var syncTx by remember(oldName) { mutableStateOf(true) }
        var syncRules by remember(oldName) { mutableStateOf(true) }
        var syncAi by remember(oldName) { mutableStateOf(true) }
        val txCount = txCountByChannel[oldName] ?: 0
        val ruleCount = ruleCountByChannel[oldName] ?: 0
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("编辑渠道「$oldName」") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it.take(28) },
                        label = { Text("新渠道名称") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("channel_rename_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("同步历史账单（$txCount 笔）", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = syncTx, onCheckedChange = { syncTx = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("同步识别规则（$ruleCount 条）", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = syncRules, onCheckedChange = { syncRules = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("同步 AI 理解提示词", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = syncAi, onCheckedChange = { syncAi = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameChannel(
                            oldName = oldName,
                            newName = editedName,
                            syncTransactions = syncTx,
                            syncRules = syncRules,
                            syncAiPrompt = syncAi
                        )
                        renameTarget = null
                    },
                    enabled = editedName.trim().isNotEmpty() && !editedName.trim().equals(oldName, ignoreCase = true),
                    modifier = Modifier.testTag("channel_rename_confirm")
                ) {
                    Text("保存并同步")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    deleteTarget?.let { target ->
        val fallbackOptions = remember(allChannels, target) {
            allChannels.filterNot { it.equals(target, ignoreCase = true) }.ifEmpty { listOf("其他") }
        }
        var fallbackChannel by remember(target) {
            mutableStateOf(if ("其他" in fallbackOptions) "其他" else fallbackOptions.first())
        }
        var menuExpanded by remember { mutableStateOf(false) }
        var syncTx by remember(target) { mutableStateOf(true) }
        var syncRules by remember(target) { mutableStateOf(true) }
        var syncAi by remember(target) { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除渠道「$target」") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "删除后，已关联该渠道的历史账单与规则将自动归入以下替代渠道：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        FilledTonalButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("替代渠道：$fallbackChannel")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            fallbackOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = {
                                        fallbackChannel = opt
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("同步迁移历史账单", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = syncTx, onCheckedChange = { syncTx = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("同步迁移识别规则", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = syncRules, onCheckedChange = { syncRules = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("同步更新 AI 提示词", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = syncAi, onCheckedChange = { syncAi = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteChannel(
                            targetName = target,
                            fallbackChannel = fallbackChannel,
                            syncTransactions = syncTx,
                            syncRules = syncRules,
                            syncAiPrompt = syncAi
                        )
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("channel_delete_confirm")
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    if (showResetDialog) {
        var syncAi by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("恢复默认渠道列表？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "将把常用渠道列表恢复为系统内置默认集合（不会删除历史账单记录）。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("同时更新 AI 理解提示词", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = syncAi, onCheckedChange = { syncAi = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetChannelsToDefault(syncAiPrompt = syncAi)
                        showResetDialog = false
                    }
                ) {
                    Text("恢复默认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("取消") }
            }
        )
    }
}
