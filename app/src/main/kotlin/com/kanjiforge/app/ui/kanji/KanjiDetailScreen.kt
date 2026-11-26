package com.kanjiforge.app.ui.kanji

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kanjiforge.app.domain.KanjiDetailState
import com.kanjiforge.app.domain.ReviewType
import com.kanjiforge.app.domain.SessionMode
import com.kanjiforge.app.domain.StudyCatalog
import com.kanjiforge.app.domain.displayLabel
import com.kanjiforge.app.domain.formatInterval
import com.kanjiforge.app.domain.gradeLabel
import com.kanjiforge.app.ui.components.CollectionPickerButton
import com.kanjiforge.app.ui.components.EmptyMessage
import com.kanjiforge.app.ui.components.ForgeCard
import com.kanjiforge.app.ui.components.FuriganaText
import com.kanjiforge.app.ui.components.JapaneseSpeechButton
import com.kanjiforge.app.ui.components.KanjiHaptic
import com.kanjiforge.app.ui.components.LocalSnackbarHostState
import com.kanjiforge.app.ui.components.MetaPill
import com.kanjiforge.app.ui.components.ReadingChipRow
import com.kanjiforge.app.ui.components.RecessedKanjiFrame
import com.kanjiforge.app.ui.components.SectionLabel
import com.kanjiforge.app.ui.components.StrokeOrderControls
import com.kanjiforge.app.ui.components.StrokePathCanvas
import com.kanjiforge.app.ui.components.rememberKanjiHaptics
import com.kanjiforge.app.ui.components.rememberStrokePlaybackController
import com.kanjiforge.app.ui.theme.KanjiForgeTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class KanjiDetailViewModel(
    val code: Int,
    private val studyCatalog: StudyCatalog,
) : ViewModel() {
    val state: StateFlow<KanjiDetailState> = studyCatalog.observeKanjiDetail(code)
        .stateIn(viewModelScope, SharingStarted.Lazily, KanjiDetailState.Loading)

    fun toggleFavorite(character: String, save: Boolean) {
        viewModelScope.launch {
            studyCatalog.toggleFavorite(ReviewType.KANJI, character, save)
        }
    }

    suspend fun setCollectionMembership(setId: String, character: String, member: Boolean) {
        studyCatalog.setCollectionMembership(setId, ReviewType.KANJI, character, member)
    }

    suspend fun createCollection(name: String, character: String) {
        studyCatalog.createCollectionWithItem(name, ReviewType.KANJI, character)
    }
}

