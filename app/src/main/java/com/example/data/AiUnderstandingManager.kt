package com.example.data

import android.content.Context
import com.example.model.AiConfig
import com.example.model.AiProviderMode
import com.example.model.CategoryCatalog
import com.example.model.CurrencyCatalog
import com.example.model.LocalModelFileSpec
import com.example.model.LocalModelPreset
import com.example.model.ParseMethod
import com.example.model.ParsedNotificationResult
import com.example.model.TransactionType
import com.example.parser.ChinaNotificationParser
import com.example.parser.ChinaPaymentCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.CustomLocalModelInfo
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

object LocalModelCatalog {
    const val CUSTOM_PREFIX = "custom_"

    val allowedWeightExtensions = setOf(
        "gguf", "onnx", "tflite", "safetensors", "bin", "pt", "pth", "vocab", "txt", "json", "model"
    )
    private val blockedExecutableExtensions = setOf(
        "dex", "jar", "so", "apk", "aab", "exe", "dll", "sh", "bat", "elf"
    )

    val presets: List<LocalModelPreset> = listOf(
        LocalModelPreset(
            id = "ernie-3.0-nano-zh",
            name = "轻巧极速档 · ERNIE-3.0-Nano-Zh",
            repoId = "nghuyong/ernie-3.0-nano-zh",
            license = "Apache-2.0",
            sizeHint = "约 130 KB（超小体积 · 极速按需下载）",
            summary = "第一档超轻量中文核，毫秒级离线分词并匹配消费/收入/退款意图，几乎不占存储空间。",
            files = listOf(
                LocalModelFileSpec("config.json", "resolve/main/config.json"),
                LocalModelFileSpec("vocab.txt", "resolve/main/vocab.txt")
            )
        ),
        LocalModelPreset(
            id = "bge-small-zh-v15",
            name = "均衡推荐档 · BGE-Small-Zh v1.5",
            repoId = "BAAI/bge-small-zh-v15",
            license = "MIT License",
            sizeHint = "约 560 KB（中等体积 · 词表 + 分词 + 配置）",
            summary = "第二档智源开源中文平衡核，兼顾体积与理解精度，适合日常消费分类、商户切分与意图评分。",
            files = listOf(
                LocalModelFileSpec("config.json", "resolve/main/config.json"),
                LocalModelFileSpec("vocab.txt", "resolve/main/vocab.txt"),
                LocalModelFileSpec("tokenizer.json", "resolve/main/tokenizer.json")
            )
        ),
        LocalModelPreset(
            id = "qwen25-0.5b-instruct",
            name = "增强全量档 · Qwen2.5-0.5B-Instruct",
            repoId = "Qwen/Qwen2.5-0.5B-Instruct",
            license = "Apache-2.0",
            sizeHint = "约 7.1 MB（大词表体积 · BPE 全量词表 + 配置）",
            summary = "第三档通义轻量全量词表增强核，覆盖复杂账单长文本、外币符号及多语言混合商户解析。",
            files = listOf(
                LocalModelFileSpec("config.json", "resolve/main/config.json"),
                LocalModelFileSpec("tokenizer_config.json", "resolve/main/tokenizer_config.json"),
                LocalModelFileSpec("tokenizer.json", "resolve/main/tokenizer.json")
            )
        )
    )

    fun resolve(modelId: String?): LocalModelPreset =
        presets.firstOrNull { it.id == modelId } ?: resolveDefaultPreset()

    private fun resolveDefaultPreset(): LocalModelPreset =
        presets.firstOrNull { it.id == "bge-small-zh-v15" } ?: presets.first()

    fun modelRepoUrl(mirrorBase: String, repoId: String): String {
        val cleanBase = mirrorBase.trim().trimEnd('/').ifBlank { "https://hf-mirror.com" }
        return "$cleanBase/${repoId.trimStart('/')}"
    }

