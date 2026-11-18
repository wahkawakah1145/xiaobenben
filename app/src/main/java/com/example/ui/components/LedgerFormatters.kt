package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.model.LedgerTransaction
import com.example.model.ParseMethod
import com.example.model.TransactionType
import com.example.ui.theme.AlipayBlue
import com.example.ui.theme.BankAmber
import com.example.ui.theme.BankCrimson
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TransferCobalt
import com.example.ui.theme.UnionPayBlue
import com.example.ui.theme.WechatGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

fun formatYuan(cents: Long, showSign: Boolean = false, type: TransactionType? = null): String {
    val absYuan = "%.2f".format(kotlin.math.abs(cents) / 100.0)
    if (!showSign || type == null) return "¥$absYuan"
    return when (type) {
        TransactionType.EXPENSE -> "-¥$absYuan"
        TransactionType.INCOME -> "+¥$absYuan"
        TransactionType.TRANSFER -> "¥$absYuan"
    }
}

fun formatTimeShort(millis: Long): String {
    return SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(millis))
}

fun formatDateHeader(millis: Long): String {
    val dateFmt = SimpleDateFormat("MM月dd日 EEEE", Locale.CHINA)
    return dateFmt.format(Date(millis))
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
        "餐饮美食" -> Icons.Rounded.Restaurant
        "交通出行" -> Icons.Rounded.DirectionsCar
        "购物消费" -> Icons.Rounded.ShoppingBag
        "居家生活" -> Icons.Rounded.Home
        "休闲娱乐" -> Icons.Rounded.Movie
        "医疗健康" -> Icons.Rounded.LocalHospital
        "工资奖金" -> Icons.Rounded.Payments
        "理财收益" -> Icons.Rounded.Savings
        "退款返现" -> Icons.Rounded.ReceiptLong
        "转账红包", "转账收款" -> Icons.Rounded.CardGiftcard
        else -> Icons.Rounded.AccountBalance
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
                    imageVector = Icons.Rounded.AutoAwesome,
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
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                Text(
                    text = formatYuan(transaction.amountCents, showSign = true, type = transaction.txType),
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = transactionTypeColor(transaction.txType),
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChannelBadge(channel = transaction.channel)
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

            if (!transaction.note.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "识别备注",
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
                            text = "原始通知文本",
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
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
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
    onDismiss: () -> Unit,
    onSave: (
        amountCents: Long,
        type: TransactionType,
        category: String,
        merchant: String,
        channel: String,
        cardLastFour: String?,
        note: String?
    ) -> Unit
) {
    var selectedType by remember {
        mutableStateOf(initialTransaction?.txType ?: TransactionType.EXPENSE)
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
    var validationError by remember { mutableStateOf<String?>(null) }

    val availableCategories = when (selectedType) {
        TransactionType.INCOME -> CategoryCatalog.incomeCategories
        TransactionType.EXPENSE -> CategoryCatalog.expenseCategories
        TransactionType.TRANSFER -> listOf("转账红包", "其他")
    }

    val quickChannels = listOf("支付宝", "微信支付", "云闪付", "招商银行", "工商银行", "建设银行", "手动记账")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialTransaction == null) "手动记一笔" else "编辑账单")
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

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; validationError = null },
                    label = { Text("金额 (人民币元)") },
                    prefix = { Text("¥") },
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
                        onValueChange = { note = it.take(100) },
                        label = { Text("备注 (可选)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
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
                    val cents = (yuan * 100.0).roundToLong().coerceAtLeast(1L)
                    onSave(
                        cents,
                        selectedType,
                        category,
                        merchant.trim().ifBlank { category },
                        channel.trim().ifBlank { "手动记账" },
                        cardTail.trim().ifBlank { null },
                        note.trim().ifBlank { null }
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
}
