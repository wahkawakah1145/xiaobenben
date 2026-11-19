package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.ui.theme.AppIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.CategoryCatalog
import com.example.model.CurrencyCatalog
import com.example.model.LedgerTransaction
import com.example.model.ParseMethod
import com.example.model.TransactionType
import com.example.ui.theme.AlipayBlue
import com.example.ui.theme.BankAmber
import com.example.ui.theme.BankCrimson
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.ui.theme.LocalNumberFontFamily
import com.example.ui.theme.TransferCobalt
import com.example.ui.theme.UnionPayBlue
import com.example.ui.theme.WechatGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

fun formatCurrency(
    cents: Long,
    currencyCode: String = "CNY",
    showSign: Boolean = false,
    type: TransactionType? = null
): String {
    val info = CurrencyCatalog.resolve(currencyCode)
    val absValue = kotlin.math.abs(cents) / 100.0
    val formatted = if (info.zeroDecimalDisplay && kotlin.math.abs(cents) % 100L == 0L) {
        "%.0f".format(absValue)
    } else {
        "%.2f".format(absValue)
    }
    val base = "${info.symbol}$formatted"
    if (!showSign || type == null) return base
    return when (type) {
        TransactionType.EXPENSE -> "-$base"
        TransactionType.INCOME -> "+$base"
        TransactionType.TRANSFER -> base
    }
}

fun formatYuan(cents: Long, showSign: Boolean = false, type: TransactionType? = null): String =
    formatCurrency(cents, "CNY", showSign, type)

fun formatTimeShort(millis: Long): String {
    return SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(millis))
}

fun formatDateHeader(millis: Long): String {
    return SimpleDateFormat("MM月dd日 EEEE", Locale.CHINA).format(Date(millis))
}

fun formatDateTimeFull(millis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))
}

fun channelBrandColor(channel: String): Color {
    return when {
        channel.contains("支付宝") -> AlipayBlue
        channel.contains("微信") -> WechatGreen
        channel.contains("云闪付") || channel.contains("银联") -> UnionPayBlue
        channel.contains("招商") || channel.contains("工商") || channel.contains("中国银行") ||
            channel.contains("中信") || channel.contains("广发") -> BankCrimson
        channel.contains("建设") || channel.contains("农业") || channel.contains("交通") ||
            channel.contains("邮储") || channel.contains("平安") || channel.contains("浦发") -> BankAmber
        else -> Color(0xFF0F6B54)
    }
}

fun categoryIcon(category: String): ImageVector {
    return when (category) {
        "餐饮美食" -> AppIcons.Restaurant
        "交通出行" -> AppIcons.DirectionsCar
        "购物消费" -> AppIcons.ShoppingBag
        "居家生活" -> Icons.Rounded.Home
        "休闲娱乐" -> AppIcons.Movie
        "医疗健康" -> AppIcons.LocalHospital
        "工资奖金" -> AppIcons.Payments
        "理财收益" -> AppIcons.Savings
        "退款返现" -> AppIcons.ReceiptLong
        "转账红包", "转账收款" -> AppIcons.CardGiftcard
        else -> AppIcons.AccountBalance
    }
}

fun transactionTypeColor(type: TransactionType): Color {
    return when (type) {
        TransactionType.EXPENSE -> ExpenseCoral
        TransactionType.INCOME -> IncomeJade
        TransactionType.TRANSFER -> TransferCobalt
    }
}

