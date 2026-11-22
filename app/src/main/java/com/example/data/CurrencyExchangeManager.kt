package com.example.data

import com.example.model.CurrencyCatalog
import com.example.model.ExchangeRateSource
import com.example.model.ExchangeRateState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.roundToLong

data class ParsedRatesPayload(
    val ratesToCny: Map<String, Double>,
    val updatedCount: Int,
    val sourceLabel: String,
    val rateDateText: String,
    val availableCurrencyCodes: List<String> = ratesToCny.keys.sorted()
)

class CurrencyExchangeManager {

    private val client: OkHttpClient
        get() = AppHttpClient.derived(connectTimeoutSec = 12, readTimeoutSec = 15)

    suspend fun refreshRatesDatabase(
        repository: LedgerRepository,
        sourceOverride: ExchangeRateSource? = null,
        customUrlOverride: String? = null
    ): Result<ExchangeRateState> = withContext(Dispatchers.IO) {
        try {
            val settings = repository.getSettingsOnce()
            val selectedSource = sourceOverride ?: ExchangeRateSource.fromRaw(settings.exchangeRateSource)
            val customUrl = (customUrlOverride ?: settings.customExchangeRateUrl).trim()

            val targetUrl = when (selectedSource) {
                ExchangeRateSource.CUSTOM -> {
                    if (!customUrl.startsWith("http://", ignoreCase = true) &&
                        !customUrl.startsWith("https://", ignoreCase = true)
                    ) {
                        return@withContext Result.failure(
                            IllegalArgumentException("自定义汇率链接需以 http:// 或 https:// 开头")
                        )
                    }
                    customUrl
                }
                else -> selectedSource.endpointUrl
            }

            val request = Request.Builder()
                .url(targetUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "XiaoBenBen-Android")
                .get()
                .build()

            val bodyText = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("汇率服务响应异常 (HTTP ${response.code})")
                    )
                }
                response.body?.string() ?: throw IllegalStateException("汇率响应体为空")
            }

            val defaultSourceLabel = if (selectedSource == ExchangeRateSource.CUSTOM) {
                "自定义源 ($targetUrl)"
            } else {
                "${selectedSource.label} (基准 CNY)"
            }

            val enabledCodes = CurrencyCatalog.normalizeEnabledForeignCodes(
                settings.enabledCurrencyCodes,
                ensureCode = settings.baseCurrencyCode
            )
            // Parse ALL currencies returned by the selected/custom data source so every returned currency becomes selectable
            val parsed = parseRatesPayload(
                bodyText = bodyText,
                fallbackSourceLabel = defaultSourceLabel,
                targetForeignCodes = null
            ) ?: return@withContext Result.failure(
                IllegalStateException("无法解析该接口返回的汇率 JSON 格式（需包含 rates / conversion_rates / cny 等字段）")
            )

            val enabledUpdatedCount = enabledCodes.count { it in parsed.ratesToCny }
            val totalAvailableCount = parsed.availableCurrencyCodes.size
            val now = System.currentTimeMillis()
            val status = if (selectedSource == ExchangeRateSource.CUSTOM) {
                "已从自定义源同步 $totalAvailableCount 种可选币种（已启用 $enabledUpdatedCount 种更新）"
            } else {
                "已从 ${selectedSource.label} 更新 $enabledUpdatedCount 种已启用汇率（共 $totalAvailableCount 种可选币种）"
            }
            repository.saveExchangeRates(
                ratesToCny = parsed.ratesToCny,
                updatedAtMillis = now,
                status = status,
                dataSource = parsed.sourceLabel,
                rateDateText = parsed.rateDateText,
                sourceId = selectedSource.name,
                sourceAvailableCodes = parsed.availableCurrencyCodes
            )
            Result.success(
                ExchangeRateState(
                    ratesToCny = parsed.ratesToCny,
                    updatedAtMillis = now,
                    lastStatus = status,
                    dataSource = parsed.sourceLabel,
                    rateDateText = parsed.rateDateText,
                    sourceId = selectedSource.name,
                    sourceAvailableCodes = parsed.availableCurrencyCodes
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException("更新汇率失败：${e.localizedMessage ?: "网络异常"}", e))
        }
    }

    companion object {
        private val IGNORED_TOP_LEVEL_KEYS = setOf(
            "DATE", "TIME", "TIMESTAMP", "TTL", "CODE", "STATUS", "RESULT", "OK",
            "SUCCESS", "ERROR", "VERSION", "COUNT", "TOTAL", "BASE", "AMOUNT"
        )

        fun parseRatesPayload(
            bodyText: String,
            fallbackSourceLabel: String,
            targetForeignCodes: Collection<String>? = null
        ): ParsedRatesPayload? {
            val json = runCatching { JSONObject(bodyText) }.getOrNull() ?: return null

            var baseCode = json.optString("base_code")
                .ifBlank { json.optString("base") }
                .trim()
                .uppercase()

            var usedTopLevelFallback = false
            val rawRatesObj: JSONObject = when {
                json.optJSONObject("rates") != null -> json.getJSONObject("rates")
                json.optJSONObject("conversion_rates") != null -> json.getJSONObject("conversion_rates")
                json.optJSONObject("cny") != null -> {
                    if (baseCode.isBlank()) baseCode = "CNY"
                    json.getJSONObject("cny")
                }
                json.optJSONObject("usd") != null -> {
                    if (baseCode.isBlank()) baseCode = "USD"
                    json.getJSONObject("usd")
                }
                json.optJSONObject("eur") != null -> {
                    if (baseCode.isBlank()) baseCode = "EUR"
                    json.getJSONObject("eur")
                }
                json.optJSONObject("data") != null -> json.getJSONObject("data")
                else -> {
                    usedTopLevelFallback = true
                    json
                }
            }

            if (baseCode.isBlank()) baseCode = "CNY"

            val perBaseMap = LinkedHashMap<String, Double>()
            val keys = rawRatesObj.keys()
            while (keys.hasNext()) {
                val rawKey = keys.next()
                val code = rawKey.trim().uppercase()
                if (!CurrencyCatalog.isValidCurrencyCode(code)) continue
                if (usedTopLevelFallback && code in IGNORED_TOP_LEVEL_KEYS) continue
                val directVal = rawRatesObj.optDouble(rawKey, Double.NaN)
                val resolvedVal = when {
                    !directVal.isNaN() && directVal > 0.0 -> directVal
                    else -> rawRatesObj.optJSONObject(rawKey)?.optDouble("value", Double.NaN)
                        ?.takeIf { !it.isNaN() && it > 0.0 }
                }
                if (resolvedVal != null && resolvedVal > 0.0) {
                    perBaseMap[code] = resolvedVal
                }
            }

            if (perBaseMap.isEmpty()) return null
            perBaseMap.putIfAbsent(baseCode, 1.0)

            val cnyPerBase = if (baseCode == "CNY") 1.0 else (perBaseMap["CNY"] ?: return null)
            if (cnyPerBase <= 0.0) return null

            val allAvailableCodes = perBaseMap.keys.toMutableSet().apply { add("CNY") }.sorted()
            val targetSet = targetForeignCodes?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() }?.toSet()
            val updatedMap = LinkedHashMap<String, Double>()
            updatedMap["CNY"] = 1.0
            var updatedCount = 0

            if (targetSet != null) {
                // Explicit filter mode (e.g. unit test requesting only specific foreign codes)
                targetSet.forEach { code ->
                    if (code == "CNY") return@forEach
                    val unitPerBase = perBaseMap[code]
                    if (unitPerBase != null && unitPerBase > 0.0) {
                        updatedMap[code] = cnyPerBase / unitPerBase
                        updatedCount += 1
                    } else {
                        CurrencyCatalog.byCode[code]?.let { updatedMap[code] = it.defaultRateToCny }
                    }
                }
            } else {
                // Full-source mode: include EVERY currency returned by the source (standard + custom codes)
                perBaseMap.forEach { (code, unitPerBase) ->
                    if (code == "CNY") {
                        updatedMap["CNY"] = 1.0
                    } else if (unitPerBase > 0.0) {
                        updatedMap[code] = cnyPerBase / unitPerBase
                        updatedCount += 1
                    }
                }
            }
            if (updatedCount == 0) return null

            val providerUrl = json.optString("provider").trim()
            val rateDateText = json.optString("time_last_update_utc")
                .ifBlank { json.optString("date") }
                .ifBlank { json.optString("updated_at") }
                .replace(" +0000", " UTC")
                .trim()
            val sourceLabel = if (providerUrl.isNotBlank()) {
                "$fallbackSourceLabel · $providerUrl"
            } else {
                fallbackSourceLabel
            }

            return ParsedRatesPayload(
                ratesToCny = updatedMap,
                updatedCount = updatedCount,
                sourceLabel = sourceLabel,
                rateDateText = rateDateText,
                availableCurrencyCodes = allAvailableCodes
            )
        }

        fun rateBetween(
            fromCurrency: String,
            toCurrency: String,
            ratesToCny: Map<String, Double>
        ): Double {
            val from = fromCurrency.uppercase().ifBlank { "CNY" }
            val to = toCurrency.uppercase().ifBlank { "CNY" }
            if (from == to) return 1.0
            val fromToCny = if (from == "CNY") 1.0 else (ratesToCny[from] ?: CurrencyCatalog.resolve(from).defaultRateToCny)
            val toToCny = if (to == "CNY") 1.0 else (ratesToCny[to] ?: CurrencyCatalog.resolve(to).defaultRateToCny)
            if (toToCny <= 0.0) return 1.0
            return fromToCny / toToCny
        }

        fun convertCents(
            amountCents: Long,
            fromCurrency: String,
            toCurrency: String,
            ratesToCny: Map<String, Double>
        ): Long {
            val from = fromCurrency.uppercase().ifBlank { "CNY" }
            val to = toCurrency.uppercase().ifBlank { "CNY" }
            if (from == to) return amountCents
            val rate = rateBetween(from, to, ratesToCny)
            return (amountCents * rate).roundToLong()
        }
    }
}
