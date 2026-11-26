package com.kanjiforge.app.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kanjiforge.app.data.reference.model.StudySequenceFamily
import com.kanjiforge.app.domain.DeckSetSummary
import com.kanjiforge.app.domain.LevelProgressSummary
import com.kanjiforge.app.domain.SequenceLevelsState
import com.kanjiforge.app.domain.SequenceSetsState
import com.kanjiforge.app.domain.SessionMode
import com.kanjiforge.app.domain.StudyCatalog
import com.kanjiforge.app.domain.TodayDashboardState
import com.kanjiforge.app.ui.components.EmptyMessage
import com.kanjiforge.app.ui.components.ForgeCard
import com.kanjiforge.app.ui.components.ForgeProgressBar
import com.kanjiforge.app.ui.components.KanjiGlyphBadge
import com.kanjiforge.app.ui.components.KanjiHaptic
import com.kanjiforge.app.ui.components.LevelCircleBadge
import com.kanjiforge.app.ui.components.MetaPill
import com.kanjiforge.app.ui.components.ModeIconBadge
import com.kanjiforge.app.ui.components.ScreenHeading
import com.kanjiforge.app.ui.components.SectionLabel
import com.kanjiforge.app.ui.components.rememberKanjiHaptics
import com.kanjiforge.app.ui.theme.KanjiForgeTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

class StudySequencesViewModel(studyCatalog: StudyCatalog) : ViewModel() {
    val dashboard: StateFlow<TodayDashboardState> = studyCatalog.observeTodayDashboard()
        .stateIn(viewModelScope, SharingStarted.Lazily, TodayDashboardState.Loading)
}

class StudyLevelsViewModel(
    val family: StudySequenceFamily,
    studyCatalog: StudyCatalog,
) : ViewModel() {
    val state: StateFlow<SequenceLevelsState> = studyCatalog.observeSequenceLevels(family)
        .stateIn(viewModelScope, SharingStarted.Lazily, SequenceLevelsState.Loading)
}

class StudySetsViewModel(
    val family: StudySequenceFamily,
    val level: Int,
    studyCatalog: StudyCatalog,
) : ViewModel() {
    val state: StateFlow<SequenceSetsState> = studyCatalog.observeSequenceSets(family, level)
        .stateIn(viewModelScope, SharingStarted.Lazily, SequenceSetsState.Loading)
}

