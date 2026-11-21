package com.example.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.BudgetSettings
import com.example.model.CategoryCatalog
import com.example.model.CustomParsingRule
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
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
    modifier: Modifier = Modifier
) {
    val rulesState by viewModel.rulesState.collectAsStateWithLifecycle()
    val allTransactions = (transactionsState as? UiState.Success)?.data ?: emptyList()
    val allRules = (rulesState as? UiState.Success)?.data ?: emptyList()

    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var showAddDialog by remember { mutableStateOf(false) }
    var renamingCategory by remember { mutableStateOf<String?>(null) }
    var deletingCategory by remember { mutableStateOf<String?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val activeCategories = remember(settings, selectedType) {
        CategoryCatalog.categoriesForType(selectedType, settings)
    }

    val txCountByCategory = remember(allTransactions) {
        allTransactions.groupingBy { it.category }.eachCount()
    }
    val ruleCountByCategory = remember(allRules) {
        allRules.groupingBy { it.defaultCategory }.eachCount()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "category_header_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("自定义分类管理", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "可自由新增、重命名或移除支出/收入/转账分类；修改分类名时默认同步更新对应历史账单、通知识别规则与 AI 理解系统提示词。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            TransactionType.entries.forEachIndexed { idx, type ->
                                SegmentedButton(
                                    selected = selectedType == type,
                                    onClick = { selectedType = type },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = idx,
                                        count = TransactionType.entries.size
                                    ),
                                    modifier = Modifier.testTag("category_tab_${type.name}")
                                ) {
                                    Text("${type.label} (${CategoryCatalog.categoriesForType(type, settings).size})")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showAddDialog = true },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("category_add_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("新增${selectedType.label}分类")
                            }
                            OutlinedButton(
                                onClick = { showResetConfirm = true },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("category_reset_default_button")
                            ) {
                                Icon(AppIcons.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("恢复默认")
                            }
                        }
                    }
                }
            }

            items(activeCategories, key = { "${selectedType.name}_$it" }) { catName ->
                val txCount = txCountByCategory[catName] ?: 0
                val ruleCount = ruleCountByCategory[catName] ?: 0
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_item_$catName")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = catName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "关联 $txCount 笔账单 · $ruleCount 条识别规则",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { renamingCategory = catName },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("category_rename_$catName")
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "修改分类名称")
                        }
                        IconButton(
                            enabled = activeCategories.size > 1,
                            onClick = { deletingCategory = catName },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("category_delete_$catName")
                        ) {
                            Icon(
                                imageVector = AppIcons.DeleteOutline,
                                contentDescription = "删除分类",
                                tint = if (activeCategories.size > 1) ExpenseCoral else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        var syncAi by remember { mutableStateOf(true) }
        var errorText by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新增${selectedType.label}分类") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        if (trimmed in activeCategories) {
                            errorText = "该分类已存在"
                            return@Button
                        }
                        viewModel.addCustomCategory(
                            type = selectedType,
                            name = trimmed,
                            syncAiPrompt = syncAi
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
                        subtitle = "更新系统/自定义提示词中的分类映射名称",
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
        val remainingOptions = remember(activeCategories, targetName) {
            activeCategories.filterNot { it == targetName }
        }
        var fallbackCategory by remember(targetName) {
            mutableStateOf(
                remainingOptions.firstOrNull { it == "其他" } ?: remainingOptions.firstOrNull() ?: "其他"
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
                        text = "原归属此分类的历史账单与规则将迁移到下方目标分类：",
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
                        title = "迁移历史账单",
                        subtitle = "归入「$fallbackCategory」",
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
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("恢复默认分类列表？") },
            text = {
                Text("将把支出、收入与转账分类列表恢复为系统内置默认分类，并同步刷新 AI 理解提示词（不会修改已有历史账单记录）。")
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetCategoriesToDefault(syncAiPrompt = true)
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
