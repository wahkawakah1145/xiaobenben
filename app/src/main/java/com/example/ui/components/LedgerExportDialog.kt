package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.example.ui.theme.AppIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.LedgerExportFormat
import com.example.data.LedgerExportManager
import com.example.model.CurrencyCatalog
import com.example.model.LedgerTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LedgerExportDialog(
    scopeTitle: String,
    transactions: List<LedgerTransaction>,
    baseCurrencyCode: String = "CNY",
    ratesToCny: Map<String, Double> = CurrencyCatalog.defaultRatesToCny,
    bookName: String? = null,
    onDismiss: () -> Unit,
    onExportFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedFormat by remember { mutableStateOf(LedgerExportFormat.XLSX) }
    var includeAnalysis by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(LedgerExportFormat.CSV.mimeType)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isExporting = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        LedgerExportManager.exportToStream(
                            outputStream = out,
                            format = LedgerExportFormat.CSV,
                            transactions = transactions,
                            scopeTitle = scopeTitle,
                            baseCurrencyCode = baseCurrencyCode,
                            ratesToCny = ratesToCny,
                            includeAnalysisText = includeAnalysis,
                            bookName = bookName
                        )
                    } ?: error("无法打开目标写入流")
                }
            }
            isExporting = false
            result
                .onSuccess { res ->
                    val suffix = if (res.includedAnalysis) "（含收支分析文本）" else ""
                    onExportFeedback("已导出 ${res.exportedCount} 笔账单为 CSV$suffix")
                    onDismiss()
                }
                .onFailure { err ->
                    onExportFeedback("导出 CSV 失败：${err.localizedMessage ?: "请重试"}")
                }
        }
    }

    val xlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(LedgerExportFormat.XLSX.mimeType)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isExporting = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        LedgerExportManager.exportToStream(
                            outputStream = out,
                            format = LedgerExportFormat.XLSX,
                            transactions = transactions,
                            scopeTitle = scopeTitle,
                            baseCurrencyCode = baseCurrencyCode,
                            ratesToCny = ratesToCny,
                            includeAnalysisText = includeAnalysis,
                            bookName = bookName
                        )
                    } ?: error("无法打开目标写入流")
                }
            }
            isExporting = false
            result
                .onSuccess { res ->
                    val suffix = if (res.includedAnalysis) "（含独立「收支分析」工作表）" else ""
                    onExportFeedback("已导出 ${res.exportedCount} 笔账单为 XLSX$suffix")
                    onDismiss()
                }
                .onFailure { err ->
                    onExportFeedback("导出 XLSX 失败：${err.localizedMessage ?: "请重试"}")
                }
        }
    }

    val jsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(LedgerExportFormat.JSON.mimeType)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isExporting = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        LedgerExportManager.exportToStream(
                            outputStream = out,
                            format = LedgerExportFormat.JSON,
                            transactions = transactions,
                            scopeTitle = scopeTitle,
                            baseCurrencyCode = baseCurrencyCode,
                            ratesToCny = ratesToCny,
                            includeAnalysisText = includeAnalysis,
                            bookName = bookName
                        )
                    } ?: error("无法打开目标写入流")
                }
            }
            isExporting = false
            result
                .onSuccess { res ->
                    val suffix = if (res.includedAnalysis) "（含结构化分析与摘要文本）" else ""
                    onExportFeedback("已导出 ${res.exportedCount} 笔账单为 JSON$suffix")
                    onDismiss()
                }
                .onFailure { err ->
                    onExportFeedback("导出 JSON 失败：${err.localizedMessage ?: "请重试"}")
                }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        icon = { Icon(AppIcons.Download, contentDescription = null) },
        title = { Text("导出账单数据") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            AppIcons.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = scopeTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "共 ${transactions.size} 笔账单 · 折算基准币种 $baseCurrencyCode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                Text(
                    text = "选择导出文件格式",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                LedgerExportFormat.entries.forEach { fmt ->
                    val selected = selectedFormat == fmt
                    Surface(
                        onClick = { if (!isExporting) selectedFormat = fmt },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_format_${fmt.extension}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { if (!isExporting) selectedFormat = fmt }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fmt.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = fmt.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "附带收支分析文本",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = when (selectedFormat) {
                                    LedgerExportFormat.CSV -> "在表格顶部附加收支概况、分类与渠道分布摘要"
                                    LedgerExportFormat.XLSX -> "额外生成「收支分析」工作表，含统计表与纯文本摘要"
                                    LedgerExportFormat.JSON -> "附加 analysis 对象与纯文本 plainText 摘要"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = includeAnalysis,
                            onCheckedChange = { if (!isExporting) includeAnalysis = it },
                            modifier = Modifier.testTag("export_include_analysis_switch")
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.48f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_privacy_notice_card")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "隐私与安全提示",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "导出的 CSV / XLSX / JSON 文件为明文未加密格式，包含金额、商户、卡尾号、单号与备注等个人财务敏感信息，请妥善保管，切勿随意上传至公开网盘或陌生群聊。如需长期归档，建议优先使用「本地备份」生成加密 ZIP 备份包。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.92f)
                            )
                        }
                    }
                }

                if (transactions.isEmpty()) {
                    Text(
                        text = "当前范围内没有可导出的账单记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val fileName = LedgerExportManager.suggestedFileName(scopeTitle, selectedFormat)
                    when (selectedFormat) {
                        LedgerExportFormat.CSV -> csvLauncher.launch(fileName)
                        LedgerExportFormat.XLSX -> xlsxLauncher.launch(fileName)
                        LedgerExportFormat.JSON -> jsonLauncher.launch(fileName)
                    }
                },
                enabled = !isExporting && transactions.isNotEmpty(),
                modifier = Modifier.testTag("export_confirm_button")
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导出中...")
                } else {
                    Text("选择保存位置并导出")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isExporting,
                modifier = Modifier.testTag("export_cancel_button")
            ) {
                Text("取消")
            }
        }
    )
}
