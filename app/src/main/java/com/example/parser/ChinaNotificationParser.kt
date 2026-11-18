package com.example.parser

import com.example.model.CustomParsingRule
import com.example.model.NumberFormatStyle
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
        "收款", "入账", "到账", "转入", "存入", "工资", "退款", "返现", "收益", "红包",
        "转账", "还款", "提现", "动账", "交易", "人民币", "RMB", "USD", "HKD", "EUR",
        "JPY", "GBP", "美元", "港币", "日元", "欧元", "英镑", "¥", "￥", "$", "€", "£", "元"
    )

    private val refundKeywords = listOf(
        "退款", "退回", "退货", "返现", "冲正", "已退款", "退还"
    )

    private val incomeKeywords = listOf(
        "收入", "入账", "到账", "存入", "转入", "工资", "薪资", "奖金", "发薪", "报销",
        "收益", "分红", "利息", "收到来自", "已收款", "成功收款", "收款", "贷记", "向你转账"
    )

    private val transferKeywords = listOf(
        "信用卡还款", "自动还款", "零钱通转入", "余额宝转入", "资金归集", "账户互转", "提现", "转账"
    )

    private val expenseKeywords = listOf(
        "支出", "扣款", "扣费", "代扣", "刷卡", "消费", "付款", "已支付", "成功支付",
        "支付", "支取", "转出", "缴费", "买入", "下单", "借记", "汇出"
    )

    private val amountNumberRegexToken =
        """([0-9]{1,3}(?:,[0-9]{3})+(?:\.[0-9]{1,2})?|[0-9]{1,3}(?:\s[0-9]{3})+(?:[.,][0-9]{1,2})?|[0-9]{1,3}(?:\.[0-9]{3})+,[0-9]{1,2}|[0-9]+(?:[.,][0-9]{1,2})?)"""

    private val amountPatterns = listOf(
        Regex("""(?:人民币|RMB|CNY|USD|HKD|EUR|JPY|GBP|SGD|KRW|AUD|CAD|MOP|TWD|美元|港币|港元|欧元|日元|英镑|新币|韩元|澳元|加元|澳门元|新台币|HK\$|US\$|S\$|A\$|C\$|NT\$|MOP\$|[¥￥$€£₩])\s*[+\-]?\s*$amountNumberRegexToken""", RegexOption.IGNORE_CASE),
        Regex("""(?:金额|付款|消费|支出|扣款|扣费|支付|入账|到账|存入|收款|转入|转出|支取|转账|还款)[为:：]?\s*[+\-]?\s*$amountNumberRegexToken\s*(?:元|美元|港币|港元|日元|欧元|英镑|円)?"""),
        Regex("""$amountNumberRegexToken\s*(?:元|美元|港币|港元|日元|欧元|英镑|円|USD|HKD|EUR|JPY|GBP)""", RegexOption.IGNORE_CASE)
    )

    private val standaloneNumberRegex = Regex("""(?<![0-9a-zA-Z.:/])$amountNumberRegexToken(?![0-9a-zA-Z:/])""")

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

        val matchedCustomRule = customRules
            .filter { it.enabled }
            .sortedWith(
                compareByDescending<CustomParsingRule> { it.priority }
                    .thenByDescending { it.keywordPattern.length }
            )
            .firstOrNull { rule ->
                (rule.packageFilter == "*" || rule.packageFilter.isBlank() || rule.packageFilter == packageName) &&
                    rule.keywordPattern.isNotBlank() &&
                    runCatching { Regex(rule.keywordPattern, RegexOption.IGNORE_CASE).containsMatchIn(combined) }
                        .getOrElse { combined.contains(rule.keywordPattern, ignoreCase = true) }
            }

        val ruleNumberStyle = NumberFormatStyle.fromRaw(matchedCustomRule?.numberFormatPattern)
        val allowLoose = matchedCustomRule != null && matchedCustomRule.priority > 0
        val amountCents = extractAmountCents(
            text = combined,
            numberStyle = ruleNumberStyle,
            allowLooseStandaloneNumber = allowLoose
        ) ?: return null
        val currencyCode = detectCurrencyCode(combined)

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

    fun extractKeywordCandidates(
        title: String,
        content: String,
        merchant: String = "",
        channel: String = ""
    ): List<String> {
        val candidates = LinkedHashSet<String>()
        val combined = "$title $content".trim()

        val cleanMerchant = merchant.trim()
        if (cleanMerchant.length >= 2 &&
            !cleanMerchant.endsWith("消费") &&
            !cleanMerchant.endsWith("收款") &&
            !cleanMerchant.endsWith("转账") &&
            cleanMerchant != "未知商户"
        ) {
            candidates.add(cleanMerchant.take(20))
        }

        Regex("""[【\[「"“]([^】\]」"”]{2,18})[】\]」"”]""").findAll(combined).forEach { match ->
            val token = match.groupValues[1].trim()
            if (token.length >= 2 && !token.all { it.isDigit() }) {
                candidates.add(token)
            }
        }

        if (channel.isNotBlank() && channel != "未知渠道") {
            candidates.add(channel)
        }

        val actionTokens = listOf(
            "消费", "扣款", "付款", "支出", "刷卡", "收款", "入账", "到账",
            "退款", "退回", "返现", "工资", "转入", "转出", "转账", "还款", "代扣"
        )
        actionTokens.forEach { token ->
            if (combined.contains(token)) candidates.add(token)
        }

        val stripped = combined
            .replace(Regex("""\d{1,2}月\d{1,2}日"""), " ")
            .replace(Regex("""\d{1,2}:\d{2}(?::\d{2})?"""), " ")
            .replace(Regex("""(?:尾号|账户)\s*\d{4}"""), " ")
            .replace(Regex("""[0-9]{1,3}(?:[,\s.][0-9]{3})*(?:[.,][0-9]{1,2})?\s*(?:元|美元|港币|港元|日元|欧元|英镑|円)?"""), " ")

        stripped.split(Regex("""[\s，。、：:；;!！?？()（）【】\[\]「」"“”/\\|]+"""))
            .map { it.trim() }
            .filter { token ->
                token.length in 2..14 &&
                    !token.all { ch -> ch.isDigit() || ch == '.' || ch == ',' } &&
                    token !in listOf("您账户", "您尾号", "可用余额", "人民币", "明细", "提醒", "通知")
            }
            .take(8)
            .forEach { candidates.add(it) }

        return candidates.take(10).toList()
    }

    fun suggestRuleKeyword(
        title: String,
        content: String,
        merchant: String,
        channel: String
    ): String {
        return extractKeywordCandidates(title, content, merchant, channel).firstOrNull()
            ?: title.trim().replace(Regex("""\s+"""), "").take(16).ifBlank { content.trim().take(12) }
    }

    fun detectNumberFormatStyle(text: String): NumberFormatStyle {
        val cleaned = stripBalanceAndDates(text)
        return when {
            Regex("""\d{1,3}(?:\.\d{3})+,\d{1,2}""").containsMatchIn(cleaned) ||
                Regex("""(?<![\d.,])\d+,\d{2}(?!\d)""").containsMatchIn(cleaned) ->
                NumberFormatStyle.DOT_THOUSANDS_COMMA
            Regex("""\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?""").containsMatchIn(cleaned) ->
                NumberFormatStyle.COMMA_THOUSANDS_DOT
            else -> NumberFormatStyle.AUTO
        }
    }

    fun parseNormalizedAmountCents(
        rawNumber: String,
        style: NumberFormatStyle = NumberFormatStyle.AUTO
    ): Long? {
        val compact = rawNumber.trim().replace(" ", "")
        if (compact.isEmpty()) return null

        val normalizedDoubleStr = when (style) {
            NumberFormatStyle.DOT_THOUSANDS_COMMA -> {
                if (compact.contains(",")) {
                    compact.replace(".", "").replace(",", ".")
                } else {
                    compact
                }
            }
            NumberFormatStyle.PLAIN_INTEGER -> {
                compact.replace(",", "").replace(".", "")
            }
            NumberFormatStyle.AUTO, NumberFormatStyle.COMMA_THOUSANDS_DOT -> {
                when {
                    // Explicit European dot thousands + comma decimal e.g. 2.111,10
                    Regex("""^\d{1,3}(?:\.\d{3})+,\d{1,2}$""").matches(compact) ->
                        compact.replace(".", "").replace(",", ".")
                    // Pure comma decimal e.g. 2111,10 (comma not followed by 3 digits)
                    Regex("""^\d+,\d{1,2}$""").matches(compact) ->
                        compact.replace(",", ".")
                    // Standard 2,111.10 or 2111.10 or 2,111 -> identical after removing grouping commas
                    else -> compact.replace(",", "")
                }
            }
        }

        val value = normalizedDoubleStr.toDoubleOrNull() ?: return null
        if (value <= 0.0 || value > 10_000_000.0) return null
        return (value * 100.0).roundToLong()
    }

    fun detectCurrencyCode(text: String): String {
        val target = stripBalanceAndDates(text).ifBlank { text }

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

    fun extractAmountCents(
        text: String,
        numberStyle: NumberFormatStyle = NumberFormatStyle.AUTO,
        allowLooseStandaloneNumber: Boolean = false
    ): Long? {
        val target = stripBalanceAndDates(text).ifBlank { text }

        for (pattern in amountPatterns) {
            val match = pattern.find(target)
            if (match != null) {
                val cents = parseNormalizedAmountCents(match.groupValues[1], numberStyle)
                if (cents != null) return cents
            }
        }

        if (allowLooseStandaloneNumber) {
            val sanitizedForLoose = target
                .replace(Regex("""(?:尾号|账户|卡号)\s*\d{4}"""), " ")
                .replace(Regex("""\d{4}\s*卡"""), " ")
            standaloneNumberRegex.findAll(sanitizedForLoose).forEach { match ->
                val raw = match.groupValues[1]
                // Avoid matching bare 4-digit year/card-tail unless it has decimal or grouping separator
                val hasFormatting = raw.contains('.') || raw.contains(',') || raw.contains(' ')
                if (hasFormatting || raw.length != 4) {
                    val cents = parseNormalizedAmountCents(raw, numberStyle)
                    if (cents != null) return cents
                }
            }
        }
        return null
    }

    private fun stripBalanceAndDates(text: String): String {
        return text
            .replace(Regex("""(?:可用余额|当前余额|账户余额|交易后余额|余额|可用额度)[为:：]?\s*(?:人民币|RMB|CNY|USD|HKD|EUR|JPY|GBP|[¥￥$€£])?\s*[0-9,.\s]+(?:元|美元|港币|日元|欧元|英镑)?"""), " ")
            .replace(Regex("""\d{4}[-/年]\d{1,2}[-/月]\d{1,2}日?"""), " ")
            .replace(Regex("""\d{1,2}月\d{1,2}日"""), " ")
            .replace(Regex("""\d{1,2}:\d{2}(?::\d{2})?"""), " ")
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

    fun detectTransactionType(text: String): TransactionType {
        // Strip brand/descriptor words that misleadingly contain "支付" or "收款"
        val cleaned = text
            .replace("微信支付", " ")
            .replace("支付宝", " ")
            .replace("云闪付", " ")
            .replace("支付方式", " ")
            .replace("付款方式", " ")
            .replace("收款方", " ")
            .replace("收款人", " ")
            .replace("收款商户", " ")
            .replace("消费金融", " ")
            .replace("消费积分", " ")

        val hasPositiveSign = Regex("""(?:^|[\s:：])\+ ?(?:人民币|RMB|USD|HKD|EUR|JPY|GBP|[¥￥$€£])?\s*\d""").containsMatchIn(cleaned)
        val hasNegativeSign = Regex("""(?:^|[\s:：])- ?(?:人民币|RMB|USD|HKD|EUR|JPY|GBP|[¥￥$€£])?\s*\d""").containsMatchIn(cleaned)
        if (hasPositiveSign && !hasNegativeSign) return TransactionType.INCOME

        // Refund / reversal takes precedence even when mentioning original consumption
        if (refundKeywords.any { cleaned.contains(it) }) {
            return TransactionType.INCOME
        }

        val firstIncomeIdx = incomeKeywords
            .mapNotNull { kw -> cleaned.indexOf(kw).takeIf { it >= 0 }?.let { it to kw } }
            .minByOrNull { it.first }
        val firstExpenseIdx = expenseKeywords
            .mapNotNull { kw -> cleaned.indexOf(kw).takeIf { it >= 0 }?.let { it to kw } }
            .minByOrNull { it.first }
        val hasTransferCue = transferKeywords.any { cleaned.contains(it) }

        return when {
            firstIncomeIdx != null && firstExpenseIdx == null -> TransactionType.INCOME
            firstExpenseIdx != null && firstIncomeIdx == null -> {
                if (hasTransferCue && !cleaned.contains("消费") && !cleaned.contains("向") && !cleaned.contains("扣款")) {
                    TransactionType.TRANSFER
                } else {
                    TransactionType.EXPENSE
                }
            }
            firstIncomeIdx != null && firstExpenseIdx != null -> {
                // Strong income nouns like 工资 / 薪资 / 到账 / 入账 / 收入 beat passive mentions
                val strongIncome = listOf("工资", "薪资", "奖金", "收入", "入账", "到账", "存入", "转入", "报销", "收到来自")
                    .any { cleaned.contains(it) }
                val strongExpense = listOf("支出", "消费", "扣款", "扣费", "刷卡", "向.*付款".toRegex().pattern)
                    .any { cleaned.contains(it) || Regex("""向.{1,20}付款""").containsMatchIn(cleaned) }
                when {
                    strongIncome && !strongExpense -> TransactionType.INCOME
                    strongExpense && !strongIncome -> TransactionType.EXPENSE
                    firstIncomeIdx.first < firstExpenseIdx.first -> TransactionType.INCOME
                    else -> TransactionType.EXPENSE
                }
            }
            hasNegativeSign -> TransactionType.EXPENSE
            hasTransferCue -> TransactionType.TRANSFER
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
            text.contains("退款") || text.contains("退回") -> "${channel}退款"
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
                combined.contains("退款") || combined.contains("退回") || combined.contains("返现") -> "退款返现"
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

