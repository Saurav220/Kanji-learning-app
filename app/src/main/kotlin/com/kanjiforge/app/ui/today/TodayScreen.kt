package com.kanjiforge.app.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kanjiforge.app.domain.DayActivity
import com.kanjiforge.app.domain.IntervalBucket
import com.kanjiforge.app.domain.SessionMode
import com.kanjiforge.app.domain.StudyCatalog
import com.kanjiforge.app.domain.TodayDashboardState
import com.kanjiforge.app.ui.components.ForgeCard
import com.kanjiforge.app.ui.components.ForgeProgressBar
import com.kanjiforge.app.ui.components.KanjiHaptic
import com.kanjiforge.app.ui.components.ModeIconBadge
import com.kanjiforge.app.ui.components.SectionLabel
import com.kanjiforge.app.ui.components.rememberKanjiHaptics
import com.kanjiforge.app.ui.theme.KanjiForgeTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter
import java.util.Locale

class TodayViewModel(studyCatalog: StudyCatalog) : ViewModel() {
    val state: StateFlow<TodayDashboardState> = studyCatalog.observeTodayDashboard()
        .stateIn(viewModelScope, SharingStarted.Lazily, TodayDashboardState.Loading)
}

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onStart: (SessionMode) -> Unit,
    onStudy: () -> Unit,
    onKana: () -> Unit,
    onBeginnerKanji: () -> Unit,
    onProgress: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        TodayDashboardState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is TodayDashboardState.Ready -> {
            TodayReadyContent(
                state = current,
                onStart = onStart,
                onStudy = onStudy,
                onKana = onKana,
                onBeginnerKanji = onBeginnerKanji,
                onProgress = onProgress,
            )
        }
    }
}

@Composable
private fun TodayReadyContent(
    state: TodayDashboardState.Ready,
    onStart: (SessionMode) -> Unit,
    onStudy: () -> Unit,
    onKana: () -> Unit,
    onBeginnerKanji: () -> Unit,
    onProgress: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 720.dp
        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1.15f),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (state.showBeginnerPrompt) {
                        BeginnerPromptCard(onKana = onKana, onBeginnerKanji = onBeginnerKanji)
                    }
                    GuidedStudyHeroCard(state = state, onStart = onStart, onStudy = onStudy, onProgress = onProgress)
                    PracticeSectionCard(onStart = onStart)
                }
                Column(
                    modifier = Modifier.weight(0.85f),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    ReviewIntervalsCard(intervals = state.intervals, totalKanji = state.totalReviewedKanji)
                    WeeklyActivityCard(activity = state.weekActivity)
                    SummaryFooterText(state)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.showBeginnerPrompt) {
                    item { BeginnerPromptCard(onKana = onKana, onBeginnerKanji = onBeginnerKanji) }
                }
                item {
                    GuidedStudyHeroCard(state = state, onStart = onStart, onStudy = onStudy, onProgress = onProgress)
                }
                item {
                    ReviewIntervalsCard(intervals = state.intervals, totalKanji = state.totalReviewedKanji)
                }
                item {
                    WeeklyActivityCard(activity = state.weekActivity)
                }
                item {
                    PracticeSectionCard(onStart = onStart)
                }
                item {
                    SummaryFooterText(state)
                }
            }
        }
    }
}

