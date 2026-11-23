package com.example.parser

import com.example.model.ChannelCatalog
import com.example.model.CustomParsingRule
import com.example.model.NumberFormatStyle
import com.example.model.ParseMethod
import com.example.model.ParsedNotificationResult
import com.example.model.TransactionType
import java.util.Calendar
import kotlin.math.roundToLong

object ChinaNotificationParser {

    private val noiseKeywords = listOf(
        "验证码", "动态密码", "校验码", "登录提醒", "异地登录",
        "密码修改", "好友请求", "群聊消息", "语音通话", "视频通话",
        "快递已签收", "专属福利", "免息分期邀约", "账单分期提醒", "验证码为"
    )

    private val financialTriggerKeywords = listOf(
        "付款", "消费", "支出", "扣款", "扣费", "支付", "刷卡", "支取", "转出", "缴费", "代扣",
        "收款", "入账", "到账", "转入", "存入", "工资", "退款", "返现", "收益", "红包",
        "转账", "还款", "提现", "动账", "交易", "金额", "人民币", "RMB", "CNY", "USD", "HKD", "EUR",
        "JPY", "GBP", "SGD", "AUD", "CAD", "KRW", "THB", "MYR", "TWD", "MOP", "CHF", "NZD",
        "美元", "美金", "港币", "港元", "日元", "日圆", "欧元", "英镑", "新加坡元", "新币",
        "澳元", "澳币", "加元", "加币", "韩元", "泰铢", "新台币", "台币", "澳门元", "澳门币",
        "瑞士法郎", "瑞郎", "林吉特", "马币", "新西兰元", "纽元", "卢布", "迪拉姆",
        "¥", "￥", "$", "€", "£", "₩", "元"
    )

    private val refundKeywords = listOf(
        "退款", "退回", "退货", "返现", "冲正", "已退款", "退还"
    )

    private val incomeKeywords = listOf(
        "收入", "入账", "到账", "存入", "转入", "工资", "薪资", "奖金", "发薪", "报销",
        "收益", "分红", "利息", "收到来自", "已收款", "成功收款", "收款", "贷记", "向你转账"
    )

    private val selfTransferAndWithdrawKeywords = listOf(
        "零钱提现", "余额提现", "微信提现", "支付宝提现", "提现到银行卡", "提现至银行卡",
        "提现到账", "提现成功", "提现申请", "提现至", "提现到",
        "零钱充值", "充值到零钱", "零钱通转入", "零钱通转出", "转入零钱通", "转出零钱通",
        "余额宝转入", "余额宝转出", "转入余额宝", "转出余额宝",
        "本人转账", "向本人转账", "转给本人", "本人账户", "本人名下", "本人银行卡",
        "同名账户", "同名转账", "自己转账", "自转", "账户互转", "内部转账",
        "资金归集", "信用卡还款", "自动还款", "还信用卡", "不计收支", "中性交易"
    )

    private val selfTransferAndWithdrawRegexes = listOf(
        Regex("""(?:微信|支付宝|零钱|余额).{0,6}提现"""),
        Regex("""提现.{0,8}(?:到账|成功|银行卡|储蓄卡|尾号|至|到)"""),
        Regex("""(?:本人|同名|自己).{0,6}(?:转账|转入|转出|汇款|账户|银行卡)"""),
        Regex("""(?:零钱通|余额宝|朝朝宝).{0,6}(?:转入|转出|申购|赎回)""")
    )

    private val transferKeywords = listOf(
        "信用卡还款", "自动还款", "零钱通转入", "余额宝转入", "资金归集", "账户互转", "提现", "转账"
    )

    fun isSelfTransferOrWithdraw(text: String): Boolean {
        if (text.isBlank()) return false
        val hasRefund = refundKeywords.any { text.contains(it) }
        if (hasRefund && !text.contains("提现") && !text.contains("本人") && !text.contains("同名")) {
            return false
        }
        return selfTransferAndWithdrawKeywords.any { text.contains(it) } ||
            selfTransferAndWithdrawRegexes.any { it.containsMatchIn(text) }
    }

    private val expenseKeywords = listOf(
        "支出", "扣款", "扣费", "代扣", "刷卡", "消费", "付款", "已支付", "成功支付",
        "支付", "支取", "转出", "缴费", "买入", "下单", "借记", "汇出"
    )

    private val amountNumberRegexToken =
        """([0-9]{1,3}(?:,[0-9]{3})+(?:\.[0-9]{1,2})?|[0-9]{1,3}(?:\s[0-9]{3})+(?:[.,][0-9]{1,2})?|[0-9]{1,3}(?:\.[0-9]{3})+,[0-9]{1,2}|[0-9]+(?:[.,][0-9]{1,2})?)"""

    // Ordered longest-first so multi-char Chinese currency names (e.g. 新加坡元, 人民币, 新台币) match before generic 元
    private val prefixCurrencyUnitToken =
        """(?:人民币|新加坡元|新西兰元|瑞士法郎|菲律宾比索|墨西哥比索|阿根廷比索|沙特里亚尔|卡塔尔里亚尔|土耳其里拉|印度卢比|南非兰特|瑞典克朗|挪威克朗|丹麦克朗|捷克克朗|新台币|澳门元|澳门币|林吉特|迪拉姆|印尼盾|越南盾|兹罗提|雷亚尔|美元|美金|港币|港元|欧元|日元|日圆|英镑|新币|韩元|澳元|澳币|加元|加币|纽元|台币|瑞郎|泰铢|马币|越盾|卢布|福林|谢克尔|MOP\$|HK\$|US\$|NZ\$|MX\$|NT\$|S\$|A\$|C\$|(?<![a-zA-Z])(?:RMB|CNY|USD|HKD|EUR|JPY|GBP|SGD|KRW|AUD|CAD|MOP|TWD|CHF|NZD|THB|MYR|IDR|VND|PHP|INR|RUB|BRL|MXN|ZAR|TRY|SEK|NOK|DKK|PLN|CZK|HUF|AED|SAR|QAR|ILS|ARS|RM)(?![a-zA-Z])|[¥￥$€£₩฿₫₱₹₽₺₪]|zł|Kč)"""

    private val suffixCurrencyUnitToken =
        """(?:新加坡元|新西兰元|瑞士法郎|菲律宾比索|墨西哥比索|阿根廷比索|沙特里亚尔|卡塔尔里亚尔|土耳其里拉|印度卢比|南非兰特|瑞典克朗|挪威克朗|丹麦克朗|捷克克朗|人民币|新台币|澳门元|澳门币|林吉特|迪拉姆|印尼盾|越南盾|兹罗提|雷亚尔|块钱|美元|美金|港币|港元|欧元|日元|日圆|英镑|新币|韩元|澳元|澳币|加元|加币|纽元|台币|瑞郎|泰铢|马币|越盾|卢布|福林|谢克尔|元|块(?![板模区])|圆|円|(?<![a-zA-Z])(?:CNY|RMB|USD|HKD|EUR|JPY|GBP|SGD|KRW|AUD|CAD|MOP|TWD|CHF|NZD|THB|MYR|IDR|VND|PHP|INR|RUB|BRL|MXN|ZAR|TRY|SEK|NOK|DKK|PLN|CZK|HUF|AED|SAR|QAR|ILS|ARS)(?![a-zA-Z0-9]))"""

