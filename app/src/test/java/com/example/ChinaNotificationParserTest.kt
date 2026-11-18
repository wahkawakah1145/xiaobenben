package com.example

import com.example.model.SourceApp
import com.example.model.TransactionType
import com.example.parser.ChinaNotificationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChinaNotificationParserTest {

    @Test
    fun parsesCmbBankExpenseNotification() {
        val result = ChinaNotificationParser.parse(
            packageName = "com.android.mms",
            title = "95555 招商银行",
            content = "【招商银行】您账户8812于11月18日08:30在星巴克(静安寺店)消费人民币42.00元，可用余额3,210.50元。"
        )
        assertNotNull(result)
        assertEquals(4200L, result!!.amountCents)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("招商银行", result.channel)
        assertEquals("8812", result.cardLastFour)
        assertEquals("餐饮美食", result.category)
        assertEquals("星巴克(静安寺店)", result.merchant)
    }

    @Test
    fun parsesIcbcSalaryIncomeWithThousandsSeparator() {
        val result = ChinaNotificationParser.parse(
            packageName = "com.android.mms",
            title = "95588",
            content = "【工商银行】您尾号1024卡11月18日09:15工资入账人民币18,500.00元，余额26,430.20元。"
        )
        assertNotNull(result)
        assertEquals(1850000L, result!!.amountCents)
        assertEquals(TransactionType.INCOME, result.type)
        assertEquals("工商银行", result.channel)
        assertEquals("1024", result.cardLastFour)
        assertEquals("工资奖金", result.category)
    }

    @Test
    fun parsesAlipayAndWechatNotifications() {
        val alipay = ChinaNotificationParser.parse(
            packageName = "com.eg.android.AlipayGphone",
            title = "支付宝支付助手",
            content = "你已成功向瑞幸咖啡付款18.50元，使用花呗扣款。"
        )
        assertNotNull(alipay)
        assertEquals(1850L, alipay!!.amountCents)
        assertEquals("支付宝", alipay.channel)
        assertEquals(SourceApp.ALIPAY, alipay.sourceApp)
        assertEquals("餐饮美食", alipay.category)

        val wechat = ChinaNotificationParser.parse(
            packageName = "com.tencent.mm",
            title = "微信支付",
            content = "微信支付：你已向「美团外卖」付款38.60元，支付方式：零钱通。"
        )
        assertNotNull(wechat)
        assertEquals(3860L, wechat!!.amountCents)
        assertEquals("微信支付", wechat.channel)
        assertEquals(SourceApp.WECHAT, wechat.sourceApp)
        assertEquals("美团外卖", wechat.merchant)
    }

    @Test
    fun ignoresOtpVerificationCodes() {
        val otp = ChinaNotificationParser.parse(
            packageName = "com.android.mms",
            title = "95555",
            content = "【招商银行】验证码849210，您正在进行登录操作，金额100元限额校验，请勿泄露。"
        )
        assertNull(otp)
    }

    @Test
    fun appliesCustomRuleAndLocalSemanticExtraction() {
        val rule = com.example.model.CustomParsingRule(
            name = "公司食堂",
            packageFilter = "*",
            keywordPattern = "员工餐厅|北京银行",
            channelName = "北京银行",
            defaultCategory = "餐饮美食",
            defaultType = TransactionType.EXPENSE.name,
            enabled = true
        )
        val matched = ChinaNotificationParser.parse(
            packageName = "com.android.mms",
            title = "95526",
            content = "【北京银行】您尾号3319卡在员工餐厅消费24.50元。",
            customRules = listOf(rule)
        )
        assertNotNull(matched)
        assertEquals(com.example.model.ParseMethod.RULE_ENGINE, matched!!.parseMethod)
        assertEquals("北京银行", matched.channel)
        assertEquals("餐饮美食", matched.category)

        val semanticOnly = ChinaNotificationParser.parse(
            packageName = "com.android.mms",
            title = "95599 农业银行",
            content = "【农业银行】尾号6618卡扣款58.00元，商户「喜茶小程序」。",
            enableLocalSemanticPass = true
        )
        assertNotNull(semanticOnly)
        assertEquals(com.example.model.ParseMethod.LOCAL_NLP, semanticOnly!!.parseMethod)
        assertEquals("喜茶小程序", semanticOnly.merchant)
        assertEquals("餐饮美食", semanticOnly.category)
    }

    @Test
    fun generatesOfflineMonthlySpendingInsight() {
        val txs = listOf(
            com.example.model.LedgerTransaction(
                amountCents = 4200L,
                type = TransactionType.EXPENSE.name,
                category = "餐饮美食",
                merchant = "星巴克",
                channel = "招商银行"
            ),
            com.example.model.LedgerTransaction(
                amountCents = 185000L,
                type = TransactionType.INCOME.name,
                category = "工资奖金",
                merchant = "工资发放",
                channel = "工商银行"
            )
        )
        val summary = com.example.parser.LocalSpendingAnalyzer.generateMonthlySummary(
            monthLabel = "2026年11月",
            transactions = txs,
            monthlyBudgetCents = 500_000L
        )
        org.junit.Assert.assertTrue(summary.contains("¥42.00"))
        org.junit.Assert.assertTrue(summary.contains("餐饮美食"))
        org.junit.Assert.assertTrue(summary.contains("星巴克"))
    }

    @Test
    fun detectsMultiCurrencyNotificationsAndConvertsToBaseCurrency() {
        val usdTx = ChinaNotificationParser.parse(
            packageName = "com.android.mms",
            title = "95555 招商银行",
            content = "【招商银行】您尾号8812Visa卡境外消费USD 15.00，商户OpenAI。"
        )
        assertNotNull(usdTx)
        assertEquals("USD", usdTx!!.currencyCode)
        assertEquals(1500L, usdTx.amountCents)

        val hkdTx = ChinaNotificationParser.parse(
            packageName = "com.eg.android.AlipayGphone",
            title = "支付宝",
            content = "你已成功向港铁付款港币24.50元。"
        )
        assertNotNull(hkdTx)
        assertEquals("HKD", hkdTx!!.currencyCode)
        assertEquals(2450L, hkdTx.amountCents)

        val jpyCurrency = ChinaNotificationParser.detectCurrencyCode("消费日元3200円")
        assertEquals("JPY", jpyCurrency)

        val rates = mapOf("CNY" to 1.0, "USD" to 7.2, "HKD" to 0.92)
        val usdToCny = com.example.data.CurrencyExchangeManager.convertCents(1000L, "USD", "CNY", rates)
        assertEquals(7200L, usdToCny)
    }

    @Test
    fun parsesManualImportCsvAndShorthandText() {
        val csv = """
            交易时间,交易对方,收/支,金额,支付方式,币种
            2026-11-18 12:30:00,麦当劳,支出,36.50,支付宝,CNY
            2026-11-18 18:00:00,公司报销,收入,200.00,招商银行,CNY
        """.trimIndent()
        val csvResult = com.example.data.ManualImportManager.parseImportedContent(csv, "alipay.csv").getOrThrow()
        assertEquals(2, csvResult.transactions.size)
        assertEquals(3650L, csvResult.transactions[0].amountCents)
        assertEquals(TransactionType.INCOME.name, csvResult.transactions[1].type)

        val text = "星巴克 35.5\nUSD 12.8 ChatGPT"
        val textResult = com.example.data.ManualImportManager.parseImportedContent(text).getOrThrow()
        assertEquals(2, textResult.transactions.size)
        assertEquals("CNY", textResult.transactions[0].currencyCode)
        assertEquals("USD", textResult.transactions[1].currencyCode)
    }

    @Test
    fun financeHumorTipsWrapSafelyAndAnalyzerSkipsUnconfirmed() {
        val tipZero = com.example.parser.FinanceHumorTips.tipAt(0)
        val tipWrapped = com.example.parser.FinanceHumorTips.tipAt(com.example.parser.FinanceHumorTips.tips.size)
        val tipNegative = com.example.parser.FinanceHumorTips.tipAt(-1)
        assertEquals(tipZero, tipWrapped)
        org.junit.Assert.assertTrue(tipNegative.isNotBlank())

        val txs = listOf(
            com.example.model.LedgerTransaction(
                amountCents = 1000L,
                currencyCode = "USD",
                type = TransactionType.EXPENSE.name,
                category = "餐饮美食",
                merchant = "ConfirmedCafe",
                channel = "招商银行",
                confirmed = true
            ),
            com.example.model.LedgerTransaction(
                amountCents = 99900L,
                currencyCode = "CNY",
                type = TransactionType.EXPENSE.name,
                category = "数码购物",
                merchant = "PendingItem",
                channel = "支付宝",
                confirmed = false
            )
        )
        val summary = com.example.parser.LocalSpendingAnalyzer.generateMonthlySummary(
            monthLabel = "2026年11月",
            transactions = txs,
            monthlyBudgetCents = 100_000L,
            baseCurrencyCode = "CNY",
            ratesToCny = mapOf("CNY" to 1.0, "USD" to 7.2)
        )
        org.junit.Assert.assertTrue(summary.contains("¥72.00"))
        org.junit.Assert.assertFalse(summary.contains("PendingItem"))
    }
}