@Composable
private fun BeginnerPromptCard(
    onKana: () -> Unit,
    onBeginnerKanji: () -> Unit,
) {
    val haptics = rememberKanjiHaptics()
    ForgeCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "New to Japanese?",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Start with kana charts or beginner N5 kanji sets.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        haptics(KanjiHaptic.TAP)
                        onKana()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text("Kana charts", maxLines = 1, textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = {
                        haptics(KanjiHaptic.TAP)
                        onBeginnerKanji()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text("Beginner sets", maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun GuidedStudyHeroCard(
    state: TodayDashboardState.Ready,
    onStart: (SessionMode) -> Unit,
    onStudy: () -> Unit,
    onProgress: () -> Unit,
) {
    val haptics = rememberKanjiHaptics()
    val newToday = state.newKanjiToday.coerceAtMost(state.dailyNew)

    ForgeCard {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Guided Study",
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${state.dueKanji} due · ${state.remainingNew} new kanji available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "New kanji today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "$newToday / ${state.dailyNew}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                ForgeProgressBar(progress = state.dailyProgress, height = 4.dp)
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            haptics(KanjiHaptic.TAP)
                            onStart(SessionMode.CARDS)
                        },
                        enabled = state.dueKanji > 0 || state.remainingNew > 0,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    ) {
                        val label = when {
                            state.dueKanji > 0 -> "Continue study"
                            state.remainingNew > 0 -> "Start new kanji (${state.remainingNew})"
                            else -> "Today's study complete"
                        }
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label, maxLines = 1, textAlign = TextAlign.Center)
                    }
                    OutlinedButton(
                        onClick = {
                            haptics(KanjiHaptic.TAP)
                            onProgress()
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.heightIn(min = 50.dp),
                    ) {
                        Icon(Icons.Outlined.Insights, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Progress")
                    }
                }

                OutlinedButton(
                    onClick = {
                        haptics(KanjiHaptic.TAP)
                        onStudy()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        "Study by sequence · 10-card sets",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewIntervalsCard(intervals: List<IntervalBucket>, totalKanji: Int) {
    val colors = KanjiForgeTheme.colors
    val tints = colors.intervalBuckets
    val maxBucket = intervals.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

    ForgeCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Review intervals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "$totalKanji kanji",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                intervals.forEachIndexed { index, bucket ->
                    val tint = tints.getOrElse(index) { MaterialTheme.colorScheme.primary }
                    val fillFraction = if (bucket.count == 0) 0f else (bucket.count.toFloat() / maxBucket).coerceIn(0.20f, 1f)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            bucket.count.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (bucket.count > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(colors.progressTrack),
                        ) {
                            if (fillFraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fillFraction)
                                        .height(3.dp)
                                        .clip(CircleShape)
                                        .background(tint),
                                )
                            }
                        }
                        Text(
                            bucket.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyActivityCard(activity: List<DayActivity>) {
    val locale = Locale.getDefault()
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEEEE", locale) }
    val colors = KanjiForgeTheme.colors
    val activeDays = activity.count { it.count > 0 }

    ForgeCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("This week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "$activeDays of 7 days",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                activity.forEach { day ->
                    val hasActivity = day.count > 0
                    val fillColor = when {
                        hasActivity -> colors.activeDayFill
                        else -> colors.inactiveDayFill
                    }
                    val textColor = when {
                        hasActivity -> colors.onActiveDayFill
                        day.isToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val borderStroke = if (day.isToday) {
                        BorderStroke(1.25.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 36.dp),
                            shape = CircleShape,
                            color = fillColor,
                            border = borderStroke,
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (day.count == 0) "–" else day.count.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = textColor,
                                    fontWeight = if (hasActivity || day.isToday) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                        Text(
                            day.date.format(formatter).uppercase(locale),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (day.isToday) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeSectionCard(onStart: (SessionMode) -> Unit) {
    val themeColors = KanjiForgeTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(title = "Practice")
        ForgeCard {
            Column(Modifier.fillMaxWidth()) {
                PracticeListRow(
                    title = "Flashcards",
                    detail = "Meanings and readings",
                    icon = Icons.Outlined.AutoStories,
                    accent = themeColors.modeCardsAccent,
                ) { onStart(SessionMode.CARDS) }
                HorizontalDivider(
                    thickness = 0.75.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                PracticeListRow(
                    title = "Quick quiz",
                    detail = "Choose the matching meaning or reading",
                    icon = Icons.Outlined.Quiz,
                    accent = themeColors.modeQuizAccent,
                ) { onStart(SessionMode.QUIZ) }
                HorizontalDivider(
                    thickness = 0.75.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                PracticeListRow(
                    title = "Write kanji",
                    detail = "Draw each stroke with touch or pen",
                    icon = Icons.Outlined.Gesture,
                    accent = themeColors.modeWritingAccent,
                ) { onStart(SessionMode.WRITING) }
            }
        }
    }
}

@Composable
private fun PracticeListRow(
    title: String,
    detail: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
) {
    val haptics = rememberKanjiHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable {
                haptics(KanjiHaptic.TAP)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ModeIconBadge(icon = icon, accent = accent, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = "Start $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryFooterText(state: TodayDashboardState.Ready) {
    Text(
        "${state.totalReviewedKanji} kanji reviewed · ${state.reviewedToday} reviews today",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        textAlign = TextAlign.Center,
    )
}