@Composable
fun KanjiDetailScreen(
    viewModel: KanjiDetailViewModel,
    onStart: (SessionMode, Int) -> Unit,
    onWord: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSentenceReadings by remember { mutableStateOf(true) }

    when (val current = state) {
        KanjiDetailState.Loading -> {
            Box(Modifier.fillMaxSize().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        KanjiDetailState.NotFound -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyMessage("Character not found", "This character is unavailable in the local catalog.")
            }
        }
        is KanjiDetailState.Error -> {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(current.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is KanjiDetailState.Ready -> {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val isWide = maxWidth >= 760.dp
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
                            modifier = Modifier.weight(0.92f),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            HeroSpecimenCard(current = current, viewModel = viewModel)
                            QuickStudyActions(current = current, viewModel = viewModel, onStart = onStart)
                        }
                        Column(
                            modifier = Modifier.weight(1.08f),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            StrokeOrderCard(code = viewModel.code, strokePaths = current.detail.strokePaths)
                            GuidedStudyCard(current = current)
                            RadicalsCard(current = current)
                            WordsCard(current = current, onWord = onWord)
                            SentencesCard(
                                current = current,
                                showSentenceReadings = showSentenceReadings,
                                onToggleReadings = { showSentenceReadings = !showSentenceReadings },
                            )
                            NamesCard(current = current)
                            SourceNotesCard(notes = current.detail.notes)
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item { HeroSpecimenCard(current = current, viewModel = viewModel) }
                        item { QuickStudyActions(current = current, viewModel = viewModel, onStart = onStart) }
                        item { StrokeOrderCard(code = viewModel.code, strokePaths = current.detail.strokePaths) }
                        item { GuidedStudyCard(current = current) }
                        if (current.detail.radicals.isNotEmpty() || current.detail.decomposition.isNotBlank()) {
                            item { RadicalsCard(current = current) }
                        }
                        item { WordsCard(current = current, onWord = onWord) }
                        item {
                            SentencesCard(
                                current = current,
                                showSentenceReadings = showSentenceReadings,
                                onToggleReadings = { showSentenceReadings = !showSentenceReadings },
                            )
                        }
                        item { NamesCard(current = current) }
                        if (current.detail.notes.isNotBlank()) {
                            item { SourceNotesCard(notes = current.detail.notes) }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSpecimenCard(
    current: KanjiDetailState.Ready,
    viewModel: KanjiDetailViewModel,
) {
    val entry = current.detail
    val character = entry.summary.character
    val haptics = rememberKanjiHaptics()
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    val metaSummary = listOfNotNull(
        entry.summary.jlpt?.let { "JLPT N$it" },
        entry.summary.grade?.let(::gradeLabel),
        entry.summary.strokeCount?.let { "$it strokes" },
        entry.summary.sequence?.let { "#$it" },
    ).joinToString(" · ")

    ForgeCard {
        Column(
            Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = metaSummary.ifBlank { "Kanji" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        JapaneseSpeechButton(
                            text = entry.on.firstOrNull() ?: entry.kun.firstOrNull().orEmpty(),
                            contentDescription = "Play character reading",
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (current.isSaved) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        IconButton(
                            onClick = {
                                val nextSaved = !current.isSaved
                                haptics(if (nextSaved) KanjiHaptic.CONFIRM else KanjiHaptic.TAP)
                                viewModel.toggleFavorite(character, nextSaved)
                                snackbarHostState?.let { host ->
                                    scope.launch {
                                        host.currentSnackbarData?.dismiss()
                                        host.showSnackbar(
                                            if (nextSaved) "Saved character" else "Removed from saved",
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (current.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (current.isSaved) "Remove saved character" else "Save character",
                                tint = if (current.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            RecessedKanjiFrame(
                character = character,
                size = 168.dp,
                glyphSize = 84.sp,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    entry.summary.meaning.ifBlank { "Meaning unavailable in source" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                if (entry.translation.isNotBlank() && entry.translation != entry.summary.meaning) {
                    Text(
                        entry.translation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (entry.on.isNotEmpty() || entry.kun.isNotEmpty() || entry.nanori.isNotEmpty()) {
                ReadingChipRow(on = entry.on, kun = entry.kun, nanori = entry.nanori)
            }
        }
    }
}

@Composable
private fun QuickStudyActions(
    current: KanjiDetailState.Ready,
    viewModel: KanjiDetailViewModel,
    onStart: (SessionMode, Int) -> Unit,
) {
    val haptics = rememberKanjiHaptics()
    val character = current.detail.summary.character

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    haptics(KanjiHaptic.TAP)
                    onStart(SessionMode.CARDS, viewModel.code)
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.AutoStories, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Study flashcards")
            }
            OutlinedButton(
                onClick = {
                    haptics(KanjiHaptic.TAP)
                    onStart(SessionMode.WRITING, viewModel.code)
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.Gesture, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Write practice")
            }
        }
        CollectionPickerButton(
            choices = current.collectionChoices,
            onToggleCollection = { setId, member ->
                viewModel.setCollectionMembership(setId, character, member)
            },
            onCreateCollection = { name ->
                viewModel.createCollection(name, character)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StrokeOrderCard(code: Int, strokePaths: List<String>) {
    ForgeCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel(
                title = "Stroke order",
                badge = strokePaths.size.takeIf { it > 0 }?.let { "$it strokes" },
            )
            if (strokePaths.isNotEmpty()) {
                val playbackController = rememberStrokePlaybackController(
                    resetKey = code,
                    totalStrokes = strokePaths.size,
                    initialShowGuide = true,
                )
                val playback = playbackController.state
                val activeVisible = if (playback.isPlaying || playback.visibleCount > 0) playback.visibleCount else 0

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    StrokePathCanvas(
                        paths = strokePaths,
                        modifier = Modifier.widthIn(max = 210.dp).fillMaxWidth().aspectRatio(1f),
                        showGuide = playback.showGuide,
                        activeCount = activeVisible,
                    )
                }
                StrokeOrderControls(
                    state = playback,
                    onTogglePlay = playbackController::togglePlay,
                    onStepNext = playbackController::stepNext,
                    showGuideToggle = true,
                    onToggleGuide = playbackController::toggleGuide,
                )
            } else {
                Text(
                    "Stroke data is unavailable for this character.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GuidedStudyCard(current: KanjiDetailState.Ready) {
    ForgeCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val review = current.review
            SectionLabel(
                title = "Guided study",
                trailing = current.dueText?.let { due ->
                    {
                        Text(due, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                },
            )
            if (review == null) {
                Text(
                    "Not studied yet · this character is new to your review history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Reviews", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(review.repetitions.toString(), style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Interval", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            formatInterval(review.intervalDays),
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Serif,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Last rating", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            review.lastRating.displayLabel(),
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Serif,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RadicalsCard(current: KanjiDetailState.Ready) {
    val entry = current.detail
    if (entry.radicals.isEmpty() && entry.decomposition.isBlank()) return
    ForgeCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Radicals & decomposition")
            if (entry.radicals.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    entry.radicals.forEach { radical ->
                        MetaPill(
                            text = "${radical.character} ${radical.meaning}".trim(),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (entry.decomposition.isNotBlank()) {
                Text(
                    "Components: ${entry.decomposition}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WordsCard(
    current: KanjiDetailState.Ready,
    onWord: (Long) -> Unit,
) {
    val words = current.detail.words
    val haptics = rememberKanjiHaptics()
    val colors = KanjiForgeTheme.colors

    ForgeCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            SectionLabel(
                title = "Words",
                badge = "${words.size}",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (words.isEmpty()) {
                Text(
                    "No linked words were recorded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            } else {
                Spacer(Modifier.height(6.dp))
                words.forEachIndexed { index, word ->
                    if (index > 0) {
                        HorizontalDivider(
                            thickness = 0.75.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptics(KanjiHaptic.TAP)
                                onWord(word.id)
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    word.forms.firstOrNull() ?: word.readings.firstOrNull().orEmpty(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    word.readings.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            Text(
                                word.glosses.firstOrNull() ?: word.translation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        word.jlpt?.let { level ->
                            val tint = colors.jlptColor(level)
                            MetaPill(
                                text = "N$level",
                                containerColor = tint.copy(alpha = 0.14f),
                                contentColor = tint,
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SentencesCard(
    current: KanjiDetailState.Ready,
    showSentenceReadings: Boolean,
    onToggleReadings: () -> Unit,
) {
    val sentences = current.detail.sentences
    val haptics = rememberKanjiHaptics()

    ForgeCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(
                title = "Sentences",
                badge = "${sentences.size}",
                trailing = if (sentences.isNotEmpty()) {
                    {
                        FilterChip(
                            selected = showSentenceReadings,
                            onClick = {
                                haptics(KanjiHaptic.TICK)
                                onToggleReadings()
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                            label = { Text(if (showSentenceReadings) "Furigana on" else "Furigana off") },
                        )
                    }
                } else {
                    null
                },
            )
            if (sentences.isEmpty()) {
                Text(
                    "No linked sentences were recorded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                sentences.forEachIndexed { index, sentence ->
                    if (index > 0) {
                        HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FuriganaText(
                            rawText = sentence.text,
                            style = MaterialTheme.typography.bodyLarge,
                            showReadings = showSentenceReadings,
                        )
                        if (sentence.translation.isNotBlank()) {
                            Text(
                                sentence.translation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NamesCard(current: KanjiDetailState.Ready) {
    val names = current.detail.names
    ForgeCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Name examples", badge = names.size.toString())
            Text(
                names.joinToString(" · ") { "${it.name} (${it.reading})" }
                    .ifBlank { "No linked names were recorded." },
                style = MaterialTheme.typography.bodyMedium,
                color = if (names.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SourceNotesCard(notes: String) {
    if (notes.isBlank()) return
    ForgeCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("Source notes")
            Text(notes, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
