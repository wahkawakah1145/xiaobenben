package com.example.data

import com.example.model.AiConfig
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaException
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GgufHeaderInfo(
    val valid: Boolean,
    val version: Int = 0,
    val tensorCount: Long = 0L,
    val kvCount: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val errorMessage: String = ""
)

data class LocalLlmRuntimeStatus(
    val loadedModelPath: String? = null,
    val loadedModelName: String? = null,
    val isLoaded: Boolean = false,
    val isBusy: Boolean = false,
    val contextSize: Int = 0,
    val threads: Int = 0,
    val lastTokensPerSecond: Float = 0f,
    val lastTokensGenerated: Int = 0,
    val lastLatencyMs: Long = 0L,
    val lastError: String? = null
)

data class LocalGgufCompletion(
    val text: String,
    val tokensGenerated: Int,
    val tokensPerSecond: Float,
    val promptEvalTimeMs: Long,
    val generateTimeMs: Long
)

class NativeGgufUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Thread-safe singleton manager for on-device llama.cpp (.gguf) inference.
 *
 * Memory policy for background bookkeeping safety:
 * - Lazy-loaded on demand (0 MB native RAM until inference is requested).
 * - Single-flight Mutex prevents concurrent notifications from double-loading weights.
 * - Automatic idle unload timer (configurable via AiConfig.localAutoUnloadSeconds) releases
 *   native llama.cpp weights + KV cache cleanly so background RAM returns to baseline while
 *   NotificationListenerService / AccessibilityService stay continuously active.
 * - Immediate release hook invoked by LedgerApplication.requestLightBackgroundTrim / onTrimMemory.
 */
object LocalGgufRuntimeManager {

    private val mutex = Mutex()
    private val idleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var loadedModel: LlamaModel? = null

    @Volatile
    private var loadedModelPath: String? = null

    @Volatile
    private var loadedModelName: String? = null

    @Volatile
    private var loadedConfigSignature: String? = null

    @Volatile
    private var autoUnloadJob: Job? = null

    private val _status = MutableStateFlow(LocalLlmRuntimeStatus())
    val status: StateFlow<LocalLlmRuntimeStatus> = _status.asStateFlow()

    /**
     * Validates the first 24 bytes of a GGUF file without allocating large buffers.
     * Catches truncated downloads, HTML 404 mirror pages, or Git LFS pointer text files immediately.
     */
    fun inspectGgufFile(file: File): GgufHeaderInfo {
        if (!file.exists() || !file.isFile) {
            return GgufHeaderInfo(
                valid = false,
                errorMessage = "模型权重文件不存在"
            )
        }
        val fileSize = file.length()
        if (fileSize < 24L) {
            return GgufHeaderInfo(
                valid = false,
                fileSizeBytes = fileSize,
                errorMessage = "权重文件过小（${fileSize} 字节），可能下载未完成"
            )
        }
        return runCatching {
            val header = ByteArray(24)
            FileInputStream(file).use { input ->
                var readTotal = 0
                while (readTotal < 24) {
                    val read = input.read(header, readTotal, 24 - readTotal)
                    if (read <= 0) break
                    readTotal += read
                }
                if (readTotal < 24) {
                    return GgufHeaderInfo(
                        valid = false,
                        fileSizeBytes = fileSize,
                        errorMessage = "无法读取完整 GGUF 文件头"
                    )
                }
            }
            val magic = String(header, 0, 4, Charsets.US_ASCII)
            if (magic != "GGUF") {
                val preview = String(header, 0, minOf(header.size, 24), Charsets.UTF_8)
                    .replace('\n', ' ')
                    .replace('\r', ' ')
                    .trim()
                val hint = when {
                    preview.startsWith("version https://git-lfs", ignoreCase = true) ->
                        "文件为 Git LFS 指针文本而非真实权重，请重新下载或从本地导入完整 .gguf 文件"
                    preview.startsWith("<!DOCTYPE", ignoreCase = true) ||
                        preview.startsWith("<html", ignoreCase = true) ->
                        "下载镜像返回了 HTML 网页而非权重文件，请切换镜像或手动导入 .gguf"
                    else ->
                        "文件头标识为「${magic.take(4)}」，不是有效的 GGUF 格式权重"
                }
                return GgufHeaderInfo(
                    valid = false,
                    fileSizeBytes = fileSize,
                    errorMessage = hint
                )
            }
            val buffer = ByteBuffer.wrap(header, 4, 20).order(ByteOrder.LITTLE_ENDIAN)
            val version = buffer.int
            val tensorCount = buffer.long
            val kvCount = buffer.long
            if (version !in 2..3) {
                return GgufHeaderInfo(
                    valid = false,
                    version = version,
                    tensorCount = tensorCount,
                    kvCount = kvCount,
                    fileSizeBytes = fileSize,
                    errorMessage = "不支持的 GGUF 版本（v$version），当前推理引擎仅支持 GGUF v2/v3"
                )
            }
            if (tensorCount == 0L) {
                return GgufHeaderInfo(
                    valid = false,
                    version = version,
                    tensorCount = tensorCount,
                    kvCount = kvCount,
                    fileSizeBytes = fileSize,
                    errorMessage = "GGUF 张量数量为 0，权重文件无效"
                )
            }
            GgufHeaderInfo(
                valid = true,
                version = version,
                tensorCount = tensorCount,
                kvCount = kvCount,
                fileSizeBytes = fileSize
            )
        }.getOrElse { err ->
            GgufHeaderInfo(
                valid = false,
                fileSizeBytes = fileSize,
                errorMessage = "校验 GGUF 文件失败：${err.message ?: err.javaClass.simpleName}"
            )
        }
    }

