package com.kanjiforge.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.kanjiforge.app.domain.FuriganaSegment
import com.kanjiforge.app.domain.formatFuriganaPlain
import com.kanjiforge.app.domain.parseFurigana

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FuriganaText(
    rawText: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    readingColor: Color = MaterialTheme.colorScheme.primary,
    showReadings: Boolean = true,
) {
    if (!showReadings) {
        val plain = remember(rawText) { formatFuriganaPlain(rawText) }
        if (plain.isEmpty()) return
        Text(
            text = plain,
            modifier = modifier,
            style = style,
            color = color,
        )
        return
    }

    val segments = remember(rawText) { parseFurigana(rawText) }
    if (segments.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        segments.forEach { segment ->
            RubySegment(
                segment = segment,
                style = style,
                color = color,
                readingColor = readingColor,
                showReading = showReadings && !segment.reading.isNullOrBlank(),
            )
        }
    }
}

@Composable
private fun RubySegment(
    segment: FuriganaSegment,
    style: TextStyle,
    color: Color,
    readingColor: Color,
    showReading: Boolean,
) {
    if (!showReading) {
        Text(
            text = segment.base,
            style = style,
            color = color,
        )
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = segment.reading.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = readingColor,
            )
            Text(
                text = segment.base,
                style = style,
                color = color,
            )
        }
    }
}
