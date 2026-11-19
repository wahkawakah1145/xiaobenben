package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import com.example.ui.theme.AppIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.example.model.CategoryCatalog
import com.example.model.CurrencyCatalog
import com.example.model.TransactionType
import com.example.ui.theme.ExpenseCoral

@Composable
fun BatchSelectionActionBar(
    selectedCount: Int,
    allVisibleSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onBatchEdit: () -> Unit,
    onBatchDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .widthIn(max = 680.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("batch_action_bar")
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onClearSelection,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("batch_clear_selection_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "退出多选"
                        )
                    }
                    Text(
                        text = "已选择 $selectedCount 笔账单",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                TextButton(
                    onClick = onToggleSelectAll,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("batch_select_all_button")
                ) {
                    Icon(
                        imageVector = AppIcons.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (allVisibleSelected) "取消全选" else "全选当前")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onBatchDelete,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("batch_delete_button")
                ) {
                    Icon(
                        imageVector = AppIcons.DeleteOutline,
                        contentDescription = null,
                        tint = ExpenseCoral,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("删除所选", color = ExpenseCoral)
                }

                Button(
                    onClick = onBatchEdit,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .minimumInteractiveComponentSize()
                        .testTag("batch_edit_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("批量编辑字段")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchEditTransactionsDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (
        type: TransactionType?,
        category: String?,
        channel: String?,
        merchant: String?,
        currencyCode: String?,
        note: String?
    ) -> Unit
) {
    var selectedType by remember { mutableStateOf<TransactionType?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedChannel by remember { mutableStateOf<String?>(null) }
    var useCustomChannel by remember { mutableStateOf(false) }
    var customChannelInput by remember { mutableStateOf("") }
    var selectedCurrency by remember { mutableStateOf<String?>(null) }
    var editMerchant by remember { mutableStateOf(false) }
    var merchantInput by remember { mutableStateOf("") }
    var editNote by remember { mutableStateOf(false) }
    var noteInput by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val availableCategories = remember(selectedType) {
        when (selectedType) {
            TransactionType.INCOME -> CategoryCatalog.incomeCategories
            TransactionType.TRANSFER -> listOf("转账红包", "其他")
            TransactionType.EXPENSE -> CategoryCatalog.expenseCategories
            null -> (CategoryCatalog.expenseCategories + CategoryCatalog.incomeCategories).distinct()
        }
    }
    val quickChannels = remember {
        listOf("支付宝", "微信支付", "云闪付", "招商银行", "工商银行", "建设银行", "手动记账")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("批量编辑 $selectedCount 笔账单")
                Text(
                    text = "保持「不修改」的字段将保留原记录不变",
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("收支类型", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedType == null,
                        onClick = {
                            selectedType = null
                            validationError = null
                        },
                        label = { Text("不修改") }
                    )
                    TransactionType.entries.forEach { t ->
                        FilterChip(
                            selected = selectedType == t,
                            onClick = {
                                selectedType = t
                                if (selectedCategory != null) {
                                    val allowed = when (t) {
                                        TransactionType.INCOME -> CategoryCatalog.incomeCategories
                                        TransactionType.TRANSFER -> listOf("转账红包", "其他")
                                        TransactionType.EXPENSE -> CategoryCatalog.expenseCategories
                                    }
                                    if (selectedCategory !in allowed) {
                                        selectedCategory = allowed.firstOrNull()
                                    }
                                }
                                validationError = null
                            },
                            label = { Text(t.label) },
                            modifier = Modifier.testTag("batch_type_${t.name}")
                        )
                    }
                }

                Text("分类", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = {
                            selectedCategory = null
                            validationError = null
                        },
                        label = { Text("不修改") }
                    )
                    availableCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = cat
                                validationError = null
                            },
                            label = { Text(cat) },
                            modifier = Modifier.testTag("batch_cat_$cat")
                        )
                    }
                }

                Text("支付渠道 / 银行", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = !useCustomChannel && selectedChannel == null,
                        onClick = {
                            useCustomChannel = false
                            selectedChannel = null
                            validationError = null
                        },
                        label = { Text("不修改") }
                    )
                    quickChannels.forEach { item ->
                        FilterChip(
                            selected = !useCustomChannel && selectedChannel == item,
                            onClick = {
                                useCustomChannel = false
                                selectedChannel = item
                                validationError = null
                            },
                            label = { Text(item) }
                        )
                    }
                    FilterChip(
                        selected = useCustomChannel,
                        onClick = {
                            useCustomChannel = true
                            validationError = null
                        },
                        label = { Text("自定义") }
                    )
                }

                if (useCustomChannel) {
                    OutlinedTextField(
                        value = customChannelInput,
                        onValueChange = {
                            customChannelInput = it.take(32)
                            validationError = null
                        },
                        label = { Text("输入目标渠道名") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("batch_custom_channel_input")
                    )
                }

                Text("币种（变更后会同步刷新该笔账单的汇率折算快照）", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCurrency == null,
                        onClick = {
                            selectedCurrency = null
                            validationError = null
                        },
                        label = { Text("不修改") }
                    )
                    CurrencyCatalog.currencies.forEach { c ->
                        FilterChip(
                            selected = selectedCurrency == c.code,
                            onClick = {
                                selectedCurrency = c.code
                                validationError = null
                            },
                            label = { Text("${c.code} ${c.chineseName}") }
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = editMerchant,
                        onCheckedChange = {
                            editMerchant = it
                            validationError = null
                        },
                        modifier = Modifier.testTag("batch_toggle_merchant")
                    )
                    Text(
                        text = "统一修改商户 / 交易对象",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (editMerchant) {
                    OutlinedTextField(
                        value = merchantInput,
                        onValueChange = {
                            merchantInput = it.take(60)
                            validationError = null
                        },
                        label = { Text("新的商户名称") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("batch_merchant_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = editNote,
                        onCheckedChange = {
                            editNote = it
                            validationError = null
                        },
                        modifier = Modifier.testTag("batch_toggle_note")
                    )
                    Text(
                        text = "统一修改备注（留空将清空备注）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (editNote) {
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = {
                            noteInput = it.take(100)
                            validationError = null
                        },
                        label = { Text("新的备注内容") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("batch_note_input")
                    )
                }

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = ExpenseCoral,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    val resolvedChannel = if (useCustomChannel) {
                        val trimmed = customChannelInput.trim()
                        if (trimmed.isBlank()) {
                            validationError = "请输入自定义渠道名称"
                            return@FilledTonalButton
                        }
                        trimmed
                    } else {
                        selectedChannel
                    }
                    val resolvedMerchant = if (editMerchant) {
                        val trimmed = merchantInput.trim()
                        if (trimmed.isBlank()) {
                            validationError = "请输入新的商户名称"
                            return@FilledTonalButton
                        }
                        trimmed
                    } else {
                        null
                    }
                    val resolvedNote = if (editNote) noteInput.trim() else null

                    if (selectedType == null &&
                        selectedCategory == null &&
                        resolvedChannel == null &&
                        resolvedMerchant == null &&
                        selectedCurrency == null &&
                        !editNote
                    ) {
                        validationError = "请至少选择一项需要批量修改的字段"
                        return@FilledTonalButton
                    }

                    onConfirm(
                        selectedType,
                        selectedCategory,
                        resolvedChannel,
                        resolvedMerchant,
                        selectedCurrency,
                        resolvedNote
                    )
                },
                modifier = Modifier.testTag("batch_edit_confirm_button")
            ) {
                Text("应用修改")
            }
        }
    )
}
