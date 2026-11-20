package com.example.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private val quickPresetSeedColors = listOf(
    0xFF0F6B54L,
    0xFF2563EBL,
    0xFF7C3AEDL,
    0xFFDB2777L,
    0xFFDC2626L,
    0xFFD97706L,
    0xFF0891B2L,
    0xFF334155L
)

private fun argbToHex6(argb: Int): String = "%06X".format(argb and 0xFFFFFF)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomColorPickerDialog(
    initialColorArgb: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val initHsv = remember(initialColorArgb) {
        FloatArray(3).apply {
            AndroidColor.colorToHSV((initialColorArgb.toInt() or (0xFF shl 24)), this)
        }
    }

    var hue by remember { mutableFloatStateOf(initHsv[0]) }
    var sat by remember { mutableFloatStateOf(initHsv[1]) }
    var value by remember { mutableFloatStateOf(initHsv[2]) }

    fun currentArgb(): Int = AndroidColor.HSVToColor(floatArrayOf(hue, sat, value))

    var hexInput by remember { mutableStateOf(argbToHex6(currentArgb())) }

    fun syncFromHsv(h: Float, s: Float, v: Float) {
        hue = h.coerceIn(0f, 360f)
        sat = s.coerceIn(0f, 1f)
        value = v.coerceIn(0f, 1f)
        hexInput = argbToHex6(currentArgb())
    }

    fun syncFromRgb(r: Int, g: Int, b: Int) {
        val rgb = AndroidColor.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(rgb, hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        hexInput = argbToHex6(rgb)
    }

    val liveArgb = currentArgb()
    val liveColor = Color(liveArgb)
    val redVal = AndroidColor.red(liveArgb)
    val greenVal = AndroidColor.green(liveArgb)
    val blueVal = AndroidColor.blue(liveArgb)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp)
                .heightIn(max = 680.dp)
                .testTag("custom_color_picker_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Sticky / Pinned Top Header: Always shows Color Swatch + Editable Hex Input
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "自定义主题调色板",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = liveColor,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("color_picker_preview_swatch")
                        ) {}
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = { raw ->
                                val cleaned = raw.uppercase()
                                    .removePrefix("#")
                                    .filter { it in '0'..'9' || it in 'A'..'F' }
                                    .take(6)
                                hexInput = cleaned
                                if (cleaned.length == 6) {
                                    val parsed = cleaned.toIntOrNull(16)
                                    if (parsed != null) {
                                        val argb = parsed or (0xFF shl 24)
                                        val hsv = FloatArray(3)
                                        AndroidColor.colorToHSV(argb, hsv)
                                        hue = hsv[0]
                                        sat = hsv[1]
                                        value = hsv[2]
                                    }
                                }
                            },
                            label = { Text("HEX 色值（常驻可输入）") },
                            prefix = { Text("#") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Ascii
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("color_picker_hex_input")
                        )
                    }
                }

                HorizontalDivider()

                // Scrollable Wheel + HSV + RGB Controls
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Circular HSV Color Wheel
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        HsvColorWheel(
                            hue = hue,
                            saturation = sat,
                            brightness = value,
                            onHueSatChange = { nextHue, nextSat ->
                                syncFromHsv(nextHue, nextSat, value)
                            },
                            modifier = Modifier
                                .size(216.dp)
                                .testTag("color_picker_wheel")
                        )
                    }

                    // Quick Swatches
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        quickPresetSeedColors.forEach { presetArgb ->
                            val c = Color(presetArgb.toInt())
                            val selected = (liveArgb and 0xFFFFFF) == (presetArgb.toInt() and 0xFFFFFF)
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.7f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        val hsv = FloatArray(3)
                                        AndroidColor.colorToHSV(presetArgb.toInt(), hsv)
                                        syncFromHsv(hsv[0], hsv[1], hsv[2])
                                    }
                            )
                        }
                    }

                    HorizontalDivider()

                    // HSV Sliders & Numeric Inputs
                    Text(
                        text = "HSV 色相 / 饱和度 / 明度",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ChannelSliderInputRow(
                        label = "H°",
                        valueInt = hue.roundToInt(),
                        maxInt = 360,
                        sliderColor = Color.hsv(hue.coerceIn(0f, 360f), 0.9f, 0.95f),
                        testTagPrefix = "color_hsv_h",
                        onChange = { next -> syncFromHsv(next.toFloat(), sat, value) }
                    )
                    ChannelSliderInputRow(
                        label = "S%",
                        valueInt = (sat * 100f).roundToInt(),
                        maxInt = 100,
                        sliderColor = liveColor,
                        testTagPrefix = "color_hsv_s",
                        onChange = { next -> syncFromHsv(hue, next / 100f, value) }
                    )
                    ChannelSliderInputRow(
                        label = "V%",
                        valueInt = (value * 100f).roundToInt(),
                        maxInt = 100,
                        sliderColor = liveColor,
                        testTagPrefix = "color_hsv_v",
                        onChange = { next -> syncFromHsv(hue, sat, next / 100f) }
                    )

                    HorizontalDivider()

                    // RGB Sliders & Numeric Inputs
                    Text(
                        text = "RGB 通道",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ChannelSliderInputRow(
                        label = "R",
                        valueInt = redVal,
                        maxInt = 255,
                        sliderColor = Color(0xFFD94848),
                        testTagPrefix = "color_rgb_r",
                        onChange = { next -> syncFromRgb(next, greenVal, blueVal) }
                    )
                    ChannelSliderInputRow(
                        label = "G",
                        valueInt = greenVal,
                        maxInt = 255,
                        sliderColor = Color(0xFF16A34A),
                        testTagPrefix = "color_rgb_g",
                        onChange = { next -> syncFromRgb(redVal, next, blueVal) }
                    )
                    ChannelSliderInputRow(
                        label = "B",
                        valueInt = blueVal,
                        maxInt = 255,
                        sliderColor = Color(0xFF2563EB),
                        testTagPrefix = "color_rgb_b",
                        onChange = { next -> syncFromRgb(redVal, greenVal, next) }
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val finalArgb = (liveColor.toArgb().toLong() and 0x00FFFFFFL) or 0xFF000000L
                            onConfirm(finalArgb)
                        },
                        modifier = Modifier.testTag("color_picker_confirm_button")
                    ) {
                        Text("应用颜色")
                    }
                }
            }
        }
    }
}

