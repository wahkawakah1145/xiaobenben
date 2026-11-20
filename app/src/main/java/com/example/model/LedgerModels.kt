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
    IMPORT("手动导入"),
    LOCAL_BENCH("识别测试");

    companion object {
        fun fromRaw(raw: String?): SourceApp =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MANUAL
    }
}

enum class ParseMethod(val label: String) {
    RULE_ENGINE("规则匹配"),
    LOCAL_NLP("AI理解"),
    MANUAL("手动录入"),
    IMPORT("导入");

    companion object {
        fun fromRaw(raw: String?): ParseMethod =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MANUAL
    }
}

data class CurrencyInfo(
    val code: String,
    val symbol: String,
    val chineseName: String,
    val defaultRateToCny: Double,
    val zeroDecimalDisplay: Boolean = false
)

object CurrencyCatalog {
    val currencies = listOf(
        CurrencyInfo("CNY", "¥", "人民币", 1.0),
        CurrencyInfo("USD", "$", "美元", 7.24),
        CurrencyInfo("HKD", "HK$", "港币", 0.93),
        CurrencyInfo("EUR", "€", "欧元", 7.82),
        CurrencyInfo("JPY", "JP¥", "日元", 0.047, zeroDecimalDisplay = true),
        CurrencyInfo("GBP", "£", "英镑", 9.21),
        CurrencyInfo("SGD", "S$", "新加坡元", 5.42),
        CurrencyInfo("KRW", "₩", "韩元", 0.0052, zeroDecimalDisplay = true),
        CurrencyInfo("AUD", "A$", "澳元", 4.72),
        CurrencyInfo("CAD", "C$", "加元", 5.18),
        CurrencyInfo("MOP", "MOP$", "澳门元", 0.90),
        CurrencyInfo("TWD", "NT$", "新台币", 0.225)
    )

    val byCode: Map<String, CurrencyInfo> = currencies.associateBy { it.code }

    val defaultRatesToCny: Map<String, Double> = currencies.associate { it.code to it.defaultRateToCny }

    fun resolve(code: String?): CurrencyInfo =
        byCode[code?.uppercase()] ?: currencies.first()
}

data class ExchangeRateState(
    val ratesToCny: Map<String, Double> = CurrencyCatalog.defaultRatesToCny,
    val updatedAtMillis: Long = 0L,
    val lastStatus: String = "",
    val dataSource: String = "内置参考汇率（初始基准）",
    val rateDateText: String = ""
)

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色");

    companion object {
        fun fromRaw(raw: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SYSTEM
    }
}

enum class LatinNumberFont(val label: String) {
    BUILTIN("内置字体"),
    SYSTEM("系统默认");

    companion object {
        fun fromRaw(raw: String?): LatinNumberFont =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BUILTIN
    }
}

enum class CardDensity(val label: String) {
    COMPACT("紧凑"),
    STANDARD("标准"),
    COMFORTABLE("舒展");

    companion object {
        fun fromRaw(raw: String?): CardDensity =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: STANDARD
    }
}

enum class HomeHeaderOrder(val label: String) {
    TIPS_TOP("Tips 置顶"),
    SUMMARY_TOP("汇总置顶"),
    HIDE_TIPS("隐藏 Tips");

    companion object {
        fun fromRaw(raw: String?): HomeHeaderOrder =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: TIPS_TOP
    }
}

enum class FabPlacement(val label: String) {
    BOTTOM_END("右下角"),
    BOTTOM_START("左下角");

    companion object {
        fun fromRaw(raw: String?): FabPlacement =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BOTTOM_END
    }
}

enum class ExchangeRateAutoUpdateFrequency(
    val label: String,
    val intervalHours: Int
) {
    MANUAL("仅手动更新", 0),
    EVERY_6_HOURS("每 6 小时", 6),
    DAILY("每天", 24),
    EVERY_3_DAYS("每 3 天", 72),
    WEEKLY("每周", 168);

    companion object {
        fun fromRaw(raw: String?): ExchangeRateAutoUpdateFrequency =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MANUAL
    }
}

enum class AiProviderMode(val label: String, val description: String) {
    DISABLED("关闭", "仅使用内置规则与本地基础正则语义"),
    LOCAL_SMALL_MODEL("本地开源小模型", "按需下载开源中文轻量模型词表与配置，离线推理"),
    ONLINE_API("在线 API 接口", "连接自定义 OpenAI 兼容在线大模型接口")
    ;

    companion object {
        fun fromRaw(raw: String?): AiProviderMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DISABLED
    }
}

data class LocalModelFileSpec(
    val fileName: String,
    val repoPath: String
)

data class LocalModelPreset(
    val id: String,
    val name: String,
    val repoId: String,
    val license: String,
    val sizeHint: String,
    val summary: String,
    val files: List<LocalModelFileSpec>
)

