package com.example.parser

import com.example.model.CustomParsingRule
import com.example.model.ParseMethod
import com.example.model.ParsedNotificationResult
import com.example.model.TransactionType
import kotlin.math.roundToLong

object ChinaNotificationParser {

    private val noiseKeywords = listOf(
        "验证码", "动态密码", "校验码", "登录提醒", "异地登录",
        "密码修改", "好友请求", "群聊消息", "语音通话", "视频通话",
        "快递已签收", "专属福利", "免息分期邀约", "账单分期提醒", "验证码为"
    )

    private val financialTriggerKeywords = listOf(
        "付款", "消费", "支出", "扣款", "支付", "刷卡", "支取", "转出", "缴费", "代扣",
        "收款", "入账", "到账", "转入", "工资", "退款", "返现", "收益", "红包",
        "转账", "还款", "提现", "动账", "交易", "人民币", "RMB", "USD", "HKD", "EUR",
        "JPY", "GBP", "美元", "港币", "日元", "欧元", "英镑", "¥", "￥", "$", "€", "£", "元"
    )

    private val incomeKeywords = listOf(
        "收款", "入账", "到账", "转入", "工资", "薪资", "奖金", "退款", "返现", "收益", "分红", "报销", "收入", "退回"
    )

    private val transferKeywords = listOf(
        "转账", "还款", "提现", "零钱通转入", "余额宝转入", "信用卡还款", "资金归集"
    )

    private val expenseKeywords = listOf(
        "付款", "消费", "支出", "扣款", "支付", "刷卡", "支取", "转出", "缴费", "代扣", "扣费", "买入", "下单"
    )

    private val amountPatterns = listOf(
        Regex("""(?:人民币|RMB|CNY|USD|HKD|EUR|JPY|GBP|SGD|KRW|AUD|CAD|MOP|TWD|美元|港币|港元|欧元|日元|英镑|新币|韩元|澳元|加元|澳门元|新台币|HK\$|US\$|S\$|A\$|C\$|NT\$|MOP\$|[¥￥$€£₩])\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:金额|付款|消费|支出|扣款|支付|入账|到账|收款|转入|转出|支取|转账|还款)[为:：]?\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s*(?:元|美元|港币|港元|日元|欧元|英镑|円)?"""),
        Regex("""([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s*(?:元|美元|港币|港元|日元|欧元|英镑|円|USD|HKD|EUR|JPY|GBP)""", RegexOption.IGNORE_CASE)
    )

    private val cardTailPatterns = listOf(
        Regex("""尾号\s*(\d{4})"""),
        Regex("""账户\s*(\d{4})"""),
        Regex("""[卡号]\s*[（(]?(\d{4})[）)]?"""),
        Regex("""(\d{4})\s*卡""")
    )

    private val merchantPatterns = listOf(
        Regex("""(?:向|给)\s*[「"“]?([^「」"”\n，。,\s]{2,24})[」"”]?\s*(?:付款|转账|支付)"""),
        Regex("""在\s*([^，。,\n\s]{2,28}?)\s*(?:刷卡|快捷|扫码|线上)?(?:消费|支出|付款|支付)"""),
        Regex("""(?:商户|交易商户|收款方|付款方|对方|交易场所)[:：]\s*([^，。,\n]{2,28})"""),
        Regex("""[（(]([^（）()\n]{2,24}(?:消费|店|超市|外卖|医院|地铁|出行|收益到账|代扣|商城))[）)]""")
    )

    fun isLikelyFinancialNotification(packageName: String, title: String, content: String): Boolean {
        val combined = "$title $content"
        if (combined.isBlank()) return false
        if (noiseKeywords.any { combined.contains(it) }) return false

        val hasFinancialCue = financialTriggerKeywords.count { combined.contains(it, ignoreCase = true) } >= 2
        val hasAmount = extractAmountCents(combined) != null

        return if (ChinaPaymentCatalog.isWatchedPackage(packageName)) {
            hasAmount && (hasFinancialCue || combined.contains("元") || combined.contains("¥") ||
                combined.contains("￥") || combined.contains("$") || combined.contains("€") || combined.contains("£"))
        } else {
            hasAmount && hasFinancialCue
        }
    }

    fun hasUncertainFinancialCue(packageName: String, title: String, content: String): Boolean {
        val combined = "$title $content"
        if (combined.isBlank()) return false
        if (noiseKeywords.any { combined.contains(it) }) return false
        val cueCount = financialTriggerKeywords.count { combined.contains(it, ignoreCase = true) }
        return if (ChinaPaymentCatalog.isWatchedPackage(packageName)) {
            cueCount >= 1
        } else {
            cueCount >= 2
        }
    }

