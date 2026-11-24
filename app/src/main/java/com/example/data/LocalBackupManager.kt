package com.example.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalBackupManager {

    fun buildDefaultBackupFileName(nowMillis: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(Date(nowMillis))
        return "xiaobenben_backup_$stamp.zip"
    }

    suspend fun inspectBackupRequiresPassword(
        context: Context,
        uri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@runCatching false
            BackupCryptoCodec.requiresPassword(bytes)
        }.getOrDefault(false)
    }

    suspend fun exportBackupToUri(
        context: Context,
        uri: Uri,
        repository: LedgerRepository,
        password: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val transactions = repository.getAllTransactionsOnce()
            val rules = repository.getRulesOnce()
            val settings = repository.getSettingsOnce()
            val books = repository.getAllBooksOnce()
            val bookLinks = repository.getAllBookLinksOnce()
            val json = LedgerBackupCodec.encodeBackupJson(transactions, rules, settings, books, bookLinks)
            val encryptedZip = BackupCryptoCodec.encodeEncryptedZip(json, password)

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(encryptedZip)
                out.flush()
            } ?: return@withContext Result.failure(IllegalStateException("无法写入所选本地路径"))

            repository.markLocalBackupCompleted(System.currentTimeMillis())
            val encNote = if (!password.isNullOrBlank()) "（已使用自定义密码 AES-256-GCM 加密打包）" else "（已使用 AES-256-GCM 加密打包）"
            Result.success("本地加密导出成功：已保存 ${transactions.size} 笔账单、${rules.size} 条规则$encNote")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException("导出失败：${e.localizedMessage ?: "文件写入异常"}", e))
        }
    }

    suspend fun restoreBackupFromUri(
        context: Context,
        uri: Uri,
        repository: LedgerRepository,
        password: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val rawBytes = context.contentResolver.openInputStream(uri)
                ?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("无法读取所选备份文件"))

            if (rawBytes.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("所选备份文件内容为空"))
            }

            val decoded = BackupCryptoCodec.decodeBackupBytes(rawBytes, password)
            val rawBody = decoded.jsonText.trim()
            if (rawBody.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("所选备份文件内容为空"))
            }

            val currentSettings = repository.getSettingsOnce()
            val payload = LedgerBackupCodec.decodeBackupJson(rawBody, currentSettings)
            if (payload.transactions.isEmpty() && payload.parsingRules.isEmpty() && payload.settings == null) {
                return@withContext Result.failure(IllegalArgumentException("未在文件中识别到有效的账单或规则备份"))
            }

            repository.mergeTransactionsFromBackup(payload.transactions)
            repository.mergeRulesFromBackup(payload.parsingRules)
            repository.mergeBooksFromBackup(payload.books, payload.bookLinks)
            payload.settings?.let { restored ->
                repository.saveSettings(
                    restored.copy(
                        lastLocalBackupMillis = currentSettings.lastLocalBackupMillis,
                        lastBackupReminderMillis = currentSettings.lastBackupReminderMillis
                    )
                )
            }

            val formatTag = if (decoded.wasEncryptedZip) "加密压缩包" else "旧版明文 JSON"
            Result.success("本地恢复成功（$formatTag）：合并 ${payload.transactions.size} 笔账单、${payload.parsingRules.size} 条规则")
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupPasswordRequiredException) {
            Result.failure(e)
        } catch (e: BackupWrongPasswordException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("恢复失败：${e.localizedMessage ?: "备份文件格式无效"}", e))
        }
    }
}
