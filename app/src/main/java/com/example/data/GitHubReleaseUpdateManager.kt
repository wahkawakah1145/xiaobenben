package com.example.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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
    val hasNewerVersion: Boolean
)

/**
 * Lightweight on-demand GitHub Releases checker using existing OkHttp & org.json.
 * Strictly manual-only: never runs background polling or automatic installation.
 */
object GitHubReleaseUpdateManager {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun checkLatestRelease(
        repoPath: String,
        currentVersionName: String
    ): Result<ReleaseUpdateInfo> = withContext(Dispatchers.IO) {
        val normalizedRepo = repoPath.trim().trim('/')
        if (!normalizedRepo.contains("/")) {
            return@withContext Result.failure(IllegalArgumentException("无效的 GitHub 仓库地址：$repoPath"))
        }
        val apiUrl = "https://api.github.com/repos/$normalizedRepo/releases/latest"
        try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "XiaoBenBen-Android/$currentVersionName")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                when {
                    response.code == 404 -> {
                        Result.failure(IllegalStateException("仓库 $normalizedRepo 尚未发布正式 Release 版本"))
                    }
                    response.code == 403 -> {
                        Result.failure(IllegalStateException("GitHub API 请求频率受限 (HTTP 403)，请稍后再试或直接前往仓库查看"))
                    }
                    !response.isSuccessful -> {
                        Result.failure(IllegalStateException("检查更新失败 (HTTP ${response.code})"))
                    }
                    bodyText.isBlank() -> {
                        Result.failure(IllegalStateException("GitHub Release 响应为空"))
                    }
                    else -> {
                        Result.success(parseLatestReleaseJson(bodyText, normalizedRepo, currentVersionName))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException("检查更新失败：${e.localizedMessage ?: "网络连接异常"}", e))
        }
    }

    fun parseLatestReleaseJson(
        jsonText: String,
        repoPath: String,
        currentVersionName: String
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
                    // Prefer arm64-v8a / armv8 APK if multiple assets exist
                    val isArm64 = name.contains("arm64", ignoreCase = true) ||
                        name.contains("armv8", ignoreCase = true)
                    if (apkUrl == null || isArm64) {
                        apkUrl = dl
                        apkName = name
                        apkSize = asset.optLong("size", 0L).coerceAtLeast(0L)
                        if (isArm64) break
                    }
                }
            }
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
            hasNewerVersion = hasNewer
        )
    }

    /**
     * Compares semantic version strings (e.g. "v1.2.0" vs "1.1.9" or "1.0").
     * Returns true only when [remoteTag] is strictly newer than [currentVersion].
     */
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
        // If numeric parts are equal, a stable release (no hyphen suffix) is newer than a prerelease
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
