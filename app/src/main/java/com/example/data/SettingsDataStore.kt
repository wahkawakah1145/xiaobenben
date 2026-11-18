package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.BudgetSettings
import com.example.model.WebDavConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.smartLedgerDataStore by preferencesDataStore(name = "smart_ledger_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val MONTHLY_BUDGET_CENTS = longPreferencesKey("monthly_budget_cents")
        val AUTO_RECORD_ENABLED = booleanPreferencesKey("auto_record_enabled")
        val SMART_SEMANTIC_ENABLED = booleanPreferencesKey("smart_semantic_enabled")
        val NOTIFY_ON_RECORDED = booleanPreferencesKey("notify_on_recorded")

        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val WEBDAV_REMOTE_FOLDER = stringPreferencesKey("webdav_remote_folder")
        val WEBDAV_LAST_SYNC_MILLIS = longPreferencesKey("webdav_last_sync_millis")
        val WEBDAV_LAST_SYNC_STATUS = stringPreferencesKey("webdav_last_sync_status")
    }

    val budgetSettingsFlow: Flow<BudgetSettings> = context.smartLedgerDataStore.data.map { prefs ->
        BudgetSettings(
            monthlyBudgetCents = prefs[Keys.MONTHLY_BUDGET_CENTS] ?: 500_000L,
            autoRecordEnabled = prefs[Keys.AUTO_RECORD_ENABLED] ?: true,
            smartSemanticEnabled = prefs[Keys.SMART_SEMANTIC_ENABLED] ?: true,
            notifyOnRecorded = prefs[Keys.NOTIFY_ON_RECORDED] ?: true
        )
    }

    val webDavConfigFlow: Flow<WebDavConfig> = context.smartLedgerDataStore.data.map { prefs ->
        WebDavConfig(
            serverUrl = prefs[Keys.WEBDAV_URL] ?: "",
            username = prefs[Keys.WEBDAV_USERNAME] ?: "",
            password = prefs[Keys.WEBDAV_PASSWORD] ?: "",
            remoteFolder = prefs[Keys.WEBDAV_REMOTE_FOLDER] ?: "ZhiJiZhang",
            lastSyncMillis = prefs[Keys.WEBDAV_LAST_SYNC_MILLIS] ?: 0L,
            lastSyncStatus = prefs[Keys.WEBDAV_LAST_SYNC_STATUS] ?: ""
        )
    }

    suspend fun getBudgetSettingsOnce(): BudgetSettings = budgetSettingsFlow.first()

    suspend fun getWebDavConfigOnce(): WebDavConfig = webDavConfigFlow.first()

    suspend fun saveBudgetSettings(settings: BudgetSettings) {
        context.smartLedgerDataStore.edit { prefs ->
            prefs[Keys.MONTHLY_BUDGET_CENTS] = settings.monthlyBudgetCents.coerceIn(0L, 1_000_000_000L)
            prefs[Keys.AUTO_RECORD_ENABLED] = settings.autoRecordEnabled
            prefs[Keys.SMART_SEMANTIC_ENABLED] = settings.smartSemanticEnabled
            prefs[Keys.NOTIFY_ON_RECORDED] = settings.notifyOnRecorded
        }
    }

    suspend fun saveWebDavConfig(config: WebDavConfig) {
        context.smartLedgerDataStore.edit { prefs ->
            prefs[Keys.WEBDAV_URL] = config.serverUrl.trim()
            prefs[Keys.WEBDAV_USERNAME] = config.username.trim()
            prefs[Keys.WEBDAV_PASSWORD] = config.password
            prefs[Keys.WEBDAV_REMOTE_FOLDER] = config.remoteFolder.trim().ifBlank { "ZhiJiZhang" }
            prefs[Keys.WEBDAV_LAST_SYNC_MILLIS] = config.lastSyncMillis
            prefs[Keys.WEBDAV_LAST_SYNC_STATUS] = config.lastSyncStatus
        }
    }

    suspend fun updateWebDavSyncStatus(millis: Long, status: String) {
        context.smartLedgerDataStore.edit { prefs ->
            prefs[Keys.WEBDAV_LAST_SYNC_MILLIS] = millis
            prefs[Keys.WEBDAV_LAST_SYNC_STATUS] = status
        }
    }
}
