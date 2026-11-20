package com.example.ui.theme

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
    val priSatMul: Float,
    val secHueShift: Float,
    val secSatMul: Float,
    val terHueShift: Float,
    val terSatMul: Float,
    val neutralSat: Float
)

fun buildCustomColorScheme(
    seedColor: Color,
    style: CustomPaletteStyle,
    darkTheme: Boolean
): ColorScheme {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(seedColor.toArgb(), hsv)
    val baseHue = hsv[0]
    val baseSat = hsv[1].coerceAtLeast(0.18f)

    val (priSatMul, secHueShift, secSatMul, terHueShift, terSatMul, neutralSat) = when (style) {
        CustomPaletteStyle.TONAL_SPOT -> PaletteSpec(0.82f, 18f, 0.34f, 60f, 0.52f, 0.045f)
        CustomPaletteStyle.EXPRESSIVE -> PaletteSpec(0.95f, 120f, 0.58f, 215f, 0.72f, 0.06f)
        CustomPaletteStyle.NEUTRAL -> PaletteSpec(0.32f, 8f, 0.16f, 36f, 0.22f, 0.025f)
        CustomPaletteStyle.VIBRANT -> PaletteSpec(1.00f, 35f, 0.72f, 95f, 0.85f, 0.055f)
        CustomPaletteStyle.MONOCHROME -> PaletteSpec(0.0f, 0f, 0.0f, 0f, 0.0f, 0.0f)
    }

    val pSat = if (style == CustomPaletteStyle.MONOCHROME) 0f else (baseSat * priSatMul).coerceIn(0.12f, 0.96f)
    val sSat = if (style == CustomPaletteStyle.MONOCHROME) 0f else (baseSat * secSatMul).coerceIn(0.08f, 0.82f)
    val tSat = if (style == CustomPaletteStyle.MONOCHROME) 0f else (baseSat * terSatMul).coerceIn(0.10f, 0.88f)
    val nHue = baseHue

    return if (!darkTheme) {
        val primary = hsvColor(baseHue, pSat, if (style == CustomPaletteStyle.MONOCHROME) 0.18f else 0.58f)
        val primaryContainer = hsvColor(baseHue, (pSat * 0.32f).coerceIn(0f, 0.40f), 0.94f)
        val secondary = hsvColor(baseHue + secHueShift, sSat, if (style == CustomPaletteStyle.MONOCHROME) 0.36f else 0.52f)
        val secondaryContainer = hsvColor(baseHue + secHueShift, (sSat * 0.35f).coerceIn(0f, 0.36f), 0.92f)
        val tertiary = hsvColor(baseHue + terHueShift, tSat, if (style == CustomPaletteStyle.MONOCHROME) 0.28f else 0.56f)
        val tertiaryContainer = hsvColor(baseHue + terHueShift, (tSat * 0.34f).coerceIn(0f, 0.38f), 0.93f)

        val bg = hsvColor(nHue, neutralSat, 0.985f)
        val surfaceLow = hsvColor(nHue, neutralSat * 1.15f, 0.96f)
        val surface = hsvColor(nHue, neutralSat * 1.35f, 0.94f)
        val surfaceHigh = hsvColor(nHue, neutralSat * 1.55f, 0.91f)

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
            surfaceContainerLow = surfaceLow,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceHigh,
            outline = hsvColor(nHue, neutralSat * 2f, 0.52f),
            outlineVariant = hsvColor(nHue, neutralSat * 1.8f, 0.80f)
        )
    } else {
        val primary = hsvColor(baseHue, (pSat * 0.65f).coerceIn(0f, 0.72f), if (style == CustomPaletteStyle.MONOCHROME) 0.90f else 0.88f)
        val primaryContainer = hsvColor(baseHue, (pSat * 0.85f).coerceIn(0f, 0.85f), if (style == CustomPaletteStyle.MONOCHROME) 0.26f else 0.34f)
        val secondary = hsvColor(baseHue + secHueShift, (sSat * 0.68f).coerceIn(0f, 0.65f), 0.82f)
        val secondaryContainer = hsvColor(baseHue + secHueShift, (sSat * 0.82f).coerceIn(0f, 0.72f), 0.30f)
        val tertiary = hsvColor(baseHue + terHueShift, (tSat * 0.70f).coerceIn(0f, 0.68f), 0.84f)
        val tertiaryContainer = hsvColor(baseHue + terHueShift, (tSat * 0.82f).coerceIn(0f, 0.75f), 0.32f)

        val bg = hsvColor(nHue, neutralSat * 1.4f, 0.085f)
        val surfaceLow = hsvColor(nHue, neutralSat * 1.5f, 0.115f)
        val surface = hsvColor(nHue, neutralSat * 1.6f, 0.145f)
        val surfaceHigh = hsvColor(nHue, neutralSat * 1.7f, 0.185f)

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
            surfaceContainerLow = surfaceLow,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceHigh,
            outline = hsvColor(nHue, neutralSat * 2f, 0.56f),
            outlineVariant = hsvColor(nHue, neutralSat * 1.8f, 0.28f)
        )
    }
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

    val colorScheme = when {
        appearanceSettings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        appearanceSettings.dynamicColor -> {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
        else -> {
            buildCustomColorScheme(
                seedColor = Color(appearanceSettings.customPrimaryColorArgb.toInt()),
                style = appearanceSettings.customPaletteStyle,
                darkTheme = darkTheme
            )
        }
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
