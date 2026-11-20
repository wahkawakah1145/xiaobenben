package com.example.ui.theme

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import com.example.model.AppearanceSettings
import com.example.model.CustomPaletteStyle
import com.example.model.LatinNumberFont
import com.example.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = JadePrimaryLight,
    onPrimary = OnJadePrimaryLight,
    primaryContainer = JadePrimaryContainerLight,
    onPrimaryContainer = OnJadePrimaryContainerLight,
    secondary = SlateSecondaryLight,
    onSecondary = OnSlateSecondaryLight,
    secondaryContainer = SlateSecondaryContainerLight,
    onSecondaryContainer = OnSlateSecondaryContainerLight,
    tertiary = GoldTertiaryLight,
    onTertiary = OnGoldTertiaryLight,
    tertiaryContainer = GoldTertiaryContainerLight,
    onTertiaryContainer = OnGoldTertiaryContainerLight,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

private val DarkColorScheme = darkColorScheme(
    primary = JadePrimaryDark,
    onPrimary = OnJadePrimaryDark,
    primaryContainer = JadePrimaryContainerDark,
    onPrimaryContainer = OnJadePrimaryContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = OnSlateSecondaryDark,
    secondaryContainer = SlateSecondaryContainerDark,
    onSecondaryContainer = OnSlateSecondaryContainerDark,
    tertiary = GoldTertiaryDark,
    onTertiary = OnGoldTertiaryDark,
    tertiaryContainer = GoldTertiaryContainerDark,
    onTertiaryContainer = OnGoldTertiaryContainerDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

private fun hsvColor(hue: Float, saturation: Float, value: Float): Color {
    val wrappedHue = ((hue % 360f) + 360f) % 360f
    return Color(
        AndroidColor.HSVToColor(
            floatArrayOf(wrappedHue, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
        )
    )
}

private fun contrastingOn(bg: Color): Color {
    return if (bg.luminance() > 0.42f) Color(0xFF101413) else Color(0xFFFFFFFF)
}

private data class PaletteSpec(
    val priHueShift: Float,
    val priSatTarget: Float,
    val priSatMul: Float,
    val secHueShift: Float,
    val secSatTarget: Float,
    val terHueShift: Float,
    val terSatTarget: Float,
    val neutralHueShift: Float,
    val neutralSat: Float,
    val containerSatBoost: Float
)

fun buildCustomColorScheme(
    seedColor: Color,
    style: CustomPaletteStyle,
    darkTheme: Boolean
): ColorScheme {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(seedColor.toArgb(), hsv)
    val baseHue = hsv[0]
    val rawSat = hsv[1]
    val effectiveSeedSat = rawSat.coerceAtLeast(0.45f)

    val spec = when (style) {
        CustomPaletteStyle.TONAL_SPOT -> PaletteSpec(
            priHueShift = 0f,
            priSatTarget = 0.62f,
            priSatMul = 0.85f,
            secHueShift = 18f,
            secSatTarget = 0.28f,
            terHueShift = 60f,
            terSatTarget = 0.46f,
            neutralHueShift = 0f,
            neutralSat = 0.04f,
            containerSatBoost = 0.30f
        )
        CustomPaletteStyle.EXPRESSIVE -> PaletteSpec(
            priHueShift = 22f,
            priSatTarget = 0.80f,
            priSatMul = 1.15f,
            secHueShift = 140f,
            secSatTarget = 0.68f,
            terHueShift = 235f,
            terSatTarget = 0.78f,
            neutralHueShift = 28f,
            neutralSat = 0.095f,
            containerSatBoost = 0.48f
        )
        CustomPaletteStyle.NEUTRAL -> PaletteSpec(
            priHueShift = 0f,
            priSatTarget = 0.16f,
            priSatMul = 0.24f,
            secHueShift = 8f,
            secSatTarget = 0.10f,
            terHueShift = 30f,
            terSatTarget = 0.16f,
            neutralHueShift = 0f,
            neutralSat = 0.014f,
            containerSatBoost = 0.14f
        )
        CustomPaletteStyle.VIBRANT -> PaletteSpec(
            priHueShift = -8f,
            priSatTarget = 0.94f,
            priSatMul = 1.35f,
            secHueShift = 38f,
            secSatTarget = 0.82f,
            terHueShift = 95f,
            terSatTarget = 0.88f,
            neutralHueShift = -6f,
            neutralSat = 0.075f,
            containerSatBoost = 0.56f
        )
        CustomPaletteStyle.MONOCHROME -> PaletteSpec(
            priHueShift = 0f,
            priSatTarget = 0f,
            priSatMul = 0f,
            secHueShift = 0f,
            secSatTarget = 0f,
            terHueShift = 0f,
            terSatTarget = 0f,
            neutralHueShift = 0f,
            neutralSat = 0f,
            containerSatBoost = 0f
        )
    }

    val pHue = baseHue + spec.priHueShift
    val nHue = baseHue + spec.neutralHueShift

    val pSat = when (style) {
        CustomPaletteStyle.MONOCHROME -> 0f
        CustomPaletteStyle.NEUTRAL -> (effectiveSeedSat * spec.priSatMul).coerceIn(0.08f, 0.20f)
        CustomPaletteStyle.VIBRANT -> maxOf(spec.priSatTarget, (effectiveSeedSat * spec.priSatMul)).coerceIn(0.84f, 0.98f)
        CustomPaletteStyle.EXPRESSIVE -> maxOf(spec.priSatTarget, (effectiveSeedSat * spec.priSatMul)).coerceIn(0.70f, 0.94f)
        CustomPaletteStyle.TONAL_SPOT -> (effectiveSeedSat * spec.priSatMul).coerceIn(0.38f, 0.82f)
    }
    val sSat = if (style == CustomPaletteStyle.MONOCHROME) 0f else spec.secSatTarget
    val tSat = if (style == CustomPaletteStyle.MONOCHROME) 0f else spec.terSatTarget

    return if (!darkTheme) {
        val primaryVal = when (style) {
            CustomPaletteStyle.MONOCHROME -> 0.14f
            CustomPaletteStyle.NEUTRAL -> 0.42f
            CustomPaletteStyle.VIBRANT -> 0.68f
            CustomPaletteStyle.EXPRESSIVE -> 0.62f
            CustomPaletteStyle.TONAL_SPOT -> 0.54f
        }
        val primary = hsvColor(pHue, pSat, primaryVal)
        val primaryContainer = hsvColor(
            pHue,
            if (style == CustomPaletteStyle.MONOCHROME) 0f else (pSat * spec.containerSatBoost).coerceIn(0.06f, 0.55f),
            if (style == CustomPaletteStyle.MONOCHROME) 0.88f else 0.94f
        )
        val secondary = hsvColor(baseHue + spec.secHueShift, sSat, if (style == CustomPaletteStyle.MONOCHROME) 0.34f else 0.52f)
        val secondaryContainer = hsvColor(
            baseHue + spec.secHueShift,
            if (style == CustomPaletteStyle.MONOCHROME) 0f else (sSat * 0.40f).coerceIn(0.06f, 0.45f),
            0.91f
        )
        val tertiary = hsvColor(baseHue + spec.terHueShift, tSat, if (style == CustomPaletteStyle.MONOCHROME) 0.26f else 0.56f)
        val tertiaryContainer = hsvColor(
            baseHue + spec.terHueShift,
            if (style == CustomPaletteStyle.MONOCHROME) 0f else (tSat * 0.42f).coerceIn(0.06f, 0.48f),
            0.92f
        )

        val bg = hsvColor(nHue, spec.neutralSat, 0.985f)
        val surfaceLow = hsvColor(nHue, spec.neutralSat * 1.25f, 0.96f)
        val surface = hsvColor(nHue, spec.neutralSat * 1.45f, 0.935f)
        val surfaceHigh = hsvColor(nHue, spec.neutralSat * 1.65f, 0.905f)

        lightColorScheme(
            primary = primary,
            onPrimary = contrastingOn(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = contrastingOn(primaryContainer),
            secondary = secondary,
            onSecondary = contrastingOn(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = contrastingOn(secondaryContainer),
            tertiary = tertiary,
            onTertiary = contrastingOn(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = contrastingOn(tertiaryContainer),
            background = bg,
            onBackground = Color(0xFF151918),
            surface = bg,
            onSurface = Color(0xFF151918),
            surfaceVariant = surface,
            onSurfaceVariant = Color(0xFF434B48),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = surfaceLow,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceHigh,
            surfaceContainerHighest = hsvColor(nHue, spec.neutralSat * 1.8f, 0.875f),
            outline = hsvColor(nHue, spec.neutralSat * 2f, 0.52f),
            outlineVariant = hsvColor(nHue, spec.neutralSat * 1.8f, 0.80f)
        )
    } else {
        val darkPriSat = when (style) {
            CustomPaletteStyle.MONOCHROME -> 0f
            CustomPaletteStyle.NEUTRAL -> pSat.coerceIn(0.06f, 0.16f)
            CustomPaletteStyle.VIBRANT -> (pSat * 0.88f).coerceIn(0.70f, 0.90f)
            CustomPaletteStyle.EXPRESSIVE -> (pSat * 0.80f).coerceIn(0.58f, 0.82f)
            CustomPaletteStyle.TONAL_SPOT -> (pSat * 0.65f).coerceIn(0.28f, 0.68f)
        }
        val darkPriVal = when (style) {
            CustomPaletteStyle.MONOCHROME -> 0.94f
            CustomPaletteStyle.VIBRANT -> 0.96f
            CustomPaletteStyle.EXPRESSIVE -> 0.92f
            CustomPaletteStyle.NEUTRAL -> 0.82f
            CustomPaletteStyle.TONAL_SPOT -> 0.88f
        }
        val primary = hsvColor(pHue, darkPriSat, darkPriVal)
        val primaryContainer = hsvColor(
            pHue,
            if (style == CustomPaletteStyle.MONOCHROME) 0f else (pSat * 0.92f).coerceIn(0.14f, 0.92f),
            when (style) {
                CustomPaletteStyle.MONOCHROME -> 0.24f
                CustomPaletteStyle.VIBRANT -> 0.44f
                CustomPaletteStyle.EXPRESSIVE -> 0.40f
                else -> 0.34f
            }
        )
        val secondary = hsvColor(
            baseHue + spec.secHueShift,
            if (style == CustomPaletteStyle.MONOCHROME) 0f else (sSat * 0.78f).coerceIn(0.08f, 0.78f),
            0.85f
        )
        val secondaryContainer = hsvColor(
            baseHue + spec.secHueShift,
            if (style == CustomPaletteStyle.MONOCHROME) 0f else (sSat * 0.86f).coerceIn(0.10f, 0.82f),
            0.32f
        )
        val tertiary = hsvColor(
            baseHue + spec.terHueShift,
            if (style == CustomPaletteStyle.MONOCHROME) 0f else (tSat * 0.80f).coerceIn(0.08f, 0.82f),
            0.88f
        )
        val tertiaryContainer = hsvColor(
            baseHue + spec.terHueShift,
            if (style == CustomPaletteStyle.MONOCHROME) 0f else (tSat * 0.86f).coerceIn(0.10f, 0.84f),
            0.34f
        )

        val bg = hsvColor(nHue, spec.neutralSat * 1.6f, 0.085f)
        val surfaceLow = hsvColor(nHue, spec.neutralSat * 1.75f, 0.115f)
        val surface = hsvColor(nHue, spec.neutralSat * 1.9f, 0.145f)
        val surfaceHigh = hsvColor(nHue, spec.neutralSat * 2.0f, 0.185f)

        darkColorScheme(
            primary = primary,
            onPrimary = contrastingOn(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = contrastingOn(primaryContainer),
            secondary = secondary,
            onSecondary = contrastingOn(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = contrastingOn(secondaryContainer),
            tertiary = tertiary,
            onTertiary = contrastingOn(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = contrastingOn(tertiaryContainer),
            background = bg,
            onBackground = Color(0xFFE3E8E6),
            surface = bg,
            onSurface = Color(0xFFE3E8E6),
            surfaceVariant = surface,
            onSurfaceVariant = Color(0xFFBAC3BF),
            surfaceContainerLowest = hsvColor(nHue, spec.neutralSat * 1.4f, 0.06f),
            surfaceContainerLow = surfaceLow,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceHigh,
            surfaceContainerHighest = hsvColor(nHue, spec.neutralSat * 2.1f, 0.225f),
            outline = hsvColor(nHue, spec.neutralSat * 2f, 0.56f),
            outlineVariant = hsvColor(nHue, spec.neutralSat * 1.8f, 0.28f)
        )
    }
}

fun resolveDynamicSeedColor(context: Context, fallbackPrimary: Color): Color {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val raw = runCatching {
            ContextCompat.getColor(context, android.R.color.system_accent1_500)
        }.getOrNull()
        if (raw != null && raw != 0) {
            return Color(raw)
        }
    }
    return fallbackPrimary
}

fun applyPaletteStyleToDynamicScheme(
    dynamicScheme: ColorScheme,
    style: CustomPaletteStyle,
    darkTheme: Boolean,
    seedOverride: Color? = null
): ColorScheme {
    if (style == CustomPaletteStyle.TONAL_SPOT) return dynamicScheme
    return buildCustomColorScheme(
        seedColor = seedOverride ?: dynamicScheme.primary,
        style = style,
        darkTheme = darkTheme
    )
}

fun applyOledPureBlack(scheme: ColorScheme): ColorScheme {
    return scheme.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainer = Color(0xFF121212),
        surfaceContainerHigh = Color(0xFF1B1B1B),
        surfaceContainerHighest = Color(0xFF242424),
        surfaceVariant = Color(0xFF141414),
        outlineVariant = Color(0xFF2C2C2C)
    )
}

@Composable
fun SmartLedgerTheme(
    appearanceSettings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (appearanceSettings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val baseScheme = when {
        appearanceSettings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val baseDynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            val richSeed = resolveDynamicSeedColor(context, baseDynamic.primary)
            applyPaletteStyleToDynamicScheme(
                dynamicScheme = baseDynamic,
                style = appearanceSettings.customPaletteStyle,
                darkTheme = darkTheme,
                seedOverride = richSeed
            )
        }
        appearanceSettings.dynamicColor -> {
            val fallbackSeed = if (darkTheme) JadePrimaryDark else JadePrimaryLight
            if (appearanceSettings.customPaletteStyle == CustomPaletteStyle.TONAL_SPOT) {
                if (darkTheme) DarkColorScheme else LightColorScheme
            } else {
                buildCustomColorScheme(
                    seedColor = fallbackSeed,
                    style = appearanceSettings.customPaletteStyle,
                    darkTheme = darkTheme
                )
            }
        }
        else -> {
            buildCustomColorScheme(
                seedColor = Color(appearanceSettings.customPrimaryColorArgb.toInt()),
                style = appearanceSettings.customPaletteStyle,
                darkTheme = darkTheme
            )
        }
    }

    val colorScheme = if (darkTheme && appearanceSettings.oledPureBlack) {
        applyOledPureBlack(baseScheme)
    } else {
        baseScheme
    }

    val numberFontFamily = when (appearanceSettings.latinNumberFont) {
        LatinNumberFont.BUILTIN -> JetBrainsMonoFamily
        LatinNumberFont.SYSTEM -> FontFamily.Default
    }
    val typography = buildAppTypography(appearanceSettings.latinNumberFont)
    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density * appearanceSettings.uiDensityScale.coerceIn(0.75f, 1.35f),
        fontScale = baseDensity.fontScale
    )

    CompositionLocalProvider(
        LocalNumberFontFamily provides numberFontFamily,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
