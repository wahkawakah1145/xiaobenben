package com.example.ui.components

import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import com.example.R
import com.example.ui.theme.AppIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.AppIconStyle
import com.example.model.CategoryCatalog
import com.example.model.CurrencyCatalog
import com.example.model.IconVisualStyle
import com.example.model.LedgerTransaction
import com.example.model.ParseMethod
import com.example.model.SimilarBillCriterion
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
    return SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(millis))
}

fun formatDateHeader(
    millis: Long,
    nowYear: Int = Calendar.getInstance().get(Calendar.YEAR)
): String {
    val txCal = Calendar.getInstance().apply { timeInMillis = millis }
    val pattern = if (txCal.get(Calendar.YEAR) != nowYear) {
        "yyyy年MM月dd日 EEEE"
    } else {
        "MM月dd日 EEEE"
    }
    return SimpleDateFormat(pattern, Locale.CHINA).format(Date(millis))
}

fun formatItemTimeCompact(
    millis: Long,
    nowYear: Int = Calendar.getInstance().get(Calendar.YEAR)
): String {
    val txCal = Calendar.getInstance().apply { timeInMillis = millis }
    val pattern = if (txCal.get(Calendar.YEAR) != nowYear) {
        "yyyy-MM-dd HH:mm:ss"
    } else {
        "HH:mm:ss"
    }
    return SimpleDateFormat(pattern, Locale.CHINA).format(Date(millis))
}

