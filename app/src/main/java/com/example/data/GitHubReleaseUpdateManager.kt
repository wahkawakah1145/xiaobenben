package com.example.data

import com.example.model.GitHubUpdateMirror
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class ReleaseUpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val body: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkAssetName: String?,
    val apkSizeBytes: Long,
    val publishedAt: String,
    val hasNewerVersion: Boolean,
    val usedMirrorLabel: String = GitHubUpdateMirror.DIRECT.label
)

/**
 * Lightweight on-demand GitHub Releases checker using existing OkHttp & org.json.
 * Supports optional domestic GitHub mirrors for both release metadata check and APK download acceleration.
 * Strictly manual-only: never runs background polling or automatic installation.
 */
object GitHubReleaseUpdateManager {

    private val client: OkHttpClient by lazy {
        AppHttpClient.derived(connectTimeoutSec = 12, readTimeoutSec = 15, followRedirects = true)
    }

    fun applyMirrorPrefix(rawUrl: String, mirror: GitHubUpdateMirror): String {
        val cleanUrl = rawUrl.trim()
        if (cleanUrl.isBlank() || mirror == GitHubUpdateMirror.DIRECT || mirror.proxyPrefix.isBlank()) {
            return cleanUrl
        }
        val prefix = mirror.proxyPrefix.trimEnd('/') + "/"
        return if (cleanUrl.startsWith(prefix, ignoreCase = true)) cleanUrl else "$prefix$cleanUrl"
    }

