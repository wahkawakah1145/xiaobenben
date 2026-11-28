package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.JsonReader
import android.util.JsonToken
import com.example.model.AiConfig
import com.example.model.AiProviderMode
import com.example.model.CurrencyCatalog
import com.example.model.CustomLocalModelInfo
import com.example.model.CustomParsingRule
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

    const val DEFAULT_LOCAL_MODEL_ID = "lfm25-350m-q4km"

    val presets: List<LocalModelPreset> = listOf(
        LocalModelPreset(
            id = "ernie-3.0-nano-zh",
            name = "轻巧极速档 · ERNIE-3.0-Nano-Zh",
            repoId = "nghuyong/ernie-3.0-nano-zh",
            license = "Apache-2.0",
            sizeHint = "约 130 KB（超小体积 · 极速按需下载）",
            summary = "超轻量中文分词核，毫秒级离线切词并匹配消费/收入/退款意图，几乎不占存储空间。",
            files = listOf(
                LocalModelFileSpec("config.json", "resolve/main/config.json"),
                LocalModelFileSpec("vocab.txt", "resolve/main/vocab.txt")
            )
        ),
        LocalModelPreset(
            id = "qwen25-0.5b-instruct",
            name = "轻量全词表档 · Qwen2.5-0.5B-Instruct",
            repoId = "Qwen/Qwen2.5-0.5B-Instruct",
            license = "Apache-2.0",
            sizeHint = "约 7.1 MB（BPE 全量词表 + 配置）",
            summary = "通义千问 0.5B BPE 全量词表增强核，覆盖复杂账单长文本、外币符号及多语言混合商户解析。",
            files = listOf(
                LocalModelFileSpec("config.json", "resolve/main/config.json"),
                LocalModelFileSpec("tokenizer_config.json", "resolve/main/tokenizer_config.json"),
                LocalModelFileSpec("tokenizer.json", "resolve/main/tokenizer.json")
            )
        ),
        LocalModelPreset(
            id = "lfm25-350m-q4km",
            name = "端侧大模型 · LFM2.5-350M (Q4_K_M)",
            repoId = "LiquidAI/LFM2.5-350M-GGUF",
            license = "LFM Open License v1.0",
            sizeHint = "约 229 MB（350M 端侧大模型 · Q4_K_M 量化 GGUF）",
            summary = "Liquid AI 新一代 350M 混合架构端侧大模型（Q4_K_M 量化权重，32K 上下文），体积低于 350 MB，强项在于结构化账单信息抽取、复杂多币种银行通知与离线语义归类。",
            files = listOf(
                LocalModelFileSpec("LFM2.5-350M-Q4_K_M.gguf", "resolve/main/LFM2.5-350M-Q4_K_M.gguf")
            )
        )
    )

    fun resolve(modelId: String?): LocalModelPreset =
        presets.firstOrNull { it.id == modelId } ?: resolveDefaultPreset()

    private fun resolveDefaultPreset(): LocalModelPreset =
        presets.firstOrNull { it.id == DEFAULT_LOCAL_MODEL_ID } ?: presets.first()

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
    val vocabCountEstimate: Int,
    val isGgufModel: Boolean = false,
    val ggufVersion: Int = 0,
    val ggufTensorCount: Long = 0L,
    val validationError: String = ""
)

data class AiChatReply(
    val text: String,
    val engineLabel: String,
    val tokensGenerated: Int = 0,
    val tokensPerSecond: Float = 0f,
    val latencyMs: Long = 0L
)

class AiUnderstandingManager {

    private val client: OkHttpClient
        get() = sharedClient

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

