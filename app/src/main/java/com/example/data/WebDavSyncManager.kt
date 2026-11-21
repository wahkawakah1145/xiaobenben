package com.example.data

import com.example.model.BudgetSettings
import com.example.model.CustomParsingRule
import com.example.model.LedgerTransaction
import com.example.model.WebDavConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class WebDavSyncManager {

    private val client: OkHttpClient
        get() = AppHttpClient.derived(connectTimeoutSec = 15, readTimeoutSec = 30, writeTimeoutSec = 30)

    suspend fun testConnection(config: WebDavConfig): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = normalizeBaseUrl(config.serverUrl)
                ?: return@withContext Result.failure(IllegalArgumentException("请输入有效的 WebDAV 服务器地址（如 https://dav.jianguoyun.com/dav/）"))

            val authHeader = buildAuthHeader(config)
            val propfindBody = """<?xml version="1.0" encoding="utf-8" ?><D:propfind xmlns:D="DAV:"><D:prop><D:resourcetype/></D:prop></D:propfind>"""
                .toRequestBody("application/xml; charset=utf-8".toMediaType())

            val reqBuilder = Request.Builder()
                .url(baseUrl)
                .method("PROPFIND", propfindBody)
                .header("Depth", "0")
            if (authHeader != null) reqBuilder.header("Authorization", authHeader)

            client.newCall(reqBuilder.build()).execute().use { resp ->
                when {
                    resp.code in 200..299 || resp.code == 207 ->
                        Result.success("连接成功 (HTTP ${resp.code})")
                    resp.code == 401 || resp.code == 403 ->
                        Result.failure(IllegalStateException("认证失败 (HTTP ${resp.code})，请检查账号与应用密码"))
                    else ->
                        Result.failure(IllegalStateException("服务器响应异常：HTTP ${resp.code} ${resp.message}"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException("无法连接 WebDAV 服务器：${e.localizedMessage ?: "网络异常"}", e))
        }
    }

    suspend fun backupToWebDav(
        config: WebDavConfig,
        repository: LedgerRepository
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = normalizeBaseUrl(config.serverUrl)
                ?: return@withContext Result.failure(IllegalArgumentException("请先配置有效的 WebDAV 服务器地址"))

            val folder = config.remoteFolder.trim().trim('/').ifBlank { "XiaoBenBen" }
            val folderUrl = "$baseUrl$folder/"
            val fileUrl = "$folderUrl$BACKUP_FILENAME"
            val authHeader = buildAuthHeader(config)

            val mkcolBuilder = Request.Builder()
                .url(folderUrl)
                .method("MKCOL", ByteArray(0).toRequestBody(null))
            if (authHeader != null) mkcolBuilder.header("Authorization", authHeader)
            client.newCall(mkcolBuilder.build()).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) {
                    return@withContext Result.failure(IllegalStateException("WebDAV 认证失败 (HTTP ${resp.code})"))
                }
            }

            val transactions = repository.getAllTransactionsOnce()
            val rules = repository.getRulesOnce()
            val settings = repository.getSettingsOnce()
            val books = repository.getAllBooksOnce()
            val bookLinks = repository.getAllBookLinksOnce()
            val backupJson = LedgerBackupCodec.encodeBackupJson(transactions, rules, settings, books, bookLinks)

            val putBuilder = Request.Builder()
                .url(fileUrl)
                .put(backupJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            if (authHeader != null) putBuilder.header("Authorization", authHeader)

            client.newCall(putBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 201 || resp.code == 204) {
                    val msg = "备份成功：${transactions.size} 笔账单，${rules.size} 条规则"
                    repository.updateWebDavSyncStatus(System.currentTimeMillis(), msg)
                    Result.success(msg)
                } else {
                    val err = "上传备份失败：HTTP ${resp.code} ${resp.message}"
                    repository.updateWebDavSyncStatus(System.currentTimeMillis(), err)
                    Result.failure(IllegalStateException(err))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val err = "WebDAV 备份失败：${e.localizedMessage ?: "网络异常"}"
            repository.updateWebDavSyncStatus(System.currentTimeMillis(), err)
            Result.failure(IllegalStateException(err, e))
        }
    }

    suspend fun restoreFromWebDav(
        config: WebDavConfig,
        repository: LedgerRepository
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = normalizeBaseUrl(config.serverUrl)
                ?: return@withContext Result.failure(IllegalArgumentException("请先配置有效的 WebDAV 服务器地址"))

            val folder = config.remoteFolder.trim().trim('/').ifBlank { "XiaoBenBen" }
            val authHeader = buildAuthHeader(config)

            val candidates = listOf(
                "$baseUrl$folder/$BACKUP_FILENAME",
                "$baseUrl$folder/$LEGACY_BACKUP_FILENAME"
            )
            var jsonBody: String? = null
            for (fileUrl in candidates) {
                val getBuilder = Request.Builder().url(fileUrl).get()
                if (authHeader != null) getBuilder.header("Authorization", authHeader)
                client.newCall(getBuilder.build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        jsonBody = resp.body?.string()
                    } else if (resp.code != 404) {
                        return@withContext Result.failure(
                            IllegalStateException("下载备份失败：HTTP ${resp.code} ${resp.message}")
                        )
                    }
                }
                if (jsonBody != null) break
            }

            val body = jsonBody
                ?: return@withContext Result.failure(IllegalStateException("远程目录 $folder 下尚未找到备份文件"))

            val currentSettings = repository.getSettingsOnce()
            val payload = LedgerBackupCodec.decodeBackupJson(body, currentSettings)

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

            val msg = "同步恢复成功：合并 ${payload.transactions.size} 笔账单，${payload.parsingRules.size} 条规则"
            repository.updateWebDavSyncStatus(System.currentTimeMillis(), msg)
            Result.success(msg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val err = "WebDAV 恢复失败：${e.localizedMessage ?: "数据格式或网络异常"}"
            repository.updateWebDavSyncStatus(System.currentTimeMillis(), err)
            Result.failure(IllegalStateException(err, e))
        }
    }

    private fun normalizeBaseUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun buildAuthHeader(config: WebDavConfig): String? {
        if (config.username.isBlank() && config.password.isBlank()) return null
        return Credentials.basic(config.username.trim(), config.password, Charsets.UTF_8)
    }

    companion object {
        const val BACKUP_FILENAME = "xiaobenben_backup.json"
        const val LEGACY_BACKUP_FILENAME = "zhijizhang_backup.json"
    }
}
