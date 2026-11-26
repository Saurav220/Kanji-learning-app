package com.kanjiforge.app.ui.components

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState?> { null }

enum class KanjiHaptic {
    TAP,
    TICK,
    CONFIRM,
    REJECT,
    HEAVY,
}

@Composable
fun rememberKanjiHaptics(): (KanjiHaptic) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { kind ->
            val feedbackCode = when (kind) {
                KanjiHaptic.TAP -> HapticFeedbackConstants.CONTEXT_CLICK
                KanjiHaptic.TICK -> HapticFeedbackConstants.CLOCK_TICK
                KanjiHaptic.HEAVY -> HapticFeedbackConstants.LONG_PRESS
                KanjiHaptic.CONFIRM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.CONTEXT_CLICK
                }
                KanjiHaptic.REJECT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.REJECT
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
            }
            runCatching { view.performHapticFeedback(feedbackCode) }
        }
    }
}

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}
