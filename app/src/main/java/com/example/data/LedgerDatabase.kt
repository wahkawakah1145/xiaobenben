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
            enabled = rule.enabled,
            createdAtMillis = rule.createdAtMillis,
            updatedAtMillis = rule.updatedAtMillis
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

    @Query("SELECT * FROM parsing_rules ORDER BY createdAtMillis DESC")
    fun observeAllRules(): Flow<List<ParsingRuleEntity>>

    @Query("SELECT * FROM parsing_rules ORDER BY createdAtMillis DESC")
    suspend fun getAllRulesOnce(): List<ParsingRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(entity: ParsingRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRules(entities: List<ParsingRuleEntity>)

    @Query("UPDATE parsing_rules SET enabled = :enabled, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun setRuleEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("DELETE FROM parsing_rules WHERE id = :id")
    suspend fun deleteRuleById(id: String)
}

@Database(
    entities = [TransactionEntity::class, ParsingRuleEntity::class],
    version = 2,
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

        fun getInstance(context: Context): LedgerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    "smart_ledger.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
