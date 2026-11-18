package com.example.data

import com.example.model.CurrencyCatalog
import com.example.model.ExchangeRateState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

class CurrencyExchangeManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun refreshRatesDatabase(repository: LedgerRepository): Result<ExchangeRateState> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(RATE_ENDPOINT)
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

                val json = JSONObject(bodyText)
                val ratesObj = json.optJSONObject("rates")
                    ?: return@withContext Result.failure(IllegalStateException("汇率数据格式无法识别"))

                val updatedMap = CurrencyCatalog.defaultRatesToCny.toMutableMap()
                var updatedCount = 0
                CurrencyCatalog.currencies.forEach { info ->
                    if (info.code == "CNY") {
                        updatedMap["CNY"] = 1.0
                    } else {
                        val perCny = ratesObj.optDouble(info.code, 0.0)
                        if (perCny > 0.0) {
                            updatedMap[info.code] = 1.0 / perCny
                            updatedCount += 1
                        }
                    }
                }

                val now = System.currentTimeMillis()
                val status = "已更新 $updatedCount 种货币汇率"
                repository.saveExchangeRates(updatedMap, now, status)
                Result.success(
                    ExchangeRateState(
                        ratesToCny = updatedMap,
                        updatedAtMillis = now,
                        lastStatus = status
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(IllegalStateException("更新汇率失败：${e.localizedMessage ?: "网络异常"}", e))
            }
        }

    companion object {
        private const val RATE_ENDPOINT = "https://open.er-api.com/v6/latest/CNY"

        fun convertCents(
            amountCents: Long,
            fromCurrency: String,
            toCurrency: String,
            ratesToCny: Map<String, Double>
        ): Long {
            val from = fromCurrency.uppercase().ifBlank { "CNY" }
            val to = toCurrency.uppercase().ifBlank { "CNY" }
            if (from == to) return amountCents

            val fromToCny = if (from == "CNY") 1.0 else (ratesToCny[from] ?: CurrencyCatalog.resolve(from).defaultRateToCny)
            val toToCny = if (to == "CNY") 1.0 else (ratesToCny[to] ?: CurrencyCatalog.resolve(to).defaultRateToCny)
            if (toToCny <= 0.0) return amountCents

            val cnyCents = amountCents * fromToCny
            return (cnyCents / toToCny).roundToLong()
        }
    }
}
