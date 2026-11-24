package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ChannelCatalog
import com.example.model.ChannelSplitter
import com.example.model.FeatureTipsCatalog
import com.example.model.LedgerTransaction
import com.example.ui.components.DraggableHierarchyColumn
import com.example.ui.components.FeatureTipBanner
import com.example.ui.components.LinkedTransactionsDialog
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

    val topLevelChannels = remember(allChannels, settings.channelParentMap) {
        ChannelCatalog.topLevelChannels(allChannels, settings.channelParentMap)
    }

    val childrenByParent = remember(topLevelChannels, allChannels, settings.channelParentMap) {
        topLevelChannels.associateWith { parent ->
            ChannelCatalog.descendantsOf(parent, allChannels, settings.channelParentMap)
        }
    }

    val txListByChannel = remember(allTransactions) {
        val map = LinkedHashMap<String, MutableList<LedgerTransaction>>()
        allTransactions.forEach { tx ->
            ChannelSplitter.splitChannels(tx.channel).distinct().forEach { token ->
                map.getOrPut(token) { ArrayList() }.add(tx)
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

    var expandedParents by remember { mutableStateOf(emptySet<String>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var inspectingBillsChannel by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedChannels by remember { mutableStateOf(emptySet<String>()) }
    var showBatchParentPicker by remember { mutableStateOf(false) }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    SettingsAutoScrollEffect(
        listState = listState,
        route = ROUTE_SETTINGS_CHANNELS,
        sectionKeysInOrder = listOf("channel_header_info", "channel_actions_bar", "channel_count_label")
    )

    fun spliceFullChannelOrder(updatedSubset: List<String>): List<String> {
        val subsetSet = updatedSubset.toHashSet()
        val iterator = updatedSubset.iterator()
        return allChannels.map { item ->
            if (item in subsetSet && iterator.hasNext()) iterator.next() else item
        }
    }

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
                                "长按拖动排序；将 A 拖到 B 上稍作停顿待 B 高光后松手即可归属进 B。点击含子渠道项可展开/收起，各项修改均可撤回。",
                                ROUTE_SETTINGS_CHANNELS
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                        )
                        FeatureTipBanner(
                            tipId = FeatureTipsCatalog.CHANNEL_ATTRIBUTION,
                            text = "默认归属：花呗 / 余额宝归属「支付宝」，零钱 / 零钱通归属「微信支付」，点击对应父渠道即可展开查看与管理。",
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
                    // Non-destructive bill display mode: child name vs parent name (never alters raw bills)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "账单中渠道显示方式（不改动账单原始内容）",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = settings.channelShowChildren,
                                onClick = { viewModel.setChannelShowChildren(true) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                modifier = Modifier.testTag("channel_display_child_mode")
                            ) {
                                Text("账单显示子渠道名")
                            }
                            SegmentedButton(
                                selected = !settings.channelShowChildren,
                                onClick = { viewModel.setChannelShowChildren(false) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                modifier = Modifier.testTag("channel_display_parent_mode")
                            ) {
                                Text("账单显示父渠道名")
                            }
                        }
                        Text(
                            text = if (settings.channelShowChildren) {
                                "账单列表、详情与统计按子渠道原名显示（如「花呗」「零钱通」），不修改原始账单"
                            } else {
                                "账单列表、详情与统计自动归并显示为父渠道名（如「支付宝」「微信支付」），原始账单保持不变且可随时切回"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    Text("移出父渠道")
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
                text = "渠道分组列表（${topLevelChannels.size} 个主渠道 · 共 ${allChannels.size} 个）",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        item(key = "channel_drag_list") {
            DraggableHierarchyColumn(
                items = topLevelChannels,
                key = { it },
                spacing = 10.dp,
                dragEnabled = !multiSelectMode,
                enableNesting = !multiSelectMode,
                lazyListState = listState,
                canNest = { dragged, target ->
                    ChannelCatalog.canAssignParent(dragged, target, settings.channelParentMap)
                },
                onReorder = { reorderedTop ->
                    viewModel.reorderChannels(spliceFullChannelOrder(reorderedTop))
                },
                onNestInto = { dragged, target ->
                    expandedParents = expandedParents + target
                    viewModel.setChannelParent(dragged, target)
                }
            ) { channel, isDragging, isNestTarget, dragModifier ->
                val children = childrenByParent[channel].orEmpty()
                val isExpanded = channel in expandedParents
                val exactTxCount = txListByChannel[channel]?.size ?: 0
                val totalTxCount = remember(channel, children, allTransactions) {
                    if (children.isEmpty()) exactTxCount else {
                        val targets = (listOf(channel) + children).mapTo(HashSet()) { it.lowercase() }
                        allTransactions.count { tx ->
                            ChannelSplitter.splitChannels(tx.channel).any { it.lowercase() in targets }
                        }
                    }
                }
                val ruleCount = ruleCountByChannel[channel] ?: 0
                val isFromHistoryOnly = channel !in configuredSet
                val isSelected = channel in selectedChannels

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = when {
                        isNestTarget -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
                        isDragging -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f)
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
                        .then(dragModifier)
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
                                .clickable {
                                    if (multiSelectMode) {
                                        selectedChannels = if (isSelected) selectedChannels - channel else selectedChannels + channel
                                    } else if (children.isNotEmpty()) {
                                        expandedParents = if (isExpanded) expandedParents - channel else expandedParents + channel
                                    }
                                }
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
                                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                    if (children.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    expandedParents = if (isExpanded) expandedParents - channel else expandedParents + channel
                                                }
                                                .testTag("channel_expand_toggle_$channel")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = "含 ${children.size} 子渠道",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                                    contentDescription = if (isExpanded) "收起子渠道" else "展开子渠道",
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { inspectingBillsChannel = channel }
                                            .testTag("channel_bills_button_$channel")
                                    ) {
                                        Text(
                                            text = if (children.isNotEmpty() && totalTxCount != exactTxCount) {
                                                "查看账单 $totalTxCount 笔 ›"
                                            } else {
                                                "查看账单 $exactTxCount 笔 ›"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (ruleCount > 0) {
                                        Text(
                                            text = "$ruleCount 规则",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (!multiSelectMode) {
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

                        // Expandable nested child channels inside parent card
                        AnimatedVisibility(
                            visible = children.isNotEmpty() && isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 12.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                DraggableHierarchyColumn(
                                    items = children,
                                    key = { "channel_child_${channel}_$it" },
                                    spacing = 6.dp,
                                    dragEnabled = !multiSelectMode && children.size > 1,
                                    enableNesting = false,
                                    onReorder = { reorderedChildren ->
                                        viewModel.reorderChannels(spliceFullChannelOrder(reorderedChildren))
                                    }
                                ) { childName, childDragging, _, childDragMod ->
                                    val childTxCount = txListByChannel[childName]?.size ?: 0
                                    val childRuleCount = ruleCountByChannel[childName] ?: 0
                                    val childSelected = childName in selectedChannels

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = when {
                                            childDragging -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                                            childSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                            else -> MaterialTheme.colorScheme.surfaceContainer
                                        },
                                        border = BorderStroke(
                                            width = if (childDragging) 1.5.dp else 0.8.dp,
                                            color = if (childDragging) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(childDragMod)
                                            .let { mod ->
                                                if (multiSelectMode) {
                                                    mod.clickable {
                                                        selectedChannels = if (childSelected) selectedChannels - childName else selectedChannels + childName
                                                    }
                                                } else mod
                                            }
                                            .testTag("channel_child_item_$childName")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (multiSelectMode) {
                                                Checkbox(
                                                    checked = childSelected,
                                                    onCheckedChange = { checked ->
                                                        selectedChannels = if (checked) selectedChannels + childName else selectedChannels - childName
                                                    },
                                                    modifier = Modifier.testTag("channel_check_$childName")
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = childName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "查看账单 $childTxCount 笔 ›",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier
                                                            .clickable { inspectingBillsChannel = childName }
                                                            .testTag("channel_bills_button_$childName")
                                                    )
                                                    if (childRuleCount > 0) {
                                                        Text(
                                                            text = "· $childRuleCount 规则",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                            if (!multiSelectMode) {
                                                IconButton(
                                                    onClick = { viewModel.setChannelParent(childName, null) },
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .testTag("channel_unparent_$childName")
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.Close,
                                                        contentDescription = "移出父渠道",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { renameTarget = childName },
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .testTag("channel_edit_$childName")
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.Edit,
                                                        contentDescription = "编辑子渠道名称",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { deleteTarget = childName },
                                                    enabled = allChannels.size > 1,
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .testTag("channel_delete_$childName")
                                                ) {
                                                    Icon(
                                                        AppIcons.DeleteOutline,
                                                        contentDescription = "删除子渠道",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(17.dp)
                                                    )
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
        }
    }

    inspectingBillsChannel?.let { targetCh ->
        val descendants = remember(targetCh, allChannels, settings.channelParentMap) {
            ChannelCatalog.descendantsOf(targetCh, allChannels, settings.channelParentMap)
        }
        val exactTxs = remember(targetCh, txListByChannel) {
            txListByChannel[targetCh].orEmpty()
        }
        val withChildrenTxs = remember(targetCh, descendants, allTransactions) {
            if (descendants.isEmpty()) exactTxs else {
                val targetSet = (listOf(targetCh) + descendants).mapTo(HashSet()) { it.lowercase() }
                allTransactions.filter { tx ->
                    ChannelSplitter.splitChannels(tx.channel).any { it.lowercase() in targetSet }
                }
            }
        }
        LinkedTransactionsDialog(
            title = "渠道「$targetCh」关联账单",
            exactTransactions = exactTxs,
            withChildrenTransactions = withChildrenTxs,
            hasChildren = descendants.isNotEmpty(),
            settings = settings,
            viewModel = viewModel,
            onDismiss = { inspectingBillsChannel = null }
        )
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
                                        expandedParents = expandedParents + target
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
                        topLevelChannels.take(12).forEach { ch ->
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
                        selectedParent?.let { expandedParents = expandedParents + it }
                        viewModel.addCustomChannel(
                            name = trimmed,
                            syncAiPrompt = syncAi,
                            parentName = selectedParent
                        )
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
        val txCount = txListByChannel[oldName]?.size ?: 0
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
                    OutlinedButton(
                        onClick = { inspectingBillsChannel = oldName },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("channel_rename_view_bills")
                    ) {
                        Text("快捷查看关联账单（$txCount 笔）")
                    }
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
        val txCount = txListByChannel[target]?.size ?: 0

        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除渠道「$target」") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { inspectingBillsChannel = target },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("channel_delete_view_bills")
                    ) {
                        Text("查看关联账单（$txCount 笔）")
                    }
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
                        text = "将把常用渠道列表与默认归属关系恢复为内置默认集合（不会删除历史账单记录，且支持撤回）。",
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
