package com.kanjiforge.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanjiforge.app.ui.theme.KanjiForgeTheme

@Composable
fun ForgeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = KanjiForgeTheme.colors.cardBorder,
    shape: Shape = RoundedCornerShape(14.dp),
    @Suppress("UNUSED_PARAMETER") ambientGlow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptics = rememberKanjiHaptics()
    val border = BorderStroke(0.75.dp, borderColor.copy(alpha = 0.75f))

    if (onClick != null) {
        Card(
            onClick = {
                haptics(KanjiHaptic.TAP)
                onClick()
            },
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            border = border,
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                disabledContainerColor = containerColor.copy(alpha = 0.65f),
            ),
            content = content,
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            border = border,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            content = content,
        )
    }
}

@Composable
fun RecessedKanjiFrame(
    character: String,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    glyphSize: TextUnit = 80.sp,
    glyphColor: Color = MaterialTheme.colorScheme.onSurface,
    @Suppress("UNUSED_PARAMETER") bottomLeftLabel: String? = null,
    @Suppress("UNUSED_PARAMETER") bottomRightLabel: String? = null,
) {
    val colors = KanjiForgeTheme.colors
    val gridColor = colors.cardBorder
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(0.75.dp, colors.cardBorder.copy(alpha = 0.85f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 7f), 0f)
            val w = this.size.width
            val h = this.size.height
            drawLine(gridColor.copy(alpha = 0.65f), Offset(w / 2f, 0f), Offset(w / 2f, h), 0.75f, pathEffect = dash)
            drawLine(gridColor.copy(alpha = 0.65f), Offset(0f, h / 2f), Offset(w, h / 2f), 0.75f, pathEffect = dash)
        }
        Text(
            text = character,
            fontFamily = FontFamily.Serif,
            fontSize = glyphSize,
            lineHeight = glyphSize * 1.12f,
            fontWeight = FontWeight.Normal,
            color = glyphColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun DotStatusPill(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ScreenHeading(eyebrow: String, title: String, description: String? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
        Text(
            eyebrow.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionLabel(
    title: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.2.sp,
        )
        if (!badge.isNullOrBlank()) {
            MetaPill(
                text = badge,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun KanjiGlyphBadge(
    glyph: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    accent: Color = MaterialTheme.colorScheme.onSurface,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(0.75.dp, KanjiForgeTheme.colors.cardBorder.copy(alpha = 0.75f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val textStyle = when {
            size >= 68.dp -> MaterialTheme.typography.displayMedium
            size >= 48.dp -> MaterialTheme.typography.headlineMedium
            else -> MaterialTheme.typography.titleLarge
        }
        Text(
            text = glyph.ifBlank { "·" },
            style = textStyle,
            fontFamily = FontFamily.Serif,
            color = accent,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
fun LevelCircleBadge(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ModeIconBadge(
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(size * 0.50f),
        )
    }
}

@Composable
fun MetaPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadingChipRow(
    on: List<String>,
    kun: List<String>,
    nanori: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val colors = KanjiForgeTheme.colors
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        on.forEach { reading ->
            ReadingPill(tag = "ON", reading = reading, container = colors.readingOnContainer, content = colors.onReadingOn)
        }
        kun.forEach { reading ->
            ReadingPill(tag = "KUN", reading = reading, container = colors.readingKunContainer, content = colors.onReadingKun)
        }
        nanori.forEach { reading ->
            ReadingPill(tag = "NAME", reading = reading, container = colors.readingNameContainer, content = colors.onReadingName)
        }
    }
}

@Composable
private fun ReadingPill(
    tag: String,
    reading: String,
    container: Color,
    content: Color,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = reading,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun ForgeProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = KanjiForgeTheme.colors.progressTrack,
) {
    val reduceMotion = rememberReduceMotion()
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 320),
        label = "forge-progress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        if (animated > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(height)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector? = null,
) {
    ForgeCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KanjiListRow(
    character: String,
    title: String,
    subtitle: String,
    trailing: String = "",
    metaTags: List<Pair<String, Color?>> = emptyList(),
    showChevron: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KanjiGlyphBadge(glyph = character, size = 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title.ifBlank { "Meaning unavailable" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (metaTags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    metaTags.forEach { (label, tint) ->
                        MetaPill(
                            text = label,
                            containerColor = tint?.copy(alpha = 0.14f) ?: MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        } else if (showChevron) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun EmptyMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.SearchOff,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
