package com.kanjiforge.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kanjiforge.app.domain.ProgressState
import com.kanjiforge.app.domain.SessionMode
import com.kanjiforge.app.domain.SessionRecord
import com.kanjiforge.app.domain.StudyCatalog
import com.kanjiforge.app.domain.formatSessionTimestamp
import com.kanjiforge.app.ui.components.EmptyMessage
import com.kanjiforge.app.ui.components.ForgeCard
import com.kanjiforge.app.ui.components.MetaPill
import com.kanjiforge.app.ui.components.MetricTile
import com.kanjiforge.app.ui.components.ModeIconBadge
import com.kanjiforge.app.ui.components.ScreenHeading
import com.kanjiforge.app.ui.components.SectionLabel
import com.kanjiforge.app.ui.theme.KanjiForgeTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

class ProgressViewModel(studyCatalog: StudyCatalog) : ViewModel() {
    val state: StateFlow<ProgressState> = studyCatalog.observeProgress()
        .stateIn(viewModelScope, SharingStarted.Lazily, ProgressState.Loading)
}

@Composable
fun ProgressScreen(viewModel: ProgressViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeading(
                "YOUR PROGRESS",
                "Small steps add up",
                "Review history lives locally and can be exported from Settings.",
            )
        }
        when (val current = state) {
            ProgressState.Loading -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is ProgressState.Ready -> {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile(
                            label = "Kanji due",
                            value = current.dueKanji.toString(),
                            accent = MaterialTheme.colorScheme.tertiary,
                            icon = Icons.Outlined.Schedule,
                            modifier = Modifier.weight(1f),
                        )
                        MetricTile(
                            label = "Reviewed today",
                            value = current.reviewedToday.toString(),
                            accent = MaterialTheme.colorScheme.primary,
                            icon = Icons.Outlined.CheckCircle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile(
                            label = "Saved kanji",
                            value = current.favoriteKanji.toString(),
                            accent = MaterialTheme.colorScheme.secondary,
                            icon = Icons.Outlined.BookmarkBorder,
                            modifier = Modifier.weight(1f),
                        )
                        MetricTile(
                            label = "Saved words",
                            value = current.favoriteWords.toString(),
                            accent = MaterialTheme.colorScheme.secondary,
                            icon = Icons.AutoMirrored.Outlined.MenuBook,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    SectionLabel(
                        title = "Recent sessions",
                        badge = current.sessions.size.toString(),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (current.sessions.isEmpty()) {
                    item {
                        EmptyMessage(
                            title = "No sessions yet",
                            message = "Your completed study sessions will appear here.",
                            icon = Icons.Outlined.History,
                        )
                    }
                } else {
                    items(current.sessions, key = { it.id }) { item -> SessionRow(item) }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SessionRow(item: SessionRecord) {
    val colors = KanjiForgeTheme.colors
    val (icon, accent, modeLabel) = when (item.mode) {
        SessionMode.CARDS -> Triple(Icons.Outlined.AutoStories, colors.modeCardsAccent, "Flashcards")
        SessionMode.QUIZ -> Triple(Icons.Outlined.Quiz, colors.modeQuizAccent, "Quick quiz")
        SessionMode.WRITING -> Triple(Icons.Outlined.Gesture, colors.modeWritingAccent, "Writing practice")
    }
    val accuracy = if (item.total > 0) ((item.correct.toFloat() / item.total) * 100f).roundToInt() else 0

    ForgeCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeIconBadge(icon = icon, accent = accent, size = 42.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(modeLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${item.correct} of ${item.total} · ${formatSessionTimestamp(item.finishedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MetaPill(
                text = "$accuracy%",
                containerColor = accent.copy(alpha = 0.16f),
                contentColor = accent,
            )
        }
    }
}