@Composable
private fun HsvColorWheel(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onHueSatChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val hueColors = remember {
        listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
            Color.Red
        )
    }

    BoxWithConstraints(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    fun updateAt(pos: Offset) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val radius = minOf(cx, cy).coerceAtLeast(1f)
                        val dx = pos.x - cx
                        val dy = pos.y - cy
                        val rawAngle = (atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI).toFloat()
                        val nextHue = ((rawAngle % 360f) + 360f) % 360f
                        val nextSat = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
                        onHueSatChange(nextHue, nextSat)
                    }
                    detectTapGestures { offset -> updateAt(offset) }
                }
                .pointerInput(Unit) {
                    fun updateAt(pos: Offset) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val radius = minOf(cx, cy).coerceAtLeast(1f)
                        val dx = pos.x - cx
                        val dy = pos.y - cy
                        val rawAngle = (atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI).toFloat()
                        val nextHue = ((rawAngle % 360f) + 360f) % 360f
                        val nextSat = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
                        onHueSatChange(nextHue, nextSat)
                    }
                    detectDragGestures { change, _ ->
                        change.consume()
                        updateAt(change.position)
                    }
                }
        ) {
            val radius = size.minDimension / 2f - 4.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(
                brush = Brush.sweepGradient(colors = hueColors, center = center),
                radius = radius,
                center = center
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
            if (brightness < 0.99f) {
                drawCircle(
                    color = Color.Black.copy(alpha = (1f - brightness).coerceIn(0f, 0.88f)),
                    radius = radius,
                    center = center
                )
            }
            drawCircle(
                color = Color.Gray.copy(alpha = 0.35f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            val angleRad = hue * PI / 180.0
            val thumbDist = saturation.coerceIn(0f, 1f) * radius
            val thumbCenter = Offset(
                x = center.x + (cos(angleRad) * thumbDist).toFloat(),
                y = center.y + (sin(angleRad) * thumbDist).toFloat()
            )
            val indicatorColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)))
            drawCircle(
                color = indicatorColor,
                radius = 9.dp.toPx(),
                center = thumbCenter
            )
            drawCircle(
                color = Color.White,
                radius = 9.dp.toPx(),
                center = thumbCenter,
                style = Stroke(width = 2.5.dp.toPx())
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.45f),
                radius = 10.5.dp.toPx(),
                center = thumbCenter,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

@Composable
private fun ChannelSliderInputRow(
    label: String,
    valueInt: Int,
    maxInt: Int,
    sliderColor: Color,
    testTagPrefix: String,
    onChange: (Int) -> Unit
) {
    var textValue by remember(valueInt) { mutableStateOf(valueInt.toString()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(26.dp)
        )
        Slider(
            value = valueInt.toFloat().coerceIn(0f, maxInt.toFloat()),
            onValueChange = { raw ->
                val v = raw.roundToInt().coerceIn(0, maxInt)
                textValue = v.toString()
                onChange(v)
            },
            valueRange = 0f..maxInt.toFloat(),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = sliderColor,
                activeTrackColor = sliderColor
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("${testTagPrefix}_slider")
        )
        OutlinedTextField(
            value = textValue,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(3)
                textValue = digits
                val parsed = digits.toIntOrNull()
                if (parsed != null) {
                    onChange(parsed.coerceIn(0, maxInt))
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .width(68.dp)
                .testTag("${testTagPrefix}_input")
        )
    }
}
