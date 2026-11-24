package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CurrencyExchangeManager
import com.example.model.BudgetSettings
import com.example.model.CategoryCatalog
import com.example.model.ChannelCatalog
import com.example.model.CurrencyCatalog
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.ui.theme.TransferCobalt
import com.example.viewmodel.LedgerViewModel

/**
 * Reusable quick-viewer modal listing all transactions associated with a category or channel
 * (with optional toggle to include attributed children), supporting search and tapping any
 * row to open full transaction details/editing.
 */
@Composable
fun LinkedTransactionsDialog(
    title: String,
    exactTransactions: List<LedgerTransaction>,
    withChildrenTransactions: List<LedgerTransaction>,
    hasChildren: Boolean,
    settings: BudgetSettings,
    viewModel: LedgerViewModel,
    onDismiss: () -> Unit
) {
    val exchangeState by viewModel.exchangeRateState.collectAsStateWithLifecycle()
    val books by viewModel.booksState.collectAsStateWithLifecycle()
    val bookLinks by viewModel.bookLinksState.collectAsStateWithLifecycle()

    var includeChildren by remember(title, hasChildren) { mutableStateOf(hasChildren) }
    var searchQuery by remember(title) { mutableStateOf("") }
    var inspectingTx by remember { mutableStateOf<LedgerTransaction?>(null) }
    var editingTx by remember { mutableStateOf<LedgerTransaction?>(null) }

    val baseList = remember(includeChildren, exactTransactions, withChildrenTransactions) {
        (if (includeChildren && hasChildren) withChildrenTransactions else exactTransactions)
            .sortedByDescending { it.occurredAtMillis }
    }
    val filtered = remember(baseList, searchQuery, settings.categoryParentMap, settings.channelParentMap) {
        val q = searchQuery.trim()
        if (q.isEmpty()) baseList else baseList.filter {
            it.matchesSearchQuery(q, settings.categoryParentMap, settings.channelParentMap)
        }
    }

    val baseCode = CurrencyCatalog.resolve(settings.baseCurrencyCode).code
    val summary = remember(filtered, baseCode, exchangeState.ratesToCny) {
        var expenseBase = 0L
        var incomeBase = 0L
        filtered.forEach { tx ->
            val converted = CurrencyExchangeManager.convertCents(
                amountCents = tx.amountCents,
                fromCurrency = tx.currencyCode,
                toCurrency = baseCode,
                ratesToCny = exchangeState.ratesToCny
            )
            when (tx.txType) {
                TransactionType.EXPENSE -> expenseBase += converted
                TransactionType.INCOME -> incomeBase += converted
                TransactionType.TRANSFER -> Unit
            }
        }
        expenseBase to incomeBase
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append("共 ${filtered.size} 笔")
                        if (summary.first > 0L) append(" · 支出 ${formatCurrency(summary.first, baseCode)}")
                        if (summary.second > 0L) append(" · 收入 ${formatCurrency(summary.second, baseCode)}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("linked_transactions_dialog"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasChildren) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = includeChildren,
                            onClick = { includeChildren = true },
                            label = { Text("含子类 (${withChildrenTransactions.size})") },
                            modifier = Modifier.testTag("linked_tx_include_children")
                        )
                        FilterChip(
                            selected = !includeChildren,
                            onClick = { includeChildren = false },
                            label = { Text("仅本项 (${exactTransactions.size})") },
                            modifier = Modifier.testTag("linked_tx_exact_only")
                        )
                    }
                }

                if (baseList.size > 4 || searchQuery.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索关联账单") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "清除搜索", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无关联账单条目",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { it.id }) { tx ->
                            val txType = tx.txType
                            val amountColor = when (txType) {
                                TransactionType.EXPENSE -> ExpenseCoral
                                TransactionType.INCOME -> IncomeJade
                                TransactionType.TRANSFER -> TransferCobalt
                            }
                            val prefix = when (txType) {
                                TransactionType.EXPENSE -> "-"
                                TransactionType.INCOME -> "+"
                                TransactionType.TRANSFER -> ""
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { inspectingTx = tx }
                                    .testTag("linked_tx_row_${tx.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = tx.merchant.ifBlank { tx.category },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${tx.category} · ${tx.channel} · ${formatDateTimeFull(tx.occurredAtMillis)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$prefix${formatCurrency(tx.amountCents, tx.currencyCode)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = amountColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )

    inspectingTx?.let { tx ->
        val (recBase, recRate, recConverted) = viewModel.recordedSnapshotDisplay(tx)
        val assignedBookIds = remember(bookLinks, tx.id) {
            bookLinks.asSequence()
                .filter { it.transactionId == tx.id }
                .map { it.bookId }
                .toSet()
        }
        TransactionDetailSheet(
            transaction = tx,
            baseCurrencyCode = baseCode,
            liveRateToBase = viewModel.liveRateToBase(tx),
            liveConvertedBaseCents = viewModel.convertToBaseCents(tx),
            recordedBaseCurrencyCode = recBase,
            recordedRateToBase = recRate,
            recordedConvertedBaseCents = recConverted,
            books = books,
            assignedBookIds = assignedBookIds,
            onUpdateTransactionBooks = { ids -> viewModel.setBooksForTransaction(tx.id, ids) },
            onCreateBookQuick = { name, onCreated -> viewModel.saveBook(name = name, onSaved = onCreated) },
            onDismiss = { inspectingTx = null },
            onEdit = {
                inspectingTx = null
                editingTx = tx
            },
            onDelete = {
                viewModel.deleteTransaction(tx.id)
                inspectingTx = null
            }
        )
    }

    editingTx?.let { tx ->
        var extTradeType: String? = tx.tradeType
        var extProductName: String? = tx.productName
        var extTradeStatus: String? = tx.tradeStatus
        var extTxNo: String? = tx.transactionNo
        var extMerchantOrderNo: String? = tx.merchantOrderNo
        var extCustomFields: Map<String, String> = tx.customFields
        TransactionEditorDialog(
            initialTransaction = tx,
            defaultCurrencyCode = baseCode,
            enabledCurrencyCodes = settings.enabledCurrencyCodes,
            expenseCategories = CategoryCatalog.activeExpenseCategories(settings),
            incomeCategories = CategoryCatalog.activeIncomeCategories(settings),
            transferCategories = CategoryCatalog.activeTransferCategories(settings),
            channelOptions = ChannelCatalog.activeChannels(settings),
            onDismiss = { editingTx = null },
            onSaveExtended = { tt, pn, ts, tno, mno ->
                extTradeType = tt
                extProductName = pn
                extTradeStatus = ts
                extTxNo = tno
                extMerchantOrderNo = mno
            },
            onSaveCustomFields = { extCustomFields = it },
            onSave = { amountCents, currencyCode, type, category, merchant, channel, cardLastFour, note, occurredAtMillis ->
                viewModel.updateTransaction(
                    txId = tx.id,
                    amountCents = amountCents,
                    currencyCode = currencyCode,
                    type = type,
                    category = category,
                    merchant = merchant,
                    channel = channel,
                    cardLastFour = cardLastFour,
                    note = note,
                    occurredAtMillis = occurredAtMillis,
                    confirmed = true,
                    tradeType = extTradeType,
                    productName = extProductName,
                    tradeStatus = extTradeStatus,
                    transactionNo = extTxNo,
                    merchantOrderNo = extMerchantOrderNo,
                    updateExtendedFields = true,
                    customFields = extCustomFields
                )
                editingTx = null
            }
        )
    }
}
