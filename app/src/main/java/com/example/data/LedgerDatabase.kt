package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.CustomParsingRule
import com.example.model.LedgerTransaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val amountCents: Long,
    val currencyCode: String = "CNY",
    val type: String,
    val category: String,
    val merchant: String,
    val channel: String,
    val cardLastFour: String?,
    val sourceApp: String,
    val parseMethod: String,
    val rawNotification: String?,
    val note: String?,
    val occurredAtMillis: Long,
    val confirmed: Boolean,
    val recordedBaseCurrencyCode: String = "CNY",
    val recordedRateToBase: Double = 0.0,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): LedgerTransaction = LedgerTransaction(
        id = id,
        amountCents = amountCents,
        currencyCode = currencyCode,
        type = type,
        category = category,
        merchant = merchant,
        channel = channel,
        cardLastFour = cardLastFour,
        sourceApp = sourceApp,
        parseMethod = parseMethod,
        rawNotification = rawNotification,
        note = note,
        occurredAtMillis = occurredAtMillis,
        confirmed = confirmed,
        recordedBaseCurrencyCode = recordedBaseCurrencyCode,
        recordedRateToBase = recordedRateToBase,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )

    companion object {
        fun fromDomain(tx: LedgerTransaction): TransactionEntity = TransactionEntity(
            id = tx.id,
            amountCents = tx.amountCents,
            currencyCode = tx.currencyCode.uppercase().ifBlank { "CNY" },
            type = tx.type,
            category = tx.category,
            merchant = tx.merchant,
            channel = tx.channel,
            cardLastFour = tx.cardLastFour,
            sourceApp = tx.sourceApp,
            parseMethod = tx.parseMethod,
            rawNotification = tx.rawNotification,
            note = tx.note,
            occurredAtMillis = tx.occurredAtMillis,
            confirmed = tx.confirmed,
            recordedBaseCurrencyCode = tx.recordedBaseCurrencyCode.uppercase().ifBlank { "CNY" },
            recordedRateToBase = tx.recordedRateToBase,
            createdAtMillis = tx.createdAtMillis,
            updatedAtMillis = tx.updatedAtMillis
        )
    }
}

@Entity(tableName = "parsing_rules")
data class ParsingRuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val packageFilter: String,
    val keywordPattern: String,
    val channelName: String,
    val defaultCategory: String,
    val defaultType: String,
    val merchantOverride: String = "",
    val numberFormatPattern: String = "AUTO",
    val priority: Int = 10,
    val enabled: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): CustomParsingRule = CustomParsingRule(
        id = id,
        name = name,
        packageFilter = packageFilter,
        keywordPattern = keywordPattern,
        channelName = channelName,
        defaultCategory = defaultCategory,
        defaultType = defaultType,
        merchantOverride = merchantOverride,
        numberFormatPattern = numberFormatPattern,
        priority = priority,
        enabled = enabled,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )

    companion object {
        fun fromDomain(rule: CustomParsingRule): ParsingRuleEntity = ParsingRuleEntity(
            id = rule.id,
            name = rule.name,
            packageFilter = rule.packageFilter,
            keywordPattern = rule.keywordPattern,
            channelName = rule.channelName,
            defaultCategory = rule.defaultCategory,
            defaultType = rule.defaultType,
            merchantOverride = rule.merchantOverride,
            numberFormatPattern = rule.numberFormatPattern,
            priority = rule.priority,
            enabled = rule.enabled,
            createdAtMillis = rule.createdAtMillis,
            updatedAtMillis = rule.updatedAtMillis
        )
    }
}

