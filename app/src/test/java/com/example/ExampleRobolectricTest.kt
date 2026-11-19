package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
}
