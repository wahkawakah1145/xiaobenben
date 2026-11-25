package com.example.util

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.R
import com.example.model.AppIconStyle
import kotlin.math.roundToInt

enum class ShortcutPermissionState {
    GRANTED,
    DENIED,
    UNSUPPORTED
}

object DesktopIconSyncManager {
    private const val ALIAS_EMERALD = "com.example.LauncherEmerald"
    private const val ALIAS_MINIMAL = "com.example.LauncherMinimal"
    private const val ALIAS_INK = "com.example.LauncherInk"
    private const val ALIAS_CYBER = "com.example.LauncherCyber"
    private const val ALIAS_VINTAGE = "com.example.LauncherVintage"
    private const val ALIAS_MONET = "com.example.LauncherMonet"

    private const val MIUI_OP_INSTALL_SHORTCUT = 10017

    private val allAliases = listOf(
        ALIAS_EMERALD,
        ALIAS_MINIMAL,
        ALIAS_INK,
        ALIAS_CYBER,
        ALIAS_VINTAGE,
        ALIAS_MONET
    )

    data class AdaptiveIconResources(
        @DrawableRes val backgroundRes: Int,
        @DrawableRes val foregroundRes: Int
    )

    fun adaptiveIconResourcesFor(style: AppIconStyle): AdaptiveIconResources = when (style) {
        AppIconStyle.EMERALD -> AdaptiveIconResources(
            backgroundRes = R.drawable.ic_launcher_background,
            foregroundRes = R.drawable.ic_launcher_foreground
        )
        AppIconStyle.MINIMAL -> AdaptiveIconResources(
            backgroundRes = R.drawable.ic_launcher_bg_minimal,
            foregroundRes = R.drawable.ic_launcher_fg_minimal
        )
        AppIconStyle.INK -> AdaptiveIconResources(
            backgroundRes = R.drawable.ic_launcher_bg_ink,
            foregroundRes = R.drawable.ic_launcher_fg_ink
        )
        AppIconStyle.CYBER -> AdaptiveIconResources(
            backgroundRes = R.drawable.ic_launcher_bg_cyber,
            foregroundRes = R.drawable.ic_launcher_fg_cyber
        )
        AppIconStyle.VINTAGE -> AdaptiveIconResources(
            backgroundRes = R.drawable.ic_launcher_bg_vintage,
            foregroundRes = R.drawable.ic_launcher_fg_vintage
        )
        AppIconStyle.MONET_ADAPTIVE -> AdaptiveIconResources(
            backgroundRes = R.drawable.ic_launcher_bg_monet,
            foregroundRes = R.drawable.ic_launcher_fg_monet
        )
    }

    /**
     * Builds a lightweight configuration-scoped Context matching [isDark] so vector drawables
     * resolve the exact same light/night color tokens used by the Android launcher.
     */
    fun createThemedIconContext(context: Context, isDark: Boolean): Context {
        val baseConfig = context.resources.configuration
        val targetNight = if (isDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val currentNight = baseConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (currentNight == targetNight) return context
        val config = Configuration(baseConfig).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or targetNight
        }
        return runCatching { context.createConfigurationContext(config) }.getOrDefault(context)
    }

