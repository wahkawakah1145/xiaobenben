package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppIconStyle
import com.example.model.AppearanceSettings
import com.example.model.BudgetSettings
import com.example.model.CardDensity
import com.example.model.CurrencyCatalog
import com.example.model.CustomPaletteStyle
import com.example.model.ExchangeRateState
import com.example.model.FabPlacement
import com.example.model.HomeHeaderOrder
import com.example.model.IconVisualStyle
import com.example.model.LatinNumberFont
import com.example.model.PredictiveBackStyle
import com.example.model.TabClickAnimationStyle
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
        val ROOT_KEEP_ALIVE_ENABLED = booleanPreferencesKey("root_keep_alive_enabled")
        val BACKUP_REMINDER_ENABLED = booleanPreferencesKey("backup_reminder_enabled")
        val BACKUP_REMINDER_INTERVAL_HOURS = intPreferencesKey("backup_reminder_interval_hours")
        val LAST_LOCAL_BACKUP_MILLIS = longPreferencesKey("last_local_backup_millis")
        val LAST_BACKUP_REMINDER_MILLIS = longPreferencesKey("last_backup_reminder_millis")
        val RULES_SEEDED = booleanPreferencesKey("default_rules_seeded_v1")

        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val WEBDAV_REMOTE_FOLDER = stringPreferencesKey("webdav_remote_folder")
        val WEBDAV_LAST_SYNC_MILLIS = longPreferencesKey("webdav_last_sync_millis")
        val WEBDAV_LAST_SYNC_STATUS = stringPreferencesKey("webdav_last_sync_status")

        val DYNAMIC_COLOR = booleanPreferencesKey("appearance_dynamic_color")
        val CUSTOM_PRIMARY_COLOR_ARGB = longPreferencesKey("appearance_custom_primary_color_argb")
        val CUSTOM_PALETTE_STYLE = stringPreferencesKey("appearance_custom_palette_style")
        val THEME_MODE = stringPreferencesKey("appearance_theme_mode")
        val LATIN_NUMBER_FONT = stringPreferencesKey("appearance_latin_number_font")
        val UI_DENSITY_SCALE = floatPreferencesKey("appearance_ui_density_scale")
        val CARD_DENSITY = stringPreferencesKey("appearance_card_density")
        val HOME_HEADER_ORDER = stringPreferencesKey("appearance_home_header_order")
        val FAB_PLACEMENT = stringPreferencesKey("appearance_fab_placement")
        val SHOW_CHANNEL_BADGE = booleanPreferencesKey("appearance_show_channel_badge")
        val SHOW_PARSE_BADGE = booleanPreferencesKey("appearance_show_parse_badge")
        val TIP_TAG_TEXT = stringPreferencesKey("appearance_tip_tag_text")
        val CUSTOM_TIPS_RAW = stringPreferencesKey("appearance_custom_tips_raw")
        val TAB_CLICK_ANIMATION = stringPreferencesKey("appearance_tab_click_animation")
        val PREDICTIVE_BACK_ENABLED = booleanPreferencesKey("appearance_predictive_back_enabled")
        val PREDICTIVE_BACK_STYLE = stringPreferencesKey("appearance_predictive_back_style")
        val HOME_BRAND_SHOW_ICON = booleanPreferencesKey("appearance_home_brand_show_icon")
        val HOME_BRAND_SHOW_TITLE = booleanPreferencesKey("appearance_home_brand_show_title")
        val HOME_BRAND_CUSTOM_NAME = stringPreferencesKey("appearance_home_brand_custom_name")
        val HOME_BRAND_CUSTOM_SUBTITLE = stringPreferencesKey("appearance_home_brand_custom_subtitle")
        val HOME_BRAND_ICON_SIZE_DP = intPreferencesKey("appearance_home_brand_icon_size_dp")
        val HOME_BRAND_ICON_CORNER_DP = intPreferencesKey("appearance_home_brand_icon_corner_dp")
        val HOME_BRAND_ICON_SPACING_DP = intPreferencesKey("appearance_home_brand_icon_spacing_dp")
        val HOME_BRAND_TITLE_SIZE_SP = intPreferencesKey("appearance_home_brand_title_size_sp")
        val HOME_BRAND_TITLE_BOLD = booleanPreferencesKey("appearance_home_brand_title_bold")
        val HOME_BRAND_SUBTITLE_SIZE_SP = intPreferencesKey("appearance_home_brand_subtitle_size_sp")
        val ANALYTICS_PIE_SIZE_DP = intPreferencesKey("appearance_analytics_pie_size_dp")
        val ANALYTICS_PULL_CALENDAR_DISTANCE_DP = intPreferencesKey("appearance_analytics_pull_calendar_distance_dp")
        val APP_ICON_STYLE_ID = stringPreferencesKey("appearance_app_icon_style_id")
        val ICON_VISUAL_STYLE = stringPreferencesKey("appearance_icon_visual_style")

        val EXCHANGE_RATES_JSON = stringPreferencesKey("exchange_rates_json")
        val EXCHANGE_RATES_UPDATED_AT = longPreferencesKey("exchange_rates_updated_at")
        val EXCHANGE_RATES_STATUS = stringPreferencesKey("exchange_rates_status")
        val EXCHANGE_RATES_SOURCE = stringPreferencesKey("exchange_rates_source")
        val EXCHANGE_RATES_DATE_TEXT = stringPreferencesKey("exchange_rates_date_text")
        val EXCHANGE_RATE_AUTO_UPDATE_FREQ = stringPreferencesKey("exchange_rate_auto_update_freq")

        val AI_PROVIDER_MODE = stringPreferencesKey("ai_provider_mode")
        val AI_LOCAL_MODEL_ID = stringPreferencesKey("ai_local_model_id")
        val AI_LOCAL_MODEL_MIRROR = stringPreferencesKey("ai_local_model_mirror")
        val AI_API_BASE_URL = stringPreferencesKey("ai_api_base_url")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_API_MODEL_NAME = stringPreferencesKey("ai_api_model_name")
        val AI_FALLBACK_ONLY = booleanPreferencesKey("ai_fallback_only")
    }

    val budgetSettingsFlow: Flow<BudgetSettings> = context.smartLedgerDataStore.data.map { prefs ->
        BudgetSettings(
            monthlyBudgetCents = prefs[Keys.MONTHLY_BUDGET_CENTS] ?: 500_000L,
            autoRecordEnabled = prefs[Keys.AUTO_RECORD_ENABLED] ?: true,
            requireManualConfirm = prefs[Keys.REQUIRE_MANUAL_CONFIRM] ?: false,
            smartSemanticEnabled = prefs[Keys.SMART_SEMANTIC_ENABLED] ?: true,
            notifyOnRecorded = prefs[Keys.NOTIFY_ON_RECORDED] ?: true,
            baseCurrencyCode = prefs[Keys.BASE_CURRENCY_CODE] ?: "CNY",
            rootKeepAliveEnabled = prefs[Keys.ROOT_KEEP_ALIVE_ENABLED] ?: false,
            backupReminderEnabled = prefs[Keys.BACKUP_REMINDER_ENABLED] ?: false,
            backupReminderIntervalHours = (prefs[Keys.BACKUP_REMINDER_INTERVAL_HOURS] ?: 168).coerceIn(1, 8760),
            lastLocalBackupMillis = prefs[Keys.LAST_LOCAL_BACKUP_MILLIS] ?: 0L,
            lastBackupReminderMillis = prefs[Keys.LAST_BACKUP_REMINDER_MILLIS] ?: 0L,
            exchangeRateUpdateFrequency = com.example.model.ExchangeRateAutoUpdateFrequency
                .fromRaw(prefs[Keys.EXCHANGE_RATE_AUTO_UPDATE_FREQ]).name
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
        val storedTag = prefs[Keys.TIP_TAG_TEXT] ?: ""
        AppearanceSettings(
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            customPrimaryColorArgb = prefs[Keys.CUSTOM_PRIMARY_COLOR_ARGB] ?: 0xFF0F6B54L,
            customPaletteStyle = CustomPaletteStyle.fromRaw(prefs[Keys.CUSTOM_PALETTE_STYLE]),
            themeMode = ThemeMode.fromRaw(prefs[Keys.THEME_MODE]),
            latinNumberFont = LatinNumberFont.fromRaw(prefs[Keys.LATIN_NUMBER_FONT]),
            uiDensityScale = (prefs[Keys.UI_DENSITY_SCALE] ?: 1.0f).coerceIn(0.75f, 1.35f),
            cardDensity = CardDensity.fromRaw(prefs[Keys.CARD_DENSITY]),
            homeHeaderOrder = HomeHeaderOrder.fromRaw(prefs[Keys.HOME_HEADER_ORDER]),
            fabPlacement = FabPlacement.fromRaw(prefs[Keys.FAB_PLACEMENT]),
            showChannelBadge = prefs[Keys.SHOW_CHANNEL_BADGE] ?: true,
            showParseBadge = prefs[Keys.SHOW_PARSE_BADGE] ?: true,
            tipTagText = if (storedTag == "金钱真相") "" else storedTag,
            customTipsRaw = prefs[Keys.CUSTOM_TIPS_RAW] ?: "",
            tabClickAnimation = TabClickAnimationStyle.fromRaw(prefs[Keys.TAB_CLICK_ANIMATION]),
            predictiveBackEnabled = prefs[Keys.PREDICTIVE_BACK_ENABLED] ?: true,
            predictiveBackStyle = PredictiveBackStyle.fromRaw(prefs[Keys.PREDICTIVE_BACK_STYLE]),
            homeBrandShowIcon = prefs[Keys.HOME_BRAND_SHOW_ICON] ?: true,
            homeBrandShowTitle = prefs[Keys.HOME_BRAND_SHOW_TITLE] ?: true,
            homeBrandCustomName = prefs[Keys.HOME_BRAND_CUSTOM_NAME] ?: "",
            homeBrandCustomSubtitle = prefs[Keys.HOME_BRAND_CUSTOM_SUBTITLE] ?: "",
            homeBrandIconSizeDp = (prefs[Keys.HOME_BRAND_ICON_SIZE_DP] ?: 34).coerceIn(20, 52),
            homeBrandIconCornerDp = (prefs[Keys.HOME_BRAND_ICON_CORNER_DP] ?: 10).coerceIn(0, 26),
            homeBrandIconSpacingDp = (prefs[Keys.HOME_BRAND_ICON_SPACING_DP] ?: 10).coerceIn(4, 24),
            homeBrandTitleSizeSp = (prefs[Keys.HOME_BRAND_TITLE_SIZE_SP] ?: 24).coerceIn(16, 36),
            homeBrandTitleBold = prefs[Keys.HOME_BRAND_TITLE_BOLD] ?: true,
            homeBrandSubtitleSizeSp = (prefs[Keys.HOME_BRAND_SUBTITLE_SIZE_SP] ?: 11).coerceIn(9, 16),
            analyticsPieSizeDp = (prefs[Keys.ANALYTICS_PIE_SIZE_DP] ?: 220).coerceIn(140, 300),
            analyticsPullCalendarDistanceDp = (prefs[Keys.ANALYTICS_PULL_CALENDAR_DISTANCE_DP] ?: 128).coerceIn(72, 220),
            appIconStyleId = AppIconStyle.fromId(prefs[Keys.APP_ICON_STYLE_ID]).id,
            iconVisualStyle = IconVisualStyle.fromRaw(prefs[Keys.ICON_VISUAL_STYLE])
        )
    }

    val aiConfigFlow: Flow<com.example.model.AiConfig> = context.smartLedgerDataStore.data.map { prefs ->
        com.example.model.AiConfig(
            providerMode = com.example.model.AiProviderMode.fromRaw(prefs[Keys.AI_PROVIDER_MODE]),
            localModelId = prefs[Keys.AI_LOCAL_MODEL_ID] ?: "bge-small-zh-v15",
            localModelMirror = prefs[Keys.AI_LOCAL_MODEL_MIRROR] ?: "https://hf-mirror.com",
            apiBaseUrl = prefs[Keys.AI_API_BASE_URL] ?: "",
            apiKey = prefs[Keys.AI_API_KEY] ?: "",
            apiModelName = prefs[Keys.AI_API_MODEL_NAME] ?: "",
            useAiAsFallbackOnly = prefs[Keys.AI_FALLBACK_ONLY] ?: true
        )
    }

    val exchangeRateStateFlow: Flow<ExchangeRateState> = context.smartLedgerDataStore.data.map { prefs ->
        val parsed = decodeRatesJson(prefs[Keys.EXCHANGE_RATES_JSON])
        ExchangeRateState(
            ratesToCny = parsed,
            updatedAtMillis = prefs[Keys.EXCHANGE_RATES_UPDATED_AT] ?: 0L,
            lastStatus = prefs[Keys.EXCHANGE_RATES_STATUS] ?: "",
            dataSource = prefs[Keys.EXCHANGE_RATES_SOURCE] ?: "内置参考汇率（初始基准）",
            rateDateText = prefs[Keys.EXCHANGE_RATES_DATE_TEXT] ?: ""
        )
    }

    suspend fun getBudgetSettingsOnce(): BudgetSettings = budgetSettingsFlow.first()

    suspend fun getWebDavConfigOnce(): WebDavConfig = webDavConfigFlow.first()

    suspend fun getExchangeRateStateOnce(): ExchangeRateState = exchangeRateStateFlow.first()

    suspend fun getAiConfigOnce(): com.example.model.AiConfig = aiConfigFlow.first()

    suspend fun isRulesSeeded(): Boolean =
        context.smartLedgerDataStore.data.first()[Keys.RULES_SEEDED] ?: false

    suspend fun markRulesSeeded() {
        context.smartLedgerDataStore.edit { prefs ->
            prefs[Keys.RULES_SEEDED] = true
        }
    }

    suspend fun saveBudgetSettings(settings: BudgetSettings) {
        context.smartLedgerDataStore.edit { prefs ->
            prefs[Keys.MONTHLY_BUDGET_CENTS] = settings.monthlyBudgetCents.coerceIn(0L, 1_000_000_000L)
            prefs[Keys.AUTO_RECORD_ENABLED] = settings.autoRecordEnabled
            prefs[Keys.REQUIRE_MANUAL_CONFIRM] = settings.requireManualConfirm
            prefs[Keys.SMART_SEMANTIC_ENABLED] = settings.smartSemanticEnabled
            prefs[Keys.NOTIFY_ON_RECORDED] = settings.notifyOnRecorded
            prefs[Keys.BASE_CURRENCY_CODE] = CurrencyCatalog.resolve(settings.baseCurrencyCode).code
            prefs[Keys.ROOT_KEEP_ALIVE_ENABLED] = settings.rootKeepAliveEnabled
            prefs[Keys.BACKUP_REMINDER_ENABLED] = settings.backupReminderEnabled
            prefs[Keys.BACKUP_REMINDER_INTERVAL_HOURS] = settings.backupReminderIntervalHours.coerceIn(1, 8760)
            prefs[Keys.LAST_LOCAL_BACKUP_MILLIS] = settings.lastLocalBackupMillis.coerceAtLeast(0L)
            prefs[Keys.LAST_BACKUP_REMINDER_MILLIS] = settings.lastBackupReminderMillis.coerceAtLeast(0L)
            prefs[Keys.EXCHANGE_RATE_AUTO_UPDATE_FREQ] = com.example.model.ExchangeRateAutoUpdateFrequency
                .fromRaw(settings.exchangeRateUpdateFrequency).name
        }
    }

    suspend fun markLocalBackupCompleted(millis: Long) {
        context.smartLedgerDataStore.edit { prefs ->
            prefs[Keys.LAST_LOCAL_BACKUP_MILLIS] = millis
        }
    }

    suspend fun markBackupReminderSent(millis: Long) {
        context.smartLedgerDataStore.edit { prefs ->
            prefs[Keys.LAST_BACKUP_REMINDER_MILLIS] = millis
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
            prefs[Keys.CUSTOM_PRIMARY_COLOR_ARGB] = settings.customPrimaryColorArgb
            prefs[Keys.CUSTOM_PALETTE_STYLE] = settings.customPaletteStyle.name
            prefs[Keys.THEME_MODE] = settings.themeMode.name
            prefs[Keys.LATIN_NUMBER_FONT] = settings.latinNumberFont.name
            prefs[Keys.UI_DENSITY_SCALE] = settings.uiDensityScale.coerceIn(0.75f, 1.35f)
            prefs[Keys.CARD_DENSITY] = settings.cardDensity.name
            prefs[Keys.HOME_HEADER_ORDER] = settings.homeHeaderOrder.name
            prefs[Keys.FAB_PLACEMENT] = settings.fabPlacement.name
            prefs[Keys.SHOW_CHANNEL_BADGE] = settings.showChannelBadge
            prefs[Keys.SHOW_PARSE_BADGE] = settings.showParseBadge
            prefs[Keys.TIP_TAG_TEXT] = settings.tipTagText.trim().take(16)
            prefs[Keys.CUSTOM_TIPS_RAW] = settings.customTipsRaw
            prefs[Keys.TAB_CLICK_ANIMATION] = settings.tabClickAnimation.name
            prefs[Keys.PREDICTIVE_BACK_ENABLED] = settings.predictiveBackEnabled
            prefs[Keys.PREDICTIVE_BACK_STYLE] = settings.predictiveBackStyle.name
            prefs[Keys.HOME_BRAND_SHOW_ICON] = settings.homeBrandShowIcon
            prefs[Keys.HOME_BRAND_SHOW_TITLE] = settings.homeBrandShowTitle
            prefs[Keys.HOME_BRAND_CUSTOM_NAME] = settings.homeBrandCustomName.take(24)
            prefs[Keys.HOME_BRAND_CUSTOM_SUBTITLE] = settings.homeBrandCustomSubtitle.take(36)
            prefs[Keys.HOME_BRAND_ICON_SIZE_DP] = settings.homeBrandIconSizeDp.coerceIn(20, 52)
            prefs[Keys.HOME_BRAND_ICON_CORNER_DP] = settings.homeBrandIconCornerDp.coerceIn(0, 26)
            prefs[Keys.HOME_BRAND_ICON_SPACING_DP] = settings.homeBrandIconSpacingDp.coerceIn(4, 24)
            prefs[Keys.HOME_BRAND_TITLE_SIZE_SP] = settings.homeBrandTitleSizeSp.coerceIn(16, 36)
            prefs[Keys.HOME_BRAND_TITLE_BOLD] = settings.homeBrandTitleBold
            prefs[Keys.HOME_BRAND_SUBTITLE_SIZE_SP] = settings.homeBrandSubtitleSizeSp.coerceIn(9, 16)
            prefs[Keys.ANALYTICS_PIE_SIZE_DP] = settings.analyticsPieSizeDp.coerceIn(140, 300)
            prefs[Keys.ANALYTICS_PULL_CALENDAR_DISTANCE_DP] = settings.analyticsPullCalendarDistanceDp.coerceIn(72, 220)
            prefs[Keys.APP_ICON_STYLE_ID] = AppIconStyle.fromId(settings.appIconStyleId).id
            prefs[Keys.ICON_VISUAL_STYLE] = settings.iconVisualStyle.name
        }
    }

    suspend fun saveAiConfig(config: com.example.model.AiConfig) {
        context.smartLedgerDataStore.edit { prefs ->
            prefs[Keys.AI_PROVIDER_MODE] = config.providerMode.name
            prefs[Keys.AI_LOCAL_MODEL_ID] = config.localModelId.trim().ifBlank { "bge-small-zh-v15" }
            prefs[Keys.AI_LOCAL_MODEL_MIRROR] = config.localModelMirror.trim().ifBlank { "https://hf-mirror.com" }
            prefs[Keys.AI_API_BASE_URL] = config.apiBaseUrl.trim()
            prefs[Keys.AI_API_KEY] = config.apiKey.trim()
            prefs[Keys.AI_API_MODEL_NAME] = config.apiModelName.trim()
            prefs[Keys.AI_FALLBACK_ONLY] = config.useAiAsFallbackOnly
        }
    }

    suspend fun saveExchangeRates(
        ratesToCny: Map<String, Double>,
        updatedAtMillis: Long,
        status: String,
        dataSource: String,
        rateDateText: String
    ) {
        val json = JSONObject().apply {
            ratesToCny.forEach { (code, value) ->
                if (value > 0.0) put(code, value)
            }
        }.toString()
        context.smartLedgerDataStore.edit { prefs ->
            prefs[Keys.EXCHANGE_RATES_JSON] = json
            prefs[Keys.EXCHANGE_RATES_UPDATED_AT] = updatedAtMillis
            prefs[Keys.EXCHANGE_RATES_STATUS] = status
            prefs[Keys.EXCHANGE_RATES_SOURCE] = dataSource
            prefs[Keys.EXCHANGE_RATES_DATE_TEXT] = rateDateText
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
