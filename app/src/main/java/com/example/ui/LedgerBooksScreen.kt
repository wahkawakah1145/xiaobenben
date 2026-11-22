package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.CurrencyExchangeManager
import com.example.model.BudgetSettings
import com.example.model.CategoryCatalog
import com.example.model.ExchangeRateState
import com.example.model.LedgerBook
import com.example.model.LedgerTransaction
import com.example.model.TransactionBookLink
import com.example.model.TransactionType
import com.example.parser.QuickAnalysisBreakdownItem
import com.example.parser.QuickAnalysisReport
import com.example.ui.components.CategoryIconAvatar
import com.example.ui.components.ChannelBadge
import com.example.ui.components.TransactionDetailSheet
import com.example.ui.components.TransactionEditorDialog
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatDateHeader
import com.example.ui.components.formatItemTimeCompact
import com.example.ui.components.transactionTypeColor
import com.example.model.IconVisualStyle
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
    openBookId: String? = null,
    onOpenBook: (String) -> Unit = {},
    onCloseBook: () -> Unit = {},
    canSwipeToAnalytics: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    detailListState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val allTransactions = (transactionsState as? UiState.Success)?.data ?: emptyList()
    val txById = remember(allTransactions) { allTransactions.associateBy { it.id } }
    val baseCurrency = settings.baseCurrencyCode
    val numberFont = LocalNumberFontFamily.current

    val txIdsByBook = remember(bookLinks) {
        bookLinks.groupBy({ it.bookId }, { it.transactionId }).mapValues { it.value.toSet() }
    }
    val bookIdsByTx = remember(bookLinks) {
        bookLinks.groupBy({ it.transactionId }, { it.bookId }).mapValues { it.value.toSet() }
    }

    var showCreateBookDialog by remember { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<LedgerBook?>(null) }
    var deletingBook by remember { mutableStateOf<LedgerBook?>(null) }
    var quickSummaryBook by remember { mutableStateOf<Pair<String, QuickAnalysisReport>?>(null) }
    var inspectingTx by remember { mutableStateOf<LedgerTransaction?>(null) }
    var editingTx by remember { mutableStateOf<LedgerTransaction?>(null) }
    val activeOpenBook = remember(books, openBookId) {
        openBookId?.let { id -> books.firstOrNull { it.id == id } }
    }

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
        AnimatedContent(
            targetState = activeOpenBook?.id,
            transitionSpec = {
                if (targetState != null && initialState == null) {
                    (slideInHorizontally(tween(220)) { it / 4 } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(tween(180)) { -it / 6 } + fadeOut(tween(140)))
                } else if (targetState == null && initialState != null) {
                    (slideInHorizontally(tween(220)) { -it / 4 } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(tween(180)) { it / 4 } + fadeOut(tween(140)))
                } else {
                    fadeIn(tween(160)) togetherWith fadeOut(tween(140))
                }
            },
            label = "books_detail_switch",
            modifier = Modifier.fillMaxSize()
        ) { currentBookId ->
            val openBook = currentBookId?.let { id -> books.firstOrNull { it.id == id } }
            if (openBook != null) {
                val bookTxList = remember(txIdsByBook, openBook.id, txById) {
                    txIdsByBook[openBook.id].orEmpty()
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
                val accent = Color(openBook.colorArgb.toInt())

                LazyColumn(
                    state = detailListState,
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize()
                        .testTag("book_detail_list_${openBook.id}"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "book_detail_hero_${openBook.id}") {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.34f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(13.dp))
                                            .background(accent.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.MenuBook,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = openBook.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = buildString {
                                                append("共 ${bookTxList.size} 笔账单")
                                                if (openBook.description.isNotBlank()) {
                                                    append(" · ${openBook.description}")
                                                }
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { editingBook = openBook },
                                        modifier = Modifier.testTag("detail_edit_book_${openBook.id}")
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "编辑账本", modifier = Modifier.size(18.dp))
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
                                        enabled = bookTxList.isNotEmpty(),
                                        onClick = {
                                            val report = viewModel.buildQuickSelectionReport(
                                                transactions = bookTxList,
                                                scopeTitle = "账本「${openBook.name}」统计摘要"
                                            )
                                            quickSummaryBook = "账本「${openBook.name}」" to report
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("detail_quick_summary_book_${openBook.id}")
                                    ) {
                                        Text("文本统计", maxLines = 1)
                                    }
                                    FilledTonalButton(
                                        onClick = { onAnalyzeBook(openBook.id) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1.15f)
                                            .testTag("detail_analyze_book_${openBook.id}")
                                    ) {
                                        Icon(AppIcons.Insights, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("收支分析", maxLines = 1)
                                    }
                                }

                                if (canSwipeToAnalytics) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = accent.copy(alpha = 0.08f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "提示：直接横滑可切换到本账本的「收支分析」，再横滑回来仍停留在当前明细页；按返回键回到账本主页",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (bookTxList.isEmpty()) {
                        item(key = "book_detail_empty") {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.ReceiptLong,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text("该账本暂无明细", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "可在主页长按选中账单点击「加入账本」，或点击任意账单详情中的「添加到账本」归入此账本。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(bookTxList, key = { it.id }) { tx ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { inspectingTx = tx }
                                    .testTag("book_detail_tx_${tx.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CategoryIconAvatar(
                                        category = tx.category,
                                        tintColor = transactionTypeColor(tx.txType),
                                        visualStyle = IconVisualStyle.FILLED,
                                        size = 38.dp
                                    )
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = tx.merchant,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
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
                                        onClick = { viewModel.removeTransactionsFromBook(listOf(tx.id), openBook.id) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("remove_tx_from_book_${tx.id}")
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
                    item(key = "book_detail_bottom_spacer") {
                        Spacer(modifier = Modifier.height(64.dp))
                    }
                }
            } else {
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
                                        text = "点击任意账本进入明细列表；在明细页可直接横滑进入该账本的收支分析",
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
                                        text = "新建账本后，可在主页长按选中账单点击底部「加入账本」，或在账单详情页直接添加到账本。",
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

                            Card(
                                onClick = { onOpenBook(book.id) },
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
                                            enabled = bookTxList.isNotEmpty(),
                                            onClick = {
                                                val report = viewModel.buildQuickSelectionReport(
                                                    transactions = bookTxList,
                                                    scopeTitle = "账本「${book.name}」统计摘要"
                                                )
                                                quickSummaryBook = "账本「${book.name}」" to report
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
                                }
                            }
                        }
                    }
                    item(key = "books_list_bottom_spacer") {
                        Spacer(modifier = Modifier.height(64.dp))
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

    quickSummaryBook?.let { (title, report) ->
        QuickAnalysisTextDialog(
            title = title,
            summaryText = report.plainText,
            report = report,
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
            onMarkNonBill = if (!tx.confirmed || !tx.rawNotification.isNullOrBlank()) {
                {
                    viewModel.markTransactionAsNonBill(tx)
                    inspectingTx = null
                }
            } else null,
            books = books,
            assignedBookIds = bookIdsByTx[tx.id].orEmpty(),
            onUpdateTransactionBooks = { chosen ->
                viewModel.setBooksForTransaction(tx.id, chosen)
            },
            onCreateBookQuick = { name, onCreated ->
                viewModel.saveBook(name = name, onSaved = onCreated)
            },
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
            enabledCurrencyCodes = settings.enabledCurrencyCodes,
            expenseCategories = CategoryCatalog.activeExpenseCategories(settings),
            incomeCategories = CategoryCatalog.activeIncomeCategories(settings),
            transferCategories = CategoryCatalog.activeTransferCategories(settings),
            allTransactions = allTransactions,
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
            },
            onBatchApplySimilar = { targetIds, type, category, channel, merchant, currencyCode, note ->
                viewModel.batchUpdateTransactions(
                    txIds = targetIds,
                    type = type,
                    category = category,
                    channel = channel,
                    merchant = merchant,
                    currencyCode = currencyCode,
                    note = note
                )
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
    onConfirm: (selectedBookIds: Set<String>) -> Unit,
    allowEmptySelection: Boolean = false,
    titleText: String? = null,
    confirmButtonLabel: ((Int) -> String)? = null
) {
    var checkedBookIds by remember(preselectedBookIds) { mutableStateOf(preselectedBookIds) }
    var showQuickCreate by remember { mutableStateOf(false) }
    var newBookName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titleText ?: "加入账本（已选 $selectedTxCount 笔）")
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allIds = books.mapTo(LinkedHashSet()) { it.id }
                        val allSelected = checkedBookIds.containsAll(allIds)
                        TextButton(
                            onClick = {
                                checkedBookIds = if (allSelected) emptySet() else allIds
                            },
                            modifier = Modifier.testTag("add_to_book_select_all_button")
                        ) {
                            Text(if (allSelected) "取消全选" else "全选")
                        }
                        TextButton(
                            onClick = {
                                checkedBookIds = (allIds - checkedBookIds).toSet()
                            },
                            modifier = Modifier.testTag("add_to_book_invert_button")
                        ) {
                            Text("反选")
                        }
                    }
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
                enabled = allowEmptySelection || checkedBookIds.isNotEmpty(),
                onClick = { onConfirm(checkedBookIds) },
                modifier = Modifier.testTag("add_to_book_confirm_button")
            ) {
                Text(confirmButtonLabel?.invoke(checkedBookIds.size) ?: "加入 (${checkedBookIds.size})")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAnalysisTextDialog(
    title: String,
    summaryText: String,
    onDismiss: () -> Unit,
    report: QuickAnalysisReport? = null
) {
    val clipboard = LocalClipboardManager.current
    val copyPlainText = report?.plainText ?: summaryText
    var copied by remember(copyPlainText) { mutableStateOf(false) }
    var showPlainTextPreview by remember(copyPlainText) { mutableStateOf(report == null) }
    val numberFont = LocalNumberFontFamily.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 520.dp)
                .testTag("quick_analysis_dialog_root")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header banner with subtle theme-aligned gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f)
                                )
                            )
                        )
                        .padding(start = 18.dp, end = 10.dp, top = 16.dp, bottom = 14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Insights,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (report != null) {
                                    Text(
                                        text = report.scopeTitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "关闭",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        if (report != null) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                QuickAnalysisMetaPill(text = report.dateRange)
                                QuickAnalysisMetaPill(text = "共 ${report.totalCount} 笔")
                                if (report.expenseCount > 0) {
                                    QuickAnalysisMetaPill(text = "支出 ${report.expenseCount}")
                                }
                                if (report.incomeCount > 0) {
                                    QuickAnalysisMetaPill(text = "收入 ${report.incomeCount}")
                                }
                                if (report.transferCount > 0) {
                                    QuickAnalysisMetaPill(text = "转账 ${report.transferCount}")
                                }
                                if (report.foreignCount > 0) {
                                    QuickAnalysisMetaPill(text = "含 ${report.foreignCount} 笔外币")
                                }
                            }
                        }
                    }
                }

                // Scrollable structured analysis body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (report != null) {
                        // 3-column KPI cards
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickAnalysisKpiCard(
                                label = "支出合计",
                                value = formatCurrency(report.totalExpenseCents, report.baseCurrencyCode),
                                valueColor = ExpenseCoral,
                                modifier = Modifier.weight(1f)
                            )
                            QuickAnalysisKpiCard(
                                label = "收入合计",
                                value = formatCurrency(report.totalIncomeCents, report.baseCurrencyCode),
                                valueColor = IncomeJade,
                                modifier = Modifier.weight(1f)
                            )
                            QuickAnalysisKpiCard(
                                label = "净收支",
                                value = (if (report.netCents >= 0L) "+" else "-") +
                                    formatCurrency(kotlin.math.abs(report.netCents), report.baseCurrencyCode),
                                valueColor = if (report.netCents >= 0L) IncomeJade else ExpenseCoral,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Secondary metric strip
                        if (report.expenseCount > 0 || report.transferCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FlowRow(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (report.expenseCount > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "笔均支出",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = formatCurrency(report.avgExpenseCents, report.baseCurrencyCode),
                                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = numberFont),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    if (report.transferCount > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "转账/不计收支",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = formatCurrency(report.totalTransferCents, report.baseCurrencyCode),
                                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = numberFont),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "统计主币种",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = report.baseCurrencyCode,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // Largest expense / income highlights
                        if (report.largestExpense != null || report.largestIncome != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "单笔极值",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    report.largestExpense?.let { peak ->
                                        QuickAnalysisHighlightRow(
                                            tag = "最高支出",
                                            accent = ExpenseCoral,
                                            merchant = peak.merchant,
                                            dateText = peak.dateText,
                                            amountText = formatCurrency(peak.amountCents, report.baseCurrencyCode)
                                        )
                                    }
                                    if (report.largestExpense != null && report.largestIncome != null) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                    }
                                    report.largestIncome?.let { peak ->
                                        QuickAnalysisHighlightRow(
                                            tag = "最高收入",
                                            accent = IncomeJade,
                                            merchant = peak.merchant,
                                            dateText = peak.dateText,
                                            amountText = formatCurrency(peak.amountCents, report.baseCurrencyCode)
                                        )
                                    }
                                }
                            }
                        }

                        // Category breakdown bars
                        if (report.topCategories.isNotEmpty()) {
                            QuickAnalysisBreakdownCard(
                                sectionTitle = "分类支出分布",
                                subtitle = null,
                                items = report.topCategories,
                                baseCurrencyCode = report.baseCurrencyCode,
                                barColor = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Channel breakdown bars
                        if (report.topChannels.isNotEmpty()) {
                            QuickAnalysisBreakdownCard(
                                sectionTitle = "渠道支出分布",
                                subtitle = if (report.hasCompositeChannels) "组合支付渠道已按 & 等分隔符分别统计" else null,
                                items = report.topChannels,
                                baseCurrencyCode = report.baseCurrencyCode,
                                barColor = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        // Toggleable raw plain text preview
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "复制后仍为纯文本摘要，便于直接粘贴分享",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { showPlainTextPreview = !showPlainTextPreview },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("quick_analysis_toggle_plaintext")
                            ) {
                                Text(if (showPlainTextPreview) "收起纯文本" else "查看纯文本")
                            }
                        }
                    }

                    AnimatedVisibility(visible = showPlainTextPreview) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_analysis_dialog_text")
                        ) {
                            SelectionContainer {
                                Text(
                                    text = copyPlainText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Bottom actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(copyPlainText))
                            copied = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("quick_analysis_copy_button")
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Rounded.Check else AppIcons.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (copied) "已复制纯文本" else "复制纯文本")
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("quick_analysis_close_button")
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAnalysisMetaPill(text: String) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun QuickAnalysisKpiCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val numberFont = LocalNumberFontFamily.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = numberFont),
                color = valueColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickAnalysisHighlightRow(
    tag: String,
    accent: Color,
    merchant: String,
    dateText: String,
    amountText: String
) {
    val numberFont = LocalNumberFontFamily.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = accent.copy(alpha = 0.14f)
        ) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = merchant,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = amountText,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = numberFont),
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QuickAnalysisBreakdownCard(
    sectionTitle: String,
    subtitle: String?,
    items: List<QuickAnalysisBreakdownItem>,
    baseCurrencyCode: String,
    barColor: Color
) {
    val numberFont = LocalNumberFontFamily.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            items.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${item.label} · ${item.count} 笔",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${formatCurrency(item.amountCents, baseCurrencyCode)} (${"%.1f".format(item.sharePercent)}%)",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = numberFont),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        val fraction = (item.sharePercent / 100.0).toFloat().coerceIn(0.03f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(6.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(barColor.copy(alpha = 0.85f))
                        )
                    }
                }
            }
        }
    }
}