    fun parse(
        packageName: String,
        title: String,
        content: String,
        customRules: List<CustomParsingRule> = emptyList(),
        enableLocalSemanticPass: Boolean = true
    ): ParsedNotificationResult? {
        val combined = "$title\n$content".trim()
        if (combined.isBlank()) return null
        if (noiseKeywords.any { combined.contains(it) }) return null

        val amountCents = extractAmountCents(combined) ?: return null
        val currencyCode = detectCurrencyCode(combined)

        val matchedCustomRule = customRules
            .filter { it.enabled }
            .sortedWith(
                compareByDescending<CustomParsingRule> { it.priority }
                    .thenByDescending { it.keywordPattern.length }
            )
            .firstOrNull { rule ->
                (rule.packageFilter == "*" || rule.packageFilter == packageName) &&
                    rule.keywordPattern.isNotBlank() &&
                    runCatching { Regex(rule.keywordPattern, RegexOption.IGNORE_CASE).containsMatchIn(combined) }
                        .getOrElse { combined.contains(rule.keywordPattern, ignoreCase = true) }
            }

        val sourceApp = ChinaPaymentCatalog.resolveSourceApp(packageName, combined)
        val channel = matchedCustomRule?.channelName?.takeIf { it.isNotBlank() }
            ?: detectChannel(packageName, combined)
        val type = if (matchedCustomRule != null && !matchedCustomRule.defaultType.equals("AUTO", ignoreCase = true)) {
            TransactionType.fromRaw(matchedCustomRule.defaultType)
        } else {
            detectTransactionType(combined)
        }
        val cardLastFour = extractCardLastFour(combined)
        val extractedMerchant = extractMerchant(combined, channel, type, enableLocalSemanticPass)
        val merchant = matchedCustomRule?.merchantOverride?.trim()?.takeIf { it.isNotBlank() }
            ?: extractedMerchant

        val hasExplicitRuleCategory = matchedCustomRule != null &&
            matchedCustomRule.defaultCategory.isNotBlank() &&
            matchedCustomRule.defaultCategory != "自动" &&
            matchedCustomRule.defaultCategory != "其他"
        val category = if (hasExplicitRuleCategory) {
            matchedCustomRule!!.defaultCategory
        } else {
            inferCategory(merchant, combined, type)
        }

        val isHighPriorityCustomMatch = matchedCustomRule != null &&
            (matchedCustomRule.priority > 0 || hasExplicitRuleCategory)

        val usedSemanticHeuristics = enableLocalSemanticPass &&
            !isHighPriorityCustomMatch &&
            (merchant != "${channel}消费" && channel != "未知渠道")

        val parseMethod = when {
            isHighPriorityCustomMatch -> ParseMethod.RULE_ENGINE
            usedSemanticHeuristics -> ParseMethod.LOCAL_NLP
            else -> ParseMethod.RULE_ENGINE
        }

        val hasGenericMerchant = merchant == "${channel}消费" ||
            merchant == "${channel}收款" ||
            merchant == "${channel}转账"

        val confidence = when {
            isHighPriorityCustomMatch -> 0.96f
            channel != "未知渠道" && !hasGenericMerchant && category != "其他" -> 0.92f
            channel != "未知渠道" && category != "其他" -> 0.88f
            channel != "未知渠道" -> 0.78f
            else -> 0.62f
        }

        val uncertaintyReasons = buildList {
            if (channel == "未知渠道") add("未知渠道")
            if (category == "其他" && type != TransactionType.TRANSFER) add("未能确定分类")
            if (hasGenericMerchant && !isHighPriorityCustomMatch) add("未提取到明确商户")
        }
        val needsInquiry = !isHighPriorityCustomMatch &&
            (confidence < 0.85f || channel == "未知渠道" || (category == "其他" && hasGenericMerchant))

        return ParsedNotificationResult(
            isFinancialTransaction = true,
            amountCents = amountCents,
            currencyCode = currencyCode,
            type = type,
            category = category,
            merchant = merchant,
            channel = channel,
            cardLastFour = cardLastFour,
            sourceApp = sourceApp,
            parseMethod = parseMethod,
            confidence = confidence,
            reasoning = if (matchedCustomRule != null) {
                "命中规则「${matchedCustomRule.name}」"
            } else {
                "识别渠道「$channel」· 分类「$category」· 币种 $currencyCode"
            },
            needsInquiry = needsInquiry,
            inquiryReason = if (needsInquiry) {
                uncertaintyReasons.distinct().joinToString("、").ifBlank { "格式不够明确，需确认" }
            } else ""
        )
    }

