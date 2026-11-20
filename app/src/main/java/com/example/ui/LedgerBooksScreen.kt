package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CurrencyExchangeManager
import com.example.model.BudgetSettings
import com.example.model.ExchangeRateState
import com.example.model.LedgerBook
import com.example.model.LedgerTransaction
import com.example.model.TransactionBookLink
import com.example.model.TransactionType
import com.example.ui.components.ChannelBadge
import com.example.ui.components.TransactionDetailSheet
import com.example.ui.components.TransactionEditorDialog
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatDateHeader
import com.example.ui.components.formatItemTimeCompact
import com.example.ui.components.transactionTypeColor
import com.example.ui.theme.AppIcons
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.ui.theme.LocalNumberFontFamily
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.UiState

private val bookColorPresets = listOf(
    0xFF0F6B54L,
    0xFF1677FFL,
    0xFFE07A2EL,
    0xFF7B4EC9L,
    0xFFC6382EL,
    0xFF008B8BL,
    0xFFB87300L,
    0xFF334155L
)

@Composable
fun LedgerBooksScreen(
    books: List<LedgerBook>,
    bookLinks: List<TransactionBookLink>,
    transactionsState: UiState<List<LedgerTransaction>>,
    settings: BudgetSettings,
    exchangeRates: ExchangeRateState,
    viewModel: LedgerViewModel,
    onAnalyzeBook: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val allTransactions = (transactionsState as? UiState.Success)?.data ?: emptyList()
    val txById = remember(allTransactions) { allTransactions.associateBy { it.id } }
    val baseCurrency = settings.baseCurrencyCode
    val numberFont = LocalNumberFontFamily.current

    val txIdsByBook = remember(bookLinks) {
        bookLinks.groupBy({ it.bookId }, { it.transactionId }).mapValues { it.value.toSet() }
    }

    var showCreateBookDialog by remember { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<LedgerBook?>(null) }
    var deletingBook by remember { mutableStateOf<LedgerBook?>(null) }
    var expandedBookId by remember { mutableStateOf<String?>(null) }
    var quickSummaryBook by remember { mutableStateOf<Pair<String, String>?>(null) }
    var inspectingTx by remember { mutableStateOf<LedgerTransaction?>(null) }
    var editingTx by remember { mutableStateOf<LedgerTransaction?>(null) }

    fun toBase(tx: LedgerTransaction): Long = CurrencyExchangeManager.convertCents(
        tx.amountCents,
        tx.currencyCode,
        baseCurrency,
        exchangeRates.ratesToCny
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "books_overview_header") {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "我的账本 (${books.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "按项目、旅行、家庭或报销拆分账单；同一笔账单可归属多个账本并单独分析",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { showCreateBookDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("create_book_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("新建账本")
                        }
                    }
                }
            }

            if (books.isEmpty()) {
                item(key = "books_empty_state") {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("books_empty_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                                modifier = Modifier.size(44.dp)
                            )
                            Text("还没有创建账本", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "新建账本后，可在主页长按选中账单点击底部「加入账本」（支持多选），再对各账本进行收支图表与文本分析。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { showCreateBookDialog = true },
                                modifier = Modifier.testTag("books_empty_create_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("创建第一个账本")
                            }
                        }
                    }
                }
            } else {
                items(books, key = { it.id }) { book ->
                    val bookTxList = remember(txIdsByBook, book.id, txById) {
                        txIdsByBook[book.id].orEmpty()
                            .mapNotNull { txById[it] }
                            .sortedByDescending { it.occurredAtMillis }
                    }
                    val confirmedBookTxs = remember(bookTxList) { bookTxList.filter { it.confirmed } }
                    val expenseCents = remember(confirmedBookTxs, baseCurrency, exchangeRates.ratesToCny) {
                        confirmedBookTxs.filter { it.txType == TransactionType.EXPENSE }.sumOf { toBase(it) }
                    }
                    val incomeCents = remember(confirmedBookTxs, baseCurrency, exchangeRates.ratesToCny) {
                        confirmedBookTxs.filter { it.txType == TransactionType.INCOME }.sumOf { toBase(it) }
                    }
                    val netCents = incomeCents - expenseCents
                    val accent = Color(book.colorArgb.toInt())
                    val isExpanded = expandedBookId == book.id

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("book_card_${book.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(accent.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.MenuBook,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = book.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            append("${bookTxList.size} 笔账单")
                                            if (book.description.isNotBlank()) {
                                                append(" · ${book.description}")
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { editingBook = book },
                                    modifier = Modifier.testTag("edit_book_${book.id}")
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "编辑账本", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { deletingBook = book },
                                    modifier = Modifier.testTag("delete_book_${book.id}")
                                ) {
                                    Icon(
                                        AppIcons.DeleteOutline,
                                        contentDescription = "删除账本",
                                        tint = ExpenseCoral,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("支出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = formatCurrency(expenseCents, baseCurrency),
                                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = numberFont),
                                            color = ExpenseCoral,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("收入", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = formatCurrency(incomeCents, baseCurrency),
                                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = numberFont),
                                            color = IncomeJade,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("结余", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = (if (netCents < 0L) "-" else "") + formatCurrency(kotlin.math.abs(netCents), baseCurrency),
                                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = numberFont),
                                            color = if (netCents >= 0L) IncomeJade else ExpenseCoral,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        expandedBookId = if (isExpanded) null else book.id
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("toggle_book_items_${book.id}")
                                ) {
                                    Text(if (isExpanded) "收起明细" else "查看明细", maxLines = 1)
                                }
                                OutlinedButton(
                                    enabled = bookTxList.isNotEmpty(),
                                    onClick = {
                                        val summary = viewModel.buildQuickSelectionSummary(
                                            transactions = bookTxList,
                                            scopeTitle = "账本「${book.name}」统计摘要"
                                        )
                                        quickSummaryBook = "账本「${book.name}」" to summary
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("quick_summary_book_${book.id}")
                                ) {
                                    Text("文本统计", maxLines = 1)
                                }
                                FilledTonalButton(
                                    onClick = { onAnalyzeBook(book.id) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .testTag("analyze_book_${book.id}")
                                ) {
                                    Icon(AppIcons.Insights, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("图表分析", maxLines = 1)
                                }
                            }

                            if (isExpanded) {
                                HorizontalDivider()
                                if (bookTxList.isEmpty()) {
                                    Text(
                                        text = "该账本暂无关联账单。前往主页长按选中账单后，点击底部「加入账本」即可归入。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        bookTxList.forEach { tx ->
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { inspectingTx = tx }
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = tx.merchant,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            ChannelBadge(channel = tx.channel)
                                                            Text(
                                                                text = "${tx.category} · ${formatDateHeader(tx.occurredAtMillis)} ${formatItemTimeCompact(tx.occurredAtMillis)}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = formatCurrency(tx.amountCents, tx.currencyCode, showSign = true, type = tx.txType),
                                                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = numberFont),
                                                        color = transactionTypeColor(tx.txType),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    IconButton(
                                                        onClick = { viewModel.removeTransactionsFromBook(listOf(tx.id), book.id) },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Close,
                                                            contentDescription = "从账本移出",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(16.dp)
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

    if (showCreateBookDialog) {
        BookEditorDialog(
            initialBook = null,
            onDismiss = { showCreateBookDialog = false },
            onSave = { name, desc, colorArgb ->
                viewModel.saveBook(name = name, description = desc, colorArgb = colorArgb)
                showCreateBookDialog = false
            }
        )
    }

    editingBook?.let { target ->
        BookEditorDialog(
            initialBook = target,
            onDismiss = { editingBook = null },
            onSave = { name, desc, colorArgb ->
                viewModel.saveBook(
                    name = name,
                    description = desc,
                    colorArgb = colorArgb,
                    existingBookId = target.id
                )
                editingBook = null
            }
        )
    }

    deletingBook?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingBook = null },
            title = { Text("删除账本「${target.name}」") },
            text = { Text("仅删除此账本分组与关联关系，原本的账单记录不会被删除。") },
            dismissButton = {
                TextButton(onClick = { deletingBook = null }) { Text("取消") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBook(target.id)
                        deletingBook = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseCoral)
                ) {
                    Text("确认删除")
                }
            }
        )
    }

    quickSummaryBook?.let { (title, body) ->
        QuickAnalysisTextDialog(
            title = title,
            summaryText = body,
            onDismiss = { quickSummaryBook = null }
        )
    }

    inspectingTx?.let { tx ->
        val (recordedBase, recordedRate, recordedCents) = viewModel.recordedSnapshotDisplay(tx)
        TransactionDetailSheet(
            transaction = tx,
            baseCurrencyCode = baseCurrency,
            liveRateToBase = viewModel.liveRateToBase(tx),
            liveConvertedBaseCents = viewModel.convertToBaseCents(tx),
            recordedBaseCurrencyCode = recordedBase,
            recordedRateToBase = recordedRate,
            recordedConvertedBaseCents = recordedCents,
            onDismiss = { inspectingTx = null },
            onEdit = {
                editingTx = tx
                inspectingTx = null
            },
            onDelete = {
                viewModel.deleteTransaction(tx.id)
                inspectingTx = null
            }
        )
    }

    editingTx?.let { tx ->
        TransactionEditorDialog(
            initialTransaction = tx,
            defaultCurrencyCode = baseCurrency,
            onDismiss = { editingTx = null },
            onSave = { amountCents, currencyCode, type, category, merchant, channel, tail, note, occurredAtMillis ->
                viewModel.updateTransaction(
                    txId = tx.id,
                    amountCents = amountCents,
                    currencyCode = currencyCode,
                    type = type,
                    category = category,
                    merchant = merchant,
                    channel = channel,
                    cardLastFour = tail,
                    note = note,
                    occurredAtMillis = occurredAtMillis,
                    confirmed = true
                )
                editingTx = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookEditorDialog(
    initialBook: LedgerBook?,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, colorArgb: Long) -> Unit
) {
    var name by remember(initialBook?.id) { mutableStateOf(initialBook?.name ?: "") }
    var description by remember(initialBook?.id) { mutableStateOf(initialBook?.description ?: "") }
    var colorArgb by remember(initialBook?.id) { mutableLongStateOf(initialBook?.colorArgb ?: bookColorPresets.first()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialBook == null) "新建账本" else "编辑账本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40); errorText = null },
                    label = { Text("账本名称") },
                    placeholder = { Text("例如：日本旅行、家庭日常、公司报销") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("book_editor_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(120) },
                    label = { Text("备注说明（可选）") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("book_editor_desc_input")
                )
                Text("标识颜色", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    bookColorPresets.forEach { preset ->
                        val selected = preset == colorArgb
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(preset.toInt()))
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { colorArgb = preset }
                        )
                    }
                }
                if (errorText != null) {
                    Text(errorText!!, color = ExpenseCoral, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isEmpty()) {
                        errorText = "请输入账本名称"
                        return@Button
                    }
                    onSave(name.trim(), description.trim(), colorArgb)
                },
                modifier = Modifier.testTag("book_editor_save_button")
            ) {
                Text("保存")
            }
        }
    )
}

@Composable
fun AddToBooksMultiSelectDialog(
    selectedTxCount: Int,
    books: List<LedgerBook>,
    preselectedBookIds: Set<String>,
    onCreateBookQuick: (name: String, onCreated: (LedgerBook) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (selectedBookIds: Set<String>) -> Unit
) {
    var checkedBookIds by remember(preselectedBookIds) { mutableStateOf(preselectedBookIds) }
    var showQuickCreate by remember { mutableStateOf(false) }
    var newBookName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("加入账本（已选 $selectedTxCount 笔）")
                Text(
                    text = "可多选目标账本，支持单笔账单归属多个账本",
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (books.isEmpty()) {
                    Text(
                        text = "当前还没有账本，请在下方快速新建一个账本。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    books.forEach { book ->
                        val checked = book.id in checkedBookIds
                        val accent = Color(book.colorArgb.toInt())
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = if (checked) 1.5.dp else 1.dp,
                                color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    checkedBookIds = if (checked) checkedBookIds - book.id else checkedBookIds + book.id
                                }
                                .testTag("add_to_book_option_${book.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        checkedBookIds = if (checked) checkedBookIds - book.id else checkedBookIds + book.id
                                    }
                                )
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(book.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    if (book.description.isNotBlank()) {
                                        Text(
                                            text = book.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                if (!showQuickCreate && books.isNotEmpty()) {
                    TextButton(
                        onClick = { showQuickCreate = true },
                        modifier = Modifier.testTag("add_to_book_quick_create_toggle")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新建账本")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newBookName,
                            onValueChange = { newBookName = it.take(40) },
                            label = { Text("新账本名称") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("add_to_book_quick_create_input")
                        )
                        FilledTonalButton(
                            enabled = newBookName.trim().isNotEmpty(),
                            onClick = {
                                val target = newBookName.trim()
                                newBookName = ""
                                onCreateBookQuick(target) { created ->
                                    checkedBookIds = checkedBookIds + created.id
                                    showQuickCreate = false
                                }
                            },
                            modifier = Modifier.testTag("add_to_book_quick_create_confirm")
                        ) {
                            Text("添加")
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        confirmButton = {
            Button(
                enabled = checkedBookIds.isNotEmpty(),
                onClick = { onConfirm(checkedBookIds) },
                modifier = Modifier.testTag("add_to_book_confirm_button")
            ) {
                Text("加入 (${checkedBookIds.size})")
            }
        }
    )
}

@Composable
fun QuickAnalysisTextDialog(
    title: String,
    summaryText: String,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(summaryText) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(AppIcons.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_analysis_dialog_text")
            ) {
                SelectionContainer {
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(summaryText))
                    copied = true
                },
                modifier = Modifier.testTag("quick_analysis_copy_button")
            ) {
                Icon(
                    imageVector = if (copied) Icons.Rounded.Check else AppIcons.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (copied) "已复制" else "复制文本")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
