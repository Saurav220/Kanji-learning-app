package com.kanjiforge.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kanjiforge.app.ui.theme.KanjiForgeTheme
import kotlin.math.hypot

private sealed interface CachedStroke {
    data class Dot(val center: Offset) : CachedStroke
    data class Curve(val path: Path) : CachedStroke
}

@Composable
fun KanjiDrawingPad(
    paths: List<List<Offset>>,
    onPathsChange: (List<List<Offset>>) -> Unit,
    modifier: Modifier = Modifier,
    onDrawingChanged: (Boolean) -> Unit = {},
    drawBackground: Boolean = false,
) {
    val latestPaths by rememberUpdatedState(paths)
    val latestOnPathsChange by rememberUpdatedState(onPathsChange)
    val latestOnDrawingChanged by rememberUpdatedState(onDrawingChanged)

    val livePoints = remember { mutableStateListOf<Offset>() }
    val liveStrokePath = remember { Path() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val cachedCompleted = remember(paths, canvasSize) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) {
            emptyList()
        } else {
            val w = canvasSize.width.toFloat()
            val h = canvasSize.height.toFloat()
            paths.filter { it.isNotEmpty() }.map { stroke ->
                if (stroke.size == 1) {
                    val p = stroke.first()
                    CachedStroke.Dot(Offset(p.x * w, p.y * h))
                } else {
                    CachedStroke.Curve(
                        Path().apply {
                            populateSmoothPath(this, stroke, w, h)
                        },
                    )
                }
            }
        }
    }

    val colors = KanjiForgeTheme.colors
    val shape = RoundedCornerShape(18.dp)
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f) }

    val padModifier = modifier
        .clip(shape)
        .then(if (drawBackground) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        .semantics { contentDescription = "Kanji writing canvas" }
        .onSizeChanged { canvasSize = it }
        .pointerInput(canvasSize) {
            if (canvasSize.width <= 0 || canvasSize.height <= 0) return@pointerInput
            val width = canvasSize.width.toFloat()
            val height = canvasSize.height.toFloat()

            fun normalize(position: Offset): Offset = Offset(
                (position.x / width).coerceIn(0f, 1f),
                (position.y / height).coerceIn(0f, 1f),
            )

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                val pointerId = down.id
                livePoints.clear()
                livePoints.add(normalize(down.position))
                latestOnDrawingChanged(true)
                var completed = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()
                        if (change == null) {
                            completed = true
                            break
                        }
                        if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                            change.consume()
                            completed = true
                            break
                        }
                        if (change.positionChanged()) {
                            val next = normalize(change.position)
                            val prev = livePoints.lastOrNull()
                            if (prev == null || hypot(next.x - prev.x, next.y - prev.y) >= 0.003f) {
                                livePoints.add(next)
                            }
                            change.consume()
                        }
                    }
                    if (completed && livePoints.isNotEmpty()) {
                        latestOnPathsChange(latestPaths + listOf(livePoints.toList()))
                    }
                } finally {
                    livePoints.clear()
                    latestOnDrawingChanged(false)
                }
            }
        }

    Canvas(modifier = padModifier) {
        val gridWidth = 1.dp.toPx()
        val diagonalColor = colors.canvasGrid.copy(alpha = colors.canvasGrid.alpha * 0.45f)
        drawLine(diagonalColor, Offset.Zero, Offset(size.width, size.height), gridWidth, pathEffect = dashEffect)
        drawLine(diagonalColor, Offset(size.width, 0f), Offset(0f, size.height), gridWidth, pathEffect = dashEffect)
        drawLine(colors.canvasGrid, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), gridWidth, pathEffect = dashEffect)
        drawLine(colors.canvasGrid, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), gridWidth, pathEffect = dashEffect)

        val strokePx = 6.dp.toPx()
        val strokeStyle = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val dotRadius = strokePx / 2f

        cachedCompleted.forEach { stroke ->
            when (stroke) {
                is CachedStroke.Dot -> drawCircle(colors.drawingInk, radius = dotRadius, center = stroke.center)
                is CachedStroke.Curve -> drawPath(stroke.path, colors.drawingInk, style = strokeStyle)
            }
        }

        val activeCount = livePoints.size
        if (activeCount == 1) {
            val pt = livePoints[0]
            drawCircle(
                color = colors.drawingInk,
                radius = dotRadius,
                center = Offset(pt.x * size.width, pt.y * size.height),
            )
        } else if (activeCount > 1) {
            liveStrokePath.reset()
            populateSmoothPath(liveStrokePath, livePoints, size.width, size.height)
            drawPath(liveStrokePath, colors.drawingInk, style = strokeStyle)
        }
    }
}

private fun populateSmoothPath(
    path: Path,
    normalizedPoints: List<Offset>,
    width: Float,
    height: Float,
) {
    if (normalizedPoints.size < 2) return
    val p0x = normalizedPoints[0].x * width
    val p0y = normalizedPoints[0].y * height
    path.moveTo(p0x, p0y)
    if (normalizedPoints.size == 2) {
        path.lineTo(normalizedPoints[1].x * width, normalizedPoints[1].y * height)
        return
    }
    var prevX = p0x
    var prevY = p0y
    val lastIndex = normalizedPoints.lastIndex
    for (i in 1..lastIndex) {
        val currX = normalizedPoints[i].x * width
        val currY = normalizedPoints[i].y * height
        if (i < lastIndex) {
            val midX = (prevX + currX) / 2f
            val midY = (prevY + currY) / 2f
            path.quadraticTo(prevX, prevY, midX, midY)
        } else {
            path.quadraticTo(prevX, prevY, currX, currY)
        }
        prevX = currX
        prevY = currY
    }
}