    fun suggestRuleKeyword(
        title: String,
        content: String,
        merchant: String,
        channel: String
    ): String {
        val cleanMerchant = merchant.trim()
        if (cleanMerchant.length >= 2 &&
            !cleanMerchant.endsWith("消费") &&
            !cleanMerchant.endsWith("收款") &&
            !cleanMerchant.endsWith("转账") &&
            cleanMerchant != "未知商户"
        ) {
            return cleanMerchant.take(20)
        }
        val bracketed = Regex("""[【\[「"“]([^】\]」"”]{2,16})[】\]」"”]""").find("$title $content")
            ?.groupValues?.get(1)?.trim()
        if (!bracketed.isNullOrBlank()) return bracketed
        if (channel.isNotBlank() && channel != "未知渠道") return channel
        val cleanTitle = title.trim().replace(Regex("""\s+"""), "")
        if (cleanTitle.length >= 2) return cleanTitle.take(16)
        return content.trim().take(12)
    }

    fun detectCurrencyCode(text: String): String {
        val sanitized = text
            .replace(Regex("""(?:可用余额|当前余额|账户余额|交易后余额|余额|可用额度)[为:：]?\s*(?:人民币|RMB|CNY|USD|HKD|EUR|JPY|GBP|[¥￥$€£])?\s*[0-9,.]+(?:元|美元|港币|日元|欧元|英镑)?"""), "")
        val target = sanitized.ifBlank { text }

        return when {
            target.contains("HKD", ignoreCase = true) || target.contains("HK$") ||
                target.contains("港币") || target.contains("港元") -> "HKD"
            target.contains("MOP", ignoreCase = true) || target.contains("澳门元") -> "MOP"
            target.contains("TWD", ignoreCase = true) || target.contains("NT$") ||
                target.contains("新台币") || target.contains("台币") -> "TWD"
            target.contains("SGD", ignoreCase = true) || target.contains("S$") ||
                target.contains("新加坡元") || target.contains("新币") -> "SGD"
            target.contains("AUD", ignoreCase = true) || target.contains("A$") ||
                target.contains("澳元") -> "AUD"
            target.contains("CAD", ignoreCase = true) || target.contains("C$") ||
                target.contains("加元") -> "CAD"
            target.contains("USD", ignoreCase = true) || target.contains("US$") ||
                target.contains("美元") || target.contains("美金") ||
                Regex("""\$\s*[0-9]""").containsMatchIn(target) -> "USD"
            target.contains("EUR", ignoreCase = true) || target.contains("欧元") ||
                target.contains("€") -> "EUR"
            target.contains("GBP", ignoreCase = true) || target.contains("英镑") ||
                target.contains("£") -> "GBP"
            target.contains("JPY", ignoreCase = true) || target.contains("日元") ||
                target.contains("円") -> "JPY"
            target.contains("KRW", ignoreCase = true) || target.contains("韩元") ||
                target.contains("₩") -> "KRW"
            else -> "CNY"
        }
    }

    fun extractAmountCents(text: String): Long? {
        val sanitized = text
            .replace(Regex("""(?:可用余额|当前余额|账户余额|交易后余额|余额|可用额度)[为:：]?\s*(?:人民币|RMB|CNY|USD|HKD|EUR|JPY|GBP|[¥￥$€£])?\s*[0-9,.]+(?:元|美元|港币|日元|欧元|英镑)?"""), "")
        val target = sanitized.ifBlank { text }

        for (pattern in amountPatterns) {
            val match = pattern.find(target)
            if (match != null) {
                val rawNum = match.groupValues[1].replace(",", "")
                val yuan = rawNum.toDoubleOrNull()
                if (yuan != null && yuan > 0.0 && yuan <= 10_000_000.0) {
                    return (yuan * 100.0).roundToLong()
                }
            }
        }
        return null
    }

    private fun detectChannel(packageName: String, text: String): String {
        ChinaPaymentCatalog.watchedPackageMap[packageName]?.let { return it.name }

        for (item in ChinaPaymentCatalog.channels) {
            if (text.contains(item.name) || item.smsCodes.any { text.contains(it) }) {
                return item.name
            }
        }

        return when {
            text.contains("花呗") || text.contains("余额宝") || text.contains("支付宝") -> "支付宝"
            text.contains("零钱通") || text.contains("微信") -> "微信支付"
            text.contains("银联") || text.contains("云闪付") -> "云闪付"
            text.contains("网商银行") -> "网商银行"
            text.contains("微众银行") -> "微众银行"
            text.contains("北京银行") -> "北京银行"
            text.contains("上海银行") -> "上海银行"
            text.contains("华夏银行") -> "华夏银行"
            text.contains("信用卡") || text.contains("借记卡") -> "银行卡"
            else -> "未知渠道"
        }
    }

