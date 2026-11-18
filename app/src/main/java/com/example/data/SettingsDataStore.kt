package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppearanceSettings
import com.example.model.BudgetSettings
import com.example.model.CardDensity
import com.example.model.CurrencyCatalog
import com.example.model.ExchangeRateState
import com.example.model.FabPlacement
import com.example.model.HomeHeaderOrder
import com.example.model.LatinNumberFont
import com.example.model.ThemeMode
import com.example.model.WebDavConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.smartLedgerDataStore by preferencesDataStore(name = "smart_ledger_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val MONTHLY_BUDGET_CENTS = longPreferencesKey("monthly_budget_cents")
        val AUTO_RECORD_ENABLED = booleanPreferencesKey("auto_record_enabled")
        val REQUIRE_MANUAL_CONFIRM = booleanPreferencesKey("require_manual_confirm")
        val SMART_SEMANTIC_ENABLED = booleanPreferencesKey("smart_semantic_enabled")
        val NOTIFY_ON_RECORDED = booleanPreferencesKey("notify_on_recorded")
        val BASE_CURRENCY_CODE = stringPreferencesKey("base_currency_code")

        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val WEBDAV_REMOTE_FOLDER = stringPreferencesKey("webdav_remote_folder")
        val WEBDAV_LAST_SYNC_MILLIS = longPreferencesKey("webdav_last_sync_millis")
        val WEBDAV_LAST_SYNC_STATUS = stringPreferencesKey("webdav_last_sync_status")

        val DYNAMIC_COLOR = booleanPreferencesKey("appearance_dynamic_color")
        val THEME_MODE = stringPreferencesKey("appearance_theme_mode")
        val LATIN_NUMBER_FONT = stringPreferencesKey("appearance_latin_number_font")
        val CARD_DENSITY = stringPreferencesKey("appearance_card_density")
        val HOME_HEADER_ORDER = stringPreferencesKey("appearance_home_header_order")
        val FAB_PLACEMENT = stringPreferencesKey("appearance_fab_placement")
        val SHOW_CHANNEL_BADGE = booleanPreferencesKey("appearance_show_channel_badge")
        val SHOW_PARSE_BADGE = booleanPreferencesKey("appearance_show_parse_badge")

        val EXCHANGE_RATES_JSON = stringPreferencesKey("exchange_rates_json")
        val EXCHANGE_RATES_UPDATED_AT = longPreferencesKey("exchange_rates_updated_at")
        val EXCHANGE_RATES_STATUS = stringPreferencesKey("exchange_rates_status")
    }

    val budgetSettingsFlow: Flow<BudgetSettings> = context.smartLedgerDataStore.data.map { prefs ->
        BudgetSettings(
            monthlyBudgetCents = prefs[Keys.MONTHLY_BUDGET_CENTS] ?: 500_000L,
            autoRecordEnabled = prefs[Keys.AUTO_RECORD_ENABLED] ?: true,
            requireManualConfirm = prefs[Keys.REQUIRE_MANUAL_CONFIRM] ?: false,
            smartSemanticEnabled = prefs[Keys.SMART_SEMANTIC_ENABLED] ?: true,
            notifyOnRecorded = prefs[Keys.NOTIFY_ON_RECORDED] ?: true,
            baseCurrencyCode = prefs[Keys.BASE_CURRENCY_CODE] ?: "CNY"
        )
    }

    val webDavConfigFlow: Flow<WebDavConfig> = context.smartLedgerDataStore.data.map { prefs ->
        WebDavConfig(
            serverUrl = prefs[Keys.WEBDAV_URL] ?: "",
            username = prefs[Keys.WEBDAV_USERNAME] ?: "",
            password = prefs[Keys.WEBDAV_PASSWORD] ?: "",
            remoteFolder = prefs[Keys.WEBDAV_REMOTE_FOLDER] ?: "XiaoBenBen",
            lastSyncMillis = prefs[Keys.WEBDAV_LAST_SYNC_MILLIS] ?: 0L,
            lastSyncStatus = prefs[Keys.WEBDAV_LAST_SYNC_STATUS] ?: ""
        )
    }

    val appearanceSettingsFlow: Flow<AppearanceSettings> = context.smartLedgerDataStore.data.map { prefs ->
        AppearanceSettings(
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            themeMode = ThemeMode.fromRaw(prefs[Keys.THEME_MODE]),
            latinNumberFont = LatinNumberFont.fromRaw(prefs[Keys.LATIN_NUMBER_FONT]),
            cardDensity = CardDensity.fromRaw(prefs[Keys.CARD_DENSITY]),
            homeHeaderOrder = HomeHeaderOrder.fromRaw(prefs[Keys.HOME_HEADER_ORDER]),
            fabPlacement = FabPlacement.fromRaw(prefs[Keys.FAB_PLACEMENT]),
            showChannelBadge = prefs[Keys.SHOW_CHANNEL_BADGE] ?: true,
            showParseBadge = prefs[Keys.SHOW_PARSE_BADGE] ?: true
        )
    }

    val exchangeRateStateFlow: Flow<ExchangeRateState> = context.smartLedgerDataStore.data.map { prefs ->
        val parsed = decodeRatesJson(prefs[Keys.EXCHANGE_RATES_JSON])
        ExchangeRateState(
            ratesToCny = parsed,
            updatedAtMillis = prefs[Keys.EXCHANGE_RATES_UPDATED_AT] ?: 0L,
            lastStatus = prefs[Keys.EXCHANGE_RATES_STATUS] ?: ""
        )
    }

    suspend fun getBudgetSettingsOnce(): BudgetSettings = budgetSettingsFlow.first()

    suspend fun getWebDavConfigOnce(): WebDavConfig = webDavConfigFlow.first()

    suspend fun getExchangeRateStateOnce(): ExchangeRateState = exchangeRateStateFlow.first()

    suspend fun saveBudgetSettings(settings: BudgetSettings) {
        context.smartLedgerDataStore.edit { prefs ->
            prefs[Keys.MONTHLY_BUDGET_CENTS] = settings.monthlyBudgetCents.coerceIn(0L, 1_000_000_000L)
            prefs[Keys.AUTO_RECORD_ENABLED] = settings.autoRecordEnabled
            prefs[Keys.REQUIRE_MANUAL_CONFIRM] = settings.requireManualConfirm
            prefs[Keys.SMART_SEMANTIC_ENABLED] = settings.smartSemanticEnabled
            prefs[Keys.NOTIFY_ON_RECORDED] = settings.notifyOnRecorded
            prefs[Keys.BASE_CURRENCY_CODE] = CurrencyCatalog.resolve(settings.baseCurrencyCode).code
        }
    }

    suspend fun saveWebDavConfig(config: WebDavConfig) {
        context.smartLedgerDataStore.edit { prefs ->
            prefs[Keys.WEBDAV_URL] = config.serverUrl.trim()
            prefs[Keys.WEBDAV_USERNAME] = config.username.trim()
            prefs[Keys.WEBDAV_PASSWORD] = config.password
            prefs[Keys.WEBDAV_REMOTE_FOLDER] = config.remoteFolder.trim().ifBlank { "XiaoBenBen" }
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

    suspend fun saveAppearanceSettings(settings: AppearanceSettings) {
        context.smartLedgerDataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_COLOR] = settings.dynamicColor
            prefs[Keys.THEME_MODE] = settings.themeMode.name
            prefs[Keys.LATIN_NUMBER_FONT] = settings.latinNumberFont.name
            prefs[Keys.CARD_DENSITY] = settings.cardDensity.name
            prefs[Keys.HOME_HEADER_ORDER] = settings.homeHeaderOrder.name
            prefs[Keys.FAB_PLACEMENT] = settings.fabPlacement.name
            prefs[Keys.SHOW_CHANNEL_BADGE] = settings.showChannelBadge
            prefs[Keys.SHOW_PARSE_BADGE] = settings.showParseBadge
        }
    }

    suspend fun saveExchangeRates(ratesToCny: Map<String, Double>, updatedAtMillis: Long, status: String) {
        val json = JSONObject().apply {
            ratesToCny.forEach { (code, value) ->
                if (value > 0.0) put(code, value)
            }
        }.toString()
        context.smartLedgerDataStore.edit { prefs ->
            prefs[Keys.EXCHANGE_RATES_JSON] = json
            prefs[Keys.EXCHANGE_RATES_UPDATED_AT] = updatedAtMillis
            prefs[Keys.EXCHANGE_RATES_STATUS] = status
        }
    }

    private fun decodeRatesJson(raw: String?): Map<String, Double> {
        val merged = CurrencyCatalog.defaultRatesToCny.toMutableMap()
        if (raw.isNullOrBlank()) return merged
        runCatching {
            val obj = JSONObject(raw)
            obj.keys().forEach { key ->
                val value = obj.optDouble(key, 0.0)
                if (value > 0.0) merged[key.uppercase()] = value
            }
        }
        merged["CNY"] = 1.0
        return merged
    }
}