    /**
     * Draws the exact adaptive launcher background + foreground vector layers onto [canvas]
     * using Android's standard 72dp/108dp safe-zone crop (-25%..+125%) for pixel-accurate parity
     * between desktop launcher icons and in-app previews.
     */
    fun drawAdaptiveIconLayers(
        context: Context,
        style: AppIconStyle,
        isDark: Boolean,
        canvas: Canvas,
        widthPx: Float,
        heightPx: Float
    ): Boolean {
        if (widthPx <= 0f || heightPx <= 0f) return false
        val themedCtx = createThemedIconContext(context, isDark)
        val resIds = adaptiveIconResourcesFor(style)
        val bgDrawable = runCatching { ContextCompat.getDrawable(themedCtx, resIds.backgroundRes)?.mutate() }.getOrNull()
        val fgDrawable = runCatching { ContextCompat.getDrawable(themedCtx, resIds.foregroundRes)?.mutate() }.getOrNull()
        if (bgDrawable == null && fgDrawable == null) return false

        // Standard Android adaptive icon viewport is 108dp while the masked icon shows the inner 72dp (108 / 72 = 1.5x).
        val left = (-widthPx * 0.25f).roundToInt()
        val top = (-heightPx * 0.25f).roundToInt()
        val right = (widthPx * 1.25f).roundToInt()
        val bottom = (heightPx * 1.25f).roundToInt()

        bgDrawable?.let {
            it.setBounds(left, top, right, bottom)
            it.draw(canvas)
        }
        fgDrawable?.let {
            it.setBounds(left, top, right, bottom)
            it.draw(canvas)
        }
        return true
    }