    private fun detectTransactionType(text: String): TransactionType {
        val hasIncome = incomeKeywords.any { text.contains(it) }
        val hasExpense = expenseKeywords.any { text.contains(it) }
        val hasTransfer = transferKeywords.any { text.contains(it) }

        return when {
            hasIncome && !hasExpense -> TransactionType.INCOME
            hasTransfer && !text.contains("消费") && !text.contains("向") -> TransactionType.TRANSFER
            hasExpense -> TransactionType.EXPENSE
            hasIncome -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }
    }

    private fun extractCardLastFour(text: String): String? {
        for (pattern in cardTailPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    private fun extractMerchant(
        text: String,
        channel: String,
        type: TransactionType,
        enableSemanticPass: Boolean
    ): String {
        for (pattern in merchantPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val candidate = match.groupValues[1]
                    .trim()
                    .removeSuffix("消费")
                    .removeSuffix("支出")
                    .trim()
                if (candidate.length >= 2 && candidate != channel) {
                    return candidate.take(36)
                }
            }
        }
        if (enableSemanticPass) {
            val bracketMatch = Regex("""[「"“【]([^」"”】]{2,20})[」"”】]""").findAll(text)
                .map { it.groupValues[1].trim() }
                .firstOrNull { !it.contains("银行") && it != channel && !it.contains("通知") }
            if (bracketMatch != null) return bracketMatch
        }
        return when {
            text.contains("工资") || text.contains("薪资") -> "工资发放"
            text.contains("理财") && text.contains("收益") -> "理财收益"
            text.contains("退款") -> "${channel}退款"
            type == TransactionType.INCOME -> "${channel}收款"
            type == TransactionType.TRANSFER -> "${channel}转账"
            else -> "${channel}消费"
        }
    }

    fun inferCategory(merchant: String, text: String, type: TransactionType): String {
        val combined = "$merchant $text"
        if (type == TransactionType.INCOME) {
            return when {
                combined.contains("工资") || combined.contains("薪资") || combined.contains("奖金") -> "工资奖金"
                combined.contains("理财") || combined.contains("收益") || combined.contains("利息") || combined.contains("分红") -> "理财收益"
                combined.contains("退款") || combined.contains("返现") -> "退款返现"
                else -> "转账收款"
            }
        }
        if (type == TransactionType.TRANSFER) return "转账红包"

        return when {
            listOf("星巴克", "瑞幸", "喜茶", "奈雪", "麦当劳", "肯德基", "美团", "饿了么", "外卖", "餐饮", "火锅", "海底捞", "咖啡", "奶茶", "烧烤", "面馆", "食堂", "霸王茶姬", "蜜雪")
                .any { combined.contains(it, ignoreCase = true) } -> "餐饮美食"

            listOf("滴滴", "高德", "打车", "地铁", "公交", "铁路", "12306", "航旅", "机票", "加油", "中石化", "中石油", "停车", "ETC", "高速", "单车", "哈啰")
                .any { combined.contains(it, ignoreCase = true) } -> "交通出行"

            listOf("京东", "淘宝", "天猫", "拼多多", "超市", "永辉", "盒马", "山姆", "华润万家", "叮咚", "便利店", "7-11", "全家", "罗森", "商城", "百货", "优衣库", "苹果", "Apple", "Amazon", "抖音商城")
                .any { combined.contains(it, ignoreCase = true) } -> "购物消费"

            listOf("电网", "水费", "燃气", "物业", "话费", "宽带", "移动", "联通", "电信", "房租", "供暖", "电费")
                .any { combined.contains(it, ignoreCase = true) } -> "居家生活"

            listOf("电影", "影城", "万达", "网易云", "腾讯视频", "爱奇艺", "B站", "游戏", "Steam", "Netflix", "Spotify", "ChatGPT", "KTV", "健身", "旅游", "酒店", "携程", "去哪儿")
                .any { combined.contains(it, ignoreCase = true) } -> "休闲娱乐"

            listOf("医院", "药房", "药店", "门诊", "体检", "挂号", "牙科", "医疗", "大药房")
                .any { combined.contains(it, ignoreCase = true) } -> "医疗健康"

            listOf("红包", "转账", "亲属卡")
                .any { combined.contains(it, ignoreCase = true) } -> "转账红包"

            else -> "其他"
        }
    }
}