@Composable
fun StudySequencesScreen(
    viewModel: StudySequencesViewModel,
    onSelectSequence: (StudySequenceFamily) -> Unit,
    onStartDaily: (SessionMode) -> Unit,
) {
    val dashboardState by viewModel.dashboard.collectAsStateWithLifecycle()
    val haptics = rememberKanjiHaptics()
    val colors = KanjiForgeTheme.colors

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 700.dp) 2 else 1
        val familyRows = remember(columns) { StudySequenceFamily.entries.chunked(columns) }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = if (columns > 1) 24.dp else 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeading(
                    "STUDY ROOM",
                    "Choose a kanji sequence",
                    "Follow an ordered curriculum in focused 10-kanji sets.",
                )
            }
            when (val dash = dashboardState) {
                TodayDashboardState.Loading -> Unit
                is TodayDashboardState.Ready -> {
                    item {
                        ForgeCard {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        "${dash.dueKanji} kanji due",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "${dash.reviewedToday} reviewed today · ${dash.remainingNew} new left (target ${dash.dailyNew})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Button(
                                    enabled = dash.dueKanji > 0 || dash.remainingNew > 0,
                                    onClick = {
                                        haptics(KanjiHaptic.TAP)
                                        onStartDaily(SessionMode.CARDS)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (dash.dueKanji > 0) "Start review" else "Start new kanji")
                                }
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Daily practice modes", modifier = Modifier.padding(top = 4.dp)) }
            item {
                if (columns > 1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StudyModeCard("Flashcards", "Meaning & reading", Icons.Outlined.AutoStories, colors.modeCardsAccent, Modifier.weight(1f)) {
                            onStartDaily(SessionMode.CARDS)
                        }
                        StudyModeCard("Multiple choice", "Quick recall quiz", Icons.Outlined.Quiz, colors.modeQuizAccent, Modifier.weight(1f)) {
                            onStartDaily(SessionMode.QUIZ)
                        }
                        StudyModeCard("Writing", "Draw & compare", Icons.Outlined.Gesture, colors.modeWritingAccent, Modifier.weight(1f)) {
                            onStartDaily(SessionMode.WRITING)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StudyModeCard("Flashcards", "Meaning, reading, or character prompts", Icons.Outlined.AutoStories, colors.modeCardsAccent) {
                            onStartDaily(SessionMode.CARDS)
                        }
                        StudyModeCard("Multiple choice", "Test meanings and readings", Icons.Outlined.Quiz, colors.modeQuizAccent) {
                            onStartDaily(SessionMode.QUIZ)
                        }
                        StudyModeCard("Writing practice", "Draw with touch or pen and compare with the guide", Icons.Outlined.Gesture, colors.modeWritingAccent) {
                            onStartDaily(SessionMode.WRITING)
                        }
                    }
                }
            }
            item { SectionLabel("Curated sequences", modifier = Modifier.padding(top = 4.dp)) }
            itemsIndexed(familyRows, key = { _, row -> "sequence-${row.first().name}" }) { _, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { family ->
                        StudySequenceChoice(family, Modifier.weight(1f)) { onSelectSequence(family) }
                    }
                    if (row.size < columns) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            item {
                Text(
                    "Review intervals follow KanjiForge's local schedule.",
                    Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun StudyLevelsScreen(
    viewModel: StudyLevelsViewModel,
    onSelectLevel: (Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 700.dp) 2 else 1

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = if (columns > 1) 24.dp else 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeading(
                    "STUDY ROOM",
                    viewModel.family.title,
                    "Open a level to see its 10-kanji study sets and progress.",
                )
            }
            when (val current = state) {
                SequenceLevelsState.Loading -> {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is SequenceLevelsState.Error -> {
                    item { Text(current.message, color = MaterialTheme.colorScheme.error) }
                }
                is SequenceLevelsState.Ready -> {
                    item {
                        Text(
                            "${current.remainingNew} new kanji left today · target ${current.dailyNew}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (current.levels.isEmpty()) {
                        item {
                            EmptyMessage("No sequence entries", "No readable sequence entries are available.")
                        }
                    } else {
                        val levelRows = current.levels.chunked(columns)
                        itemsIndexed(levelRows, key = { _, row -> "${row.first().deck.family.name}-${row.first().deck.level}" }) { _, row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { summary ->
                                    StudyLevelRow(summary, Modifier.weight(1f)) { onSelectLevel(summary.deck.level) }
                                }
                                if (row.size < columns) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun StudySetsScreen(
    viewModel: StudySetsViewModel,
    onStartSet: (SessionMode, offset: Int, batchSize: Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showModePicker by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf(SessionMode.CARDS) }
    var pendingOffset by remember { mutableIntStateOf(0) }
    var pendingSize by remember { mutableIntStateOf(10) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 700.dp) 2 else 1

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = if (columns > 1) 24.dp else 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ScreenHeading(
                    "STUDY SETS",
                    viewModel.family.deckTitle(viewModel.level),
                    "Each set keeps its place in the sequence. Pick any set to choose a study mode.",
                )
            }
            when (val current = state) {
                SequenceSetsState.Loading -> {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is SequenceSetsState.Error -> {
                    item { Text(current.message, color = MaterialTheme.colorScheme.error) }
                }
                is SequenceSetsState.Ready -> {
                    item {
                        Text(
                            "${current.totalKanji} kanji · ${current.sets.size} sets · ${current.remainingNew} new left today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (current.sets.isEmpty()) {
                        item {
                            EmptyMessage("Empty level", "No kanji are available in this level.")
                        }
                    } else {
                        val setRows = current.sets.chunked(columns)
                        itemsIndexed(setRows, key = { _, row -> "${current.family.name}-${current.level}-set-${row.first().setNumber}" }) { _, row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { setSummary ->
                                    StudyDeckSetRow(setSummary, Modifier.weight(1f)) {
                                        pendingOffset = setSummary.offset
                                        pendingSize = setSummary.batchSize
                                        selectedMode = SessionMode.CARDS
                                        showModePicker = true
                                    }
                                }
                                if (row.size < columns) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showModePicker) {
        StudyModePickerDialog(
            selectedMode = selectedMode,
            onSelectMode = { selectedMode = it },
            onStart = {
                showModePicker = false
                onStartSet(selectedMode, pendingOffset, pendingSize)
            },
            onDismiss = { showModePicker = false },
        )
    }
}

@Composable
fun StudyModePickerDialog(
    selectedMode: SessionMode,
    onSelectMode: (SessionMode) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = KanjiForgeTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Choose a study mode", style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StudyModeOption(
                    title = "Flashcard study",
                    description = "Review character, meaning, and reading prompts.",
                    icon = Icons.Outlined.AutoStories,
                    accent = colors.modeCardsAccent,
                    selected = selectedMode == SessionMode.CARDS,
                ) { onSelectMode(SessionMode.CARDS) }
                StudyModeOption(
                    title = "Multiple choice",
                    description = "Check your recall with a short quiz.",
                    icon = Icons.Outlined.Quiz,
                    accent = colors.modeQuizAccent,
                    selected = selectedMode == SessionMode.QUIZ,
                ) { onSelectMode(SessionMode.QUIZ) }
                StudyModeOption(
                    title = "Writing practice",
                    description = "Draw each kanji, then compare with the stroke guide.",
                    icon = Icons.Outlined.Gesture,
                    accent = colors.modeWritingAccent,
                    selected = selectedMode == SessionMode.WRITING,
                ) { onSelectMode(SessionMode.WRITING) }
            }
        },
        confirmButton = {
            Button(onClick = onStart, shape = RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun StudySequenceChoice(
    family: StudySequenceFamily,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ForgeCard(onClick = onClick, modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 74.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(family.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                    MetaPill(text = family.rangeLabel)
                }
                Text(family.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudyLevelRow(
    summary: LevelProgressSummary,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val colors = KanjiForgeTheme.colors
    val deck = summary.deck
    val setCount = (deck.count + 9) / 10
    val progress = if (deck.count == 0) 0f else summary.learned.toFloat() / deck.count
    val pct = (progress * 100f).roundToInt()
    val accent = when (deck.family) {
        StudySequenceFamily.JLPT, StudySequenceFamily.JLPT_REVISED -> colors.jlptColor(deck.level)
        else -> MaterialTheme.colorScheme.secondary
    }
    val badgeText = when (deck.family) {
        StudySequenceFamily.JLPT, StudySequenceFamily.JLPT_REVISED -> "N${deck.level}"
        StudySequenceFamily.SCHOOL_GRADES, StudySequenceFamily.JOUYOU_REVISED -> "G${deck.level}"
        StudySequenceFamily.HADAMITZKY -> "ALL"
        else -> "${deck.level}"
    }

    ForgeCard(onClick = onOpen, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LevelCircleBadge(label = badgeText, accent = accent, size = 42.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(deck.family.deckTitle(deck.level), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${deck.count} kanji · $setCount study sets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("$pct%", style = MaterialTheme.typography.labelLarge, color = accent)
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "Open ${deck.family.deckTitle(deck.level)}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ForgeProgressBar(progress = progress, color = accent, height = 4.dp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MetaPill(text = "${summary.learned} studied")
                if (summary.due > 0) {
                    MetaPill(
                        text = "${summary.due} due",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyDeckSetRow(
    setSummary: DeckSetSummary,
    modifier: Modifier = Modifier,
    onStudy: () -> Unit,
) {
    val progress = if (setSummary.totalInSet == 0) 0f else setSummary.learned.toFloat() / setSummary.totalInSet
    val actionBg = when {
        !setSummary.canStudy -> MaterialTheme.colorScheme.surfaceVariant
        setSummary.due > 0 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val actionFg = when {
        !setSummary.canStudy -> MaterialTheme.colorScheme.onSurfaceVariant
        setSummary.due > 0 -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    ForgeCard(onClick = onStudy, enabled = setSummary.canStudy, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KanjiGlyphBadge(
                    glyph = setSummary.leadCharacter.ifBlank { setSummary.setNumber.toString() },
                    size = 46.dp,
                    accent = if (setSummary.canStudy) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Set ${setSummary.setNumber}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${setSummary.totalInSet} kanji · ${setSummary.learned} studied · ${setSummary.due} due",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MetaPill(
                    text = setSummary.actionLabel,
                    containerColor = actionBg,
                    contentColor = actionFg,
                )
            }
            ForgeProgressBar(progress = progress, height = 4.dp)
            if (!setSummary.canStudy) {
                Text(
                    "Today's new-kanji limit is reached.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StudyModeOption(
    title: String,
    description: String,
    icon: ImageVector,
    accent: Color,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = KanjiForgeTheme.colors
    ForgeCard(
        onClick = onSelect,
        containerColor = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = if (selected) MaterialTheme.colorScheme.primary else colors.cardBorder,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ModeIconBadge(icon = icon, accent = accent, size = 36.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun StudyModeCard(
    title: String,
    copy: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onStart: () -> Unit,
) {
    ForgeCard(onClick = onStart, modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 70.dp).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeIconBadge(icon = icon, accent = accent, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(copy, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