            if (ext == "gguf") {
                val ggufHeader = LocalGgufRuntimeManager.inspectGgufFile(targetFile)
                if (!ggufHeader.valid) {
                    dir.deleteRecursively()
                    return@withContext Result.failure(
                        IllegalArgumentException("导入的 GGUF 权重校验失败：${ggufHeader.errorMessage}")
                    )
                }
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
        if (ext == "gguf") {
            val header = LocalGgufRuntimeManager.inspectGgufFile(file)
            return if (header.valid) "GGUF 量化权重" else "GGUF 模型文件（校验异常）"
        }
        val header = ByteArray(16)
        val readCount = runCatching {
            file.inputStream().use { it.read(header) }
        }.getOrDefault(0)
        val magic = if (readCount >= 4) String(header, 0, 4, Charsets.US_ASCII) else ""
        return when {
            magic == "GGUF" -> "GGUF 量化权重"
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
            return runCatching {
                file.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.count() }
            }.getOrDefault(0)
        }
        if (ext == "json") {
            return countTokenizerJsonVocabStreaming(file)
        }
        return 0
    }

    fun getInstallStatus(context: Context, modelId: String): LocalModelInstallStatus {
        if (LocalModelCatalog.isCustomModelId(modelId)) {
            val info = getCustomModelInfo(context, modelId)
            return if (info != null) {
                val dir = modelDirectory(context, info.id)
                val ggufFile = LocalGgufRuntimeManager.findPrimaryGgufFile(dir)
                if (ggufFile != null) {
                    val header = LocalGgufRuntimeManager.inspectGgufFile(ggufFile)
                    LocalModelInstallStatus(
                        modelId = info.id,
                        installed = header.valid,
                        totalBytes = info.sizeBytes,
                        vocabCountEstimate = info.vocabTokenCount.coerceAtLeast(32_000),
                        isGgufModel = true,
                        ggufVersion = header.version,
                        ggufTensorCount = header.tensorCount,
                        validationError = if (header.valid) "" else header.errorMessage
                    )
                } else {
                    LocalModelInstallStatus(
                        modelId = info.id,
                        installed = true,
                        totalBytes = info.sizeBytes,
                        vocabCountEstimate = info.vocabTokenCount.coerceAtLeast(32_000),
                        isGgufModel = false
                    )
                }
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
        val filesPresent = expectedFiles.all { it.exists() && it.length() > 16L }
        val bytes = expectedFiles.sumOf { if (it.exists()) it.length() else 0L }
        val ggufFile = expectedFiles.firstOrNull { it.name.endsWith(".gguf", ignoreCase = true) }
        val isGguf = ggufFile != null
        val ggufHeader = if (ggufFile != null && ggufFile.exists()) {
            LocalGgufRuntimeManager.inspectGgufFile(ggufFile)
        } else {
            null
        }
        val installed = filesPresent && (!isGguf || (ggufHeader?.valid == true))
        val vocabFile = File(dir, "vocab.txt")
        val vocabCount = when {
            vocabFile.exists() -> vocabFile.useLines { it.count() }
            isGguf && installed -> 65_536
            installed -> 150_000
            else -> 0
        }
        return LocalModelInstallStatus(
            modelId = preset.id,
            installed = installed,
            totalBytes = bytes,
            vocabCountEstimate = vocabCount,
            isGgufModel = isGguf,
            ggufVersion = ggufHeader?.version ?: 0,
            ggufTensorCount = ggufHeader?.tensorCount ?: 0L,
            validationError = if (ggufFile != null && ggufFile.exists() && ggufHeader != null && !ggufHeader.valid) {
                ggufHeader.errorMessage
            } else {
                ""
            }
        )
    }

    fun deleteLocalModel(context: Context, modelId: String): Boolean {
        val targetId = if (LocalModelCatalog.isCustomModelId(modelId)) {
            modelId
        } else {
            LocalModelCatalog.resolve(modelId).id
        }
        val dir = modelDirectory(context, targetId)
        val loadedPath = LocalGgufRuntimeManager.status.value.loadedModelPath
        if (loadedPath != null && loadedPath.startsWith(dir.absolutePath)) {
            LocalGgufRuntimeManager.releaseModelIfIdle(force = true)
        }
        return if (dir.exists()) dir.deleteRecursively() else true
    }

    private fun formatTransferBytes(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024L -> "${bytes / 1024L} KB"
            else -> "$bytes B"
        }
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
        val downloadClient = client.newBuilder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
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

                downloadClient.newCall(request).execute().use { response ->
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
                            val buffer = ByteArray(64 * 1024)
                            var fileBytesRead = 0L
                            var lastEmitMs = 0L
                            var n: Int
                            while (input.read(buffer).also { n = it } != -1) {
                                output.write(buffer, 0, n)
                                fileBytesRead += n
                                val nowMs = System.currentTimeMillis()
                                if (nowMs - lastEmitMs >= 180L) {
                                    lastEmitMs = nowMs
                                    val fileFrac = if (contentLength > 0L) {
                                        (fileBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        0.5f
                                    }
                                    val overall = (idx + fileFrac) / totalFiles.toFloat()
                                    val sizeProgress = if (contentLength > 0L) {
                                        "${formatTransferBytes(fileBytesRead)} / ${formatTransferBytes(contentLength)}"
                                    } else {
                                        formatTransferBytes(fileBytesRead)
                                    }
                                    onProgress(
                                        overall.coerceIn(0f, 0.99f),
                                        "正在下载 ${spec.fileName} ($sizeProgress)"
                                    )
                                }
                            }
                            output.flush()
                        }
                    }

                    if (!tmpFile.exists() || tmpFile.length() < 16L) {
                        tmpFile.delete()
                        return@withContext Result.failure(IllegalStateException("下载文件 ${spec.fileName} 校验失败"))
                    }
                    if (spec.fileName.endsWith(".gguf", ignoreCase = true)) {
                        val ggufHeader = LocalGgufRuntimeManager.inspectGgufFile(tmpFile)
                        if (!ggufHeader.valid) {
                            tmpFile.delete()
                            return@withContext Result.failure(
                                IllegalStateException("下载的 GGUF 权重校验失败：${ggufHeader.errorMessage}")
                            )
                        }
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
        fallbackParsed: ParsedNotificationResult?,
        availableRules: List<CustomParsingRule> = emptyList(),
        activeCategories: List<String> = com.example.model.CategoryCatalog.allCategories,
        activeChannels: List<String> = com.example.model.ChannelCatalog.defaultChannels,
        billFieldsConfig: List<com.example.model.BillFieldConfig> = com.example.model.BillFieldCatalog.defaultFields,
        categoryParentMap: Map<String, String> = emptyMap(),
        channelParentMap: Map<String, String> = com.example.model.ChannelCatalog.defaultChannelParentMap,
        collapseChannelToParent: Boolean = true,
        bypassAmountUnitGate: Boolean = false,
        lowMemoryBackground: Boolean = false
    ): Result<ParsedNotificationResult> {
        val selectedRules = resolveSelectedRules(config, availableRules)
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
                    content = content,
                    selectedRules = selectedRules,
                    activeCategories = activeCategories,
                    activeChannels = activeChannels,
                    billFieldsConfig = billFieldsConfig,
                    categoryParentMap = categoryParentMap,
                    channelParentMap = channelParentMap,
                    collapseChannelToParent = collapseChannelToParent,
                    bypassAmountUnitGate = bypassAmountUnitGate
                )
            }
            AiProviderMode.LOCAL_SMALL_MODEL -> {
                parseWithLocalModel(
                    context = context,
                    modelId = config.localModelId,
                    packageName = packageName,
                    title = title,
                    content = content,
                    selectedRules = selectedRules,
                    customPrompt = config.customSystemPrompt,
                    activeChannels = activeChannels,
                    channelParentMap = channelParentMap,
                    collapseChannelToParent = collapseChannelToParent,
                    bypassAmountUnitGate = bypassAmountUnitGate,
                    aiConfig = config,
                    activeCategories = activeCategories,
                    billFieldsConfig = billFieldsConfig,
                    categoryParentMap = categoryParentMap,
                    lowMemoryBackground = lowMemoryBackground
                )
            }
        }
    }

    suspend fun preloadLocalGgufModel(
        context: Context,
        config: AiConfig
    ): Result<String> {
        val isCustom = LocalModelCatalog.isCustomModelId(config.localModelId)
        val resolvedId = if (isCustom) config.localModelId else LocalModelCatalog.resolve(config.localModelId).id
        val dir = modelDirectory(context, resolvedId)
        val ggufFile = LocalGgufRuntimeManager.findPrimaryGgufFile(dir)
            ?: return Result.failure(
                IllegalStateException("当前所选本地模型不是 .gguf 权重，无需预加载原生大模型引擎")
            )
        val header = LocalGgufRuntimeManager.inspectGgufFile(ggufFile)
        if (!header.valid) {
            return Result.failure(IllegalArgumentException(header.errorMessage))
        }
        val displayName = resolveModelDisplayName(context, resolvedId)
        val startedAt = System.currentTimeMillis()
        val loadResult = LocalGgufRuntimeManager.ensureLoaded(
            modelFile = ggufFile,
            displayName = displayName,
            config = config,
            lowMemoryMode = false
        )
        return loadResult.map {
            val elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
            "模型「$displayName」已加载到端侧引擎（GGUF v${header.version} · ${header.tensorCount} 张量 · 上下文 ${config.contextSize} · 耗时 ${elapsedMs}ms）"
        }
    }

    suspend fun chatTest(
        context: Context,
        config: AiConfig,
        history: List<com.example.model.AiChatMessage>,
        userInput: String,
        useLedgerSystemPrompt: Boolean = false,
        availableRules: List<CustomParsingRule> = emptyList(),
        activeCategories: List<String> = com.example.model.CategoryCatalog.allCategories,
        activeChannels: List<String> = com.example.model.ChannelCatalog.defaultChannels,
        billFieldsConfig: List<com.example.model.BillFieldConfig> = com.example.model.BillFieldCatalog.defaultFields,
        categoryParentMap: Map<String, String> = emptyMap(),
        channelParentMap: Map<String, String> = com.example.model.ChannelCatalog.defaultChannelParentMap
    ): Result<AiChatReply> = withContext(Dispatchers.IO) {
        val cleanInput = userInput.trim()
        if (cleanInput.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("请输入对话内容"))
        }
        val selectedRules = resolveSelectedRules(config, availableRules)
        val systemPrompt = if (useLedgerSystemPrompt) {
            buildEffectiveSystemPrompt(
                config = config,
                selectedRules = selectedRules,
                activeCategories = activeCategories,
                activeChannels = activeChannels,
                billFieldsConfig = billFieldsConfig,
                categoryParentMap = categoryParentMap,
                channelParentMap = channelParentMap
            )
        } else {
            "你是一个简洁、精准、友好的中文 AI 助手，请直接清晰地回答用户问题。"
        }

        when (config.providerMode) {
            AiProviderMode.DISABLED -> {
                Result.failure(IllegalStateException("AI 理解当前已关闭，请先启用「本地开源小模型」或「在线 API 接口」"))
            }
            AiProviderMode.LOCAL_SMALL_MODEL -> {
                val isCustom = LocalModelCatalog.isCustomModelId(config.localModelId)
                val resolvedId = if (isCustom) config.localModelId else LocalModelCatalog.resolve(config.localModelId).id
                val status = getInstallStatus(context, resolvedId)
                val displayName = resolveModelDisplayName(context, resolvedId)
                if (!status.installed) {
                    val hint = status.validationError.ifBlank { "尚未下载或导入模型文件" }
                    return@withContext Result.failure(
                        IllegalStateException("本地模型「$displayName」未就绪：$hint")
                    )
                }
                val dir = modelDirectory(context, resolvedId)
                val ggufFile = LocalGgufRuntimeManager.findPrimaryGgufFile(dir)
                    ?: return@withContext Result.failure(
                        IllegalStateException("「$displayName」为轻量分词词表包，仅支持通知语义解析；请切换至 LFM2.5-350M (GGUF) 或导入 .gguf 权重以进行端侧对话测试。")
                    )
                val recentTurns = history.filter { !it.isError }.takeLast(6)
                val stitchedPrompt = if (recentTurns.isEmpty()) {
                    cleanInput
                } else {
                    buildString {
                        appendLine("以下是之前的对话上下文：")
                        recentTurns.forEach { msg ->
                            val roleLabel = if (msg.isUser) "用户" else "助手"
                            appendLine("$roleLabel：${msg.content.take(400)}")
                        }
                        append("用户：$cleanInput")
                    }
                }
                val completion = LocalGgufRuntimeManager.complete(
                    modelFile = ggufFile,
                    displayName = displayName,
                    config = config,
                    systemPrompt = systemPrompt,
                    userPrompt = stitchedPrompt,
                    lowMemoryMode = false,
                    releaseImmediatelyAfter = false
                ).getOrElse { err ->
                    return@withContext Result.failure(err)
                }
                Result.success(
                    AiChatReply(
                        text = completion.text.ifBlank { "（模型未输出文本内容，可适当调大最大输出 Token 或调整温度参数）" },
                        engineLabel = "端侧 GGUF · ${displayName.substringBefore(" ")}",
                        tokensGenerated = completion.tokensGenerated,
                        tokensPerSecond = completion.tokensPerSecond,
                        latencyMs = (completion.promptEvalTimeMs + completion.generateTimeMs).coerceAtLeast(1L)
                    )
                )
            }
            AiProviderMode.ONLINE_API -> {
                val rawEndpoint = config.apiBaseUrl.trim()
                val apiKey = config.apiKey.trim()
                val modelName = config.apiModelName.trim()
                if (rawEndpoint.isEmpty() || apiKey.isEmpty() || modelName.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("请先在 AI 理解页面填写完整的在线 API 地址、API Key 与模型名称"))
                }
                val chatUrl = normalizeChatCompletionsUrl(rawEndpoint)
                val messagesJson = JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    history.filter { !it.isError }.takeLast(10).forEach { msg ->
                        put(
                            JSONObject()
                                .put("role", if (msg.isUser) "user" else "assistant")
                                .put("content", msg.content)
                        )
                    }
                    put(JSONObject().put("role", "user").put("content", cleanInput))
                }
                val payload = JSONObject().apply {
                    put("model", modelName)
                    put("temperature", config.temperature.coerceIn(0.0f, 1.5f).toDouble())
                    put("top_p", config.topP.coerceIn(0.1f, 1.0f).toDouble())
                    put("max_tokens", config.maxTokens.coerceIn(64, 2048))
                    put("messages", messagesJson)
                }.toString()
                val startedAt = System.currentTimeMillis()
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
                    val latencyMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                    val root = JSONObject(responseBody)
                    val choices = root.optJSONArray("choices")
                        ?: return@withContext Result.failure(IllegalStateException("在线 API 响应缺少 choices 字段"))
                    if (choices.length() == 0) {
                        return@withContext Result.failure(IllegalStateException("在线 API 未返回回复内容"))
                    }
                    val replyText = choices.getJSONObject(0)
                        .optJSONObject("message")
                        ?.optString("content")
                        ?.trim()
                        .orEmpty()
                    val completionTokens = root.optJSONObject("usage")?.optInt("completion_tokens", 0) ?: 0
                    val tps = if (completionTokens > 0 && latencyMs > 0L) {
                        completionTokens * 1000f / latencyMs.toFloat()
                    } else {
                        0f
                    }
                    Result.success(
                        AiChatReply(
                            text = replyText.ifBlank { "（在线模型返回空文本）" },
                            engineLabel = "在线 API · $modelName",
                            tokensGenerated = completionTokens,
                            tokensPerSecond = tps,
                            latencyMs = latencyMs
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(IllegalStateException("调用在线对话失败：${e.localizedMessage ?: "网络异常"}", e))
                }
            }
        }
    }

    suspend fun parseWithLocalModel(
        context: Context,
        modelId: String,
        packageName: String,
        title: String,
        content: String,
        selectedRules: List<CustomParsingRule> = emptyList(),
        customPrompt: String = "",
        activeChannels: List<String> = com.example.model.ChannelCatalog.defaultChannels,
        channelParentMap: Map<String, String> = com.example.model.ChannelCatalog.defaultChannelParentMap,
        collapseChannelToParent: Boolean = true,
        bypassAmountUnitGate: Boolean = false,
        aiConfig: AiConfig = AiConfig(),
        activeCategories: List<String> = com.example.model.CategoryCatalog.allCategories,
        billFieldsConfig: List<com.example.model.BillFieldConfig> = com.example.model.BillFieldCatalog.defaultFields,
        categoryParentMap: Map<String, String> = emptyMap(),
        lowMemoryBackground: Boolean = false
    ): Result<ParsedNotificationResult> = withContext(Dispatchers.Default) {
        val combined = "$title\n$content".trim()
        if (combined.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("通知内容为空"))
        }
        if (!bypassAmountUnitGate && !ChinaNotificationParser.hasExplicitAmountUnit(combined)) {
            return@withContext Result.failure(
                IllegalStateException("通知中未检测到前后带金额单位的数字，已跳过 AI 理解")
            )
        }

        val isCustom = LocalModelCatalog.isCustomModelId(modelId)
        val resolvedId = if (isCustom) modelId else LocalModelCatalog.resolve(modelId).id
        val status = getInstallStatus(context, resolvedId)
        if (!status.installed) {
            val missingName = if (isCustom) "导入的模型权重" else LocalModelCatalog.resolve(resolvedId).name
            val detail = status.validationError.takeIf { it.isNotBlank() }?.let { "（$it）" } ?: ""
            return@withContext Result.failure(
                IllegalStateException("本地模型「$missingName」尚未就绪$detail，请先在「AI理解」中下载或导入")
            )
        }

        val dir = modelDirectory(context, resolvedId)
        val modelDisplayName = resolveModelDisplayName(context, resolvedId)
        val modelTag = modelDisplayName.substringBefore(" ")
        val matchedPromptRule = matchPromptReferenceRule(selectedRules, packageName, title, content)
        if (matchedPromptRule != null && matchedPromptRule.isIgnoreRule) {
            return@withContext Result.failure(
                IllegalStateException("参考规则「${matchedPromptRule.name}」将其判定为非账单忽略通知")
            )
        }
        val ruleSuffix = buildString {
            if (matchedPromptRule != null) append(" · 参考规则「${matchedPromptRule.name}」")
            else if (selectedRules.isNotEmpty()) append(" · 附${selectedRules.size}条规则参考")
            if (customPrompt.isNotBlank()) append(" · 自定义提示词")
        }

        // 1. Real on-device llama.cpp inference path whenever a .gguf model weight is installed
        val ggufFile = LocalGgufRuntimeManager.findPrimaryGgufFile(dir)
        var ggufFallbackNote = ""
        if (ggufFile != null) {
            val effectiveConfig = aiConfig.copy(
                localModelId = resolvedId,
                customSystemPrompt = customPrompt.ifBlank { aiConfig.customSystemPrompt }
            )
            val systemPrompt = buildEffectiveSystemPrompt(
                config = effectiveConfig,
                selectedRules = selectedRules,
                activeCategories = activeCategories,
                activeChannels = activeChannels,
                billFieldsConfig = billFieldsConfig,
                categoryParentMap = categoryParentMap,
                channelParentMap = channelParentMap
            )
            val userPrompt = buildString {
                appendLine("应用包名: ${packageName.ifBlank { "unknown" }}")
                appendLine("通知标题: $title")
                appendLine("通知正文: $content")
                if (bypassAmountUnitGate) {
                    appendLine("补充说明: 用户已在通知日志中手动强制调用 AI 识别，请尽力结合语义识别真实动账金额、币种、交易对方与渠道。")
                }
            }
            val ggufResult = LocalGgufRuntimeManager.complete(
                modelFile = ggufFile,
                displayName = modelDisplayName,
                config = effectiveConfig,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                lowMemoryMode = lowMemoryBackground,
                releaseImmediatelyAfter = lowMemoryBackground && effectiveConfig.localAutoUnloadSeconds == 0
            )
            if (ggufResult.isSuccess) {
                val completion = ggufResult.getOrThrow()
                val parsedJson = extractFirstJsonObject(completion.text)
                if (parsedJson != null) {
                    val isFinancial = parsedJson.optBoolean("isFinancial", false)
                    val amountYuan = parsedJson.optDouble("amount", 0.0)
                    if (isFinancial && amountYuan > 0.0 && amountYuan <= 10_000_000.0) {
                        val speedLabel = String.format(Locale.US, "%.1f tok/s", completion.tokensPerSecond)
                        return@withContext Result.success(
                            buildParsedResultFromAiJson(
                                parsedJson = parsedJson,
                                amountYuan = amountYuan,
                                packageName = packageName,
                                title = title,
                                content = content,
                                selectedRules = selectedRules,
                                activeChannels = activeChannels,
                                channelParentMap = channelParentMap,
                                collapseChannelToParent = collapseChannelToParent,
                                reasoning = "端侧 GGUF [$modelTag · $speedLabel]$ruleSuffix"
                            )
                        )
                    } else if (!isFinancial && !bypassAmountUnitGate) {
                        return@withContext Result.failure(
                            IllegalStateException("端侧 GGUF [$modelTag] 判定该通知非有效动账")
                        )
                    }
                }
                // Model loaded & ran cleanly, but output wasn't strict JSON — fall through to semantic extraction
                ggufFallbackNote = "端侧 GGUF [$modelTag · 语义校正]"
            } else {
                val err = ggufResult.exceptionOrNull()
                if (err is NativeGgufUnavailableException) {
                    // Non-arm64 architecture (e.g. x86_64 emulator / JVM test): gracefully fall back to semantic extractor
                    ggufFallbackNote = "端侧语义 [$modelTag]"
                } else {
                    // Real GGUF load/inference failure: surface directly so the user knows why .gguf failed
                    return@withContext Result.failure(
                        err ?: IllegalStateException("端侧 GGUF 模型加载或推理失败")
                    )
                }
            }
        }

        // 2. Local vocab / semantic extraction path (for vocab-only small packs or GGUF JSON-correction fallback)
        val primaryMention = ChinaNotificationParser.extractPrimaryAmountAndCurrency(
            text = combined,
            allowLooseStandaloneNumber = bypassAmountUnitGate,
            requireExplicitUnit = !bypassAmountUnitGate
        ) ?: return@withContext Result.failure(
            IllegalStateException(
                if (bypassAmountUnitGate) {
                    "端侧本地模型未能在该通知文本中提取到有效金额数字"
                } else {
                    "本地模型未能在文本中检测到带金额单位的有效金额"
                }
            )
        )
        val amountCents = primaryMention.amountCents

        val (matchedVocabHits, totalVocabCount) = scanVocabMatchesStreaming(dir, combined)
        val currencyCode = if (primaryMention.hasSpecificCurrency) {
            primaryMention.currencyCode
        } else {
            ChinaNotificationParser.detectCurrencyCode(combined)
        }
        val type = when {
            matchedPromptRule != null && !matchedPromptRule.defaultType.equals("AUTO", ignoreCase = true) ->
                TransactionType.fromRaw(matchedPromptRule.defaultType)
            else -> inferTypeWithVocab(combined)
        }
        val sourceApp = ChinaPaymentCatalog.resolveSourceApp(packageName, combined)
        val rawChannel = matchedPromptRule?.channelName?.trim()?.takeIf { ChinaNotificationParser.isConcreteChannelName(it) }
            ?: ChinaNotificationParser.resolveChannelFromRules(packageName, combined, selectedRules, content.trim())
            ?: ChinaNotificationParser.detectChannel(packageName, combined, activeChannels)
                .takeIf { ChinaNotificationParser.isConcreteChannelName(it) }
            ?: ChinaPaymentCatalog.watchedPackageMap[packageName]?.name
            ?: ChinaPaymentCatalog.channels.firstOrNull { ch ->
                combined.contains(ch.name) || ch.smsCodes.any { combined.contains(it) }
            }?.name
            ?: "未知渠道"
        val channel = com.example.model.ChannelCatalog.normalizeRecognizedChannel(
            rawChannel = rawChannel,
            parentMap = channelParentMap,
            collapseToParent = collapseChannelToParent
        )

        val baseParsed = ChinaNotificationParser.parse(
            packageName = packageName,
            title = title,
            content = content,
            customRules = selectedRules,
            enableLocalSemanticPass = true,
            customChannels = activeChannels,
            channelParentMap = channelParentMap,
            collapseChannelToParent = collapseChannelToParent
        )
        val merchant = matchedPromptRule?.merchantOverride?.takeIf { it.isNotBlank() }
            ?: baseParsed?.merchant
                ?.takeIf { !it.endsWith("消费") && !it.endsWith("收款") && it != "未知渠道消费" }
            ?: extractEntityFromVocabTokens(combined, matchedVocabHits, channel, type)
        val category = matchedPromptRule?.defaultCategory?.takeIf { it.isNotBlank() && it != "自动" }
            ?: inferCategoryScored(merchant, combined, type)

        val confidence = if ((matchedVocabHits.isNotEmpty() || matchedPromptRule != null || isCustom) && channel != "未知渠道") {
            0.91f
        } else {
            0.84f
        }
        val customInfo = if (isCustom) getCustomModelInfo(context, resolvedId) else null
        val modelDesc = when {
            ggufFallbackNote.isNotBlank() -> "$ggufFallbackNote$ruleSuffix"
            customInfo != null -> "本地自导权重 [$modelTag · ${customInfo.formatLabel}]$ruleSuffix"
            else -> "本地模型 [$modelTag · $totalVocabCount 词表]$ruleSuffix"
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

    private fun buildParsedResultFromAiJson(
        parsedJson: JSONObject,
        amountYuan: Double,
        packageName: String,
        title: String,
        content: String,
        selectedRules: List<CustomParsingRule>,
        activeChannels: List<String>,
        channelParentMap: Map<String, String>,
        collapseChannelToParent: Boolean,
        reasoning: String
    ): ParsedNotificationResult {
        val currency = CurrencyCatalog.resolve(parsedJson.optString("currency", "CNY")).code
        val rawAiType = TransactionType.fromRaw(parsedJson.optString("type", "EXPENSE"))
        val txType = if (ChinaNotificationParser.isSelfTransferOrWithdraw("$title $content")) {
            TransactionType.TRANSFER
        } else {
            rawAiType
        }
        val category = parsedJson.optString("category")
            .trim()
            .ifBlank { ChinaNotificationParser.inferCategory("", "$title $content", txType) }
            .take(40)
        val rawAiChannel = parsedJson.optString("channel")
            .trim()
            .takeIf { it.isNotBlank() && it != "null" && ChinaNotificationParser.isConcreteChannelName(it) }
            ?.take(40)
            ?: ChinaNotificationParser.resolveChannelFromRules(packageName, "$title\n$content", selectedRules, content.trim())
            ?: ChinaNotificationParser.detectChannel(packageName, "$title $content", activeChannels)
                .takeIf { ChinaNotificationParser.isConcreteChannelName(it) }
            ?: ChinaPaymentCatalog.watchedPackageMap[packageName]?.name
            ?: "未知渠道"
        val channel = com.example.model.ChannelCatalog.normalizeRecognizedChannel(
            rawChannel = rawAiChannel,
            parentMap = channelParentMap,
            collapseToParent = collapseChannelToParent
        )
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
        val customFields = buildMap<String, String> {
            val obj = parsedJson.optJSONObject("customFields")
            if (obj != null) {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next().trim().take(40)
                    val v = obj.optString(k).trim().take(160)
                    if (k.isNotEmpty() && v.isNotEmpty() && v != "null") {
                        put(k, v)
                    }
                }
            }
        }
        return ParsedNotificationResult(
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
            reasoning = reasoning,
            needsInquiry = false,
            inquiryReason = "",
            occurredAtMillis = occurredAtMillis,
            customFields = customFields
        )
    }

    suspend fun parseWithOnlineApi(
        config: AiConfig,
        packageName: String,
        title: String,
        content: String,
        selectedRules: List<CustomParsingRule> = emptyList(),
        activeCategories: List<String> = com.example.model.CategoryCatalog.allCategories,
        activeChannels: List<String> = com.example.model.ChannelCatalog.defaultChannels,
        billFieldsConfig: List<com.example.model.BillFieldConfig> = com.example.model.BillFieldCatalog.defaultFields,
        categoryParentMap: Map<String, String> = emptyMap(),
        channelParentMap: Map<String, String> = com.example.model.ChannelCatalog.defaultChannelParentMap,
        collapseChannelToParent: Boolean = true,
        bypassAmountUnitGate: Boolean = false
    ): Result<ParsedNotificationResult> = withContext(Dispatchers.IO) {
        val rawEndpoint = config.apiBaseUrl.trim()
        val apiKey = config.apiKey.trim()
        val modelName = config.apiModelName.trim()
        if (rawEndpoint.isEmpty() || apiKey.isEmpty() || modelName.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("请完整填写在线 API 地址、API Key 与模型名称"))
        }
        val combined = "$title\n$content".trim()
        if (!bypassAmountUnitGate && !ChinaNotificationParser.hasExplicitAmountUnit(combined)) {
            return@withContext Result.failure(
                IllegalStateException("通知中未检测到前后带金额单位的数字，已跳过在线 AI 理解")
            )
        }

        val chatUrl = normalizeChatCompletionsUrl(rawEndpoint)
        val systemPrompt = buildEffectiveSystemPrompt(
            config = config,
            selectedRules = selectedRules,
            activeCategories = activeCategories,
            activeChannels = activeChannels,
            billFieldsConfig = billFieldsConfig,
            categoryParentMap = categoryParentMap,
            channelParentMap = channelParentMap
        )

        val userPrompt = buildString {
            appendLine("应用包名: ${packageName.ifBlank { "unknown" }}")
            appendLine("通知标题: $title")
            appendLine("通知正文: $content")
            if (bypassAmountUnitGate) {
                appendLine("补充说明: 用户已在通知日志中手动强制调用 AI 识别，请尽力结合语义识别真实动账金额、币种、交易对方与渠道（即使金额附近未出现常见货币符号或单位）。")
            }
        }

        val payload = JSONObject().apply {
            put("model", modelName)
            put("temperature", config.temperature.coerceIn(0.0f, 1.5f).toDouble())
            put("top_p", config.topP.coerceIn(0.1f, 1.0f).toDouble())
            put("max_tokens", config.maxTokens.coerceIn(64, 2048))
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
            val rawAiType = TransactionType.fromRaw(parsedJson.optString("type", "EXPENSE"))
            val txType = if (ChinaNotificationParser.isSelfTransferOrWithdraw("$title $content")) {
                TransactionType.TRANSFER
            } else {
                rawAiType
            }
            val category = parsedJson.optString("category")
                .trim()
                .ifBlank { ChinaNotificationParser.inferCategory("", "$title $content", txType) }
                .take(40)
            val rawAiChannel = parsedJson.optString("channel")
                .trim()
                .takeIf { it.isNotBlank() && it != "null" && ChinaNotificationParser.isConcreteChannelName(it) }
                ?.take(40)
                ?: ChinaNotificationParser.resolveChannelFromRules(packageName, "$title\n$content", selectedRules, content.trim())
                ?: ChinaNotificationParser.detectChannel(packageName, "$title $content", activeChannels)
                    .takeIf { ChinaNotificationParser.isConcreteChannelName(it) }
                ?: ChinaPaymentCatalog.watchedPackageMap[packageName]?.name
                ?: "未知渠道"
            val channel = com.example.model.ChannelCatalog.normalizeRecognizedChannel(
                rawChannel = rawAiChannel,
                parentMap = channelParentMap,
                collapseToParent = collapseChannelToParent
            )
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
            val customFields = buildMap<String, String> {
                val obj = parsedJson.optJSONObject("customFields")
                if (obj != null) {
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next().trim().take(40)
                        val v = obj.optString(k).trim().take(160)
                        if (k.isNotEmpty() && v.isNotEmpty() && v != "null") {
                            put(k, v)
                        }
                    }
                }
            }

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
                    occurredAtMillis = occurredAtMillis,
                    customFields = customFields
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

    private fun scanVocabMatchesStreaming(modelDir: File, text: String): Pair<List<String>, Int> {
        val candidates = extractCandidateTokens(text)
        if (candidates.isEmpty()) return emptyList<String>() to 0

        val matchedSet = HashSet<String>(candidates.size)
        var totalCount = 0

        val vocabTxt = File(modelDir, "vocab.txt")
        val tokenizerJson = File(modelDir, "tokenizer.json")
        val hasGgufWeight = modelDir.listFiles()?.any { it.isFile && it.name.endsWith(".gguf", true) } == true
        if (hasGgufWeight && !vocabTxt.exists() && !tokenizerJson.exists()) {
            return emptyList<String>() to 65_536
        }
        when {
            vocabTxt.exists() -> {
                totalCount = scanVocabLines(vocabTxt, candidates, matchedSet)
            }
            tokenizerJson.exists() -> {
                totalCount = scanTokenizerJsonVocab(tokenizerJson, candidates, matchedSet)
            }
            else -> {
                val anyVocabTxt = modelDir.listFiles()?.firstOrNull {
                    it.isFile && (it.name.endsWith(".txt", true) || it.name.endsWith(".vocab", true))
                }
                val anyTokenizerJson = modelDir.listFiles()?.firstOrNull {
                    it.isFile && it.name.endsWith(".json", true) && it.name != "custom_meta.json"
                }
                when {
                    anyVocabTxt != null -> totalCount = scanVocabLines(anyVocabTxt, candidates, matchedSet)
                    anyTokenizerJson != null -> totalCount = scanTokenizerJsonVocab(anyTokenizerJson, candidates, matchedSet)
                }
            }
        }
        return greedyOrderMatchedTokens(text, matchedSet) to totalCount
    }

    private fun scanVocabLines(
        file: File,
        candidates: Set<String>,
        matchedOut: MutableSet<String>
    ): Int {
        var count = 0
        runCatching {
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { raw ->
                    val token = raw.trim()
                    if (token.isNotEmpty() && !token.startsWith("[")) {
                        count++
                        if (token in candidates) matchedOut.add(token)
                    }
                }
            }
        }
        return count
    }

    private fun countTokenizerJsonVocabStreaming(file: File): Int {
        return scanTokenizerJsonVocab(file, emptySet(), HashSet(0))
    }

    private fun scanTokenizerJsonVocab(
        file: File,
        candidates: Set<String>,
        matchedOut: MutableSet<String>
    ): Int {
        var count = 0
        runCatching {
            JsonReader(file.bufferedReader(Charsets.UTF_8)).use { reader ->
                reader.isLenient = true
                if (reader.peek() != JsonToken.BEGIN_OBJECT) return@runCatching
                reader.beginObject()
                while (reader.hasNext()) {
                    val topKey = reader.nextName()
                    if (topKey == "model" && reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val modelKey = reader.nextName()
                            if (modelKey == "vocab" && reader.peek() == JsonToken.BEGIN_OBJECT) {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    val token = reader.nextName()
                                    if (reader.peek() != JsonToken.NULL) {
                                        reader.skipValue()
                                    } else {
                                        reader.nextNull()
                                    }
                                    if (token.isNotBlank()) {
                                        count++
                                        if (token in candidates) matchedOut.add(token)
                                    }
                                }
                                reader.endObject()
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endObject()
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }
        return count
    }

    private fun matchPromptReferenceRule(
        selectedRules: List<CustomParsingRule>,
        packageName: String,
        title: String,
        content: String
    ): CustomParsingRule? {
        if (selectedRules.isEmpty()) return null
        val combined = "$title\n$content"
        return selectedRules
            .sortedByDescending { it.priority }
            .firstOrNull { rule ->
                val pkgOk = rule.packageFilter == "*" ||
                    rule.packageFilter.isBlank() ||
                    rule.packageFilter.equals(packageName, ignoreCase = true)
                val kwOk = rule.keywordPattern.isBlank() ||
                    rule.keywordPattern.split("|", ",", "，")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .any { kw -> combined.contains(kw, ignoreCase = true) }
                val channelOk = rule.channelName.isNotBlank() && combined.contains(rule.channelName, ignoreCase = true)
                pkgOk && (kwOk || channelOk)
            }
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

    companion object {
        fun buildDefaultSystemPrompt(
            categories: List<String> = com.example.model.CategoryCatalog.allCategories,
            channels: List<String> = com.example.model.ChannelCatalog.defaultChannels,
            billFieldsConfig: List<com.example.model.BillFieldConfig> = com.example.model.BillFieldCatalog.defaultFields,
            categoryParentMap: Map<String, String> = emptyMap(),
            channelParentMap: Map<String, String> = com.example.model.ChannelCatalog.defaultChannelParentMap
        ): String {
            val categoryUnion = categories
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .ifEmpty { com.example.model.CategoryCatalog.allCategories }
                .joinToString("|")
            val channelUnion = channels
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .ifEmpty { com.example.model.ChannelCatalog.defaultChannels }
                .joinToString("|")
            val customOptionalFields = com.example.model.BillFieldCatalog.customFields(billFieldsConfig)
                .filter { it.enabled }
            val customFieldsSchema = if (customOptionalFields.isNotEmpty()) {
                val keysHint = customOptionalFields.joinToString(",") { "\"${it.id}\":\"${it.label}或null\"" }
                ",\"customFields\":{$keysHint}"
            } else {
                ""
            }
            val channelAttributionSummary = channelParentMap.entries
                .filter { it.key.isNotBlank() && it.value.isNotBlank() }
                .groupBy({ it.value }, { it.key })
                .entries
                .joinToString("；") { (parent, children) ->
                    "识别到${children.joinToString("、") { "“$it”" }}时归属为“$parent”"
                }
                .ifBlank { "识别到“花呗”“余额宝”时 channel 统一归属为“支付宝”；识别到“零钱”“零钱通”时 channel 统一归属为“微信支付”" }
            val categoryAttributionSummary = categoryParentMap.entries
                .filter { it.key.isNotBlank() && it.value.isNotBlank() }
                .groupBy({ it.value }, { it.key })
                .entries
                .joinToString("；") { (parent, children) ->
                    "${children.joinToString("、")}归属「$parent」"
                }
            val extraRules = buildString {
                if (categoryAttributionSummary.isNotBlank()) {
                    appendLine("8. 【分类归属关系】$categoryAttributionSummary。")
                }
                if (customOptionalFields.isNotEmpty()) {
                    val idx = if (categoryAttributionSummary.isNotBlank()) 9 else 8
                    val fieldDesc = customOptionalFields.joinToString("、") { "${it.label}(key:${it.id})" }
                    appendLine("$idx. 【自定义账单信息字段】若文本包含以下信息请提取并填入 customFields：$fieldDesc；未提及的键可省略或填 null。")
                }
            }.trimEnd()
            return buildString {
                appendLine("你是一个精准的中国大陆账单与支付通知结构化解析引擎。请判断通知或账单文本是否为真实动账记录，并仅输出一行严格合法的 JSON 对象（不要包含 Markdown 代码块或额外解释）：")
                appendLine("""{"isFinancial":boolean,"amount":number,"currency":"ISO4217三位币种代码(如CNY/USD/HKD/EUR/JPY/GBP/CHF/SGD/KRW/AUD/CAD/THB/MYR等)","type":"EXPENSE|INCOME|TRANSFER","category":"$categoryUnion","merchant":"交易对方或商户名","channel":"$channelUnion","cardLastFour":"四位尾号或null","occurredTime":"yyyy-MM-dd HH:mm:ss或MM-dd HH:mm:ss或HH:mm:ss或null","confidence":0.0到1.0之间数字$customFieldsSchema}""")
                appendLine("严格遵守以下判定规则：")
                appendLine("1. 验证码、快递物流、登录提醒、营销广告、免息分期邀约等非动账消息必须返回 isFinancial=false，不可编造未出现的金额。")
                appendLine("2. 【严格金额单位校验】仅当通知中存在前后紧邻明确金额单位或中文/外币货币单位的数字（如 ¥/￥/$/€/£/元/块/人民币/新加坡元/美元/港元/日元/RMB/CNY/USD/SGD 等）时才视为有效金额；任何前后无金额单位的孤立数字（如 [3条]、中国移动10086、10010、955xx、订单号、流量GB、人数、验证码、时间等）一律返回 isFinancial=false。")
                appendLine("3. 【多金额精准提取】同一通知出现多个金额时（如同时出现消费/支出金额与已冻结金额、可用余额、折合人民币、手续费、原价或优惠立减），仅提取“支出/消费/付款/扣款/入账/收款”等核心交易字眼对应的金额及其配对币种（如“消费交易，金额10.49新加坡元，已冻结55.50人民币”应提取 amount=10.49, currency=\"SGD\"）。")
                appendLine("4. 【不计收支规则】微信/支付宝零钱提现、余额提现、提现到银行卡、银行卡收到提现到账、零钱充值、转入/转出零钱通或余额宝、本人名下账户互转、本人转账/同名转账、信用卡还款均属于内部资金划转，不计入日常支出与收入，必须返回 \"type\":\"TRANSFER\"，且 category 设为 \"不计收支\" 或 \"账户互转\"。")
                appendLine("5. 【渠道归属规则】$channelAttributionSummary。")
                appendLine("6. 消费退款、退货返现、冲正退回的 type 应为 INCOME（category 设为 \"退款返现\"）；他人向我转账收款 type 为 INCOME，向他人消费付款或发红包 type 为 EXPENSE。")
                append("7. occurredTime 尽量保留到秒（yyyy-MM-dd HH:mm:ss）；未出现时间则返回 null。")
                if (extraRules.isNotBlank()) {
                    appendLine()
                    append(extraRules)
                }
            }.trimIndent()
        }

        val DEFAULT_SYSTEM_PROMPT: String = buildDefaultSystemPrompt(
            com.example.model.CategoryCatalog.allCategories,
            com.example.model.ChannelCatalog.defaultChannels
        )

        private val LEGACY_DEFAULT_PROMPT_MARKERS = listOf(
            "CNY|USD|HKD|EUR|JPY|GBP|SGD|KRW|AUD|CAD|MOP|TWD",
            "yyyy-MM-dd HH:mm或MM-dd HH:mm或HH:mm或null"
        )

        private val CATEGORY_FIELD_REGEX = Regex("""("category"\s*:\s*")([^"]*)(")""")
        private val CHANNEL_FIELD_REGEX = Regex("""("channel"\s*:\s*")([^"]*)(")""")
        private val CUSTOM_CATEGORIES_LINE_REGEX = Regex("""(?m)^【当前可用分类列表】.*$""")
        private val CUSTOM_CHANNELS_LINE_REGEX = Regex("""(?m)^【当前可用渠道列表】.*$""")
        private val CUSTOM_BILL_FIELDS_LINE_REGEX = Regex("""(?m)^【自定义账单信息字段】.*$""")

        fun isLegacyOrDefaultPrompt(
            raw: String,
            activeCategories: List<String> = com.example.model.CategoryCatalog.allCategories,
            activeChannels: List<String> = com.example.model.ChannelCatalog.defaultChannels,
            billFieldsConfig: List<com.example.model.BillFieldConfig> = com.example.model.BillFieldCatalog.defaultFields,
            categoryParentMap: Map<String, String> = emptyMap(),
            channelParentMap: Map<String, String> = com.example.model.ChannelCatalog.defaultChannelParentMap
        ): Boolean {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() ||
                trimmed == DEFAULT_SYSTEM_PROMPT.trim() ||
                trimmed == buildDefaultSystemPrompt(
                    categories = activeCategories,
                    channels = activeChannels,
                    billFieldsConfig = billFieldsConfig,
                    categoryParentMap = categoryParentMap,
                    channelParentMap = channelParentMap
                ).trim()
            ) {
                return true
            }
            val isVeryOldDefault = LEGACY_DEFAULT_PROMPT_MARKERS.all { trimmed.contains(it) } &&
                !trimmed.contains("零钱提现")
            val isPreviousDefaultWithoutUnitRule =
                trimmed.contains("你是一个精准的中国大陆账单与支付通知结构化解析引擎。") &&
                    trimmed.contains("【不计收支规则】") &&
                    !trimmed.contains("【严格金额单位校验】")
            val isPreviousDefaultWithoutChannelRule =
                trimmed.contains("你是一个精准的中国大陆账单与支付通知结构化解析引擎。") &&
                    trimmed.contains("【严格金额单位校验】") &&
                    !trimmed.contains("【渠道归属规则】")
            val isPreviousDefaultWithoutMultiAmountRule =
                trimmed.contains("你是一个精准的中国大陆账单与支付通知结构化解析引擎。") &&
                    trimmed.contains("【渠道归属规则】") &&
                    !trimmed.contains("【多金额精准提取】")
            return isVeryOldDefault ||
                isPreviousDefaultWithoutUnitRule ||
                isPreviousDefaultWithoutChannelRule ||
                isPreviousDefaultWithoutMultiAmountRule
        }

        fun sanitizeStoredCustomPrompt(
            raw: String,
            activeCategories: List<String> = com.example.model.CategoryCatalog.allCategories,
            activeChannels: List<String> = com.example.model.ChannelCatalog.defaultChannels,
            billFieldsConfig: List<com.example.model.BillFieldConfig> = com.example.model.BillFieldCatalog.defaultFields,
            categoryParentMap: Map<String, String> = emptyMap(),
            channelParentMap: Map<String, String> = com.example.model.ChannelCatalog.defaultChannelParentMap
        ): String {
            val trimmed = raw.trim()
            return if (isLegacyOrDefaultPrompt(
                    raw = trimmed,
                    activeCategories = activeCategories,
                    activeChannels = activeChannels,
                    billFieldsConfig = billFieldsConfig,
                    categoryParentMap = categoryParentMap,
                    channelParentMap = channelParentMap
                )
            ) "" else trimmed
        }

        fun syncPromptForCategoryChange(
            currentCustomPrompt: String,
            oldName: String?,
            newName: String?,
            allActiveCategories: List<String>
        ): String {
            val cleanedPrompt = sanitizeStoredCustomPrompt(currentCustomPrompt, allActiveCategories)
            // When using the built-in prompt, leave stored custom prompt blank so it dynamically uses allActiveCategories.
            if (cleanedPrompt.isBlank()) return ""

            var updated = cleanedPrompt
            val cleanOld = oldName?.trim()?.takeIf { it.isNotEmpty() }
            val cleanNew = newName?.trim()?.takeIf { it.isNotEmpty() }
            val activeUnion = allActiveCategories
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString("|")

            if (cleanOld != null && cleanNew != null && cleanOld != cleanNew) {
                updated = updated.replace(cleanOld, cleanNew)
            }

            val categoryMatch = CATEGORY_FIELD_REGEX.find(updated)
            if (categoryMatch != null) {
                val replacement = "${categoryMatch.groupValues[1]}$activeUnion${categoryMatch.groupValues[3]}"
                updated = updated.replaceRange(categoryMatch.range, replacement)
            } else {
                if (cleanOld != null && cleanNew == null) {
                    updated = updated
                        .replace("|$cleanOld", "")
                        .replace("$cleanOld|", "")
                        .replace("、$cleanOld", "")
                        .replace("$cleanOld、", "")
                }
                val summaryLine = "【当前可用分类列表】$activeUnion"
                updated = if (CUSTOM_CATEGORIES_LINE_REGEX.containsMatchIn(updated)) {
                    CUSTOM_CATEGORIES_LINE_REGEX.replace(updated, summaryLine)
                } else {
                    "$updated\n$summaryLine"
                }
            }
            return sanitizeStoredCustomPrompt(updated, allActiveCategories).take(4000)
        }

        fun syncPromptForChannelChange(
            currentCustomPrompt: String,
            oldName: String?,
            newName: String?,
            allActiveChannels: List<String>,
            allActiveCategories: List<String> = com.example.model.CategoryCatalog.allCategories
        ): String {
            val cleanedPrompt = sanitizeStoredCustomPrompt(currentCustomPrompt, allActiveCategories, allActiveChannels)
            if (cleanedPrompt.isBlank()) return ""

            var updated = cleanedPrompt
            val cleanOld = oldName?.trim()?.takeIf { it.isNotEmpty() }
            val cleanNew = newName?.trim()?.takeIf { it.isNotEmpty() }
            val activeUnion = allActiveChannels
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString("|")

            if (cleanOld != null && cleanNew != null && cleanOld != cleanNew) {
                updated = updated.replace(cleanOld, cleanNew)
            }

            val channelMatch = CHANNEL_FIELD_REGEX.find(updated)
            if (channelMatch != null) {
                val replacement = "${channelMatch.groupValues[1]}$activeUnion${channelMatch.groupValues[3]}"
                updated = updated.replaceRange(channelMatch.range, replacement)
            } else {
                if (cleanOld != null && cleanNew == null) {
                    updated = updated
                        .replace("|$cleanOld", "")
                        .replace("$cleanOld|", "")
                        .replace("、$cleanOld", "")
                        .replace("$cleanOld、", "")
                }
                val summaryLine = "【当前可用渠道列表】$activeUnion"
                updated = if (CUSTOM_CHANNELS_LINE_REGEX.containsMatchIn(updated)) {
                    CUSTOM_CHANNELS_LINE_REGEX.replace(updated, summaryLine)
                } else {
                    "$updated\n$summaryLine"
                }
            }
            return sanitizeStoredCustomPrompt(updated, allActiveCategories, allActiveChannels).take(4000)
        }

        private val sharedClient: OkHttpClient by lazy {
            AppHttpClient.derived(
                connectTimeoutSec = 15,
                readTimeoutSec = 35,
                writeTimeoutSec = 20
            )
        }

        fun trimMemory(evictConnections: Boolean = true) {
            if (evictConnections) {
                AppHttpClient.trimIdleResources()
            }
        }

        fun resolveSelectedRules(
            config: AiConfig,
            availableRules: List<CustomParsingRule>
        ): List<CustomParsingRule> {
            if (config.selectedPromptRuleIds.isEmpty() || availableRules.isEmpty()) return emptyList()
            val idSet = config.selectedPromptRuleIds.toSet()
            return availableRules.filter { it.id in idSet }
        }

        fun formatRulesPromptBlock(selectedRules: List<CustomParsingRule>): String {
            if (selectedRules.isEmpty()) return ""
            return buildString {
                appendLine("【用户选定参考识别规则（解析时优先参考下列规则映射）】")
                selectedRules.take(24).forEachIndexed { idx, rule ->
                    val typeDesc = when {
                        rule.isIgnoreRule -> "忽略(非动账通知)"
                        rule.defaultType.equals("EXPENSE", true) -> "支出"
                        rule.defaultType.equals("INCOME", true) -> "收入"
                        rule.defaultType.equals("TRANSFER", true) -> "转账"
                        else -> "自动判断"
                    }
                    append("${idx + 1}. 规则「${rule.name.ifBlank { rule.channelName }}」")
                    append("｜包名:${rule.packageFilter.ifBlank { "*" }}")
                    if (rule.keywordPattern.isNotBlank()) append("｜关键词:${rule.keywordPattern}")
                    if (rule.channelName.isNotBlank()) append("｜渠道:${rule.channelName}")
                    append("｜类型:$typeDesc")
                    if (rule.defaultCategory.isNotBlank() && rule.defaultCategory != "自动") {
                        append("｜分类:${rule.defaultCategory}")
                    }
                    if (rule.merchantOverride.isNotBlank()) {
                        append("｜商户名:${rule.merchantOverride}")
                    }
                    if (rule.templateSignature.isNotBlank()) {
                        append("｜模板:${rule.templateSignature.take(60)}")
                    }
                    appendLine()
                }
            }.trimEnd()
        }

        fun syncPromptForBillFieldsChange(
            currentCustomPrompt: String,
            billFieldsConfig: List<com.example.model.BillFieldConfig>,
            allActiveCategories: List<String> = com.example.model.CategoryCatalog.allCategories,
            allActiveChannels: List<String> = com.example.model.ChannelCatalog.defaultChannels
        ): String {
            val cleanedPrompt = sanitizeStoredCustomPrompt(
                raw = currentCustomPrompt,
                activeCategories = allActiveCategories,
                activeChannels = allActiveChannels,
                billFieldsConfig = billFieldsConfig
            )
            if (cleanedPrompt.isBlank()) return ""
            val customFields = com.example.model.BillFieldCatalog.customFields(billFieldsConfig).filter { it.enabled }
            var updated = cleanedPrompt
            if (customFields.isEmpty()) {
                updated = CUSTOM_BILL_FIELDS_LINE_REGEX.replace(updated, "").trim()
            } else {
                val summaryLine = "【自定义账单信息字段】" + customFields.joinToString("、") { "${it.label}(${it.id})" }
                updated = if (CUSTOM_BILL_FIELDS_LINE_REGEX.containsMatchIn(updated)) {
                    CUSTOM_BILL_FIELDS_LINE_REGEX.replace(updated, summaryLine)
                } else {
                    "$updated\n$summaryLine"
                }
            }
            return sanitizeStoredCustomPrompt(
                raw = updated,
                activeCategories = allActiveCategories,
                activeChannels = allActiveChannels,
                billFieldsConfig = billFieldsConfig
            ).take(4000)
        }

        fun buildEffectiveSystemPrompt(
            config: AiConfig,
            selectedRules: List<CustomParsingRule> = emptyList(),
            activeCategories: List<String> = com.example.model.CategoryCatalog.allCategories,
            activeChannels: List<String> = com.example.model.ChannelCatalog.defaultChannels,
            billFieldsConfig: List<com.example.model.BillFieldConfig> = com.example.model.BillFieldCatalog.defaultFields,
            categoryParentMap: Map<String, String> = emptyMap(),
            channelParentMap: Map<String, String> = com.example.model.ChannelCatalog.defaultChannelParentMap
        ): String {
            val defaultPrompt = buildDefaultSystemPrompt(
                categories = activeCategories,
                channels = activeChannels,
                billFieldsConfig = billFieldsConfig,
                categoryParentMap = categoryParentMap,
                channelParentMap = channelParentMap
            )
            val basePrompt = sanitizeStoredCustomPrompt(
                raw = config.customSystemPrompt,
                activeCategories = activeCategories,
                activeChannels = activeChannels,
                billFieldsConfig = billFieldsConfig,
                categoryParentMap = categoryParentMap,
                channelParentMap = channelParentMap
            ).ifBlank { defaultPrompt }
            val rulesBlock = formatRulesPromptBlock(selectedRules)
            return if (rulesBlock.isBlank()) {
                basePrompt
            } else {
                "$basePrompt\n\n$rulesBlock"
            }
        }

        internal fun extractCandidateTokens(text: String): Set<String> {
            if (text.isBlank()) return emptySet()
            val clean = text.replace(Regex("""[\s，。、：:；;!！?？()（）【】\[\]「」"“”]+"""), " ")
            val candidates = LinkedHashSet<String>(128)
            clean.split(" ").forEach { chunk ->
                for (i in 0 until chunk.length) {
                    val maxLen = minOf(6, chunk.length - i)
                    for (len in 2..maxLen) {
                        candidates.add(chunk.substring(i, i + len))
                    }
                }
            }
            return candidates
        }

        internal fun greedyOrderMatchedTokens(text: String, matchedSet: Set<String>): List<String> {
            if (matchedSet.isEmpty()) return emptyList()
            val found = mutableListOf<String>()
            val clean = text.replace(Regex("""[\s，。、：:；;!！?？()（）【】\[\]「」"“”]+"""), " ")
            clean.split(" ").forEach { chunk ->
                var i = 0
                while (i < chunk.length) {
                    var matched: String? = null
                    val maxLen = minOf(6, chunk.length - i)
                    for (len in maxLen downTo 2) {
                        val sub = chunk.substring(i, i + len)
                        if (sub in matchedSet) {
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
    }
}