    suspend fun checkLatestRelease(
        repoPath: String,
        currentVersionName: String,
        mirror: GitHubUpdateMirror = GitHubUpdateMirror.DIRECT
    ): Result<ReleaseUpdateInfo> = withContext(Dispatchers.IO) {
        val normalizedRepo = repoPath.trim().trim('/')
        if (!normalizedRepo.contains("/")) {
            return@withContext Result.failure(IllegalArgumentException("无效的 GitHub 仓库地址：$repoPath"))
        }

        val officialApiUrl = "https://api.github.com/repos/$normalizedRepo/releases/latest"
        val officialWebLatestUrl = "https://github.com/$normalizedRepo/releases/latest"

        val candidateRequests = if (mirror == GitHubUpdateMirror.DIRECT) {
            listOf(officialApiUrl to true)
        } else {
            listOf(
                applyMirrorPrefix(officialApiUrl, mirror) to true,
                applyMirrorPrefix(officialWebLatestUrl, mirror) to false,
                officialApiUrl to true
            )
        }

        var lastError: Throwable? = null
        for ((url, isApiJson) in candidateRequests) {
            try {
                val reqBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "XiaoBenBen-Android/$currentVersionName")
                    .get()
                if (isApiJson) {
                    reqBuilder.header("Accept", "application/vnd.github+json")
                } else {
                    reqBuilder.header("Accept", "application/json")
                }

                client.newCall(reqBuilder.build()).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty().trim()
                    when {
                        response.code == 404 -> {
                            lastError = IllegalStateException("仓库 $normalizedRepo 尚未发布正式 Release 版本")
                        }
                        response.code == 403 -> {
                            lastError = IllegalStateException("GitHub API 请求频率受限 (HTTP 403)，请稍后再试或切换镜像源")
                        }
                        !response.isSuccessful -> {
                            lastError = IllegalStateException("检查更新失败 (HTTP ${response.code})")
                        }
                        bodyText.isBlank() -> {
                            lastError = IllegalStateException("GitHub Release 响应为空")
                        }
                        bodyText.startsWith("{") -> {
                            val parsed = parseLatestReleaseJson(
                                jsonText = bodyText,
                                repoPath = normalizedRepo,
                                currentVersionName = currentVersionName,
                                mirror = mirror
                            )
                            return@withContext Result.success(parsed)
                        }
                        else -> {
                            val extractedTag = extractTagFromUrlOrHtml(response.request.url.toString(), bodyText)
                            if (extractedTag != null) {
                                val cleanVer = extractedTag.removePrefix("v").removePrefix("V").ifBlank { extractedTag }
                                val rawReleaseUrl = "https://github.com/$normalizedRepo/releases/tag/$extractedTag"
                                val guessedApkName = "xiaobenben-$extractedTag-arm64-v8a.apk"
                                val rawApkUrl = "https://github.com/$normalizedRepo/releases/download/$extractedTag/$guessedApkName"
                                return@withContext Result.success(
                                    ReleaseUpdateInfo(
                                        tagName = extractedTag,
                                        versionName = cleanVer,
                                        releaseName = "小本本 $extractedTag",
                                        body = "已通过 ${mirror.label} 检测到最新 Release 标签 $extractedTag，点击下方按钮可加速下载 APK 或打开 Release 页面查看完整日志。",
                                        htmlUrl = rawReleaseUrl,
                                        apkDownloadUrl = applyMirrorPrefix(rawApkUrl, mirror),
                                        apkAssetName = guessedApkName,
                                        apkSizeBytes = 0L,
                                        publishedAt = "",
                                        hasNewerVersion = isRemoteVersionNewer(extractedTag, currentVersionName),
                                        usedMirrorLabel = mirror.label
                                    )
                                )
                            }
                            lastError = IllegalStateException("镜像源返回非 JSON 内容，已尝试切换节点")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }

        val errMsg = lastError?.localizedMessage?.takeIf { it.isNotBlank() } ?: "网络连接异常"
        Result.failure(IllegalStateException("检查更新失败：$errMsg", lastError))
    }

    fun parseLatestReleaseJson(
        jsonText: String,
        repoPath: String,
        currentVersionName: String,
        mirror: GitHubUpdateMirror = GitHubUpdateMirror.DIRECT
    ): ReleaseUpdateInfo {
        val obj = JSONObject(jsonText)
        val tagName = obj.optString("tag_name").trim()
        val versionName = tagName.removePrefix("v").removePrefix("V").ifBlank { tagName }
        val releaseName = obj.optString("name").trim().ifBlank { tagName.ifBlank { "最新版本" } }
        val body = obj.optString("body").trim()
        val htmlUrl = obj.optString("html_url").trim()
            .ifBlank { "https://github.com/$repoPath/releases" }
        val publishedAt = obj.optString("published_at").trim()

        var apkUrl: String? = null
        var apkName: String? = null
        var apkSize = 0L
        val assets = obj.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name").trim()
                val dl = asset.optString("browser_download_url").trim()
                if (name.endsWith(".apk", ignoreCase = true) && dl.isNotBlank()) {
                    val isArm64 = name.contains("arm64", ignoreCase = true) ||
                        name.contains("armv8", ignoreCase = true)
                    if (apkUrl == null || isArm64) {
                        apkUrl = applyMirrorPrefix(dl, mirror)
                        apkName = name
                        apkSize = asset.optLong("size", 0L).coerceAtLeast(0L)
                        if (isArm64) break
                    }
                }
            }
        }

        if (apkUrl == null && tagName.isNotBlank()) {
            val guessedName = "xiaobenben-$tagName-arm64-v8a.apk"
            val rawDl = "https://github.com/$repoPath/releases/download/$tagName/$guessedName"
            apkUrl = applyMirrorPrefix(rawDl, mirror)
            apkName = guessedName
        }

        val hasNewer = isRemoteVersionNewer(tagName.ifBlank { versionName }, currentVersionName)
        return ReleaseUpdateInfo(
            tagName = tagName.ifBlank { "v$versionName" },
            versionName = versionName,
            releaseName = releaseName,
            body = body,
            htmlUrl = htmlUrl,
            apkDownloadUrl = apkUrl,
            apkAssetName = apkName,
            apkSizeBytes = apkSize,
            publishedAt = publishedAt,
            hasNewerVersion = hasNewer,
            usedMirrorLabel = mirror.label
        )
    }

    private fun extractTagFromUrlOrHtml(finalUrl: String, html: String): String? {
        val urlMatch = Regex("""/releases/tag/([vV]?[0-9][0-9A-Za-z._-]*)""").find(finalUrl)
        if (urlMatch != null) return urlMatch.groupValues[1]
        val htmlMatch = Regex("""/releases/tag/([vV]?[0-9][0-9A-Za-z._-]*)""").find(html)
        return htmlMatch?.groupValues?.get(1)
    }

    fun isRemoteVersionNewer(remoteTag: String, currentVersion: String): Boolean {
        val remoteParts = extractNumericVersionParts(remoteTag)
        val currentParts = extractNumericVersionParts(currentVersion)
        if (remoteParts.isEmpty()) return false
        if (currentParts.isEmpty()) return true
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        val remoteHasPre = remoteTag.trim().removePrefix("v").removePrefix("V").contains('-')
        val currentHasPre = currentVersion.trim().removePrefix("v").removePrefix("V").contains('-')
        return !remoteHasPre && currentHasPre
    }

    private fun extractNumericVersionParts(raw: String): List<Int> {
        val core = raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        return core.split('.')
            .mapNotNull { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() }
    }
}
