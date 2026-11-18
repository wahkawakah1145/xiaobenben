package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BudgetSettings
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import com.example.service.NotificationBridge
import com.example.ui.components.ChannelBadge
import com.example.ui.components.ParseMethodBadge
import com.example.ui.components.TransactionDetailSheet
import com.example.ui.components.TransactionEditorDialog
import com.example.ui.components.categoryIcon
import com.example.ui.components.formatDateHeader
import com.example.ui.components.formatTimeShort
import com.example.ui.components.formatYuan
import com.example.ui.components.transactionTypeColor
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.UiState
import java.util.Calendar

@Composable
fun LedgerHomeScreen(
    transactionsState: UiState<List<LedgerTransaction>>,
    settings: BudgetSettings,
    isListenerEnabled: Boolean,
    viewModel: LedgerViewModel,
    onNavigateToParser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("全部") }
    var selectedTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }
    var editingTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val filterOptions = listOf("全部", "支出", "收入", "支付宝", "微信支付", "银行卡", "云闪付")

    val allTransactions = (transactionsState as? UiState.Success)?.data ?: emptyList()

    val nowCal = remember { Calendar.getInstance() }
    val currentYear = nowCal.get(Calendar.YEAR)
    val currentMonth = nowCal.get(Calendar.MONTH)

    val monthlyStats by remember(allTransactions) {
        derivedStateOf {
            val monthItems = LedgerViewModel.filterByMonth(allTransactions, currentYear, currentMonth)
            val expense = monthItems.filter { it.txType == TransactionType.EXPENSE }.sumOf { it.amountCents }
            val income = monthItems.filter { it.txType == TransactionType.INCOME }.sumOf { it.amountCents }
            Triple(expense, income, monthItems.size)
        }
    }

    val filteredTransactions by remember(allTransactions, searchQuery, selectedFilter) {
        derivedStateOf {
            allTransactions.filter { tx ->
                val matchesFilter = when (selectedFilter) {
                    "全部" -> true
                    "支出" -> tx.txType == TransactionType.EXPENSE
                    "收入" -> tx.txType == TransactionType.INCOME
                    "支付宝" -> tx.channel.contains("支付宝")
                    "微信支付" -> tx.channel.contains("微信")
                    "云闪付" -> tx.channel.contains("云闪付") || tx.channel.contains("银联")
                    "银行卡" -> tx.channel.contains("银行")
                    else -> true
                }
                val q = searchQuery.trim()
                val matchesSearch = q.isEmpty() ||
                    tx.merchant.contains(q, ignoreCase = true) ||
                    tx.category.contains(q, ignoreCase = true) ||
                    tx.channel.contains(q, ignoreCase = true) ||
                    (tx.note?.contains(q, ignoreCase = true) == true)
                matchesFilter && matchesSearch
            }
        }
    }

    val groupedByDate by remember(filteredTransactions) {
        derivedStateOf {
            filteredTransactions.groupBy { formatDateHeader(it.occurredAtMillis) }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Listener permission banner
            item(key = "listener_banner") {
                ListenerStatusBanner(
                    isEnabled = isListenerEnabled,
                    onOpenSettings = { NotificationBridge.openNotificationListenerSettings(context) },
                    onOpenTestBench = onNavigateToParser
                )
            }

            // 2. Monthly Summary Hero Card
            item(key = "summary_card") {
                MonthlySummaryCard(
                    monthTitle = "${currentYear}年${currentMonth + 1}月",
                    expenseCents = monthlyStats.first,
                    incomeCents = monthlyStats.second,
                    txCount = monthlyStats.third,
                    budgetCents = settings.monthlyBudgetCents
                )
            }

            // 3. Search & Filter Chips
            item(key = "search_and_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索商户、银行、分类或备注") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ledger_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filterOptions.forEach { option ->
                            FilterChip(
                                selected = selectedFilter == option,
                                onClick = { selectedFilter = option },
                                label = { Text(option) },
                                modifier = Modifier.testTag("filter_chip_$option")
                            )
                        }
                    }
                }
            }

            // 4. Content states
            when (transactionsState) {
                is UiState.Loading -> {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                is UiState.Error -> {
                    item(key = "error") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = ExpenseCoral.copy(alpha = 0.10f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "账单读取失败：${transactionsState.message}",
                                color = ExpenseCoral,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    if (filteredTransactions.isEmpty()) {
                        item(key = "empty") {
                            EmptyLedgerCard(
                                hasSearch = searchQuery.isNotBlank() || selectedFilter != "全部",
                                onOpenParser = onNavigateToParser,
                                onAddManual = { showCreateDialog = true }
                            )
                        }
                    } else {
                        groupedByDate.forEach { (dateHeader, itemsForDay) ->
                            val dayExpense = itemsForDay
                                .filter { it.txType == TransactionType.EXPENSE }
                                .sumOf { it.amountCents }
                            val dayIncome = itemsForDay
                                .filter { it.txType == TransactionType.INCOME }
                                .sumOf { it.amountCents }

                            item(key = "header_$dateHeader") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dateHeader,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (dayExpense > 0L) {
                                            Text(
                                                text = "支 ${formatYuan(dayExpense)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ExpenseCoral
                                            )
                                        }
                                        if (dayIncome > 0L) {
                                            Text(
                                                text = "收 ${formatYuan(dayIncome)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = IncomeJade
                                            )
                                        }
                                    }
                                }
                            }

                            items(
                                items = itemsForDay,
                                key = { it.id.ifBlank { "${it.occurredAtMillis}_${it.merchant}" } }
                            ) { tx ->
                                TransactionRowCard(
                                    transaction = tx,
                                    onClick = { selectedTransaction = tx }
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_transaction_fab")
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.add_transaction_cd)
            )
        }
    }

    selectedTransaction?.let { tx ->
        TransactionDetailSheet(
            transaction = tx,
            onDismiss = { selectedTransaction = null },
            onEdit = {
                editingTransaction = tx
                selectedTransaction = null
            },
            onDelete = {
                viewModel.deleteTransaction(tx.id)
                selectedTransaction = null
            }
        )
    }

    if (showCreateDialog) {
        TransactionEditorDialog(
            initialTransaction = null,
            onDismiss = { showCreateDialog = false },
            onSave = { amountCents, type, category, merchant, channel, tail, note ->
                viewModel.addManualTransaction(
                    amountCents = amountCents,
                    type = type,
                    category = category,
                    merchant = merchant,
                    channel = channel,
                    cardLastFour = tail,
                    note = note
                )
                showCreateDialog = false
            }
        )
    }

    editingTransaction?.let { tx ->
        TransactionEditorDialog(
            initialTransaction = tx,
            onDismiss = { editingTransaction = null },
            onSave = { amountCents, type, category, merchant, channel, tail, note ->
                viewModel.updateTransaction(
                    txId = tx.id,
                    amountCents = amountCents,
                    type = type,
                    category = category,
                    merchant = merchant,
                    channel = channel,
                    cardLastFour = tail,
                    note = note,
                    occurredAtMillis = tx.occurredAtMillis
                )
                editingTransaction = null
            }
        )
    }
}