    fun weightsBrowserPortalUrl(mirrorBase: String): String {
        val cleanBase = mirrorBase.trim().trimEnd('/').ifBlank { "https://hf-mirror.com" }
        return "$cleanBase/models?sort=trending"
    }

    fun isCustomModelId(modelId: String?): Boolean =
        !modelId.isNullOrBlank() && modelId.startsWith(CUSTOM_PREFIX)

    fun isBlockedExecutableExtension(ext: String): Boolean =
        ext.lowercase(Locale.ROOT) in blockedExecutableExtensions

    fun isSupportedWeightExtension(ext: String): Boolean =
        ext.lowercase(Locale.ROOT) in allowedWeightExtensions
}

data class LocalModelInstallStatus(
    val modelId: String,
    val installed: Boolean,
    val totalBytes: Long,
    val vocabCountEstimate: Int
)

class AiUnderstandingManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    fun modelDirectory(context: Context, modelId: String): File {
        val root = File(context.filesDir, "local_ai_models")
        if (!root.exists()) root.mkdirs()
        return File(root, modelId)
    }

    fun getCustomModelInfo(context: Context, modelId: String): CustomLocalModelInfo? {
        if (!LocalModelCatalog.isCustomModelId(modelId)) return null
        val dir = modelDirectory(context, modelId)
        val metaFile = File(dir, "custom_meta.json")
        if (!metaFile.exists()) return null
        return runCatching {
            val obj = JSONObject(metaFile.readText(Charsets.UTF_8))
            val weightFile = File(dir, obj.optString("fileName"))
            if (!weightFile.exists() || weightFile.length() <= 0L) return null
            CustomLocalModelInfo(
                id = modelId,
                displayName = obj.optString("displayName", modelId),
                fileName = obj.optString("fileName", weightFile.name),
                formatLabel = obj.optString("formatLabel", "自定义权重"),
                sizeBytes = weightFile.length(),
                importedAtMillis = obj.optLong("importedAtMillis", weightFile.lastModified()),
                vocabTokenCount = obj.optInt("vocabTokenCount", 0)
            )
        }.getOrNull()
    }

    fun listCustomImportedModels(context: Context): List<CustomLocalModelInfo> {
        val root = File(context.filesDir, "local_ai_models")
        if (!root.exists() || !root.isDirectory) return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory && LocalModelCatalog.isCustomModelId(it.name) }
            ?.mapNotNull { getCustomModelInfo(context, it.name) }
            ?.sortedByDescending { it.importedAtMillis }
            .orEmpty()
    }

    fun resolveModelDisplayName(context: Context, modelId: String?): String {
        if (LocalModelCatalog.isCustomModelId(modelId)) {
            return getCustomModelInfo(context, modelId!!)?.displayName ?: "已导入自定义模型"
        }
        return LocalModelCatalog.resolve(modelId).name
    }

    suspend fun importCustomModelWeight(
        context: Context,
        uri: Uri,
        explicitFileName: String? = null
    ): Result<CustomLocalModelInfo> = withContext(Dispatchers.IO) {
        try {
            val rawName = (explicitFileName ?: resolveDisplayNameFromUri(context, uri) ?: "imported_model.onnx")
                .replace(Regex("""[^\w.\-\u4e00-\u9fa5]"""), "_")
                .trim('_')
                .ifBlank { "imported_model.onnx" }

            val ext = rawName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (LocalModelCatalog.isBlockedExecutableExtension(ext)) {
                return@withContext Result.failure(
                    SecurityException("出于系统与上架安全策略，禁止导入可执行二进制文件 (.$ext)，仅支持静态模型权重与词表文件。")
                )
            }
            if (ext.isBlank() || !LocalModelCatalog.isSupportedWeightExtension(ext)) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "不支持的文件扩展名「${ext.ifBlank { "无扩展名" }}」，请导入 .gguf / .onnx / .tflite / .safetensors / .bin / .json / .txt 权重或词表文件。"
                    )
                )
            }

            val customId = "${LocalModelCatalog.CUSTOM_PREFIX}${UUID.randomUUID().toString().take(8)}"
            val dir = modelDirectory(context, customId)
            if (!dir.exists()) dir.mkdirs()

            val targetFile = File(dir, rawName)
            var totalBytes = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var n: Int
                    while (input.read(buffer).also { n = it } != -1) {
                        output.write(buffer, 0, n)
                        totalBytes += n
                    }
                    output.flush()
                }
            } ?: return@withContext Result.failure(IllegalStateException("无法读取所选文件流"))

            if (!targetFile.exists() || totalBytes < 16L) {
                dir.deleteRecursively()
                return@withContext Result.failure(IllegalArgumentException("导入的模型文件过小或为空"))
            }

            val detectedFormat = inspectWeightFormat(targetFile, ext)
            val vocabCount = extractPrintableChineseVocabCount(targetFile, ext)
            val now = System.currentTimeMillis()
            val displayName = rawName.substringBeforeLast('.').take(36).ifBlank { rawName }

            val metaJson = JSONObject().apply {
                put("id", customId)
                put("displayName", displayName)
                put("fileName", rawName)
                put("formatLabel", detectedFormat)
                put("sizeBytes", totalBytes)
                put("importedAtMillis", now)
                put("vocabTokenCount", vocabCount)
            }
            File(dir, "custom_meta.json").writeText(metaJson.toString(), Charsets.UTF_8)

            Result.success(
                CustomLocalModelInfo(
                    id = customId,
                    displayName = displayName,
                    fileName = rawName,
                    formatLabel = detectedFormat,
                    sizeBytes = totalBytes,
                    importedAtMillis = now,
                    vocabTokenCount = vocabCount
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException("导入模型权重失败：${e.localizedMessage ?: "未知错误"}", e))
        }
    }

    private fun resolveDisplayNameFromUri(context: Context, uri: Uri): String? {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(idx)
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun inspectWeightFormat(file: File, ext: String): String {
        val header = ByteArray(16)
        val readCount = runCatching {
            file.inputStream().use { it.read(header) }
        }.getOrDefault(0)
        val magic = if (readCount >= 4) String(header, 0, 4, Charsets.US_ASCII) else ""
        return when {
            magic == "GGUF" -> "GGUF 量化权重"
            ext == "gguf" -> "GGUF 模型文件"
            ext == "onnx" -> "ONNX 推理图"
            ext == "tflite" -> "LiteRT / TFLite 权重"
            ext == "safetensors" -> "SafeTensors 安全权重"
            ext == "txt" || ext == "vocab" -> "开源中文词表"
            ext == "json" -> "Tokenizer 分词器配置"
            else -> "${ext.uppercase(Locale.ROOT)} 权重"
        }
    }

    private fun extractPrintableChineseVocabCount(file: File, ext: String): Int {
        if (ext == "txt" || ext == "vocab") {
            return runCatching { file.useLines { it.count() } }.getOrDefault(0)
        }
        if (ext == "json") {
            return runCatching {
                val obj = JSONObject(file.readText(Charsets.UTF_8))
                obj.optJSONObject("model")?.optJSONObject("vocab")?.length() ?: 0
            }.getOrDefault(0)
        }
        return 0
    }

    fun getInstallStatus(context: Context, modelId: String): LocalModelInstallStatus {
        if (LocalModelCatalog.isCustomModelId(modelId)) {
            val info = getCustomModelInfo(context, modelId)
            return if (info != null) {
                LocalModelInstallStatus(
                    modelId = info.id,
                    installed = true,
                    totalBytes = info.sizeBytes,
                    vocabCountEstimate = info.vocabTokenCount.coerceAtLeast(32_000)
                )
            } else {
                LocalModelInstallStatus(modelId, installed = false, totalBytes = 0L, vocabCountEstimate = 0)
            }
        }

        val preset = LocalModelCatalog.resolve(modelId)
        val dir = modelDirectory(context, preset.id)
        if (!dir.exists()) {
            return LocalModelInstallStatus(preset.id, installed = false, totalBytes = 0L, vocabCountEstimate = 0)
        }
        val expectedFiles = preset.files.map { File(dir, it.fileName) }
        val installed = expectedFiles.all { it.exists() && it.length() > 16L }
        val bytes = expectedFiles.sumOf { if (it.exists()) it.length() else 0L }
        val vocabFile = File(dir, "vocab.txt")
        val vocabCount = when {
            vocabFile.exists() -> vocabFile.useLines { it.count() }
            installed -> 150_000
            else -> 0
        }
        return LocalModelInstallStatus(
            modelId = preset.id,
            installed = installed,
            totalBytes = bytes,
            vocabCountEstimate = vocabCount
        )
    }

    fun deleteLocalModel(context: Context, modelId: String): Boolean {
        val targetId = if (LocalModelCatalog.isCustomModelId(modelId)) {
            modelId
        } else {
            LocalModelCatalog.resolve(modelId).id
        }
        val dir = modelDirectory(context, targetId)
        return if (dir.exists()) dir.deleteRecursively() else true
    }

    suspend fun downloadLocalModel(
        context: Context,
        modelId: String,
        mirrorBaseUrl: String,
        onProgress: (progressFraction: Float, statusText: String) -> Unit
    ): Result<LocalModelInstallStatus> = withContext(Dispatchers.IO) {
        val preset = LocalModelCatalog.resolve(modelId)
        val dir = modelDirectory(context, preset.id)
        if (!dir.exists()) dir.mkdirs()

        val cleanMirror = mirrorBaseUrl.trim().trimEnd('/').ifBlank { "https://hf-mirror.com" }
        try {
            val totalFiles = preset.files.size
            preset.files.forEachIndexed { idx, spec ->
                val url = "$cleanMirror/${preset.repoId}/${spec.repoPath}"
                onProgress(idx.toFloat() / totalFiles.toFloat(), "正在下载 ${spec.fileName} (${idx + 1}/$totalFiles)...")

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "XiaoBenBen-Android/1.0")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("下载 ${spec.fileName} 失败 (HTTP ${response.code})，可尝试切换镜像源")
                        )
                    }
                    val body = response.body ?: return@withContext Result.failure(
                        IllegalStateException("下载 ${spec.fileName} 响应体为空")
                    )
                    val contentLength = body.contentLength()
                    val tmpFile = File(dir, "${spec.fileName}.tmp")
                    val targetFile = File(dir, spec.fileName)

                    body.byteStream().use { input ->
                        FileOutputStream(tmpFile).use { output ->
                            val buffer = ByteArray(8192)
                            var fileBytesRead = 0L
                            var n: Int
                            while (input.read(buffer).also { n = it } != -1) {
                                output.write(buffer, 0, n)
                                fileBytesRead += n
                                val fileFrac = if (contentLength > 0L) {
                                    (fileBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0.5f
                                }
                                val overall = (idx + fileFrac) / totalFiles.toFloat()
                                onProgress(
                                    overall.coerceIn(0f, 0.99f),
                                    "正在下载 ${spec.fileName} (${fileBytesRead / 1024} KB)"
                                )
                            }
                            output.flush()
                        }
                    }

                    if (!tmpFile.exists() || tmpFile.length() < 16L) {
                        tmpFile.delete()
                        return@withContext Result.failure(IllegalStateException("下载文件 ${spec.fileName} 校验失败"))
                    }
                    if (targetFile.exists()) targetFile.delete()
                    if (!tmpFile.renameTo(targetFile)) {
                        tmpFile.copyTo(targetFile, overwrite = true)
                        tmpFile.delete()
                    }
                }
            }
            onProgress(1f, "${preset.name} 下载完成")
            Result.success(getInstallStatus(context, preset.id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException("模型下载异常：${e.localizedMessage ?: "请检查网络连接"}", e))
        }
    }

    suspend fun enhanceOrParseNotification(
        context: Context,
        config: AiConfig,
        packageName: String,
        title: String,
        content: String,
        fallbackParsed: ParsedNotificationResult?
    ): Result<ParsedNotificationResult> {
        return when (config.providerMode) {
            AiProviderMode.DISABLED -> {
                fallbackParsed?.let { Result.success(it) }
                    ?: Result.failure(IllegalStateException("AI理解当前已关闭"))
            }
            AiProviderMode.ONLINE_API -> {
                parseWithOnlineApi(
                    config = config,
                    packageName = packageName,
                    title = title,
                    content = content
                )
            }
            AiProviderMode.LOCAL_SMALL_MODEL -> {
                parseWithLocalModel(
                    context = context,
                    modelId = config.localModelId,
                    packageName = packageName,
                    title = title,
                    content = content
                )
            }
        }
    }

    suspend fun parseWithLocalModel(
        context: Context,
        modelId: String,
        packageName: String,
        title: String,
        content: String
    ): Result<ParsedNotificationResult> = withContext(Dispatchers.Default) {
        val isCustom = LocalModelCatalog.isCustomModelId(modelId)
        val resolvedId = if (isCustom) modelId else LocalModelCatalog.resolve(modelId).id
        val status = getInstallStatus(context, resolvedId)
        if (!status.installed) {
            val missingName = if (isCustom) "导入的模型权重" else LocalModelCatalog.resolve(resolvedId).name
            return@withContext Result.failure(
                IllegalStateException("本地模型「$missingName」尚未就绪，请先在「AI理解」中下载或导入")
            )
        }

        val combined = "$title\n$content".trim()
        if (combined.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("通知内容为空"))
        }

        val dir = modelDirectory(context, resolvedId)
        val vocabTokens = loadVocabTokens(dir)
        val amountCents = ChinaNotificationParser.extractAmountCents(
            text = combined,
            allowLooseStandaloneNumber = true
        ) ?: return@withContext Result.failure(IllegalStateException("本地小模型未能在文本中检测到有效金额"))

        val matchedVocabHits = tokenizeWithVocab(combined, vocabTokens)
        val currencyCode = ChinaNotificationParser.detectCurrencyCode(combined)
        val type = inferTypeWithVocab(combined)
        val sourceApp = ChinaPaymentCatalog.resolveSourceApp(packageName, combined)
        val channel = ChinaPaymentCatalog.watchedPackageMap[packageName]?.name
            ?: ChinaPaymentCatalog.channels.firstOrNull { ch ->
                combined.contains(ch.name) || ch.smsCodes.any { combined.contains(it) }
            }?.name
            ?: ChinaNotificationParser.parse(packageName, title, content, emptyList(), true)?.channel
            ?: "未知渠道"

        val baseParsed = ChinaNotificationParser.parse(packageName, title, content, emptyList(), true)
        val merchant = baseParsed?.merchant
            ?.takeIf { !it.endsWith("消费") && !it.endsWith("收款") && it != "未知渠道消费" }
            ?: extractEntityFromVocabTokens(combined, matchedVocabHits, channel, type)
        val category = inferCategoryScored(merchant, combined, type)

        val confidence = if ((matchedVocabHits.isNotEmpty() || isCustom) && channel != "未知渠道") 0.90f else 0.84f
        val modelTag = resolveModelDisplayName(context, resolvedId).substringBefore(" ")
        val customInfo = if (isCustom) getCustomModelInfo(context, resolvedId) else null
        val modelDesc = if (customInfo != null) {
            "本地自导权重 [$modelTag · ${customInfo.formatLabel}] 离线理解"
        } else {
            "本地模型 [$modelTag · ${vocabTokens.size} 词表] 离线理解"
        }
        val occurredAt = ChinaNotificationParser.extractOccurredAtMillis(combined)
        Result.success(
            ParsedNotificationResult(
                isFinancialTransaction = true,
                amountCents = amountCents,
                currencyCode = currencyCode,
                type = type,
                category = category,
                merchant = merchant.take(40),
                channel = channel,
                cardLastFour = baseParsed?.cardLastFour,
                sourceApp = sourceApp,
                parseMethod = ParseMethod.LOCAL_NLP,
                confidence = confidence,
                reasoning = modelDesc,
                needsInquiry = false,
                inquiryReason = "",
                occurredAtMillis = occurredAt
            )
        )
    }

    suspend fun parseWithOnlineApi(
        config: AiConfig,
        packageName: String,
        title: String,
        content: String
    ): Result<ParsedNotificationResult> = withContext(Dispatchers.IO) {
        val rawEndpoint = config.apiBaseUrl.trim()
        val apiKey = config.apiKey.trim()
        val modelName = config.apiModelName.trim()
        if (rawEndpoint.isEmpty() || apiKey.isEmpty() || modelName.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("请完整填写在线 API 地址、API Key 与模型名称"))
        }

        val chatUrl = normalizeChatCompletionsUrl(rawEndpoint)
        val systemPrompt = """
            你是一个精准的中国大陆账单与支付通知结构化解析引擎。请判断通知是否为真实动账交易，并仅输出一行严格合法的 JSON 对象（不要包含 Markdown 代码块或额外解释）：
            {"isFinancial":boolean,"amount":number,"currency":"CNY|USD|HKD|EUR|JPY|GBP|SGD|KRW|AUD|CAD|MOP|TWD","type":"EXPENSE|INCOME|TRANSFER","category":"餐饮美食|交通出行|购物消费|居家生活|休闲娱乐|医疗健康|工资奖金|理财收益|退款返现|转账收款|转账红包|其他","merchant":"交易对方或商户名","channel":"支付渠道或银行名称","cardLastFour":"四位尾号或null","occurredTime":"yyyy-MM-dd HH:mm或MM-dd HH:mm或HH:mm或null","confidence":0.0到1.0之间数字}
            注意：验证码、快递、营销广告通知必须返回 isFinancial=false；退款或返现 type 应为 INCOME；若文本包含实际消费/入账时间请填入 occurredTime；不可编造未出现的金额。
        """.trimIndent()

        val userPrompt = buildString {
            appendLine("应用包名: ${packageName.ifBlank { "unknown" }}")
            appendLine("通知标题: $title")
            appendLine("通知正文: $content")
        }

        val payload = JSONObject().apply {
            put("model", modelName)
            put("temperature", 0.1)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userPrompt))
                }
            )
        }.toString()

        try {
            val request = Request.Builder()
                .url(chatUrl)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val responseBody = client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val snippet = text.take(180).replace("\n", " ")
                    return@withContext Result.failure(
                        IllegalStateException("在线 AI 接口返回 HTTP ${response.code}${if (snippet.isNotBlank()) "：$snippet" else ""}")
                    )
                }
                text
            }

            val root = JSONObject(responseBody)
            val choices = root.optJSONArray("choices")
                ?: return@withContext Result.failure(IllegalStateException("在线 API 响应缺少 choices 字段"))
            if (choices.length() == 0) {
                return@withContext Result.failure(IllegalStateException("在线 API 未返回候选结果"))
            }
            val contentText = choices.getJSONObject(0)
                .optJSONObject("message")
                ?.optString("content")
                ?.trim()
                .orEmpty()

            val parsedJson = extractFirstJsonObject(contentText)
                ?: return@withContext Result.failure(IllegalStateException("在线 AI 输出未能解析为 JSON：$contentText"))

            val isFinancial = parsedJson.optBoolean("isFinancial", false)
            val amountYuan = parsedJson.optDouble("amount", 0.0)
            if (!isFinancial || amountYuan <= 0.0 || amountYuan > 10_000_000.0) {
                return@withContext Result.failure(IllegalStateException("AI 判定非有效动账通知或未识别到有效金额"))
            }

            val currency = CurrencyCatalog.resolve(parsedJson.optString("currency", "CNY")).code
            val txType = TransactionType.fromRaw(parsedJson.optString("type", "EXPENSE"))
            val category = parsedJson.optString("category")
                .trim()
                .ifBlank { ChinaNotificationParser.inferCategory("", "$title $content", txType) }
                .take(40)
            val channel = parsedJson.optString("channel")
                .trim()
                .takeIf { it.isNotBlank() && it != "null" }
                ?.take(40)
                ?: ChinaPaymentCatalog.watchedPackageMap[packageName]?.name
                ?: "未知渠道"
            val merchant = parsedJson.optString("merchant")
                .trim()
                .takeIf { it.isNotBlank() && it != "null" }
                ?.take(80)
                ?: "${channel}${txType.label}"
            val cardLastFour = parsedJson.optString("cardLastFour")
                .trim()
                .takeIf { it.length == 4 && it.all(Char::isDigit) }
            val aiTimeText = parsedJson.optString("occurredTime")
                .trim()
                .takeIf { it.isNotBlank() && it != "null" }
            val occurredAtMillis = aiTimeText?.let { ChinaNotificationParser.extractOccurredAtMillis(it) }
                ?: ChinaNotificationParser.extractOccurredAtMillis("$title\n$content")
            val confidence = parsedJson.optDouble("confidence", 0.92).toFloat().coerceIn(0.5f, 1.0f)

            Result.success(
                ParsedNotificationResult(
                    isFinancialTransaction = true,
                    amountCents = (amountYuan * 100.0).roundToLong().coerceAtLeast(1L),
                    currencyCode = currency,
                    type = txType,
                    category = category,
                    merchant = merchant,
                    channel = channel,
                    cardLastFour = cardLastFour,
                    sourceApp = ChinaPaymentCatalog.resolveSourceApp(packageName, "$title $content"),
                    parseMethod = ParseMethod.LOCAL_NLP,
                    confidence = confidence,
                    reasoning = "在线 AI [$modelName] 理解",
                    needsInquiry = false,
                    inquiryReason = "",
                    occurredAtMillis = occurredAtMillis
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException("调用在线 AI 失败：${e.localizedMessage ?: "网络或 JSON 解析异常"}", e))
        }
    }

    private fun normalizeChatCompletionsUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return when {
            trimmed.endsWith("/chat/completions", ignoreCase = true) -> trimmed
            trimmed.endsWith("/v1", ignoreCase = true) -> "$trimmed/chat/completions"
            else -> "$trimmed/chat/completions"
        }
    }

    private fun extractFirstJsonObject(raw: String): JSONObject? {
        val cleaned = raw
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(cleaned.substring(start, end + 1)) }.getOrNull()
    }

    private fun loadVocabTokens(modelDir: File): Set<String> {
        val vocabTxt = File(modelDir, "vocab.txt")
        if (vocabTxt.exists()) {
            val tokens = HashSet<String>(24000)
            vocabTxt.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    val token = line.trim()
                    if (token.isNotEmpty() && !token.startsWith("[")) {
                        tokens.add(token)
                    }
                }
            }
            return tokens
        }
        val tokenizerJson = File(modelDir, "tokenizer.json")
        if (tokenizerJson.exists()) {
            return runCatching {
                val obj = JSONObject(tokenizerJson.readText(Charsets.UTF_8))
                val vocabObj = obj.optJSONObject("model")?.optJSONObject("vocab")
                val tokens = HashSet<String>(32000)
                vocabObj?.keys()?.forEachRemaining { k ->
                    if (k.isNotBlank()) tokens.add(k)
                }
                tokens
            }.getOrDefault(emptySet())
        }
        // Fallback: if custom weight file has .txt / .vocab / .json directly inside modelDir
        val anyVocabTxt = modelDir.listFiles()?.firstOrNull {
            it.isFile && (it.name.endsWith(".txt", true) || it.name.endsWith(".vocab", true))
        }
        if (anyVocabTxt != null) {
            val tokens = HashSet<String>(16000)
            runCatching {
                anyVocabTxt.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        val t = line.trim()
                        if (t.isNotEmpty() && !t.startsWith("[")) tokens.add(t)
                    }
                }
            }
            return tokens
        }
        val anyTokenizerJson = modelDir.listFiles()?.firstOrNull {
            it.isFile && it.name.endsWith(".json", true) && it.name != "custom_meta.json"
        }
        if (anyTokenizerJson != null) {
            return runCatching {
                val obj = JSONObject(anyTokenizerJson.readText(Charsets.UTF_8))
                val vocabObj = obj.optJSONObject("model")?.optJSONObject("vocab")
                val tokens = HashSet<String>(24000)
                vocabObj?.keys()?.forEachRemaining { k ->
                    if (k.isNotBlank()) tokens.add(k)
                }
                tokens
            }.getOrDefault(emptySet())
        }
        return emptySet()
    }

    private fun tokenizeWithVocab(text: String, vocab: Set<String>): List<String> {
        if (vocab.isEmpty()) return emptyList()
        val found = mutableListOf<String>()
        val clean = text.replace(Regex("""[\s，。、：:；;!！?？()（）【】\[\]「」"“”]+"""), " ")
        clean.split(" ").forEach { chunk ->
            var i = 0
            while (i < chunk.length) {
                var matched: String? = null
                val maxLen = minOf(6, chunk.length - i)
                for (len in maxLen downTo 2) {
                    val sub = chunk.substring(i, i + len)
                    if (sub in vocab) {
                        matched = sub
                        break
                    }
                }
                if (matched != null) {
                    found.add(matched)
                    i += matched.length
                } else {
                    i += 1
                }
            }
        }
        return found.distinct()
    }

    private fun inferTypeWithVocab(text: String): TransactionType {
        return ChinaNotificationParser.detectTransactionType(text)
    }

    private fun inferCategoryScored(merchant: String, text: String, type: TransactionType): String {
        val ruleBased = ChinaNotificationParser.inferCategory(merchant, text, type)
        if (ruleBased != "其他") return ruleBased
        if (type == TransactionType.EXPENSE) {
            val lower = "$merchant $text".lowercase()
            return when {
                listOf("餐", "茶", "饭", "食", "饮", "面", "粉", "饼", "面包").any { lower.contains(it) } -> "餐饮美食"
                listOf("车", "油", "票", "行", "航", "铁", "停").any { lower.contains(it) } -> "交通出行"
                listOf("买", "购", "百货", "店", "mall", "shop").any { lower.contains(it) } -> "购物消费"
                listOf("水", "电", "费", "网", "租", "物业").any { lower.contains(it) } -> "居家生活"
                listOf("影", "剧", "乐", "游", "vip", "会员").any { lower.contains(it) } -> "休闲娱乐"
                listOf("医", "药", "诊", "康").any { lower.contains(it) } -> "医疗健康"
                else -> "其他"
            }
        }
        return ruleBased
    }

    private fun extractEntityFromVocabTokens(
        text: String,
        vocabTokens: List<String>,
        channel: String,
        type: TransactionType
    ): String {
        val candidates = ChinaNotificationParser.extractKeywordCandidates("", text, "", channel)
        val stopWords = setOf(
            "消费", "支出", "扣款", "付款", "支付", "收款", "入账", "到账",
            "退款", "转账", "人民币", "美元", "港币", "微信", "支付宝", channel
        )
        val best = candidates.firstOrNull { it !in stopWords && it.length >= 2 }
            ?: vocabTokens.firstOrNull { it !in stopWords && it.length >= 2 && !it.all(Char::isDigit) }
        return best ?: when (type) {
            TransactionType.INCOME -> "${channel}收款"
            TransactionType.TRANSFER -> "${channel}转账"
            TransactionType.EXPENSE -> "${channel}消费"
        }
    }
}
