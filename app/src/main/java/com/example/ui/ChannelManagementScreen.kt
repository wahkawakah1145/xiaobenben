package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ChannelCatalog
import com.example.model.ChannelSplitter
import com.example.model.FeatureTipsCatalog
import com.example.ui.components.DraggableHierarchyColumn
import com.example.ui.components.FeatureTipBanner
import com.example.ui.theme.AppIcons
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
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

    val visibleChannels = remember(allChannels, settings.channelParentMap, settings.channelShowChildren) {
        if (settings.channelShowChildren) {
            allChannels
        } else {
            ChannelCatalog.topLevelChannels(allChannels, settings.channelParentMap)
        }
    }

    val hasAnyHierarchy = remember(allChannels, settings.channelParentMap) {
        allChannels.any { ChannelCatalog.resolveDirectParent(it, settings.channelParentMap) != null }
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
    var showConsolidateDialog by remember { mutableStateOf(false) }

    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedChannels by remember { mutableStateOf(emptySet<String>()) }
    var showBatchParentPicker by remember { mutableStateOf(false) }

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
                                text = settingsHighlightedText("渠道归属、排序与同步", ROUTE_SETTINGS_CHANNELS),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = settingsHighlightedText(
                                "长按拖动排序；将 A 拖放到 B 上且 B 高光时松手即可归属进 B，也可多选一键归属或整合子类到父渠道。",
                                ROUTE_SETTINGS_CHANNELS
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                        )
                        FeatureTipBanner(
                            tipId = FeatureTipsCatalog.CHANNEL_ATTRIBUTION,
                            text = "默认归属：花呗 / 余额宝归属「支付宝」，零钱 / 零钱通归属「微信支付」，支持自定义任意渠道附属关系。",
                            appearance = appearance,
                            onDismiss = { viewModel.dismissFeatureTip(it) }
                        )
                    }
                }
            }
        }

        item(key = "channel_actions_bar") {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (settings.channelShowChildren) "显示全部子渠道" else "仅显示父渠道",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (settings.channelShowChildren) {
                                    "展示所有独立渠道与子渠道（如花呗、零钱等）"
                                } else {
                                    "自动将子渠道归并至对应父渠道显示与识别"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.channelShowChildren,
                            onCheckedChange = { viewModel.setChannelShowChildren(it) },
                            modifier = Modifier.testTag("channel_show_children_switch")
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("channel_add_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("新增渠道")
                        }
                        OutlinedButton(
                            onClick = {
                                multiSelectMode = !multiSelectMode
                                if (!multiSelectMode) selectedChannels = emptySet()
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("channel_multiselect_toggle")
                        ) {
                            Text(if (multiSelectMode) "退出多选" else "多选归属")
                        }
                        if (hasAnyHierarchy) {
                            OutlinedButton(
                                onClick = { showConsolidateDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("channel_consolidate_button")
                            ) {
                                Text("一键整合子类到父类")
                            }
                        }
                        OutlinedButton(
                            onClick = { showResetDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("channel_reset_button")
                        ) {
                            Icon(AppIcons.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("恢复默认")
                        }
                    }

                    if (multiSelectMode) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "已选 ${selectedChannels.size} 项",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    enabled = selectedChannels.isNotEmpty(),
                                    onClick = {
                                        viewModel.batchSetChannelParent(selectedChannels, null)
                                        selectedChannels = emptySet()
                                        multiSelectMode = false
                                    }
                                ) {
                                    Text("解除归属")
                                }
                                Button(
                                    enabled = selectedChannels.isNotEmpty(),
                                    onClick = { showBatchParentPicker = true },
                                    modifier = Modifier.testTag("channel_batch_assign_button")
                                ) {
                                    Text("一键归属到...")
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "channel_count_label") {
            Text(
                text = "当前展示渠道（共 ${visibleChannels.size} / ${allChannels.size} 个）",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        item(key = "channel_drag_list") {
            DraggableHierarchyColumn(
                items = visibleChannels,
                key = { it },
                spacing = 10.dp,
                dragEnabled = !multiSelectMode,
                enableNesting = !multiSelectMode,
                canNest = { dragged, target ->
                    ChannelCatalog.canAssignParent(dragged, target, settings.channelParentMap)
                },
                onReorder = { reordered ->
                    viewModel.reorderChannels(reordered)
                },
                onNestInto = { dragged, target ->
                    viewModel.setChannelParent(dragged, target)
                }
            ) { channel, isDragging, isNestTarget, dragModifier ->
                val txCount = txCountByChannel[channel] ?: 0
                val ruleCount = ruleCountByChannel[channel] ?: 0
                val isFromHistoryOnly = channel !in configuredSet
                val directParent = ChannelCatalog.resolveDirectParent(channel, settings.channelParentMap)
                val childCount = ChannelCatalog.childrenOf(channel, allChannels, settings.channelParentMap).size
                val isSelected = channel in selectedChannels

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = when {
                        isNestTarget -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                        isDragging -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f)
                        else -> MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = if (isNestTarget || isDragging) 2.dp else 1.dp,
                        color = when {
                            isNestTarget -> MaterialTheme.colorScheme.primary
                            isDragging -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = if (directParent != null && settings.channelShowChildren) 18.dp else 0.dp)
                        .then(dragModifier)
                        .let { mod ->
                            if (multiSelectMode) {
                                mod.clickable {
                                    selectedChannels = if (isSelected) selectedChannels - channel else selectedChannels + channel
                                }
                            } else {
                                mod
                            }
                        }
                        .testTag("channel_item_$channel")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isNestTarget) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "松手将拖动项归属进「$channel」",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (multiSelectMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedChannels = if (checked) selectedChannels + channel else selectedChannels - channel
                                    },
                                    modifier = Modifier.testTag("channel_check_$channel")
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = channel,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (directParent != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Text(
                                                text = "归属「$directParent」",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (childCount > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "含 $childCount 子渠道",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (isFromHistoryOnly) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = "来自历史",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                            if (!multiSelectMode) {
                                if (directParent != null) {
                                    IconButton(
                                        onClick = { viewModel.setChannelParent(channel, null) },
                                        modifier = Modifier.testTag("channel_unparent_$channel")
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "解除渠道归属",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
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
            }
        }
    }

    if (showBatchParentPicker) {
        val candidateParents = remember(allChannels, selectedChannels, settings.channelParentMap) {
            allChannels.filter { candidate ->
                candidate !in selectedChannels &&
                    selectedChannels.any { child ->
                        ChannelCatalog.canAssignParent(child, candidate, settings.channelParentMap)
                    }
            }
        }
        AlertDialog(
            onDismissRequest = { showBatchParentPicker = false },
            title = { Text("将所选 ${selectedChannels.size} 个渠道归属到") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (candidateParents.isEmpty()) {
                        Text(
                            text = "没有可用的目标父渠道（已跳过会形成循环归属的项）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            candidateParents.forEach { target ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        viewModel.batchSetChannelParent(selectedChannels, target)
                                        selectedChannels = emptySet()
                                        multiSelectMode = false
                                        showBatchParentPicker = false
                                    },
                                    label = { Text(target) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatchParentPicker = false }) { Text("关闭") }
            }
        )
    }

    if (showConsolidateDialog) {
        var syncTx by remember { mutableStateOf(true) }
        var syncRules by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showConsolidateDialog = false },
            title = { Text("一键整合子渠道到父渠道？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "将所有已配置附属关系的子渠道账单与规则一键归并至其根父渠道（如将「花呗」「余额宝」整合至「支付宝」）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("整合历史账单渠道", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = syncTx, onCheckedChange = { syncTx = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("整合识别规则渠道", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = syncRules, onCheckedChange = { syncRules = it })
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showConsolidateDialog = false }) { Text("取消") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.consolidateChannelChildrenToParent(
                            parentName = null,
                            syncTransactions = syncTx,
                            syncRules = syncRules
                        )
                        showConsolidateDialog = false
                    },
                    modifier = Modifier.testTag("channel_consolidate_confirm")
                ) {
                    Text("确认整合")
                }
            }
        )
    }

    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        var selectedParent by remember { mutableStateOf<String?>(null) }
        var syncAi by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新增渠道") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(28) },
                        label = { Text("渠道名称（如：京东白条、美团月付）") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("channel_add_input")
                    )
                    Text(
                        text = "可选归属父渠道（留空则为独立父渠道）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedParent == null,
                            onClick = { selectedParent = null },
                            label = { Text("独立父渠道") }
                        )
                        allChannels.take(12).forEach { ch ->
                            FilterChip(
                                selected = selectedParent == ch,
                                onClick = { selectedParent = ch },
                                label = { Text(ch) }
                            )
                        }
                    }
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
                        val trimmed = newName.trim()
                        viewModel.addCustomChannel(name = trimmed, syncAiPrompt = syncAi)
                        if (selectedParent != null && !selectedParent.equals(trimmed, ignoreCase = true)) {
                            viewModel.setChannelParent(trimmed, selectedParent)
                        }
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
            mutableStateOf(
                ChannelCatalog.resolveDirectParent(target, settings.channelParentMap)
                    ?.takeIf { it in fallbackOptions }
                    ?: if ("其他" in fallbackOptions) "其他" else fallbackOptions.first()
            )
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
                        text = "删除后，已关联该渠道的历史账单与规则将自动归属到以下渠道：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        FilledTonalButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("归属目标渠道：$fallbackChannel")
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
                        Text("同步归属历史账单", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = syncTx, onCheckedChange = { syncTx = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("同步归属识别规则", style = MaterialTheme.typography.bodySmall)
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
            title = { Text("恢复默认渠道与归属关系？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "将把常用渠道列表与默认归属关系恢复为内置默认集合（不会删除历史账单记录）。",
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
