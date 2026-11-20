package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight bundled Material-style vector icons replacing the ~18MB
 * `material-icons-extended` dependency to dramatically reduce APK size.
 */
object AppIcons {

    private fun icon(
        name: String,
        autoMirror: Boolean = false,
        block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror
    ).apply(block).build()

    private val fill = SolidColor(Color.Black)

    val PieChart: ImageVector by lazy {
        icon("PieChart") {
            path(fill = fill) {
                moveTo(11f, 2f)
                verticalLineToRelative(20f)
                curveToRelative(-5.07f, -0.5f, -9f, -4.79f, -9f, -10f)
                reflectiveCurveToRelative(3.93f, -9.5f, 9f, -10f)
                close()
                moveTo(13.03f, 2f)
                verticalLineToRelative(8.99f)
                horizontalLineTo(22f)
                curveToRelative(-0.47f, -4.74f, -4.24f, -8.52f, -8.97f, -8.99f)
                close()
                moveTo(13.03f, 13.01f)
                verticalLineTo(22f)
                curveToRelative(4.74f, -0.47f, 8.5f, -4.25f, 8.97f, -8.99f)
                horizontalLineToRelative(-8.97f)
                close()
            }
        }
    }

    val PieChartOutlined: ImageVector by lazy {
        icon("PieChartOutlined") {
            path(fill = fill) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(13f, 4.07f)
                curveToRelative(3.61f, 0.45f, 6.48f, 3.32f, 6.93f, 6.93f)
                horizontalLineTo(13f)
                verticalLineTo(4.07f)
                close()
                moveTo(4f, 12f)
                curveToRelative(0f, -4.06f, 3.07f, -7.44f, 7f, -7.93f)
                verticalLineToRelative(15.87f)
                curveToRelative(-3.93f, -0.5f, -7f, -3.88f, -7f, -7.94f)
                close()
                moveTo(13f, 19.93f)
                verticalLineTo(13f)
                horizontalLineToRelative(6.93f)
                curveToRelative(-0.45f, 3.61f, -3.32f, 6.48f, -6.93f, 6.93f)
                close()
            }
        }
    }

    val ReceiptLong: ImageVector by lazy {
        icon("ReceiptLong") {
            path(fill = fill) {
                moveTo(19.5f, 3.5f)
                lineTo(18f, 2f)
                lineToRelative(-1.5f, 1.5f)
                lineTo(15f, 2f)
                lineToRelative(-1.5f, 1.5f)
                lineTo(12f, 2f)
                lineToRelative(-1.5f, 1.5f)
                lineTo(9f, 2f)
                lineTo(7.5f, 3.5f)
                lineTo(6f, 2f)
                verticalLineToRelative(14f)
                horizontalLineTo(3f)
                verticalLineToRelative(3f)
                curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
                horizontalLineToRelative(12f)
                curveToRelative(1.66f, 0f, 3f, -1.34f, 3f, -3f)
                verticalLineTo(2f)
                lineToRelative(-1.5f, 1.5f)
                close()
                moveTo(15f, 20f)
                horizontalLineTo(6f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                verticalLineToRelative(-1f)
                horizontalLineToRelative(10f)
                verticalLineToRelative(2f)
                close()
                moveTo(19f, 19f)
                curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
                reflectiveCurveToRelative(-1f, -0.45f, -1f, -1f)
                verticalLineToRelative(-3f)
                horizontalLineTo(8f)
                verticalLineTo(5f)
                horizontalLineToRelative(11f)
                verticalLineToRelative(14f)
                close()
            }
            path(fill = fill) {
                moveTo(9f, 7f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(2f)
                horizontalLineTo(9f)
                close()
                moveTo(16f, 7f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-2f)
                close()
                moveTo(9f, 10f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(2f)
                horizontalLineTo(9f)
                close()
                moveTo(16f, 10f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-2f)
                close()
            }
        }
    }

    val ReceiptLongOutlined: ImageVector by lazy { ReceiptLong }

    val Rule: ImageVector by lazy {
        icon("Rule", autoMirror = true) {
            path(fill = fill) {
                moveTo(16.54f, 11f)
                lineTo(13f, 7.46f)
                lineToRelative(1.41f, -1.41f)
                lineToRelative(2.12f, 2.12f)
                lineToRelative(4.24f, -4.24f)
                lineToRelative(1.41f, 1.41f)
                lineTo(16.54f, 11f)
                close()
                moveTo(11f, 7f)
                horizontalLineTo(2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(9f)
                verticalLineTo(7f)
                close()
                moveTo(21f, 13.41f)
                lineTo(19.59f, 12f)
                lineTo(17f, 14.59f)
                lineTo(14.41f, 12f)
                lineTo(13f, 13.41f)
                lineTo(15.59f, 16f)
                lineTo(13f, 18.59f)
                lineTo(14.41f, 20f)
                lineTo(17f, 17.41f)
                lineTo(19.59f, 20f)
                lineTo(21f, 18.59f)
                lineTo(18.41f, 16f)
                lineTo(21f, 13.41f)
                close()
                moveTo(11f, 15f)
                horizontalLineTo(2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(9f)
                verticalLineToRelative(-2f)
                close()
            }
        }
    }

    val OpenInNew: ImageVector by lazy {
        icon("OpenInNew", autoMirror = true) {
            path(fill = fill) {
                moveTo(19f, 19f)
                horizontalLineTo(5f)
                verticalLineTo(5f)
                horizontalLineToRelative(7f)
                verticalLineTo(3f)
                horizontalLineTo(5f)
                curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineToRelative(-7f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(7f)
                close()
                moveTo(14f, 3f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(3.59f)
                lineToRelative(-9.83f, 9.83f)
                lineToRelative(1.41f, 1.41f)
                lineTo(19f, 6.41f)
                verticalLineTo(10f)
                horizontalLineToRelative(2f)
                verticalLineTo(3f)
                horizontalLineToRelative(-7f)
                close()
            }
        }
    }

    val AccountBalance: ImageVector by lazy {
        icon("AccountBalance") {
            path(fill = fill) {
                moveTo(4f, 10f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(7f)
                horizontalLineTo(4f)
                close()
                moveTo(10.5f, 10f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(7f)
                horizontalLineToRelative(-3f)
                close()
                moveTo(2f, 19f)
                horizontalLineToRelative(20f)
                verticalLineToRelative(3f)
                horizontalLineTo(2f)
                close()
                moveTo(17f, 10f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(7f)
                horizontalLineToRelative(-3f)
                close()
                moveTo(12f, 1f)
                lineTo(2f, 6f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(20f)
                verticalLineTo(6f)
                close()
            }
        }
    }

    val AccountBalanceWallet: ImageVector by lazy {
        icon("AccountBalanceWallet") {
            path(fill = fill) {
                moveTo(21f, 18f)
                verticalLineToRelative(1f)
                curveToRelative(0f, 1.1f, -0.9f, 2f, -2f, 2f)
                horizontalLineTo(5f)
                curveToRelative(-1.11f, 0f, -2f, -0.9f, -2f, -2f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, 0.89f, -2f, 2f, -2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, 0.9f, 2f, 2f)
                verticalLineToRelative(1f)
                horizontalLineToRelative(-9f)
                curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(8f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
                horizontalLineToRelative(9f)
                close()
                moveTo(12f, 16f)
                horizontalLineToRelative(10f)
                verticalLineTo(8f)
                horizontalLineTo(12f)
                verticalLineToRelative(8f)
                close()
                moveTo(16f, 13.5f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveToRelative(0.67f, -1.5f, 1.5f, -1.5f)
                reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
                reflectiveCurveToRelative(-0.67f, 1.5f, -1.5f, 1.5f)
                close()
            }
        }
    }

    val AccessibilityNew: ImageVector by lazy {
        icon("AccessibilityNew") {
            path(fill = fill) {
                moveTo(20.5f, 6f)
                curveToRelative(-2.61f, 0.7f, -5.67f, 1f, -8.5f, 1f)
                reflectiveCurveToRelative(-5.89f, -0.3f, -8.5f, -1f)
                lineTo(3f, 8f)
                curveToRelative(1.86f, 0.5f, 4f, 0.83f, 6f, 1f)
                verticalLineToRelative(13f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-6f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(2f)
                verticalLineTo(9f)
                curveToRelative(2f, -0.17f, 4.14f, -0.5f, 6f, -1f)
                lineToRelative(-0.5f, -2f)
                close()
                moveTo(12f, 6f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                close()
            }
        }
    }

    val AutoAwesome: ImageVector by lazy {
        icon("AutoAwesome") {
            path(fill = fill) {
                moveTo(19f, 9f)
                lineToRelative(1.25f, -2.75f)
                lineTo(23f, 5f)
                lineToRelative(-2.75f, -1.25f)
                lineTo(19f, 1f)
                lineToRelative(-1.25f, 2.75f)
                lineTo(15f, 5f)
                lineToRelative(2.75f, 1.25f)
                lineTo(19f, 9f)
                close()
                moveTo(11.5f, 9.5f)
                lineTo(9f, 4f)
                lineTo(6.5f, 9.5f)
                lineTo(1f, 12f)
                lineToRelative(5.5f, 2.5f)
                lineTo(9f, 20f)
                lineToRelative(2.5f, -5.5f)
                lineTo(17f, 12f)
                lineToRelative(-5.5f, -2.5f)
                close()
                moveTo(19f, 15f)
                lineToRelative(-1.25f, 2.75f)
                lineTo(15f, 19f)
                lineToRelative(2.75f, 1.25f)
                lineTo(19f, 23f)
                lineToRelative(1.25f, -2.75f)
                lineTo(23f, 19f)
                lineToRelative(-2.75f, -1.25f)
                lineTo(19f, 15f)
                close()
            }
        }
    }

    val AutoFixHigh: ImageVector by lazy {
        icon("AutoFixHigh") {
            path(fill = fill) {
                moveTo(7.5f, 5.6f)
                lineTo(10f, 7f)
                lineTo(8.6f, 4.5f)
                lineTo(10f, 2f)
                lineTo(7.5f, 3.4f)
                lineTo(5f, 2f)
                lineToRelative(1.4f, 2.5f)
                lineTo(5f, 7f)
                close()
                moveTo(19.5f, 15.4f)
                lineTo(17f, 14f)
                lineToRelative(1.4f, 2.5f)
                lineTo(17f, 19f)
                lineToRelative(2.5f, -1.4f)
                lineTo(22f, 19f)
                lineToRelative(-1.4f, -2.5f)
                lineTo(22f, 14f)
                close()
                moveTo(22f, 2f)
                lineToRelative(-2.5f, 1.4f)
                lineTo(17f, 2f)
                lineToRelative(1.4f, 2.5f)
                lineTo(17f, 7f)
                lineToRelative(2.5f, -1.4f)
                lineTo(22f, 7f)
                lineToRelative(-1.4f, -2.5f)
                close()
                moveTo(14.37f, 7.29f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                lineTo(1.29f, 18.96f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
                lineToRelative(2.34f, 2.34f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                lineTo(16.7f, 11.05f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                lineToRelative(-2.33f, -2.35f)
                close()
                moveTo(13.34f, 12.78f)
                lineToRelative(-2.12f, -2.12f)
                lineToRelative(2.44f, -2.44f)
                lineToRelative(2.12f, 2.12f)
                lineToRelative(-2.44f, 2.44f)
                close()
            }
        }
    }

    val BarChart: ImageVector by lazy {
        icon("BarChart") {
            path(fill = fill) {
                moveTo(4f, 9f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(11f)
                horizontalLineTo(4f)
                close()
                moveTo(16f, 13f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(7f)
                horizontalLineToRelative(-4f)
                close()
                moveTo(10f, 4f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(16f)
                horizontalLineToRelative(-4f)
                close()
            }
        }
    }

    val BatteryChargingFull: ImageVector by lazy {
        icon("BatteryChargingFull") {
            path(fill = fill) {
                moveTo(15.67f, 4f)
                horizontalLineTo(14f)
                verticalLineTo(2f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(2f)
                horizontalLineTo(8.33f)
                curveTo(7.6f, 4f, 7f, 4.6f, 7f, 5.33f)
                verticalLineToRelative(15.33f)
                curveTo(7f, 21.4f, 7.6f, 22f, 8.33f, 22f)
                horizontalLineToRelative(7.33f)
                curveToRelative(0.74f, 0f, 1.34f, -0.6f, 1.34f, -1.33f)
                verticalLineTo(5.33f)
                curveTo(17f, 4.6f, 16.4f, 4f, 15.67f, 4f)
                close()
                moveTo(11f, 20f)
                verticalLineToRelative(-5.5f)
                horizontalLineTo(9f)
                lineTo(13f, 7f)
                verticalLineToRelative(5.5f)
                horizontalLineToRelative(2f)
                lineTo(11f, 20f)
                close()
            }
        }
    }

    val Bolt: ImageVector by lazy {
        icon("Bolt") {
            path(fill = fill) {
                moveTo(11f, 21f)
                horizontalLineToRelative(-1f)
                lineToRelative(1f, -7f)
                horizontalLineTo(7.5f)
                curveToRelative(-0.88f, 0f, -0.33f, -0.75f, -0.31f, -0.78f)
                curveTo(8.48f, 10.94f, 10.42f, 7.54f, 13.01f, 3f)
                horizontalLineToRelative(1f)
                lineToRelative(-1f, 7f)
                horizontalLineToRelative(3.51f)
                curveToRelative(0.4f, 0f, 0.62f, 0.19f, 0.4f, 0.66f)
                curveTo(12.97f, 17.55f, 11f, 21f, 11f, 21f)
                close()
            }
        }
    }

    val CardGiftcard: ImageVector by lazy {
        icon("CardGiftcard") {
            path(fill = fill) {
                moveTo(20f, 6f)
                horizontalLineToRelative(-2.18f)
                curveToRelative(0.11f, -0.31f, 0.18f, -0.65f, 0.18f, -1f)
                curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
                curveToRelative(-1.05f, 0f, -1.96f, 0.54f, -2.5f, 1.35f)
                lineToRelative(-0.5f, 0.67f)
                lineToRelative(-0.5f, -0.68f)
                curveTo(10.96f, 2.54f, 10.05f, 2f, 9f, 2f)
                curveTo(7.34f, 2f, 6f, 3.34f, 6f, 5f)
                curveToRelative(0f, 0.35f, 0.07f, 0.69f, 0.18f, 1f)
                horizontalLineTo(4f)
                curveToRelative(-1.11f, 0f, -1.99f, 0.89f, -1.99f, 2f)
                lineTo(2f, 19f)
                curveToRelative(0f, 1.11f, 0.89f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.11f, 0f, 2f, -0.89f, 2f, -2f)
                verticalLineTo(8f)
                curveToRelative(0f, -1.11f, -0.89f, -2f, -2f, -2f)
                close()
                moveTo(15f, 4f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                reflectiveCurveToRelative(-1f, -0.45f, -1f, -1f)
                reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
                close()
                moveTo(9f, 4f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                reflectiveCurveToRelative(-1f, -0.45f, -1f, -1f)
                reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
                close()
                moveTo(20f, 19f)
                horizontalLineTo(4f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(2f)
                close()
                moveTo(20f, 14f)
                horizontalLineTo(4f)
                verticalLineTo(8f)
                horizontalLineToRelative(5.08f)
                lineTo(7f, 10.83f)
                lineTo(8.62f, 12f)
                lineTo(12f, 7.4f)
                lineToRelative(3.38f, 4.6f)
                lineTo(17f, 10.83f)
                lineTo(14.92f, 8f)
                horizontalLineTo(20f)
                verticalLineToRelative(6f)
                close()
            }
        }
    }

    val CleaningServices: ImageVector by lazy {
        icon("CleaningServices") {
            path(fill = fill) {
                moveTo(16f, 11f)
                horizontalLineToRelative(-1f)
                verticalLineTo(3f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineToRelative(-2f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(8f)
                horizontalLineTo(8f)
                curveToRelative(-2.76f, 0f, -5f, 2.24f, -5f, 5f)
                verticalLineToRelative(7f)
                horizontalLineToRelative(18f)
                verticalLineToRelative(-7f)
                curveToRelative(0f, -2.76f, -2.24f, -5f, -5f, -5f)
                close()
                moveTo(11f, 3f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(8f)
                horizontalLineToRelative(-2f)
                verticalLineTo(3f)
                close()
                moveTo(19f, 21f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-3f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-3f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
                verticalLineToRelative(3f)
                horizontalLineTo(9f)
                verticalLineToRelative(-3f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
                verticalLineToRelative(3f)
                horizontalLineTo(5f)
                verticalLineToRelative(-5f)
                curveToRelative(0f, -1.65f, 1.35f, -3f, 3f, -3f)
                horizontalLineToRelative(8f)
                curveToRelative(1.65f, 0f, 3f, 1.35f, 3f, 3f)
                verticalLineToRelative(5f)
                close()
            }
        }
    }

    val CloudDownload: ImageVector by lazy {
        icon("CloudDownload") {
            path(fill = fill) {
                moveTo(19.35f, 10.04f)
                curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
                curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
                curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
                curveToRelative(0f, 3.31f, 2.69f, 6f, 6f, 6f)
                horizontalLineToRelative(13f)
                curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
                curveToRelative(0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                close()
                moveTo(17f, 13f)
                lineToRelative(-5f, 5f)
                lineToRelative(-5f, -5f)
                horizontalLineToRelative(3f)
                verticalLineTo(9f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(3f)
                close()
            }
        }
    }

    val CloudUpload: ImageVector by lazy {
        icon("CloudUpload") {
            path(fill = fill) {
                moveTo(19.35f, 10.04f)
                curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
                curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
                curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
                curveToRelative(0f, 3.31f, 2.69f, 6f, 6f, 6f)
                horizontalLineToRelative(13f)
                curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
                curveToRelative(0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                close()
                moveTo(14f, 13f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(-4f)
                horizontalLineTo(7f)
                lineToRelative(5f, -5f)
                lineToRelative(5f, 5f)
                horizontalLineToRelative(-3f)
                close()
            }
        }
    }

    val Code: ImageVector by lazy {
        icon("Code") {
            path(fill = fill) {
                moveTo(9.4f, 16.6f)
                lineTo(4.8f, 12f)
                lineToRelative(4.6f, -4.6f)
                lineTo(8f, 6f)
                lineToRelative(-6f, 6f)
                lineToRelative(6f, 6f)
                lineToRelative(1.4f, -1.4f)
                close()
                moveTo(14.6f, 16.6f)
                lineToRelative(4.6f, -4.6f)
                lineToRelative(-4.6f, -4.6f)
                lineTo(16f, 6f)
                lineToRelative(6f, 6f)
                lineToRelative(-6f, 6f)
                lineToRelative(-1.4f, -1.4f)
                close()
            }
        }
    }

    val CurrencyExchange: ImageVector by lazy {
        icon("CurrencyExchange") {
            path(fill = fill) {
                moveTo(12.89f, 11.1f)
                curveToRelative(-1.78f, -0.59f, -2.64f, -0.96f, -2.64f, -1.9f)
                curveToRelative(0f, -1.02f, 1.11f, -1.39f, 1.81f, -1.39f)
                curveToRelative(1.31f, 0f, 1.79f, 0.99f, 1.9f, 1.34f)
                lineToRelative(1.58f, -0.67f)
                curveToRelative(-0.15f, -0.44f, -0.82f, -1.91f, -2.66f, -2.23f)
                verticalLineTo(5f)
                horizontalLineToRelative(-1.75f)
                verticalLineToRelative(1.26f)
                curveToRelative(-2.6f, 0.56f, -2.62f, 2.85f, -2.62f, 2.96f)
                curveToRelative(0f, 2.27f, 2.25f, 2.91f, 3.35f, 3.31f)
                curveToRelative(1.58f, 0.56f, 2.28f, 1.07f, 2.28f, 2.03f)
                curveToRelative(0f, 1.13f, -1.05f, 1.61f, -1.98f, 1.61f)
                curveToRelative(-1.82f, 0f, -2.34f, -1.87f, -2.4f, -2.09f)
                lineTo(8.1f, 14.75f)
                curveToRelative(0.63f, 2.19f, 2.28f, 2.78f, 3.02f, 2.96f)
                verticalLineTo(19f)
                horizontalLineToRelative(1.75f)
                verticalLineToRelative(-1.24f)
                curveToRelative(0.52f, -0.09f, 3.02f, -0.59f, 3.02f, -3.22f)
                curveToRelative(0.01f, -1.39f, -0.6f, -2.61f, -3f, -3.44f)
                close()
                moveTo(3f, 12f)
                curveToRelative(0f, -2.61f, 1.67f, -4.83f, 4f, -5.65f)
                verticalLineTo(4.26f)
                curveTo(3.55f, 5.15f, 1f, 8.27f, 1f, 12f)
                curveToRelative(0f, 3.55f, 2.31f, 6.56f, 5.5f, 7.61f)
                verticalLineToRelative(-2.13f)
                curveTo(4.42f, 16.53f, 3f, 14.44f, 3f, 12f)
                close()
                moveTo(17f, 4.26f)
                verticalLineToRelative(2.09f)
                curveToRelative(2.33f, 0.82f, 4f, 3.04f, 4f, 5.65f)
                curveToRelative(0f, 2.44f, -1.42f, 4.53f, -3.5f, 5.48f)
                verticalLineToRelative(2.13f)
                curveTo(20.69f, 18.56f, 23f, 15.55f, 23f, 12f)
                curveToRelative(0f, -3.73f, -2.55f, -6.85f, -6f, -7.74f)
                close()
            }
        }
    }

    val DeleteOutline: ImageVector by lazy {
        icon("DeleteOutline") {
            path(fill = fill) {
                moveTo(6f, 19f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(8f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                horizontalLineTo(6f)
                verticalLineToRelative(12f)
                close()
                moveTo(8f, 9f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(10f)
                horizontalLineTo(8f)
                verticalLineTo(9f)
                close()
                moveTo(15.5f, 4f)
                lineToRelative(-1f, -1f)
                horizontalLineToRelative(-5f)
                lineToRelative(-1f, 1f)
                horizontalLineTo(5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(14f)
                verticalLineTo(4f)
                close()
            }
        }
    }

    val Description: ImageVector by lazy {
        icon("Description") {
            path(fill = fill) {
                moveTo(14f, 2f)
                horizontalLineTo(6f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(4f, 20f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 1.99f, 2f)
                horizontalLineTo(18f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8f)
                lineToRelative(-6f, -6f)
                close()
                moveTo(16f, 18f)
                horizontalLineTo(8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(2f)
                close()
                moveTo(16f, 14f)
                horizontalLineTo(8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(2f)
                close()
                moveTo(13f, 9f)
                verticalLineTo(3.5f)
                lineTo(18.5f, 9f)
                horizontalLineTo(13f)
                close()
            }
        }
    }

    val DirectionsCar: ImageVector by lazy {
        icon("DirectionsCar") {
            path(fill = fill) {
                moveTo(18.92f, 6.01f)
                curveTo(18.72f, 5.42f, 18.16f, 5f, 17.5f, 5f)
                horizontalLineToRelative(-11f)
                curveToRelative(-0.66f, 0f, -1.21f, 0.42f, -1.42f, 1.01f)
                lineTo(3f, 12f)
                verticalLineToRelative(8f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(1f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                verticalLineToRelative(-1f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(1f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(1f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                verticalLineToRelative(-8f)
                lineToRelative(-2.08f, -5.99f)
                close()
                moveTo(6.5f, 16f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(5.67f, 13f, 6.5f, 13f)
                reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
                reflectiveCurveTo(7.33f, 16f, 6.5f, 16f)
                close()
                moveTo(17.5f, 16f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveToRelative(0.67f, -1.5f, 1.5f, -1.5f)
                reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
                reflectiveCurveToRelative(-0.67f, 1.5f, -1.5f, 1.5f)
                close()
                moveTo(5f, 11f)
                lineToRelative(1.5f, -4.5f)
                horizontalLineToRelative(11f)
                lineTo(19f, 11f)
                horizontalLineTo(5f)
                close()
            }
        }
    }

    val DoneAll: ImageVector by lazy {
        icon("DoneAll") {
            path(fill = fill) {
                moveTo(18f, 7f)
                lineToRelative(-1.41f, -1.41f)
                lineToRelative(-6.34f, 6.34f)
                lineToRelative(1.41f, 1.41f)
                lineTo(18f, 7f)
                close()
                moveTo(22.24f, 5.59f)
                lineTo(11.66f, 16.17f)
                lineTo(7.48f, 12f)
                lineToRelative(-1.41f, 1.41f)
                lineTo(11.66f, 19f)
                lineToRelative(12f, -12f)
                lineToRelative(-1.42f, -1.41f)
                close()
                moveTo(0.41f, 13.41f)
                lineTo(6f, 19f)
                lineToRelative(1.41f, -1.41f)
                lineTo(1.83f, 12f)
                lineTo(0.41f, 13.41f)
                close()
            }
        }
    }

    val Download: ImageVector by lazy {
        icon("Download") {
            path(fill = fill) {
                moveTo(5f, 20f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-2f)
                horizontalLineTo(5f)
                verticalLineToRelative(2f)
                close()
                moveTo(19f, 9f)
                horizontalLineToRelative(-4f)
                verticalLineTo(3f)
                horizontalLineTo(9f)
                verticalLineToRelative(6f)
                horizontalLineTo(5f)
                lineToRelative(7f, 7f)
                lineToRelative(7f, -7f)
                close()
            }
        }
    }

    val FileOpen: ImageVector by lazy {
        icon("FileOpen") {
            path(fill = fill) {
                moveTo(14f, 2f)
                horizontalLineTo(6f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(16f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 1.99f, 2f)
                horizontalLineTo(15f)
                verticalLineToRelative(-8f)
                horizontalLineToRelative(5f)
                verticalLineTo(8f)
                lineToRelative(-6f, -6f)
                close()
                moveTo(13f, 9f)
                verticalLineTo(3.5f)
                lineTo(18.5f, 9f)
                horizontalLineTo(13f)
                close()
                moveTo(17f, 21.66f)
                verticalLineTo(16f)
                horizontalLineToRelative(5.66f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-2.24f)
                lineToRelative(2.95f, 2.95f)
                lineToRelative(-1.41f, 1.41f)
                lineTo(19f, 19.41f)
                verticalLineToRelative(2.24f)
                horizontalLineToRelative(-2f)
                close()
            }
        }
    }

    val FolderCopy: ImageVector by lazy {
        icon("FolderCopy") {
            path(fill = fill) {
                moveTo(3f, 6f)
                horizontalLineTo(1f)
                verticalLineToRelative(13f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(17f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                verticalLineTo(6f)
                close()
                moveTo(21f, 4f)
                horizontalLineToRelative(-7f)
                lineToRelative(-2f, -2f)
                horizontalLineTo(7f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(5f, 15f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(6f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
            }
        }
    }

    val ForkRight: ImageVector by lazy {
        icon("ForkRight") {
            path(fill = fill) {
                moveTo(14.59f, 15.59f)
                lineTo(16f, 17f)
                lineToRelative(4f, -4f)
                lineToRelative(-4f, -4f)
                lineToRelative(-1.41f, 1.41f)
                lineTo(16.17f, 12f)
                horizontalLineTo(11f)
                curveToRelative(-0.83f, 0f, -1.59f, 0.34f, -2.14f, 0.89f)
                verticalLineTo(6.83f)
                lineToRelative(1.59f, 1.59f)
                lineTo(11.86f, 7f)
                lineTo(7.86f, 3f)
                lineToRelative(-4f, 4f)
                lineToRelative(1.41f, 1.41f)
                lineTo(6.86f, 6.83f)
                verticalLineTo(21f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-5f)
                curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
                horizontalLineToRelative(5.31f)
                lineToRelative(-1.58f, 1.59f)
                close()
            }
        }
    }

    val HelpOutline: ImageVector by lazy {
        icon("HelpOutline") {
            path(fill = fill) {
                moveTo(11f, 18f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                close()
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(12f, 20f)
                curveToRelative(-4.41f, 0f, -8f, -3.59f, -8f, -8f)
                reflectiveCurveToRelative(3.59f, -8f, 8f, -8f)
                reflectiveCurveToRelative(8f, 3.59f, 8f, 8f)
                reflectiveCurveToRelative(-3.59f, 8f, -8f, 8f)
                close()
                moveTo(12f, 6f)
                curveToRelative(-2.21f, 0f, -4f, 1.79f, -4f, 4f)
                horizontalLineToRelative(2f)
                curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
                reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
                curveToRelative(0f, 2f, -3f, 1.75f, -3f, 5f)
                horizontalLineToRelative(2f)
                curveToRelative(0f, -2.25f, 3f, -2.5f, 3f, -5f)
                curveToRelative(0f, -2.21f, -1.79f, -4f, -4f, -4f)
                close()
            }
        }
    }

    val Insights: ImageVector by lazy {
        icon("Insights") {
            path(fill = fill) {
                moveTo(21f, 8f)
                curveToRelative(-1.45f, 0f, -2.26f, 1.44f, -1.93f, 2.51f)
                lineToRelative(-3.55f, 3.56f)
                curveToRelative(-0.3f, -0.09f, -0.74f, -0.09f, -1.04f, 0f)
                lineToRelative(-2.55f, -2.55f)
                curveTo(12.27f, 10.45f, 11.46f, 9f, 10f, 9f)
                curveToRelative(-1.45f, 0f, -2.27f, 1.44f, -1.93f, 2.52f)
                lineToRelative(-4.56f, 4.55f)
                curveTo(2.44f, 15.74f, 1f, 16.55f, 1f, 18f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                curveToRelative(1.45f, 0f, 2.26f, -1.44f, 1.93f, -2.51f)
                lineToRelative(4.55f, -4.56f)
                curveToRelative(0.3f, 0.09f, 0.74f, 0.09f, 1.04f, 0f)
                lineToRelative(2.55f, 2.55f)
                curveTo(12.73f, 16.55f, 13.54f, 18f, 15f, 18f)
                curveToRelative(1.45f, 0f, 2.27f, -1.44f, 1.93f, -2.52f)
                lineToRelative(3.56f, -3.55f)
                curveTo(21.56f, 12.26f, 23f, 11.45f, 23f, 10f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(15f, 9f)
                lineToRelative(0.94f, -2.07f)
                lineTo(18f, 6f)
                lineToRelative(-2.06f, -0.93f)
                lineTo(15f, 3f)
                lineToRelative(-0.92f, 2.07f)
                lineTo(12f, 6f)
                lineToRelative(2.08f, 0.93f)
                close()
                moveTo(3.5f, 11f)
                lineTo(4f, 9f)
                lineToRelative(2f, -0.5f)
                lineTo(4f, 8f)
                lineTo(3.5f, 6f)
                lineTo(3f, 8f)
                lineTo(1f, 8.5f)
                lineTo(3f, 9f)
                close()
            }
        }
    }

    val LocalHospital: ImageVector by lazy {
        icon("LocalHospital") {
            path(fill = fill) {
                moveTo(19f, 3f)
                horizontalLineTo(5f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(3f, 19f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(18f, 14f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(-4f)
                horizontalLineTo(6f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(4f)
                verticalLineTo(6f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(4f)
                close()
            }
        }
    }

    val Movie: ImageVector by lazy {
        icon("Movie") {
            path(fill = fill) {
                moveTo(18f, 4f)
                lineToRelative(2f, 4f)
                horizontalLineToRelative(-3f)
                lineToRelative(-2f, -4f)
                horizontalLineToRelative(-2f)
                lineToRelative(2f, 4f)
                horizontalLineToRelative(-3f)
                lineToRelative(-2f, -4f)
                horizontalLineTo(8f)
                lineToRelative(2f, 4f)
                horizontalLineTo(7f)
                lineTo(5f, 4f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(2f, 18f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(4f)
                horizontalLineToRelative(-4f)
                close()
            }
        }
    }

    val NotificationsActive: ImageVector by lazy {
        icon("NotificationsActive") {
            path(fill = fill) {
                moveTo(7.58f, 4.08f)
                lineTo(6.15f, 2.65f)
                curveTo(3.75f, 4.48f, 2.17f, 7.3f, 2.03f, 10.5f)
                horizontalLineToRelative(2f)
                curveToRelative(0.15f, -2.65f, 1.51f, -4.97f, 3.55f, -6.42f)
                close()
                moveTo(19.97f, 10.5f)
                horizontalLineToRelative(2f)
                curveToRelative(-0.15f, -3.2f, -1.73f, -6.02f, -4.12f, -7.85f)
                lineToRelative(-1.42f, 1.43f)
                curveToRelative(2.02f, 1.45f, 3.39f, 3.77f, 3.54f, 6.42f)
                close()
                moveTo(18f, 11f)
                curveToRelative(0f, -3.07f, -1.64f, -5.64f, -4.5f, -6.32f)
                verticalLineTo(4f)
                curveToRelative(0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f)
                reflectiveCurveToRelative(-1.5f, 0.67f, -1.5f, 1.5f)
                verticalLineToRelative(0.68f)
                curveTo(7.63f, 5.36f, 6f, 7.92f, 6f, 11f)
                verticalLineToRelative(5f)
                lineToRelative(-2f, 2f)
                verticalLineToRelative(1f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(-1f)
                lineToRelative(-2f, -2f)
                verticalLineToRelative(-5f)
                close()
                moveTo(12f, 22f)
                curveToRelative(0.14f, 0f, 0.27f, -0.01f, 0.4f, -0.04f)
                curveToRelative(0.65f, -0.14f, 1.18f, -0.58f, 1.44f, -1.18f)
                curveToRelative(0.1f, -0.24f, 0.15f, -0.5f, 0.15f, -0.78f)
                horizontalLineToRelative(-4f)
                curveToRelative(0.01f, 1.1f, 0.9f, 2f, 2.01f, 2f)
                close()
            }
        }
    }

    val NotificationsOff: ImageVector by lazy {
        icon("NotificationsOff") {
            path(fill = fill) {
                moveTo(20f, 18.69f)
                lineTo(7.84f, 6.14f)
                lineTo(5.27f, 3.49f)
                lineTo(4f, 4.76f)
                lineToRelative(2.8f, 2.8f)
                verticalLineToRelative(0.01f)
                curveToRelative(-0.52f, 0.99f, -0.8f, 2.16f, -0.8f, 3.42f)
                verticalLineToRelative(5f)
                lineToRelative(-2f, 2f)
                verticalLineToRelative(1f)
                horizontalLineToRelative(13.73f)
                lineToRelative(2f, 2f)
                lineTo(21f, 19.72f)
                lineToRelative(-1f, -1.03f)
                close()
                moveTo(12f, 22f)
                curveToRelative(1.11f, 0f, 2f, -0.89f, 2f, -2f)
                horizontalLineToRelative(-4f)
                curveToRelative(0f, 1.11f, 0.89f, 2f, 2f, 2f)
                close()
                moveTo(18f, 14.68f)
                verticalLineTo(11f)
                curveToRelative(0f, -3.08f, -1.64f, -5.64f, -4.5f, -6.32f)
                verticalLineTo(4f)
                curveToRelative(0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f)
                reflectiveCurveToRelative(-1.5f, 0.67f, -1.5f, 1.5f)
                verticalLineToRelative(0.68f)
                curveToRelative(-0.15f, 0.03f, -0.29f, 0.08f, -0.42f, 0.12f)
                lineToRelative(-0.1f, 0.03f)
                lineToRelative(8.02f, 9.85f)
                close()
            }
        }
    }

    val Palette: ImageVector by lazy {
        icon("Palette") {
            path(fill = fill) {
                moveTo(12f, 3f)
                curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                reflectiveCurveToRelative(4.03f, 9f, 9f, 9f)
                curveToRelative(0.83f, 0f, 1.5f, -0.67f, 1.5f, -1.5f)
                curveToRelative(0f, -0.39f, -0.15f, -0.74f, -0.39f, -1.01f)
                curveToRelative(-0.23f, -0.26f, -0.38f, -0.61f, -0.38f, -0.99f)
                curveToRelative(0f, -0.83f, 0.67f, -1.5f, 1.5f, -1.5f)
                horizontalLineTo(16f)
                curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
                curveToRelative(0f, -4.42f, -4.03f, -8f, -9f, -8f)
                close()
                moveTo(6.5f, 12f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(5.67f, 9f, 6.5f, 9f)
                reflectiveCurveTo(8f, 9.67f, 8f, 10.5f)
                reflectiveCurveTo(7.33f, 12f, 6.5f, 12f)
                close()
                moveTo(9.5f, 8f)
                curveTo(8.67f, 8f, 8f, 7.33f, 8f, 6.5f)
                reflectiveCurveTo(8.67f, 5f, 9.5f, 5f)
                reflectiveCurveTo(11f, 5.67f, 11f, 6.5f)
                reflectiveCurveTo(10.33f, 8f, 9.5f, 8f)
                close()
                moveTo(14.5f, 8f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(13.67f, 5f, 14.5f, 5f)
                reflectiveCurveTo(16f, 5.67f, 16f, 6.5f)
                reflectiveCurveTo(15.33f, 8f, 14.5f, 8f)
                close()
                moveTo(17.5f, 12f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(16.67f, 9f, 17.5f, 9f)
                reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
                reflectiveCurveToRelative(-0.67f, 1.5f, -1.5f, 1.5f)
                close()
            }
        }
    }

    val Payments: ImageVector by lazy {
        icon("Payments") {
            path(fill = fill) {
                moveTo(19f, 14f)
                verticalLineTo(6f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineTo(3f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(8f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                close()
                moveTo(10f, 13f)
                curveToRelative(-1.66f, 0f, -3f, -1.34f, -3f, -3f)
                reflectiveCurveToRelative(1.34f, -3f, 3f, -3f)
                reflectiveCurveToRelative(3f, 1.34f, 3f, 3f)
                reflectiveCurveToRelative(-1.34f, 3f, -3f, 3f)
                close()
                moveTo(23f, 7f)
                verticalLineToRelative(11f)
                curveToRelative(0f, 1.1f, -0.9f, 2f, -2f, 2f)
                horizontalLineTo(4f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(17f)
                verticalLineTo(7f)
                horizontalLineToRelative(2f)
                close()
            }
        }
    }

    val PendingActions: ImageVector by lazy {
        icon("PendingActions") {
            path(fill = fill) {
                moveTo(17f, 12f)
                curveToRelative(-2.76f, 0f, -5f, 2.24f, -5f, 5f)
                reflectiveCurveToRelative(2.24f, 5f, 5f, 5f)
                reflectiveCurveToRelative(5f, -2.24f, 5f, -5f)
                reflectiveCurveToRelative(-2.24f, -5f, -5f, -5f)
                close()
                moveTo(18.65f, 19.35f)
                lineToRelative(-2.15f, -2.15f)
                verticalLineTo(14f)
                horizontalLineToRelative(1f)
                verticalLineToRelative(2.79f)
                lineToRelative(1.85f, 1.85f)
                lineToRelative(-0.7f, 0.71f)
                close()
                moveTo(18f, 3f)
                horizontalLineToRelative(-3.18f)
                curveTo(14.4f, 1.84f, 13.3f, 1f, 12f, 1f)
                reflectiveCurveTo(9.6f, 1.84f, 9.18f, 3f)
                horizontalLineTo(6f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(15f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(6.11f)
                curveToRelative(-0.59f, -0.57f, -1.07f, -1.25f, -1.42f, -2f)
                horizontalLineTo(6f)
                verticalLineTo(5f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(8f)
                verticalLineTo(5f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(5.08f)
                curveToRelative(0.71f, 0.1f, 1.38f, 0.31f, 2f, 0.6f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(12f, 5f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
                reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                close()
            }
        }
    }

    val Psychology: ImageVector by lazy {
        icon("Psychology") {
            path(fill = fill) {
                moveTo(13f, 8.57f)
                curveToRelative(-0.79f, 0f, -1.43f, 0.64f, -1.43f, 1.43f)
                reflectiveCurveToRelative(0.64f, 1.43f, 1.43f, 1.43f)
                reflectiveCurveToRelative(1.43f, -0.64f, 1.43f, -1.43f)
                reflectiveCurveToRelative(-0.64f, -1.43f, -1.43f, -1.43f)
                close()
                moveTo(13f, 3f)
                curveTo(9.25f, 3f, 6.2f, 5.94f, 6.02f, 9.64f)
                lineTo(4.1f, 12.2f)
                curveToRelative(-0.25f, 0.33f, -0.01f, 0.8f, 0.4f, 0.8f)
                horizontalLineTo(6f)
                verticalLineToRelative(3f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(1f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(7f)
                verticalLineToRelative(-4.68f)
                curveToRelative(2.36f, -1.12f, 4f, -3.53f, 4f, -6.32f)
                curveToRelative(0f, -3.87f, -3.13f, -7f, -7f, -7f)
                close()
                moveTo(16f, 10f)
                curveToRelative(0f, 1.66f, -1.34f, 3f, -3f, 3f)
                reflectiveCurveToRelative(-3f, -1.34f, -3f, -3f)
                reflectiveCurveToRelative(1.34f, -3f, 3f, -3f)
                reflectiveCurveToRelative(3f, 1.34f, 3f, 3f)
                close()
            }
        }
    }

    val Restaurant: ImageVector by lazy {
        icon("Restaurant") {
            path(fill = fill) {
                moveTo(11f, 9f)
                horizontalLineTo(9f)
                verticalLineTo(2f)
                horizontalLineTo(7f)
                verticalLineToRelative(7f)
                horizontalLineTo(5f)
                verticalLineTo(2f)
                horizontalLineTo(3f)
                verticalLineToRelative(7f)
                curveToRelative(0f, 2.12f, 1.66f, 3.84f, 3.75f, 3.97f)
                verticalLineTo(22f)
                horizontalLineToRelative(2.5f)
                verticalLineToRelative(-9.03f)
                curveTo(11.34f, 12.84f, 13f, 11.12f, 13f, 9f)
                verticalLineTo(2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(7f)
                close()
                moveTo(16f, 6f)
                verticalLineToRelative(8f)
                horizontalLineToRelative(2.5f)
                verticalLineToRelative(8f)
                horizontalLineTo(21f)
                verticalLineTo(2f)
                curveToRelative(-2.76f, 0f, -5f, 2.24f, -5f, 4f)
                close()
            }
        }
    }

    val Restore: ImageVector by lazy {
        icon("Restore") {
            path(fill = fill) {
                moveTo(13f, 3f)
                curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                horizontalLineTo(1f)
                lineToRelative(3.89f, 3.89f)
                lineToRelative(0.07f, 0.14f)
                lineTo(9f, 12f)
                horizontalLineTo(6f)
                curveToRelative(0f, -3.87f, 3.13f, -7f, 7f, -7f)
                reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
                reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f)
                curveToRelative(-1.93f, 0f, -3.68f, -0.79f, -4.94f, -2.06f)
                lineToRelative(-1.42f, 1.42f)
                curveTo(8.27f, 19.99f, 10.51f, 21f, 13f, 21f)
                curveToRelative(4.97f, 0f, 9f, -4.03f, 9f, -9f)
                reflectiveCurveToRelative(-4.03f, -9f, -9f, -9f)
                close()
                moveTo(12f, 8f)
                verticalLineToRelative(5f)
                lineToRelative(4.28f, 2.54f)
                lineToRelative(0.72f, -1.21f)
                lineToRelative(-3.5f, -2.08f)
                verticalLineTo(8f)
                horizontalLineTo(12f)
                close()
            }
        }
    }

    val Save: ImageVector by lazy {
        icon("Save") {
            path(fill = fill) {
                moveTo(17f, 3f)
                horizontalLineTo(5f)
                curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                lineToRelative(-4f, -4f)
                close()
                moveTo(12f, 19f)
                curveToRelative(-1.66f, 0f, -3f, -1.34f, -3f, -3f)
                reflectiveCurveToRelative(1.34f, -3f, 3f, -3f)
                reflectiveCurveToRelative(3f, 1.34f, 3f, 3f)
                reflectiveCurveToRelative(-1.34f, 3f, -3f, 3f)
                close()
                moveTo(15f, 9f)
                horizontalLineTo(5f)
                verticalLineTo(5f)
                horizontalLineToRelative(10f)
                verticalLineToRelative(4f)
                close()
            }
        }
    }

    val Savings: ImageVector by lazy {
        icon("Savings") {
            path(fill = fill) {
                moveTo(19.83f, 7.5f)
                lineToRelative(-2.27f, -0.75f)
                curveToRelative(-0.02f, 0f, -0.04f, -0.01f, -0.06f, -0.01f)
                curveToRelative(-0.37f, -0.9f, -1.03f, -1.66f, -1.87f, -2.14f)
                lineToRelative(-0.27f, -1.78f)
                curveToRelative(-0.1f, -0.57f, -0.61f, -0.95f, -1.17f, -0.82f)
                curveToRelative(-1.11f, 0.25f, -1.96f, 1.14f, -2.19f, 2.24f)
                horizontalLineTo(9.5f)
                curveTo(5.91f, 4.14f, 3f, 7.05f, 3f, 10.64f)
                curveToRelative(0f, 2.38f, 1.29f, 4.46f, 3.21f, 5.58f)
                lineTo(6f, 21f)
                horizontalLineToRelative(3f)
                lineToRelative(0.78f, -2.14f)
                curveToRelative(0.71f, 0.14f, 1.45f, 0.19f, 2.22f, 0.14f)
                lineToRelative(0.78f, 2f)
                horizontalLineToRelative(3f)
                lineToRelative(-0.15f, -3.02f)
                curveToRelative(1.13f, -0.71f, 2.02f, -1.78f, 2.51f, -3.05f)
                lineToRelative(2.14f, -0.71f)
                curveToRelative(0.43f, -0.14f, 0.72f, -0.55f, 0.72f, -1f)
                verticalLineTo(8.5f)
                curveToRelative(0f, -0.45f, -0.29f, -0.86f, -0.72f, -1f)
                horizontalLineToRelative(0.55f)
                close()
                moveTo(15.5f, 10.5f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
                reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                close()
                moveTo(9f, 8.5f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(1.5f)
                horizontalLineTo(9f)
                verticalLineTo(8.5f)
                close()
            }
        }
    }

    val Schedule: ImageVector by lazy {
        icon("Schedule") {
            path(fill = fill) {
                moveTo(11.99f, 2f)
                curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.47f, 10f, 9.99f, 10f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
                close()
                moveTo(12f, 20f)
                curveToRelative(-4.42f, 0f, -8f, -3.58f, -8f, -8f)
                reflectiveCurveToRelative(3.58f, -8f, 8f, -8f)
                reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
                reflectiveCurveToRelative(-3.58f, 8f, -8f, 8f)
                close()
                moveTo(12.5f, 7f)
                horizontalLineTo(11f)
                verticalLineToRelative(6f)
                lineToRelative(5.25f, 3.15f)
                lineToRelative(0.75f, -1.23f)
                lineToRelative(-4.5f, -2.67f)
                close()
            }
        }
    }

    val Security: ImageVector by lazy {
        icon("Security") {
            path(fill = fill) {
                moveTo(12f, 1f)
                lineTo(3f, 5f)
                verticalLineToRelative(6f)
                curveToRelative(0f, 5.55f, 3.84f, 10.74f, 9f, 12f)
                curveToRelative(5.16f, -1.26f, 9f, -6.45f, 9f, -12f)
                verticalLineTo(5f)
                lineToRelative(-9f, -4f)
                close()
                moveTo(12f, 11.99f)
                horizontalLineToRelative(7f)
                curveToRelative(-0.53f, 4.12f, -3.28f, 7.79f, -7f, 8.94f)
                verticalLineTo(12f)
                horizontalLineTo(5f)
                verticalLineTo(6.3f)
                lineToRelative(7f, -3.11f)
                verticalLineToRelative(8.8f)
                close()
            }
        }
    }

    val ShoppingBag: ImageVector by lazy {
        icon("ShoppingBag") {
            path(fill = fill) {
                moveTo(18f, 6f)
                horizontalLineToRelative(-2f)
                curveToRelative(0f, -2.21f, -1.79f, -4f, -4f, -4f)
                reflectiveCurveTo(8f, 3.79f, 8f, 6f)
                horizontalLineTo(6f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(12f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(12f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(10f, 10f)
                curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
                reflectiveCurveToRelative(-1f, -0.45f, -1f, -1f)
                verticalLineTo(8f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
                moveTo(12f, 4f)
                curveToRelative(1.1f, 0f, 2f, 0.9f, 2f, 2f)
                horizontalLineToRelative(-4f)
                curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
                close()
                moveTo(16f, 10f)
                curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
                reflectiveCurveToRelative(-1f, -0.45f, -1f, -1f)
                verticalLineTo(8f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
            }
        }
    }

    val Storage: ImageVector by lazy {
        icon("Storage") {
            path(fill = fill) {
                moveTo(2f, 20f)
                horizontalLineToRelative(20f)
                verticalLineToRelative(-4f)
                horizontalLineTo(2f)
                verticalLineToRelative(4f)
                close()
                moveTo(4f, 17f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineTo(4f)
                verticalLineToRelative(-2f)
                close()
                moveTo(2f, 4f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(20f)
                verticalLineTo(4f)
                horizontalLineTo(2f)
                close()
                moveTo(6f, 7f)
                horizontalLineTo(4f)
                verticalLineTo(5f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
                moveTo(2f, 14f)
                horizontalLineToRelative(20f)
                verticalLineToRelative(-4f)
                horizontalLineTo(2f)
                verticalLineToRelative(4f)
                close()
                moveTo(4f, 11f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineTo(4f)
                verticalLineToRelative(-2f)
                close()
            }
        }
    }

    val Terminal: ImageVector by lazy {
        icon("Terminal") {
            path(fill = fill) {
                moveTo(20f, 4f)
                horizontalLineTo(4f)
                curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(12f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(6f)
                curveToRelative(0f, -1.1f, -0.89f, -2f, -2f, -2f)
                close()
                moveTo(20f, 18f)
                horizontalLineTo(4f)
                verticalLineTo(8f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(10f)
                close()
                moveTo(18f, 17f)
                horizontalLineToRelative(-6f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(2f)
                close()
                moveTo(7.5f, 17f)
                lineToRelative(-1.41f, -1.41f)
                lineTo(8.67f, 13f)
                lineToRelative(-2.59f, -2.59f)
                lineTo(7.5f, 9f)
                lineToRelative(4f, 4f)
                lineToRelative(-4f, 4f)
                close()
            }
        }
    }

    val Tune: ImageVector by lazy {
        icon("Tune") {
            path(fill = fill) {
                moveTo(3f, 17f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                close()
                moveTo(3f, 5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(10f)
                verticalLineTo(5f)
                horizontalLineTo(3f)
                close()
                moveTo(13f, 21f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(2f)
                close()
                moveTo(7f, 9f)
                verticalLineToRelative(2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineTo(9f)
                horizontalLineTo(7f)
                close()
                moveTo(21f, 13f)
                verticalLineToRelative(-2f)
                horizontalLineTo(11f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(10f)
                close()
                moveTo(15f, 9f)
                horizontalLineToRelative(2f)
                verticalLineTo(7f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineToRelative(-4f)
                verticalLineTo(3f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(6f)
                close()
            }
        }
    }

    val Visibility: ImageVector by lazy {
        icon("Visibility") {
            path(fill = fill) {
                moveTo(12f, 4.5f)
                curveTo(7f, 4.5f, 2.73f, 7.61f, 1f, 12f)
                curveToRelative(1.73f, 4.39f, 6f, 7.5f, 11f, 7.5f)
                reflectiveCurveToRelative(9.27f, -3.11f, 11f, -7.5f)
                curveToRelative(-1.73f, -4.39f, -6f, -7.5f, -11f, -7.5f)
                close()
                moveTo(12f, 17f)
                curveToRelative(-2.76f, 0f, -5f, -2.24f, -5f, -5f)
                reflectiveCurveToRelative(2.24f, -5f, 5f, -5f)
                reflectiveCurveToRelative(5f, 2.24f, 5f, 5f)
                reflectiveCurveToRelative(-2.24f, 5f, -5f, 5f)
                close()
                moveTo(12f, 9f)
                curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
                reflectiveCurveToRelative(1.34f, 3f, 3f, 3f)
                reflectiveCurveToRelative(3f, -1.34f, 3f, -3f)
                reflectiveCurveToRelative(-1.34f, -3f, -3f, -3f)
                close()
            }
        }
    }

    val VisibilityOff: ImageVector by lazy {
        icon("VisibilityOff") {
            path(fill = fill) {
                moveTo(12f, 7f)
                curveToRelative(2.76f, 0f, 5f, 2.24f, 5f, 5f)
                curveToRelative(0f, 0.65f, -0.13f, 1.26f, -0.36f, 1.83f)
                lineToRelative(2.92f, 2.92f)
                curveToRelative(1.51f, -1.26f, 2.7f, -2.89f, 3.43f, -4.75f)
                curveToRelative(-1.73f, -4.39f, -6f, -7.5f, -11f, -7.5f)
                curveToRelative(-1.4f, 0f, -2.74f, 0.25f, -3.98f, 0.7f)
                lineToRelative(2.16f, 2.16f)
                curveTo(10.74f, 7.13f, 11.35f, 7f, 12f, 7f)
                close()
                moveTo(2f, 4.27f)
                lineToRelative(2.28f, 2.28f)
                lineToRelative(0.46f, 0.46f)
                curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1f, 12f)
                curveToRelative(1.73f, 4.39f, 6f, 7.5f, 11f, 7.5f)
                curveToRelative(1.55f, 0f, 3.03f, -0.3f, 4.38f, -0.84f)
                lineToRelative(0.42f, 0.42f)
                lineTo(19.73f, 22f)
                lineTo(21f, 20.73f)
                lineTo(3.27f, 3f)
                lineTo(2f, 4.27f)
                close()
                moveTo(7.53f, 9.8f)
                lineToRelative(1.55f, 1.55f)
                curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f)
                curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
                curveToRelative(0.22f, 0f, 0.44f, -0.03f, 0.65f, -0.08f)
                lineToRelative(1.55f, 1.55f)
                curveToRelative(-0.67f, 0.33f, -1.41f, 0.53f, -2.2f, 0.53f)
                curveToRelative(-2.76f, 0f, -5f, -2.24f, -5f, -5f)
                curveToRelative(0f, -0.79f, 0.2f, -1.53f, 0.53f, -2.2f)
                close()
                moveTo(11.84f, 9.02f)
                lineToRelative(3.15f, 3.15f)
                lineToRelative(0.02f, -0.16f)
                curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
                lineToRelative(-0.17f, 0.01f)
                close()
            }
        }
    }

    val WarningAmber: ImageVector by lazy {
        icon("WarningAmber") {
            path(fill = fill, pathFillType = PathFillType.NonZero) {
                moveTo(12f, 5.99f)
                lineTo(19.53f, 19f)
                horizontalLineTo(4.47f)
                lineTo(12f, 5.99f)
                moveTo(12f, 2f)
                lineTo(1f, 21f)
                horizontalLineToRelative(22f)
                lineTo(12f, 2f)
                close()
                moveTo(13f, 16f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2f)
                close()
                moveTo(13f, 10f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-4f)
                close()
            }
        }
    }

    val WifiFind: ImageVector by lazy {
        icon("WifiFind") {
            path(fill = fill) {
                moveTo(11f, 14f)
                curveToRelative(0f, -3.36f, 2.64f, -6f, 6f, -6f)
                curveToRelative(2.2f, 0f, 4.08f, 1.13f, 5.13f, 2.86f)
                lineTo(24f, 8.98f)
                curveTo(20.93f, 5.9f, 16.69f, 4f, 12f, 4f)
                curveTo(7.31f, 4f, 3.07f, 5.9f, 0f, 8.98f)
                lineTo(12f, 21f)
                lineToRelative(1.86f, -1.87f)
                curveTo(12.14f, 18.09f, 11f, 16.2f, 11f, 14f)
                close()
                moveTo(21f, 14f)
                curveToRelative(0f, -2.24f, -1.76f, -4f, -4f, -4f)
                reflectiveCurveToRelative(-4f, 1.76f, -4f, 4f)
                reflectiveCurveToRelative(1.76f, 4f, 4f, 4f)
                curveToRelative(0.75f, 0f, 1.44f, -0.21f, 2.03f, -0.56f)
                lineTo(21.59f, 20f)
                lineTo(23f, 18.59f)
                lineToRelative(-2.56f, -2.56f)
                curveTo(20.79f, 15.44f, 21f, 14.75f, 21f, 14f)
                close()
                moveTo(17f, 16f)
                curveToRelative(-1.12f, 0f, -2f, -0.88f, -2f, -2f)
                reflectiveCurveToRelative(0.88f, -2f, 2f, -2f)
                reflectiveCurveToRelative(2f, 0.88f, 2f, 2f)
                reflectiveCurveToRelative(-0.88f, 2f, -2f, 2f)
                close()
            }
        }
    }

    val ContentCopy: ImageVector by lazy {
        icon("ContentCopy") {
            path(fill = fill) {
                moveTo(16f, 1f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                horizontalLineToRelative(2f)
                verticalLineTo(3f)
                horizontalLineToRelative(12f)
                verticalLineTo(1f)
                close()
                moveTo(19f, 5f)
                horizontalLineTo(8f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(11f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(19f, 21f)
                horizontalLineTo(8f)
                verticalLineTo(7f)
                horizontalLineToRelative(11f)
                verticalLineToRelative(14f)
                close()
            }
        }
    }

    val CalendarMonth: ImageVector by lazy {
        icon("CalendarMonth") {
            path(fill = fill) {
                moveTo(19f, 4f)
                horizontalLineToRelative(-1f)
                verticalLineTo(2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                horizontalLineTo(8f)
                verticalLineTo(2f)
                horizontalLineTo(6f)
                verticalLineToRelative(2f)
                horizontalLineTo(5f)
                curveToRelative(-1.11f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(3f, 20f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(6f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(19f, 20f)
                horizontalLineTo(5f)
                verticalLineTo(10f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(10f)
                close()
                moveTo(9f, 14f)
                horizontalLineTo(7f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
                moveTo(13f, 14f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
                moveTo(17f, 14f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
                moveTo(9f, 18f)
                horizontalLineTo(7f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
                moveTo(13f, 18f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
                moveTo(17f, 18f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
            }
        }
    }

    val MenuBook: ImageVector by lazy {
        icon("MenuBook") {
            path(fill = fill) {
                moveTo(21f, 5f)
                curveToRelative(-1.11f, -0.35f, -2.33f, -0.5f, -3.5f, -0.5f)
                curveToRelative(-1.95f, 0f, -4.05f, 0.4f, -5.5f, 1.5f)
                curveToRelative(-1.45f, -1.1f, -3.55f, -1.5f, -5.5f, -1.5f)
                reflectiveCurveTo(2.45f, 4.9f, 1f, 6f)
                verticalLineToRelative(14.65f)
                curveToRelative(0f, 0.25f, 0.25f, 0.5f, 0.5f, 0.5f)
                curveToRelative(0.1f, 0f, 0.15f, -0.05f, 0.25f, -0.05f)
                curveTo(3.1f, 20.45f, 5.05f, 20f, 6.5f, 20f)
                curveToRelative(1.95f, 0f, 4.05f, 0.4f, 5.5f, 1.5f)
                curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
                curveToRelative(1.65f, 0f, 3.35f, 0.3f, 4.75f, 1.05f)
                curveToRelative(0.1f, 0.05f, 0.15f, 0.05f, 0.25f, 0.05f)
                curveToRelative(0.25f, 0f, 0.5f, -0.25f, 0.5f, -0.5f)
                verticalLineTo(6f)
                curveToRelative(-0.6f, -0.45f, -1.25f, -0.75f, -2f, -1f)
                close()
                moveTo(21f, 18.5f)
                curveToRelative(-1.1f, -0.35f, -2.3f, -0.5f, -3.5f, -0.5f)
                curveToRelative(-1.7f, 0f, -4.15f, 0.65f, -5.5f, 1.5f)
                verticalLineTo(8f)
                curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
                curveToRelative(1.2f, 0f, 2.4f, 0.15f, 3.5f, 0.5f)
                verticalLineToRelative(11.5f)
                close()
            }
        }
    }

    val MenuBookOutlined: ImageVector by lazy { MenuBook }

    val LibraryAdd: ImageVector by lazy {
        icon("LibraryAdd") {
            path(fill = fill) {
                moveTo(4f, 6f)
                horizontalLineTo(2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-2f)
                horizontalLineTo(4f)
                verticalLineTo(6f)
                close()
                moveTo(20f, 2f)
                horizontalLineTo(8f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(12f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(12f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(4f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(19f, 11f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-4f)
                horizontalLineTo(9f)
                verticalLineTo(9f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(2f)
                close()
            }
        }
    }

    val ArrowUpward: ImageVector by lazy {
        icon("ArrowUpward") {
            path(fill = fill) {
                moveTo(4f, 12f)
                lineToRelative(1.41f, 1.41f)
                lineTo(11f, 7.83f)
                verticalLineTo(20f)
                horizontalLineToRelative(2f)
                verticalLineTo(7.83f)
                lineToRelative(5.58f, 5.59f)
                lineTo(20f, 12f)
                lineToRelative(-8f, -8f)
                lineToRelative(-8f, 8f)
                close()
            }
        }
    }

    val ArrowDownward: ImageVector by lazy {
        icon("ArrowDownward") {
            path(fill = fill) {
                moveTo(20f, 12f)
                lineToRelative(-1.41f, -1.41f)
                lineTo(13f, 16.17f)
                verticalLineTo(4f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(12.17f)
                lineToRelative(-5.58f, -5.59f)
                lineTo(4f, 12f)
                lineToRelative(8f, 8f)
                lineToRelative(8f, -8f)
                close()
            }
        }
    }

    val AddToHomeScreen: ImageVector by lazy {
        icon("AddToHomeScreen") {
            path(fill = fill) {
                moveTo(18f, 1.01f)
                lineTo(8f, 1f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(2f)
                verticalLineTo(5f)
                horizontalLineToRelative(10f)
                verticalLineToRelative(14f)
                horizontalLineTo(8f)
                verticalLineToRelative(-1f)
                horizontalLineTo(6f)
                verticalLineToRelative(3f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(10f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(3f)
                curveToRelative(0f, -1.1f, -0.9f, -1.99f, -2f, -1.99f)
                close()
                moveTo(10f, 15f)
                horizontalLineToRelative(2f)
                verticalLineTo(8f)
                horizontalLineTo(5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(3.59f)
                lineTo(3f, 15.59f)
                lineTo(4.41f, 17f)
                lineTo(10f, 11.41f)
                verticalLineTo(15f)
                close()
            }
        }
    }
}