fun formatDateTimeFull(millis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(millis))
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
    onMarkNonBill: (() -> Unit)? = null,
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

            val extraFields = listOfNotNull(
                transaction.tradeType?.takeIf { it.isNotBlank() }?.let { "交易类型" to it },
                transaction.productName?.takeIf { it.isNotBlank() }?.let { "商品" to it },
                transaction.tradeStatus?.takeIf { it.isNotBlank() }?.let { "当前状态" to it },
                transaction.balanceAfterCents?.let { bal ->
                    val prefix = if (bal < 0L) "-" else ""
                    "本次余额" to "$prefix${formatCurrency(kotlin.math.abs(bal), transaction.currencyCode)}"
                },
                transaction.transactionNo?.takeIf { it.isNotBlank() }?.let { "交易单号" to it },
                transaction.merchantOrderNo?.takeIf { it.isNotBlank() }?.let { "商户单号" to it }
            )
            if (extraFields.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_extended_bill_fields")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "账单明细信息",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        extraFields.forEach { (label, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onMarkNonBill != null) {
                        OutlinedButton(
                            onClick = onMarkNonBill,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseCoral),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_mark_non_bill_button")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("非账单")
                        }
                    }
                    Button(
                        onClick = onConfirmPending,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sheet_confirm_pending_button")
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("确认入账")
                    }
                }
            } else if (onMarkNonBill != null && !transaction.rawNotification.isNullOrBlank()) {
                OutlinedButton(
                    onClick = onMarkNonBill,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseCoral),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_mark_non_bill_confirmed_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("标记为非账单（不再识别此类通知）")
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
    enabledCurrencyCodes: List<String> = CurrencyCatalog.defaultEnabledForeignCodes,
    expenseCategories: List<String> = CategoryCatalog.expenseCategories,
    incomeCategories: List<String> = CategoryCatalog.incomeCategories,
    transferCategories: List<String> = CategoryCatalog.transferCategories,
    allTransactions: List<LedgerTransaction> = emptyList(),
    onBatchApplySimilar: ((
        matchedIds: List<String>,
        type: TransactionType?,
        category: String?,
        channel: String?,
        merchant: String?,
        currencyCode: String?,
        note: String?
    ) -> Unit)? = null,
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
    ) -> Unit,
    onSaveExtended: ((
        tradeType: String?,
        productName: String?,
        tradeStatus: String?,
        transactionNo: String?,
        merchantOrderNo: String?
    ) -> Unit)? = null,
    onMarkAsNonBill: (() -> Unit)? = null
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
    var tradeType by remember { mutableStateOf(initialTransaction?.tradeType ?: "") }
    var productName by remember { mutableStateOf(initialTransaction?.productName ?: "") }
    var tradeStatus by remember { mutableStateOf(initialTransaction?.tradeStatus ?: "") }
    var transactionNo by remember { mutableStateOf(initialTransaction?.transactionNo ?: "") }
    var merchantOrderNo by remember { mutableStateOf(initialTransaction?.merchantOrderNo ?: "") }
    var showMoreFields by remember {
        mutableStateOf(
            !initialTransaction?.tradeType.isNullOrBlank() ||
                !initialTransaction?.productName.isNullOrBlank() ||
                !initialTransaction?.tradeStatus.isNullOrBlank() ||
                !initialTransaction?.transactionNo.isNullOrBlank() ||
                !initialTransaction?.merchantOrderNo.isNullOrBlank()
        )
    }
    var currencyQuery by remember { mutableStateOf("") }
    val filteredCurrencies = remember(currencyQuery, enabledCurrencyCodes, selectedCurrency) {
        if (currencyQuery.isBlank()) {
            CurrencyCatalog.enabledCurrenciesWithCny(enabledCurrencyCodes, ensureCode = selectedCurrency)
        } else {
            CurrencyCatalog.search(currencyQuery)
        }
    }
    val initialMillis = remember(initialTransaction) {
        initialTransaction?.occurredAtMillis ?: System.currentTimeMillis()
    }
    var dateText by remember(initialMillis) {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(initialMillis)))
    }
    var timeText by remember(initialMillis) {
        mutableStateOf(SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(initialMillis)))
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val availableCategories = remember(selectedType, expenseCategories, incomeCategories, transferCategories, category) {
        val base = when (selectedType) {
            TransactionType.INCOME -> incomeCategories
            TransactionType.EXPENSE -> expenseCategories
            TransactionType.TRANSFER -> transferCategories
        }
        if (category.isNotBlank() && category !in base) base + category else base
    }

    val canFindSimilar = initialTransaction != null && allTransactions.isNotEmpty() && onBatchApplySimilar != null
    var showSimilarPanel by remember(initialTransaction?.id) { mutableStateOf(false) }
    val availableSimilarCriteria = remember(initialTransaction) {
        if (initialTransaction == null) {
            emptyList()
        } else {
            SimilarBillCriterion.entries.filter { it.previewValue(initialTransaction) != null }
        }
    }
    var selectedSimilarCriteria by remember(initialTransaction?.id) {
        mutableStateOf(
            if (initialTransaction != null && SimilarBillCriterion.MERCHANT.previewValue(initialTransaction) != null) {
                setOf(SimilarBillCriterion.MERCHANT)
            } else {
                availableSimilarCriteria.take(1).toSet()
            }
        )
    }
    val matchedSimilarTransactions = remember(initialTransaction, allTransactions, selectedSimilarCriteria) {
        if (initialTransaction == null || selectedSimilarCriteria.isEmpty()) {
            emptyList()
        } else {
            allTransactions.filter { other ->
                other.id != initialTransaction.id &&
                    other.matchesSimilarCriteria(initialTransaction, selectedSimilarCriteria)
            }
        }
    }
    var selectedSimilarIds by remember(initialTransaction?.id, matchedSimilarTransactions) {
        mutableStateOf<Set<String>>(matchedSimilarTransactions.mapTo(LinkedHashSet()) { it.id })
    }
    var syncTypeToSimilar by remember(initialTransaction?.id) { mutableStateOf(true) }
    var syncCategoryToSimilar by remember(initialTransaction?.id) { mutableStateOf(true) }
    var syncMerchantToSimilar by remember(initialTransaction?.id) { mutableStateOf(false) }
    var syncChannelToSimilar by remember(initialTransaction?.id) { mutableStateOf(false) }
    var syncCurrencyToSimilar by remember(initialTransaction?.id) { mutableStateOf(false) }
    var syncNoteToSimilar by remember(initialTransaction?.id) { mutableStateOf(false) }

    val quickChannels = listOf("支付宝", "微信支付", "云闪付", "招商银行", "工商银行", "建设银行", "手动记账")
    val currencyInfo = CurrencyCatalog.resolve(selectedCurrency)

    val clipboardManager = LocalClipboardManager.current
    var rawCopied by remember(initialTransaction?.id) { mutableStateOf(false) }

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
                val rawRecord = initialTransaction?.rawNotification?.takeIf { it.isNotBlank() }
                if (rawRecord != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_raw_record_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "原始记录（可长按选区复制）",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(rawRecord))
                                        rawCopied = true
                                    },
                                    modifier = Modifier.testTag("editor_copy_raw_button")
                                ) {
                                    Icon(
                                        imageVector = if (rawCopied) Icons.Rounded.Check else AppIcons.ContentCopy,
                                        contentDescription = "复制原始记录",
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (rawCopied) "已复制" else "复制")
                                }
                            }
                            SelectionContainer {
                                Text(
                                    text = rawRecord,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("editor_raw_record_text")
                                )
                            }
                        }
                    }
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TransactionType.entries.forEachIndexed { index, txType ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = TransactionType.entries.size
                            ),
                            onClick = {
                                selectedType = txType
                                if (txType == TransactionType.INCOME && category !in incomeCategories) {
                                    category = incomeCategories.firstOrNull() ?: "工资奖金"
                                } else if (txType == TransactionType.EXPENSE && category !in expenseCategories) {
                                    category = expenseCategories.firstOrNull() ?: "餐饮美食"
                                } else if (txType == TransactionType.TRANSFER && category !in transferCategories) {
                                    category = transferCategories.firstOrNull() ?: "不计收支"
                                }
                            },
                            selected = selectedType == txType
                        ) {
                            Text(txType.label)
                        }
                    }
                }

                Text("币种", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = currencyQuery,
                    onValueChange = { currencyQuery = it },
                    placeholder = { Text("搜索币种，如 hkd / 港元 / 泰铢") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_currency_search")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filteredCurrencies.forEach { c ->
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

                TextButton(
                    onClick = { showMoreFields = !showMoreFields },
                    modifier = Modifier.testTag("editor_toggle_extended_fields")
                ) {
                    Text(if (showMoreFields) "收起更多账单信息 ▲" else "展开更多账单信息（商品/类型/状态/单号）▼")
                }
                if (showMoreFields) {
                    OutlinedTextField(
                        value = productName,
                        onValueChange = { productName = it.take(120) },
                        label = { Text("商品 / 说明 (可选)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = tradeType,
                            onValueChange = { tradeType = it.take(60) },
                            label = { Text("交易类型 (可选)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = tradeStatus,
                            onValueChange = { tradeStatus = it.take(40) },
                            label = { Text("当前状态 (可选)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = transactionNo,
                        onValueChange = { transactionNo = it.take(80) },
                        label = { Text("交易单号 (可选)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = merchantOrderNo,
                        onValueChange = { merchantOrderNo = it.take(80) },
                        label = { Text("商户单号 (可选)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("记账时间（精确到秒，可修正通知延迟/导入偏差）", style = MaterialTheme.typography.labelMedium)
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
                        onValueChange = { timeText = it.take(8); validationError = null },
                        label = { Text("时间 (HH:mm:ss)") },
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
                            timeText = SimpleDateFormat("HH:mm:ss", Locale.US).format(now)
                        },
                        label = { Text("此刻") }
                    )
                    AssistChip(
                        onClick = {
                            val millis = (parseDateTimeInputMillis(dateText, timeText) ?: System.currentTimeMillis()) - 3_600_000L
                            val d = Date(millis)
                            dateText = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)
                            timeText = SimpleDateFormat("HH:mm:ss", Locale.US).format(d)
                        },
                        label = { Text("-1小时") }
                    )
                    AssistChip(
                        onClick = {
                            val millis = System.currentTimeMillis() - 86_400_000L
                            val d = Date(millis)
                            dateText = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)
                            timeText = SimpleDateFormat("HH:mm:ss", Locale.US).format(d)
                        },
                        label = { Text("昨天此时") }
                    )
                }

                if (canFindSimilar && initialTransaction != null) {
                    HorizontalDivider()
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSimilarPanel = !showSimilarPanel }
                                    .testTag("editor_similar_bills_toggle"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "查找相似账单并一键更改",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (matchedSimilarTransactions.isEmpty()) {
                                            "按商户/商品/渠道/分类等条件查找同特征账单"
                                        } else {
                                            "当前条件匹配到 ${matchedSimilarTransactions.size} 笔其他相似账单"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = if (showSimilarPanel) "收起 ▲" else "展开 ▼",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (showSimilarPanel) {
                                Text(
                                    text = "1. 选择匹配要素（可多选，需同时满足）：",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    availableSimilarCriteria.forEach { criterion ->
                                        val isPicked = criterion in selectedSimilarCriteria
                                        val previewVal = criterion.previewValue(initialTransaction).orEmpty().take(12)
                                        FilterChip(
                                            selected = isPicked,
                                            onClick = {
                                                selectedSimilarCriteria = if (isPicked) {
                                                    selectedSimilarCriteria - criterion
                                                } else {
                                                    selectedSimilarCriteria + criterion
                                                }
                                            },
                                            label = { Text("${criterion.label}:$previewVal") },
                                            modifier = Modifier.testTag("editor_similar_criterion_${criterion.name}")
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "命中 ${matchedSimilarTransactions.size} 笔，已选 ${selectedSimilarIds.size} 笔",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (matchedSimilarTransactions.isNotEmpty()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(
                                                onClick = {
                                                    val allIds = matchedSimilarTransactions.mapTo(LinkedHashSet()) { it.id }
                                                    selectedSimilarIds = if (selectedSimilarIds.size >= allIds.size) {
                                                        emptySet()
                                                    } else {
                                                        allIds
                                                    }
                                                }
                                            ) {
                                                Text(
                                                    if (selectedSimilarIds.size >= matchedSimilarTransactions.size) "全不选" else "全选",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                            TextButton(
                                                onClick = {
                                                    val allIds = matchedSimilarTransactions.mapTo(LinkedHashSet()) { it.id }
                                                    selectedSimilarIds = allIds - selectedSimilarIds
                                                },
                                                modifier = Modifier.testTag("editor_similar_invert_button")
                                            ) {
                                                Text("反选", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }

                                if (matchedSimilarTransactions.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        matchedSimilarTransactions.take(25).forEach { sim ->
                                            val checked = sim.id in selectedSimilarIds
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedSimilarIds = if (checked) {
                                                            selectedSimilarIds - sim.id
                                                        } else {
                                                            selectedSimilarIds + sim.id
                                                        }
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Checkbox(
                                                        checked = checked,
                                                        onCheckedChange = {
                                                            selectedSimilarIds = if (checked) {
                                                                selectedSimilarIds - sim.id
                                                            } else {
                                                                selectedSimilarIds + sim.id
                                                            }
                                                        }
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = sim.merchant,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Medium,
                                                            maxLines = 1
                                                        )
                                                        Text(
                                                            text = "${sim.category} · ${sim.channel} · ${formatDateHeader(sim.occurredAtMillis)}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1
                                                        )
                                                    }
                                                    Text(
                                                        text = formatCurrency(sim.amountCents, sim.currencyCode, showSign = true, type = sim.txType),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                        if (matchedSimilarTransactions.size > 25) {
                                            Text(
                                                text = "仅预览前 25 笔，一键更改将作用于全部已勾选的 ${selectedSimilarIds.size} 笔相似账单",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Text(
                                        text = "2. 选择要一键同步给相似账单的字段：",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        FilterChip(
                                            selected = syncTypeToSimilar,
                                            onClick = { syncTypeToSimilar = !syncTypeToSimilar },
                                            label = { Text("收支(${selectedType.label})") }
                                        )
                                        FilterChip(
                                            selected = syncCategoryToSimilar,
                                            onClick = { syncCategoryToSimilar = !syncCategoryToSimilar },
                                            label = { Text("分类($category)") }
                                        )
                                        FilterChip(
                                            selected = syncMerchantToSimilar,
                                            onClick = { syncMerchantToSimilar = !syncMerchantToSimilar },
                                            label = { Text("商户") }
                                        )
                                        FilterChip(
                                            selected = syncChannelToSimilar,
                                            onClick = { syncChannelToSimilar = !syncChannelToSimilar },
                                            label = { Text("渠道") }
                                        )
                                        FilterChip(
                                            selected = syncCurrencyToSimilar,
                                            onClick = { syncCurrencyToSimilar = !syncCurrencyToSimilar },
                                            label = { Text("币种($selectedCurrency)") }
                                        )
                                        FilterChip(
                                            selected = syncNoteToSimilar,
                                            onClick = { syncNoteToSimilar = !syncNoteToSimilar },
                                            label = { Text("备注") }
                                        )
                                    }

                                    val hasAnySyncField = syncTypeToSimilar || syncCategoryToSimilar ||
                                        syncMerchantToSimilar || syncChannelToSimilar ||
                                        syncCurrencyToSimilar || syncNoteToSimilar
                                    Button(
                                        enabled = selectedSimilarIds.isNotEmpty() && hasAnySyncField,
                                        onClick = {
                                            val yuan = amountText.trim().toDoubleOrNull()
                                            if (yuan == null || yuan <= 0.0 || yuan > 10_000_000.0) {
                                                validationError = "请输入有效的交易金额（大于 0）"
                                                return@Button
                                            }
                                            val parsedTimeMillis = parseDateTimeInputMillis(dateText, timeText)
                                            if (parsedTimeMillis == null) {
                                                validationError = "请输入有效日期和时间，例如 2026-11-19 与 08:30:00"
                                                return@Button
                                            }
                                            val cents = (yuan * 100.0).roundToLong().coerceAtLeast(1L)
                                            val cleanMerchant = merchant.trim().ifBlank { category }
                                            val cleanChannel = channel.trim().ifBlank { "手动记账" }
                                            val cleanNote = note.trim().ifBlank { null }
                                            onSaveExtended?.invoke(
                                                tradeType.trim().ifBlank { null },
                                                productName.trim().ifBlank { null },
                                                tradeStatus.trim().ifBlank { null },
                                                transactionNo.trim().ifBlank { null },
                                                merchantOrderNo.trim().ifBlank { null }
                                            )
                                            onSave(
                                                cents,
                                                selectedCurrency,
                                                selectedType,
                                                category,
                                                cleanMerchant,
                                                cleanChannel,
                                                cardTail.trim().ifBlank { null },
                                                cleanNote,
                                                parsedTimeMillis
                                            )
                                            onBatchApplySimilar?.invoke(
                                                selectedSimilarIds.toList(),
                                                if (syncTypeToSimilar) selectedType else null,
                                                if (syncCategoryToSimilar) category else null,
                                                if (syncChannelToSimilar) cleanChannel else null,
                                                if (syncMerchantToSimilar) cleanMerchant else null,
                                                if (syncCurrencyToSimilar) selectedCurrency else null,
                                                if (syncNoteToSimilar) (cleanNote ?: "") else null
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("editor_apply_similar_button")
                                    ) {
                                        Text("保存并一键更改 ${selectedSimilarIds.size + 1} 笔相似账单")
                                    }
                                }
                            }
                        }
                    }
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
                        validationError = "请输入有效日期和时间，例如 2026-11-19 与 08:30:00"
                        return@Button
                    }
                    val cents = (yuan * 100.0).roundToLong().coerceAtLeast(1L)
                    onSaveExtended?.invoke(
                        tradeType.trim().ifBlank { null },
                        productName.trim().ifBlank { null },
                        tradeStatus.trim().ifBlank { null },
                        transactionNo.trim().ifBlank { null },
                        merchantOrderNo.trim().ifBlank { null }
                    )
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (initialTransaction != null && onMarkAsNonBill != null) {
                    TextButton(
                        onClick = onMarkAsNonBill,
                        colors = ButtonDefaults.textButtonColors(contentColor = ExpenseCoral),
                        modifier = Modifier.testTag("editor_mark_non_bill_button")
                    ) {
                        Text("非账单")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
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
    val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm")
    for (p in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(p, Locale.US).apply { isLenient = false }.parse("$cleanDate $cleanTime")?.time
        }.getOrNull()
        if (parsed != null) return parsed
    }
    return null
}

private fun DrawScope.drawMinimalYenSymbol(
    cx: Float,
    cy: Float,
    scale: Float,
    color: Color,
    strokeWidth: Float
) {
    val arm = scale * 0.34f
    drawLine(color, Offset(cx - arm, cy - scale * 0.36f), Offset(cx, cy - scale * 0.04f), strokeWidth, cap = StrokeCap.Round)
    drawLine(color, Offset(cx + arm, cy - scale * 0.36f), Offset(cx, cy - scale * 0.04f), strokeWidth, cap = StrokeCap.Round)
    drawLine(color, Offset(cx, cy - scale * 0.04f), Offset(cx, cy + scale * 0.38f), strokeWidth, cap = StrokeCap.Round)
    drawLine(color, Offset(cx - arm * 0.92f, cy + scale * 0.06f), Offset(cx + arm * 0.92f, cy + scale * 0.06f), strokeWidth, cap = StrokeCap.Round)
    drawLine(color, Offset(cx - arm * 0.92f, cy + scale * 0.22f), Offset(cx + arm * 0.92f, cy + scale * 0.22f), strokeWidth, cap = StrokeCap.Round)
}

@Composable
fun AppBrandIconAvatar(
    styleId: String,
    size: Dp,
    cornerDp: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String = "小本本应用图标"
) {
    val style = AppIconStyle.fromId(styleId)
    val shape = RoundedCornerShape(cornerDp.coerceAtLeast(0.dp))
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiary
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer

    Surface(
        shape = shape,
        shadowElevation = 2.dp,
        border = BorderStroke(0.8.dp, primary.copy(alpha = 0.24f)),
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
        ) {
            val w = this.size.width
            val h = this.size.height
            when (style) {
                AppIconStyle.EMERALD -> {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(primary, lerp(primary, Color.Black, 0.18f))
                        )
                    )
                    drawRoundRect(
                        color = onPrimary,
                        topLeft = Offset(w * 0.27f, h * 0.22f),
                        size = Size(w * 0.46f, h * 0.56f),
                        cornerRadius = CornerRadius(w * 0.09f, w * 0.09f)
                    )
                    drawLine(
                        color = primary.copy(alpha = 0.45f),
                        start = Offset(w * 0.34f, h * 0.34f),
                        end = Offset(w * 0.34f, h * 0.66f),
                        strokeWidth = w * 0.035f,
                        cap = StrokeCap.Round
                    )
                    drawMinimalYenSymbol(
                        cx = w * 0.53f,
                        cy = h * 0.50f,
                        scale = w * 0.26f,
                        color = primary,
                        strokeWidth = w * 0.044f
                    )
                }
                AppIconStyle.MINIMAL -> {
                    drawRect(color = primaryContainer)
                    val fold = Path().apply {
                        moveTo(w * 0.29f, h * 0.23f)
                        lineTo(w * 0.60f, h * 0.23f)
                        lineTo(w * 0.72f, h * 0.35f)
                        lineTo(w * 0.72f, h * 0.77f)
                        lineTo(w * 0.29f, h * 0.77f)
                        close()
                    }
                    drawPath(
                        path = fold,
                        color = primary,
                        style = Stroke(width = w * 0.046f, join = StrokeJoin.Round)
                    )
                    drawMinimalYenSymbol(
                        cx = w * 0.505f,
                        cy = h * 0.535f,
                        scale = w * 0.23f,
                        color = primary,
                        strokeWidth = w * 0.042f
                    )
                }
                AppIconStyle.INK -> {
                    val inkBg = lerp(primary, Color(0xFF10211F), 0.62f)
                    drawRect(color = inkBg)
                    drawCircle(
                        color = primaryContainer,
                        radius = w * 0.075f,
                        center = Offset(w * 0.65f, h * 0.34f)
                    )
                    val ridge = Path().apply {
                        moveTo(w * 0.20f, h * 0.68f)
                        lineTo(w * 0.39f, h * 0.44f)
                        lineTo(w * 0.54f, h * 0.60f)
                        lineTo(w * 0.64f, h * 0.49f)
                        lineTo(w * 0.80f, h * 0.68f)
                    }
                    drawPath(
                        path = ridge,
                        color = lerp(primaryContainer, Color.White, 0.5f),
                        style = Stroke(width = w * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    drawLine(
                        color = primaryContainer.copy(alpha = 0.65f),
                        start = Offset(w * 0.28f, h * 0.77f),
                        end = Offset(w * 0.72f, h * 0.77f),
                        strokeWidth = w * 0.032f,
                        cap = StrokeCap.Round
                    )
                }
                AppIconStyle.CYBER -> {
                    val slate = lerp(primary, Color(0xFF0F172A), 0.78f)
                    drawRect(color = slate)
                    val hex = Path().apply {
                        moveTo(w * 0.50f, h * 0.20f)
                        lineTo(w * 0.76f, h * 0.35f)
                        lineTo(w * 0.76f, h * 0.65f)
                        lineTo(w * 0.50f, h * 0.80f)
                        lineTo(w * 0.24f, h * 0.65f)
                        lineTo(w * 0.24f, h * 0.35f)
                        close()
                    }
                    drawPath(
                        path = hex,
                        color = lerp(primary, Color.White, 0.32f),
                        style = Stroke(width = w * 0.044f, join = StrokeJoin.Round)
                    )
                    drawMinimalYenSymbol(
                        cx = w * 0.50f,
                        cy = h * 0.50f,
                        scale = w * 0.23f,
                        color = tertiary,
                        strokeWidth = w * 0.042f
                    )
                }
                AppIconStyle.VINTAGE -> {
                    val leather = lerp(primary, Color(0xFF5D4037), 0.52f)
                    val cream = Color(0xFFF5ECD8)
                    val brass = lerp(tertiary, Color(0xFFE5B96E), 0.45f)
                    drawRect(color = leather)
                    drawRoundRect(
                        color = cream,
                        topLeft = Offset(w * 0.26f, h * 0.23f),
                        size = Size(w * 0.48f, h * 0.54f),
                        cornerRadius = CornerRadius(w * 0.07f, w * 0.07f),
                        style = Stroke(width = w * 0.044f)
                    )
                    drawLine(
                        color = cream.copy(alpha = 0.85f),
                        start = Offset(w * 0.37f, h * 0.23f),
                        end = Offset(w * 0.37f, h * 0.77f),
                        strokeWidth = w * 0.038f
                    )
                    drawRoundRect(
                        color = brass,
                        topLeft = Offset(w * 0.57f, h * 0.44f),
                        size = Size(w * 0.22f, h * 0.12f),
                        cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
                    )
                }
                AppIconStyle.MONET_ADAPTIVE -> {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(primaryContainer, tertiaryContainer.copy(alpha = 0.85f))
                        )
                    )
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(w * 0.24f, h * 0.30f),
                        size = Size(w * 0.52f, h * 0.40f),
                        cornerRadius = CornerRadius(w * 0.11f, w * 0.11f)
                    )
                    drawCircle(
                        color = primaryContainer,
                        radius = w * 0.055f,
                        center = Offset(w * 0.63f, h * 0.50f)
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryIconAvatar(
    category: String,
    tintColor: Color,
    visualStyle: IconVisualStyle,
    size: Dp = 42.dp,
    modifier: Modifier = Modifier
) {
    val iconVec = categoryIcon(category)
    val iconSize = size * 0.5f
    when (visualStyle) {
        IconVisualStyle.FILLED -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(tintColor.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVec,
                    contentDescription = category,
                    tint = tintColor,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        IconVisualStyle.OUTLINED -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(tintColor.copy(alpha = 0.04f))
                    .border(1.4.dp, tintColor.copy(alpha = 0.58f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVec,
                    contentDescription = category,
                    tint = tintColor,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        IconVisualStyle.DUOTONE -> {
            val squircle = RoundedCornerShape(13.dp)
            Box(
                modifier = modifier
                    .size(size)
                    .clip(squircle)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                tintColor.copy(alpha = 0.24f),
                                tintColor.copy(alpha = 0.07f)
                            )
                        )
                    )
                    .border(0.8.dp, tintColor.copy(alpha = 0.22f), squircle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVec,
                    contentDescription = category,
                    tint = tintColor,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        IconVisualStyle.SQUIRCLE -> {
            val stampShape = RoundedCornerShape(13.dp)
            Box(
                modifier = modifier
                    .size(size)
                    .clip(stampShape)
                    .background(tintColor.copy(alpha = 0.90f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVec,
                    contentDescription = category,
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
