package com.example.model

import java.util.UUID

enum class TransactionType(val label: String) {
    EXPENSE("支出"),
    INCOME("收入"),
    TRANSFER("转账");

    companion object {
        fun fromRaw(raw: String?): TransactionType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: EXPENSE
    }
}

enum class SourceApp(val label: String) {
    ALIPAY("支付宝"),
    WECHAT("微信支付"),
    UNIONPAY("云闪付"),
    BANK_APP("银行App"),
    BANK_SMS("银行短信"),
    MANUAL("手动记账"),
    LOCAL_BENCH("识别测试台");

    companion object {
        fun fromRaw(raw: String?): SourceApp =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MANUAL
    }
}

enum class ParseMethod(val label: String) {
    RULE_ENGINE("规则匹配"),
    LOCAL_NLP("本地语义"),
    MANUAL("手动录入");

    companion object {
        fun fromRaw(raw: String?): ParseMethod =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MANUAL
    }
}

data class LedgerTransaction(
    val id: String = UUID.randomUUID().toString(),
    val amountCents: Long = 0L,
    val type: String = TransactionType.EXPENSE.name,
    val category: String = "其他",
    val merchant: String = "",
    val channel: String = "支付宝",
    val cardLastFour: String? = null,
    val sourceApp: String = SourceApp.MANUAL.name,
    val parseMethod: String = ParseMethod.MANUAL.name,
    val rawNotification: String? = null,
    val note: String? = null,
    val occurredAtMillis: Long = System.currentTimeMillis(),
    val confirmed: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val txType: TransactionType get() = TransactionType.fromRaw(type)
    val txSourceApp: SourceApp get() = SourceApp.fromRaw(sourceApp)
    val txParseMethod: ParseMethod get() = ParseMethod.fromRaw(parseMethod)
}

data class CustomParsingRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val packageFilter: String = "*",
    val keywordPattern: String = "",
    val channelName: String = "",
    val defaultCategory: String = "其他",
    val defaultType: String = TransactionType.EXPENSE.name,
    val enabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

data class BudgetSettings(
    val monthlyBudgetCents: Long = 500_000L,
    val autoRecordEnabled: Boolean = true,
    val smartSemanticEnabled: Boolean = true,
    val notifyOnRecorded: Boolean = true
)

data class WebDavConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val remoteFolder: String = "ZhiJiZhang",
    val lastSyncMillis: Long = 0L,
    val lastSyncStatus: String = ""
)

data class ParsedNotificationResult(
    val isFinancialTransaction: Boolean,
    val amountCents: Long,
    val type: TransactionType,
    val category: String,
    val merchant: String,
    val channel: String,
    val cardLastFour: String?,
    val sourceApp: SourceApp,
    val parseMethod: ParseMethod,
    val confidence: Float,
    val reasoning: String
)

data class CapturedNotificationLog(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val content: String,
    val capturedAtMillis: Long,
    val status: String,
    val summary: String
)

data class ChannelCatalogItem(
    val id: String,
    val name: String,
    val shortCode: String,
    val categoryLabel: String,
    val packageNames: List<String>,
    val smsCodes: List<String>,
    val sampleNotificationTitle: String,
    val sampleNotificationBody: String
)

object CategoryCatalog {
    val expenseCategories = listOf(
        "餐饮美食",
        "交通出行",
        "购物消费",
        "居家生活",
        "休闲娱乐",
        "医疗健康",
        "转账红包",
        "其他"
    )

    val incomeCategories = listOf(
        "工资奖金",
        "理财收益",
        "退款返现",
        "转账收款",
        "其他"
    )

    val allCategories = (expenseCategories + incomeCategories).distinct()
}
