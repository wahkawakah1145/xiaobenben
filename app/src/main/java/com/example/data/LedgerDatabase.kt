package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.CapturedNotificationLog
import com.example.model.CustomParsingRule
import com.example.model.LedgerBook
import com.example.model.LedgerTransaction
import com.example.model.TransactionBookLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
    val tradeType: String? = null,
    val productName: String? = null,
    val tradeStatus: String? = null,
    val transactionNo: String? = null,
    val merchantOrderNo: String? = null,
    val balanceAfterCents: Long? = null,
    val customFieldsJson: String? = null,
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
        tradeType = tradeType,
        productName = productName,
        tradeStatus = tradeStatus,
        transactionNo = transactionNo,
        merchantOrderNo = merchantOrderNo,
        balanceAfterCents = balanceAfterCents,
        customFields = decodeCustomFieldsJson(customFieldsJson),
        occurredAtMillis = occurredAtMillis,
        confirmed = confirmed,
        recordedBaseCurrencyCode = recordedBaseCurrencyCode,
        recordedRateToBase = recordedRateToBase,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )

    companion object {
        fun encodeCustomFieldsJson(fields: Map<String, String>): String? {
            if (fields.isEmpty()) return null
            val obj = JSONObject()
            var count = 0
            fields.forEach { (k, v) ->
                val ck = k.trim().take(48)
                val cv = v.trim().take(160)
                if (ck.isNotEmpty() && cv.isNotEmpty()) {
                    obj.put(ck, cv)
                    count++
                }
            }
            return if (count == 0) null else obj.toString()
        }

        fun decodeCustomFieldsJson(raw: String?): Map<String, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            return runCatching {
                val obj = JSONObject(raw)
                val map = LinkedHashMap<String, String>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next().trim().take(48)
                    val v = obj.optString(k).trim().take(160)
                    if (k.isNotEmpty() && v.isNotEmpty()) {
                        map[k] = v
                    }
                }
                map
            }.getOrDefault(emptyMap())
        }

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
            tradeType = tx.tradeType,
            productName = tx.productName,
            tradeStatus = tx.tradeStatus,
            transactionNo = tx.transactionNo,
            merchantOrderNo = tx.merchantOrderNo,
            balanceAfterCents = tx.balanceAfterCents,
            customFieldsJson = encodeCustomFieldsJson(tx.customFields),
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
    val templateSignature: String = "",
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
        templateSignature = templateSignature,
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
            templateSignature = rule.templateSignature,
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


@Entity(tableName = "ledger_books")
data class LedgerBookEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val colorArgb: Long = 0xFF0F6B54L,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): LedgerBook = LedgerBook(
        id = id,
        name = name,
        description = description,
        colorArgb = colorArgb,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )

    companion object {
        fun fromDomain(book: LedgerBook): LedgerBookEntity = LedgerBookEntity(
            id = book.id,
            name = book.name,
            description = book.description,
            colorArgb = book.colorArgb,
            createdAtMillis = book.createdAtMillis,
            updatedAtMillis = book.updatedAtMillis
        )
    }
}

