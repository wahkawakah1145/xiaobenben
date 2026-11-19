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

    @Test
    fun uncertainNotificationTriggersInquiryAndLearnedRuleResolvesIt() {
        val ambiguous = ChinaNotificationParser.parse(
            packageName = "com.custom.wallet",
            title = "电子钱包通知",
            content = "【电子钱包】完成交易128.00元，请留意账单明细。"
        )
        assertNotNull(ambiguous)
        org.junit.Assert.assertTrue(ambiguous!!.needsInquiry)
        assertEquals("未知渠道", ambiguous.channel)

        val keyword = ChinaNotificationParser.suggestRuleKeyword(
            title = "电子钱包通知",
            content = "【电子钱包】完成交易128.00元",
            merchant = ambiguous.merchant,
            channel = ambiguous.channel
        )
        assertEquals("电子钱包", keyword)

        val learnedRule = com.example.model.CustomParsingRule(
            name = "学习 · 电子钱包",
            packageFilter = "*",
            keywordPattern = keyword,
            channelName = "企业电子钱包",
            defaultCategory = "餐饮美食",
            defaultType = TransactionType.EXPENSE.name,
            merchantOverride = "园区福利社",
            priority = 20,
            enabled = true
        )
        val resolved = ChinaNotificationParser.parse(
            packageName = "com.custom.wallet",
            title = "电子钱包通知",
            content = "【电子钱包】完成交易128.00元，请留意账单明细。",
            customRules = listOf(learnedRule)
        )
        assertNotNull(resolved)
        org.junit.Assert.assertFalse(resolved!!.needsInquiry)
        assertEquals("企业电子钱包", resolved.channel)
        assertEquals("餐饮美食", resolved.category)
        assertEquals("园区福利社", resolved.merchant)
        assertEquals(0.96f, resolved.confidence)
    }

    @Test
    fun customTipsOverrideDefaultListAndWrapSafely() {
        val customRaw = "第一条自定义金句\n\n第二条自定义金句"
        val effective = com.example.parser.FinanceHumorTips.effectiveTips(customRaw)
        assertEquals(2, effective.size)
        assertEquals("第一条自定义金句", com.example.parser.FinanceHumorTips.tipAt(0, customRaw))
        assertEquals("第二条自定义金句", com.example.parser.FinanceHumorTips.tipAt(1, customRaw))
        assertEquals("第一条自定义金句", com.example.parser.FinanceHumorTips.tipAt(2, customRaw))
    }

    @Test
    fun parsesEquivalentNumberFormatsIdenticallyAndSupportsLearnedFormats() {
        val plain = ChinaNotificationParser.parse(
            packageName = "com.custom.bank",
            title = "银行动账提醒",
            content = "【XX银行】您的账户入账人民币2111.10元。"
        )
        val grouped = ChinaNotificationParser.parse(
            packageName = "com.custom.bank",
            title = "银行动账提醒",
            content = "【XX银行】您的账户入账人民币2,111.10元。"
        )
        assertNotNull(plain)
        assertNotNull(grouped)
        assertEquals(211110L, plain!!.amountCents)
        assertEquals(plain.amountCents, grouped!!.amountCents)
        assertEquals(
            com.example.model.NumberFormatStyle.COMMA_THOUSANDS_DOT,
            ChinaNotificationParser.detectNumberFormatStyle("入账人民币2,111.10元")
        )

        val euroRule = com.example.model.CustomParsingRule(
            name = "欧区账单",
            packageFilter = "*",
            keywordPattern = "EuroPay",
            channelName = "EuroPay",
            defaultCategory = "购物消费",
            defaultType = "AUTO",
            numberFormatPattern = com.example.model.NumberFormatStyle.DOT_THOUSANDS_COMMA.name,
            priority = 20,
            enabled = true
        )
        val euroTx = ChinaNotificationParser.parse(
            packageName = "com.euro.pay",
            title = "EuroPay 通知",
            content = "EuroPay 扣款 EUR 2.111,10",
            customRules = listOf(euroRule)
        )
        assertNotNull(euroTx)
        assertEquals(211110L, euroTx!!.amountCents)
        assertEquals("EUR", euroTx.currencyCode)
        assertEquals(TransactionType.EXPENSE, euroTx.type)
    }

    @Test
    fun accuratelyDistinguishesIncomeRefundAndExpenseDespiteBrandKeywords() {
        val wechatIncome = ChinaNotificationParser.parse(
            packageName = "com.tencent.mm",
            title = "微信支付",
            content = "微信支付：收到来自张三的转账 100.00元。"
        )
        assertNotNull(wechatIncome)
        assertEquals(TransactionType.INCOME, wechatIncome!!.type)
        assertEquals(10000L, wechatIncome.amountCents)

        val alipayIncome = ChinaNotificationParser.parse(
            packageName = "com.eg.android.AlipayGphone",
            title = "支付宝通知",
            content = "支付宝收款到账50.00元，支付方式：余额。"
        )
        assertNotNull(alipayIncome)
        assertEquals(TransactionType.INCOME, alipayIncome!!.type)
        assertEquals(5000L, alipayIncome.amountCents)

        val refundIncome = ChinaNotificationParser.parse(
            packageName = "com.android.mms",
            title = "95555 招商银行",
            content = "【招商银行】您尾号8812卡信用卡消费退款人民币120.00元已退回。"
        )
        assertNotNull(refundIncome)
        assertEquals(TransactionType.INCOME, refundIncome!!.type)
        assertEquals(12000L, refundIncome.amountCents)

        val candidates = ChinaNotificationParser.extractKeywordCandidates(
            title = "企业园区卡通知",
            content = "【园区一卡通】您在职工食堂消费18.00元。",
            merchant = "职工食堂",
            channel = "未知渠道"
        )
        org.junit.Assert.assertTrue(candidates.contains("职工食堂"))
        org.junit.Assert.assertTrue(candidates.contains("园区一卡通"))
    }

    @Test
    fun historicalSnapshotDiffersFromUpdatedLiveExchangeRate() {
        val recordedRate = com.example.data.CurrencyExchangeManager.rateBetween(
            fromCurrency = "USD",
            toCurrency = "CNY",
            ratesToCny = mapOf("CNY" to 1.0, "USD" to 7.10)
        )
        val recordedCents = com.example.data.CurrencyExchangeManager.convertCents(
            amountCents = 1000L,
            fromCurrency = "USD",
            toCurrency = "CNY",
            ratesToCny = mapOf("CNY" to 1.0, "USD" to 7.10)
        )
        val liveRate = com.example.data.CurrencyExchangeManager.rateBetween(
            fromCurrency = "USD",
            toCurrency = "CNY",
            ratesToCny = mapOf("CNY" to 1.0, "USD" to 7.35)
        )
        val liveCents = com.example.data.CurrencyExchangeManager.convertCents(
            amountCents = 1000L,
            fromCurrency = "USD",
            toCurrency = "CNY",
            ratesToCny = mapOf("CNY" to 1.0, "USD" to 7.35)
        )
        assertEquals(7.10, recordedRate, 0.0001)
        assertEquals(7100L, recordedCents)
        assertEquals(7.35, liveRate, 0.0001)
        assertEquals(7350L, liveCents)
    }

    @Test
    fun backupReminderSchedulerDetectsDueIntervalsAndFormatsLabels() {
        val now = 1_700_100_000_000L
        val twoDaysAgo = now - 49 * 3_600_000L
        org.junit.Assert.assertTrue(
            com.example.service.BackupReminderScheduler.isBackupDue(
                enabled = true,
                intervalHours = 48,
                lastLocalBackupMillis = twoDaysAgo,
                lastBackupReminderMillis = 0L,
                nowMillis = now
            )
        )
        org.junit.Assert.assertFalse(
            com.example.service.BackupReminderScheduler.isBackupDue(
                enabled = true,
                intervalHours = 72,
                lastLocalBackupMillis = twoDaysAgo,
                lastBackupReminderMillis = 0L,
                nowMillis = now
            )
        )
        assertEquals("7 天", com.example.service.BackupReminderScheduler.formatIntervalLabel(168))
        assertEquals("12 小时", com.example.service.BackupReminderScheduler.formatIntervalLabel(12))
        org.junit.Assert.assertTrue(com.example.parser.FinanceHumorTips.tips.size >= 35)
    }

    @Test
    fun extractsOccurredTimestampFromDelayedAndExplicitNotifications() {
        val refCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 19, 10, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val refMillis = refCal.timeInMillis

        val yesterdayTx = ChinaNotificationParser.parse(
            packageName = "com.eg.android.AlipayGphone",
            title = "支付宝通知",
            content = "你于昨天 22:15向夜宵大排档付款55.00元。",
            referenceTimeMillis = refMillis
        )
        assertNotNull(yesterdayTx)
        assertNotNull(yesterdayTx!!.occurredAtMillis)
        val parsedYesterdayCal = java.util.Calendar.getInstance().apply {
            timeInMillis = yesterdayTx.occurredAtMillis!!
        }
        assertEquals(2026, parsedYesterdayCal.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.NOVEMBER, parsedYesterdayCal.get(java.util.Calendar.MONTH))
        assertEquals(18, parsedYesterdayCal.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(22, parsedYesterdayCal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(15, parsedYesterdayCal.get(java.util.Calendar.MINUTE))

        val explicitTx = ChinaNotificationParser.parse(
            packageName = "com.android.mms",
            title = "95555 招商银行",
            content = "【招商银行】账户8812于2026-11-18 08:30:12在星巴克消费42.00元。",
            referenceTimeMillis = refMillis
        )
        assertNotNull(explicitTx)
        val explicitCal = java.util.Calendar.getInstance().apply {
            timeInMillis = explicitTx!!.occurredAtMillis!!
        }
        assertEquals(18, explicitCal.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(8, explicitCal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(30, explicitCal.get(java.util.Calendar.MINUTE))
    }

    @Test
    fun filtersAndSummarizesYearMonthWeekDayGranularities() {
        fun calMillis(year: Int, monthZero: Int, day: Int, hour: Int = 12): Long =
            java.util.Calendar.getInstance().apply {
                set(year, monthZero, day, hour, 0, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

        // 2026-11-18 is Wednesday
        val anchor = calMillis(2026, java.util.Calendar.NOVEMBER, 18)
        val txToday = com.example.model.LedgerTransaction(
            id = "d1",
            amountCents = 2500L,
            merchant = "今日午餐",
            occurredAtMillis = calMillis(2026, java.util.Calendar.NOVEMBER, 18, 13)
        )
        val txSameWeek = com.example.model.LedgerTransaction(
            id = "w1",
            amountCents = 4000L,
            merchant = "周一咖啡",
            occurredAtMillis = calMillis(2026, java.util.Calendar.NOVEMBER, 16, 9)
        )
        val txSameMonthEarlier = com.example.model.LedgerTransaction(
            id = "m1",
            amountCents = 8000L,
            merchant = "月初聚餐",
            occurredAtMillis = calMillis(2026, java.util.Calendar.NOVEMBER, 2, 19)
        )
        val txSameYearEarlierMonth = com.example.model.LedgerTransaction(
            id = "y1",
            amountCents = 15000L,
            merchant = "十月超市",
            occurredAtMillis = calMillis(2026, java.util.Calendar.OCTOBER, 5, 16)
        )
        val all = listOf(txToday, txSameWeek, txSameMonthEarlier, txSameYearEarlierMonth)

        assertEquals(1, com.example.viewmodel.LedgerViewModel.filterByGranularity(all, com.example.model.AnalyticsGranularity.DAY, anchor).size)
        assertEquals(2, com.example.viewmodel.LedgerViewModel.filterByGranularity(all, com.example.model.AnalyticsGranularity.WEEK, anchor).size)
        assertEquals(3, com.example.viewmodel.LedgerViewModel.filterByGranularity(all, com.example.model.AnalyticsGranularity.MONTH, anchor).size)
        assertEquals(4, com.example.viewmodel.LedgerViewModel.filterByGranularity(all, com.example.model.AnalyticsGranularity.YEAR, anchor).size)

        val yearSummary = com.example.parser.LocalSpendingAnalyzer.generatePeriodSummary(
            periodLabel = "2026年",
            granularity = com.example.model.AnalyticsGranularity.YEAR,
            transactions = all,
            monthlyBudgetCents = 500_000L
        )
        org.junit.Assert.assertTrue(yearSummary.contains("2026年"))
        org.junit.Assert.assertTrue(yearSummary.contains("年度参考预算"))
    }
}



