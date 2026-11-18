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
}