    private val prefixUnitAmountRegex = Regex(
        """($prefixCurrencyUnitToken)\s*[+\-]?\s*$amountNumberRegexToken(?:\s*(?:元|圆))?""",
        RegexOption.IGNORE_CASE
    )

    private val suffixUnitAmountRegex = Regex(
        """(?<![0-9a-zA-Z.:/])$amountNumberRegexToken\s*($suffixCurrencyUnitToken)""",
        RegexOption.IGNORE_CASE
    )

    private val cueAmountRegex = Regex(
        """(?:交易金额|消费金额|支出金额|付款金额|实付金额|实扣金额|扣款金额|入账金额|收款金额|转账金额|金额|实付|实扣|付款|消费|支出|扣款|扣费|支付|入账|到账|存入|收款|转入|转出|支取|转账|还款)[为是:：\s]*[+\-]?\s*$amountNumberRegexToken(?:\s*($suffixCurrencyUnitToken))?""",
        RegexOption.IGNORE_CASE
    )

    private val explicitUnitAmountPatterns = listOf(
        prefixUnitAmountRegex,
        suffixUnitAmountRegex
    )

    private val nonAmountNoiseRegexes = listOf(
        Regex("""[\[\【(（]\s*\d+\s*条[^\]\】)）]{0,12}[\]\】)）]"""),
        Regex("""(?<!\d)\d+\s*(?:条新?消息|条未读|条通知|条短信|个联系人|人发来|条回复)"""),
        Regex("""(?:中国移动|中国联通|中国电信|移动|联通|电信|客服热线|客服|热线)?\s*(?:10086|10010|10000|10001|106\d{4,}|123\d{2})\b"""),
        Regex("""(?:验证码|校验码|动态码|确认码)[为是:：\s]*\d{4,8}"""),
        Regex("""(?:尾号|账户|卡号)\s*[（(]?\d{4}[）)]?"""),
        Regex("""(?<!\d)\d+(?:\.\d+)?\s*(?:分钟|小时|秒钟|秒|GB|MB|KB|TB|Mbps|Gbps|%|‰|分期|积分|分贝)(?![a-zA-Z0-9])""", RegexOption.IGNORE_CASE)
    )

    private val standaloneNumberRegex = Regex("""(?<![0-9a-zA-Z.:/])$amountNumberRegexToken(?![0-9a-zA-Z:/])""")

    data class ExtractedAmountMention(
        val amountCents: Long,
        val currencyCode: String,
        val hasExplicitUnit: Boolean,
        val hasSpecificCurrency: Boolean,
        val startIndex: Int,
        val endIndex: Int,
        val score: Int
    )

    private val secondaryAmountCues = listOf(
        "已冻结", "预授权冻结", "预冻结", "冻结金额", "冻结",
        "可用余额", "当前余额", "账户余额", "交易后余额", "卡内余额", "活期余额", "余额",
        "可用额度", "剩余额度", "信用额度", "当前额度", "额度",
        "折合人民币", "约合人民币", "合人民币", "折合", "约合", "参考汇率", "汇率",
        "手续费", "服务费", "运费险",
        "优惠立减", "立减", "满减", "优惠", "抵扣", "红包抵扣", "积分抵扣", "减免",
        "原价", "应付", "限额", "最低还款", "本期应还", "账单总额", "总计应还"
    )

    private val strongPrimaryAmountCues = listOf(
        "实际支出", "实际支付", "实际扣款", "实付", "实扣", "净支出", "最终支付"
    )

    private val primaryTransactionCues = listOf(
        "无卡消费", "刷卡消费", "快捷支付", "扫码支付", "消费交易", "消费支出", "消费",
        "支出", "扣款", "扣费", "代扣", "付款", "已支付", "成功支付", "支付", "刷卡", "支取", "转出", "缴费",
        "入账", "到账", "收入", "存入", "收款", "转入", "工资", "退款", "退回", "返现", "提现", "转账", "还款"
    )