    /**
     * Renders the unified adaptive launcher vector resources directly to a rounded-square [Bitmap].
     */
    fun renderAdaptiveResourceBitmap(
        context: Context,
        styleId: String,
        isDark: Boolean,
        sizePx: Int = 192,
        cornerRadiusRatio: Float = 0.24f
    ): Bitmap {
        val targetSize = sizePx.coerceIn(64, 512)
        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = targetSize.toFloat()
        val clipPath = Path().apply {
            val radius = w * cornerRadiusRatio.coerceIn(0f, 0.5f)
            addRoundRect(RectF(0f, 0f, w, w), radius, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)
        val drawn = drawAdaptiveIconLayers(
            context = context,
            style = AppIconStyle.fromId(styleId),
            isDark = isDark,
            canvas = canvas,
            widthPx = w,
            heightPx = w
        )
        canvas.restore()
        if (!drawn) {
            return renderThemedSvgBitmap(
                styleId = styleId,
                primaryArgb = 0xFF086350.toInt(),
                primaryContainerArgb = 0xFFC2F0DF.toInt(),
                onPrimaryArgb = 0xFFFFFFFF.toInt(),
                tertiaryArgb = 0xFF0A7C6E.toInt(),
                sizePx = targetSize
            )
        }
        return bitmap
    }

    fun aliasClassNameFor(style: AppIconStyle): String = when (style) {
        AppIconStyle.EMERALD -> ALIAS_EMERALD
        AppIconStyle.MINIMAL -> ALIAS_MINIMAL
        AppIconStyle.INK -> ALIAS_INK
        AppIconStyle.CYBER -> ALIAS_CYBER
        AppIconStyle.VINTAGE -> ALIAS_VINTAGE
        AppIconStyle.MONET_ADAPTIVE -> ALIAS_MONET
    }

    /**
     * Switches the enabled launcher <activity-alias>.
     * Caller should confirm with the user before calling this and [restartApp].
     */
    fun applyLauncherIconAlias(
        context: Context,
        style: AppIconStyle
    ): Boolean {
        val targetAlias = aliasClassNameFor(style)
        val pm = context.packageManager
        val pkg = context.packageName
        return runCatching {
            allAliases.forEach { aliasName ->
                val state = if (aliasName == targetAlias) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, aliasName),
                    state,
                    PackageManager.DONT_KILL_APP
                )
            }
            true
        }.getOrDefault(false)
    }

    fun restartApp(context: Context) {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        context.startActivity(launchIntent)
        Process.killProcess(Process.myPid())
    }

    /**
     * Inspects whether the system launcher supports pinning shortcuts and whether OEM permission
     * (e.g. HyperOS/MIUI OP_INSTALL_SHORTCUT) is currently granted.
     */
    fun checkPinShortcutPermission(context: Context): ShortcutPermissionState {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return ShortcutPermissionState.UNSUPPORTED
        }
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return ShortcutPermissionState.GRANTED
        val uid = Process.myUid()
        val pkg = context.packageName

        // Check MIUI / HyperOS custom AppOps code (10017 = OP_INSTALL_SHORTCUT) if available
        val oemMode = runCatching {
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            method.invoke(appOps, MIUI_OP_INSTALL_SHORTCUT, uid, pkg) as? Int
        }.getOrNull()

        if (oemMode == AppOpsManager.MODE_IGNORED || oemMode == AppOpsManager.MODE_ERRORED || oemMode == 5) {
            return ShortcutPermissionState.DENIED
        }

        // Also check standard string op if exposed by OEM ROM
        val stringOpMode = runCatching {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow("android:install_shortcut", uid, pkg)
        }.getOrNull()
        if (stringOpMode == AppOpsManager.MODE_IGNORED || stringOpMode == AppOpsManager.MODE_ERRORED) {
            return ShortcutPermissionState.DENIED
        }

        return ShortcutPermissionState.GRANTED
    }

    /**
     * Opens the system permission screen where the user can enable "桌面快捷方式" (Pin Shortcut).
     */
    fun openShortcutPermissionSettings(context: Context): Boolean {
        val pkg = context.packageName
        val candidateIntents = listOf(
            // Xiaomi HyperOS / MIUI app permission editor (contains "桌面快捷方式")
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            // Fallback to Android App Details Settings
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", pkg, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        for (intent in candidateIntents) {
            val ok = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    /**
     * Renders the unified minimalist SVG/vector icon geometry tinted with active Monet or custom colors.
     */
    fun renderThemedSvgBitmap(
        styleId: String,
        primaryArgb: Int,
        primaryContainerArgb: Int,
        onPrimaryArgb: Int,
        tertiaryArgb: Int,
        sizePx: Int = 192
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = sizePx.toFloat()
        val style = AppIconStyle.fromId(styleId)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            this.strokeCap = Paint.Cap.ROUND
            this.strokeJoin = Paint.Join.ROUND
        }

        val radius = w * 0.24f
        val bgRect = RectF(0f, 0f, w, w)

        fun drawYen(cx: Float, cy: Float, scale: Float, color: Int, strokeW: Float) {
            strokePaint.color = color
            strokePaint.strokeWidth = strokeW
            val arm = scale * 0.34f
            canvas.drawLine(cx - arm, cy - scale * 0.36f, cx, cy - scale * 0.04f, strokePaint)
            canvas.drawLine(cx + arm, cy - scale * 0.36f, cx, cy - scale * 0.04f, strokePaint)
            canvas.drawLine(cx, cy - scale * 0.04f, cx, cy + scale * 0.38f, strokePaint)
            canvas.drawLine(cx - arm * 0.92f, cy + scale * 0.06f, cx + arm * 0.92f, cy + scale * 0.06f, strokePaint)
            canvas.drawLine(cx - arm * 0.92f, cy + scale * 0.22f, cx + arm * 0.92f, cy + scale * 0.22f, strokePaint)
        }

        when (style) {
            AppIconStyle.EMERALD -> {
                fillPaint.color = primaryArgb
                canvas.drawRoundRect(bgRect, radius, radius, fillPaint)
                fillPaint.color = onPrimaryArgb
                val book = RectF(w * 0.27f, w * 0.22f, w * 0.73f, w * 0.78f)
                canvas.drawRoundRect(book, w * 0.09f, w * 0.09f, fillPaint)
                drawYen(w * 0.52f, w * 0.50f, w * 0.28f, primaryArgb, w * 0.045f)
            }
            AppIconStyle.MINIMAL -> {
                fillPaint.color = primaryContainerArgb
                canvas.drawRoundRect(bgRect, radius, radius, fillPaint)
                strokePaint.color = primaryArgb
                strokePaint.strokeWidth = w * 0.048f
                val path = Path().apply {
                    moveTo(w * 0.29f, w * 0.23f)
                    lineTo(w * 0.60f, w * 0.23f)
                    lineTo(w * 0.72f, w * 0.35f)
                    lineTo(w * 0.72f, w * 0.77f)
                    lineTo(w * 0.29f, w * 0.77f)
                    close()
                }
                canvas.drawPath(path, strokePaint)
                drawYen(w * 0.505f, w * 0.535f, w * 0.24f, primaryArgb, w * 0.042f)
            }
            AppIconStyle.INK -> {
                val darkWash = ColorUtils.blendARGB(primaryArgb, 0xFF132220.toInt(), 0.58f)
                fillPaint.color = darkWash
                canvas.drawRoundRect(bgRect, radius, radius, fillPaint)
                fillPaint.color = primaryContainerArgb
                canvas.drawCircle(w * 0.65f, w * 0.34f, w * 0.075f, fillPaint)
                strokePaint.color = ColorUtils.blendARGB(primaryContainerArgb, 0xFFFFFFFF.toInt(), 0.45f)
                strokePaint.strokeWidth = w * 0.05f
                val ridge = Path().apply {
                    moveTo(w * 0.20f, w * 0.68f)
                    lineTo(w * 0.39f, w * 0.44f)
                    lineTo(w * 0.54f, w * 0.60f)
                    lineTo(w * 0.64f, w * 0.49f)
                    lineTo(w * 0.80f, w * 0.68f)
                }
                canvas.drawPath(ridge, strokePaint)
            }
            AppIconStyle.CYBER -> {
                val slate = ColorUtils.blendARGB(primaryArgb, 0xFF0F172A.toInt(), 0.76f)
                fillPaint.color = slate
                canvas.drawRoundRect(bgRect, radius, radius, fillPaint)
                val bright = ColorUtils.blendARGB(primaryArgb, 0xFFFFFFFF.toInt(), 0.32f)
                strokePaint.color = bright
                strokePaint.strokeWidth = w * 0.044f
                val hex = Path().apply {
                    moveTo(w * 0.50f, w * 0.20f)
                    lineTo(w * 0.76f, w * 0.35f)
                    lineTo(w * 0.76f, w * 0.65f)
                    lineTo(w * 0.50f, w * 0.80f)
                    lineTo(w * 0.24f, w * 0.65f)
                    lineTo(w * 0.24f, w * 0.35f)
                    close()
                }
                canvas.drawPath(hex, strokePaint)
                drawYen(w * 0.50f, w * 0.50f, w * 0.24f, tertiaryArgb, w * 0.042f)
            }
            AppIconStyle.VINTAGE -> {
                val warmLeather = ColorUtils.blendARGB(primaryArgb, 0xFF5D4037.toInt(), 0.52f)
                fillPaint.color = warmLeather
                canvas.drawRoundRect(bgRect, radius, radius, fillPaint)
                strokePaint.color = 0xFFF5ECD8.toInt()
                strokePaint.strokeWidth = w * 0.045f
                val cover = RectF(w * 0.26f, w * 0.23f, w * 0.74f, w * 0.77f)
                canvas.drawRoundRect(cover, w * 0.07f, w * 0.07f, strokePaint)
                canvas.drawLine(w * 0.37f, w * 0.23f, w * 0.37f, w * 0.77f, strokePaint)
                fillPaint.color = ColorUtils.blendARGB(tertiaryArgb, 0xFFE5B96E.toInt(), 0.45f)
                val clasp = RectF(w * 0.57f, w * 0.44f, w * 0.79f, w * 0.56f)
                canvas.drawRoundRect(clasp, w * 0.04f, w * 0.04f, fillPaint)
            }
            AppIconStyle.MONET_ADAPTIVE -> {
                fillPaint.color = primaryContainerArgb
                canvas.drawRoundRect(bgRect, radius, radius, fillPaint)
                fillPaint.color = primaryArgb
                val wallet = RectF(w * 0.24f, w * 0.30f, w * 0.76f, w * 0.70f)
                canvas.drawRoundRect(wallet, w * 0.11f, w * 0.11f, fillPaint)
                fillPaint.color = primaryContainerArgb
                canvas.drawCircle(w * 0.63f, w * 0.50f, w * 0.055f, fillPaint)
            }
        }
        return bitmap
    }

    /**
     * Center-crops and scales any [Bitmap] into a rounded square shortcut icon of [sizePx] x [sizePx].
     */
    fun cropCenterSquareBitmap(
        source: Bitmap,
        sizePx: Int = 192
    ): Bitmap {
        val targetSize = sizePx.coerceIn(64, 512)
        val srcW = source.width.coerceAtLeast(1)
        val srcH = source.height.coerceAtLeast(1)
        val cropSide = minOf(srcW, srcH)
        val left = (srcW - cropSide) / 2
        val top = (srcH - cropSide) / 2
        val srcRect = Rect(left, top, left + cropSide, top + cropSide)

        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val clipPath = Path().apply {
            val radius = targetSize * 0.22f
            addRoundRect(RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat()), radius, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, srcRect, Rect(0, 0, targetSize, targetSize), paint)
        canvas.restore()
        return output
    }

    /**
     * Safely decodes a user-selected gallery image [uri] using subsampling to prevent OOM,
     * then center-crops into a launcher-ready square bitmap.
     */
    fun decodeSquareShortcutBitmap(
        context: Context,
        uri: Uri,
        sizePx: Int = 192
    ): Bitmap? {
        val targetSize = sizePx.coerceIn(64, 512)
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            val minDim = minOf(bounds.outWidth, bounds.outHeight)
            while (minDim / (sampleSize * 2) >= targetSize) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            val cropped = cropCenterSquareBitmap(decoded, targetSize)
            if (cropped !== decoded && !decoded.isRecycled) {
                decoded.recycle()
            }
            cropped
        }.getOrNull()
    }

    /**
     * Requests pinning a launcher shortcut with fully customizable label, icon style, icon colors,
     * or a user-selected gallery image bitmap.
     * Returns (success, statusMessage).
     */
    fun pinCustomDesktopShortcut(
        context: Context,
        customName: String,
        styleId: String,
        primaryColor: Color,
        primaryContainerColor: Color,
        onPrimaryColor: Color,
        tertiaryColor: Color,
        customImageBitmap: Bitmap? = null,
        shortcutIdSuffix: String? = null,
        useAdaptiveResource: Boolean = false,
        isDarkTheme: Boolean = false
    ): Pair<Boolean, String> {
        val label = customName.trim().ifBlank { "小本本" }
        val style = AppIconStyle.fromId(styleId)
        val bitmap = customImageBitmap?.let { cropCenterSquareBitmap(it, 192) }
            ?: if (useAdaptiveResource) {
                renderAdaptiveResourceBitmap(
                    context = context,
                    styleId = style.id,
                    isDark = isDarkTheme,
                    sizePx = 192
                )
            } else {
                renderThemedSvgBitmap(
                    styleId = style.id,
                    primaryArgb = primaryColor.toArgb(),
                    primaryContainerArgb = primaryContainerColor.toArgb(),
                    onPrimaryArgb = onPrimaryColor.toArgb(),
                    tertiaryArgb = tertiaryColor.toArgb(),
                    sizePx = 192
                )
            }
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return false to "当前系统桌面不支持直接创建快捷方式，请检查桌面权限设置"
        }
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
        }
        val shortcutId = buildString {
            append("xiao_ben_ben_custom_")
            if (shortcutIdSuffix != null) {
                append(shortcutIdSuffix)
            } else if (customImageBitmap != null) {
                append("photo_${label.hashCode().toUInt()}_${System.currentTimeMillis()}")
            } else {
                append("${style.id}_${label.hashCode().toUInt()}_${(primaryColor.toArgb() and 0xFFFFFF).toString(16)}")
            }
        }
        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(label.take(18))
            .setLongLabel(label.take(28))
            .setIcon(IconCompat.createWithBitmap(bitmap))
            .setIntent(launchIntent)
            .build()

        val requested = ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        return if (requested) {
            true to "已向桌面发起创建「$label」快捷方式请求，如未弹出请在系统权限中允许「桌面快捷方式」"
        } else {
            false to "未能发起桌面快捷方式请求，请授予桌面快捷方式权限后重试"
        }
    }
}
