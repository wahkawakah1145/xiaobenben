package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("小本本", appName)
  }

  @Test
  fun `parses manual json import via ManualImportManager`() {
    val json = """
        [
          {"merchant":"Apple Store","amount":19.9,"currencyCode":"USD","type":"EXPENSE","category":"数码软件"}
        ]
    """.trimIndent()
    val jsonResult = com.example.data.ManualImportManager.parseImportedContent(json, "bills.json").getOrThrow()
    assertEquals(1, jsonResult.transactions.size)
    assertEquals("USD", jsonResult.transactions.first().currencyCode)
    assertEquals(1990L, jsonResult.transactions.first().amountCents)
  }

  @Test
  fun `urgent backup notification channel uses high importance`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.service.NotificationBridge.ensureNotificationChannels(context)
    val manager = context.getSystemService(android.app.NotificationManager::class.java)
    val channel = manager.getNotificationChannel(com.example.service.NotificationBridge.CHANNEL_BACKUP_URGENT_ID)
    org.junit.Assert.assertNotNull(channel)
    assertEquals(android.app.NotificationManager.IMPORTANCE_HIGH, channel.importance)
  }

  @Test
  fun `backup codec round trips transactions rules and reminder settings`() {
    val originalTx = com.example.model.LedgerTransaction(
      id = "tx-1",
      amountCents = 211110L,
      currencyCode = "USD",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "购物消费",
      merchant = "Amazon",
      channel = "招商银行",
      recordedBaseCurrencyCode = "CNY",
      recordedRateToBase = 7.18
    )
    val originalRule = com.example.model.CustomParsingRule(
      id = "rule-1",
      name = "美亚消费",
      keywordPattern = "Amazon",
      channelName = "招商银行",
      defaultCategory = "购物消费",
      numberFormatPattern = com.example.model.NumberFormatStyle.COMMA_THOUSANDS_DOT.name
    )
    val originalSettings = com.example.model.BudgetSettings(
      monthlyBudgetCents = 800_000L,
      baseCurrencyCode = "CNY",
      backupReminderEnabled = true,
      backupReminderIntervalHours = 48
    )
    val json = com.example.data.LedgerBackupCodec.encodeBackupJson(
      transactions = listOf(originalTx),
      rules = listOf(originalRule),
      settings = originalSettings
    )
    val decoded = com.example.data.LedgerBackupCodec.decodeBackupJson(json)
    assertEquals(1, decoded.transactions.size)
    assertEquals(211110L, decoded.transactions[0].amountCents)
    assertEquals(7.18, decoded.transactions[0].recordedRateToBase, 0.0001)
    assertEquals(1, decoded.parsingRules.size)
    assertEquals(
      com.example.model.NumberFormatStyle.COMMA_THOUSANDS_DOT.name,
      decoded.parsingRules[0].numberFormatPattern
    )
    val decodedSettings = decoded.settings!!
    assertEquals(true, decodedSettings.backupReminderEnabled)
    assertEquals(48, decodedSettings.backupReminderIntervalHours)
  }

  @Test
  fun `batchUpdateTransactions and deleteTransactions update Room database accurately`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.LedgerRepository(context)
    val tx1 = com.example.model.LedgerTransaction(
      id = "batch-tx-1",
      amountCents = 2500L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "其他",
      merchant = "无名小店1",
      channel = "支付宝",
      occurredAtMillis = 1_731_900_000_000L,
      confirmed = true
    )
    val tx2 = com.example.model.LedgerTransaction(
      id = "batch-tx-2",
      amountCents = 3800L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "其他",
      merchant = "无名小店2",
      channel = "微信支付",
      occurredAtMillis = 1_731_910_000_000L,
      confirmed = true
    )
    repository.addTransaction(tx1).getOrThrow()
    repository.addTransaction(tx2).getOrThrow()

    val updatedCount = repository.batchUpdateTransactions(
      txIds = listOf("batch-tx-1", "batch-tx-2"),
      category = "餐饮美食",
      channel = "招商银行",
      note = "团队聚餐"
    ).getOrThrow()
    assertEquals(2, updatedCount)

    val afterUpdate = repository.getAllTransactionsOnce().filter { it.id.startsWith("batch-tx-") }
    assertEquals(2, afterUpdate.size)
    afterUpdate.forEach {
      assertEquals("餐饮美食", it.category)
      assertEquals("招商银行", it.channel)
      assertEquals("团队聚餐", it.note)
    }

    val deletedCount = repository.deleteTransactions(listOf("batch-tx-1")).getOrThrow()
    assertEquals(1, deletedCount)
    val remainingIds = repository.getAllTransactionsOnce().map { it.id }
    org.junit.Assert.assertFalse("batch-tx-1" in remainingIds)
    org.junit.Assert.assertTrue("batch-tx-2" in remainingIds)
  }

  @Test
  fun `appearance default tipTagText is empty and exchange rate update frequency persists`() = kotlinx.coroutines.runBlocking {
    val defaultAppearance = com.example.model.AppearanceSettings()
    assertEquals("", defaultAppearance.tipTagText)

    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.LedgerRepository(context)
    val updatedSettings = com.example.model.BudgetSettings(
      monthlyBudgetCents = 600_000L,
      exchangeRateUpdateFrequency = com.example.model.ExchangeRateAutoUpdateFrequency.EVERY_6_HOURS.name
    )
    repository.saveSettings(updatedSettings).getOrThrow()
    val loadedSettings = repository.getSettingsOnce()
    assertEquals(
      com.example.model.ExchangeRateAutoUpdateFrequency.EVERY_6_HOURS.name,
      loadedSettings.exchangeRateUpdateFrequency
    )
  }

  @Test
  fun `aiConfig persists and LocalModelCatalog resolves open source small models`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.LedgerRepository(context)
    val preset = com.example.data.LocalModelCatalog.resolve("qwen25-0.5b-instruct")
    assertEquals("Qwen/Qwen2.5-0.5B-Instruct", preset.repoId)
    org.junit.Assert.assertTrue(preset.files.isNotEmpty())
    assertEquals(
      listOf("ernie-3.0-nano-zh", "qwen25-0.5b-instruct", "lfm25-350m-q4km"),
      com.example.data.LocalModelCatalog.presets.map { it.id }
    )
    assertEquals(
      "https://hf-mirror.com/LiquidAI/LFM2.5-350M-GGUF",
      com.example.data.LocalModelCatalog.modelRepoUrl("https://hf-mirror.com", "LiquidAI/LFM2.5-350M-GGUF")
    )
    assertEquals(
      "https://huggingface.co/models?sort=trending",
      com.example.data.LocalModelCatalog.weightsBrowserPortalUrl("https://huggingface.co/")
    )

    val config = com.example.model.AiConfig(
      providerMode = com.example.model.AiProviderMode.ONLINE_API,
      localModelId = preset.id,
      localModelMirror = "https://huggingface.co",
      apiBaseUrl = "https://api.deepseek.com/v1/chat/completions",
      apiKey = "sk-test-token",
      apiModelName = "deepseek-chat",
      useAiAsFallbackOnly = false
    )
    repository.saveAiConfig(config).getOrThrow()
    val loaded = repository.getAiConfigOnce()
    assertEquals(com.example.model.AiProviderMode.ONLINE_API, loaded.providerMode)
    assertEquals("https://api.deepseek.com/v1/chat/completions", loaded.apiBaseUrl)
    assertEquals("deepseek-chat", loaded.apiModelName)
    assertEquals(false, loaded.useAiAsFallbackOnly)
  }

  @Test
  fun `appearance navigation and predictive back preferences persist cleanly`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.LedgerRepository(context)
    val saved = com.example.model.AppearanceSettings(
      tabClickAnimation = com.example.model.TabClickAnimationStyle.FADE,
      predictiveBackEnabled = true,
      predictiveBackStyle = com.example.model.PredictiveBackStyle.SLIDE_RIGHT
    )
    repository.saveAppearanceSettings(saved).getOrThrow()
    val loaded = repository.observeAppearanceSettings().first()
    assertEquals(com.example.model.TabClickAnimationStyle.FADE, loaded.tabClickAnimation)
    assertEquals(true, loaded.predictiveBackEnabled)
    assertEquals(com.example.model.PredictiveBackStyle.SLIDE_RIGHT, loaded.predictiveBackStyle)
  }

  @Test
  fun `storage optimization inspects footprint vacuums db and prunes old raw notifications`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.LedgerRepository(context)
    val now = System.currentTimeMillis()
    val oldTx = com.example.model.LedgerTransaction(
      id = "old-raw-tx",
      amountCents = 5800L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "餐饮美食",
      merchant = "老店面",
      channel = "支付宝",
      rawNotification = "Very long raw notification payload from 60 days ago ".repeat(6),
      occurredAtMillis = now - 60L * 86_400_000L,
      confirmed = true
    )
    val recentTx = com.example.model.LedgerTransaction(
      id = "recent-raw-tx",
      amountCents = 2200L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "交通出行",
      merchant = "地铁出行",
      channel = "微信支付",
      rawNotification = "Recent raw notification payload",
      occurredAtMillis = now - 2L * 86_400_000L,
      confirmed = true
    )
    repository.addTransaction(oldTx).getOrThrow()
    repository.addTransaction(recentTx).getOrThrow()

    val footprintBefore = repository.inspectStorageFootprint(context)
    org.junit.Assert.assertTrue(footprintBefore.databaseBytes > 0L)
    org.junit.Assert.assertTrue(footprintBefore.rawNotificationCount >= 2)

    val result = repository.optimizeStorage(context, pruneRawLogsOlderThanDays = 30).getOrThrow()
    org.junit.Assert.assertTrue(result.prunedRawLogs >= 1)

    val allAfter = repository.getAllTransactionsOnce()
    val oldAfter = allAfter.first { it.id == "old-raw-tx" }
    val recentAfter = allAfter.first { it.id == "recent-raw-tx" }
    assertEquals(null, oldAfter.rawNotification)
    assertEquals(5800L, oldAfter.amountCents)
    assertEquals("老店面", oldAfter.merchant)
    org.junit.Assert.assertNotNull(recentAfter.rawNotification)
  }

  @Test
  fun `ignore rule suppresses future matching notifications and edited transaction overwrites template rule`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.LedgerRepository(context)

    val ignoreRule = repository.recordIgnoredNotificationRule(
      packageName = "com.tencent.mm",
      title = "积分到期提醒",
      content = "【微信商城】您的880积分将于本月底到期，消费满50元可用"
    ).getOrThrow()
    org.junit.Assert.assertTrue(ignoreRule.isIgnoreRule)

    val rulesAfterIgnore = repository.getRulesOnce()
    val matchedIgnore = com.example.parser.ChinaNotificationParser.matchesIgnoreRule(
      packageName = "com.tencent.mm",
      title = "积分到期提醒",
      content = "【微信商城】您的1200积分将于本月底到期，消费满100元可用",
      customRules = rulesAfterIgnore
    )
    org.junit.Assert.assertNotNull(matchedIgnore)
    val parsedIgnored = com.example.parser.ChinaNotificationParser.parse(
      packageName = "com.tencent.mm",
      title = "积分到期提醒",
      content = "【微信商城】您的1200积分将于本月底到期，消费满100元可用",
      customRules = rulesAfterIgnore,
      enableLocalSemanticPass = false
    )
    assertEquals(false, parsedIgnored?.isFinancialTransaction)

    val rawBill = "【招商银行】您尾号8812储蓄卡于11月19日在瑞幸咖啡快捷支付人民币28.00元。"
    val tx = com.example.model.LedgerTransaction(
      id = "tpl-test-tx",
      amountCents = 2800L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "其他",
      merchant = "瑞幸咖啡",
      channel = "招商银行",
      sourceApp = com.example.model.SourceApp.BANK_SMS.name,
      rawNotification = rawBill,
      confirmed = true
    )
    repository.addTransaction(tx).getOrThrow()

    val learnedFirst = repository.updateTransaction(
      txId = "tpl-test-tx",
      amountCents = 2800L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE,
      category = "餐饮美食",
      merchant = "瑞幸咖啡",
      channel = "招商银行",
      cardLastFour = "8812",
      note = null,
      occurredAtMillis = tx.occurredAtMillis,
      learnFormatTemplate = true
    ).getOrThrow()
    org.junit.Assert.assertTrue(learnedFirst)

    // Edit the same format again: latest edit must overwrite template category/channel
    repository.updateTransaction(
      txId = "tpl-test-tx",
      amountCents = 2800L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE,
      category = "居家生活",
      merchant = "楼下精品咖啡",
      channel = "招行主卡",
      cardLastFour = "8812",
      note = null,
      occurredAtMillis = tx.occurredAtMillis,
      learnFormatTemplate = true
    ).getOrThrow()

    val rulesAfterEdit = repository.getRulesOnce()
    val tplRules = rulesAfterEdit.filter { it.isTemplateRule }
    org.junit.Assert.assertEquals(1, tplRules.size)

    val parsedNext = com.example.parser.ChinaNotificationParser.parse(
      packageName = "com.android.mms",
      title = "95555",
      content = "【招商银行】您尾号8812储蓄卡于11月20日在星巴克快捷支付人民币36.00元。",
      customRules = rulesAfterEdit,
      enableLocalSemanticPass = false
    )
    assertEquals("居家生活", parsedNext?.category)
    assertEquals("招行主卡", parsedNext?.channel)
  }

  @Test
  fun `custom AI weight import blocks executables and accepts gguf and vocab json`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val aiManager = com.example.data.AiUnderstandingManager()

    val blockedFile = java.io.File(context.cacheDir, "malicious.dex").apply {
      writeText("dummy dex bytes")
    }
    val blockedResult = aiManager.importCustomModelWeight(
      context = context,
      uri = android.net.Uri.fromFile(blockedFile),
      explicitFileName = "malicious.dex"
    )
    org.junit.Assert.assertTrue(blockedResult.isFailure)

    val validFile = java.io.File(context.cacheDir, "qwen_finance_mini.gguf").apply {
      writeBytes("GGUF\u0003\u0000\u0000\u0000外卖餐饮咖啡地铁工资退款".toByteArray(Charsets.UTF_8))
    }
    val imported = aiManager.importCustomModelWeight(
      context = context,
      uri = android.net.Uri.fromFile(validFile),
      explicitFileName = "qwen_finance_mini.gguf"
    ).getOrThrow()
    org.junit.Assert.assertTrue(imported.id.startsWith(com.example.data.LocalModelCatalog.CUSTOM_PREFIX))
    assertEquals("GGUF 量化权重", imported.formatLabel)
    org.junit.Assert.assertTrue(aiManager.listCustomImportedModels(context).any { it.id == imported.id })
  }

  @Test
  fun `expanded appearance settings dpi color style icons and delete undo restore work end to end`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.LedgerRepository(context)

    val saved = com.example.model.AppearanceSettings(
      dynamicColor = false,
      customPrimaryColorArgb = 0xFF7C3AEDL,
      customPaletteStyle = com.example.model.CustomPaletteStyle.EXPRESSIVE,
      uiDensityScale = 1.15f,
      analyticsPieSizeDp = 260,
      appIconStyleId = com.example.model.AppIconStyle.INK.id,
      iconVisualStyle = com.example.model.IconVisualStyle.DUOTONE,
      homeBrandCustomName = "私人金库",
      homeBrandTitleSizeSp = 28
    )
    repository.saveAppearanceSettings(saved).getOrThrow()
    val loaded = repository.observeAppearanceSettings().first()
    assertEquals(false, loaded.dynamicColor)
    assertEquals(0xFF7C3AEDL, loaded.customPrimaryColorArgb)
    assertEquals(com.example.model.CustomPaletteStyle.EXPRESSIVE, loaded.customPaletteStyle)
    assertEquals(1.15f, loaded.uiDensityScale, 0.001f)
    assertEquals(260, loaded.analyticsPieSizeDp)
    assertEquals(com.example.model.AppIconStyle.INK.id, loaded.appIconStyleId)
    assertEquals(com.example.model.IconVisualStyle.DUOTONE, loaded.iconVisualStyle)
    assertEquals("私人金库", loaded.homeBrandCustomName)
    assertEquals(28, loaded.homeBrandTitleSizeSp)

    val customScheme = com.example.ui.theme.buildCustomColorScheme(
      seedColor = androidx.compose.ui.graphics.Color(0xFF7C3AED),
      style = com.example.model.CustomPaletteStyle.MONOCHROME,
      darkTheme = false
    )
    org.junit.Assert.assertNotEquals(customScheme.primary, customScheme.onPrimary)

    val deletedTx = com.example.model.LedgerTransaction(
      id = "undo-restore-tx",
      amountCents = 4200L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "餐饮美食",
      merchant = "撤销测试餐厅",
      channel = "支付宝"
    )
    repository.addTransaction(deletedTx).getOrThrow()
    repository.deleteTransactions(listOf("undo-restore-tx")).getOrThrow()
    org.junit.Assert.assertFalse(repository.getAllTransactionsOnce().any { it.id == "undo-restore-tx" })

    val restored = repository.importTransactions(listOf(deletedTx)).getOrThrow()
    assertEquals(1, restored)
    org.junit.Assert.assertTrue(repository.getAllTransactionsOnce().any { it.id == "undo-restore-tx" })

    val expressiveDynamic = com.example.ui.theme.applyPaletteStyleToDynamicScheme(
      dynamicScheme = customScheme,
      style = com.example.model.CustomPaletteStyle.EXPRESSIVE,
      darkTheme = false
    )
    org.junit.Assert.assertNotEquals(expressiveDynamic.secondary, customScheme.secondary)

    assertEquals(
      "com.example.LauncherInk",
      com.example.util.DesktopIconSyncManager.aliasClassNameFor(com.example.model.AppIconStyle.INK)
    )
    val svgBitmap = com.example.util.DesktopIconSyncManager.renderThemedSvgBitmap(
      styleId = com.example.model.AppIconStyle.MINIMAL.id,
      primaryArgb = 0xFF0C6756.toInt(),
      primaryContainerArgb = 0xFFD0F0E6.toInt(),
      onPrimaryArgb = 0xFFFFFFFF.toInt(),
      tertiaryArgb = 0xFF336B5E.toInt(),
      sizePx = 96
    )
    assertEquals(96, svgBitmap.width)
    assertEquals(96, svgBitmap.height)

    // Verify smart deduplication defaults to 1 minute and pull-down calendar distance persists
    assertEquals(1, com.example.parser.SmartDeduplicator.DEFAULT_SHORT_WINDOW_MINUTES)
    assertEquals(60_000L, com.example.parser.DeduplicationTimeWindow.ONE_MINUTE.maxDiffMillis)

    repository.saveAppearanceSettings(saved.copy(analyticsPullCalendarDistanceDp = 168)).getOrThrow()
    val reloadedPull = repository.observeAppearanceSettings().first()
    assertEquals(168, reloadedPull.analyticsPullCalendarDistanceDp)
  }

  @Test
  fun `pull calendar distance defaults to 260 and clamps across 80 to 560 dp`() = kotlinx.coroutines.runBlocking {
    val defaultAppearance = com.example.model.AppearanceSettings()
    assertEquals(260, defaultAppearance.analyticsPullCalendarDistanceDp)

    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.LedgerRepository(context)

    repository.saveAppearanceSettings(defaultAppearance.copy(analyticsPullCalendarDistanceDp = 30)).getOrThrow()
    assertEquals(80, repository.observeAppearanceSettings().first().analyticsPullCalendarDistanceDp)

    repository.saveAppearanceSettings(defaultAppearance.copy(analyticsPullCalendarDistanceDp = 720)).getOrThrow()
    assertEquals(560, repository.observeAppearanceSettings().first().analyticsPullCalendarDistanceDp)

    repository.saveAppearanceSettings(defaultAppearance.copy(analyticsPullCalendarDistanceDp = 420)).getOrThrow()
    assertEquals(420, repository.observeAppearanceSettings().first().analyticsPullCalendarDistanceDp)
  }

  @Test
  fun `ai prompt editor rules selection and streaming tokenizer json vocab parsing work`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.LedgerRepository(context)

    val ruleA = com.example.model.CustomParsingRule(
      id = "prompt-rule-a",
      name = "星巴克专享",
      packageFilter = "com.eg.android.AlipayGphone",
      keywordPattern = "星巴克|Starbucks",
      channelName = "支付宝",
      defaultType = "EXPENSE",
      defaultCategory = "餐饮美食",
      merchantOverride = "星巴克咖啡"
    )
    val ruleB = com.example.model.CustomParsingRule(
      id = "prompt-rule-b",
      name = "验证码忽略",
      keywordPattern = "验证码",
      defaultType = "IGNORE"
    )

    val config = com.example.model.AiConfig(
      providerMode = com.example.model.AiProviderMode.ONLINE_API,
      customSystemPrompt = "自定义提示词：优先保持中文商户名称。",
      selectedPromptRuleIds = listOf(ruleA.id)
    )
    repository.saveAiConfig(config).getOrThrow()
    val loaded = repository.getAiConfigOnce()
    assertEquals("自定义提示词：优先保持中文商户名称。", loaded.customSystemPrompt)
    assertEquals(listOf(ruleA.id), loaded.selectedPromptRuleIds)

    val resolved = com.example.data.AiUnderstandingManager.resolveSelectedRules(
      config = loaded,
      availableRules = listOf(ruleA, ruleB)
    )
    assertEquals(1, resolved.size)
    assertEquals(ruleA.id, resolved.first().id)

    val effective = com.example.data.AiUnderstandingManager.buildEffectiveSystemPrompt(loaded, resolved)
    org.junit.Assert.assertTrue(effective.contains("自定义提示词：优先保持中文商户名称。"))
    org.junit.Assert.assertTrue(effective.contains("星巴克专享"))
    org.junit.Assert.assertFalse(effective.contains("验证码忽略"))

    val defaultPrompt = com.example.data.AiUnderstandingManager.buildEffectiveSystemPrompt(
      config.copy(customSystemPrompt = ""),
      emptyList()
    )
    assertEquals(com.example.data.AiUnderstandingManager.DEFAULT_SYSTEM_PROMPT, defaultPrompt)

    val tokenizerFile = java.io.File(context.cacheDir, "stream_vocab_tokenizer.json").apply {
      writeText("""{"version":"1.0","model":{"vocab":{"餐饮":1,"外卖":2,"地铁":3}}}""", Charsets.UTF_8)
    }
    val aiManager = com.example.data.AiUnderstandingManager()
    val imported = aiManager.importCustomModelWeight(
      context = context,
      uri = android.net.Uri.fromFile(tokenizerFile),
      explicitFileName = "stream_vocab_tokenizer.json"
    ).getOrThrow()
    assertEquals(3, imported.vocabTokenCount)
    com.example.data.AiUnderstandingManager.trimMemory(evictConnections = true)
  }

  @Test
  fun `home dimension filter and non-current-year date formatting work accurately`() {
    val anchorCal = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.MAY, 15, 14, 30, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val nowMillis = anchorCal.timeInMillis
    val sameDayTx = com.example.model.LedgerTransaction(
      id = "dim-today",
      amountCents = 1200L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "餐饮美食",
      merchant = "午餐",
      channel = "支付宝",
      occurredAtMillis = nowMillis - 3_600_000L
    )
    val lastMonthCal = (anchorCal.clone() as java.util.Calendar).apply {
      add(java.util.Calendar.MONTH, -1)
    }
    val lastMonthTx = sameDayTx.copy(
      id = "dim-last-month",
      occurredAtMillis = lastMonthCal.timeInMillis
    )
    val prevYearCal = (anchorCal.clone() as java.util.Calendar).apply {
      set(2025, java.util.Calendar.DECEMBER, 20, 9, 15, 0)
    }
    val prevYearTx = sameDayTx.copy(
      id = "dim-prev-year",
      occurredAtMillis = prevYearCal.timeInMillis
    )
    val all = listOf(sameDayTx, lastMonthTx, prevYearTx)

    assertEquals(
      listOf("dim-today"),
      com.example.viewmodel.LedgerViewModel.filterByHomeDimension(
        all,
        com.example.model.HomeStatsDimension.TODAY,
        nowMillis
      ).map { it.id }
    )
    assertEquals(
      listOf("dim-last-month"),
      com.example.viewmodel.LedgerViewModel.filterByHomeDimension(
        all,
        com.example.model.HomeStatsDimension.LAST_MONTH,
        nowMillis
      ).map { it.id }
    )
    assertEquals(
      listOf("dim-today", "dim-last-month"),
      com.example.viewmodel.LedgerViewModel.filterByHomeDimension(
        all,
        com.example.model.HomeStatsDimension.THIS_YEAR,
        nowMillis
      ).map { it.id }
    )
    assertEquals(
      3,
      com.example.viewmodel.LedgerViewModel.filterByHomeDimension(
        all,
        com.example.model.HomeStatsDimension.ALL_TIME,
        nowMillis
      ).size
    )

    val sameYearHeader = com.example.ui.components.formatDateHeader(nowMillis, nowYear = 2026)
    val prevYearHeader = com.example.ui.components.formatDateHeader(prevYearCal.timeInMillis, nowYear = 2026)
    org.junit.Assert.assertFalse(sameYearHeader.contains("2026年"))
    org.junit.Assert.assertTrue(prevYearHeader.startsWith("2025年"))

    val sameYearCompact = com.example.ui.components.formatItemTimeCompact(nowMillis, nowYear = 2026)
    val prevYearCompact = com.example.ui.components.formatItemTimeCompact(prevYearCal.timeInMillis, nowYear = 2026)
    assertEquals("14:30:00", sameYearCompact)
    assertEquals("2025-12-20 09:15:00", prevYearCompact)
  }

  @Test
  fun `notification bridge bounds logs and trims memory caches cleanly`() {
    com.example.service.NotificationBridge.trimMemoryCaches(keepRecentLogs = 0)
    repeat(25) { index ->
      com.example.service.NotificationBridge.appendLog(
        packageName = "com.example.pkg$index",
        appLabel = "App$index",
        title = "T".repeat(150),
        content = "C".repeat(260),
        status = "已入账",
        summary = "S".repeat(200)
      )
    }
    val capped = com.example.service.NotificationBridge.capturedLogs.value
    assertEquals(20, capped.size)
    org.junit.Assert.assertTrue(capped.first().title.length <= 120)
    org.junit.Assert.assertTrue(capped.first().content.length <= 220)
    org.junit.Assert.assertTrue(capped.first().summary.length <= 160)

    com.example.service.NotificationBridge.trimMemoryCaches(keepRecentLogs = 6)
    assertEquals(6, com.example.service.NotificationBridge.capturedLogs.value.size)
    com.example.service.NotificationBridge.trimMemoryCaches(keepRecentLogs = 0)
  }

  @Test
  fun `books multi-assignment backup round-trip and quick selection summary work end to end`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.LedgerRepository(context)

    val tx1 = com.example.model.LedgerTransaction(
      id = "book-tx-1",
      amountCents = 8800L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "餐饮美食",
      merchant = "东京拉面",
      channel = "支付宝"
    )
    val tx2 = com.example.model.LedgerTransaction(
      id = "book-tx-2",
      amountCents = 20000L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.INCOME.name,
      category = "退款返现",
      merchant = "酒店退押金",
      channel = "招商银行"
    )
    repository.addTransaction(tx1).getOrThrow()
    repository.addTransaction(tx2).getOrThrow()

    val bookTrip = repository.saveBook(com.example.model.LedgerBook(name = "旅行账本", description = "出游花销")).getOrThrow()
    val bookReimburse = repository.saveBook(com.example.model.LedgerBook(name = "报销账本")).getOrThrow()

    repository.addTransactionsToBooks(listOf(tx1.id, tx2.id), listOf(bookTrip.id, bookReimburse.id)).getOrThrow()
    val links = repository.getAllBookLinksOnce()
    org.junit.Assert.assertTrue(links.any { it.transactionId == tx1.id && it.bookId == bookTrip.id })
    org.junit.Assert.assertTrue(links.any { it.transactionId == tx1.id && it.bookId == bookReimburse.id })

    val encoded = com.example.data.LedgerBackupCodec.encodeBackupJson(
      transactions = listOf(tx1, tx2),
      rules = emptyList(),
      settings = com.example.model.BudgetSettings(),
      books = listOf(bookTrip, bookReimburse),
      bookLinks = links
    )
    val decoded = com.example.data.LedgerBackupCodec.decodeBackupJson(encoded)
    assertEquals(2, decoded.books.size)
    org.junit.Assert.assertTrue(decoded.bookLinks.size >= 4)

    val quickText = com.example.parser.LocalSpendingAnalyzer.generateQuickSelectionSummary(
      transactions = listOf(tx1, tx2),
      baseCurrencyCode = "CNY"
    )
    org.junit.Assert.assertTrue(quickText.contains("共 2 笔"))
    org.junit.Assert.assertTrue(quickText.contains("¥88.00"))
    org.junit.Assert.assertTrue(quickText.contains("¥200.00"))
    org.junit.Assert.assertTrue(quickText.contains("东京拉面"))
  }

  @Test
  fun `bottom tab customization oled pure black and enhanced dynamic palette styles persist and differentiate`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.LedgerRepository(context)

    val customOrder = listOf(
      com.example.model.MainBottomTab.ANALYTICS,
      com.example.model.MainBottomTab.LEDGER,
      com.example.model.MainBottomTab.SETTINGS,
      com.example.model.MainBottomTab.BOOKS
    )
    val saved = com.example.model.AppearanceSettings(
      oledPureBlack = true,
      showBooksTab = false,
      bottomTabOrder = customOrder
    )
    repository.saveAppearanceSettings(saved).getOrThrow()
    val loaded = repository.observeAppearanceSettings().first()
    assertEquals(true, loaded.oledPureBlack)
    assertEquals(false, loaded.showBooksTab)
    assertEquals(customOrder, loaded.bottomTabOrder)
    assertEquals(
      listOf(
        com.example.model.MainBottomTab.ANALYTICS,
        com.example.model.MainBottomTab.LEDGER,
        com.example.model.MainBottomTab.SETTINGS
      ),
      loaded.visibleBottomTabs()
    )

    val seed = androidx.compose.ui.graphics.Color(0xFF0F6B54)
    val tonal = com.example.ui.theme.buildCustomColorScheme(seed, com.example.model.CustomPaletteStyle.TONAL_SPOT, darkTheme = true)
    val expressive = com.example.ui.theme.buildCustomColorScheme(seed, com.example.model.CustomPaletteStyle.EXPRESSIVE, darkTheme = true)
    val vibrant = com.example.ui.theme.buildCustomColorScheme(seed, com.example.model.CustomPaletteStyle.VIBRANT, darkTheme = true)
    val neutral = com.example.ui.theme.buildCustomColorScheme(seed, com.example.model.CustomPaletteStyle.NEUTRAL, darkTheme = true)
    org.junit.Assert.assertNotEquals(tonal.primary, expressive.primary)
    org.junit.Assert.assertNotEquals(vibrant.primary, neutral.primary)

    val oledScheme = com.example.ui.theme.applyOledPureBlack(tonal)
    assertEquals(androidx.compose.ui.graphics.Color.Black, oledScheme.background)
    assertEquals(androidx.compose.ui.graphics.Color.Black, oledScheme.surface)
  }

  @Test
  fun `snackbar durations default delete-undo to manual-only and persist cleanly`() = kotlinx.coroutines.runBlocking {
    val defaultAppearance = com.example.model.AppearanceSettings()
    assertEquals(com.example.model.GlobalToastDuration.STANDARD, defaultAppearance.globalSnackbarDuration)
    assertEquals(com.example.model.DeleteUndoDuration.MANUAL_ONLY, defaultAppearance.deleteUndoDuration)

    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.LedgerRepository(context)
    val saved = defaultAppearance.copy(
      globalSnackbarDuration = com.example.model.GlobalToastDuration.LONG,
      deleteUndoDuration = com.example.model.DeleteUndoDuration.STANDARD
    )
    repository.saveAppearanceSettings(saved).getOrThrow()
    val loaded = repository.observeAppearanceSettings().first()
    assertEquals(com.example.model.GlobalToastDuration.LONG, loaded.globalSnackbarDuration)
    assertEquals(com.example.model.DeleteUndoDuration.STANDARD, loaded.deleteUndoDuration)
  }

  @Test
  fun `github release update manager compares versions and parses release json accurately`() {
    org.junit.Assert.assertTrue(
      com.example.data.GitHubReleaseUpdateManager.isRemoteVersionNewer("v1.2.0", "1.1.9")
    )
    org.junit.Assert.assertTrue(
      com.example.data.GitHubReleaseUpdateManager.isRemoteVersionNewer("v1.0.1", "1.0")
    )
    org.junit.Assert.assertFalse(
      com.example.data.GitHubReleaseUpdateManager.isRemoteVersionNewer("v1.0.0", "1.0")
    )
    org.junit.Assert.assertFalse(
      com.example.data.GitHubReleaseUpdateManager.isRemoteVersionNewer("v0.9.9", "1.0.0")
    )

    val sampleJson = """
      {
        "tag_name": "v2.1.0",
        "name": "小本本 v2.1.0 (ARMv8)",
        "body": "新增手动检查更新与相册快捷方式图标",
        "html_url": "https://github.com/wahkawakah1145/xiaobenben/releases/tag/v2.1.0",
        "published_at": "2026-11-21T00:00:00Z",
        "assets": [
          {
            "name": "xiaobenben-v2.1.0-arm64-v8a.apk",
            "size": 4567890,
            "browser_download_url": "https://github.com/wahkawakah1145/xiaobenben/releases/download/v2.1.0/xiaobenben-v2.1.0-arm64-v8a.apk"
          }
        ]
      }
    """.trimIndent()
    val parsed = com.example.data.GitHubReleaseUpdateManager.parseLatestReleaseJson(
      jsonText = sampleJson,
      repoPath = "wahkawakah1145/xiaobenben",
      currentVersionName = "1.0"
    )
    org.junit.Assert.assertTrue(parsed.hasNewerVersion)
    assertEquals("v2.1.0", parsed.tagName)
    assertEquals("xiaobenben-v2.1.0-arm64-v8a.apk", parsed.apkAssetName)
    assertEquals(4567890L, parsed.apkSizeBytes)
  }

  @Test
  fun `desktop shortcut center square bitmap crop produces exact square dimensions`() {
    val wide = android.graphics.Bitmap.createBitmap(320, 180, android.graphics.Bitmap.Config.ARGB_8888)
    val cropped = com.example.util.DesktopIconSyncManager.cropCenterSquareBitmap(wide, sizePx = 192)
    assertEquals(192, cropped.width)
    assertEquals(192, cropped.height)
  }

  @Test
  fun `currency catalog supports abbreviation chinese alias search and expanded currencies`() {
    val byAbbr = com.example.model.CurrencyCatalog.search("hkd")
    org.junit.Assert.assertTrue(byAbbr.any { it.code == "HKD" })
    val byZhAlias = com.example.model.CurrencyCatalog.search("港元")
    org.junit.Assert.assertTrue(byZhAlias.any { it.code == "HKD" })
    val byThai = com.example.model.CurrencyCatalog.search("泰铢")
    org.junit.Assert.assertTrue(byThai.any { it.code == "THB" })
    org.junit.Assert.assertTrue(com.example.model.CurrencyCatalog.currencies.size >= 30)
  }

  @Test
  fun `exchange rate parser supports multiple json schemas and non-cny cross conversion`() {
    val jsdelivrJson = """{"date":"2026-11-21","cny":{"usd":0.138,"hkd":1.08,"thb":4.8}}"""
    val parsedJsDelivr = com.example.data.CurrencyExchangeManager.parseRatesPayload(jsdelivrJson, "Currency-API")
    org.junit.Assert.assertNotNull(parsedJsDelivr)
    org.junit.Assert.assertEquals(1.0 / 0.138, parsedJsDelivr!!.ratesToCny["USD"]!!, 0.01)
    org.junit.Assert.assertEquals(1.0 / 4.8, parsedJsDelivr.ratesToCny["THB"]!!, 0.01)

    val usdBaseJson = """{"base":"USD","date":"2026-11-21","rates":{"CNY":7.2,"HKD":7.8,"EUR":0.92}}"""
    val parsedUsdBase = com.example.data.CurrencyExchangeManager.parseRatesPayload(usdBaseJson, "Custom")
    org.junit.Assert.assertNotNull(parsedUsdBase)
    org.junit.Assert.assertEquals(7.2 / 7.8, parsedUsdBase!!.ratesToCny["HKD"]!!, 0.01)
    org.junit.Assert.assertEquals(7.2 / 0.92, parsedUsdBase.ratesToCny["EUR"]!!, 0.01)
  }

  @Test
  fun `github update manager applies domestic mirror prefix to apk download url`() {
    val sampleJson = """
      {
        "tag_name": "v1.1.4.518",
        "name": "小本本 v1.1.4.518",
        "body": "mirror test",
        "html_url": "https://github.com/wahkawakah1145/xiaobenben/releases/tag/v1.1.4.518",
        "assets": [
          {
            "name": "xiaobenben-v1.1.4.518-arm64-v8a.apk",
            "size": 4123456,
            "browser_download_url": "https://github.com/wahkawakah1145/xiaobenben/releases/download/v1.1.4.518/xiaobenben-v1.1.4.518-arm64-v8a.apk"
          }
        ]
      }
    """.trimIndent()
    val parsed = com.example.data.GitHubReleaseUpdateManager.parseLatestReleaseJson(
      jsonText = sampleJson,
      repoPath = "wahkawakah1145/xiaobenben",
      currentVersionName = "1.1.4.516",
      mirror = com.example.model.GitHubUpdateMirror.GITHUB_AKAMS
    )
    org.junit.Assert.assertTrue(parsed.hasNewerVersion)
    org.junit.Assert.assertTrue(parsed.apkDownloadUrl!!.startsWith("https://github.akams.cn/https://github.com/"))
  }

  @Test
  fun `parseDateTimeInputMillis supports both second and minute precision`() {
    val withSec = com.example.ui.components.parseDateTimeInputMillis("2026-11-21", "09:15:42")
    val withoutSec = com.example.ui.components.parseDateTimeInputMillis("2026-11-21", "09:15")
    org.junit.Assert.assertNotNull(withSec)
    org.junit.Assert.assertNotNull(withoutSec)
    assertEquals(42_000L, withSec!! - withoutSec!!)
  }

  @Test
  fun `ai default system prompt is latest and migrates legacy default prompt cleanly`() {
    val latest = com.example.data.AiUnderstandingManager.DEFAULT_SYSTEM_PROMPT
    org.junit.Assert.assertTrue(latest.contains("零钱提现"))
    org.junit.Assert.assertTrue(latest.contains("本人转账"))
    org.junit.Assert.assertTrue(latest.contains("yyyy-MM-dd HH:mm:ss"))
    org.junit.Assert.assertTrue(latest.contains("TRANSFER"))

    val legacyPrompt = """
      你是一个精准的中国大陆账单与支付通知结构化解析引擎。
      {"currency":"CNY|USD|HKD|EUR|JPY|GBP|SGD|KRW|AUD|CAD|MOP|TWD","occurredTime":"yyyy-MM-dd HH:mm或MM-dd HH:mm或HH:mm或null"}
    """.trimIndent()
    assertEquals("", com.example.data.AiUnderstandingManager.sanitizeStoredCustomPrompt(legacyPrompt))

    val effectiveFromLegacy = com.example.data.AiUnderstandingManager.buildEffectiveSystemPrompt(
      com.example.model.AiConfig(customSystemPrompt = legacyPrompt)
    )
    assertEquals(latest, effectiveFromLegacy)
  }

  @Test
  fun `currency catalog defaults to 5 common foreign currencies and allows adding on demand`() {
    assertEquals(5, com.example.model.CurrencyCatalog.defaultEnabledForeignCodes.size)
    assertEquals(
      listOf("USD", "HKD", "EUR", "JPY", "GBP"),
      com.example.model.CurrencyCatalog.defaultEnabledForeignCodes
    )
    val withExtra = com.example.model.CurrencyCatalog.enabledCurrenciesWithCny(
      com.example.model.CurrencyCatalog.defaultEnabledForeignCodes + "THB"
    )
    assertEquals(7, withExtra.size)
    org.junit.Assert.assertTrue(withExtra.any { it.code == "CNY" })
    org.junit.Assert.assertTrue(withExtra.any { it.code == "THB" })

    val json = """{"date":"2026-11-21","cny":{"usd":0.138,"hkd":1.08,"eur":0.128,"jpy":21.2,"gbp":0.109,"thb":4.8}}"""
    val filteredRates = com.example.data.CurrencyExchangeManager.parseRatesPayload(
      bodyText = json,
      fallbackSourceLabel = "Test",
      targetForeignCodes = com.example.model.CurrencyCatalog.defaultEnabledForeignCodes
    )
    org.junit.Assert.assertNotNull(filteredRates)
    assertEquals(5, filteredRates!!.updatedCount)
    org.junit.Assert.assertFalse(filteredRates.ratesToCny.containsKey("THB"))
  }

  @Test
  fun `shared http client and database memory trim execute safely`() {
    com.example.data.AppHttpClient.trimIdleResources()
    com.example.data.LedgerDatabase.trimMemoryIfOpen()
  }

  @Test
  fun `parses ABC bank csv export with split numeric date time signed amounts balance and spaced log header`() {
    val csv = """
      交易日期,交易时间,交易摘要,交易金额,本次余额,对手信息,日 志 号,交易渠道,交易附言
      20251121,135144,京东金融,-70.43,2423.71,京东肯特瑞基金销售有限公司,1524375468,京东金融,
      20251121,90508,工资入账,5200.00,7623.71,示例科技有限公司,1524379999,代发工资,11月薪资
    """.trimIndent()
    val parsed = com.example.data.ManualImportManager.parseImportedContent(csv, "abc_statement.csv").getOrThrow()
    assertEquals(2, parsed.transactions.size)

    val tx1 = parsed.transactions[0]
    assertEquals(com.example.model.TransactionType.EXPENSE, tx1.txType)
    assertEquals(7043L, tx1.amountCents)
    assertEquals(242371L, tx1.balanceAfterCents)
    assertEquals("京东肯特瑞基金销售有限公司", tx1.merchant)
    assertEquals("1524375468", tx1.transactionNo)
    assertEquals("京东金融", tx1.channel)
    assertEquals("京东金融", tx1.tradeType)

    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = tx1.occurredAtMillis }
    assertEquals(2025, cal1.get(java.util.Calendar.YEAR))
    assertEquals(java.util.Calendar.NOVEMBER, cal1.get(java.util.Calendar.MONTH))
    assertEquals(21, cal1.get(java.util.Calendar.DAY_OF_MONTH))
    assertEquals(13, cal1.get(java.util.Calendar.HOUR_OF_DAY))
    assertEquals(51, cal1.get(java.util.Calendar.MINUTE))
    assertEquals(44, cal1.get(java.util.Calendar.SECOND))

    val tx2 = parsed.transactions[1]
    assertEquals(com.example.model.TransactionType.INCOME, tx2.txType)
    assertEquals(520000L, tx2.amountCents)
    assertEquals(762371L, tx2.balanceAfterCents)
    assertEquals("11月薪资", tx2.note)
    val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = tx2.occurredAtMillis }
    assertEquals(9, cal2.get(java.util.Calendar.HOUR_OF_DAY))
    assertEquals(5, cal2.get(java.util.Calendar.MINUTE))
    assertEquals(8, cal2.get(java.util.Calendar.SECOND))
  }

  @Test
  fun `smart numeric timestamp parser converts compact digits and epoch numbers`() {
    val compact14 = com.example.data.ManualImportManager.parseDateAndTimeMillis("20251121135144", "", allowExcelSerial = false)
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = compact14 }
    assertEquals(2025, cal.get(java.util.Calendar.YEAR))
    assertEquals(13, cal.get(java.util.Calendar.HOUR_OF_DAY))
    assertEquals(51, cal.get(java.util.Calendar.MINUTE))
    assertEquals(44, cal.get(java.util.Calendar.SECOND))

    val epochSec = com.example.data.ManualImportManager.parseDateAndTimeMillis("1763704304", "", allowExcelSerial = false)
    assertEquals(1763704304_000L, epochSec)
    val epochMs = com.example.data.ManualImportManager.parseDateAndTimeMillis("1763704304123", "", allowExcelSerial = false)
    assertEquals(1763704304123L, epochMs)
  }

  @Test
  fun `manual import annotateDuplicates detects existing and intra-batch duplicates and isolates new items`() {
    val csv = """
      交易日期,交易时间,交易摘要,交易金额,本次余额,对手信息,日 志 号,交易渠道,交易附言
      20251121,135144,京东金融,-70.43,2423.71,京东肯特瑞基金销售有限公司,1524375468,京东金融,第一条
      20251121,135144,京东金融,-70.43,2423.71,京东肯特瑞基金销售有限公司,1524375468,京东金融,文件内重复
      20251121,140000,便利店,-15.50,2408.21,全家便利店,1524375999,银行卡,新增条目
    """.trimIndent()
    val rawPreview = com.example.data.ManualImportManager.parseImportedContent(csv, "abc.csv").getOrThrow()
    val existing = listOf(
      com.example.model.LedgerTransaction(
        id = "existing-1",
        amountCents = 7043L,
        currencyCode = "CNY",
        type = com.example.model.TransactionType.EXPENSE.name,
        category = "其他",
        merchant = "京东肯特瑞基金销售有限公司",
        channel = "京东金融",
        transactionNo = "1524375468",
        occurredAtMillis = rawPreview.transactions[0].occurredAtMillis
      )
    )
    val annotated = com.example.data.ManualImportManager.annotateDuplicates(rawPreview, existing)
    assertEquals(3, annotated.transactions.size)
    assertEquals(2, annotated.duplicateCount)
    assertEquals(1, annotated.newTransactions.size)
    assertEquals("全家便利店", annotated.newTransactions.first().merchant)
  }

  @Test
  fun `composite channel splitter splits ampersand channels and quick analysis outputs clean plain text`() {
    val parts = com.example.model.ChannelSplitter.splitChannels("花呗&红包&现金抵价券")
    assertEquals(listOf("花呗", "红包", "现金抵价券"), parts)
    org.junit.Assert.assertTrue(com.example.model.ChannelSplitter.matchesChannel("花呗&红包&现金抵价券", "红包"))
    org.junit.Assert.assertFalse(com.example.model.ChannelSplitter.matchesChannel("花呗&红包", "余额宝"))

    val tx1 = com.example.model.LedgerTransaction(
      id = "ch-1",
      amountCents = 10000L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "购物消费",
      merchant = "淘宝旗舰店",
      channel = "花呗&红包&现金抵价券",
      occurredAtMillis = 1_763_704_304_000L,
      confirmed = true
    )
    val report = com.example.parser.LocalSpendingAnalyzer.generateQuickSelectionReport(
      transactions = listOf(tx1),
      scopeTitle = "筛选测试"
    )
    org.junit.Assert.assertTrue(report.hasCompositeChannels)
    assertEquals(3, report.topChannels.size)
    org.junit.Assert.assertTrue(report.topChannels.map { it.label }.containsAll(listOf("花呗", "红包", "现金抵价券")))
    org.junit.Assert.assertTrue(report.plainText.contains("支出合计"))
    org.junit.Assert.assertFalse(report.plainText.contains("**"))
    org.junit.Assert.assertFalse(report.plainText.contains("##"))
  }

  @Test
  fun `search filter date auto-hyphen and time-of-day slot matching behave accurately`() {
    assertEquals("2025-", com.example.ui.autoFormatDateInput("202", "2025"))
    assertEquals("2025", com.example.ui.autoFormatDateInput("2025-", "2025"))
    assertEquals("2025-11-", com.example.ui.autoFormatDateInput("2025-1", "2025-11"))
    assertEquals("2025-11-21", com.example.ui.autoFormatDateInput("", "20251121"))
    assertEquals("2025-03-", com.example.ui.autoFormatDateInput("2025-3", "2025-3-"))

    assertEquals("09:", com.example.ui.autoFormatTimeOfDayInput("0", "09"))
    assertEquals("09:30", com.example.ui.autoFormatTimeOfDayInput("09:3", "09:30"))
    assertEquals(570, com.example.ui.parseTimeOfDayMinutes("09:30"))

    val calMorning = java.util.Calendar.getInstance().apply {
      set(2025, java.util.Calendar.NOVEMBER, 21, 9, 30, 0)
    }
    val calLateNight = java.util.Calendar.getInstance().apply {
      set(2025, java.util.Calendar.NOVEMBER, 21, 23, 15, 0)
    }
    org.junit.Assert.assertTrue(
      com.example.ui.matchesTimeOfDayRange(
        calMorning.timeInMillis,
        com.example.ui.TimeOfDaySlot.MORNING.startMinute,
        com.example.ui.TimeOfDaySlot.MORNING.endMinute
      )
    )
    org.junit.Assert.assertFalse(
      com.example.ui.matchesTimeOfDayRange(
        calLateNight.timeInMillis,
        com.example.ui.TimeOfDaySlot.MORNING.startMinute,
        com.example.ui.TimeOfDaySlot.MORNING.endMinute
      )
    )
    // Overnight range 22:00 ~ 02:00
    org.junit.Assert.assertTrue(
      com.example.ui.matchesTimeOfDayRange(calLateNight.timeInMillis, 22 * 60, 2 * 60)
    )
  }

  @Test
  fun `backup codec and Room v8 persist balanceAfterCents accurately`() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.LedgerRepository(context)
    val tx = com.example.model.LedgerTransaction(
      id = "bal-tx-1",
      amountCents = 7043L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "其他",
      merchant = "京东肯特瑞",
      channel = "京东金融",
      balanceAfterCents = 242371L,
      occurredAtMillis = 1_763_704_304_000L,
      confirmed = true
    )
    repository.addTransaction(tx).getOrThrow()
    val stored = repository.getAllTransactionsOnce().firstOrNull { it.id == "bal-tx-1" }
    org.junit.Assert.assertNotNull(stored)
    assertEquals(242371L, stored!!.balanceAfterCents)

    val json = com.example.data.LedgerBackupCodec.encodeBackupJson(
      transactions = listOf(tx),
      rules = emptyList(),
      settings = com.example.model.BudgetSettings()
    )
    val decoded = com.example.data.LedgerBackupCodec.decodeBackupJson(json)
    assertEquals(242371L, decoded.transactions.first().balanceAfterCents)
    repository.deleteTransaction("bal-tx-1")
  }

  @Test
  fun `deleting a custom rule triggers undo banner and undoLastDelete restores the rule`() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.LedgerRepository(context)
    val vm = com.example.viewmodel.LedgerViewModel(repo)
    val customRule = com.example.model.CustomParsingRule(
      id = "undo-test-rule-1",
      name = "可撤回测试规则",
      keywordPattern = "UndoTestMerchant",
      channelName = "支付宝",
      defaultCategory = "餐饮美食"
    )
    repo.saveParsingRule(customRule).getOrThrow()
    org.junit.Assert.assertTrue(repo.observeParsingRules().first().any { it.id == customRule.id })

    vm.deleteCustomRule(customRule.id)
    var deadline = System.currentTimeMillis() + 3000L
    while (vm.deleteUndoBanner.value == null && System.currentTimeMillis() < deadline) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      kotlinx.coroutines.delay(25)
    }
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    val banner = vm.deleteUndoBanner.value
    org.junit.Assert.assertNotNull(banner)
    assertEquals(1, banner!!.deletedRules.size)
    assertEquals(customRule.id, banner.deletedRules.first().id)
    org.junit.Assert.assertFalse(repo.observeParsingRules().first().any { it.id == customRule.id })

    vm.undoLastDelete()
    deadline = System.currentTimeMillis() + 3000L
    while (repo.observeParsingRules().first().none { it.id == customRule.id } && System.currentTimeMillis() < deadline) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      kotlinx.coroutines.delay(25)
    }
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    org.junit.Assert.assertTrue(repo.observeParsingRules().first().any { it.id == customRule.id })
    repo.deleteParsingRule(customRule.id)
  }

  @Test
  fun `chunked database loading reads large transaction dataset across multiple pages`() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.LedgerRepository(context)
    val baseTime = 1_763_700_000_000L
    val longBody = "微信支付收付款凭证详情原始通知正文".repeat(35)
    val items = (0 until 450).map { idx ->
      com.example.model.LedgerTransaction(
        id = "chunk-tx-$idx",
        amountCents = 100L + idx,
        currencyCode = "CNY",
        type = com.example.model.TransactionType.EXPENSE.name,
        category = "餐饮美食",
        merchant = "分页商户$idx",
        channel = "微信支付",
        rawNotification = "$longBody #$idx",
        occurredAtMillis = baseTime + idx * 1000L,
        confirmed = true
      )
    }
    repo.importTransactions(items).getOrThrow()

    val once = repo.getAllTransactionsOnce().filter { it.id.startsWith("chunk-tx-") }
    assertEquals(450, once.size)
    assertEquals("chunk-tx-449", once.first().id)

    val observed = repo.observeTransactions().first().filter { it.id.startsWith("chunk-tx-") }
    assertEquals(450, observed.size)

    val byIds = repo.getTransactionsByIdsOnce(listOf("chunk-tx-0", "chunk-tx-250", "chunk-tx-449"))
    assertEquals(3, byIds.size)

    repo.deleteTransactions(items.map { it.id })
  }

  @Test
  fun `multi-keyword space-separated search matches all tokens across fields`() {
    val cal = java.util.Calendar.getInstance().apply {
      set(2025, java.util.Calendar.NOVEMBER, 21, 13, 51, 44)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val tx = com.example.model.LedgerTransaction(
      id = "search-kw-1",
      amountCents = 3850L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "餐饮美食",
      merchant = "星巴克臻选",
      productName = "拿铁大杯",
      channel = "花呗&红包",
      note = "和同事午茶",
      occurredAtMillis = cal.timeInMillis
    )
    org.junit.Assert.assertTrue(tx.matchesSearchQuery("星巴克 拿铁"))
    org.junit.Assert.assertTrue(tx.matchesSearchQuery("星巴克　餐饮 2025-11-21 花呗"))
    org.junit.Assert.assertTrue(tx.matchesSearchQuery("38.50 午茶"))
    org.junit.Assert.assertFalse(tx.matchesSearchQuery("星巴克 微信"))
    org.junit.Assert.assertFalse(tx.matchesSearchQuery("拿铁 2024-01-01"))
  }

  @Test
  fun `batch import defaultSelectedIds excludes duplicates and self-transfer items`() {
    val existingTime = java.util.Calendar.getInstance().apply {
      set(2025, java.util.Calendar.NOVEMBER, 21, 13, 51, 30)
      set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val existing = com.example.model.LedgerTransaction(
      id = "exist-1",
      amountCents = 2500L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "餐饮美食",
      merchant = "瑞幸咖啡",
      channel = "支付宝",
      occurredAtMillis = existingTime
    )
    val csv = """
      交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
      2025-11-21 13:51:40,商户消费,瑞幸咖啡,生椰拿铁,支出,25.00,支付宝,支付成功,TX001,M001,重复项
      2025-11-21 14:10:00,商户消费,美团外卖,牛肉面,支出,32.00,微信支付,支付成功,TX002,M002,正常新增
      2025-11-21 15:00:00,零钱提现,微信零钱提现,提现到银行卡,不计收支,500.00,零钱,已到账,TX003,M003,本人互转
      2025-11-21 16:00:00,工资入账,某科技公司,月薪,收入,8800.00,招商银行,已入账,TX004,M004,正常收入
    """.trimIndent()
    val parsed = com.example.data.ManualImportManager.parseImportedContent(
      csv,
      "import_select_test.csv"
    ).getOrThrow()
    val preview = com.example.data.ManualImportManager.annotateDuplicates(
      preview = parsed,
      existingTransactions = listOf(existing)
    )

    assertEquals(4, preview.transactions.size)
    assertEquals(1, preview.duplicateCount)
    assertEquals(1, preview.selfTransferExcludedCount)

    val defaultSelected = preview.defaultSelectedIds()
    assertEquals(2, defaultSelected.size)
    val selectedMerchants = preview.transactions.filter { it.id in defaultSelected }.map { it.merchant }.toSet()
    assertEquals(setOf("美团外卖", "某科技公司"), selectedMerchants)

    val allIds = preview.transactions.mapTo(LinkedHashSet()) { it.id }
    val inverted = allIds - defaultSelected
    assertEquals(2, inverted.size)
    val invertedMerchants = preview.transactions.filter { it.id in inverted }.map { it.merchant }.toSet()
    assertEquals(setOf("瑞幸咖啡", "微信零钱提现"), invertedMerchants)
  }

  @Test
  fun `custom category add and rename syncs transactions rules ai prompt and backup`() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.LedgerRepository(context)
    val originalSettings = repo.observeSettings().first()
    val originalAi = repo.observeAiConfig().first()

    repo.saveAiConfig(
      originalAi.copy(
        customSystemPrompt = "自定义提示词：可用分类包含 咖啡奶茶、餐饮美食，请返回 JSON。"
      )
    )
    repo.addCustomCategory(com.example.model.TransactionType.EXPENSE, "咖啡奶茶").getOrThrow()

    val tx = com.example.model.LedgerTransaction(
      id = "cat-sync-tx-1",
      amountCents = 1800L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "咖啡奶茶",
      merchant = "霸王茶姬",
      channel = "微信支付",
      occurredAtMillis = 1_763_705_000_000L,
      confirmed = true
    )
    val rule = com.example.model.CustomParsingRule(
      id = "cat-sync-rule-1",
      name = "奶茶规则",
      keywordPattern = "霸王茶姬",
      channelName = "微信支付",
      defaultCategory = "咖啡奶茶"
    )
    repo.addTransaction(tx).getOrThrow()
    repo.saveParsingRule(rule).getOrThrow()

    val syncRes = repo.renameCategory(
      type = com.example.model.TransactionType.EXPENSE,
      oldName = "咖啡奶茶",
      rawNewName = "精品茶饮",
      syncTransactions = true,
      syncRules = true,
      syncAiPrompt = true
    ).getOrThrow()

    assertEquals(1, syncRes.updatedTransactions)
    assertEquals(1, syncRes.updatedRules)
    org.junit.Assert.assertTrue(syncRes.aiPromptSynced)

    val updatedSettings = repo.observeSettings().first()
    org.junit.Assert.assertTrue("精品茶饮" in com.example.model.CategoryCatalog.activeExpenseCategories(updatedSettings))
    org.junit.Assert.assertFalse("咖啡奶茶" in com.example.model.CategoryCatalog.activeExpenseCategories(updatedSettings))

    val updatedTx = repo.getTransactionsByIdsOnce(listOf("cat-sync-tx-1")).first()
    assertEquals("精品茶饮", updatedTx.category)

    val updatedRule = repo.observeParsingRules().first().first { it.id == "cat-sync-rule-1" }
    assertEquals("精品茶饮", updatedRule.defaultCategory)

    val updatedAi = repo.observeAiConfig().first()
    org.junit.Assert.assertTrue(updatedAi.customSystemPrompt.contains("精品茶饮"))
    org.junit.Assert.assertFalse(updatedAi.customSystemPrompt.contains("咖啡奶茶"))

    val dynamicDefaultPrompt = com.example.data.AiUnderstandingManager.buildDefaultSystemPrompt(
      com.example.model.CategoryCatalog.allActiveCategories(updatedSettings)
    )
    org.junit.Assert.assertTrue(dynamicDefaultPrompt.contains("精品茶饮"))

    val backupJson = com.example.data.LedgerBackupCodec.encodeBackupJson(
      transactions = listOf(updatedTx),
      rules = listOf(updatedRule),
      settings = updatedSettings
    )
    val decoded = com.example.data.LedgerBackupCodec.decodeBackupJson(backupJson)
    org.junit.Assert.assertTrue("精品茶饮" in (decoded.settings?.expenseCategories ?: emptyList()))

    // Cleanup
    repo.deleteTransaction("cat-sync-tx-1")
    repo.deleteParsingRule("cat-sync-rule-1")
    repo.saveSettings(originalSettings)
    repo.saveAiConfig(originalAi)
  }

  @Test
  fun `similar bill criteria matches single and combined attributes accurately`() {
    val target = com.example.model.LedgerTransaction(
      id = "sim-target",
      amountCents = 1500L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "交通出行",
      merchant = "滴滴出行",
      productName = "快车",
      channel = "微信支付",
      cardLastFour = "8899",
      occurredAtMillis = 1_763_706_000_000L
    )
    val sameMerchantAndChannel = target.copy(id = "sim-1", amountCents = 2800L, category = "其他")
    val sameMerchantDiffChannel = target.copy(id = "sim-2", channel = "支付宝")
    val diffMerchantSameAmount = target.copy(id = "sim-3", merchant = "美团打车", amountCents = 1500L)

    org.junit.Assert.assertTrue(
      sameMerchantAndChannel.matchesSimilarCriteria(
        target,
        setOf(com.example.model.SimilarBillCriterion.MERCHANT, com.example.model.SimilarBillCriterion.CHANNEL)
      )
    )
    org.junit.Assert.assertFalse(
      sameMerchantDiffChannel.matchesSimilarCriteria(
        target,
        setOf(com.example.model.SimilarBillCriterion.MERCHANT, com.example.model.SimilarBillCriterion.CHANNEL)
      )
    )
    org.junit.Assert.assertTrue(
      sameMerchantDiffChannel.matchesSimilarCriteria(
        target,
        setOf(com.example.model.SimilarBillCriterion.MERCHANT)
      )
    )
    org.junit.Assert.assertTrue(
      diffMerchantSameAmount.matchesSimilarCriteria(
        target,
        setOf(com.example.model.SimilarBillCriterion.AMOUNT, com.example.model.SimilarBillCriterion.CARD_TAIL)
      )
    )
  }

  @Test
  fun `smart deduplicator defaults to same amount and close time prioritizing richer record and optionally checks merchant similarity`() {
    val baseTime = 1_763_710_000_000L
    val sparseTx = com.example.model.LedgerTransaction(
      id = "dedup-sparse-1",
      amountCents = 3500L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "其他",
      merchant = "消费",
      channel = "其他",
      occurredAtMillis = baseTime,
      confirmed = false
    )
    val richTx = com.example.model.LedgerTransaction(
      id = "dedup-rich-1",
      amountCents = 3500L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "餐饮美食",
      merchant = "星巴克(静安寺店)",
      productName = "大杯冰美式",
      channel = "支付宝·花呗",
      transactionNo = "2026112122001488990011",
      cardLastFour = "6688",
      balanceAfterCents = 128000L,
      note = "早餐咖啡",
      rawNotification = "支付宝付款成功：向星巴克(静安寺店)支付35.00元",
      occurredAtMillis = baseTime + 15_000L,
      confirmed = true
    )

    // Default: requireMerchantSimilarity = false -> matches purely on amount + time, keeps richer tx
    val defaultGroups = com.example.parser.SmartDeduplicator.findDuplicateGroups(
      transactions = listOf(sparseTx, richTx),
      maxWindowMillis = 60_000L
    )
    assertEquals(1, defaultGroups.size)
    assertEquals("dedup-rich-1", defaultGroups.first().suggestedKeepId)
    org.junit.Assert.assertTrue(
      com.example.parser.SmartDeduplicator.informationRichnessScore(richTx) >
        com.example.parser.SmartDeduplicator.informationRichnessScore(sparseTx)
    )

    // Optional toggle: requireMerchantSimilarity = true -> dissimilar merchant names are NOT grouped
    val strictGroups = com.example.parser.SmartDeduplicator.findDuplicateGroups(
      transactions = listOf(sparseTx, richTx),
      maxWindowMillis = 60_000L,
      requireMerchantSimilarity = true
    )
    assertEquals(0, strictGroups.size)
  }

  @Test
  fun `ai understanding rejects unitless numbers like message count or 10086 and accepts explicit currency units`() = kotlinx.coroutines.runBlocking<Unit> {
    // Unitless noise notifications MUST NOT pass explicit amount-unit gate
    org.junit.Assert.assertFalse(
      com.example.parser.ChinaNotificationParser.hasExplicitAmountUnit("微信\n[3条] 张三: 晚上一起吃饭")
    )
    org.junit.Assert.assertFalse(
      com.example.parser.ChinaNotificationParser.hasExplicitAmountUnit("中国移动10086\n【中国移动】您的5G套餐已生效，查询请回复10086")
    )
    org.junit.Assert.assertFalse(
      com.example.parser.ChinaNotificationParser.hasExplicitAmountUnit("中国移动 10086\n本月已用通用流量 12.5GB，剩余 37.5GB")
    )
    org.junit.Assert.assertFalse(
      com.example.parser.ChinaNotificationParser.hasExplicitAmountUnit("信息\n验证码 482910，5分钟内有效，请勿泄露")
    )

    // Notifications with explicit currency/amount unit MUST pass
    org.junit.Assert.assertTrue(
      com.example.parser.ChinaNotificationParser.hasExplicitAmountUnit("微信支付\n星巴克消费 ¥35.00")
    )
    org.junit.Assert.assertTrue(
      com.example.parser.ChinaNotificationParser.hasExplicitAmountUnit("支付宝\n美团外卖付款 18.5元")
    )
    org.junit.Assert.assertTrue(
      com.example.parser.ChinaNotificationParser.hasExplicitAmountUnit("招商银行\nApple Store 境外消费 USD 9.99")
    )
    assertEquals(
      3500L,
      com.example.parser.ChinaNotificationParser.extractExplicitUnitAmountCents("星巴克消费 ¥35.00")
    )
    org.junit.Assert.assertNull(
      com.example.parser.ChinaNotificationParser.extractExplicitUnitAmountCents("[5条] 中国移动10086")
    )

    // Default system prompt enforces strict amount-unit validation
    val latestPrompt = com.example.data.AiUnderstandingManager.DEFAULT_SYSTEM_PROMPT
    org.junit.Assert.assertTrue(latestPrompt.contains("严格金额单位校验"))
    org.junit.Assert.assertTrue(latestPrompt.contains("10086"))

    // Local & online AI understanding short-circuit immediately on unitless notifications
    val context = ApplicationProvider.getApplicationContext<Context>()
    val aiManager = com.example.data.AiUnderstandingManager()
    val blockedLocal = aiManager.parseWithLocalModel(
      context = context,
      modelId = "lfm25-350m-q4km",
      packageName = "com.android.mms",
      title = "中国移动10086",
      content = "[2条] 您的套餐流量剩余 10086MB"
    )
    org.junit.Assert.assertTrue(blockedLocal.isFailure)
    org.junit.Assert.assertTrue(
      blockedLocal.exceptionOrNull()?.message?.contains("金额单位") == true
    )

    val blockedOnline = aiManager.parseWithOnlineApi(
      config = com.example.model.AiConfig(
        providerMode = com.example.model.AiProviderMode.ONLINE_API,
        apiBaseUrl = "https://api.example.invalid/v1",
        apiKey = "test-key",
        apiModelName = "gpt-4o-mini"
      ),
      packageName = "com.tencent.mm",
      title = "微信",
      content = "[3条] 李四: 明天见"
    )
    org.junit.Assert.assertTrue(blockedOnline.isFailure)
    org.junit.Assert.assertTrue(
      blockedOnline.exceptionOrNull()?.message?.contains("金额单位") == true
    )
  }

  @Test
  fun `marking recognized transaction as non-bill deletes entry and creates ignore rule`() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.LedgerRepository(context)
    val rawTitle = "中国移动10086"
    val rawContent = "您的5G尊享包已到账，免费赠送10元话费券待领取"
    val pendingTx = com.example.model.LedgerTransaction(
      id = "non-bill-test-1",
      amountCents = 1000L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.INCOME.name,
      category = "其他",
      merchant = "中国移动",
      channel = "短信通知",
      rawNotification = "$rawTitle | $rawContent",
      occurredAtMillis = 1_763_711_000_000L,
      confirmed = false
    )
    repo.addTransaction(pendingTx).getOrThrow()

    val createdRule = repo.markTransactionAsNonBill(pendingTx).getOrThrow()
    org.junit.Assert.assertNotNull(createdRule)
    org.junit.Assert.assertTrue(createdRule!!.isIgnoreRule)
    org.junit.Assert.assertTrue(createdRule.enabled)

    // Transaction is deleted from database
    val remaining = repo.getTransactionsByIdsOnce(listOf("non-bill-test-1"))
    assertEquals(0, remaining.size)

    // Future identical notification matches the new IGNORE rule
    val rules = repo.observeParsingRules().first()
    val matchedIgnore = com.example.parser.ChinaNotificationParser.matchesIgnoreRule(
      packageName = "",
      title = rawTitle,
      content = rawContent,
      customRules = rules
    )
    org.junit.Assert.assertNotNull(matchedIgnore)

    // Cleanup
    repo.deleteParsingRule(createdRule.id)
  }

  @Test
  fun `smart deduplicator excludes persistent ids and repository prunes excluded ids on delete`() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.LedgerRepository(context)
    val originalSettings = repo.observeSettings().first()
    val baseTime = 1_763_720_000_000L

    val txA1 = com.example.model.LedgerTransaction(
      id = "dedup-excl-a1",
      amountCents = 4200L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "餐饮美食",
      merchant = "喜茶",
      channel = "微信支付",
      occurredAtMillis = baseTime,
      confirmed = true
    )
    val txA2 = txA1.copy(id = "dedup-excl-a2", occurredAtMillis = baseTime + 10_000L, channel = "其他")
    val txB1 = txA1.copy(id = "dedup-excl-b1", amountCents = 8800L, merchant = "海底捞")
    val txB2 = txB1.copy(id = "dedup-excl-b2", occurredAtMillis = baseTime + 12_000L, channel = "其他")

    repo.importTransactions(listOf(txA1, txA2, txB1, txB2)).getOrThrow()

    // Exclude group A from smart deduplication
    val added = repo.excludeTransactionsFromDedup(listOf(txA1.id, txA2.id)).getOrThrow()
    assertEquals(2, added)
    val excludedSet = repo.observeSettings().first().dedupExcludedTransactionIds.toSet()
    org.junit.Assert.assertTrue(txA1.id in excludedSet && txA2.id in excludedSet)

    val groups = com.example.parser.SmartDeduplicator.findDuplicateGroups(
      transactions = listOf(txA1, txA2, txB1, txB2),
      maxWindowMillis = 60_000L,
      excludedTransactionIds = excludedSet
    )
    assertEquals(1, groups.size)
    assertEquals(setOf(txB1.id, txB2.id), groups.first().items.map { it.id }.toSet())

    // Deleting an excluded transaction automatically prunes its ID from dedupExcludedTransactionIds
    repo.deleteTransaction(txA1.id).getOrThrow()
    val afterDelete = repo.observeSettings().first().dedupExcludedTransactionIds
    org.junit.Assert.assertFalse(txA1.id in afterDelete)
    org.junit.Assert.assertTrue(txA2.id in afterDelete)

    // Restore remaining excluded ID
    val restored = repo.restoreExcludedDedupTransactions(listOf(txA2.id)).getOrThrow()
    assertEquals(1, restored)
    org.junit.Assert.assertFalse(txA2.id in repo.observeSettings().first().dedupExcludedTransactionIds)

    // Cleanup
    repo.deleteTransactions(listOf(txA2.id, txB1.id, txB2.id))
    repo.saveSettings(originalSettings)
  }

  @Test
  fun `markTransactionAsNonBill emits undo banner and undo restores transaction book links and removes ignore rule`() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.LedgerRepository(context)
    val vm = com.example.viewmodel.LedgerViewModel(repo)

    val book = repo.saveBook(
      com.example.model.LedgerBook(id = "nonbill-undo-book", name = "撤销测试账本")
    ).getOrThrow()
    val tx = com.example.model.LedgerTransaction(
      id = "nonbill-undo-tx-1",
      amountCents = 1990L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "其他",
      merchant = "测试运营商活动",
      channel = "短信通知",
      rawNotification = "运营商活动提醒 | 限时赠送19.90元代金券立即领取",
      occurredAtMillis = 1_763_721_000_000L,
      confirmed = false
    )
    repo.addTransaction(tx).getOrThrow()
    repo.setBooksForTransaction(tx.id, listOf(book.id)).getOrThrow()
    assertEquals(listOf(book.id), repo.getBookLinksForTransactionsOnce(listOf(tx.id)).map { it.bookId })

    vm.markTransactionAsNonBill(tx)
    var deadline = System.currentTimeMillis() + 3000L
    while (vm.deleteUndoBanner.value == null && System.currentTimeMillis() < deadline) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      kotlinx.coroutines.delay(25)
    }
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    val banner = vm.deleteUndoBanner.value
    org.junit.Assert.assertNotNull(banner)
    org.junit.Assert.assertTrue(banner!!.isNonBillMark)
    assertEquals(1, banner.deletedItems.size)
    assertEquals(1, banner.deletedBookLinks.size)
    val createdRuleId = banner.createdNonBillRuleId
    org.junit.Assert.assertNotNull(createdRuleId)
    org.junit.Assert.assertTrue(repo.getTransactionsByIdsOnce(listOf(tx.id)).isEmpty())
    org.junit.Assert.assertTrue(repo.observeParsingRules().first().any { it.id == createdRuleId })

    // Undo should restore transaction, restore its book membership, and remove the auto-created IGNORE rule
    vm.clearActionMessage()
    vm.undoLastDelete()
    deadline = System.currentTimeMillis() + 3000L
    while (vm.actionMessage.value == null && System.currentTimeMillis() < deadline) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      kotlinx.coroutines.delay(25)
    }
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    assertEquals(1, repo.getTransactionsByIdsOnce(listOf(tx.id)).size)
    assertEquals(listOf(book.id), repo.getBookLinksForTransactionsOnce(listOf(tx.id)).map { it.bookId })
    org.junit.Assert.assertFalse(repo.observeParsingRules().first().any { it.id == createdRuleId })

    // Cleanup
    repo.deleteTransaction(tx.id)
    repo.deleteBook(book.id)
  }

  @Test
  fun `single transaction book membership can be updated and cleared via setBooksForTransaction and book detail syncs analytics`() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.LedgerRepository(context)
    val vm = com.example.viewmodel.LedgerViewModel(repo)

    val book1 = repo.saveBook(com.example.model.LedgerBook(id = "global-bk-1", name = "日常")).getOrThrow()
    val book2 = repo.saveBook(com.example.model.LedgerBook(id = "global-bk-2", name = "旅行")).getOrThrow()
    val tx = com.example.model.LedgerTransaction(
      id = "global-bk-tx-1",
      amountCents = 5600L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "餐饮美食",
      merchant = "机场餐厅",
      channel = "支付宝",
      occurredAtMillis = 1_763_722_000_000L,
      confirmed = true
    )
    repo.addTransaction(tx).getOrThrow()

    repo.setBooksForTransaction(tx.id, listOf(book1.id, book2.id)).getOrThrow()
    assertEquals(setOf(book1.id, book2.id), repo.getBookLinksForTransactionsOnce(listOf(tx.id)).map { it.bookId }.toSet())

    repo.setBooksForTransaction(tx.id, listOf(book2.id)).getOrThrow()
    assertEquals(listOf(book2.id), repo.getBookLinksForTransactionsOnce(listOf(tx.id)).map { it.bookId })

    repo.setBooksForTransaction(tx.id, emptyList()).getOrThrow()
    org.junit.Assert.assertTrue(repo.getBookLinksForTransactionsOnce(listOf(tx.id)).isEmpty())

    // Opening book detail synchronizes analytics book selection; closing book detail clears openBookDetailId
    vm.openBookDetail(book2.id)
    assertEquals(book2.id, vm.openBookDetailId.value)
    assertEquals(book2.id, vm.selectedAnalyticsBookId.value)
    vm.closeBookDetail()
    org.junit.Assert.assertNull(vm.openBookDetailId.value)

    // Cleanup
    repo.deleteTransaction(tx.id)
    repo.deleteBook(book1.id)
    repo.deleteBook(book2.id)
  }

  @Test
  fun `category discovery lists all existing categories custom icons migrate on rename and round-trip in backup`() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.LedgerRepository(context)
    val originalSettings = repo.observeSettings().first()
    val originalAi = repo.observeAiConfig().first()

    val legacyTx = com.example.model.LedgerTransaction(
      id = "cat-disc-tx-1",
      amountCents = 9900L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "宠物养护",
      merchant = "萌宠医院",
      channel = "微信支付",
      occurredAtMillis = 1_763_723_000_000L,
      confirmed = true
    )
    val legacyRule = com.example.model.CustomParsingRule(
      id = "cat-disc-rule-1",
      name = "手办规则",
      keywordPattern = "盲盒旗舰店",
      channelName = "支付宝",
      defaultCategory = "潮玩手办",
      defaultType = com.example.model.TransactionType.EXPENSE.name
    )
    repo.addTransaction(legacyTx).getOrThrow()
    repo.saveParsingRule(legacyRule).getOrThrow()

    val discovered = com.example.model.CategoryCatalog.allExistingCategoriesForType(
      type = com.example.model.TransactionType.EXPENSE,
      settings = originalSettings,
      transactions = listOf(legacyTx),
      rules = listOf(legacyRule)
    )
    org.junit.Assert.assertTrue("宠物养护" in discovered)
    org.junit.Assert.assertTrue("潮玩手办" in discovered)

    // Assign custom icon to discovered category, then rename and verify icon + tx + rule + AI prompt sync
    repo.setCategoryIcon("宠物养护", "pets").getOrThrow()
    assertEquals("pets", repo.observeSettings().first().categoryIcons["宠物养护"])

    repo.saveAiConfig(originalAi.copy(customSystemPrompt = "系统提示词含 宠物养护 分类"))
    val renameResult = repo.renameCategory(
      type = com.example.model.TransactionType.EXPENSE,
      oldName = "宠物养护",
      rawNewName = "爱宠消费",
      syncTransactions = true,
      syncRules = true,
      syncAiPrompt = true
    ).getOrThrow()
    assertEquals(1, renameResult.updatedTransactions)
    org.junit.Assert.assertTrue(renameResult.aiPromptSynced)

    val updatedSettings = repo.observeSettings().first()
    assertEquals("pets", updatedSettings.categoryIcons["爱宠消费"])
    org.junit.Assert.assertNull(updatedSettings.categoryIcons["宠物养护"])
    org.junit.Assert.assertTrue("爱宠消费" in com.example.model.CategoryCatalog.activeExpenseCategories(updatedSettings))

    // Backup codec round-trips categoryIcons and dedupExcludedTransactionIds
    val enrichedSettings = updatedSettings.copy(dedupExcludedTransactionIds = listOf("tx-excl-99"))
    val backupJson = com.example.data.LedgerBackupCodec.encodeBackupJson(
      transactions = emptyList(),
      rules = emptyList(),
      settings = enrichedSettings
    )
    val decoded = com.example.data.LedgerBackupCodec.decodeBackupJson(backupJson)
    assertEquals("pets", decoded.settings?.categoryIcons?.get("爱宠消费"))
    assertEquals(listOf("tx-excl-99"), decoded.settings?.dedupExcludedTransactionIds)

    // Cleanup
    repo.deleteTransaction(legacyTx.id)
    repo.deleteParsingRule(legacyRule.id)
    repo.saveSettings(originalSettings)
    repo.saveAiConfig(originalAi)
  }

  @Test
  fun `channel normalization attributes huabei and yuebao to alipay and lingqian to wechat pay while parser recognizes custom channels`() {
    assertEquals("支付宝", com.example.model.ChannelCatalog.normalizeRecognizedChannel("花呗"))
    assertEquals("支付宝", com.example.model.ChannelCatalog.normalizeRecognizedChannel("蚂蚁花呗"))
    assertEquals("支付宝", com.example.model.ChannelCatalog.normalizeRecognizedChannel("余额宝"))
    assertEquals("微信支付", com.example.model.ChannelCatalog.normalizeRecognizedChannel("零钱"))
    assertEquals("微信支付", com.example.model.ChannelCatalog.normalizeRecognizedChannel("微信零钱"))
    assertEquals("微信支付", com.example.model.ChannelCatalog.normalizeRecognizedChannel("零钱通"))
    assertEquals("支付宝&红包", com.example.model.ChannelCatalog.normalizeRecognizedChannel("花呗&红包"))
    assertEquals("支付宝", com.example.model.ChannelCatalog.normalizeRecognizedChannel("花呗&余额宝"))
    assertEquals("微信支付", com.example.model.ChannelCatalog.normalizeRecognizedChannel("零钱&零钱通"))

    val huabeiParsed = com.example.parser.ChinaNotificationParser.parse(
      packageName = "com.android.mms",
      title = "消费提醒",
      content = "您使用花呗在瑞幸咖啡付款¥19.90元"
    )
    org.junit.Assert.assertNotNull(huabeiParsed)
    assertEquals("支付宝", huabeiParsed!!.channel)

    val lingqianParsed = com.example.parser.ChinaNotificationParser.parse(
      packageName = "com.android.mms",
      title = "支付通知",
      content = "您通过零钱通向全家便利店支付¥12.50元"
    )
    org.junit.Assert.assertNotNull(lingqianParsed)
    assertEquals("微信支付", lingqianParsed!!.channel)

    val customParsed = com.example.parser.ChinaNotificationParser.parse(
      packageName = "com.campus.card",
      title = "消费通知",
      content = "您使用校园一卡通在第一食堂消费¥14.00元",
      customChannels = listOf("支付宝", "微信支付", "校园一卡通")
    )
    org.junit.Assert.assertNotNull(customParsed)
    assertEquals("校园一卡通", customParsed!!.channel)
  }

  @Test
  fun `channel management adds renames composite channels syncs rules and ai prompt and round-trips in backup`() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.LedgerRepository(context)
    val originalSettings = repo.observeSettings().first()
    val originalAi = repo.observeAiConfig().first()

    val tx = com.example.model.LedgerTransaction(
      id = "ch-mgmt-tx-1",
      amountCents = 4500L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "餐饮美食",
      merchant = "地铁食堂",
      channel = "数字人民币&红包",
      occurredAtMillis = 1_763_724_000_000L,
      confirmed = true
    )
    val rule = com.example.model.CustomParsingRule(
      id = "ch-mgmt-rule-1",
      name = "数币规则",
      keywordPattern = "数字人民币",
      channelName = "数字人民币",
      defaultCategory = "自动",
      defaultType = "AUTO"
    )
    repo.addTransaction(tx).getOrThrow()
    repo.saveParsingRule(rule).getOrThrow()
    repo.saveAiConfig(originalAi.copy(customSystemPrompt = "自定义渠道含 数字人民币 支付"))

    repo.addCustomChannel("数字人民币", syncAiPrompt = true).getOrThrow()
    org.junit.Assert.assertTrue("数字人民币" in com.example.model.ChannelCatalog.activeChannels(repo.observeSettings().first()))

    val renameResult = repo.renameChannel(
      oldName = "数字人民币",
      rawNewName = "数币钱包",
      syncTransactions = true,
      syncRules = true,
      syncAiPrompt = true
    ).getOrThrow()
    assertEquals(1, renameResult.updatedTransactions)
    assertEquals(1, renameResult.updatedRules)
    org.junit.Assert.assertTrue(renameResult.aiPromptSynced)

    val updatedTx = repo.getAllTransactionsOnce().firstOrNull { it.id == tx.id }
    assertEquals("数币钱包&红包", updatedTx?.channel)
    assertEquals("数币钱包", repo.observeParsingRules().first().first { it.id == rule.id }.channelName)
    org.junit.Assert.assertTrue(repo.observeAiConfig().first().customSystemPrompt.contains("数币钱包"))

    val settingsAfterRename = repo.observeSettings().first()
    val backupJson = com.example.data.LedgerBackupCodec.encodeBackupJson(
      transactions = emptyList(),
      rules = emptyList(),
      settings = settingsAfterRename
    )
    val decoded = com.example.data.LedgerBackupCodec.decodeBackupJson(backupJson)
    org.junit.Assert.assertTrue("数币钱包" in (decoded.settings?.customChannels ?: emptyList()))

    val deleteResult = repo.deleteChannel(
      targetName = "数币钱包",
      fallbackChannel = "其他",
      syncTransactions = true,
      syncRules = true,
      syncAiPrompt = true
    ).getOrThrow()
    assertEquals(1, deleteResult.updatedTransactions)
    assertEquals("其他&红包", repo.getAllTransactionsOnce().firstOrNull { it.id == tx.id }?.channel)

    // Cleanup
    repo.deleteTransaction(tx.id)
    repo.deleteParsingRule(rule.id)
    repo.saveSettings(originalSettings)
    repo.saveAiConfig(originalAi)
  }

  @Test
  fun `smart deduplication convert to non counting transfer can be undone restoring original type and category`() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.LedgerRepository(context)
    val vm = com.example.viewmodel.LedgerViewModel(repo)

    val dupTx = com.example.model.LedgerTransaction(
      id = "dedup-conv-tx-1",
      amountCents = 3200L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "餐饮美食",
      merchant = "麦当劳",
      channel = "支付宝",
      occurredAtMillis = 1_763_725_000_000L,
      confirmed = true
    )
    repo.addTransaction(dupTx).getOrThrow()

    vm.convertTransactionsToNonCountingTransfer(listOf(dupTx.id))
    var deadline = System.currentTimeMillis() + 3000L
    while (vm.deleteUndoBanner.value == null && System.currentTimeMillis() < deadline) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      kotlinx.coroutines.delay(25)
    }
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    val converted = repo.getAllTransactionsOnce().firstOrNull { it.id == dupTx.id }
    assertEquals(com.example.model.TransactionType.TRANSFER.name, converted?.type)
    assertEquals("不计收支", converted?.category)
    org.junit.Assert.assertTrue(vm.deleteUndoBanner.value?.isBatchModify == true)

    vm.undoLastDelete()
    deadline = System.currentTimeMillis() + 3000L
    while (repo.getAllTransactionsOnce().firstOrNull { it.id == dupTx.id }?.type != com.example.model.TransactionType.EXPENSE.name && System.currentTimeMillis() < deadline) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      kotlinx.coroutines.delay(25)
    }
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    val restored = repo.getAllTransactionsOnce().firstOrNull { it.id == dupTx.id }
    assertEquals(com.example.model.TransactionType.EXPENSE.name, restored?.type)
    assertEquals("餐饮美食", restored?.category)

    // Cleanup
    repo.deleteTransaction(dupTx.id)
  }

  @Test
  fun `ledger export manager generates valid csv xlsx and json streams with optional analysis text`() {
    val txs = listOf(
      com.example.model.LedgerTransaction(
        id = "exp-tx-1",
        amountCents = 8850L,
        currencyCode = "CNY",
        type = com.example.model.TransactionType.EXPENSE.name,
        category = "餐饮美食",
        merchant = "海底捞火锅",
        productName = "双人套餐",
        channel = "支付宝",
        occurredAtMillis = 1_763_726_000_000L,
        confirmed = true
      ),
      com.example.model.LedgerTransaction(
        id = "exp-tx-2",
        amountCents = 500000L,
        currencyCode = "CNY",
        type = com.example.model.TransactionType.INCOME.name,
        category = "工资薪资",
        merchant = "科技公司",
        channel = "招商银行",
        occurredAtMillis = 1_763_727_000_000L,
        confirmed = true
      )
    )

    // 1. CSV with analysis text
    val csvWithOut = java.io.ByteArrayOutputStream()
    val csvRes = com.example.data.LedgerExportManager.exportToStream(
      outputStream = csvWithOut,
      format = com.example.data.LedgerExportFormat.CSV,
      transactions = txs,
      scopeTitle = "测试账单",
      includeAnalysisText = true,
      bookName = "旅行账本"
    )
    assertEquals(2, csvRes.exportedCount)
    org.junit.Assert.assertTrue(csvRes.includedAnalysis)
    val csvText = csvWithOut.toString("UTF-8")
    org.junit.Assert.assertTrue(csvText.startsWith("\uFEFF"))
    org.junit.Assert.assertTrue(csvText.contains("# "))
    org.junit.Assert.assertTrue(csvText.contains("海底捞火锅"))
    org.junit.Assert.assertTrue(csvText.contains("交易时间,收/支,分类"))

    // 1b. CSV without analysis text
    val csvNoOut = java.io.ByteArrayOutputStream()
    com.example.data.LedgerExportManager.exportToStream(
      outputStream = csvNoOut,
      format = com.example.data.LedgerExportFormat.CSV,
      transactions = txs,
      includeAnalysisText = false
    )
    val csvNoText = csvNoOut.toString("UTF-8")
    org.junit.Assert.assertFalse(csvNoText.contains("# ====="))
    org.junit.Assert.assertTrue(csvNoText.contains("海底捞火锅"))

    // 2. XLSX with analysis sheet (verify ZIP entries & round-trip via XlsxTableReader)
    val xlsxOut = java.io.ByteArrayOutputStream()
    com.example.data.LedgerExportManager.exportToStream(
      outputStream = xlsxOut,
      format = com.example.data.LedgerExportFormat.XLSX,
      transactions = txs,
      includeAnalysisText = true,
      bookName = "旅行账本"
    )
    val xlsxBytes = xlsxOut.toByteArray()
    val zipNames = mutableSetOf<String>()
    java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(xlsxBytes)).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        zipNames.add(entry.name)
        entry = zis.nextEntry
      }
    }
    org.junit.Assert.assertTrue("xl/worksheets/sheet1.xml" in zipNames)
    org.junit.Assert.assertTrue("xl/worksheets/sheet2.xml" in zipNames)
    val sheets = com.example.data.XlsxTableReader.readSheets(xlsxBytes)
    assertEquals(2, sheets.size)
    org.junit.Assert.assertTrue(sheets[0].rows.size >= 3)
    org.junit.Assert.assertTrue(sheets[0].rows.any { row -> row.any { cell -> cell.contains("海底捞火锅") } })
    org.junit.Assert.assertTrue(sheets[1].rows.any { row -> row.any { cell -> cell.contains("旅行账本") } })

    // 3. JSON with analysis
    val jsonOut = java.io.ByteArrayOutputStream()
    com.example.data.LedgerExportManager.exportToStream(
      outputStream = jsonOut,
      format = com.example.data.LedgerExportFormat.JSON,
      transactions = txs,
      includeAnalysisText = true,
      bookName = "旅行账本"
    )
    val root = org.json.JSONObject(jsonOut.toString("UTF-8"))
    assertEquals("旅行账本", root.getString("bookName"))
    assertEquals(2, root.getJSONArray("transactions").length())
    org.junit.Assert.assertTrue(root.has("analysis"))
    org.junit.Assert.assertTrue(root.getJSONObject("analysis").getString("plainText").contains("旅行账本"))
  }

  @Test
  fun `settings search catalog finds matching items and highlights matched keywords with primary color`() {
    val channelHits = com.example.ui.SettingsSearchCatalog.search("花呗 渠道")
    org.junit.Assert.assertTrue(channelHits.any { it.route == com.example.ui.ROUTE_SETTINGS_CHANNELS })

    val oledHits = com.example.ui.SettingsSearchCatalog.search("oled")
    org.junit.Assert.assertTrue(oledHits.any { it.id == "dark_mode_oled" })

    val exportHits = com.example.ui.SettingsSearchCatalog.search("xlsx 导出")
    org.junit.Assert.assertTrue(exportHits.any { it.id == "local_backup_and_export" })

    val highlightColor = androidx.compose.ui.graphics.Color(0xFF1A73E8)
    val annotated = com.example.ui.buildHighlightedSettingsAnnotatedString(
      text = "深色模式与 OLED 纯黑背景",
      rawQuery = "oled 纯黑",
      highlightColor = highlightColor
    )
    assertEquals("深色模式与 OLED 纯黑背景", annotated.text)
    val highlightedSubstrings = annotated.spanStyles
      .filter { it.item.color == highlightColor }
      .map { annotated.text.substring(it.start, it.end) }
    org.junit.Assert.assertTrue("OLED" in highlightedSubstrings)
    org.junit.Assert.assertTrue("纯黑" in highlightedSubstrings)
  }

  @Test
  fun `spending trend calculator builds buckets across granularities and filters by category and channel`() {
    val cal = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.NOVEMBER, 15, 14, 30, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val refMillis = cal.timeInMillis
    val txs = listOf(
      com.example.model.LedgerTransaction(
        id = "trend-1",
        amountCents = 5000L,
        currencyCode = "CNY",
        type = com.example.model.TransactionType.EXPENSE.name,
        category = "餐饮美食",
        merchant = "餐厅A",
        channel = "支付宝",
        occurredAtMillis = refMillis,
        confirmed = true
      ),
      com.example.model.LedgerTransaction(
        id = "trend-2",
        amountCents = 20000L,
        currencyCode = "CNY",
        type = com.example.model.TransactionType.EXPENSE.name,
        category = "购物消费",
        merchant = "商城B",
        channel = "微信支付",
        occurredAtMillis = refMillis,
        confirmed = true
      )
    )

    val (yearTitle, yearPoints) = com.example.ui.components.SpendingTrendCalculator.buildSeries(
      transactions = txs,
      granularity = com.example.model.AnalyticsGranularity.YEAR,
      periodOffset = 0,
      referenceTimeMillis = refMillis
    )
    assertEquals("2026年（按月）", yearTitle)
    assertEquals(12, yearPoints.size)
    assertEquals(25000L, yearPoints[10].expenseCents)

    val (_, filteredMonthPoints) = com.example.ui.components.SpendingTrendCalculator.buildSeries(
      transactions = txs,
      granularity = com.example.model.AnalyticsGranularity.MONTH,
      periodOffset = 0,
      categoryFilter = "餐饮美食",
      channelFilter = "支付宝",
      referenceTimeMillis = refMillis
    )
    assertEquals(30, filteredMonthPoints.size)
    assertEquals(5000L, filteredMonthPoints[14].expenseCents)
    assertEquals(1, filteredMonthPoints[14].txCount)

    val (_, dayPoints) = com.example.ui.components.SpendingTrendCalculator.buildSeries(
      transactions = txs,
      granularity = com.example.model.AnalyticsGranularity.DAY,
      periodOffset = 0,
      referenceTimeMillis = refMillis
    )
    assertEquals(24, dayPoints.size)
    assertEquals(25000L, dayPoints[14].expenseCents)
  }

  @Test
  fun `feature tips visibility and home page load batch size persist and clamp properly`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = com.example.data.SettingsDataStore(context)

    val defaultAppearance = com.example.model.AppearanceSettings()
    assertEquals(100, defaultAppearance.homePageLoadBatchSize)
    assertEquals(false, defaultAppearance.hideAllFeatureTips)
    org.junit.Assert.assertTrue(defaultAppearance.isFeatureTipVisible(com.example.model.FeatureTipsCatalog.HOME_MULTI_SELECT))

    // Dismiss one tip individually
    val singleDismissed = defaultAppearance.copy(
      dismissedTipIds = setOf(com.example.model.FeatureTipsCatalog.HOME_MULTI_SELECT),
      homePageLoadBatchSize = 999 // should clamp to 500
    )
    store.saveAppearanceSettings(singleDismissed)
    val loaded1 = store.getAppearanceSettingsOnce()
    assertEquals(500, loaded1.homePageLoadBatchSize)
    org.junit.Assert.assertFalse(loaded1.isFeatureTipVisible(com.example.model.FeatureTipsCatalog.HOME_MULTI_SELECT))
    org.junit.Assert.assertTrue(loaded1.isFeatureTipVisible(com.example.model.FeatureTipsCatalog.BOOK_DETAIL_GESTURE))

    // Global hide all tips + lower bound clamping
    store.saveAppearanceSettings(loaded1.copy(hideAllFeatureTips = true, homePageLoadBatchSize = 5))
    val loaded2 = store.getAppearanceSettingsOnce()
    assertEquals(20, loaded2.homePageLoadBatchSize)
    org.junit.Assert.assertTrue(loaded2.hideAllFeatureTips)
    org.junit.Assert.assertFalse(loaded2.isFeatureTipVisible(com.example.model.FeatureTipsCatalog.BOOK_DETAIL_GESTURE))
  }

  @Test
  fun `universal undo restores deleted book with links, removed book links, deleted category, deleted channel, dismissed inquiry and removed currency`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.LedgerRepository(context)
    val vm = com.example.viewmodel.LedgerViewModel(repo)

    fun drainUntil(timeoutMs: Long = 3000L, predicate: () -> Boolean) {
      val deadline = System.currentTimeMillis() + timeoutMs
      while (!predicate() && System.currentTimeMillis() < deadline) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(10)
      }
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    // Seed transaction, book, link, custom category, custom channel, and inquiry
    val tx = com.example.model.LedgerTransaction(
      id = "undo-tx-1",
      amountCents = 4200L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "宠物医疗",
      merchant = "爱宠诊所",
      channel = "自定义渠道Alpha",
      occurredAtMillis = System.currentTimeMillis(),
      confirmed = true
    )
    repo.addTransaction(tx).getOrThrow()
    repo.addCustomCategory(com.example.model.TransactionType.EXPENSE, "宠物医疗", syncAiPrompt = false).getOrThrow()
    repo.addCustomChannel("自定义渠道Alpha", syncAiPrompt = false).getOrThrow()
    val book = repo.saveBook(com.example.model.LedgerBook(id = "undo-book-1", name = "毛孩子账本")).getOrThrow()
    repo.addTransactionsToBooks(listOf(tx.id), listOf(book.id)).getOrThrow()
    val inquiry = com.example.model.NotificationInquiry(
      id = "undo-inq-1",
      packageName = "com.tencent.mm",
      appLabel = "微信",
      title = "支付提醒",
      content = "支付 42.00 元",
      suggestedAmountCents = 4200L,
      suggestedCurrencyCode = "CNY",
      suggestedType = com.example.model.TransactionType.EXPENSE.name,
      suggestedCategory = "宠物医疗",
      suggestedMerchant = "爱宠诊所",
      suggestedChannel = "微信支付",
      reason = "待确认"
    )
    repo.addInquiry(inquiry).getOrThrow()

    // 1. Remove transaction from book -> undo restores link
    vm.removeTransactionsFromBook(listOf(tx.id), book.id)
    drainUntil { vm.deleteUndoBanner.value != null }
    assertEquals(0, repo.getBookLinksForTransactionsOnce(listOf(tx.id)).size)
    vm.clearActionMessage()
    vm.undoLastDelete()
    drainUntil { vm.actionMessage.value != null }
    assertEquals(1, repo.getBookLinksForTransactionsOnce(listOf(tx.id)).size)

    // 2. Delete book -> undo restores book AND its links
    vm.deleteBook(book.id)
    drainUntil { vm.deleteUndoBanner.value != null }
    assertEquals(0, repo.getAllBooksOnce().count { it.id == book.id })
    vm.clearActionMessage()
    vm.undoLastDelete()
    drainUntil { vm.actionMessage.value != null }
    org.junit.Assert.assertTrue(repo.getAllBooksOnce().any { it.id == book.id })
    org.junit.Assert.assertTrue(repo.getAllBookLinksOnce().any { it.bookId == book.id && it.transactionId == tx.id })

    // 3. Delete custom category -> undo restores category and tx's original category
    vm.deleteCategory(com.example.model.TransactionType.EXPENSE, "宠物医疗", fallbackCategory = "其他", syncTransactions = true, syncRules = true, syncAiPrompt = false)
    drainUntil { vm.deleteUndoBanner.value != null }
    assertEquals("其他", repo.getTransactionsByIdsOnce(listOf(tx.id)).first().category)
    vm.clearActionMessage()
    vm.undoLastDelete()
    drainUntil { vm.actionMessage.value != null }
    assertEquals("宠物医疗", repo.getTransactionsByIdsOnce(listOf(tx.id)).first().category)
    org.junit.Assert.assertTrue("宠物医疗" in repo.getSettingsOnce().expenseCategories)

    // 4. Delete custom channel -> undo restores channel and tx's original channel
    vm.deleteChannel("自定义渠道Alpha", fallbackChannel = "其他", syncTransactions = true, syncRules = true, syncAiPrompt = false)
    drainUntil { vm.deleteUndoBanner.value != null }
    assertEquals("其他", repo.getTransactionsByIdsOnce(listOf(tx.id)).first().channel)
    vm.clearActionMessage()
    vm.undoLastDelete()
    drainUntil { vm.actionMessage.value != null }
    assertEquals("自定义渠道Alpha", repo.getTransactionsByIdsOnce(listOf(tx.id)).first().channel)
    org.junit.Assert.assertTrue("自定义渠道Alpha" in repo.getSettingsOnce().customChannels)

    // 5. Dismiss inquiry -> undo restores inquiry
    vm.dismissInquiry(inquiry.id)
    drainUntil { vm.deleteUndoBanner.value != null }
    vm.clearActionMessage()
    vm.undoLastDelete()
    drainUntil { vm.actionMessage.value != null }
    org.junit.Assert.assertTrue(repo.observeInquiries().first().any { it.id == inquiry.id })

    // 6. Remove enabled foreign currency -> undo restores it
    vm.removeEnabledCurrency("JPY")
    drainUntil { vm.deleteUndoBanner.value != null }
    org.junit.Assert.assertFalse("JPY" in repo.getSettingsOnce().enabledCurrencyCodes)
    vm.clearActionMessage()
    vm.undoLastDelete()
    drainUntil { vm.actionMessage.value != null }
    org.junit.Assert.assertTrue("JPY" in repo.getSettingsOnce().enabledCurrencyCodes)
  }

  @Test
  fun `thread local formatters, O1 epoch range filters and new settings search entries work accurately`() {
    val cal2025 = java.util.Calendar.getInstance().apply {
      set(2025, java.util.Calendar.DECEMBER, 31, 23, 59, 59)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val cal2026 = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.JANUARY, 1, 0, 0, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val header2025 = com.example.ui.components.formatDateHeader(cal2025.timeInMillis, nowYear = 2026)
    val header2026 = com.example.ui.components.formatDateHeader(cal2026.timeInMillis, nowYear = 2026)
    org.junit.Assert.assertTrue(header2025.startsWith("2025年12月31日"))
    org.junit.Assert.assertTrue(header2026.startsWith("01月01日"))
    assertEquals("23:59:59", com.example.ui.components.formatTimeShort(cal2025.timeInMillis))

    val tx2025 = com.example.model.LedgerTransaction(id = "y25", amountCents = 100L, occurredAtMillis = cal2025.timeInMillis)
    val tx2026 = com.example.model.LedgerTransaction(id = "y26", amountCents = 200L, occurredAtMillis = cal2026.timeInMillis)
    val all = listOf(tx2025, tx2026)
    assertEquals(listOf("y25"), com.example.viewmodel.LedgerViewModel.filterByYear(all, 2025).map { it.id })
    assertEquals(listOf("y26"), com.example.viewmodel.LedgerViewModel.filterByYear(all, 2026).map { it.id })
    assertEquals(listOf("y25"), com.example.viewmodel.LedgerViewModel.filterByMonth(all, 2025, java.util.Calendar.DECEMBER).map { it.id })
    assertEquals(listOf("y26"), com.example.viewmodel.LedgerViewModel.filterByDay(all, cal2026.timeInMillis).map { it.id })

    val tipHits = com.example.ui.SettingsSearchCatalog.search("功能提示")
    org.junit.Assert.assertTrue(tipHits.any { it.sectionKey == "feature_tips_management" })
    val batchHits = com.example.ui.SettingsSearchCatalog.search("加载 条数")
    org.junit.Assert.assertTrue(batchHits.any { it.sectionKey == "home_batch_load_settings" })
  }

  @Test
  fun `currency catalog covers every selected source currency and custom source dynamically adds returned currencies`() {
    val emptyRates = com.example.model.ExchangeRateState()
    // Frankfurter covers all 31 ECB currencies (CNY + 30 foreign)
    val frankfurterList = com.example.model.CurrencyCatalog.availableCurrenciesForSource(
      source = com.example.model.ExchangeRateSource.FRANKFURTER,
      exchangeRates = emptyRates
    )
    assertEquals(31, frankfurterList.size)
    org.junit.Assert.assertTrue(frankfurterList.any { it.code == "ISK" })
    org.junit.Assert.assertFalse(frankfurterList.any { it.code == "VND" })

    // ExchangeRate-API covers full ISO-4217 set (>120 currencies including exotic codes like KZT, UAH, ARS)
    val erApiList = com.example.model.CurrencyCatalog.availableCurrenciesForSource(
      source = com.example.model.ExchangeRateSource.EXCHANGE_RATE_API,
      exchangeRates = emptyRates
    )
    org.junit.Assert.assertTrue(erApiList.size >= 120)
    org.junit.Assert.assertTrue(erApiList.any { it.code == "KZT" })
    org.junit.Assert.assertTrue(erApiList.any { it.code == "UAH" })

    // Currency-API covers ISO + crypto/metals (BTC, ETH, USDT, XAU)
    val currencyApiList = com.example.model.CurrencyCatalog.availableCurrenciesForSource(
      source = com.example.model.ExchangeRateSource.CURRENCY_API_JSDELIVR,
      exchangeRates = emptyRates
    )
    org.junit.Assert.assertTrue(currencyApiList.size > erApiList.size)
    org.junit.Assert.assertTrue(currencyApiList.any { it.code == "BTC" })
    org.junit.Assert.assertTrue(currencyApiList.any { it.code == "USDT" })
    org.junit.Assert.assertTrue(currencyApiList.any { it.code == "XAU" })

    // Custom source dynamically parses and exposes all currencies returned by its JSON payload
    val customPayload = """
      {
        "date": "2026-11-22",
        "rates": {
          "CNY": 1.0,
          "USD": 0.138,
          "KZT": 68.5,
          "USDT": 0.1381,
          "MYC": 2.5
        }
      }
    """.trimIndent()
    val parsed = com.example.data.CurrencyExchangeManager.parseRatesPayload(
      bodyText = customPayload,
      fallbackSourceLabel = "Custom",
      targetForeignCodes = null
    )
    org.junit.Assert.assertNotNull(parsed)
    val nonNullParsed = parsed!!
    org.junit.Assert.assertTrue("KZT" in nonNullParsed.availableCurrencyCodes)
    org.junit.Assert.assertTrue("USDT" in nonNullParsed.availableCurrencyCodes)
    org.junit.Assert.assertTrue("MYC" in nonNullParsed.availableCurrencyCodes)
    org.junit.Assert.assertTrue(nonNullParsed.ratesToCny.containsKey("MYC"))
    org.junit.Assert.assertTrue((nonNullParsed.ratesToCny["MYC"] ?: 0.0) > 0.39 && (nonNullParsed.ratesToCny["MYC"] ?: 0.0) < 0.41)

    val customState = com.example.model.ExchangeRateState(
      ratesToCny = nonNullParsed.ratesToCny,
      sourceId = com.example.model.ExchangeRateSource.CUSTOM.name,
      sourceAvailableCodes = nonNullParsed.availableCurrencyCodes
    )
    val customList = com.example.model.CurrencyCatalog.availableCurrenciesForSource(
      source = com.example.model.ExchangeRateSource.CUSTOM,
      exchangeRates = customState
    )
    assertEquals(setOf("CNY", "USD", "KZT", "USDT", "MYC"), customList.map { it.code }.toSet())

    // Dynamic codes remain valid when enabled in settings
    val normalized = com.example.model.CurrencyCatalog.normalizeEnabledForeignCodes(listOf("USD", "MYC", "USDT"))
    assertEquals(listOf("USD", "MYC", "USDT"), normalized)
    val resolvedCustom = com.example.model.CurrencyCatalog.resolve("MYC")
    assertEquals("MYC", resolvedCustom.code)

    // Expanded feature tips catalog includes all new non-settings toggleable tips
    val tipIds = com.example.model.FeatureTipsCatalog.allTips.map { it.id }.toSet()
    org.junit.Assert.assertTrue(com.example.model.FeatureTipsCatalog.HOME_SEARCH_SYNTAX in tipIds)
    org.junit.Assert.assertTrue(com.example.model.FeatureTipsCatalog.BOOKS_OVERVIEW in tipIds)
    org.junit.Assert.assertTrue(com.example.model.FeatureTipsCatalog.ANALYTICS_PIE_PINCH in tipIds)
    org.junit.Assert.assertTrue(com.example.model.FeatureTipsCatalog.CALENDAR_GESTURES in tipIds)
  }

  @Test
  fun `chinese currency units and multi-amount notification extract only primary transaction amount and currency`() {
    val bocDualCurrency =
      "您的借记卡****(尾号)于1123发生双信息无卡消费交易，金额10.49新加坡元，已冻结55.50人民币。【中国银行】"
    org.junit.Assert.assertTrue(com.example.parser.ChinaNotificationParser.hasExplicitAmountUnit(bocDualCurrency))
    assertEquals(1049L, com.example.parser.ChinaNotificationParser.extractExplicitUnitAmountCents(bocDualCurrency))

    val parsedBoc = com.example.parser.ChinaNotificationParser.parse(
      packageName = "com.android.mms",
      title = "95566",
      content = bocDualCurrency
    )
    org.junit.Assert.assertNotNull(parsedBoc)
    assertEquals(1049L, parsedBoc!!.amountCents)
    assertEquals("SGD", parsedBoc.currencyCode)
    assertEquals(com.example.model.TransactionType.EXPENSE, parsedBoc.type)
    assertEquals("中国银行", parsedBoc.channel)

    // Reverse clause order: frozen amount first, actual expense second
    val reverseOrder = "已冻结55.50人民币，您的借记卡于今日消费支出10.49新加坡元。【中国银行】"
    val parsedReverse = com.example.parser.ChinaNotificationParser.parse("", "", reverseOrder)
    org.junit.Assert.assertNotNull(parsedReverse)
    assertEquals(1049L, parsedReverse!!.amountCents)
    assertEquals("SGD", parsedReverse.currencyCode)

    // Original price + discount + actual expense
    val discountText = "支付宝付款：原价100.00元，优惠立减15.00元，实际支出85.00元。"
    val parsedDiscount = com.example.parser.ChinaNotificationParser.parse("com.eg.android.AlipayGphone", "支付宝", discountText)
    org.junit.Assert.assertNotNull(parsedDiscount)
    assertEquals(8500L, parsedDiscount!!.amountCents)
    assertEquals("CNY", parsedDiscount.currencyCode)

    // Foreign consumption + converted RMB equivalent
    val convertedText = "【中国银行】您尾号3048信用卡消费10.49新加坡元，折合人民币55.50元。"
    val parsedConverted = com.example.parser.ChinaNotificationParser.parse("", "", convertedText)
    org.junit.Assert.assertNotNull(parsedConverted)
    assertEquals(1049L, parsedConverted!!.amountCents)
    assertEquals("SGD", parsedConverted.currencyCode)

    // Suffix Chinese currency unit "55.50人民币" is recognized with explicit unit
    org.junit.Assert.assertTrue(com.example.parser.ChinaNotificationParser.hasExplicitAmountUnit("本次消费55.50人民币"))
    assertEquals(5550L, com.example.parser.ChinaNotificationParser.extractExplicitUnitAmountCents("本次消费55.50人民币"))

    // Unitless noise remains rejected
    org.junit.Assert.assertFalse(com.example.parser.ChinaNotificationParser.hasExplicitAmountUnit("[3条] 中国移动10086 流量剩余12.5GB"))
    org.junit.Assert.assertNull(com.example.parser.ChinaNotificationParser.extractExplicitUnitAmountCents("[3条] 中国移动10086 流量剩余12.5GB"))
  }

  @Test
  fun `rule keyword channel mapping never falls back to unknown channel even with shadowing fullwidth pipes or sms package`() {
    val rules = listOf(
      // Higher-priority rule with empty channel (simulating generic transfer/expense rule)
      com.example.model.CustomParsingRule(
        id = "r-high-blank",
        name = "通用消费",
        packageFilter = "*",
        keywordPattern = "消费",
        channelName = "",
        defaultCategory = "自动",
        defaultType = "AUTO",
        priority = 60,
        enabled = true
      ),
      // Higher-priority rule accidentally saved with "未知渠道"
      com.example.model.CustomParsingRule(
        id = "r-high-unknown",
        name = "错误默认渠道",
        packageFilter = "*",
        keywordPattern = "交易",
        channelName = "未知渠道",
        defaultCategory = "自动",
        defaultType = "AUTO",
        priority = 70,
        enabled = true
      ),
      // Target bank rule with keyword "中国银行" -> channel "中国银行"
      com.example.model.CustomParsingRule(
        id = "r-boc",
        name = "中国银行规则",
        packageFilter = "*",
        keywordPattern = "中国银行",
        channelName = "中国银行",
        defaultCategory = "其他",
        defaultType = "AUTO",
        priority = 10,
        enabled = true
      ),
      // Custom regional bank with full-width pipe and specific app packageFilter
      com.example.model.CustomParsingRule(
        id = "r-starbank",
        name = "星河银行",
        packageFilter = "com.custom.starbank",
        keywordPattern = "星河村镇银行｜96001",
        channelName = "星河村镇银行",
        defaultCategory = "购物消费",
        defaultType = "EXPENSE",
        priority = 15,
        enabled = true
      )
    )

    val parsedBoc = com.example.parser.ChinaNotificationParser.parse(
      packageName = "",
      title = "",
      content = "您的借记卡于1123发生消费交易，金额10.49新加坡元，已冻结55.50人民币。【中国银行】",
      customRules = rules
    )
    org.junit.Assert.assertNotNull(parsedBoc)
    assertEquals("中国银行", parsedBoc!!.channel)

    // Specific-package rule with full-width pipe still resolves channel for SMS & test bench
    val parsedStarSms = com.example.parser.ChinaNotificationParser.parse(
      packageName = "com.android.mms",
      title = "96001",
      content = "您尾号2211账户消费支出68.00元。",
      customRules = rules
    )
    org.junit.Assert.assertNotNull(parsedStarSms)
    assertEquals("星河村镇银行", parsedStarSms!!.channel)

    val parsedStarBench = com.example.parser.ChinaNotificationParser.parse(
      packageName = "",
      title = "",
      content = "【星河村镇银行】您消费32.50元。",
      customRules = rules
    )
    org.junit.Assert.assertNotNull(parsedStarBench)
    assertEquals("星河村镇银行", parsedStarBench!!.channel)
  }

  @Test
  fun `custom monitored apps policy filters notifications accurately and round-trips through datastore and backup`() = kotlinx.coroutines.runBlocking<Unit> {
    // 1. Default policy: built-in watched apps always processed; unknown apps rely on fallback + likely financial
    val defaultPolicy = com.example.service.AutoBookkeepingNotificationService.MonitoredAppPolicy(
      customized = false,
      packages = emptySet(),
      allowOtherFinancialAppsFallback = true
    )
    org.junit.Assert.assertTrue(
      com.example.service.AutoBookkeepingNotificationService.shouldProcessNotificationPackage(
        packageName = "com.tencent.mm",
        isSelfTest = false,
        policy = defaultPolicy,
        isLikelyFinancial = false
      )
    )
    org.junit.Assert.assertTrue(
      com.example.service.AutoBookkeepingNotificationService.shouldProcessNotificationPackage(
        packageName = "com.unknown.app",
        isSelfTest = false,
        policy = defaultPolicy,
        isLikelyFinancial = true
      )
    )
    org.junit.Assert.assertFalse(
      com.example.service.AutoBookkeepingNotificationService.shouldProcessNotificationPackage(
        packageName = "com.unknown.app",
        isSelfTest = false,
        policy = defaultPolicy,
        isLikelyFinancial = false
      )
    )

    // 2. Strict custom policy (fallback disabled): only selected packages + self-test pass
    val strictCustomPolicy = com.example.service.AutoBookkeepingNotificationService.MonitoredAppPolicy(
      customized = true,
      packages = setOf("com.custom.bank", "com.android.mms"),
      allowOtherFinancialAppsFallback = false
    )
    org.junit.Assert.assertTrue(
      com.example.service.AutoBookkeepingNotificationService.shouldProcessNotificationPackage(
        packageName = "com.custom.bank",
        isSelfTest = false,
        policy = strictCustomPolicy,
        isLikelyFinancial = false
      )
    )
    org.junit.Assert.assertFalse(
      com.example.service.AutoBookkeepingNotificationService.shouldProcessNotificationPackage(
        packageName = "com.tencent.mm",
        isSelfTest = false,
        policy = strictCustomPolicy,
        isLikelyFinancial = true
      )
    )
    org.junit.Assert.assertTrue(
      com.example.service.AutoBookkeepingNotificationService.shouldProcessNotificationPackage(
        packageName = "com.live150yo.lvshuiqingshan",
        isSelfTest = true,
        policy = strictCustomPolicy,
        isLikelyFinancial = false
      )
    )

    // 3. SettingsDataStore + LedgerBackupCodec round-trip
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = com.example.data.SettingsDataStore(context)
    val original = store.getBudgetSettingsOnce()
    val updated = original.copy(
      monitoredAppsCustomized = true,
      monitoredAppPackages = listOf("com.tencent.mm", "com.eg.android.AlipayGphone", "com.custom.bank"),
      allowOtherFinancialAppsFallback = false
    )
    store.saveBudgetSettings(updated)
    val loaded = store.getBudgetSettingsOnce()
    org.junit.Assert.assertTrue(loaded.monitoredAppsCustomized)
    assertEquals(listOf("com.tencent.mm", "com.eg.android.AlipayGphone", "com.custom.bank"), loaded.monitoredAppPackages)
    org.junit.Assert.assertFalse(loaded.allowOtherFinancialAppsFallback)

    val backupJson = com.example.data.LedgerBackupCodec.encodeBackupJson(
      transactions = emptyList(),
      rules = emptyList(),
      settings = loaded
    )
    val decoded = com.example.data.LedgerBackupCodec.decodeBackupJson(backupJson)
    val decodedSettings = requireNotNull(decoded.settings)
    org.junit.Assert.assertTrue(decodedSettings.monitoredAppsCustomized)
    assertEquals(loaded.monitoredAppPackages, decodedSettings.monitoredAppPackages)
    org.junit.Assert.assertFalse(decodedSettings.allowOtherFinancialAppsFallback)

    // 4. Settings search catalog covers monitored apps
    val searchHits = com.example.ui.SettingsSearchCatalog.search("监听应用")
    org.junit.Assert.assertTrue(searchHits.any { it.route == com.example.ui.ROUTE_SETTINGS_MONITORED_APPS })

    // Restore original settings
    store.saveBudgetSettings(original)
  }

  @Test
  fun `category and channel hierarchy supports attribution cycle prevention parent-only display and consolidation`() = kotlinx.coroutines.runBlocking<Unit> {
    // 1. Cycle prevention & display resolution
    val catMap = mapOf("咖啡奶茶" to "餐饮美食")
    org.junit.Assert.assertFalse(com.example.model.CategoryCatalog.canAssignParent("餐饮美食", "咖啡奶茶", catMap))
    org.junit.Assert.assertFalse(com.example.model.CategoryCatalog.canAssignParent("餐饮美食", "餐饮美食", catMap))
    org.junit.Assert.assertTrue(com.example.model.CategoryCatalog.canAssignParent("夜宵", "餐饮美食", catMap))

    assertEquals("餐饮美食", com.example.model.CategoryCatalog.resolveDisplayCategory("咖啡奶茶", catMap, showChildren = false))
    assertEquals("咖啡奶茶", com.example.model.CategoryCatalog.resolveDisplayCategory("咖啡奶茶", catMap, showChildren = true))

    val chMap = com.example.model.ChannelCatalog.defaultChannelParentMap
    assertEquals("支付宝", com.example.model.ChannelCatalog.resolveDisplayChannel("花呗", chMap, showChildren = false))
    assertEquals("花呗", com.example.model.ChannelCatalog.resolveDisplayChannel("花呗", chMap, showChildren = true))
    assertEquals("微信支付", com.example.model.ChannelCatalog.resolveDisplayChannel("零钱通", chMap, showChildren = false))

    // 2. Repository consolidation rewrites transactions & rules into parent
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.LedgerRepository(context)
    val store = com.example.data.SettingsDataStore(context)
    val originalSettings = store.getBudgetSettingsOnce()

    val tx = com.example.model.LedgerTransaction(
      amountCents = 2500L,
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "咖啡奶茶",
      merchant = "瑞幸咖啡",
      channel = "花呗"
    )
    repo.addTransaction(tx).getOrThrow()
    repo.setCategoryParent("咖啡奶茶", "餐饮美食").getOrThrow()
    repo.setChannelParent("花呗", "支付宝").getOrThrow()

    val catSync = repo.consolidateCategoryChildrenToParent("餐饮美食").getOrThrow()
    org.junit.Assert.assertTrue(catSync.updatedTransactions >= 1)
    val chSync = repo.consolidateChannelChildrenToParent("支付宝").getOrThrow()
    org.junit.Assert.assertTrue(chSync.updatedTransactions >= 1)

    val allTxs = repo.getAllTransactionsOnce()
    val reloaded = allTxs.first { it.id == tx.id }
    assertEquals("餐饮美食", reloaded.category)
    assertEquals("支付宝", reloaded.channel)

    // Cleanup test transaction & settings
    repo.deleteTransaction(tx.id)
    store.saveBudgetSettings(originalSettings)
  }

  @Test
  fun `custom bill fields support ordering folding full-text search Room v9 persistence and AI prompt sync`() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.LedgerRepository(context)
    val store = com.example.data.SettingsDataStore(context)
    val originalSettings = store.getBudgetSettingsOnce()

    val customField = com.example.model.BillFieldConfig(
      id = "custom_project_code",
      label = "项目编号",
      isBuiltIn = false,
      isMajorItem = false,
      foldedInMore = true,
      enabled = true
    )
    val updatedFields = com.example.model.BillFieldCatalog.normalizeFields(
      listOf(customField) + com.example.model.BillFieldCatalog.defaultFields
    )
    repo.saveBillFieldsConfig(updatedFields).getOrThrow()

    val reloadedSettings = store.getBudgetSettingsOnce()
    org.junit.Assert.assertTrue(reloadedSettings.billFieldsConfig.any { it.id == "custom_project_code" && it.foldedInMore })

    // Verify AI prompt includes custom field key & label
    val aiPrompt = com.example.data.AiUnderstandingManager.buildDefaultSystemPrompt(
      billFieldsConfig = reloadedSettings.billFieldsConfig,
      categoryParentMap = reloadedSettings.categoryParentMap,
      channelParentMap = reloadedSettings.channelParentMap
    )
    org.junit.Assert.assertTrue(aiPrompt.contains("项目编号"))
    org.junit.Assert.assertTrue(aiPrompt.contains("custom_project_code"))

    // Persist transaction with customFields in Room v9 and verify search matches custom field & parent hierarchy
    val tx = com.example.model.LedgerTransaction(
      amountCents = 8800L,
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "咖啡奶茶",
      merchant = "星巴克",
      channel = "花呗",
      customFields = mapOf("custom_project_code" to "PRJ-2026-ALPHA")
    )
    repo.addTransaction(tx).getOrThrow()
    val loadedTx = repo.getAllTransactionsOnce().first { it.id == tx.id }
    assertEquals("PRJ-2026-ALPHA", loadedTx.customFields["custom_project_code"])

    val catParents = mapOf("咖啡奶茶" to "餐饮美食")
    val chParents = mapOf("花呗" to "支付宝")
    org.junit.Assert.assertTrue(loadedTx.matchesSearchQuery("PRJ-2026-ALPHA", catParents, chParents))
    org.junit.Assert.assertTrue(loadedTx.matchesSearchQuery("餐饮美食 支付宝", catParents, chParents))
    org.junit.Assert.assertFalse(loadedTx.matchesSearchQuery("不存在关键词", catParents, chParents))

    // Cleanup
    repo.deleteTransaction(tx.id)
    store.saveBudgetSettings(originalSettings)
  }

  @Test
  fun `notification logs persist with pagination search count-time retention and unlimited option`() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.LedgerRepository(context)
    val store = com.example.data.SettingsDataStore(context)
    val originalSettings = store.getBudgetSettingsOnce()

    repo.clearAllNotificationLogs().getOrThrow()
    // Unlimited & permanent retention (0 / 0)
    store.saveBudgetSettings(originalSettings.copy(notificationLogMaxCount = 0, notificationLogMaxDays = 0))
    val now = System.currentTimeMillis()
    for (i in 1..6) {
      repo.recordNotificationLog(
        packageName = "com.eg.android.AlipayGphone",
        appLabel = "支付宝",
        title = "交易提醒 #$i",
        content = "您支出${i * 10}.00元",
        status = "已自动入账",
        summary = "¥${i * 10}.00",
        capturedAtMillis = now - (6 - i) * 60_000L
      ).getOrThrow()
    }
    assertEquals(6, repo.observeNotificationLogsCount().first())

    // Pagination & search
    val page1 = repo.loadNotificationLogsPage(query = "", limit = 4, offset = 0)
    val page2 = repo.loadNotificationLogsPage(query = "", limit = 4, offset = 4)
    assertEquals(4, page1.size)
    assertEquals(2, page2.size)
    val searchHits = repo.loadNotificationLogsPage(query = "#5", limit = 20, offset = 0)
    assertEquals(1, searchHits.size)
    org.junit.Assert.assertTrue(searchHits.first().title.contains("#5"))

    // Count retention cap prunes oldest entries
    repo.updateNotificationLogRetention(maxCount = 3, maxDays = 0).getOrThrow()
    assertEquals(3, repo.observeNotificationLogsCount().first())

    // Cleanup
    repo.clearAllNotificationLogs()
    store.saveBudgetSettings(originalSettings)
  }

  @Test
  fun `encrypted zip backup round-trips with default key custom password and legacy plaintext json compatibility`() {
    val sampleJson = com.example.data.LedgerBackupCodec.encodeBackupJson(
      transactions = emptyList(),
      rules = emptyList(),
      settings = com.example.model.BudgetSettings(monthlyBudgetCents = 666_600L)
    )

    // 1. Default app-key encrypted ZIP round-trip
    val defaultZipBytes = com.example.data.BackupCryptoCodec.encodeEncryptedZip(sampleJson, password = null)
    org.junit.Assert.assertTrue(com.example.data.BackupCryptoCodec.isZipArchive(defaultZipBytes))
    // Ensure plaintext JSON is NOT exposed verbatim inside the encrypted binary
    org.junit.Assert.assertFalse(String(defaultZipBytes, Charsets.UTF_8).contains("666600"))
    val decodedDefault = com.example.data.BackupCryptoCodec.decodeBackupBytes(defaultZipBytes, password = null)
    org.junit.Assert.assertTrue(decodedDefault.wasEncryptedZip)
    org.junit.Assert.assertFalse(decodedDefault.usedCustomPassword)
    assertEquals(sampleJson, decodedDefault.jsonText)

    // 2. Custom password encrypted ZIP round-trip + missing/wrong password rejection
    val pwdZipBytes = com.example.data.BackupCryptoCodec.encodeEncryptedZip(sampleJson, password = "MyStrongPassword!2026")
    org.junit.Assert.assertTrue(com.example.data.BackupCryptoCodec.requiresPassword(pwdZipBytes))
    var caughtRequired = false
    try {
      com.example.data.BackupCryptoCodec.decodeBackupBytes(pwdZipBytes, password = null)
    } catch (_: com.example.data.BackupPasswordRequiredException) {
      caughtRequired = true
    }
    org.junit.Assert.assertTrue(caughtRequired)

    var caughtWrong = false
    try {
      com.example.data.BackupCryptoCodec.decodeBackupBytes(pwdZipBytes, password = "WrongPassword")
    } catch (_: com.example.data.BackupWrongPasswordException) {
      caughtWrong = true
    }
    org.junit.Assert.assertTrue(caughtWrong)

    val decodedWithPwd = com.example.data.BackupCryptoCodec.decodeBackupBytes(pwdZipBytes, password = "MyStrongPassword!2026")
    org.junit.Assert.assertTrue(decodedWithPwd.usedCustomPassword)
    assertEquals(sampleJson, decodedWithPwd.jsonText)

    // 3. Backward-compatible legacy plaintext JSON import
    val legacyDecoded = com.example.data.BackupCryptoCodec.decodeBackupBytes(sampleJson.toByteArray(Charsets.UTF_8), password = null)
    org.junit.Assert.assertFalse(legacyDecoded.wasEncryptedZip)
    assertEquals(sampleJson, legacyDecoded.jsonText)
  }

  @Test
  fun `fine-grained haptic settings persist and settings search covers personalization bill fields logs and haptics`() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = com.example.data.SettingsDataStore(context)
    val originalAppearance = store.getAppearanceSettingsOnce()

    val customAppearance = originalAppearance.copy(
      hapticEnabled = true,
      hapticFollowSystem = false,
      hapticIntensity = 0.75f,
      hapticOnClick = false,
      hapticOnGesture = true,
      hapticOnLongPress = true,
      hapticOnDragReorder = true,
      hapticOnToggle = false,
      hapticOnDestructive = true
    )
    store.saveAppearanceSettings(customAppearance)
    val reloaded = store.getAppearanceSettingsOnce()
    org.junit.Assert.assertTrue(reloaded.hapticEnabled)
    org.junit.Assert.assertFalse(reloaded.hapticFollowSystem)
    assertEquals(0.75f, reloaded.hapticIntensity, 0.01f)
    org.junit.Assert.assertFalse(reloaded.hapticOnClick)
    org.junit.Assert.assertTrue(reloaded.hapticOnGesture)
    org.junit.Assert.assertTrue(reloaded.hapticOnDragReorder)
    org.junit.Assert.assertFalse(reloaded.hapticOnToggle)

    // Verify notification without valid amount is ignored by parser
    val nonAmountParsed = com.example.parser.ChinaNotificationParser.parse(
      packageName = "com.tencent.mm",
      title = "微信支付",
      content = "账单月报已生成，点击查看详情"
    )
    org.junit.Assert.assertNull(nonAmountParsed)

    // Verify SettingsSearchCatalog covers all new/renamed sections
    org.junit.Assert.assertTrue(com.example.ui.SettingsSearchCatalog.search("个性化设置").any { it.route == com.example.ui.ROUTE_SETTINGS_APPEARANCE_THEME })
    org.junit.Assert.assertTrue(com.example.ui.SettingsSearchCatalog.search("触感反馈").any { it.route == com.example.ui.ROUTE_SETTINGS_APPEARANCE_INTERACTION })
    org.junit.Assert.assertTrue(com.example.ui.SettingsSearchCatalog.search("账单信息").any { it.route == com.example.ui.ROUTE_SETTINGS_BILL_FIELDS })
    org.junit.Assert.assertTrue(com.example.ui.SettingsSearchCatalog.search("通知读取日志").any { it.route == com.example.ui.ROUTE_SETTINGS_NOTIFICATION_LOGS })
    org.junit.Assert.assertTrue(com.example.ui.SettingsSearchCatalog.search("加密").any { it.route == com.example.ui.ROUTE_SETTINGS_LOCAL_BACKUP })

    // Restore original appearance
    store.saveAppearanceSettings(originalAppearance)
  }

  @Test
  fun `notification logs strictly record selected monitored apps and exclude unselected packages`() {
    val customPolicy = com.example.service.AutoBookkeepingNotificationService.MonitoredAppPolicy(
      customized = true,
      packages = setOf("com.tencent.mm", "com.eg.android.AlipayGphone"),
      allowOtherFinancialAppsFallback = true
    )

    // Selected apps and explicit self-test notifications are logged
    org.junit.Assert.assertTrue(
      com.example.service.AutoBookkeepingNotificationService.shouldLogNotificationPackage(
        packageName = "com.tencent.mm",
        isSelfTest = false,
        policy = customPolicy
      )
    )
    org.junit.Assert.assertTrue(
      com.example.service.AutoBookkeepingNotificationService.shouldLogNotificationPackage(
        packageName = "com.example",
        isSelfTest = true,
        policy = customPolicy
      )
    )

    // Unselected app is NOT logged even if financial fallback is enabled
    org.junit.Assert.assertFalse(
      com.example.service.AutoBookkeepingNotificationService.shouldLogNotificationPackage(
        packageName = "com.icbc",
        isSelfTest = false,
        policy = customPolicy
      )
    )
    // Unselected financial app may still be processed by fallback without polluting monitored-only log
    org.junit.Assert.assertTrue(
      com.example.service.AutoBookkeepingNotificationService.shouldProcessNotificationPackage(
        packageName = "com.icbc",
        isSelfTest = false,
        policy = customPolicy,
        isLikelyFinancial = true
      )
    )

    // Default (uncustomized) policy logs default watched payment apps but not unrelated apps
    val defaultPolicy = com.example.service.AutoBookkeepingNotificationService.MonitoredAppPolicy(
      customized = false,
      packages = emptySet(),
      allowOtherFinancialAppsFallback = true
    )
    org.junit.Assert.assertTrue(
      com.example.service.AutoBookkeepingNotificationService.shouldLogNotificationPackage(
        packageName = "com.tencent.mm",
        isSelfTest = false,
        policy = defaultPolicy
      )
    )
    org.junit.Assert.assertFalse(
      com.example.service.AutoBookkeepingNotificationService.shouldLogNotificationPackage(
        packageName = "com.random.unrelated.game",
        isSelfTest = false,
        policy = defaultPolicy
      )
    )
  }

  @Test
  fun `draggable hierarchy math supports multi-item crossing both ways and middle-band hover dwell for nesting`() {
    // 4 items of varying heights stacked vertically
    val bounds = listOf(
      com.example.ui.components.HierarchyItemBounds(top = 0f, height = 100f),   // idx 0: 0..100
      com.example.ui.components.HierarchyItemBounds(top = 110f, height = 160f), // idx 1: 110..270 (tall expanded parent)
      com.example.ui.components.HierarchyItemBounds(top = 280f, height = 100f), // idx 2: 280..380
      com.example.ui.components.HierarchyItemBounds(top = 390f, height = 100f)  // idx 3: 390..490
    )

    // 1. Hovering inside middle dwell zone of item 1 returns HoverForNest (does not swap prematurely)
    val hoverDecision = com.example.ui.components.DraggableHierarchyMath.evaluateDragPosition(
      draggedIdx = 0,
      dragCenterY = 190f, // 50% of item 1
      boundsInOrder = bounds,
      enableNesting = true,
      canNestAtIndex = { true },
      activeNestTargetIdx = null
    )
    assertEquals(com.example.ui.components.DragHitDecision.HoverForNest(1), hoverDecision)

    // 2. Dragging downward past item 1's nest zone (bottom edge >= 78%) swaps across item 1 cleanly
    val crossOneDown = com.example.ui.components.DraggableHierarchyMath.evaluateDragPosition(
      draggedIdx = 0,
      dragCenterY = 250f, // > 110 + 160*0.82 = 241.2
      boundsInOrder = bounds,
      enableNesting = true,
      canNestAtIndex = { true },
      activeNestTargetIdx = null
    )
    assertEquals(com.example.ui.components.DragHitDecision.SwapToIndex(1), crossOneDown)

    // 3. Fast drag downward past multiple items jumps directly across to index 3
    val crossMultiDown = com.example.ui.components.DraggableHierarchyMath.evaluateDragPosition(
      draggedIdx = 0,
      dragCenterY = 475f,
      boundsInOrder = bounds,
      enableNesting = true,
      canNestAtIndex = { true },
      activeNestTargetIdx = null
    )
    assertEquals(com.example.ui.components.DragHitDecision.SwapToIndex(3), crossMultiDown)

    // 4. Dragging upward from index 3 across tall item 1 to index 0 succeeds without stalling
    val crossMultiUp = com.example.ui.components.DraggableHierarchyMath.evaluateDragPosition(
      draggedIdx = 3,
      dragCenterY = 15f,
      boundsInOrder = bounds,
      enableNesting = true,
      canNestAtIndex = { true },
      activeNestTargetIdx = null
    )
    assertEquals(com.example.ui.components.DragHitDecision.SwapToIndex(0), crossMultiUp)

    // 5. With nesting disabled (e.g. bill field order or sibling children), midpoint crossing swaps smoothly
    val noNestSwap = com.example.ui.components.DraggableHierarchyMath.evaluateDragPosition(
      draggedIdx = 0,
      dragCenterY = 200f, // past item 1 center (190)
      boundsInOrder = bounds,
      enableNesting = false,
      canNestAtIndex = { false },
      activeNestTargetIdx = null
    )
    assertEquals(com.example.ui.components.DragHitDecision.SwapToIndex(1), noNestSwap)
    assertEquals(450L, com.example.ui.components.DraggableHierarchyMath.DEFAULT_NEST_DWELL_MS)
  }

  @Test
  fun `category and channel hierarchy display toggles non-destructively and viewModel undo restores modifications`() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.LedgerRepository(context)
    val vm = com.example.viewmodel.LedgerViewModel(repo)
    val originalSettings = repo.observeSettings().first()
    val originalAi = repo.observeAiConfig().first()

    val tx = com.example.model.LedgerTransaction(
      id = "hier-nd-tx-1",
      amountCents = 2800L,
      currencyCode = "CNY",
      type = com.example.model.TransactionType.EXPENSE.name,
      category = "咖啡茶饮",
      merchant = "瑞幸咖啡",
      channel = "花呗子卡",
      occurredAtMillis = 1_763_750_000_000L,
      confirmed = true
    )
    repo.addTransaction(tx).getOrThrow()

    val parentMap = mapOf("咖啡茶饮" to "餐饮美食")
    val channelParentMap = mapOf("花呗子卡" to "支付宝")
    assertEquals(
      listOf("咖啡茶饮"),
      com.example.model.CategoryCatalog.descendantsOf("餐饮美食", listOf("餐饮美食", "咖啡茶饮"), parentMap)
    )
    assertEquals(
      listOf("花呗子卡"),
      com.example.model.ChannelCatalog.descendantsOf("支付宝", listOf("支付宝", "花呗子卡"), channelParentMap)
    )

    // Non-destructive display mode: toggling categoryShowChildren / channelShowChildren changes displayed label only
    assertEquals(
      "咖啡茶饮",
      com.example.model.CategoryCatalog.resolveDisplayCategory("咖啡茶饮", parentMap, showChildren = true)
    )
    assertEquals(
      "餐饮美食",
      com.example.model.CategoryCatalog.resolveDisplayCategory("咖啡茶饮", parentMap, showChildren = false)
    )
    assertEquals(
      "花呗子卡",
      com.example.model.ChannelCatalog.resolveDisplayChannel("花呗子卡", channelParentMap, showChildren = true)
    )
    assertEquals(
      "支付宝",
      com.example.model.ChannelCatalog.resolveDisplayChannel("花呗子卡", channelParentMap, showChildren = false)
    )

    // Raw database record stays untouched when toggling display mode via ViewModel
    vm.setCategoryShowChildren(false)
    var deadline = System.currentTimeMillis() + 3000L
    while (vm.deleteUndoBanner.value == null && System.currentTimeMillis() < deadline) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      kotlinx.coroutines.delay(25)
    }
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    val storedAfterToggle = repo.getTransactionsByIdsOnce(listOf(tx.id)).first()
    assertEquals("咖啡茶饮", storedAfterToggle.category)
    org.junit.Assert.assertFalse(repo.observeSettings().first().categoryShowChildren)
    org.junit.Assert.assertNotNull(vm.deleteUndoBanner.value)

    // Undo restores previous categoryShowChildren setting
    vm.undoLastDelete()
    deadline = System.currentTimeMillis() + 3000L
    while (!repo.observeSettings().first().categoryShowChildren && System.currentTimeMillis() < deadline) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      kotlinx.coroutines.delay(25)
    }
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    org.junit.Assert.assertTrue(repo.observeSettings().first().categoryShowChildren)

    // Adding a custom channel with parent emits undo banner and undo removes it cleanly
    vm.dismissDeleteUndoBanner()
    vm.addCustomChannel("测试子渠道X", syncAiPrompt = false, parentName = "支付宝")
    deadline = System.currentTimeMillis() + 3000L
    while (vm.deleteUndoBanner.value == null && System.currentTimeMillis() < deadline) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      kotlinx.coroutines.delay(25)
    }
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    org.junit.Assert.assertEquals("支付宝", repo.observeSettings().first().channelParentMap["测试子渠道X"])

    vm.undoLastDelete()
    deadline = System.currentTimeMillis() + 3000L
    while (repo.observeSettings().first().channelParentMap.containsKey("测试子渠道X") && System.currentTimeMillis() < deadline) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      kotlinx.coroutines.delay(25)
    }
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    org.junit.Assert.assertFalse(repo.observeSettings().first().channelParentMap.containsKey("测试子渠道X"))

    // Cleanup
    repo.deleteTransaction(tx.id)
    repo.saveSettings(originalSettings)
    repo.saveAiConfig(originalAi)
  }

  @Test
  fun `adaptive launcher icon color resources dynamically differ between light and night modes`() {
    val baseContext = ApplicationProvider.getApplicationContext<Context>()
    val lightConfig = android.content.res.Configuration(baseContext.resources.configuration).apply {
      uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
        android.content.res.Configuration.UI_MODE_NIGHT_NO
    }
    val nightConfig = android.content.res.Configuration(baseContext.resources.configuration).apply {
      uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    val lightCtx = baseContext.createConfigurationContext(lightConfig)
    val nightCtx = baseContext.createConfigurationContext(nightConfig)

    val lightDefaultBg = lightCtx.getColor(R.color.launcher_default_bg)
    val nightDefaultBg = nightCtx.getColor(R.color.launcher_default_bg)
    org.junit.Assert.assertNotEquals(lightDefaultBg, nightDefaultBg)

    val lightInkBg = lightCtx.getColor(R.color.launcher_ink_bg)
    val nightInkBg = nightCtx.getColor(R.color.launcher_ink_bg)
    org.junit.Assert.assertNotEquals(lightInkBg, nightInkBg)

    val lightMinimalBg = lightCtx.getColor(R.color.launcher_minimal_bg)
    val nightMinimalBg = nightCtx.getColor(R.color.launcher_minimal_bg)
    org.junit.Assert.assertNotEquals(lightMinimalBg, nightMinimalBg)

    val lightVintageBg = lightCtx.getColor(R.color.launcher_vintage_bg)
    val nightVintageBg = nightCtx.getColor(R.color.launcher_vintage_bg)
    org.junit.Assert.assertNotEquals(lightVintageBg, nightVintageBg)
  }

  @Test
  fun `expanded amount vocabulary detects remittance notifications like 发起人民币元 2,000_00汇款 while ignoring noise`() {
    val remittanceMsg = "您尾号1234账户向张三发起人民币元 2,000.00汇款【招商银行】"
    org.junit.Assert.assertTrue(
      com.example.parser.ChinaNotificationParser.hasExplicitAmountUnit(remittanceMsg)
    )
    val parsedRemit = com.example.parser.ChinaNotificationParser.parse(
      packageName = "cmb.pb",
      title = "招商银行",
      content = remittanceMsg,
      enableLocalSemanticPass = true
    )
    org.junit.Assert.assertNotNull(parsedRemit)
    assertEquals(200_000L, parsedRemit!!.amountCents)
    assertEquals("CNY", parsedRemit.currencyCode)
    assertEquals(com.example.model.TransactionType.EXPENSE, parsedRemit.type)

    // Parenthesized currency unit and cue-labeled formatted decimal amounts
    org.junit.Assert.assertTrue(
      com.example.parser.ChinaNotificationParser.hasExplicitAmountUnit("账户划扣人民币(元)：1,500.50")
    )
    org.junit.Assert.assertTrue(
      com.example.parser.ChinaNotificationParser.hasExplicitAmountUnit("尾号8899交易金额 350.00")
    )
    org.junit.Assert.assertTrue(
      com.example.parser.ChinaNotificationParser.hasExplicitAmountUnit("电汇出账 港币元 880.00")
    )

    // Unitless non-financial noise must remain rejected
    org.junit.Assert.assertFalse(
      com.example.parser.ChinaNotificationParser.hasExplicitAmountUnit("[3条] 中国移动10086：您的话费套餐剩余12.5GB")
    )
    org.junit.Assert.assertFalse(
      com.example.parser.ChinaNotificationParser.hasExplicitAmountUnit("您的验证码为 482910，5分钟内有效")
    )
    org.junit.Assert.assertFalse(
      com.example.parser.ChinaNotificationParser.hasExplicitAmountUnit("订单号 20261125009911 已发货")
    )
  }

  @Test
  fun `local model catalog removes 404 bge model and provides LFM2_5 350M Q4_K_M under 350MB`() {
    val ids = com.example.data.LocalModelCatalog.presets.map { it.id }
    org.junit.Assert.assertFalse(ids.contains("bge-small-zh-v15"))
    org.junit.Assert.assertTrue(ids.contains("lfm25-350m-q4km"))

    val lfm = com.example.data.LocalModelCatalog.resolve("lfm25-350m-q4km")
    assertEquals("LiquidAI/LFM2.5-350M-GGUF", lfm.repoId)
    org.junit.Assert.assertTrue(lfm.files.any { it.fileName.endsWith("LFM2.5-350M-Q4_K_M.gguf") })

    // Legacy removed id falls back gracefully to default LFM2.5-350M
    val migrated = com.example.data.LocalModelCatalog.resolve("bge-small-zh-v15")
    assertEquals(com.example.data.LocalModelCatalog.DEFAULT_LOCAL_MODEL_ID, migrated.id)
    assertEquals("lfm25-350m-q4km", com.example.model.AiConfig().localModelId)
  }

  @Test
  fun `manual forced AI recognition bypasses unit gate and updates notification log status`() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val aiManager = com.example.data.AiUnderstandingManager()
    val repo = com.example.data.LedgerRepository(context)
    val originalAi = repo.observeAiConfig().first()

    // Prepare a tiny stub local model file for ernie-3.0-nano-zh so local parsing runs offline
    val modelDir = aiManager.modelDirectory(context, "ernie-3.0-nano-zh").apply { mkdirs() }
    val configFile = java.io.File(modelDir, "config.json").apply { writeText("""{"vocab_size":18000}""") }
    val vocabFile = java.io.File(modelDir, "vocab.txt").apply { writeText("消费\n汇款\n支付宝\n") }

    try {
      // Without bypass, unitless amount text is rejected by amount-unit gate
      val blocked = aiManager.parseWithLocalModel(
        context = context,
        modelId = "ernie-3.0-nano-zh",
        packageName = "com.eg.android.AlipayGphone",
        title = "账单提醒",
        content = "特殊通知 88.50",
        bypassAmountUnitGate = false
      )
      org.junit.Assert.assertTrue(blocked.isFailure)
      org.junit.Assert.assertTrue(blocked.exceptionOrNull()?.message?.contains("金额单位") == true)

      // With bypassAmountUnitGate = true, manual forced AI bypasses the unit gate (never fails with unit gate error)
      val forcedUnitless = aiManager.parseWithLocalModel(
        context = context,
        modelId = "ernie-3.0-nano-zh",
        packageName = "com.eg.android.AlipayGphone",
        title = "账单提醒",
        content = "特殊通知 88.50",
        bypassAmountUnitGate = true
      )
      org.junit.Assert.assertTrue(forcedUnitless.isSuccess)

      val forced = aiManager.parseWithLocalModel(
        context = context,
        modelId = "ernie-3.0-nano-zh",
        packageName = "com.eg.android.AlipayGphone",
        title = "账单提醒",
        content = "发起人民币元 2,000.00汇款",
        bypassAmountUnitGate = true
      )
      org.junit.Assert.assertTrue(forced.isSuccess)
      assertEquals(200_000L, forced.getOrThrow().amountCents)

      // Verify repository notification log status update persists cleanly
      repo.recordNotificationLog(
        packageName = "cmb.pb",
        appLabel = "招商银行",
        title = "动账提醒",
        content = "发起人民币元 2,000.00汇款",
        status = "不含有效金额，已自动忽略",
        summary = "未检测到有效动账金额"
      )
      val logs = repo.loadNotificationLogsPage(query = "发起人民币元", limit = 10, offset = 0)
      org.junit.Assert.assertTrue(logs.isNotEmpty())
      val logId = logs.first().id
      repo.updateNotificationLogStatus(logId, "手动AI已入账", "支出 ¥2000.00 · 招商银行").getOrThrow()
      val updated = repo.loadNotificationLogsPage(query = "手动AI已入账", limit = 10, offset = 0)
      org.junit.Assert.assertTrue(updated.any { it.id == logId && it.status == "手动AI已入账" })
    } finally {
      configFile.delete()
      vocabFile.delete()
      modelDir.delete()
      repo.saveAiConfig(originalAi)
    }
  }

  @Test
  fun `unified adaptive icon resources resolve and render non-empty bitmaps for every AppIconStyle`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.model.AppIconStyle.entries.forEach { style ->
      val res = com.example.util.DesktopIconSyncManager.adaptiveIconResourcesFor(style)
      org.junit.Assert.assertTrue(res.backgroundRes != 0)
      org.junit.Assert.assertTrue(res.foregroundRes != 0)
      val lightBmp = com.example.util.DesktopIconSyncManager.renderAdaptiveResourceBitmap(
        context = context,
        styleId = style.id,
        isDark = false,
        sizePx = 96
      )
      val darkBmp = com.example.util.DesktopIconSyncManager.renderAdaptiveResourceBitmap(
        context = context,
        styleId = style.id,
        isDark = true,
        sizePx = 96
      )
      assertEquals(96, lightBmp.width)
      assertEquals(96, darkBmp.width)
      lightBmp.recycle()
      darkBmp.recycle()
    }
  }
}