    fun findPrimaryGgufFile(directory: File): File? {
        if (!directory.exists() || !directory.isDirectory) return null
        return directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) && it.length() > 0L }
            ?.maxByOrNull { it.length() }
    }

    private fun buildLlamaConfig(config: AiConfig, lowMemoryMode: Boolean): LlamaConfig {
        val availableCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val effectiveContext = if (lowMemoryMode) {
            config.contextSize.coerceIn(512, 1024)
        } else {
            config.contextSize.coerceIn(512, 4096)
        }
        val effectiveThreads = if (lowMemoryMode) {
            config.threads.coerceIn(1, minOf(4, availableCores))
        } else {
            config.threads.coerceIn(1, availableCores)
        }
        return LlamaConfig(
            contextSize = effectiveContext,
            threads = effectiveThreads,
            gpuLayers = 0,
            temperature = config.temperature.coerceIn(0.0f, 1.5f),
            topP = config.topP.coerceIn(0.1f, 1.0f),
            topK = config.topK.coerceIn(1, 100),
            seed = config.seed
        )
    }

    private fun configSignature(llamaConfig: LlamaConfig): String =
        "${llamaConfig.contextSize}|${llamaConfig.threads}|${llamaConfig.temperature}|${llamaConfig.topP}|${llamaConfig.topK}|${llamaConfig.seed}"

    private fun releaseLockedUnsafe() {
        autoUnloadJob?.cancel()
        autoUnloadJob = null
        val current = loadedModel
        loadedModel = null
        loadedModelPath = null
        loadedModelName = null
        loadedConfigSignature = null
        if (current != null) {
            runCatching { Llama.releaseModel(current) }
        }
        _status.value = _status.value.copy(
            loadedModelPath = null,
            loadedModelName = null,
            isLoaded = false,
            isBusy = false,
            contextSize = 0,
            threads = 0
        )
    }

    private fun scheduleAutoUnloadLocked(seconds: Int) {
        autoUnloadJob?.cancel()
        autoUnloadJob = null
        if (seconds < 0) return // -1: keep loaded until system memory pressure or manual unload
        if (seconds == 0) {
            releaseLockedUnsafe()
            return
        }
        autoUnloadJob = idleScope.launch {
            delay(seconds * 1000L)
            if (mutex.tryLock()) {
                try {
                    if (loadedModel != null && !_status.value.isBusy) {
                        releaseLockedUnsafe()
                    }
                } finally {
                    mutex.unlock()
                }
            }
        }
    }

    suspend fun ensureLoaded(
        modelFile: File,
        displayName: String,
        config: AiConfig,
        lowMemoryMode: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val header = inspectGgufFile(modelFile)
        if (!header.valid) {
            _status.value = _status.value.copy(lastError = header.errorMessage)
            return@withContext Result.failure(IllegalArgumentException(header.errorMessage))
        }
        val llamaConfig = buildLlamaConfig(config, lowMemoryMode)
        val signature = configSignature(llamaConfig)
        mutex.withLock {
            autoUnloadJob?.cancel()
            val existing = loadedModel
            if (existing != null &&
                existing.isLoaded &&
                loadedModelPath == modelFile.absolutePath &&
                loadedConfigSignature == signature
            ) {
                scheduleAutoUnloadLocked(config.localAutoUnloadSeconds)
                return@withContext Result.success(Unit)
            }
            releaseLockedUnsafe()
            _status.value = _status.value.copy(
                isBusy = true,
                loadedModelName = displayName,
                lastError = null
            )
            try {
                val model = Llama.loadModel(modelFile.absolutePath, llamaConfig)
                loadedModel = model
                loadedModelPath = modelFile.absolutePath
                loadedModelName = displayName
                loadedConfigSignature = signature
                _status.value = _status.value.copy(
                    loadedModelPath = modelFile.absolutePath,
                    loadedModelName = displayName,
                    isLoaded = true,
                    isBusy = false,
                    contextSize = llamaConfig.contextSize,
                    threads = llamaConfig.threads,
                    lastError = null
                )
                scheduleAutoUnloadLocked(config.localAutoUnloadSeconds)
                Result.success(Unit)
            } catch (e: LinkageError) {
                val msg = "当前设备架构不支持内置 arm64-v8a GGUF 推理引擎（${e.message ?: e.javaClass.simpleName}）"
                _status.value = _status.value.copy(isLoaded = false, isBusy = false, lastError = msg)
                Result.failure(NativeGgufUnavailableException(msg, e))
            } catch (e: LlamaException) {
                val msg = "GGUF 模型加载失败：${e.message ?: "请检查模型文件完整性或调小上下文长度"}"
                _status.value = _status.value.copy(isLoaded = false, isBusy = false, lastError = msg)
                Result.failure(IllegalStateException(msg, e))
            } catch (t: Throwable) {
                val msg = "GGUF 模型加载异常：${t.message ?: t.javaClass.simpleName}"
                _status.value = _status.value.copy(isLoaded = false, isBusy = false, lastError = msg)
                Result.failure(IllegalStateException(msg, t))
            }
        }
    }

    suspend fun complete(
        modelFile: File,
        displayName: String,
        config: AiConfig,
        systemPrompt: String,
        userPrompt: String,
        maxTokensOverride: Int? = null,
        lowMemoryMode: Boolean = false,
        releaseImmediatelyAfter: Boolean = false
    ): Result<LocalGgufCompletion> = withContext(Dispatchers.IO) {
        val header = inspectGgufFile(modelFile)
        if (!header.valid) {
            _status.value = _status.value.copy(lastError = header.errorMessage)
            return@withContext Result.failure(IllegalArgumentException(header.errorMessage))
        }
        val llamaConfig = buildLlamaConfig(config, lowMemoryMode)
        val signature = configSignature(llamaConfig)
        val maxTokens = (maxTokensOverride ?: config.maxTokens)
            .coerceIn(64, if (lowMemoryMode) 512 else 2048)

        mutex.withLock {
            autoUnloadJob?.cancel()
            autoUnloadJob = null
            _status.value = _status.value.copy(
                isBusy = true,
                loadedModelName = displayName,
                lastError = null
            )
            try {
                val model = if (
                    loadedModel != null &&
                    loadedModel!!.isLoaded &&
                    loadedModelPath == modelFile.absolutePath &&
                    loadedConfigSignature == signature
                ) {
                    loadedModel!!
                } else {
                    releaseLockedUnsafe()
                    _status.value = _status.value.copy(isBusy = true, loadedModelName = displayName)
                    val fresh = Llama.loadModel(modelFile.absolutePath, llamaConfig)
                    loadedModel = fresh
                    loadedModelPath = modelFile.absolutePath
                    loadedModelName = displayName
                    loadedConfigSignature = signature
                    fresh
                }

                val result = Llama.complete(
                    model = model,
                    prompt = userPrompt,
                    systemPrompt = systemPrompt,
                    maxTokens = maxTokens
                )
                val totalLatency = (result.promptEvalTimeMs + result.generateTimeMs).coerceAtLeast(1L)
                _status.value = _status.value.copy(
                    loadedModelPath = modelFile.absolutePath,
                    loadedModelName = displayName,
                    isLoaded = true,
                    isBusy = false,
                    contextSize = llamaConfig.contextSize,
                    threads = llamaConfig.threads,
                    lastTokensPerSecond = result.tokensPerSecond,
                    lastTokensGenerated = result.tokensGenerated,
                    lastLatencyMs = totalLatency,
                    lastError = null
                )
                if (releaseImmediatelyAfter || config.localAutoUnloadSeconds == 0) {
                    releaseLockedUnsafe()
                } else {
                    scheduleAutoUnloadLocked(config.localAutoUnloadSeconds)
                }
                Result.success(
                    LocalGgufCompletion(
                        text = result.text.trim(),
                        tokensGenerated = result.tokensGenerated,
                        tokensPerSecond = result.tokensPerSecond,
                        promptEvalTimeMs = result.promptEvalTimeMs,
                        generateTimeMs = result.generateTimeMs
                    )
                )
            } catch (e: LinkageError) {
                val msg = "当前设备架构不支持内置 arm64-v8a GGUF 推理引擎（${e.message ?: e.javaClass.simpleName}）"
                _status.value = _status.value.copy(isLoaded = false, isBusy = false, lastError = msg)
                Result.failure(NativeGgufUnavailableException(msg, e))
            } catch (e: LlamaException) {
                val msg = "端侧 GGUF 推理失败：${e.message ?: "请检查模型兼容性或调小上下文长度"}"
                _status.value = _status.value.copy(isBusy = false, lastError = msg)
                if (releaseImmediatelyAfter) {
                    releaseLockedUnsafe()
                } else {
                    scheduleAutoUnloadLocked(config.localAutoUnloadSeconds)
                }
                Result.failure(IllegalStateException(msg, e))
            } catch (t: Throwable) {
                val msg = "端侧 GGUF 推理异常：${t.message ?: t.javaClass.simpleName}"
                _status.value = _status.value.copy(isBusy = false, lastError = msg)
                if (releaseImmediatelyAfter) {
                    releaseLockedUnsafe()
                } else {
                    scheduleAutoUnloadLocked(config.localAutoUnloadSeconds)
                }
                Result.failure(IllegalStateException(msg, t))
            }
        }
    }

    /**
     * Releases native llama.cpp weights and KV cache immediately if not actively running inference.
     * Safe to invoke from Application.onTrimMemory / onLowMemory on the main thread.
     */
    fun releaseModelIfIdle(force: Boolean = false) {
        if (loadedModel == null) return
        if (mutex.tryLock()) {
            try {
                releaseLockedUnsafe()
            } finally {
                mutex.unlock()
            }
        } else if (force) {
            idleScope.launch {
                mutex.withLock {
                    releaseLockedUnsafe()
                }
            }
        }
    }

    suspend fun releaseModelNow() = withContext(Dispatchers.IO) {
        mutex.withLock {
            releaseLockedUnsafe()
        }
    }
}
