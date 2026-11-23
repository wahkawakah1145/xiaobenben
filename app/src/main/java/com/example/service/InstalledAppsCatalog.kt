package com.example.service

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.parser.ChinaPaymentCatalog
import java.util.Locale

data class InstalledAppEntry(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val isRecommendedFinancial: Boolean,
    val isSmsApp: Boolean,
    val isInstalled: Boolean = true
)

object InstalledAppsCatalog {

    private val recommendedKeywordRegex = Regex(
        """银行|bank|支付|pay|钱包|wallet|云闪付|银联|unionpay|金融|消费|账单|短信|信息|message|mms|sms|微信|wechat|alipay|支付宝|美团|京东|淘宝|拼多多|抖音|快手|滴滴|携程|12306""",
        RegexOption.IGNORE_CASE
    )

    fun querySelectableApps(
        context: Context,
        extraPackages: Collection<String> = emptyList()
    ): List<InstalledAppEntry> {
        val pm = context.packageManager
        val selfPkg = context.packageName
        val entries = LinkedHashMap<String, InstalledAppEntry>()
        val smsDiscovered = HashSet<String>()

        fun queryIntentPackages(intent: Intent): List<String> {
            return runCatching {
                val resolves = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(intent, 0)
                }
                resolves.mapNotNull { it.activityInfo?.packageName?.trim()?.takeIf(String::isNotEmpty) }
            }.getOrDefault(emptyList())
        }

        // 1) SMS / Messaging apps (via Play-policy-compliant <queries> intent filters)
        val smsIntents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
        )
        smsIntents.forEach { intent ->
            queryIntentPackages(intent).forEach { pkg ->
                if (pkg != selfPkg) smsDiscovered.add(pkg)
            }
        }

        // 2) All launchable apps (via ACTION_MAIN / CATEGORY_LAUNCHER)
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidatePackages = LinkedHashSet<String>()
        candidatePackages.addAll(smsDiscovered)
        candidatePackages.addAll(queryIntentPackages(launcherIntent))
        candidatePackages.addAll(ChinaPaymentCatalog.watchedPackageMap.keys)
        candidatePackages.addAll(ChinaPaymentCatalog.smsPackageNames)
        candidatePackages.addAll(extraPackages.map { it.trim() }.filter { it.isNotEmpty() })
        candidatePackages.remove(selfPkg)

        for (pkg in candidatePackages) {
            val appInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, 0)
                }
            }.getOrNull()

            if (appInfo == null) {
                // Keep explicitly selected custom packages visible even if not installed or hidden by OEM policy
                if (pkg in extraPackages) {
                    val catalogName = ChinaPaymentCatalog.watchedPackageMap[pkg]?.name
                    val isSms = ChinaPaymentCatalog.isSmsPackage(pkg)
                    entries[pkg] = InstalledAppEntry(
                        packageName = pkg,
                        label = catalogName ?: if (isSms) "系统短信 ($pkg)" else pkg,
                        isSystemApp = isSms,
                        isRecommendedFinancial = catalogName != null || isSms || recommendedKeywordRegex.containsMatchIn(pkg),
                        isSmsApp = isSms,
                        isInstalled = false
                    )
                }
                continue
            }

            val rawLabel = runCatching { pm.getApplicationLabel(appInfo)?.toString()?.trim() }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: ChinaPaymentCatalog.watchedPackageMap[pkg]?.name
                ?: pkg
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            val isSms = pkg in smsDiscovered || ChinaPaymentCatalog.isSmsPackage(pkg)
            val isRecommended = isSms ||
                ChinaPaymentCatalog.isWatchedPackage(pkg) ||
                recommendedKeywordRegex.containsMatchIn(rawLabel) ||
                recommendedKeywordRegex.containsMatchIn(pkg)

            entries[pkg] = InstalledAppEntry(
                packageName = pkg,
                label = rawLabel,
                isSystemApp = isSystem,
                isRecommendedFinancial = isRecommended,
                isSmsApp = isSms,
                isInstalled = true
            )
        }

        return entries.values.sortedWith(
            compareByDescending<InstalledAppEntry> { it.isRecommendedFinancial }
                .thenByDescending { it.isSmsApp }
                .thenByDescending { !it.isSystemApp }
                .thenBy { it.label.lowercase(Locale.ROOT) }
        )
    }

    fun resolveAppLabel(context: Context, packageName: String): String {
        ChinaPaymentCatalog.watchedPackageMap[packageName]?.let { return it.name }
        val pm = context.packageManager
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
        }.getOrNull()
        val label = info?.let { runCatching { pm.getApplicationLabel(it)?.toString()?.trim() }.getOrNull() }
        if (!label.isNullOrBlank()) return label
        return if (ChinaPaymentCatalog.isSmsPackage(packageName)) "短信" else packageName
    }

    /**
     * Decodes an application icon into a compact [sizePx]x[sizePx] bitmap (~36 KB at 96px)
     * so UI pickers stay crisp without bloating memory.
     */
    fun loadCompactAppIcon(context: Context, packageName: String, sizePx: Int = 96): ImageBitmap? {
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val dim = sizePx.coerceIn(48, 128)
            val bitmap = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, dim, dim)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        }.getOrNull()
    }
}
