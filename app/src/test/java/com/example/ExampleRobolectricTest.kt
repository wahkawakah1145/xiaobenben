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
      listOf("ernie-3.0-nano-zh", "bge-small-zh-v15", "qwen25-0.5b-instruct"),
      com.example.data.LocalModelCatalog.presets.map { it.id }
    )
    assertEquals(
      "https://hf-mirror.com/BAAI/bge-small-zh-v15",
      com.example.data.LocalModelCatalog.modelRepoUrl("https://hf-mirror.com", "BAAI/bge-small-zh-v15")
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
}
