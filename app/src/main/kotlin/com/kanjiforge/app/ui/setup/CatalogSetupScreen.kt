package com.kanjiforge.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kanjiforge.app.data.reference.CatalogBootstrapState
import com.kanjiforge.app.ui.components.ForgeProgressBar
import com.kanjiforge.app.ui.components.KanjiGlyphBadge
import kotlin.math.roundToInt

@Composable
fun CatalogSetupScreen(state: CatalogBootstrapState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KanjiGlyphBadge(glyph = "漢", size = 76.dp)
        Spacer(Modifier.height(16.dp))
        Text("KanjiForge", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        val subtitle = when (state) {
            is CatalogBootstrapState.Copying -> {
                val pct = state.progress?.let { " ${(it * 100f).roundToInt()}%" }.orEmpty()
                "Preparing your offline kanji library…$pct"
            }
            CatalogBootstrapState.Verifying -> "Verifying offline catalog integrity…"
            is CatalogBootstrapState.Failed -> state.message
            is CatalogBootstrapState.Ready -> "Opening library…"
        }
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        when (state) {
            is CatalogBootstrapState.Copying -> {
                val progress = state.progress
                if (progress != null) {
                    ForgeProgressBar(
                        progress = progress,
                        height = 8.dp,
                        modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth(),
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
            CatalogBootstrapState.Verifying, is CatalogBootstrapState.Ready -> CircularProgressIndicator()
            is CatalogBootstrapState.Failed -> Button(
                onClick = onRetry,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Retry setup")
            }
        }
    }
}
