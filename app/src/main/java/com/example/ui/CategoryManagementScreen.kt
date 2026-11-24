package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.example.model.BudgetSettings
import com.example.model.CategoryCatalog
import com.example.model.CustomParsingRule
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import com.example.ui.components.CategoryIconCatalog
import com.example.ui.components.DraggableHierarchyColumn
import com.example.ui.components.categoryIcon
import com.example.ui.components.transactionTypeColor
import com.example.ui.theme.AppIcons
import com.example.ui.theme.ExpenseCoral
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryManagementScreen(
    settings: BudgetSettings,
    transactionsState: UiState<List<LedgerTransaction>>,
    viewModel: LedgerViewModel,
    rulesState: UiState<List<CustomParsingRule>>? = null,
    modifier: Modifier = Modifier
) {
    val observedRulesState by viewModel.rulesState.collectAsStateWithLifecycle()
    val effectiveRulesState = rulesState ?: observedRulesState
    val allTransactions = (transactionsState as? UiState.Success)?.data ?: emptyList()
    val allRules = (effectiveRulesState as? UiState.Success)?.data ?: emptyList()

    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingIconCategory by remember { mutableStateOf<String?>(null) }
    var renamingCategory by remember { mutableStateOf<String?>(null) }
    var deletingCategory by remember { mutableStateOf<String?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showConsolidateDialog by remember { mutableStateOf(false) }

    // Multi-select batch attribution state
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedNames by remember(selectedType) { mutableStateOf(emptySet<String>()) }
    var showBatchParentPicker by remember { mutableStateOf(false) }

    val configuredForType = remember(settings, selectedType) {
        CategoryCatalog.categoriesForType(selectedType, settings).toSet()
    }
    val allDisplayedCategories = remember(settings, selectedType, allTransactions, allRules) {
        CategoryCatalog.allExistingCategoriesForType(
            type = selectedType,
            settings = settings,
            transactions = allTransactions,
            rules = allRules
        )
    }
    val visibleCategories = remember(allDisplayedCategories, settings.categoryParentMap, settings.categoryShowChildren) {
        if (settings.categoryShowChildren) {
            allDisplayedCategories
        } else {
            CategoryCatalog.topLevelCategories(allDisplayedCategories, settings.categoryParentMap)
        }
    }
    val hasAnyHierarchy = remember(allDisplayedCategories, settings.categoryParentMap) {
        allDisplayedCategories.any { CategoryCatalog.resolveDirectParent(it, settings.categoryParentMap) != null }
    }

    val txCountByCategory = remember(allTransactions) {
        allTransactions.groupingBy { it.category }.eachCount()
    }
    val ruleCountByCategory = remember(allRules) {
        allRules.filterNot { it.isIgnoreRule }.groupingBy { it.defaultCategory }.eachCount()
    }
    val accentColor = transactionTypeColor(selectedType)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "category_header_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_CATEGORIES, "category_header_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsSearchArrivalBanner(
                            route = ROUTE_SETTINGS_CATEGORIES,
                            onDismiss = { viewModel.clearSettingsHighlight() }
                        )
                        Text(
                            text = settingsHighlightedText("分类、图标与归属管理", ROUTE_SETTINGS_CATEGORIES),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = settingsHighlightedText(
                                "长按拖动排序；将 A 拖放到 B 上且 B 高光时松手即可归属进 B。轻点左侧图标可更换图标。",
                                ROUTE_SETTINGS_CATEGORIES
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            TransactionType.entries.forEachIndexed { idx, type ->
                                val typeCount = remember(settings, type, allTransactions, allRules) {
                                    CategoryCatalog.allExistingCategoriesForType(
                                        type = type,
                                        settings = settings,
                                        transactions = allTransactions,
                                        rules = allRules
                                    ).size
                                }
                                SegmentedButton(
                                    selected = selectedType == type,
                                    onClick = {
                                        selectedType = type
                                        selectedNames = emptySet()
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = idx,
                                        count = TransactionType.entries.size
                                    ),
                                    modifier = Modifier.testTag("category_tab_${type.name}")
                                ) {
                                    Text("${type.label} ($typeCount)")
                                }
                            }
                        }

                        // Display mode switch: show all children vs parent-only
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (settings.categoryShowChildren) "显示全部子类" else "仅显示父类",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (settings.categoryShowChildren) {
                                        "列表与选择项展示所有分类及其归属关系"
                                    } else {
                                        "已归属的子类将折叠归并到其父类展示"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.categoryShowChildren,
                                onCheckedChange = { viewModel.setCategoryShowChildren(it) },
                                modifier = Modifier.testTag("category_show_children_switch")
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
                                    .testTag("category_add_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("新增${selectedType.label}分类")
                            }
                            OutlinedButton(
                                onClick = {
                                    multiSelectMode = !multiSelectMode
                                    if (!multiSelectMode) selectedNames = emptySet()
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("category_multiselect_toggle")
                            ) {
                                Text(if (multiSelectMode) "退出多选" else "多选归属")
                            }
                            if (hasAnyHierarchy) {
                                OutlinedButton(
                                    onClick = { showConsolidateDialog = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("category_consolidate_button")
                                ) {
                                    Text("一键整合子类到父类")
                                }
                            }
                            OutlinedButton(
                                onClick = { showResetConfirm = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("category_reset_default_button")
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
                                        text = "已选 ${selectedNames.size} 项",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        enabled = selectedNames.isNotEmpty(),
                                        onClick = {
                                            viewModel.batchSetCategoryParent(selectedNames, null)
                                            selectedNames = emptySet()
                                            multiSelectMode = false
                                        }
                                    ) {
                                        Text("解除归属")
                                    }
                                    Button(
                                        enabled = selectedNames.isNotEmpty(),
                                        onClick = { showBatchParentPicker = true },
                                        modifier = Modifier.testTag("category_batch_assign_button")
                                    ) {
                                        Text("一键归属到...")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "category_drag_list_${selectedType.name}") {
                DraggableHierarchyColumn(
                    items = visibleCategories,
                    key = { "${selectedType.name}_$it" },
                    spacing = 10.dp,
                    dragEnabled = !multiSelectMode,
                    enableNesting = !multiSelectMode,
                    canNest = { dragged, target ->
                        CategoryCatalog.canAssignParent(dragged, target, settings.categoryParentMap)
                    },
                    onReorder = { reordered ->
                        viewModel.reorderCategories(selectedType, reordered)
                    },
                    onNestInto = { dragged, target ->
                        viewModel.setCategoryParent(dragged, target)
                    }
                ) { catName, isDragging, isNestTarget, dragModifier ->
                    val txCount = txCountByCategory[catName] ?: 0
                    val ruleCount = ruleCountByCategory[catName] ?: 0
                    val isDiscoveredOnly = catName !in configuredForType
                    val customIconKey = settings.categoryIcons[catName]
                    val iconVec = categoryIcon(catName, settings.categoryIcons)
                    val directParent = CategoryCatalog.resolveDirectParent(catName, settings.categoryParentMap)
                    val childCount = CategoryCatalog.childrenOf(catName, allDisplayedCategories, settings.categoryParentMap).size
                    val isSelected = catName in selectedNames

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
                                isDiscoveredOnly -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = if (directParent != null && settings.categoryShowChildren) 18.dp else 0.dp)
                            .then(dragModifier)
                            .let { mod ->
                                if (multiSelectMode) {
                                    mod.clickable {
                                        selectedNames = if (isSelected) selectedNames - catName else selectedNames + catName
                                    }
                                } else {
                                    mod
                                }
                            }
                            .testTag("category_item_$catName")
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (isNestTarget) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "松手将拖动项归属进「$catName」",
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
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (multiSelectMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedNames = if (checked) selectedNames + catName else selectedNames - catName
                                        },
                                        modifier = Modifier.testTag("category_check_$catName")
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(accentColor.copy(alpha = 0.14f))
                                        .clickable(enabled = !multiSelectMode) { editingIconCategory = catName }
                                        .testTag("category_icon_picker_$catName"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = iconVec,
                                        contentDescription = "修改「$catName」图标",
                                        tint = accentColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = catName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (directParent != null) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "归属「$directParent」",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        if (childCount > 0) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "含 $childCount 子类",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        if (isDiscoveredOnly) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "历史/规则",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        } else if (!customIconKey.isNullOrBlank()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "自定图标",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "关联 $txCount 笔账单 · $ruleCount 条规则",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (!multiSelectMode) {
                                    if (directParent != null) {
                                        IconButton(
                                            onClick = { viewModel.setCategoryParent(catName, null) },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("category_unparent_$catName")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "解除归属",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { renamingCategory = catName },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("category_rename_$catName")
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "修改分类名称", modifier = Modifier.size(19.dp))
                                    }
                                    val canDelete = isDiscoveredOnly || configuredForType.size > 1
                                    IconButton(
                                        enabled = canDelete,
                                        onClick = { deletingCategory = catName },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("category_delete_$catName")
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.DeleteOutline,
                                            contentDescription = "删除或归属迁移分类",
                                            tint = if (canDelete) ExpenseCoral else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(19.dp)
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

    if (showBatchParentPicker) {
        val candidateParents = remember(allDisplayedCategories, selectedNames, settings.categoryParentMap) {
            allDisplayedCategories.filter { candidate ->
                candidate !in selectedNames &&
                    selectedNames.any { child ->
                        CategoryCatalog.canAssignParent(child, candidate, settings.categoryParentMap)
                    }
            }
        }
        AlertDialog(
            onDismissRequest = { showBatchParentPicker = false },
            title = { Text("将所选 ${selectedNames.size} 个分类归属到") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (candidateParents.isEmpty()) {
                        Text(
                            text = "没有可用的目标父分类（已跳过会形成循环归属的项）。",
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
                                        viewModel.batchSetCategoryParent(selectedNames, target)
                                        selectedNames = emptySet()
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
            title = { Text("一键整合子类到父类？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "将所有已配置附属关系的子分类账单与规则一键整合归并至对应的根父分类（保留归属关系规则，后续识别仍可自动归属）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CategorySyncToggleRow(
                        title = "整合历史账单",
                        subtitle = "将子类账单分类更新为其父分类",
                        checked = syncTx,
                        onCheckedChange = { syncTx = it }
                    )
                    CategorySyncToggleRow(
                        title = "整合识别规则",
                        subtitle = "将规则默认分类指向对应父分类",
                        checked = syncRules,
                        onCheckedChange = { syncRules = it }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConsolidateDialog = false }) { Text("取消") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.consolidateCategoryChildrenToParent(
                            parentName = null,
                            syncTransactions = syncTx,
                            syncRules = syncRules
                        )
                        showConsolidateDialog = false
                    },
                    modifier = Modifier.testTag("category_consolidate_confirm")
                ) {
                    Text("确认整合")
                }
            }
        )
    }

    editingIconCategory?.let { targetName ->
        val currentKey = settings.categoryIcons[targetName]
        CategoryIconPickerDialog(
            categoryName = targetName,
            selectedIconKey = currentKey,
            accentColor = accentColor,
            onSelectIcon = { key ->
                viewModel.setCategoryIcon(targetName, key)
                editingIconCategory = null
            },
            onDismiss = { editingIconCategory = null }
        )
    }

    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        var selectedIconKey by remember { mutableStateOf<String?>(null) }
        var selectedParent by remember { mutableStateOf<String?>(null) }
        var syncAi by remember { mutableStateOf(true) }
        var errorText by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新增${selectedType.label}分类") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(24); errorText = null },
                        label = { Text("分类名称") },
                        placeholder = { Text("例如：宠物开销、数码订阅、咖啡奶茶") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("category_add_name_input")
                    )
                    Text(
                        text = "可选归属父分类（留空则为独立父类）",
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
                            label = { Text("独立父类") }
                        )
                        allDisplayedCategories.forEach { cat ->
                            FilterChip(
                                selected = selectedParent == cat,
                                onClick = { selectedParent = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                    Text(
                        text = "快捷选择图标（可选）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryIconCatalog.options.forEach { option ->
                            val isSelected = selectedIconKey == option.key
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) accentColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(
                                    width = if (isSelected) 1.6.dp else 1.dp,
                                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedIconKey = if (isSelected) null else option.key
                                    }
                                    .testTag("category_add_icon_${option.key}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = option.label,
                                        tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("同步更新 AI 理解提示词", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "让 AI 解析账单时自动识别并归入该新分类",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = syncAi, onCheckedChange = { syncAi = it })
                    }
                    if (errorText != null) {
                        Text(errorText!!, color = ExpenseCoral, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isEmpty()) {
                            errorText = "请输入分类名称"
                            return@Button
                        }
                        if (trimmed in configuredForType) {
                            errorText = "该分类已存在"
                            return@Button
                        }
                        viewModel.addCustomCategory(
                            type = selectedType,
                            name = trimmed,
                            syncAiPrompt = syncAi,
                            iconKey = selectedIconKey
                        )
                        if (selectedParent != null && selectedParent != trimmed) {
                            viewModel.setCategoryParent(trimmed, selectedParent)
                        }
                        showAddDialog = false
                    },
                    modifier = Modifier.testTag("category_add_confirm_button")
                ) {
                    Text("添加")
                }
            }
        )
    }

    renamingCategory?.let { oldName ->
        var newName by remember(oldName) { mutableStateOf(oldName) }
        var syncTransactions by remember(oldName) { mutableStateOf(true) }
        var syncRules by remember(oldName) { mutableStateOf(true) }
        var syncAi by remember(oldName) { mutableStateOf(true) }
        var errorText by remember(oldName) { mutableStateOf<String?>(null) }
        val txCount = txCountByCategory[oldName] ?: 0
        val ruleCount = ruleCountByCategory[oldName] ?: 0

        AlertDialog(
            onDismissRequest = { renamingCategory = null },
            title = { Text("修改分类名称") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(24); errorText = null },
                        label = { Text("新分类名称") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("category_rename_input")
                    )
                    HorizontalDivider()
                    CategorySyncToggleRow(
                        title = "同步更新历史账单 ($txCount 笔)",
                        subtitle = "将原分类为「$oldName」的账单更新为新分类名",
                        checked = syncTransactions,
                        onCheckedChange = { syncTransactions = it }
                    )
                    CategorySyncToggleRow(
                        title = "同步更新识别规则 ($ruleCount 条)",
                        subtitle = "将默认分类指向「$oldName」的规则一并更新",
                        checked = syncRules,
                        onCheckedChange = { syncRules = it }
                    )
                    CategorySyncToggleRow(
                        title = "同步更新 AI 理解提示词",
                        subtitle = "动态更新系统/自定义提示词中的分类映射名称",
                        checked = syncAi,
                        onCheckedChange = { syncAi = it }
                    )
                    if (errorText != null) {
                        Text(errorText!!, color = ExpenseCoral, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingCategory = null }) { Text("取消") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isEmpty()) {
                            errorText = "分类名称不能为空"
                            return@Button
                        }
                        if (trimmed == oldName) {
                            errorText = "新名称与原名称相同"
                            return@Button
                        }
                        viewModel.renameCategory(
                            type = selectedType,
                            oldName = oldName,
                            newName = trimmed,
                            syncTransactions = syncTransactions,
                            syncRules = syncRules,
                            syncAiPrompt = syncAi
                        )
                        renamingCategory = null
                    },
                    modifier = Modifier.testTag("category_rename_confirm_button")
                ) {
                    Text("确认修改")
                }
            }
        )
    }

    deletingCategory?.let { targetName ->
        val remainingOptions = remember(allDisplayedCategories, targetName) {
            allDisplayedCategories.filterNot { it == targetName }.ifEmpty { listOf("其他") }
        }
        var fallbackCategory by remember(targetName) {
            mutableStateOf(
                CategoryCatalog.resolveDirectParent(targetName, settings.categoryParentMap)
                    ?.takeIf { it in remainingOptions }
                    ?: remainingOptions.firstOrNull { it == "其他" }
                    ?: remainingOptions.firstOrNull()
                    ?: "其他"
            )
        }
        var syncTransactions by remember(targetName) { mutableStateOf(true) }
        var syncRules by remember(targetName) { mutableStateOf(true) }
        var syncAi by remember(targetName) { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("删除分类「$targetName」") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "原归属此分类的历史账单与规则将归属到下方目标分类：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        remainingOptions.forEach { option ->
                            FilterChip(
                                selected = fallbackCategory == option,
                                onClick = { fallbackCategory = option },
                                label = { Text(option) }
                            )
                        }
                    }
                    HorizontalDivider()
                    CategorySyncToggleRow(
                        title = "归属历史账单",
                        subtitle = "归属到「$fallbackCategory」",
                        checked = syncTransactions,
                        onCheckedChange = { syncTransactions = it }
                    )
                    CategorySyncToggleRow(
                        title = "同步识别规则",
                        subtitle = "规则默认分类改为「$fallbackCategory」",
                        checked = syncRules,
                        onCheckedChange = { syncRules = it }
                    )
                    CategorySyncToggleRow(
                        title = "同步 AI 提示词",
                        subtitle = "从 AI 分类列表中移除「$targetName」",
                        checked = syncAi,
                        onCheckedChange = { syncAi = it }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) { Text("取消") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCategory(
                            type = selectedType,
                            targetName = targetName,
                            fallbackCategory = fallbackCategory,
                            syncTransactions = syncTransactions,
                            syncRules = syncRules,
                            syncAiPrompt = syncAi
                        )
                        deletingCategory = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseCoral),
                    modifier = Modifier.testTag("category_delete_confirm_button")
                ) {
                    Text("确认删除")
                }
            }
        )
    }

    if (showResetConfirm) {
        var resetIconsToo by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("恢复默认分类列表？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("将把支出、收入与转账分类列表及归属关系恢复为默认，并同步刷新 AI 理解提示词（不会修改已有历史账单记录）。")
                    CategorySyncToggleRow(
                        title = "同时重置自定义分类图标",
                        subtitle = "关闭则保留已设置的分类图标",
                        checked = resetIconsToo,
                        onCheckedChange = { resetIconsToo = it }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetCategoriesToDefault(syncAiPrompt = true, resetIcons = resetIconsToo)
                        showResetConfirm = false
                    },
                    modifier = Modifier.testTag("category_reset_confirm_button")
                ) {
                    Text("确认恢复")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryIconPickerDialog(
    categoryName: String,
    selectedIconKey: String?,
    accentColor: androidx.compose.ui.graphics.Color,
    onSelectIcon: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("选择「$categoryName」图标")
                Text(
                    text = "点选后立即生效，同步应用到账单列表、详情页与分析图表",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryIconCatalog.options.forEach { option ->
                        val isSelected = selectedIconKey.equals(option.key, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) accentColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = if (isSelected) 1.6.dp else 1.dp,
                                color = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectIcon(option.key) }
                                .testTag("icon_picker_option_${option.key}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(64.dp)
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = option.label,
                                    tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (!selectedIconKey.isNullOrBlank()) {
                TextButton(
                    onClick = { onSelectIcon(null) },
                    modifier = Modifier.testTag("icon_picker_reset_default")
                ) {
                    Text("恢复默认图标")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun CategorySyncToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