@Composable
fun ChannelBadge(channel: String, modifier: Modifier = Modifier) {
    val color = channelBrandColor(channel)
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = channel,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun ParseMethodBadge(method: ParseMethod, modifier: Modifier = Modifier) {
    val (bg, fg) = when (method) {
        ParseMethod.LOCAL_NLP -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        ParseMethod.RULE_ENGINE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ParseMethod.IMPORT -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        ParseMethod.MANUAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (method == ParseMethod.LOCAL_NLP) {
                Icon(
                    imageVector = AppIcons.AutoAwesome,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(11.dp)
                )
            }
            Text(
                text = method.label,
                style = MaterialTheme.typography.labelSmall,
                color = fg
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    transaction: LedgerTransaction,
    baseCurrencyCode: String,
    liveRateToBase: Double,
    liveConvertedBaseCents: Long,
    recordedBaseCurrencyCode: String,
    recordedRateToBase: Double,
    recordedConvertedBaseCents: Long,
    onDismiss: () -> Unit,
    onConfirmPending: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val numberFont = LocalNumberFontFamily.current
    val isForeignFromCurrentBase = !transaction.currencyCode.equals(baseCurrencyCode, ignoreCase = true)
    val isForeignFromRecordedBase = !transaction.currencyCode.equals(recordedBaseCurrencyCode, ignoreCase = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(transactionTypeColor(transaction.txType).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon(transaction.category),
                        contentDescription = transaction.category,
                        tint = transactionTypeColor(transaction.txType)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.merchant,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${transaction.category} · ${formatDateTimeFull(transaction.occurredAtMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatCurrency(
                            cents = transaction.amountCents,
                            currencyCode = transaction.currencyCode,
                            showSign = true,
                            type = transaction.txType
                        ),
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = numberFont),
                        color = transactionTypeColor(transaction.txType),
                        fontWeight = FontWeight.Bold
                    )
                    if (isForeignFromRecordedBase) {
                        Text(
                            text = "记账 ≈ ${formatCurrency(recordedConvertedBaseCents, recordedBaseCurrencyCode)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChannelBadge(channel = transaction.channel)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${transaction.currencyCode} ${transaction.currencyInfo.chineseName}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (!transaction.cardLastFour.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "尾号 ${transaction.cardLastFour}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                ParseMethodBadge(method = transaction.txParseMethod)
            }

            if (isForeignFromCurrentBase || isForeignFromRecordedBase) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_exchange_comparison_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "汇率快照与实时换汇",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "记录当时汇率 (1 ${transaction.currencyCode} = %.4f $recordedBaseCurrencyCode)".format(Locale.US, recordedRateToBase),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatCurrency(recordedConvertedBaseCents, recordedBaseCurrencyCode),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = numberFont),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "当前实时汇率 (1 ${transaction.currencyCode} = %.4f $baseCurrencyCode)".format(Locale.US, liveRateToBase),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = formatCurrency(liveConvertedBaseCents, baseCurrencyCode),
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = numberFont),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("detail_live_converted_amount")
                            )
                        }
                    }
                }
            }

            if (!transaction.note.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "备注",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = transaction.note,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (!transaction.rawNotification.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "原始记录",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = transaction.rawNotification,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (!transaction.confirmed && onConfirmPending != null) {
                Button(
                    onClick = onConfirmPending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_confirm_pending_button")
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("确认入账")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("delete_tx_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseCoral)
                ) {
                    Icon(AppIcons.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("删除")
                }
                Button(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("edit_tx_button")
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("编辑")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorDialog(
    initialTransaction: LedgerTransaction?,
    defaultCurrencyCode: String = "CNY",
    onDismiss: () -> Unit,
    onSave: (
        amountCents: Long,
        currencyCode: String,
        type: TransactionType,
        category: String,
        merchant: String,
        channel: String,
        cardLastFour: String?,
        note: String?,
        occurredAtMillis: Long
    ) -> Unit
) {
    var selectedType by remember {
        mutableStateOf(initialTransaction?.txType ?: TransactionType.EXPENSE)
    }
    var selectedCurrency by remember {
        mutableStateOf(initialTransaction?.currencyCode ?: defaultCurrencyCode)
    }
    var amountText by remember {
        mutableStateOf(
            if (initialTransaction != null) "%.2f".format(initialTransaction.amountCents / 100.0)
            else ""
        )
    }
    var merchant by remember { mutableStateOf(initialTransaction?.merchant ?: "") }
    var channel by remember { mutableStateOf(initialTransaction?.channel ?: "支付宝") }
    var category by remember {
        mutableStateOf(initialTransaction?.category ?: "餐饮美食")
    }
    var cardTail by remember { mutableStateOf(initialTransaction?.cardLastFour ?: "") }
    var note by remember { mutableStateOf(initialTransaction?.note ?: "") }
    val initialMillis = remember(initialTransaction) {
        initialTransaction?.occurredAtMillis ?: System.currentTimeMillis()
    }
    var dateText by remember(initialMillis) {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(initialMillis)))
    }
    var timeText by remember(initialMillis) {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.US).format(Date(initialMillis)))
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val availableCategories = when (selectedType) {
        TransactionType.INCOME -> CategoryCatalog.incomeCategories
        TransactionType.EXPENSE -> CategoryCatalog.expenseCategories
        TransactionType.TRANSFER -> listOf("转账红包", "其他")
    }

    val quickChannels = listOf("支付宝", "微信支付", "云闪付", "招商银行", "工商银行", "建设银行", "手动记账")
    val currencyInfo = CurrencyCatalog.resolve(selectedCurrency)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialTransaction == null) "记一笔" else "编辑账单")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TransactionType.entries.forEachIndexed { index, txType ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = TransactionType.entries.size
                            ),
                            onClick = {
                                selectedType = txType
                                if (txType == TransactionType.INCOME && category !in CategoryCatalog.incomeCategories) {
                                    category = "工资奖金"
                                } else if (txType == TransactionType.EXPENSE && category !in CategoryCatalog.expenseCategories) {
                                    category = "餐饮美食"
                                } else if (txType == TransactionType.TRANSFER) {
                                    category = "转账红包"
                                }
                            },
                            selected = selectedType == txType
                        ) {
                            Text(txType.label)
                        }
                    }
                }

                Text("币种", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CurrencyCatalog.currencies.forEach { c ->
                        FilterChip(
                            selected = selectedCurrency.equals(c.code, ignoreCase = true),
                            onClick = { selectedCurrency = c.code },
                            label = { Text("${c.code} ${c.chineseName}") }
                        )
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; validationError = null },
                    label = { Text("金额 (${currencyInfo.chineseName})") },
                    prefix = { Text(currencyInfo.symbol) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_amount_input")
                )

                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it; validationError = null },
                    label = { Text("商户 / 交易对象") },
                    placeholder = { Text("例如：星巴克、美团外卖") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_merchant_input")
                )

                Text("支付渠道 / 银行", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quickChannels.forEach { item ->
                        FilterChip(
                            selected = channel == item,
                            onClick = { channel = item },
                            label = { Text(item) }
                        )
                    }
                }

                OutlinedTextField(
                    value = channel,
                    onValueChange = { channel = it },
                    label = { Text("自定义渠道或银行名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("分类", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    availableCategories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cardTail,
                        onValueChange = { cardTail = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("卡尾号 (可选)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(120) },
                        label = { Text("备注 (可选)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                }

                Text("记账时间（可修正通知延迟/导入偏差）", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it.take(10); validationError = null },
                        label = { Text("日期 (yyyy-MM-dd)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.25f)
                            .testTag("editor_date_input")
                    )
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { timeText = it.take(5); validationError = null },
                        label = { Text("时间 (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.95f)
                            .testTag("editor_time_input")
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { showDatePicker = true },
                        label = { Text("日历选日期") }
                    )
                    AssistChip(
                        onClick = {
                            val now = Date(System.currentTimeMillis())
                            dateText = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
                            timeText = SimpleDateFormat("HH:mm", Locale.US).format(now)
                        },
                        label = { Text("此刻") }
                    )
                    AssistChip(
                        onClick = {
                            val millis = (parseDateTimeInputMillis(dateText, timeText) ?: System.currentTimeMillis()) - 3_600_000L
                            val d = Date(millis)
                            dateText = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)
                            timeText = SimpleDateFormat("HH:mm", Locale.US).format(d)
                        },
                        label = { Text("-1小时") }
                    )
                    AssistChip(
                        onClick = {
                            val millis = System.currentTimeMillis() - 86_400_000L
                            val d = Date(millis)
                            dateText = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)
                            timeText = SimpleDateFormat("HH:mm", Locale.US).format(d)
                        },
                        label = { Text("昨天此时") }
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
        confirmButton = {
            Button(
                onClick = {
                    val yuan = amountText.trim().toDoubleOrNull()
                    if (yuan == null || yuan <= 0.0 || yuan > 10_000_000.0) {
                        validationError = "请输入有效的交易金额（大于 0）"
                        return@Button
                    }
                    val parsedTimeMillis = parseDateTimeInputMillis(dateText, timeText)
                    if (parsedTimeMillis == null) {
                        validationError = "请输入有效日期和时间，例如 2026-11-19 与 08:30"
                        return@Button
                    }
                    val cents = (yuan * 100.0).roundToLong().coerceAtLeast(1L)
                    onSave(
                        cents,
                        selectedCurrency,
                        selectedType,
                        category,
                        merchant.trim().ifBlank { category },
                        channel.trim().ifBlank { "手动记账" },
                        cardTail.trim().ifBlank { null },
                        note.trim().ifBlank { null },
                        parsedTimeMillis
                    )
                },
                modifier = Modifier.testTag("editor_save_button")
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    if (showDatePicker) {
        val initialPickerUtc = remember(dateText) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = parseDateTimeInputMillis(dateText, "12:00") ?: initialMillis
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            utc.clear()
            utc.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            utc.timeInMillis
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialPickerUtc)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = utcMillis
                            }
                            dateText = "%04d-%02d-%02d".format(
                                Locale.US,
                                utcCal.get(Calendar.YEAR),
                                utcCal.get(Calendar.MONTH) + 1,
                                utcCal.get(Calendar.DAY_OF_MONTH)
                            )
                            validationError = null
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

fun parseDateTimeInputMillis(dateInput: String, timeInput: String): Long? {
    val cleanDate = dateInput.trim().replace('/', '-').replace('.', '-')
    val cleanTime = timeInput.trim().replace('：', ':')
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
    return runCatching {
        fmt.parse("$cleanDate $cleanTime")?.time
    }.getOrNull()
}
