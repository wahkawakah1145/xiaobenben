package com.example.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.example.model.AppearanceSettings
import kotlin.math.roundToInt

enum class AppHapticAction(val label: String) {
    CLICK("点击按钮"),
    TOGGLE("开关切换"),
    LONG_PRESS("长按触发"),
    DRAG_REORDER("拖动排序与归属"),
    DESTRUCTIVE("删除 / 危险操作"),
    GESTURE("滑动手势")
}

class AppHapticsController(
    private val view: View,
    private val context: Context,
    private val settings: AppearanceSettings
) {
    fun perform(action: AppHapticAction, forcePreview: Boolean = false) {
        if (!forcePreview) {
            if (!settings.hapticEnabled) return
            val actionAllowed = when (action) {
                AppHapticAction.CLICK -> settings.hapticOnClick
                AppHapticAction.TOGGLE -> settings.hapticOnToggle
                AppHapticAction.LONG_PRESS -> settings.hapticOnLongPress
                AppHapticAction.DRAG_REORDER -> settings.hapticOnDragReorder
                AppHapticAction.DESTRUCTIVE -> settings.hapticOnDestructive
                AppHapticAction.GESTURE -> settings.hapticOnGesture
            }
            if (!actionAllowed) return
        }

        if (settings.hapticFollowSystem) {
            performSystemFeedback(action)
        } else {
            performCustomVibration(action, settings.hapticIntensity.coerceIn(0.15f, 1.0f))
        }
    }

    private fun performSystemFeedback(action: AppHapticAction) {
        val constant = when (action) {
            AppHapticAction.CLICK -> HapticFeedbackConstants.KEYBOARD_TAP
            AppHapticAction.TOGGLE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.TOGGLE_ON
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
            AppHapticAction.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
            AppHapticAction.DRAG_REORDER -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
            AppHapticAction.DESTRUCTIVE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
            AppHapticAction.GESTURE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.GESTURE_START
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
        }
        runCatching {
            view.performHapticFeedback(constant)
        }
    }

    private fun performCustomVibration(action: AppHapticAction, intensity: Float) {
        val vibrator = resolveVibrator() ?: run {
            performSystemFeedback(action)
            return
        }
        val (baseDurationMs, weight) = when (action) {
            AppHapticAction.CLICK -> 12L to 0.72f
            AppHapticAction.TOGGLE -> 16L to 0.80f
            AppHapticAction.LONG_PRESS -> 28L to 0.95f
            AppHapticAction.DRAG_REORDER -> 14L to 0.82f
            AppHapticAction.DESTRUCTIVE -> 36L to 1.0f
            AppHapticAction.GESTURE -> 15L to 0.76f
        }
        val durationMs = (baseDurationMs * (0.65f + 0.55f * intensity)).roundToInt().coerceIn(8, 65).toLong()
        val amplitude = (255f * intensity * weight).roundToInt().coerceIn(24, 255)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (vibrator.hasAmplitudeControl()) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        }.onFailure {
            performSystemFeedback(action)
        }
    }

    private fun resolveVibrator(): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()?.takeIf { runCatching { it.hasVibrator() }.getOrDefault(false) }
}

val LocalAppHaptics = staticCompositionLocalOf<AppHapticsController?> { null }

@Composable
fun rememberAppHaptics(appearance: AppearanceSettings): AppHapticsController {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view, context, appearance) {
        AppHapticsController(view = view, context = context, settings = appearance)
    }
}