@Entity(tableName = "notification_inquiries")
data class NotificationInquiryEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val content: String,
    val suggestedAmountCents: Long,
    val suggestedCurrencyCode: String,
    val suggestedType: String,
    val suggestedCategory: String,
    val suggestedMerchant: String,
    val suggestedChannel: String,
    val cardLastFour: String?,
    val reason: String,
    val occurredAtMillis: Long,
    val createdAtMillis: Long
) {
    fun toDomain(): com.example.model.NotificationInquiry = com.example.model.NotificationInquiry(
        id = id,
        packageName = packageName,
        appLabel = appLabel,
        title = title,
        content = content,
        suggestedAmountCents = suggestedAmountCents,
        suggestedCurrencyCode = suggestedCurrencyCode,
        suggestedType = suggestedType,
        suggestedCategory = suggestedCategory,
        suggestedMerchant = suggestedMerchant,
        suggestedChannel = suggestedChannel,
        cardLastFour = cardLastFour,
        reason = reason,
        occurredAtMillis = occurredAtMillis,
        createdAtMillis = createdAtMillis
    )

    companion object {
        fun fromDomain(inq: com.example.model.NotificationInquiry): NotificationInquiryEntity = NotificationInquiryEntity(
            id = inq.id,
            packageName = inq.packageName,
            appLabel = inq.appLabel,
            title = inq.title,
            content = inq.content,
            suggestedAmountCents = inq.suggestedAmountCents,
            suggestedCurrencyCode = inq.suggestedCurrencyCode,
            suggestedType = inq.suggestedType,
            suggestedCategory = inq.suggestedCategory,
            suggestedMerchant = inq.suggestedMerchant,
            suggestedChannel = inq.suggestedChannel,
            cardLastFour = inq.cardLastFour,
            reason = inq.reason,
            occurredAtMillis = inq.occurredAtMillis,
            createdAtMillis = inq.createdAtMillis
        )
    }
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM transactions ORDER BY occurredAtMillis DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY occurredAtMillis DESC")
    suspend fun getAllTransactionsOnce(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(entity: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(entities: List<TransactionEntity>)

    @Query("UPDATE transactions SET confirmed = 1, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun confirmTransaction(id: String, updatedAt: Long)

    @Query("UPDATE transactions SET confirmed = 1, updatedAtMillis = :updatedAt WHERE confirmed = 0")
    suspend fun confirmAllPendingTransactions(updatedAt: Long)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)

    @Query("SELECT * FROM parsing_rules ORDER BY priority DESC, createdAtMillis DESC")
    fun observeAllRules(): Flow<List<ParsingRuleEntity>>

    @Query("SELECT * FROM parsing_rules ORDER BY priority DESC, createdAtMillis DESC")
    suspend fun getAllRulesOnce(): List<ParsingRuleEntity>

    @Query("SELECT COUNT(*) FROM parsing_rules")
    suspend fun getRuleCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(entity: ParsingRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRules(entities: List<ParsingRuleEntity>)

    @Query("UPDATE parsing_rules SET enabled = :enabled, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun setRuleEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("DELETE FROM parsing_rules WHERE id = :id")
    suspend fun deleteRuleById(id: String)

    @Query("SELECT * FROM notification_inquiries ORDER BY occurredAtMillis DESC")
    fun observeInquiries(): Flow<List<NotificationInquiryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInquiry(entity: NotificationInquiryEntity)

    @Query("DELETE FROM notification_inquiries WHERE id = :id")
    suspend fun deleteInquiryById(id: String)
}

@Database(
    entities = [TransactionEntity::class, ParsingRuleEntity::class, NotificationInquiryEntity::class],
    version = 4,
    exportSchema = false
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile
        private var INSTANCE: LedgerDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'CNY'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE parsing_rules ADD COLUMN merchantOverride TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE parsing_rules ADD COLUMN priority INTEGER NOT NULL DEFAULT 10")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notification_inquiries` (
                        `id` TEXT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `appLabel` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `suggestedAmountCents` INTEGER NOT NULL,
                        `suggestedCurrencyCode` TEXT NOT NULL,
                        `suggestedType` TEXT NOT NULL,
                        `suggestedCategory` TEXT NOT NULL,
                        `suggestedMerchant` TEXT NOT NULL,
                        `suggestedChannel` TEXT NOT NULL,
                        `cardLastFour` TEXT,
                        `reason` TEXT NOT NULL,
                        `occurredAtMillis` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN recordedBaseCurrencyCode TEXT NOT NULL DEFAULT 'CNY'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN recordedRateToBase REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE parsing_rules ADD COLUMN numberFormatPattern TEXT NOT NULL DEFAULT 'AUTO'")
            }
        }

        fun getInstance(context: Context): LedgerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    "smart_ledger.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
