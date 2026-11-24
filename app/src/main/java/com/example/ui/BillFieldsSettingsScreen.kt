package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.BillFieldCatalog
import com.example.model.BillFieldConfig
import com.example.model.BudgetSettings
import com.example.ui.components.DraggableHierarchyColumn
import com.example.ui.theme.AppIcons
import com.example.ui.theme.ExpenseCoral
import com.example.viewmodel.LedgerViewModel

@Composable
fun BillFieldsSettingsScreen(
    settings: BudgetSettings,
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val fields = remember(settings.billFieldsConfig) {
        BillFieldCatalog.normalizeFields(settings.billFieldsConfig)
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<BillFieldConfig?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    SettingsAutoScrollEffect(
        listState = listState,
        route = ROUTE_SETTINGS_BILL_FIELDS,
        sectionKeysInOrder = listOf("bill_fields_header", "bill_fields_drag_list")
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .testTag("bill_fields_settings_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "bill_fields_header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsSearchArrivalBanner(
                        route = ROUTE_SETTINGS_BILL_FIELDS,
                        onDismiss = { viewModel.clearSettingsHighlight() }
                    )
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingsHighlightSection(ROUTE_SETTINGS_BILL_FIELDS, "bill_fields_header")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = settingsHighlightedText("账单信息与可选字段排序", ROUTE_SETTINGS_BILL_FIELDS),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = settingsHighlightedText(
                                    "长按条目上下拖动可调整账单详情与编辑页各大项顺序；可选信息支持自定义新增、折叠进「更多账单信息」，并自动同步 AI 提示词与全局搜索。",
                                    ROUTE_SETTINGS_BILL_FIELDS
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showAddDialog = true },
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("bill_fields_add_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("新增可选信息项")
                                }
                                OutlinedButton(
                                    onClick = { showResetConfirm = true },
                                    modifier = Modifier
                                        .weight(0.85f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("bill_fields_reset_button")
                                ) {
                                    Icon(AppIcons.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("恢复默认")
                                }
                            }
                        }
                    }
                }
            }

            item(key = "bill_fields_drag_list") {
                DraggableHierarchyColumn(
                    items = fields,
                    key = { it.id },
                    spacing = 8.dp,
                    dragEnabled = true,
                    enableNesting = false,
                    onReorder = { reordered ->
                        viewModel.saveBillFieldsConfig(
                            fields = reordered,
                            syncAiPrompt = false,
                            silent = true
                        )
                    }
                ) { field, isDragging, _, dragModifier ->
                    val isOptional = !field.isMajorItem && field.id != BillFieldCatalog.ID_RAW_NOTIFICATION
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDragging) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            width = if (isDragging) 1.5.dp else 1.dp,
                            color = if (isDragging) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(dragModifier)
                            .testTag("bill_field_row_${field.id}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.Tune,
                                    contentDescription = "长按拖动排序",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = field.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when {
                                                !field.isBuiltIn -> MaterialTheme.colorScheme.tertiaryContainer
                                                field.isMajorItem -> MaterialTheme.colorScheme.primaryContainer
                                                else -> MaterialTheme.colorScheme.secondaryContainer
                                            }
                                        ) {
                                            Text(
                                                text = when {
                                                    !field.isBuiltIn -> "自定义信息"
                                                    field.isMajorItem -> "核心大项"
                                                    else -> "可选信息"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = when {
                                                    !field.isBuiltIn -> MaterialTheme.colorScheme.onTertiaryContainer
                                                    field.isMajorItem -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = buildString {
                                            append("长按上下拖动排序")
                                            if (!field.isBuiltIn) append(" · 键名 ${field.id}")
                                            if (field.foldedInMore) append(" · 折叠在更多信息")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (!field.isBuiltIn) {
                                    IconButton(
                                        onClick = { editingField = field },
                                        modifier = Modifier.testTag("bill_field_edit_${field.id}")
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "编辑字段名称", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            val next = fields.filterNot { it.id == field.id }
                                            viewModel.saveBillFieldsConfig(next, syncAiPrompt = true)
                                        },
                                        modifier = Modifier.testTag("bill_field_delete_${field.id}")
                                    ) {
                                        Icon(
                                            AppIcons.DeleteOutline,
                                            contentDescription = "删除自定义字段",
                                            tint = ExpenseCoral,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            if (isOptional) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = field.foldedInMore,
                                        onClick = {
                                            val updated = fields.map {
                                                if (it.id == field.id) it.copy(foldedInMore = !it.foldedInMore) else it
                                            }
                                            viewModel.saveBillFieldsConfig(updated, syncAiPrompt = false, silent = true)
                                        },
                                        label = {
                                            Text(if (field.foldedInMore) "已折叠进「更多账单信息」" else "直接展开显示")
                                        },
                                        modifier = Modifier.testTag("bill_field_fold_${field.id}")
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = if (field.enabled) "启用" else "隐藏",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Switch(
                                        checked = field.enabled,
                                        onCheckedChange = { enabled ->
                                            val updated = fields.map {
                                                if (it.id == field.id) it.copy(enabled = enabled) else it
                                            }
                                            viewModel.saveBillFieldsConfig(updated, syncAiPrompt = !field.isBuiltIn, silent = true)
                                        },
                                        modifier = Modifier.testTag("bill_field_enable_${field.id}")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showAddDialog) {
        var labelInput by remember { mutableStateOf("") }
        var foldedInMore by remember { mutableStateOf(true) }
        var syncAi by remember { mutableStateOf(true) }
        var errorText by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新增账单可选信息类别") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it.take(24); errorText = null },
                        label = { Text("信息类别名称") },
                        placeholder = { Text("例如：报销单号、同行人员、项目名称、发票抬头") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bill_field_add_label_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("折叠进「更多账单信息」", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "关闭则直接在账单详情与编辑主区域展示",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = foldedInMore, onCheckedChange = { foldedInMore = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("同步到 AI 理解提示词", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "AI 解析账单时自动尝试提取该字段内容",
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
                        val trimmed = labelInput.trim()
                        if (trimmed.isEmpty()) {
                            errorText = "请输入信息类别名称"
                            return@Button
                        }
                        if (fields.any { it.label.equals(trimmed, ignoreCase = true) }) {
                            errorText = "已存在同名信息类别"
                            return@Button
                        }
                        val slug = "custom_${System.currentTimeMillis().toString(36)}"
                        val newField = BillFieldConfig(
                            id = slug,
                            label = trimmed,
                            isBuiltIn = false,
                            isMajorItem = false,
                            foldedInMore = foldedInMore,
                            enabled = true
                        )
                        val insertIdx = fields.indexOfFirst { it.id == BillFieldCatalog.ID_OCCURRED_AT }
                            .takeIf { it >= 0 } ?: fields.size
                        val next = fields.toMutableList().apply { add(insertIdx, newField) }
                        viewModel.saveBillFieldsConfig(next, syncAiPrompt = syncAi)
                        showAddDialog = false
                    },
                    modifier = Modifier.testTag("bill_field_add_confirm")
                ) {
                    Text("添加")
                }
            }
        )
    }

    editingField?.let { target ->
        var labelInput by remember(target.id) { mutableStateOf(target.label) }
        var syncAi by remember(target.id) { mutableStateOf(true) }
        var errorText by remember(target.id) { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editingField = null },
            title = { Text("编辑自定义信息名称") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it.take(24); errorText = null },
                        label = { Text("新名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("同步更新 AI 提示词", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = syncAi, onCheckedChange = { syncAi = it })
                    }
                    if (errorText != null) {
                        Text(errorText!!, color = ExpenseCoral, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { editingField = null }) { Text("取消") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = labelInput.trim()
                        if (trimmed.isEmpty()) {
                            errorText = "名称不能为空"
                            return@Button
                        }
                        val next = fields.map {
                            if (it.id == target.id) it.copy(label = trimmed) else it
                        }
                        viewModel.saveBillFieldsConfig(next, syncAiPrompt = syncAi)
                        editingField = null
                    }
                ) {
                    Text("保存")
                }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("恢复默认账单信息布局？") },
            text = {
                Text("将把各大项顺序与折叠配置恢复为默认，自定义新增字段也会移除（已有账单中的历史扩展数据不会丢失）。")
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetBillFieldsConfig()
                        showResetConfirm = false
                    },
                    modifier = Modifier.testTag("bill_fields_reset_confirm")
                ) {
                    Text("恢复默认")
                }
            }
        )
    }
}