data class AiConfig(
    val providerMode: AiProviderMode = AiProviderMode.DISABLED,
    val localModelId: String = "bge-small-zh-v15",
    val localModelMirror: String = "https://hf-mirror.com",
    val apiBaseUrl: String = "",
    val apiKey: String = "",
    val apiModelName: String = "",
    val useAiAsFallbackOnly: Boolean = true
)

enum class TabClickAnimationStyle(val label: String) {
    SLIDE("平滑滑动"),
    FADE("淡入渐隐"),
    NONE("无动画");

    companion object {
        fun fromRaw(raw: String?): TabClickAnimationStyle =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SLIDE
    }
}

enum class PredictiveBackStyle(val label: String, val description: String) {
    SYSTEM_SCALE("缩放退场（推荐）", "前页按手势比例缩小并淡出，贴合系统预测性返回"),
    SLIDE_RIGHT("右滑退场", "前页向右滑出，上一级菜单从左侧柔滑复原"),
    FADE("轻柔淡出", "仅执行柔和透明度渐变过渡");

    companion object {
        fun fromRaw(raw: String?): PredictiveBackStyle =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SYSTEM_SCALE
    }
}

enum class CustomPaletteStyle(val label: String, val description: String) {
    TONAL_SPOT("Tonal Spot（经典色调）", "柔和平衡的 Material You 主次协调配色"),
    EXPRESSIVE("Expressive（鲜明表现）", "互补高活力对比配色，视觉张力更强"),
    NEUTRAL("Neutral（沉稳中性）", "低饱和灰阶质感，专注数据与阅读"),
    VIBRANT("Vibrant（高饱和）", "高亮纯正色阶，色彩更加饱满"),
    MONOCHROME("Monochrome（极简黑白）", "纯粹无彩单色调，极简克制");

    companion object {
        fun fromRaw(raw: String?): CustomPaletteStyle =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: TONAL_SPOT
    }
}

enum class AppIconStyle(
    val id: String,
    val label: String,
    val description: String
) {
    EMERALD("emerald", "翠绿经典", "经典绿水青山小本本图标"),
    MINIMAL("minimal", "极简白金", "瑞士扁平几何票据徽章"),
    INK("ink", "水墨青峦", "宋韵淡彩山水金湾意境"),
    CYBER("cyber", "棱镜深眸", "深色玻璃拟态全息霓虹"),
    VINTAGE("vintage", "复古皮本", "暖纸棕革铜扣手账风"),
    MONET_ADAPTIVE("monet", "莫奈徽标", "随当前主题主色实时染色适配");

    companion object {
        fun fromId(id: String?): AppIconStyle =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: EMERALD
    }
}

enum class IconVisualStyle(
    val label: String,
    val description: String
) {
    FILLED("柔彩圆底", "柔和纯色圆底搭配核心图标"),
    OUTLINED("极简线框", "通透细线圆环搭配轻盈图标"),
    DUOTONE("双色渐变", "双色调微渐变圆角徽章"),
    SQUIRCLE("方圆印章", "润角方章底纹与高对比色调");

    companion object {
        fun fromRaw(raw: String?): IconVisualStyle =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: FILLED
    }
}

data class CustomLocalModelInfo(
    val id: String,
    val displayName: String,
    val fileName: String,
    val formatLabel: String,
    val sizeBytes: Long,
    val importedAtMillis: Long,
    val vocabTokenCount: Int = 0
)

enum class AnalyticsGranularity(val label: String) {
    YEAR("年度"),
    MONTH("月"),
    WEEK("周"),
    DAY("日")
}

data class AppearanceSettings(
    val dynamicColor: Boolean = true,
    val customPrimaryColorArgb: Long = 0xFF0F6B54L,
    val customPaletteStyle: CustomPaletteStyle = CustomPaletteStyle.TONAL_SPOT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val latinNumberFont: LatinNumberFont = LatinNumberFont.BUILTIN,
    val uiDensityScale: Float = 1.0f,
    val cardDensity: CardDensity = CardDensity.STANDARD,
    val homeHeaderOrder: HomeHeaderOrder = HomeHeaderOrder.TIPS_TOP,
    val fabPlacement: FabPlacement = FabPlacement.BOTTOM_END,
    val showChannelBadge: Boolean = true,
    val showParseBadge: Boolean = true,
    val tipTagText: String = "",
    val customTipsRaw: String = "",
    val tabClickAnimation: TabClickAnimationStyle = TabClickAnimationStyle.SLIDE,
    val predictiveBackEnabled: Boolean = true,
    val predictiveBackStyle: PredictiveBackStyle = PredictiveBackStyle.SYSTEM_SCALE,
    val homeBrandShowIcon: Boolean = true,
    val homeBrandShowTitle: Boolean = true,
    val homeBrandCustomName: String = "",
    val homeBrandCustomSubtitle: String = "",
    val homeBrandIconSizeDp: Int = 34,
    val homeBrandIconCornerDp: Int = 10,
    val homeBrandIconSpacingDp: Int = 10,
    val homeBrandTitleSizeSp: Int = 24,
    val homeBrandTitleBold: Boolean = true,
    val homeBrandSubtitleSizeSp: Int = 11,
    val analyticsPieSizeDp: Int = 220,
    val analyticsPullCalendarDistanceDp: Int = 128,
    val appIconStyleId: String = AppIconStyle.EMERALD.id,
    val iconVisualStyle: IconVisualStyle = IconVisualStyle.FILLED
)