@Entity(
    tableName = "transaction_book_cross_ref",
    primaryKeys = ["transactionId", "bookId"],
    indices = [Index(value = ["bookId"]), Index(value = ["transactionId"])],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LedgerBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TransactionBookCrossRefEntity(
    val transactionId: String,
    val bookId: String,
    val addedAtMillis: Long = System.currentTimeMillis()
) {
    fun toDomain(): TransactionBookLink = TransactionBookLink(
        transactionId = transactionId,
        bookId = bookId,
        addedAtMillis = addedAtMillis
    )

    companion object {
        fun fromDomain(link: TransactionBookLink): TransactionBookCrossRefEntity =
            TransactionBookCrossRefEntity(
                transactionId = link.transactionId,
                bookId = link.bookId,
                addedAtMillis = link.addedAtMillis
            )
    }
}

@Entity(
    tableName = "notification_logs",
    indices = [Index(value = ["capturedAtMillis"])]
)
data class NotificationLogEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val content: String,
    val capturedAtMillis: Long,
    val status: String,
    val summary: String
) {
    fun toDomain(): CapturedNotificationLog = CapturedNotificationLog(
        id = id,
        packageName = packageName,
        appLabel = appLabel,
        title = title,
        content = content,
        capturedAtMillis = capturedAtMillis,
        status = status,
        summary = summary
    )

    companion object {
        fun fromDomain(log: CapturedNotificationLog): NotificationLogEntity = NotificationLogEntity(
            id = log.id,
            packageName = log.packageName.take(128),
            appLabel = log.appLabel.take(64),
            title = log.title.take(120),
            content = log.content.take(360),
            capturedAtMillis = log.capturedAtMillis,
            status = log.status.take(48),
            summary = log.summary.take(180)
        )
    }
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM transactions ORDER BY occurredAtMillis DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) || ':' || COALESCE(MAX(updatedAtMillis), 0) || ':' || COALESCE(SUM(amountCents), 0) FROM transactions")
    fun observeTransactionsSignature(): Flow<String>

    @Query("SELECT * FROM transactions ORDER BY occurredAtMillis DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun getTransactionsPage(limit: Int, offset: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun getTransactionsByIds(ids: List<String>): List<TransactionEntity>

    @Query("SELECT id FROM transactions")
    suspend fun getAllTransactionIdsOnce(): List<String>

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

    @Query("UPDATE transactions SET category = :newCategory, updatedAtMillis = :updatedAt WHERE category = :oldCategory")
    suspend fun renameTransactionCategory(oldCategory: String, newCategory: String, updatedAt: Long): Int

    @Query("UPDATE parsing_rules SET channelName = :newChannel, updatedAtMillis = :updatedAt WHERE channelName = :oldChannel")
    suspend fun renameRuleChannel(oldChannel: String, newChannel: String, updatedAt: Long): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteTransactionsByIds(ids: List<String>)

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

    @Query("UPDATE parsing_rules SET defaultCategory = :newCategory, updatedAtMillis = :updatedAt WHERE defaultCategory = :oldCategory")
    suspend fun renameRuleCategory(oldCategory: String, newCategory: String, updatedAt: Long): Int

    @Query("DELETE FROM parsing_rules WHERE id = :id")
    suspend fun deleteRuleById(id: String)

    @Query("SELECT * FROM notification_inquiries ORDER BY occurredAtMillis DESC")
    fun observeInquiries(): Flow<List<NotificationInquiryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInquiry(entity: NotificationInquiryEntity)

    @Query("DELETE FROM notification_inquiries WHERE id = :id")
    suspend fun deleteInquiryById(id: String)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun countTransactions(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE rawNotification IS NOT NULL AND rawNotification != ''")
    suspend fun countRawNotifications(): Int

    @Query("UPDATE transactions SET rawNotification = NULL WHERE confirmed = 1 AND rawNotification IS NOT NULL AND occurredAtMillis < :cutoffMillis")
    suspend fun pruneOldRawNotifications(cutoffMillis: Long): Int

    @Query("SELECT COUNT(*) FROM notification_inquiries")
    suspend fun countInquiries(): Int

    @Query("DELETE FROM notification_inquiries WHERE createdAtMillis < :cutoffMillis")
    suspend fun pruneStaleInquiries(cutoffMillis: Long): Int

    @Query("SELECT * FROM ledger_books ORDER BY createdAtMillis ASC")
    fun observeAllBooks(): Flow<List<LedgerBookEntity>>

    @Query("SELECT * FROM ledger_books ORDER BY createdAtMillis ASC")
    suspend fun getAllBooksOnce(): List<LedgerBookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBook(entity: LedgerBookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBooks(entities: List<LedgerBookEntity>)

    @Query("DELETE FROM ledger_books WHERE id = :id")
    suspend fun deleteBookById(id: String)

    @Query("SELECT * FROM transaction_book_cross_ref")
    fun observeAllBookRefs(): Flow<List<TransactionBookCrossRefEntity>>

    @Query("SELECT COUNT(*) || ':' || COALESCE(MAX(addedAtMillis), 0) FROM transaction_book_cross_ref")
    fun observeBookRefsSignature(): Flow<String>

    @Query("SELECT * FROM transaction_book_cross_ref ORDER BY addedAtMillis DESC, transactionId ASC, bookId ASC LIMIT :limit OFFSET :offset")
    suspend fun getBookRefsPage(limit: Int, offset: Int): List<TransactionBookCrossRefEntity>

    @Query("SELECT * FROM transaction_book_cross_ref")
    suspend fun getAllBookRefsOnce(): List<TransactionBookCrossRefEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookRefs(refs: List<TransactionBookCrossRefEntity>)

    @Query("DELETE FROM transaction_book_cross_ref WHERE bookId = :bookId")
    suspend fun deleteRefsByBookId(bookId: String)

    @Query("DELETE FROM transaction_book_cross_ref WHERE transactionId = :txId")
    suspend fun deleteRefsByTransactionId(txId: String)

    @Query("DELETE FROM transaction_book_cross_ref WHERE transactionId IN (:txIds)")
    suspend fun deleteRefsByTransactionIds(txIds: List<String>)

    @Query("SELECT * FROM transaction_book_cross_ref WHERE transactionId IN (:txIds)")
    suspend fun getBookRefsByTransactionIds(txIds: List<String>): List<TransactionBookCrossRefEntity>

    @Query("DELETE FROM transaction_book_cross_ref WHERE bookId = :bookId AND transactionId IN (:txIds)")
    suspend fun deleteRefsByBookAndTransactionIds(bookId: String, txIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationLog(entity: NotificationLogEntity)

    @Query("SELECT * FROM notification_logs ORDER BY capturedAtMillis DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun getNotificationLogsPage(limit: Int, offset: Int): List<NotificationLogEntity>

    @Query(
        """
        SELECT * FROM notification_logs
        WHERE appLabel LIKE :pattern ESCAPE '\'
           OR packageName LIKE :pattern ESCAPE '\'
           OR title LIKE :pattern ESCAPE '\'
           OR content LIKE :pattern ESCAPE '\'
           OR status LIKE :pattern ESCAPE '\'
           OR summary LIKE :pattern ESCAPE '\'
        ORDER BY capturedAtMillis DESC, id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun searchNotificationLogsPage(pattern: String, limit: Int, offset: Int): List<NotificationLogEntity>

    @Query("SELECT COUNT(*) FROM notification_logs")
    suspend fun countNotificationLogs(): Int

    @Query("SELECT COUNT(*) FROM notification_logs")
    fun observeNotificationLogsCount(): Flow<Int>

    @Query("DELETE FROM notification_logs WHERE capturedAtMillis < :cutoffMillis")
    suspend fun deleteNotificationLogsOlderThan(cutoffMillis: Long): Int

    @Query(
        """
        DELETE FROM notification_logs
        WHERE id NOT IN (
            SELECT id FROM notification_logs
            ORDER BY capturedAtMillis DESC, id DESC
            LIMIT :keepCount
        )
        """
    )
    suspend fun deleteNotificationLogsExcess(keepCount: Int): Int

    @Query("UPDATE notification_logs SET status = :status, summary = :summary WHERE id = :id")
    suspend fun updateNotificationLogStatus(id: String, status: String, summary: String)

    @Query("DELETE FROM notification_logs")
    suspend fun clearAllNotificationLogs()
}

@Database(
    entities = [
        TransactionEntity::class,
        ParsingRuleEntity::class,
        NotificationInquiryEntity::class,
        LedgerBookEntity::class,
        TransactionBookCrossRefEntity::class,
        NotificationLogEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    suspend fun vacuumAndCheckpoint() = withContext(Dispatchers.IO) {
        val db = openHelper.writableDatabase
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        db.execSQL("VACUUM")
    }

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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE parsing_rules ADD COLUMN templateSignature TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ledger_books` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `colorArgb` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transaction_book_cross_ref` (
                        `transactionId` TEXT NOT NULL,
                        `bookId` TEXT NOT NULL,
                        `addedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`transactionId`, `bookId`),
                        FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`bookId`) REFERENCES `ledger_books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_book_cross_ref_bookId` ON `transaction_book_cross_ref` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_book_cross_ref_transactionId` ON `transaction_book_cross_ref` (`transactionId`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN tradeType TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN productName TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN tradeStatus TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN transactionNo TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN merchantOrderNo TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN balanceAfterCents INTEGER")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN customFieldsJson TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notification_logs` (
                        `id` TEXT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `appLabel` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `capturedAtMillis` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_logs_capturedAtMillis` ON `notification_logs` (`capturedAtMillis`)")
            }
        }

        /**
         * Safely releases idle SQLite page memory in background without closing DB
         * or interrupting active NotificationListener / Accessibility background writes.
         */
        fun trimMemoryIfOpen() {
            val dbInstance = INSTANCE ?: return
            if (!dbInstance.isOpen) return
            runCatching {
                val sqlite = dbInstance.openHelper.writableDatabase
                if (sqlite.isOpen) {
                    sqlite.execSQL("PRAGMA shrink_memory")
                }
            }
        }

        fun getInstance(context: Context): LedgerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    "smart_ledger.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9
                    )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            runCatching { db.execSQL("PRAGMA auto_vacuum = INCREMENTAL") }
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            runCatching { db.execSQL("PRAGMA cache_size = -512") }
                            runCatching { db.execSQL("PRAGMA wal_autocheckpoint = 200") }
                            runCatching { db.query("PRAGMA incremental_vacuum").close() }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