    private val amountLabelCues = listOf(
        "交易金额", "消费金额", "支出金额", "付款金额", "入账金额", "收款金额", "金额"
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
            hasAmount && (hasFinancialCue || hasExplicitAmountUnit(combined))
        } else {
            hasAmount && hasFinancialCue
        }
    }

    fun hasUncertainFinancialCue(
        packageName: String,
        title: String,
        content: String,
        treatAsWatched: Boolean = false
    ): Boolean {
        val combined = "$title $content"
        if (combined.isBlank()) return false
        if (noiseKeywords.any { combined.contains(it) }) return false
        val cueCount = financialTriggerKeywords.count { combined.contains(it, ignoreCase = true) }
        return if (treatAsWatched || ChinaPaymentCatalog.isWatchedPackage(packageName)) {
            cueCount >= 1
        } else {
            cueCount >= 2
        }
    }

    fun buildNotificationTemplateSignature(
        rawText: String,
        keepMerchantSlotExact: Boolean = false
    ): String {
        var normalized = rawText
            .replace("\r\n", " ")
            .replace('\n', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (normalized.isEmpty()) return ""

        // Normalize variable slots so recurring bank/payment templates share one signature
        normalized = normalized
            .replace(Regex("""\d{4}\s*[-/年.]\s*\d{1,2}\s*[-/月.]\s*\d{1,2}日?"""), "<DATE>")
            .replace(Regex("""(?<!\d)\d{1,2}\s*[月/-]\s*\d{1,2}日?"""), "<DATE>")
            .replace(Regex("""(?<!\d)\d{1,2}[:时]\d{2}(?:[:分]\d{2})?"""), "<TIME>")
            .replace(Regex("""(尾号|账户|卡号)\s*[（(]?\d{4}[）)]?"""), "$1<TAIL>")
            .replace(Regex("""[¥￥$€£₩]\s*[+\-]?\s*[0-9,.\s]+"""), "<AMT>")
            .replace(Regex("""[+\-]?\s*[0-9]{1,3}(?:[,\s.][0-9]{3})*(?:[.,][0-9]{1,2})?\s*(?:元|美元|港币|港元|日元|欧元|英镑|新币|韩元|澳元|加元|澳门元|新台币|人民币|USD|HKD|EUR|JPY|GBP)""", RegexOption.IGNORE_CASE), "<AMT>")
            .replace(Regex("""\d{4,}"""), "<NUM>")
            .replace(Regex("""[+\-]?\d+(?:[.,]\d{1,2})?"""), "<AMT>")

        if (!keepMerchantSlotExact) {
            normalized = normalized
                .replace(Regex("""(在\s*)[^，。,\n\s]{2,24}?(\s*(?:刷卡|快捷|扫码|线上)?(?:消费|支出|付款|支付))"""), "$1<MERCHANT>$2")
                .replace(Regex("""((?:向|给)\s*)[「"“]?[^「」"”\n，。,\s]{2,24}[」"”]?(\s*(?:付款|转账|支付))"""), "$1<MERCHANT>$2")
                .replace(Regex("""((?:商户|交易商户|收款方|付款方|对方)[:：]\s*)[^，。,\n]{2,24}"""), "$1<MERCHANT>")
        }

        return normalized.replace(Regex("""\s+"""), " ").trim().take(180)
    }

    fun matchesIgnoreRule(
        packageName: String,
        title: String,
        content: String,
        customRules: List<CustomParsingRule>
    ): CustomParsingRule? {
        val combined = "$title\n$content".trim()
        if (combined.isBlank()) return null
        val matched = findMatchingCustomRule(
            packageName = packageName,
            combined = combined,
            rules = customRules.filter { it.enabled && it.isIgnoreRule },
            contentOnly = content.trim()
        )
        return matched
    }

    fun isConcreteChannelName(name: String?): Boolean {
        val trimmed = name?.trim().orEmpty()
        return trimmed.isNotEmpty() &&
            trimmed != "自动" &&
            trimmed != "未知渠道" &&
            trimmed != "忽略规则"
    }

    fun matchesRuleKeyword(rawPattern: String, text: String): Boolean {
        val normalized = rawPattern.trim()
            .replace('｜', '|')
            .trim('|')
            .trim()
        if (normalized.isEmpty() || text.isBlank()) return false

        val regexMatched = runCatching {
            Regex(normalized, RegexOption.IGNORE_CASE).containsMatchIn(text)
        }.getOrElse { false }
        if (regexMatched) return true

        val tokens = normalized
            .split(Regex("""[|,，、;；]+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return tokens.any { text.contains(it, ignoreCase = true) }
    }

    fun resolveChannelFromRules(
        packageName: String,
        combined: String,
        rules: List<CustomParsingRule>,
        contentOnly: String = ""
    ): String? {
        if (rules.isEmpty() || combined.isBlank()) return null
        val enabledConcreteRules = rules.filter { rule ->
            rule.enabled && !rule.isIgnoreRule && isConcreteChannelName(rule.channelName)
        }
        if (enabledConcreteRules.isEmpty()) return null

        val candidateTexts = listOfNotNull(
            combined.takeIf { it.isNotBlank() },
            contentOnly.takeIf { it.isNotBlank() && it != combined }
        )
        val incomingSigs = candidateTexts.flatMap { text ->
            listOf(
                buildNotificationTemplateSignature(text, keepMerchantSlotExact = false),
                buildNotificationTemplateSignature(text, keepMerchantSlotExact = true)
            )
        }.filter { it.isNotBlank() }.distinct()

        fun matchesRule(rule: CustomParsingRule): Boolean {
            val sig = rule.templateSignature.trim()
            val sigMatch = sig.isNotEmpty() && incomingSigs.any { inc ->
                sig.equals(inc, ignoreCase = true) ||
                    (minOf(sig.length, inc.length) >= 12 &&
                        (sig.endsWith(inc, ignoreCase = true) || inc.endsWith(sig, ignoreCase = true)))
            }
            if (sigMatch) return true
            return rule.keywordPattern.isNotBlank() && matchesRuleKeyword(rule.keywordPattern, combined)
        }

        val sorted = enabledConcreteRules.sortedWith(
            compareByDescending<CustomParsingRule> {
                it.packageFilter == packageName && packageName.isNotBlank()
            }
                .thenByDescending { it.packageFilter == "*" || it.packageFilter.isBlank() }
                .thenByDescending { it.priority }
                .thenByDescending { it.updatedAtMillis }
                .thenByDescending { it.keywordPattern.length }
        )

        // Prefer rules matching current package or wildcard; then allow explicit keyword match even across SMS/test bench
        val exactPackageHit = sorted.firstOrNull { rule ->
            (rule.packageFilter == "*" || rule.packageFilter.isBlank() || rule.packageFilter == packageName) &&
                matchesRule(rule)
        }
        if (exactPackageHit != null) return exactPackageHit.channelName.trim()

        val crossPackageKeywordHit = sorted.firstOrNull { rule ->
            rule.keywordPattern.trim().length >= 2 && matchesRuleKeyword(rule.keywordPattern, combined)
        }
        return crossPackageKeywordHit?.channelName?.trim()
    }

    fun findMatchingCustomRule(
        packageName: String,
        combined: String,
        rules: List<CustomParsingRule>,
        contentOnly: String = ""
    ): CustomParsingRule? {
        val exactEligible = rules.filter { rule ->
            rule.enabled &&
                (rule.packageFilter == "*" || rule.packageFilter.isBlank() || rule.packageFilter == packageName)
        }
        val allowCrossPackageKeywordFallback = packageName.isBlank() || ChinaPaymentCatalog.isSmsPackage(packageName)

        val candidateTexts = listOfNotNull(
            combined.takeIf { it.isNotBlank() },
            contentOnly.takeIf { it.isNotBlank() && it != combined }
        )
        val incomingSigs = candidateTexts.flatMap { text ->
            listOf(
                buildNotificationTemplateSignature(text, keepMerchantSlotExact = false),
                buildNotificationTemplateSignature(text, keepMerchantSlotExact = true)
            )
        }.filter { it.isNotBlank() }.distinct()

        fun findInPool(pool: List<CustomParsingRule>, allowTemplates: Boolean): CustomParsingRule? {
            if (pool.isEmpty()) return null
            if (allowTemplates) {
                val templateMatch = pool
                    .filter { rule ->
                        val sig = rule.templateSignature.trim()
                        sig.isNotEmpty() && incomingSigs.any { inc ->
                            sig.equals(inc, ignoreCase = true) ||
                                (minOf(sig.length, inc.length) >= 12 &&
                                    (sig.endsWith(inc, ignoreCase = true) || inc.endsWith(sig, ignoreCase = true)))
                        }
                    }
                    .sortedWith(
                        compareByDescending<CustomParsingRule> { it.isIgnoreRule }
                            .thenByDescending { it.updatedAtMillis }
                            .thenByDescending { it.priority }
                    )
                    .firstOrNull()
                if (templateMatch != null) return templateMatch
            }

            return pool
                .filter { it.keywordPattern.isNotBlank() }
                .sortedWith(
                    compareByDescending<CustomParsingRule> { it.isIgnoreRule }
                        .thenByDescending { it.priority }
                        .thenByDescending { it.updatedAtMillis }
                        .thenByDescending { it.keywordPattern.length }
                )
                .firstOrNull { rule -> matchesRuleKeyword(rule.keywordPattern, combined) }
        }

        findInPool(exactEligible, allowTemplates = true)?.let { return it }

        if (allowCrossPackageKeywordFallback) {
            val crossPool = rules.filter { rule ->
                rule.enabled &&
                    rule.packageFilter != "*" &&
                    rule.packageFilter.isNotBlank() &&
                    rule.packageFilter != packageName &&
                    rule.keywordPattern.trim().length >= 2
            }
            findInPool(crossPool, allowTemplates = false)?.let { return it }
        }

        return null
    }

    fun parse(
        packageName: String,
        title: String,
        content: String,
        customRules: List<CustomParsingRule> = emptyList(),
        enableLocalSemanticPass: Boolean = false,
        referenceTimeMillis: Long = System.currentTimeMillis(),
        customChannels: List<String> = emptyList()
    ): ParsedNotificationResult? {
        val combined = "$title\n$content".trim()
        if (combined.isBlank()) return null

        val matchedCustomRule = findMatchingCustomRule(
            packageName = packageName,
            combined = combined,
            rules = customRules,
            contentOnly = content.trim()
        )
        if (matchedCustomRule?.isIgnoreRule == true) {
            return ParsedNotificationResult(
                isFinancialTransaction = false,
                amountCents = 0L,
                currencyCode = "CNY",
                type = TransactionType.EXPENSE,
                category = "已忽略",
                merchant = "",
                channel = matchedCustomRule.channelName.ifBlank { "忽略规则" },
                cardLastFour = null,
                sourceApp = ChinaPaymentCatalog.resolveSourceApp(packageName, combined),
                parseMethod = ParseMethod.RULE_ENGINE,
                confidence = 1f,
                reasoning = "命中忽略规则「${matchedCustomRule.name}」，已跳过为非账单通知",
                needsInquiry = false,
                inquiryReason = ""
            )
        }

        if (noiseKeywords.any { combined.contains(it) }) return null

        val ruleNumberStyle = NumberFormatStyle.fromRaw(matchedCustomRule?.numberFormatPattern)
        val allowLoose = matchedCustomRule != null && matchedCustomRule.priority > 0
        val primaryMention = extractPrimaryAmountAndCurrency(
            text = combined,
            numberStyle = ruleNumberStyle,
            allowLooseStandaloneNumber = allowLoose,
            requireExplicitUnit = false
        ) ?: return null
        val amountCents = primaryMention.amountCents
        val currencyCode = if (primaryMention.hasSpecificCurrency) {
            primaryMention.currencyCode
        } else {
            detectCurrencyCode(combined)
        }

        val sourceApp = ChinaPaymentCatalog.resolveSourceApp(packageName, combined)
        val rawChannel = matchedCustomRule?.channelName?.trim()?.takeIf { isConcreteChannelName(it) }
            ?: resolveChannelFromRules(packageName, combined, customRules, content.trim())
            ?: detectChannel(packageName, combined, customChannels)
        val channel = ChannelCatalog.normalizeRecognizedChannel(rawChannel)
        val type = if (matchedCustomRule != null &&
            !matchedCustomRule.defaultType.equals("AUTO", ignoreCase = true) &&
            !matchedCustomRule.isIgnoreRule
        ) {
            TransactionType.fromRaw(matchedCustomRule.defaultType)
        } else {
            detectTransactionType(combined)
        }
        val cardLastFour = extractCardLastFour(combined)
        val extractedMerchant = extractMerchant(combined, channel, type, enableLocalSemanticPass)
        val merchant = matchedCustomRule?.merchantOverride?.trim()?.takeIf { it.isNotBlank() }
            ?: extractedMerchant

        val explicitRuleCategory = matchedCustomRule
            ?.defaultCategory
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "自动" && it != "其他" }
        val hasExplicitRuleCategory = explicitRuleCategory != null
        val category = explicitRuleCategory ?: inferCategory(merchant, combined, type)

        val isHighPriorityCustomMatch = matchedCustomRule != null &&
            (matchedCustomRule.priority > 0 || matchedCustomRule.isTemplateRule || hasExplicitRuleCategory)

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

        val extractedOccurredAt = extractOccurredAtMillis(combined, referenceTimeMillis)

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
                if (matchedCustomRule.isTemplateRule) {
                    "命中格式模版「${matchedCustomRule.name}」"
                } else {
                    "命中规则「${matchedCustomRule.name}」"
                }
            } else {
                "识别渠道「$channel」· 分类「$category」· 币种 $currencyCode"
            },
            needsInquiry = needsInquiry,
            inquiryReason = if (needsInquiry) {
                uncertaintyReasons.distinct().joinToString("、").ifBlank { "格式不够明确，需确认" }
            } else "",
            occurredAtMillis = extractedOccurredAt
        )
    }

    fun extractOccurredAtMillis(
        text: String,
        referenceMillis: Long = System.currentTimeMillis()
    ): Long? {
        if (text.isBlank()) return null
        val refCal = Calendar.getInstance().apply { timeInMillis = referenceMillis }

        // 1. Full date + time: yyyy-MM-dd HH:mm(:ss) / yyyy年M月d日 H时m分 / yyyy/M/d H:mm
        val fullDateTimeRegex = Regex(
            """(\d{4})\s*[-/年.]\s*(\d{1,2})\s*[-/月.]\s*(\d{1,2})日?\s*[T\s,，]?\s*(\d{1,2})[:时](\d{2})(?:[:分](\d{2})秒?)?"""
        )
        fullDateTimeRegex.find(text)?.let { m ->
            val y = m.groupValues[1].toIntOrNull()
            val mon = m.groupValues[2].toIntOrNull()
            val d = m.groupValues[3].toIntOrNull()
            val h = m.groupValues[4].toIntOrNull()
            val min = m.groupValues[5].toIntOrNull()
            val sec = m.groupValues[6].toIntOrNull() ?: 0
            if (y != null && mon in 1..12 && d in 1..31 && h in 0..23 && min in 0..59 && sec in 0..59) {
                return buildCalMillis(y, mon!! - 1, d!!, h!!, min!!, sec)
            }
        }

        // 2. Month + Day + Time (no year): 11月18日 08:30(:00) or 11-18 08:30 or 11/18 08:30
        val monthDayTimeRegex = Regex(
            """(?<!\d)(\d{1,2})\s*[月/-]\s*(\d{1,2})日?\s*(\d{1,2})[:时](\d{2})(?:[:分](\d{2})秒?)?"""
        )
        monthDayTimeRegex.find(text)?.let { m ->
            val mon = m.groupValues[1].toIntOrNull()
            val d = m.groupValues[2].toIntOrNull()
            val h = m.groupValues[3].toIntOrNull()
            val min = m.groupValues[4].toIntOrNull()
            val sec = m.groupValues[5].toIntOrNull() ?: 0
            if (mon in 1..12 && d in 1..31 && h in 0..23 && min in 0..59 && sec in 0..59) {
                val cal = Calendar.getInstance().apply {
                    set(refCal.get(Calendar.YEAR), mon!! - 1, d!!, h!!, min!!, sec)
                    set(Calendar.MILLISECOND, 0)
                }
                if (cal.timeInMillis > referenceMillis + 86_400_000L) {
                    cal.add(Calendar.YEAR, -1)
                }
                return cal.timeInMillis
            }
        }

        // 3. Relative day + time: 今天 / 今日 / 昨天 / 昨日 / 前天 HH:mm
        val relTimeRegex = Regex("""(今天|今日|昨天|昨日|前天)\s*(\d{1,2})[:时](\d{2})(?:[:分](\d{2})秒?)?""")
        relTimeRegex.find(text)?.let { m ->
            val dayOffset = when (m.groupValues[1]) {
                "昨天", "昨日" -> -1
                "前天" -> -2
                else -> 0
            }
            val h = m.groupValues[2].toIntOrNull()
            val min = m.groupValues[3].toIntOrNull()
            val sec = m.groupValues[4].toIntOrNull() ?: 0
            if (h in 0..23 && min in 0..59 && sec in 0..59) {
                val cal = (refCal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                    set(Calendar.HOUR_OF_DAY, h!!)
                    set(Calendar.MINUTE, min!!)
                    set(Calendar.SECOND, sec)
                    set(Calendar.MILLISECOND, 0)
                }
                return cal.timeInMillis
            }
        }

        // 4. Explicit date without time: yyyy-MM-dd / yyyy年MM月dd日
        val fullDateOnlyRegex = Regex("""(\d{4})\s*[-/年.]\s*(\d{1,2})\s*[-/月.]\s*(\d{1,2})日?""")
        fullDateOnlyRegex.find(text)?.let { m ->
            val y = m.groupValues[1].toIntOrNull()
            val mon = m.groupValues[2].toIntOrNull()
            val d = m.groupValues[3].toIntOrNull()
            if (y != null && mon in 1..12 && d in 1..31) {
                val timeOnly = Regex("""(?<!\d)(\d{1,2}):(\d{2})(?::(\d{2}))?(?!\d)""").find(text)
                val h = timeOnly?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..23 }
                    ?: refCal.get(Calendar.HOUR_OF_DAY)
                val min = timeOnly?.groupValues?.get(2)?.toIntOrNull()?.takeIf { it in 0..59 }
                    ?: refCal.get(Calendar.MINUTE)
                val sec = timeOnly?.groupValues?.get(3)?.toIntOrNull()?.takeIf { it in 0..59 } ?: if (timeOnly == null) refCal.get(Calendar.SECOND) else 0
                return buildCalMillis(y, mon!! - 1, d!!, h, min, sec)
            }
        }

        // 5. Standalone time HH:mm(:ss) -> assume reference date, guard midnight delayed notification rollover
        val timeOnlyRegex = Regex("""(?<!\d)(\d{1,2}):(\d{2})(?::(\d{2}))?(?!\d)""")
        timeOnlyRegex.find(text)?.let { m ->
            val h = m.groupValues[1].toIntOrNull()
            val min = m.groupValues[2].toIntOrNull()
            val sec = m.groupValues[3].toIntOrNull() ?: 0
            if (h in 0..23 && min in 0..59 && sec in 0..59) {
                val cal = (refCal.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, h!!)
                    set(Calendar.MINUTE, min!!)
                    set(Calendar.SECOND, sec)
                    set(Calendar.MILLISECOND, 0)
                }
                if (cal.timeInMillis > referenceMillis + 10 * 60_000L) {
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                }
                return cal.timeInMillis
            }
        }

        // 6. Month+day only: 11月18日
        val monthDayOnlyRegex = Regex("""(?<!\d)(\d{1,2})月(\d{1,2})日""")
        monthDayOnlyRegex.find(text)?.let { m ->
            val mon = m.groupValues[1].toIntOrNull()
            val d = m.groupValues[2].toIntOrNull()
            if (mon in 1..12 && d in 1..31) {
                val cal = Calendar.getInstance().apply {
                    set(
                        refCal.get(Calendar.YEAR),
                        mon!! - 1,
                        d!!,
                        refCal.get(Calendar.HOUR_OF_DAY),
                        refCal.get(Calendar.MINUTE),
                        0
                    )
                    set(Calendar.MILLISECOND, 0)
                }
                if (cal.timeInMillis > referenceMillis + 86_400_000L) {
                    cal.add(Calendar.YEAR, -1)
                }
                return cal.timeInMillis
            }
        }

        return null
    }

    private fun buildCalMillis(year: Int, monthZero: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
        return Calendar.getInstance().apply {
            set(year, monthZero, day, hour, minute, second)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
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

    fun mapCurrencyUnitToken(rawUnit: String?): Pair<String, Boolean> {
        val unit = rawUnit?.trim().orEmpty()
        if (unit.isEmpty()) return "CNY" to false
        val upper = unit.uppercase()
        return when {
            upper in setOf("CNY", "RMB") || unit == "人民币" -> "CNY" to true
            unit in setOf("元", "块钱", "块", "圆") -> "CNY" to false
            unit in setOf("¥", "￥") -> "CNY" to false
            upper == "HKD" || upper == "HK$" || unit in setOf("港币", "港元") -> "HKD" to true
            upper == "MOP" || upper == "MOP$" || unit in setOf("澳门元", "澳门币") -> "MOP" to true
            upper == "TWD" || upper == "NT$" || unit in setOf("新台币", "台币") -> "TWD" to true
            upper == "SGD" || upper == "S$" || unit in setOf("新加坡元", "新币") -> "SGD" to true
            upper == "NZD" || upper == "NZ$" || unit in setOf("新西兰元", "纽元") -> "NZD" to true
            upper == "AUD" || upper == "A$" || unit in setOf("澳元", "澳币") -> "AUD" to true
            upper == "CAD" || upper == "C$" || unit in setOf("加元", "加币") -> "CAD" to true
            upper == "CHF" || unit in setOf("瑞士法郎", "瑞郎") -> "CHF" to true
            upper == "THB" || unit in setOf("泰铢", "฿") -> "THB" to true
            upper in setOf("MYR", "RM") || unit in setOf("林吉特", "马币") -> "MYR" to true
            upper == "IDR" || unit == "印尼盾" -> "IDR" to true
            upper == "VND" || unit in setOf("越南盾", "越盾", "₫") -> "VND" to true
            upper == "PHP" || unit in setOf("菲律宾比索", "₱") -> "PHP" to true
            upper == "INR" || unit in setOf("印度卢比", "₹") -> "INR" to true
            upper == "RUB" || unit in setOf("卢布", "₽") -> "RUB" to true
            upper == "BRL" || unit == "雷亚尔" -> "BRL" to true
            upper == "MXN" || upper == "MX$" || unit == "墨西哥比索" -> "MXN" to true
            upper == "ZAR" || unit == "南非兰特" -> "ZAR" to true
            upper == "TRY" || unit in setOf("土耳其里拉", "₺") -> "TRY" to true
            upper == "SEK" || unit == "瑞典克朗" -> "SEK" to true
            upper == "NOK" || unit == "挪威克朗" -> "NOK" to true
            upper == "DKK" || unit == "丹麦克朗" -> "DKK" to true
            upper == "PLN" || unit in setOf("兹罗提", "zł") -> "PLN" to true
            upper == "CZK" || unit in setOf("捷克克朗", "Kč") -> "CZK" to true
            upper == "HUF" || unit == "福林" -> "HUF" to true
            upper == "AED" || unit == "迪拉姆" -> "AED" to true
            upper == "SAR" || unit == "沙特里亚尔" -> "SAR" to true
            upper == "QAR" || unit == "卡塔尔里亚尔" -> "QAR" to true
            upper == "ILS" || unit in setOf("谢克尔", "₪") -> "ILS" to true
            upper == "ARS" || unit == "阿根廷比索" -> "ARS" to true
            upper in setOf("USD", "US$", "$") || unit in setOf("美元", "美金") -> "USD" to true
            upper == "EUR" || unit in setOf("欧元", "€") -> "EUR" to true
            upper == "GBP" || unit in setOf("英镑", "£") -> "GBP" to true
            upper == "JPY" || unit in setOf("日元", "日圆", "円") -> "JPY" to true
            upper == "KRW" || unit in setOf("韩元", "₩") -> "KRW" to true
            else -> "CNY" to false
        }
    }

    fun detectCurrencyCode(text: String): String {
        val primary = extractPrimaryAmountAndCurrency(text = text, requireExplicitUnit = true)
        if (primary != null && primary.hasSpecificCurrency) {
            return primary.currencyCode
        }
        val target = stripBalanceAndDates(text).ifBlank { text }
        return detectCurrencyCodeFromRawText(target)
    }

    private fun detectCurrencyCodeFromRawText(target: String): String {
        return when {
            target.contains("HKD", ignoreCase = true) || target.contains("HK$") ||
                target.contains("港币") || target.contains("港元") -> "HKD"
            target.contains("MOP", ignoreCase = true) || target.contains("MOP$") ||
                target.contains("澳门元") || target.contains("澳门币") -> "MOP"
            target.contains("TWD", ignoreCase = true) || target.contains("NT$") ||
                target.contains("新台币") || target.contains("台币") -> "TWD"
            target.contains("SGD", ignoreCase = true) || target.contains("S$") ||
                target.contains("新加坡元") || target.contains("新币") -> "SGD"
            target.contains("NZD", ignoreCase = true) || target.contains("NZ$") ||
                target.contains("新西兰元") || target.contains("纽元") -> "NZD"
            target.contains("AUD", ignoreCase = true) || target.contains("A$") ||
                target.contains("澳元") || target.contains("澳币") -> "AUD"
            target.contains("CAD", ignoreCase = true) || target.contains("C$") ||
                target.contains("加元") || target.contains("加币") -> "CAD"
            target.contains("CHF", ignoreCase = true) || target.contains("瑞士法郎") ||
                target.contains("瑞郎") -> "CHF"
            target.contains("THB", ignoreCase = true) || target.contains("฿") ||
                target.contains("泰铢") -> "THB"
            target.contains("MYR", ignoreCase = true) || target.contains("林吉特") ||
                target.contains("马币") || Regex("""\bRM\s*[0-9]""").containsMatchIn(target) -> "MYR"
            target.contains("IDR", ignoreCase = true) || target.contains("印尼盾") -> "IDR"
            target.contains("VND", ignoreCase = true) || target.contains("₫") ||
                target.contains("越南盾") || target.contains("越盾") -> "VND"
            target.contains("PHP", ignoreCase = true) || target.contains("₱") ||
                target.contains("菲律宾比索") -> "PHP"
            target.contains("INR", ignoreCase = true) || target.contains("₹") ||
                target.contains("印度卢比") -> "INR"
            target.contains("RUB", ignoreCase = true) || target.contains("₽") ||
                target.contains("卢布") -> "RUB"
            target.contains("BRL", ignoreCase = true) || target.contains("雷亚尔") -> "BRL"
            target.contains("MXN", ignoreCase = true) || target.contains("MX$") ||
                target.contains("墨西哥比索") -> "MXN"
            target.contains("ZAR", ignoreCase = true) || target.contains("南非兰特") -> "ZAR"
            target.contains("TRY", ignoreCase = true) || target.contains("₺") ||
                target.contains("土耳其里拉") -> "TRY"
            target.contains("SEK", ignoreCase = true) || target.contains("瑞典克朗") -> "SEK"
            target.contains("NOK", ignoreCase = true) || target.contains("挪威克朗") -> "NOK"
            target.contains("DKK", ignoreCase = true) || target.contains("丹麦克朗") -> "DKK"
            target.contains("PLN", ignoreCase = true) || target.contains("zł") ||
                target.contains("兹罗提") -> "PLN"
            target.contains("CZK", ignoreCase = true) || target.contains("Kč") ||
                target.contains("捷克克朗") -> "CZK"
            target.contains("HUF", ignoreCase = true) || target.contains("福林") -> "HUF"
            target.contains("AED", ignoreCase = true) || target.contains("迪拉姆") -> "AED"
            target.contains("SAR", ignoreCase = true) || target.contains("沙特里亚尔") -> "SAR"
            target.contains("QAR", ignoreCase = true) || target.contains("卡塔尔里亚尔") -> "QAR"
            target.contains("ILS", ignoreCase = true) || target.contains("₪") ||
                target.contains("谢克尔") -> "ILS"
            target.contains("ARS", ignoreCase = true) || target.contains("阿根廷比索") -> "ARS"
            target.contains("USD", ignoreCase = true) || target.contains("US$") ||
                target.contains("美元") || target.contains("美金") ||
                Regex("""\$\s*[0-9]""").containsMatchIn(target) -> "USD"
            target.contains("EUR", ignoreCase = true) || target.contains("欧元") ||
                target.contains("€") -> "EUR"
            target.contains("GBP", ignoreCase = true) || target.contains("英镑") ||
                target.contains("£") -> "GBP"
            target.contains("JPY", ignoreCase = true) || target.contains("日元") ||
                target.contains("日圆") || target.contains("円") -> "JPY"
            target.contains("KRW", ignoreCase = true) || target.contains("韩元") ||
                target.contains("₩") -> "KRW"
            else -> "CNY"
        }
    }

    fun hasExplicitAmountUnit(text: String): Boolean {
        return extractExplicitUnitAmountCents(text) != null
    }

    fun extractExplicitUnitAmountCents(
        text: String,
        numberStyle: NumberFormatStyle = NumberFormatStyle.AUTO
    ): Long? {
        return extractPrimaryAmountAndCurrency(
            text = text,
            numberStyle = numberStyle,
            allowLooseStandaloneNumber = false,
            requireExplicitUnit = true
        )?.amountCents
    }

    fun extractPrimaryAmountAndCurrency(
        text: String,
        numberStyle: NumberFormatStyle = NumberFormatStyle.AUTO,
        allowLooseStandaloneNumber: Boolean = false,
        requireExplicitUnit: Boolean = false
    ): ExtractedAmountMention? {
        if (text.isBlank()) return null
        val masked = maskNonAmountNoisePreserveLength(text)
        if (masked.isBlank()) return null

        val rawCandidates = mutableListOf<ExtractedAmountMention>()

        fun addCandidate(
            rawNum: String,
            unitToken: String?,
            hasExplicitUnit: Boolean,
            start: Int,
            end: Int
        ) {
            val cents = parseNormalizedAmountCents(rawNum, numberStyle) ?: return
            val (code, isSpecific) = mapCurrencyUnitToken(unitToken)
            val contextScore = scoreAmountMentionInContext(
                maskedText = masked,
                startIndex = start,
                hasExplicitUnit = hasExplicitUnit
            )
            rawCandidates.add(
                ExtractedAmountMention(
                    amountCents = cents,
                    currencyCode = code,
                    hasExplicitUnit = hasExplicitUnit,
                    hasSpecificCurrency = isSpecific,
                    startIndex = start,
                    endIndex = end,
                    score = contextScore
                )
            )
        }

        // 1) Prefix currency unit + amount (e.g. 人民币55.50元, ¥35.00, SGD 10.49, 新加坡元10.49)
        prefixUnitAmountRegex.findAll(masked).forEach { m ->
            addCandidate(
                rawNum = m.groupValues[2],
                unitToken = m.groupValues[1],
                hasExplicitUnit = true,
                start = m.range.first,
                end = m.range.last + 1
            )
        }

        // 2) Amount + suffix currency unit (e.g. 10.49新加坡元, 55.50人民币, 38.60元, 200日元)
        suffixUnitAmountRegex.findAll(masked).forEach { m ->
            addCandidate(
                rawNum = m.groupValues[1],
                unitToken = m.groupValues[2],
                hasExplicitUnit = true,
                start = m.range.first,
                end = m.range.last + 1
            )
        }

        // 3) Cue-prefixed amounts (e.g. 消费38.60, 金额10.49) when explicit unit is not strictly required
        if (!requireExplicitUnit) {
            cueAmountRegex.findAll(masked).forEach { m ->
                val suffixUnit = m.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
                addCandidate(
                    rawNum = m.groupValues[1],
                    unitToken = suffixUnit,
                    hasExplicitUnit = suffixUnit != null,
                    start = m.range.first,
                    end = m.range.last + 1
                )
            }
        }

        // Deduplicate overlapping mentions on the exact same numeric span (prefer explicit unit / specific currency / higher score)
        val deduped = mutableListOf<ExtractedAmountMention>()
        val sortedByPriority = rawCandidates.sortedWith(
            compareByDescending<ExtractedAmountMention> { it.hasExplicitUnit }
                .thenByDescending { it.hasSpecificCurrency }
                .thenByDescending { it.score }
                .thenBy { it.startIndex }
        )
        for (candidate in sortedByPriority) {
            val overlaps = deduped.any { existing ->
                candidate.startIndex < existing.endIndex && existing.startIndex < candidate.endIndex
            }
            if (!overlaps) {
                deduped.add(candidate)
            }
        }

        if (deduped.isNotEmpty()) {
            // If all candidates are heavily penalized secondary amounts (e.g., ONLY a balance sentence),
            // still skip pure-balance mentions when caller is checking standard notification parsing unless it's the only explicit transaction.
            val nonSecondary = deduped.filter { it.score > -50 }
            val pool = nonSecondary.ifEmpty { deduped }
            val winner = pool.sortedWith(
                compareByDescending<ExtractedAmountMention> { it.score }
                    .thenByDescending { it.hasExplicitUnit }
                    .thenBy { it.startIndex }
            ).first()
            // Avoid returning a pure balance-only number when no primary cue exists and score <= -80
            if (winner.score <= -80 && !hasAnyPrimaryCue(masked)) {
                return null
            }
            return winner
        }

        if (allowLooseStandaloneNumber && !requireExplicitUnit) {
            val sanitizedForLoose = masked.replace(Regex("""\d{4}\s*卡"""), " ")
            standaloneNumberRegex.findAll(sanitizedForLoose).forEach { match ->
                val raw = match.groupValues[1]
                val hasFormatting = raw.contains('.') || raw.contains(',') || raw.contains(' ')
                if (hasFormatting || raw.length != 4) {
                    val cents = parseNormalizedAmountCents(raw, numberStyle)
                    if (cents != null) {
                        val score = scoreAmountMentionInContext(sanitizedForLoose, match.range.first, false)
                        if (score > -80) {
                            return ExtractedAmountMention(
                                amountCents = cents,
                                currencyCode = detectCurrencyCodeFromRawText(masked),
                                hasExplicitUnit = false,
                                hasSpecificCurrency = false,
                                startIndex = match.range.first,
                                endIndex = match.range.last + 1,
                                score = score
                            )
                        }
                    }
                }
            }
        }
        return null
    }

    private fun hasAnyPrimaryCue(text: String): Boolean =
        primaryTransactionCues.any { text.contains(it) } || strongPrimaryAmountCues.any { text.contains(it) }

    private fun scoreAmountMentionInContext(
        maskedText: String,
        startIndex: Int,
        hasExplicitUnit: Boolean
    ): Int {
        val clauseDelimiters = charArrayOf('，', ',', '。', ';', '；', '\n', '！', '!', '？', '?')
        val clauseStart = maskedText.lastIndexOfAny(clauseDelimiters, (startIndex - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val clausePrefix = maskedText.substring(clauseStart, startIndex).trim()

        val prevClausePrefix = if (clauseStart > 1) {
            val prevDelim = maskedText.lastIndexOfAny(clauseDelimiters, (clauseStart - 2).coerceAtLeast(0))
            val prevStart = if (prevDelim < 0) 0 else prevDelim + 1
            maskedText.substring(prevStart, (clauseStart - 1).coerceAtLeast(prevStart)).trim()
        } else {
            ""
        }

        var score = if (hasExplicitUnit) 15 else 0

        // Check nearest cue in the same clause before the amount
        val latestSecondaryPos = secondaryAmountCues
            .mapNotNull { kw -> clausePrefix.lastIndexOf(kw).takeIf { it >= 0 }?.let { it + kw.length } }
            .maxOrNull() ?: -1
        val latestStrongPrimaryPos = strongPrimaryAmountCues
            .mapNotNull { kw -> clausePrefix.lastIndexOf(kw).takeIf { it >= 0 }?.let { it + kw.length } }
            .maxOrNull() ?: -1
        val latestPrimaryPos = primaryTransactionCues
            .mapNotNull { kw -> clausePrefix.lastIndexOf(kw).takeIf { it >= 0 }?.let { it + kw.length } }
            .maxOrNull() ?: -1
        val latestAmountLabelPos = amountLabelCues
            .mapNotNull { kw -> clausePrefix.lastIndexOf(kw).takeIf { it >= 0 }?.let { it + kw.length } }
            .maxOrNull() ?: -1

        val nearestPrimaryPos = maxOf(latestStrongPrimaryPos, latestPrimaryPos)

        when {
            latestSecondaryPos > nearestPrimaryPos && latestSecondaryPos > latestAmountLabelPos -> {
                // Directly preceded by secondary/auxiliary cue such as 已冻结 / 余额 / 折合 / 优惠立减 / 原价 / 手续费
                score -= 100
            }
            latestStrongPrimaryPos >= 0 && latestStrongPrimaryPos >= latestSecondaryPos -> {
                score += 75
            }
            latestPrimaryPos >= 0 && latestPrimaryPos >= latestSecondaryPos -> {
                score += 55
            }
            latestAmountLabelPos >= 0 -> {
                // Clause says "金额10.49新加坡元" -> check whether the preceding clause is a primary transaction or secondary cue
                val prevHasPrimary = primaryTransactionCues.any { prevClausePrefix.contains(it) } ||
                    strongPrimaryAmountCues.any { prevClausePrefix.contains(it) }
                val prevHasSecondary = secondaryAmountCues.any { prevClausePrefix.contains(it) }
                score += when {
                    prevHasPrimary -> 40
                    prevHasSecondary -> -60
                    else -> 20
                }
            }
            else -> {
                // No explicit cue in the same clause; lightly consider preceding clause
                if (primaryTransactionCues.any { prevClausePrefix.contains(it) }) {
                    score += 10
                }
            }
        }
        return score
    }

    private fun maskRegexPreserveLength(input: String, regex: Regex): String {
        val chars = input.toCharArray()
        regex.findAll(input).forEach { match ->
            for (i in match.range.first..match.range.last) {
                if (i in chars.indices && chars[i] != '\n') {
                    chars[i] = ' '
                }
            }
        }
        return String(chars)
    }

    private fun maskNonAmountNoisePreserveLength(text: String): String {
        var result = text
        val dateAndTimeRegexes = listOf(
            Regex("""\d{4}[-/年]\d{1,2}[-/月]\d{1,2}日?"""),
            Regex("""\d{1,2}月\d{1,2}日"""),
            Regex("""\d{1,2}:\d{2}(?::\d{2})?""")
        )
        for (regex in dateAndTimeRegexes) {
            result = maskRegexPreserveLength(result, regex)
        }
        for (regex in nonAmountNoiseRegexes) {
            result = maskRegexPreserveLength(result, regex)
        }
        return result
    }

    fun extractAmountCents(
        text: String,
        numberStyle: NumberFormatStyle = NumberFormatStyle.AUTO,
        allowLooseStandaloneNumber: Boolean = false
    ): Long? {
        return extractPrimaryAmountAndCurrency(
            text = text,
            numberStyle = numberStyle,
            allowLooseStandaloneNumber = allowLooseStandaloneNumber,
            requireExplicitUnit = false
        )?.amountCents
    }

    private fun stripBalanceAndDates(text: String): String {
        return text
            .replace(Regex("""(?:可用余额|当前余额|账户余额|交易后余额|余额|可用额度|剩余额度|已冻结|冻结)[为是:：\s]*$prefixCurrencyUnitToken?\s*[0-9,.\s]+$suffixCurrencyUnitToken?""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\d{4}[-/年]\d{1,2}[-/月]\d{1,2}日?"""), " ")
            .replace(Regex("""\d{1,2}月\d{1,2}日"""), " ")
            .replace(Regex("""\d{1,2}:\d{2}(?::\d{2})?"""), " ")
    }

    private val bankAbbreviationMap: List<Pair<String, String>> = listOf(
        "中国工商银行" to "工商银行",
        "中国建设银行" to "建设银行",
        "中国农业银行" to "农业银行",
        "中国邮政储蓄银行" to "邮储银行",
        "邮政储蓄银行" to "邮储银行",
        "中国银行" to "中国银行",
        "交通银行" to "交通银行",
        "招商银行" to "招商银行",
        "平安银行" to "平安银行",
        "浦发银行" to "浦发银行",
        "上海浦东发展银行" to "浦发银行",
        "兴业银行" to "兴业银行",
        "中信银行" to "中信银行",
        "光大银行" to "光大银行",
        "中国光大银行" to "光大银行",
        "民生银行" to "民生银行",
        "中国民生银行" to "民生银行",
        "广发银行" to "广发银行",
        "华夏银行" to "华夏银行",
        "北京银行" to "北京银行",
        "上海银行" to "上海银行",
        "宁波银行" to "宁波银行",
        "江苏银行" to "江苏银行",
        "南京银行" to "南京银行",
        "杭州银行" to "杭州银行",
        "浙商银行" to "浙商银行",
        "渤海银行" to "渤海银行",
        "恒丰银行" to "恒丰银行",
        "网商银行" to "网商银行",
        "微众银行" to "微众银行",
        "【中行】" to "中国银行",
        "【工行】" to "工商银行",
        "【建行】" to "建设银行",
        "【农行】" to "农业银行",
        "【招行】" to "招商银行",
        "【交行】" to "交通银行",
        "【邮储】" to "邮储银行",
        "【浦发】" to "浦发银行",
        "【兴业】" to "兴业银行",
        "【中信】" to "中信银行",
        "【光大】" to "光大银行",
        "【民生】" to "民生银行",
        "【广发】" to "广发银行"
    )

    fun detectChannel(
        packageName: String,
        text: String,
        customChannels: List<String> = emptyList()
    ): String {
        // Explicit sub-channel attribution first ("花呗"/"余额宝" -> "支付宝", "零钱"/"零钱通" -> "微信支付")
        if (text.contains("花呗") || text.contains("余额宝")) return "支付宝"
        if (text.contains("零钱通") || text.contains("微信零钱") ||
            (text.contains("零钱") && !text.contains("支付宝"))
        ) {
            return "微信支付"
        }

        // User-defined / custom channels (longest match first, skipping generic labels)
        if (customChannels.isNotEmpty()) {
            val matchedCustom = customChannels
                .asSequence()
                .map { it.trim() }
                .filter { it.length >= 2 && it != "其他" && it != "银行卡" && it != "未知渠道" }
                .sortedByDescending { it.length }
                .firstOrNull { text.contains(it, ignoreCase = true) }
            if (matchedCustom != null) {
                return ChannelCatalog.normalizeRecognizedChannel(matchedCustom)
            }
        }

        // Explicit 【xxx银行】 / 【xxx支付】 sender tags in SMS or notification text take precedence over generic SMS package
        val bracketTag = Regex("""[【\[]([^】\]]{2,14})[】\]]""").findAll(text)
            .map { it.groupValues[1].trim() }
            .firstOrNull { tag ->
                tag.contains("银行") || tag.contains("支付") || tag.contains("农信") || tag.contains("信用社")
            }
        if (bracketTag != null) {
            bankAbbreviationMap.firstOrNull { bracketTag.contains(it.first) }?.let { return it.second }
            if (bracketTag.endsWith("银行") || bracketTag.endsWith("农信") || bracketTag.endsWith("信用社")) {
                return bracketTag.removePrefix("中国").let { short ->
                    if (bracketTag == "中国银行") "中国银行" else short.ifBlank { bracketTag }
                }
            }
        }

        ChinaPaymentCatalog.watchedPackageMap[packageName]?.let { return it.name }

        for (item in ChinaPaymentCatalog.channels) {
            if (text.contains(item.name) || item.smsCodes.any { text.contains(it) }) {
                return item.name
            }
        }

        for ((alias, canonical) in bankAbbreviationMap) {
            if (text.contains(alias)) return canonical
        }

        return when {
            text.contains("花呗") || text.contains("余额宝") || text.contains("支付宝") -> "支付宝"
            text.contains("零钱通") || text.contains("零钱") || text.contains("微信") -> "微信支付"
            text.contains("银联") || text.contains("云闪付") -> "云闪付"
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

        // Internal cash movement (零钱提现 / 本人转账 / 账户互转 / 信用卡还款) must not count as income or expense
        if (isSelfTransferOrWithdraw(text) || isSelfTransferOrWithdraw(cleaned)) {
            return TransactionType.TRANSFER
        }

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
        if (type == TransactionType.TRANSFER) return if (isSelfTransferOrWithdraw(combined)) "不计收支" else "转账红包"

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