data class StorageFootprintInfo(
    val databaseBytes: Long = 0L,
    val walBytes: Long = 0L,
    val preferencesBytes: Long = 0L,
    val aiModelBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val transactionCount: Int = 0,
    val rawNotificationCount: Int = 0,
    val inquiryCount: Int = 0
) {
    val totalUserDataBytes: Long get() = databaseBytes + walBytes + preferencesBytes + aiModelBytes + cacheBytes
}

data class StorageOptimizeResult(
    val beforeBytes: Long,
    val afterBytes: Long,
    val prunedRawLogs: Int,
    val prunedInquiries: Int,
    val clearedCacheBytes: Long
)

enum class NumberFormatStyle(val label: String, val example: String) {
    AUTO("自动等价识别", "2111.10 与 2,111.10 等价"),
    COMMA_THOUSANDS_DOT("逗号千分位", "2,111.10 / 2111.10"),
    DOT_THOUSANDS_COMMA("欧式逗号小数", "2.111,10 / 2111,10"),
    PLAIN_INTEGER("无小数整数", "2111 / 2,111");

    companion object {
        fun fromRaw(raw: String?): NumberFormatStyle =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AUTO
    }
}

data class LedgerTransaction(
    val id: String = UUID.randomUUID().toString(),
    val amountCents: Long = 0L,
    val currencyCode: String = "CNY",
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
    val recordedBaseCurrencyCode: String = "CNY",
    val recordedRateToBase: Double = 0.0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val txType: TransactionType get() = TransactionType.fromRaw(type)
    val txSourceApp: SourceApp get() = SourceApp.fromRaw(sourceApp)
    val txParseMethod: ParseMethod get() = ParseMethod.fromRaw(parseMethod)
    val currencyInfo: CurrencyInfo get() = CurrencyCatalog.resolve(currencyCode)
}

data class CustomParsingRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val packageFilter: String = "*",
    val keywordPattern: String = "",
    val templateSignature: String = "",
    val channelName: String = "",
    val defaultCategory: String = "自动",
    val defaultType: String = "AUTO",
    val merchantOverride: String = "",
    val numberFormatPattern: String = NumberFormatStyle.AUTO.name,
    val priority: Int = 10,
    val enabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val isIgnoreRule: Boolean get() = defaultType.equals("IGNORE", ignoreCase = true)
    val isTemplateRule: Boolean get() = templateSignature.isNotBlank() && !isIgnoreRule
}

data class NotificationInquiry(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String = "",
    val appLabel: String = "",
    val title: String = "",
    val content: String = "",
    val suggestedAmountCents: Long = 0L,
    val suggestedCurrencyCode: String = "CNY",
    val suggestedType: String = TransactionType.EXPENSE.name,
    val suggestedCategory: String = "其他",
    val suggestedMerchant: String = "",
    val suggestedChannel: String = "未知渠道",
    val cardLastFour: String? = null,
    val reason: String = "",
    val occurredAtMillis: Long = System.currentTimeMillis(),
    val createdAtMillis: Long = System.currentTimeMillis()
)

enum class AnalyticsChartStyle(val label: String) {
    PIE("饼图"),
    BAR("柱状图")
}

data class BudgetSettings(
    val monthlyBudgetCents: Long = 500_000L,
    val autoRecordEnabled: Boolean = true,
    val requireManualConfirm: Boolean = false,
    val smartSemanticEnabled: Boolean = true,
    val notifyOnRecorded: Boolean = true,
    val baseCurrencyCode: String = "CNY",
    val rootKeepAliveEnabled: Boolean = false,
    val backupReminderEnabled: Boolean = false,
    val backupReminderIntervalHours: Int = 168,
    val lastLocalBackupMillis: Long = 0L,
    val lastBackupReminderMillis: Long = 0L,
    val exchangeRateUpdateFrequency: String = ExchangeRateAutoUpdateFrequency.MANUAL.name
)

data class WebDavConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val remoteFolder: String = "XiaoBenBen",
    val lastSyncMillis: Long = 0L,
    val lastSyncStatus: String = ""
)

data class ParsedNotificationResult(
    val isFinancialTransaction: Boolean,
    val amountCents: Long,
    val currencyCode: String = "CNY",
    val type: TransactionType,
    val category: String,
    val merchant: String,
    val channel: String,
    val cardLastFour: String?,
    val sourceApp: SourceApp,
    val parseMethod: ParseMethod,
    val confidence: Float,
    val reasoning: String,
    val needsInquiry: Boolean = false,
    val inquiryReason: String = "",
    val occurredAtMillis: Long? = null
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
