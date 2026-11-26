package com.kanjiforge.app.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.kanjiforge.app.ui.theme.KanjiForgeTheme
import kotlinx.coroutines.delay
import kotlin.math.min

data class StrokePlaybackState(
    val totalStrokes: Int,
    val visibleCount: Int = 0,
    val isPlaying: Boolean = false,
    val showGuide: Boolean = true,
) {
    fun togglePlay(): StrokePlaybackState {
        if (totalStrokes <= 0) return this
        return if (isPlaying) copy(isPlaying = false) else copy(isPlaying = true, visibleCount = 0)
    }

    fun stepNext(): StrokePlaybackState {
        if (totalStrokes <= 0) return this
        return copy(
            isPlaying = false,
            showGuide = false,
            visibleCount = (visibleCount + 1).coerceAtMost(totalStrokes),
        )
    }

    fun toggleGuide(): StrokePlaybackState = copy(showGuide = !showGuide)

    fun onTick(): StrokePlaybackState {
        if (!isPlaying || totalStrokes <= 0) return copy(isPlaying = false)
        val next = (visibleCount + 1).coerceAtMost(totalStrokes)
        return copy(visibleCount = next, isPlaying = next < totalStrokes)
    }

    fun reset(newTotalStrokes: Int, initialShowGuide: Boolean = true): StrokePlaybackState =
        StrokePlaybackState(
            totalStrokes = newTotalStrokes.coerceAtLeast(0),
            visibleCount = 0,
            isPlaying = false,
            showGuide = initialShowGuide,
        )
}

@Stable
class StrokePlaybackController(
    initialTotalStrokes: Int,
    initialShowGuide: Boolean = true,
) {
    var state by mutableStateOf(
        StrokePlaybackState(
            totalStrokes = initialTotalStrokes.coerceAtLeast(0),
            showGuide = initialShowGuide,
        ),
    )
        internal set

    fun togglePlay() {
        state = state.togglePlay()
    }

    fun stepNext() {
        state = state.stepNext()
    }

    fun toggleGuide() {
        state = state.toggleGuide()
    }

    internal fun syncStrokeCount(totalStrokes: Int, initialShowGuide: Boolean) {
        if (state.totalStrokes != totalStrokes) {
            state = state.reset(totalStrokes, initialShowGuide)
        }
    }
}

@Composable
fun rememberStrokePlaybackController(
    resetKey: Any?,
    totalStrokes: Int,
    initialShowGuide: Boolean = true,
    stepDelayMs: Long = 420L,
): StrokePlaybackController {
    val controller = remember(resetKey) {
        StrokePlaybackController(initialTotalStrokes = totalStrokes, initialShowGuide = initialShowGuide)
    }
    LaunchedEffect(resetKey, totalStrokes) {
        controller.syncStrokeCount(totalStrokes, initialShowGuide)
    }
    LaunchedEffect(resetKey, controller.state.isPlaying, totalStrokes) {
        while (controller.state.isPlaying && totalStrokes > 0) {
            delay(stepDelayMs)
            controller.state = controller.state.onTick()
        }
    }
    return controller
}

@Composable
fun StrokePathCanvas(
    paths: List<String>,
    modifier: Modifier = Modifier,
    showGuide: Boolean = true,
    activeCount: Int = paths.size,
    viewBox: String = "0 0 109 109",
    drawBackground: Boolean = true,
) {
    val parsed = remember(paths) {
        paths.mapNotNull { runCatching { PathParser.createPathFromPathData(it) }.getOrNull() }
    }
    val bounds = remember(viewBox) {
        val values = viewBox.trim().split(Regex("[,\\s]+")).mapNotNull(String::toFloatOrNull)
        if (values.size == 4 && values[2] > 0f && values[3] > 0f) values else listOf(0f, 0f, 109f, 109f)
    }
    val colors = KanjiForgeTheme.colors
    val guideArgb = colors.canvasGuideStroke.toArgb()
    val activeArgb = colors.canvasActiveStroke.toArgb()

    val guidePaint = remember(guideArgb) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = guideArgb
            style = Paint.Style.STROKE
            strokeWidth = 1.9f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }
    val activePaint = remember(activeArgb) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = activeArgb
            style = Paint.Style.STROKE
            strokeWidth = 3.2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }

    val canvasShape = RoundedCornerShape(18.dp)
    val canvasModifier = modifier
        .clip(canvasShape)
        .then(
            if (drawBackground) {
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(1.dp, colors.cardBorder, canvasShape)
            } else {
                Modifier
            },
        )

    Canvas(canvasModifier) {
        val native = drawContext.canvas.nativeCanvas
        val save = native.save()
        val minX = bounds[0]
        val minY = bounds[1]
        val boxWidth = bounds[2]
        val boxHeight = bounds[3]
        val scale = min(size.width / boxWidth, size.height / boxHeight)
        native.translate(
            (size.width - boxWidth * scale) / 2f - minX * scale,
            (size.height - boxHeight * scale) / 2f - minY * scale,
        )
        native.scale(scale, scale)

        if (showGuide) {
            parsed.forEach { native.drawPath(it, guidePaint) }
        }
        val count = activeCount.coerceIn(0, parsed.size)
        for (i in 0 until count) {
            native.drawPath(parsed[i], activePaint)
        }
        native.restoreToCount(save)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StrokeOrderControls(
    state: StrokePlaybackState,
    onTogglePlay: () -> Unit,
    onStepNext: () -> Unit,
    modifier: Modifier = Modifier,
    showGuideToggle: Boolean = false,
    onToggleGuide: () -> Unit = {},
) {
    val hasStrokes = state.totalStrokes > 0
    val haptics = rememberKanjiHaptics()
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasStrokes) {
            MetaPill(
                text = "${state.visibleCount} / ${state.totalStrokes} strokes",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(
            onClick = {
                haptics(KanjiHaptic.TAP)
                onTogglePlay()
            },
            enabled = hasStrokes,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(if (state.isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (state.isPlaying) "Stop" else "Play")
        }
        OutlinedButton(
            onClick = {
                haptics(KanjiHaptic.TICK)
                onStepNext()
            },
            enabled = hasStrokes && state.visibleCount < state.totalStrokes,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(Icons.Outlined.SkipNext, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Step")
        }
        if (showGuideToggle) {
            OutlinedButton(
                onClick = {
                    haptics(KanjiHaptic.TICK)
                    onToggleGuide()
                },
                enabled = hasStrokes,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(
                    if (state.showGuide) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = null,
                )
                Spacer(Modifier.width(6.dp))
                Text(if (state.showGuide) "Hide guide" else "Show guide")
            }
        }
    }
}
