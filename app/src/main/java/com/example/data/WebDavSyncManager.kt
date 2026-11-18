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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class WebDavSyncManager {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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

            val rootJson = JSONObject().apply {
                put("version", 2)
                put("exportedAtMillis", System.currentTimeMillis())
                put("settings", JSONObject().apply {
                    put("monthlyBudgetCents", settings.monthlyBudgetCents)
                    put("autoRecordEnabled", settings.autoRecordEnabled)
                    put("requireManualConfirm", settings.requireManualConfirm)
                    put("smartSemanticEnabled", settings.smartSemanticEnabled)
                    put("notifyOnRecorded", settings.notifyOnRecorded)
                    put("baseCurrencyCode", settings.baseCurrencyCode)
                })
                put("transactions", JSONArray().apply {
                    transactions.forEach { tx ->
                        put(JSONObject().apply {
                            put("id", tx.id)
                            put("amountCents", tx.amountCents)
                            put("currencyCode", tx.currencyCode)
                            put("type", tx.type)
                            put("category", tx.category)
                            put("merchant", tx.merchant)
                            put("channel", tx.channel)
                            put("cardLastFour", tx.cardLastFour ?: JSONObject.NULL)
                            put("sourceApp", tx.sourceApp)
                            put("parseMethod", tx.parseMethod)
                            put("rawNotification", tx.rawNotification ?: JSONObject.NULL)
                            put("note", tx.note ?: JSONObject.NULL)
                            put("occurredAtMillis", tx.occurredAtMillis)
                            put("confirmed", tx.confirmed)
                            put("createdAtMillis", tx.createdAtMillis)
                            put("updatedAtMillis", tx.updatedAtMillis)
                        })
                    }
                })
                put("parsingRules", JSONArray().apply {
                    rules.forEach { rule ->
                        put(JSONObject().apply {
                            put("id", rule.id)
                            put("name", rule.name)
                            put("packageFilter", rule.packageFilter)
                            put("keywordPattern", rule.keywordPattern)
                            put("channelName", rule.channelName)
                            put("defaultCategory", rule.defaultCategory)
                            put("defaultType", rule.defaultType)
                            put("enabled", rule.enabled)
                            put("createdAtMillis", rule.createdAtMillis)
                            put("updatedAtMillis", rule.updatedAtMillis)
                        })
                    }
                })
            }

            val putBuilder = Request.Builder()
                .url(fileUrl)
                .put(rootJson.toString(2).toRequestBody("application/json; charset=utf-8".toMediaType()))
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

            val root = JSONObject(body)
            val txArray = root.optJSONArray("transactions") ?: JSONArray()
            val txList = buildList {
                for (i in 0 until txArray.length()) {
                    val o = txArray.getJSONObject(i)
                    val amount = o.optLong("amountCents", 0L)
                    if (amount <= 0L) continue
                    add(
                        LedgerTransaction(
                            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                            amountCents = amount,
                            currencyCode = o.optString("currencyCode", "CNY").ifBlank { "CNY" },
                            type = o.optString("type", "EXPENSE"),
                            category = o.optString("category", "其他"),
                            merchant = o.optString("merchant", "未知商户"),
                            channel = o.optString("channel", "未知渠道"),
                            cardLastFour = o.optString("cardLastFour").takeIf { it.isNotBlank() && it != "null" },
                            sourceApp = o.optString("sourceApp", "MANUAL"),
                            parseMethod = o.optString("parseMethod", "MANUAL"),
                            rawNotification = o.optString("rawNotification").takeIf { it.isNotBlank() && it != "null" },
                            note = o.optString("note").takeIf { it.isNotBlank() && it != "null" },
                            occurredAtMillis = o.optLong("occurredAtMillis", System.currentTimeMillis()),
                            confirmed = o.optBoolean("confirmed", true),
                            createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                            updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis())
                        )
                    )
                }
            }

            val ruleArray = root.optJSONArray("parsingRules") ?: JSONArray()
            val ruleList = buildList {
                for (i in 0 until ruleArray.length()) {
                    val o = ruleArray.getJSONObject(i)
                    add(
                        CustomParsingRule(
                            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = o.optString("name", "导入规则"),
                            packageFilter = o.optString("packageFilter", "*"),
                            keywordPattern = o.optString("keywordPattern", ""),
                            channelName = o.optString("channelName", "其他"),
                            defaultCategory = o.optString("defaultCategory", "其他"),
                            defaultType = o.optString("defaultType", "EXPENSE"),
                            enabled = o.optBoolean("enabled", true),
                            createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                            updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis())
                        )
                    )
                }
            }

            repository.mergeTransactionsFromBackup(txList)
            repository.mergeRulesFromBackup(ruleList)

            root.optJSONObject("settings")?.let { s ->
                repository.saveSettings(
                    BudgetSettings(
                        monthlyBudgetCents = s.optLong("monthlyBudgetCents", 500_000L),
                        autoRecordEnabled = s.optBoolean("autoRecordEnabled", true),
                        requireManualConfirm = s.optBoolean("requireManualConfirm", false),
                        smartSemanticEnabled = s.optBoolean("smartSemanticEnabled", true),
                        notifyOnRecorded = s.optBoolean("notifyOnRecorded", true),
                        baseCurrencyCode = s.optString("baseCurrencyCode", "CNY").ifBlank { "CNY" }
                    )
                )
            }

            val msg = "同步恢复成功：合并 ${txList.size} 笔账单，${ruleList.size} 条规则"
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
