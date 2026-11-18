package com.example.data

import android.content.Context
import com.example.model.BudgetSettings
import com.example.model.CustomParsingRule
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import com.example.model.WebDavConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class LedgerRepository(
    private val dao: LedgerDao,
    private val settingsStore: SettingsDataStore
) {
    constructor(context: Context) : this(
        dao = LedgerDatabase.getInstance(context).ledgerDao(),
        settingsStore = SettingsDataStore(context.applicationContext)
    )

    fun observeTransactions(): Flow<List<LedgerTransaction>> =
        dao.observeAllTransactions().map { list -> list.map { it.toDomain() } }

    fun observeParsingRules(): Flow<List<CustomParsingRule>> =
        dao.observeAllRules().map { list -> list.map { it.toDomain() } }

    fun observeSettings(): Flow<BudgetSettings> = settingsStore.budgetSettingsFlow

    fun observeWebDavConfig(): Flow<WebDavConfig> = settingsStore.webDavConfigFlow

    suspend fun getSettingsOnce(): BudgetSettings = settingsStore.getBudgetSettingsOnce()

    suspend fun getRulesOnce(): List<CustomParsingRule> =
        dao.getAllRulesOnce().map { it.toDomain() }

    suspend fun getAllTransactionsOnce(): List<LedgerTransaction> =
        dao.getAllTransactionsOnce().map { it.toDomain() }

    suspend fun addTransaction(tx: LedgerTransaction): Result<String> {
        return try {
            require(tx.amountCents in 1L..1_000_000_000L) { "金额需大于 0 且不超过 1000 万元" }
            val now = System.currentTimeMillis()
            val id = tx.id.ifBlank { UUID.randomUUID().toString() }
            val normalized = tx.copy(
                id = id,
                merchant = tx.merchant.trim().ifBlank { "未知商户" }.take(120),
                channel = tx.channel.trim().ifBlank { "未知渠道" }.take(60),
                category = tx.category.trim().ifBlank { "其他" }.take(40),
                cardLastFour = tx.cardLastFour?.trim()?.takeIf { it.isNotEmpty() }?.take(8),
                rawNotification = tx.rawNotification?.trim()?.takeIf { it.isNotEmpty() }?.take(600),
                note = tx.note?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
                occurredAtMillis = tx.occurredAtMillis.coerceAtLeast(1L),
                createdAtMillis = tx.createdAtMillis.takeIf { it > 0L } ?: now,
                updatedAtMillis = now
            )
            dao.upsertTransaction(TransactionEntity.fromDomain(normalized))
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTransaction(
        txId: String,
        amountCents: Long,
        type: TransactionType,
        category: String,
        merchant: String,
        channel: String,
        cardLastFour: String?,
        note: String?,
        occurredAtMillis: Long,
        confirmed: Boolean = true
    ): Result<Unit> {
        return try {
            require(txId.isNotBlank()) { "账单 ID 不能为空" }
            require(amountCents in 1L..1_000_000_000L) { "金额需大于 0 且不超过 1000 万元" }
            val existing = dao.getAllTransactionsOnce().firstOrNull { it.id == txId }
                ?: throw IllegalArgumentException("未找到对应的账单记录")
            val updated = existing.copy(
                amountCents = amountCents,
                type = type.name,
                category = category.trim().ifBlank { "其他" }.take(40),
                merchant = merchant.trim().ifBlank { "未知商户" }.take(120),
                channel = channel.trim().ifBlank { "未知渠道" }.take(60),
                cardLastFour = cardLastFour?.trim()?.takeIf { it.isNotEmpty() }?.take(8),
                note = note?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
                occurredAtMillis = occurredAtMillis.coerceAtLeast(1L),
                confirmed = confirmed,
                updatedAtMillis = System.currentTimeMillis()
            )
            dao.upsertTransaction(updated)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(txId: String): Result<Unit> {
        return try {
            require(txId.isNotBlank()) { "账单 ID 不能为空" }
            dao.deleteTransactionById(txId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun mergeTransactionsFromBackup(items: List<LedgerTransaction>) {
        if (items.isEmpty()) return
        dao.upsertTransactions(items.map { TransactionEntity.fromDomain(it) })
    }

    suspend fun addParsingRule(rule: CustomParsingRule): Result<Unit> {
        return try {
            val name = rule.name.trim()
            val keyword = rule.keywordPattern.trim()
            val channel = rule.channelName.trim()
            require(name.isNotEmpty() && keyword.isNotEmpty() && channel.isNotEmpty()) {
                "规则名称、匹配关键词和渠道不能为空"
            }
            val now = System.currentTimeMillis()
            val normalized = rule.copy(
                id = rule.id.ifBlank { UUID.randomUUID().toString() },
                name = name.take(80),
                packageFilter = rule.packageFilter.trim().ifBlank { "*" }.take(120),
                keywordPattern = keyword.take(200),
                channelName = channel.take(60),
                defaultCategory = rule.defaultCategory.trim().ifBlank { "其他" }.take(40),
                defaultType = TransactionType.fromRaw(rule.defaultType).name,
                createdAtMillis = rule.createdAtMillis.takeIf { it > 0L } ?: now,
                updatedAtMillis = now
            )
            dao.upsertRule(ParsingRuleEntity.fromDomain(normalized))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun mergeRulesFromBackup(rules: List<CustomParsingRule>) {
        if (rules.isEmpty()) return
        dao.upsertRules(rules.map { ParsingRuleEntity.fromDomain(it) })
    }

    suspend fun toggleParsingRule(ruleId: String, enabled: Boolean): Result<Unit> {
        return try {
            dao.setRuleEnabled(ruleId, enabled, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteParsingRule(ruleId: String): Result<Unit> {
        return try {
            dao.deleteRuleById(ruleId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveSettings(settings: BudgetSettings): Result<Unit> {
        return try {
            settingsStore.saveBudgetSettings(settings)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveWebDavConfig(config: WebDavConfig): Result<Unit> {
        return try {
            settingsStore.saveWebDavConfig(config)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateWebDavSyncStatus(millis: Long, status: String) {
        settingsStore.updateWebDavSyncStatus(millis, status)
    }
}