@Composable
private fun ListenerStatusBanner(
    isEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onOpenTestBench: () -> Unit
) {
    Surface(
        color = if (isEnabled) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f)
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.NotificationsOff,
                contentDescription = null,
                tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEnabled) stringResource(R.string.listener_enabled) else stringResource(R.string.listener_disabled),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = if (isEnabled) "自动监测支付宝、微信、云闪付及银行通知" else "开启权限即可自动捕捉支付与银行短信通知",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isEnabled) {
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("banner_enable_listener_button")
                ) {
                    Text(stringResource(R.string.enable_listener_action))
                }
            } else {
                TextButton(
                    onClick = onOpenTestBench,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("测试识别")
                }
            }
        }
    }
}

@Composable
private fun MonthlySummaryCard(
    monthTitle: String,
    expenseCents: Long,
    incomeCents: Long,
    txCount: Int,
    budgetCents: Long
) {
    val progress = if (budgetCents > 0L) (expenseCents.toFloat() / budgetCents.toFloat()).coerceIn(0f, 1f) else 0f
    val isOverBudget = budgetCents > 0L && expenseCents > budgetCents

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$monthTitle · 本月支出",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${txCount} 笔账单",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = formatYuan(expenseCents),
                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column {
                        Text(
                            text = "本月收入",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                        )
                        Text(
                            text = formatYuan(incomeCents),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Column {
                        Text(
                            text = "本月结余",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                        )
                        val net = incomeCents - expenseCents
                        Text(
                            text = (if (net < 0) "-" else "") + formatYuan(kotlin.math.abs(net)),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                if (budgetCents > 0L) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isOverBudget) "已超出月度预算" else "月度预算进度",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                            Text(
                                text = "${(progress * 100).toInt()}% / ${formatYuan(budgetCents)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            color = if (isOverBudget) Color(0xFFFFB4AB) else Color(0xFFA7F3D0),
                            trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRowCard(
    transaction: LedgerTransaction,
    onClick: () -> Unit
) {
    val typeColor = transactionTypeColor(transaction.txType)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("transaction_card_${transaction.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(typeColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(transaction.category),
                    contentDescription = transaction.category,
                    tint = typeColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = transaction.merchant,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChannelBadge(channel = transaction.channel)
                    if (!transaction.cardLastFour.isNullOrBlank()) {
                        Text(
                            text = "•${transaction.cardLastFour}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${transaction.category} · ${formatTimeShort(transaction.occurredAtMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formatYuan(transaction.amountCents, showSign = true, type = transaction.txType),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                    color = typeColor,
                    fontWeight = FontWeight.Bold
                )
                ParseMethodBadge(method = transaction.txParseMethod)
            }
        }
    }
}

@Composable
private fun EmptyLedgerCard(
    hasSearch: Boolean,
    onOpenParser: () -> Unit,
    onAddManual: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_ledger),
                contentDescription = stringResource(R.string.empty_ledger_cd),
                modifier = Modifier
                    .height(140.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit
            )
            Text(
                text = if (hasSearch) "没有匹配的账单记录" else "静候您的第一笔通知入账",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (hasSearch) {
                    "请尝试切换渠道筛选或清空搜索关键词。"
                } else {
                    "开启系统通知监听后，支付宝、微信、云闪付及银行动账将自动入账；您也可以在「识别」页粘贴通知文本或触发真实系统通知测试。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasSearch) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onAddManual,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("手动记一笔")
                    }
                    Button(
                        onClick = onOpenParser,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("empty_open_parser_button")
                    ) {
                        Icon(Icons.Rounded.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("去通知识别台")
                    }
                }
            }
        }
    }
}
