package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.example.ui.components.LinkedTransactionsDialog
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
    var inspectingBillsCategory by remember { mutableStateOf<String?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    // Expanded parent categories (default collapsed; clicking parent card or arrow toggles smoothly)
    var expandedParents by remember(selectedType) { mutableStateOf(emptySet<String>()) }

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
    val topLevelCategories = remember(allDisplayedCategories, settings.categoryParentMap) {
        CategoryCatalog.topLevelCategories(allDisplayedCategories, settings.categoryParentMap)
    }
    val childrenByParent = remember(topLevelCategories, allDisplayedCategories, settings.categoryParentMap) {
        topLevelCategories.associateWith { parent ->
            CategoryCatalog.descendantsOf(parent, allDisplayedCategories, settings.categoryParentMap)
        }
    }

    val txListByCategory = remember(allTransactions, selectedType) {
        allTransactions
            .filter { it.type == selectedType.name }
            .groupBy { it.category }
    }
    val ruleCountByCategory = remember(allRules) {
        allRules.filterNot { it.isIgnoreRule }.groupingBy { it.defaultCategory }.eachCount()
    }
    val accentColor = transactionTypeColor(selectedType)
    val listState = rememberLazyListState()

    fun spliceFullCategoryOrder(updatedSubset: List<String>): List<String> {
        val subsetSet = updatedSubset.toHashSet()
        val iterator = updatedSubset.iterator()
        return allDisplayedCategories.map { item ->
            if (item in subsetSet && iterator.hasNext()) iterator.next() else item
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
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
                                "长按拖动排序；将 A 拖到 B 上稍作停顿待 B 高光后松手即可归属进 B。点击含子类项可展开/收起，各项修改均可撤回。",
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

                        // Non-destructive bill display mode: show child name vs parent name (never alters raw bills)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "账单中分类显示方式（不改动账单原始内容）",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = settings.categoryShowChildren,
                                    onClick = { viewModel.setCategoryShowChildren(true) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    modifier = Modifier.testTag("category_display_child_mode")
                                ) {
                                    Text("账单显示子类名")
                                }
                                SegmentedButton(
                                    selected = !settings.categoryShowChildren,
                                    onClick = { viewModel.setCategoryShowChildren(false) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    modifier = Modifier.testTag("category_display_parent_mode")
                                ) {
                                    Text("账单显示父类名")
                                }
                            }
                            Text(
                                text = if (settings.categoryShowChildren) {
                                    "账单列表、详情与统计按子类原名显示，不修改原始账单数据"
                                } else {
                                    "账单列表、详情与统计自动按所属父类名归并显示，原始账单保持不变且可随时切回"
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
                                        Text("移出父类")
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
                    items = topLevelCategories,
                    key = { "${selectedType.name}_$it" },
                    spacing = 10.dp,
                    dragEnabled = !multiSelectMode,
                    enableNesting = !multiSelectMode,
                    lazyListState = listState,
                    canNest = { dragged, target ->
                        CategoryCatalog.canAssignParent(dragged, target, settings.categoryParentMap)
                    },
                    onReorder = { reorderedTop ->
                        viewModel.reorderCategories(selectedType, spliceFullCategoryOrder(reorderedTop))
                    },
                    onNestInto = { dragged, target ->
                        expandedParents = expandedParents + target
                        viewModel.setCategoryParent(dragged, target)
                    }
                ) { catName, isDragging, isNestTarget, dragModifier ->
                    val children = childrenByParent[catName].orEmpty()
                    val isExpanded = catName in expandedParents
                    val exactTxCount = txListByCategory[catName]?.size ?: 0
                    val totalTxCount = exactTxCount + children.sumOf { txListByCategory[it]?.size ?: 0 }
                    val ruleCount = ruleCountByCategory[catName] ?: 0
                    val isDiscoveredOnly = catName !in configuredForType
                    val customIconKey = settings.categoryIcons[catName]
                    val iconVec = categoryIcon(catName, settings.categoryIcons)
                    val isSelected = catName in selectedNames

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
                                isDiscoveredOnly -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(dragModifier)
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
                                    .clickable {
                                        if (multiSelectMode) {
                                            selectedNames = if (isSelected) selectedNames - catName else selectedNames + catName
                                        } else if (children.isNotEmpty()) {
                                            expandedParents = if (isExpanded) expandedParents - catName else expandedParents + catName
                                        }
                                    }
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
                                        if (children.isNotEmpty()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        expandedParents = if (isExpanded) expandedParents - catName else expandedParents + catName
                                                    }
                                                    .testTag("category_expand_toggle_$catName")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        text = "含 ${children.size} 子类",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Icon(
                                                        imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                                        contentDescription = if (isExpanded) "收起子类" else "展开子类",
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (isDiscoveredOnly) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "历史/规则",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
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

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { inspectingBillsCategory = catName }
                                                .testTag("category_bills_button_$catName")
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
                                            contentDescription = "删除分类",
                                            tint = if (canDelete) ExpenseCoral else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }

                            // Expandable nested child list inside the parent card
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
                                        key = { "${selectedType.name}_child_${catName}_$it" },
                                        spacing = 6.dp,
                                        dragEnabled = !multiSelectMode && children.size > 1,
                                        enableNesting = false,
                                        onReorder = { reorderedChildren ->
                                            viewModel.reorderCategories(selectedType, spliceFullCategoryOrder(reorderedChildren))
                                        }
                                    ) { childName, childDragging, _, childDragMod ->
                                        val childTxCount = txListByCategory[childName]?.size ?: 0
                                        val childRuleCount = ruleCountByCategory[childName] ?: 0
                                        val childSelected = childName in selectedNames
                                        val childIconVec = categoryIcon(childName, settings.categoryIcons)

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
                                                            selectedNames = if (childSelected) selectedNames - childName else selectedNames + childName
                                                        }
                                                    } else mod
                                                }
                                                .testTag("category_child_item_$childName")
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
                                                            selectedNames = if (checked) selectedNames + childName else selectedNames - childName
                                                        },
                                                        modifier = Modifier.testTag("category_check_$childName")
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(accentColor.copy(alpha = 0.12f))
                                                        .clickable(enabled = !multiSelectMode) { editingIconCategory = childName }
                                                        .testTag("category_icon_picker_$childName"),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = childIconVec,
                                                        contentDescription = "修改「$childName」图标",
                                                        tint = accentColor,
                                                        modifier = Modifier.size(18.dp)
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
                                                                .clickable { inspectingBillsCategory = childName }
                                                                .testTag("category_bills_button_$childName")
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
                                                        onClick = { viewModel.setCategoryParent(childName, null) },
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .testTag("category_unparent_$childName")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Close,
                                                            contentDescription = "移出父类",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(17.dp)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { renamingCategory = childName },
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .testTag("category_rename_$childName")
                                                    ) {
                                                        Icon(Icons.Rounded.Edit, contentDescription = "修改子类名称", modifier = Modifier.size(17.dp))
                                                    }
                                                    IconButton(
                                                        onClick = { deletingCategory = childName },
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .testTag("category_delete_$childName")
                                                    ) {
                                                        Icon(
                                                            imageVector = AppIcons.DeleteOutline,
                                                            contentDescription = "删除子类",
                                                            tint = ExpenseCoral,
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
    }

    inspectingBillsCategory?.let { targetCat ->
        val descendants = remember(targetCat, allDisplayedCategories, settings.categoryParentMap) {
            CategoryCatalog.descendantsOf(targetCat, allDisplayedCategories, settings.categoryParentMap)
        }
        val exactTxs = remember(targetCat, txListByCategory) {
            txListByCategory[targetCat].orEmpty()
        }
        val withChildrenTxs = remember(targetCat, descendants, txListByCategory) {
            if (descendants.isEmpty()) exactTxs else {
                val names = (listOf(targetCat) + descendants).toHashSet()
                allTransactions.filter { it.type == selectedType.name && it.category in names }
            }
        }
        LinkedTransactionsDialog(
            title = "分类「$targetCat」关联账单",
            exactTransactions = exactTxs,
            withChildrenTransactions = withChildrenTxs,
            hasChildren = descendants.isNotEmpty(),
            settings = settings,
            viewModel = viewModel,
            onDismiss = { inspectingBillsCategory = null }
        )
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
                                        expandedParents = expandedParents + target
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

    editingIconCategory?.let { targetName ->
        val currentKey = settings.categoryIcons[targetName]
        val linkedCount = (txListByCategory[targetName]?.size ?: 0) +
            CategoryCatalog.descendantsOf(targetName, allDisplayedCategories, settings.categoryParentMap)
                .sumOf { txListByCategory[it]?.size ?: 0 }
        CategoryIconPickerDialog(
            categoryName = targetName,
            selectedIconKey = currentKey,
            linkedCount = linkedCount,
            accentColor = accentColor,
            onInspectBills = { inspectingBillsCategory = targetName },
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
                        topLevelCategories.forEach { cat ->
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
                        selectedParent?.let { expandedParents = expandedParents + it }
                        viewModel.addCustomCategory(
                            type = selectedType,
                            name = trimmed,
                            syncAiPrompt = syncAi,
                            iconKey = selectedIconKey,
                            parentName = selectedParent
                        )
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
        val txCount = txListByCategory[oldName]?.size ?: 0
        val ruleCount = ruleCountByCategory[oldName] ?: 0

        AlertDialog(
            onDismissRequest = { renamingCategory = null },
            title = { Text("修改分类「$oldName」") },
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
                    OutlinedButton(
                        onClick = { inspectingBillsCategory = oldName },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("category_rename_view_bills")
                    ) {
                        Text("快捷查看关联账单（$txCount 笔）")
                    }
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
        val txCount = txListByCategory[targetName]?.size ?: 0

        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("删除分类「$targetName」") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { inspectingBillsCategory = targetName },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("category_delete_view_bills")
                    ) {
                        Text("查看关联账单（$txCount 笔）")
                    }
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
                    Text("将把支出、收入与转账分类列表及归属关系恢复为默认，并同步刷新 AI 理解提示词（不会修改已有历史账单记录，且支持撤回）。")
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
    linkedCount: Int,
    accentColor: androidx.compose.ui.graphics.Color,
    onInspectBills: () -> Unit,
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
                OutlinedButton(
                    onClick = onInspectBills,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("快捷查看关联账单（$linkedCount 笔）")
                }
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
