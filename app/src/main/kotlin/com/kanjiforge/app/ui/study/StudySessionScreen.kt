package com.kanjiforge.app.ui.study

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanjiforge.app.domain.CardDirection
import com.kanjiforge.app.domain.QuizPrompt
import com.kanjiforge.app.domain.Rating
import com.kanjiforge.app.domain.SessionMode
import com.kanjiforge.app.domain.displayLabel
import com.kanjiforge.app.ui.components.ForgeCard
import com.kanjiforge.app.ui.components.ForgeProgressBar
import com.kanjiforge.app.ui.components.JapaneseSpeechButton
import com.kanjiforge.app.ui.components.KanjiDrawingPad
import com.kanjiforge.app.ui.components.KanjiHaptic
import com.kanjiforge.app.ui.components.LocalSpeechSpeaker
import com.kanjiforge.app.ui.components.MetaPill
import com.kanjiforge.app.ui.components.MetricTile
import com.kanjiforge.app.ui.components.ReadingChipRow
import com.kanjiforge.app.ui.components.RecessedKanjiFrame
import com.kanjiforge.app.ui.components.StrokeOrderControls
import com.kanjiforge.app.ui.components.StrokePathCanvas
import com.kanjiforge.app.ui.components.rememberKanjiHaptics
import com.kanjiforge.app.ui.components.rememberReduceMotion
import com.kanjiforge.app.ui.components.rememberStrokePlaybackController
import com.kanjiforge.app.ui.theme.KanjiForgeTheme
import kotlin.math.roundToInt

@Composable
fun StudySessionScreen(
    viewModel: StudySessionViewModel,
    registerExitHandler: ((() -> Unit)?) -> Unit,
    onExit: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exitAction = remember(viewModel, onExit) { { viewModel.finishAndExit(onExit) } }

    BackHandler(onBack = exitAction)
    DisposableEffect(registerExitHandler, exitAction) {
        registerExitHandler(exitAction)
        onDispose { registerExitHandler(null) }
    }

    when (val current = state) {
        StudySessionUiState.Loading -> {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Building your session…", style = MaterialTheme.typography.titleMedium)
            }
        }
        is StudySessionUiState.Error -> {
            SessionOutcomeShell(
                icon = Icons.Outlined.ErrorOutline,
                accent = MaterialTheme.colorScheme.error,
                title = "Could not start session",
                message = current.message,
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onExit,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text("Back") }
                        Button(
                            onClick = viewModel::retry,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text("Retry") }
                    }
                },
            )
        }
        is StudySessionUiState.Empty -> {
            SessionOutcomeShell(
                icon = Icons.Outlined.School,
                accent = MaterialTheme.colorScheme.secondary,
                title = current.title,
                message = current.message,
                actions = {
                    Button(
                        onClick = onExit,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    ) { Text(current.backLabel) }
                },
            )
        }
        is StudySessionUiState.Complete -> {
            val accuracy = if (current.answeredCount > 0) {
                ((current.correctCount.toFloat() / current.answeredCount) * 100f).roundToInt()
            } else {
                0
            }
            SessionOutcomeShell(
                icon = Icons.Outlined.CheckCircle,
                accent = MaterialTheme.colorScheme.secondary,
                title = current.title,
                message = current.message,
                extraContent = {
                    if (current.mode != SessionMode.CARDS) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MetricTile(
                                label = "Practiced",
                                value = current.answeredCount.toString(),
                                accent = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            MetricTile(
                                label = "Accuracy",
                                value = "$accuracy%",
                                accent = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                },
                actions = {
                    Button(
                        onClick = onExit,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    ) { Text(current.backLabel) }
                },
            )
        }
        is StudySessionUiState.Active -> {
            ActiveSessionContent(
                state = current,
                onReveal = viewModel::reveal,
                onRate = viewModel::rate,
                onSelectQuizOption = viewModel::selectQuizOption,
                onAdvanceQuiz = viewModel::advanceAfterQuiz,
            )
        }
    }
}

@Composable
private fun SessionOutcomeShell(
    icon: ImageVector,
    accent: Color,
    title: String,
    message: String,
    extraContent: @Composable () -> Unit = {},
    actions: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        ForgeCard(modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                extraContent()
                actions()
            }
        }
    }
}

@Composable
private fun ActiveSessionContent(
    state: StudySessionUiState.Active,
    onReveal: () -> Unit,
    onRate: (Rating) -> Unit,
    onSelectQuizOption: (String?) -> Unit,
    onAdvanceQuiz: () -> Unit,
) {
    val card = state.card
    val speech = LocalSpeechSpeaker.current
    val speechReady by speech.isReady.collectAsStateWithLifecycle()
    val haptics = rememberKanjiHaptics()
    val reduceMotion = rememberReduceMotion()
    val themeColors = KanjiForgeTheme.colors
    val modeLabel = when (state.mode) {
        SessionMode.CARDS -> "Flashcards"
        SessionMode.QUIZ -> "Multiple choice"
        SessionMode.WRITING -> "Writing practice"
    }

    LaunchedEffect(state.index, card.summary.code, card.revealed, state.autoPlayAudio, speechReady) {
        if (state.mode == SessionMode.CARDS && card.revealed && state.autoPlayAudio && speechReady) {
            speech.speak(card.spokenReading)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val headerText = state.headerSubtitle?.let { "$modeLabel · $it" } ?: modeLabel
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${state.index + 1} / ${state.total}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ForgeProgressBar(
                progress = ((state.index + 1f) / state.total.coerceAtLeast(1)).coerceIn(0f, 1f),
                height = 3.dp,
            )
        }

        AnimatedContent(
            targetState = state.index to card.summary.code,
            transitionSpec = {
                if (reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (fadeIn(tween(180)) + slideInHorizontally(tween(180)) { it / 16 }) togetherWith fadeOut(tween(130))
                }
            },
            modifier = Modifier.weight(1f).widthIn(max = 680.dp).fillMaxWidth(),
            label = "study-session-card",
        ) { targetPair ->
            val snapshotHolder = remember { mutableStateOf(state) }
            if (targetPair == (state.index to card.summary.code)) {
                snapshotHolder.value = state
            }
            val shown = snapshotHolder.value
            val shownCard = shown.card

            when (shown.mode) {
                SessionMode.CARDS -> FlashcardBody(
                    shown = shown,
                    card = shownCard,
                    reduceMotion = reduceMotion,
                    onReveal = onReveal,
                )
                SessionMode.QUIZ -> {
                    val quiz = shownCard.quiz
                    if (quiz != null) {
                        QuizBody(
                            card = shownCard,
                            quiz = quiz,
                            isSubmitting = shown.isSubmitting,
                            onSelectOption = { option ->
                                haptics(if (option == quiz.correctAnswer) KanjiHaptic.CONFIRM else KanjiHaptic.REJECT)
                                onSelectQuizOption(option)
                            },
                        )
                    }
                }
                SessionMode.WRITING -> WritingBody(cardIndex = shown.index, card = shownCard)
            }
        }

        Surface(
            color = themeColors.dockContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalDivider(thickness = 0.75.dp, color = themeColors.dockBorder)
                Column(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (state.mode) {
                        SessionMode.CARDS -> {
                            if (!card.revealed) {
                                Button(
                                    onClick = {
                                        haptics(KanjiHaptic.TAP)
                                        onReveal()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                                ) {
                                    Text("Reveal answer", style = MaterialTheme.typography.labelLarge)
                                }
                            } else {
                                RatingButtons(enabled = !state.isSubmitting, onRate = onRate)
                            }
                        }

                        SessionMode.QUIZ -> {
                            val quiz = card.quiz
                            if (quiz != null) {
                                if (quiz.selectedAnswer == null) {
                                    OutlinedButton(
                                        onClick = {
                                            haptics(KanjiHaptic.TAP)
                                            onSelectQuizOption(null)
                                        },
                                        enabled = !state.isSubmitting,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    ) {
                                        Text("I don't know", style = MaterialTheme.typography.labelLarge)
                                    }
                                } else {
                                    val verdictIcon = when {
                                        quiz.wasCorrect -> Icons.Outlined.CheckCircle
                                        quiz.didNotKnow -> Icons.Outlined.Info
                                        else -> Icons.Outlined.Cancel
                                    }
                                    val verdictColor = when {
                                        quiz.wasCorrect -> themeColors.onSuccessContainer
                                        quiz.didNotKnow -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else -> MaterialTheme.colorScheme.onErrorContainer
                                    }
                                    val bannerColor = when {
                                        quiz.wasCorrect -> themeColors.successContainer
                                        quiz.didNotKnow -> MaterialTheme.colorScheme.surfaceVariant
                                        else -> MaterialTheme.colorScheme.errorContainer
                                    }
                                    Surface(
                                        color = bannerColor,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Icon(verdictIcon, contentDescription = null, tint = verdictColor)
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    when {
                                                        quiz.wasCorrect -> "Correct"
                                                        quiz.didNotKnow -> "Keep this one in mind"
                                                        else -> "Not quite"
                                                    },
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = verdictColor,
                                                )
                                                Text(
                                                    "Answer: ${quiz.correctAnswer}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = verdictColor,
                                                )
                                            }
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            haptics(KanjiHaptic.TAP)
                                            onAdvanceQuiz()
                                        },
                                        enabled = !state.isSubmitting,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                                    ) {
                                        Text(
                                            if (state.index + 1 >= state.total) "Finish session" else "Next",
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }
                            }
                        }

                        SessionMode.WRITING -> {
                            RatingButtons(enabled = !state.isSubmitting, showHeader = false, onRate = onRate)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlashcardBody(
    shown: StudySessionUiState.Active,
    card: StudyCardState,
    reduceMotion: Boolean,
    onReveal: () -> Unit,
) {
    val readings = card.detail.on + card.detail.kun
    val scrollState = rememberScrollState()
    val haptics = rememberKanjiHaptics()
    val colors = KanjiForgeTheme.colors

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = if (!card.revealed) Arrangement.Center else Arrangement.Top,
    ) {
        ForgeCard(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (!card.revealed) {
                        Modifier.clickable {
                            haptics(KanjiHaptic.TAP)
                            onReveal()
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (!card.revealed) 300.dp else 0.dp)
                    .padding(28.dp),
                verticalArrangement = if (!card.revealed) Arrangement.Center else Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (card.isNewCard) "New kanji" else when (card.cardDirection) {
                        CardDirection.MEANING -> "Meaning"
                        CardDirection.READING -> "Reading"
                        CardDirection.KANJI -> "Character"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!card.revealed) Spacer(Modifier.height(18.dp))
                if (card.isNewCard && shown.index == 0 && !card.revealed) {
                    Text(
                        "Study this character, then reveal its meaning and readings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (card.cardDirection == CardDirection.KANJI) {
                    Text(
                        text = card.summary.character,
                        fontFamily = FontFamily.Serif,
                        fontSize = 92.sp,
                        lineHeight = 104.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        when (card.cardDirection) {
                            CardDirection.MEANING -> card.summary.meaning.ifBlank { card.detail.translation.ifBlank { card.summary.character } }
                            CardDirection.READING -> readings.firstOrNull() ?: card.summary.meaning
                            CardDirection.KANJI -> card.summary.character
                        },
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                }

                if (!card.revealed) {
                    Spacer(Modifier.height(22.dp))
                    Text(
                        "Tap card to reveal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }

                AnimatedVisibility(
                    visible = card.revealed,
                    enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(180)) + expandVertically(tween(200)),
                    exit = ExitTransition.None,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        if (card.cardDirection != CardDirection.KANJI) {
                            Text(
                                card.summary.character,
                                fontFamily = FontFamily.Serif,
                                fontSize = 64.sp,
                                lineHeight = 72.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    card.summary.meaning.ifBlank { "Meaning unavailable" },
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (card.detail.translation.isNotBlank() && card.detail.translation != card.summary.meaning) {
                                    Text(
                                        card.detail.translation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                JapaneseSpeechButton(card.spokenReading)
                            }
                        }

                        if (card.detail.on.isNotEmpty() || card.detail.kun.isNotEmpty()) {
                            ReadingChipRow(on = card.detail.on, kun = card.detail.kun)
                        }

                        val meta = listOfNotNull(
                            card.summary.jlpt?.let { "JLPT N$it" },
                            card.summary.strokeCount?.let { "$it strokes" },
                        )
                        if (meta.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                meta.forEach { label ->
                                    MetaPill(text = label)
                                }
                            }
                        }

                        if (card.detail.strokePaths.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StrokePathCanvas(
                                    paths = card.detail.strokePaths,
                                    modifier = Modifier.widthIn(max = 172.dp).fillMaxWidth().aspectRatio(1f),
                                    showGuide = true,
                                    activeCount = 0,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizBody(
    card: StudyCardState,
    quiz: QuizQuestionState,
    isSubmitting: Boolean,
    onSelectOption: (String) -> Unit,
) {
    val themeColors = KanjiForgeTheme.colors
    val scrollState = rememberScrollState()
    val optionLabels = listOf("A", "B", "C", "D", "E", "F")

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ForgeCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    when (quiz.prompt) {
                        QuizPrompt.MEANING -> "What does this kanji mean?"
                        QuizPrompt.READING -> "Which reading is correct?"
                        QuizPrompt.WORD -> "Which word uses this kanji?"
                        QuizPrompt.SENTENCE -> "Which sentence uses this kanji?"
                        QuizPrompt.MIXED -> "Choose the best answer"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    card.summary.character,
                    fontFamily = FontFamily.Serif,
                    fontSize = 80.sp,
                    lineHeight = 90.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            quiz.options.forEachIndexed { index, option ->
                val answered = quiz.selectedAnswer != null
                val correctOption = answered && option == quiz.correctAnswer
                val wrongChoice = quiz.selectedAnswer == option && option != quiz.correctAnswer
                val optionColor = when {
                    !answered -> MaterialTheme.colorScheme.surface
                    correctOption -> themeColors.successContainer
                    wrongChoice -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                }
                val borderColor = when {
                    !answered -> themeColors.cardBorder
                    correctOption -> themeColors.ratingGood.copy(alpha = 0.7f)
                    wrongChoice -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    else -> themeColors.cardBorder.copy(alpha = 0.5f)
                }
                val contentColor = when {
                    correctOption -> themeColors.onSuccessContainer
                    wrongChoice -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurface
                }

                ForgeCard(
                    onClick = { onSelectOption(option) },
                    enabled = !answered && !isSubmitting,
                    containerColor = optionColor,
                    borderColor = borderColor,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(contentColor.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = optionLabels.getOrElse(index) { "${index + 1}" },
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor,
                            )
                        }
                        Text(
                            option,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor,
                        )
                        if (correctOption) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = "Correct option", tint = contentColor)
                        } else if (wrongChoice) {
                            Icon(Icons.Outlined.Cancel, contentDescription = "Selected wrong option", tint = contentColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WritingBody(
    cardIndex: Int,
    card: StudyCardState,
) {
    val strokes = card.detail.strokePaths
    val haptics = rememberKanjiHaptics()
    val colors = KanjiForgeTheme.colors
    var isDrawing by remember { mutableStateOf(false) }
    var drawnPaths by remember(cardIndex, card.summary.code) { mutableStateOf<List<List<Offset>>>(emptyList()) }
    val playbackController = rememberStrokePlaybackController(
        resetKey = cardIndex to card.summary.code,
        totalStrokes = strokes.size,
        initialShowGuide = true,
    )
    val playback = playbackController.state
    val readingsText = "${card.summary.meaning}  ·  ${(card.detail.on + card.detail.kun).take(2).joinToString(" / ")}"

    @Composable
    fun WritingPadSurface(modifier: Modifier) {
        Box(modifier) {
            if (strokes.isNotEmpty()) {
                val showBgGuide = playback.showGuide && !playback.isPlaying
                val activeVisible = if (playback.isPlaying || (!playback.showGuide && playback.visibleCount > 0)) {
                    playback.visibleCount
                } else {
                    0
                }
                StrokePathCanvas(
                    paths = strokes,
                    modifier = Modifier.matchParentSize(),
                    showGuide = showBgGuide,
                    activeCount = activeVisible,
                    drawBackground = true,
                )
            } else {
                StrokePathCanvas(
                    paths = emptyList(),
                    modifier = Modifier.matchParentSize(),
                    showGuide = false,
                    activeCount = 0,
                    drawBackground = true,
                )
            }
            KanjiDrawingPad(
                paths = drawnPaths,
                onPathsChange = { drawnPaths = it },
                onDrawingChanged = { isDrawing = it },
                modifier = Modifier.matchParentSize(),
                drawBackground = false,
            )
            if (strokes.isEmpty()) {
                Text(
                    "Stroke guide unavailable",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    @Composable
    fun WritingPromptCard() {
        ForgeCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(readingsText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    if (strokes.isNotEmpty()) {
                        Text(
                            "${drawnPaths.size} / ${strokes.size} strokes",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    JapaneseSpeechButton(card.spokenReading, contentDescription = "Play character reading")
                }
            }
        }
    }

    @Composable
    fun DrawingActionRow() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    haptics(KanjiHaptic.TICK)
                    drawnPaths = drawnPaths.dropLast(1)
                },
                enabled = drawnPaths.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Undo", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = {
                    haptics(KanjiHaptic.TAP)
                    drawnPaths = emptyList()
                },
                enabled = drawnPaths.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.Clear, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Clear", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        val isLandscape = maxWidth >= 560.dp && maxWidth > maxHeight
        if (isLandscape) {
            val padSize = (maxHeight - 8.dp).coerceAtMost(maxWidth * 0.45f).coerceAtLeast(160.dp)
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WritingPadSurface(Modifier.size(padSize))
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WritingPromptCard()
                    DrawingActionRow()
                    StrokeOrderControls(
                        state = playback,
                        onTogglePlay = playbackController::togglePlay,
                        onStepNext = playbackController::stepNext,
                        showGuideToggle = true,
                        onToggleGuide = playbackController::toggleGuide,
                    )
                    Text(
                        "Compare by eye — handwriting isn't auto-scored. Rate your recall below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState, enabled = !isDrawing),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WritingPromptCard()
                WritingPadSurface(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 300.dp)
                        .aspectRatio(1f)
                        .align(Alignment.CenterHorizontally),
                )
                DrawingActionRow()
                StrokeOrderControls(
                    state = playback,
                    onTogglePlay = playbackController::togglePlay,
                    onStepNext = playbackController::stepNext,
                    showGuideToggle = true,
                    onToggleGuide = playbackController::toggleGuide,
                )
                Text(
                    "Compare by eye — handwriting isn't auto-scored. Rate your recall below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RatingButtons(
    enabled: Boolean,
    showHeader: Boolean = true,
    onRate: (Rating) -> Unit,
) {
    val haptics = rememberKanjiHaptics()
    val themeColors = KanjiForgeTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showHeader) {
            Text(
                "Rate your recall",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Rating.entries.forEach { rating ->
                val accent = when (rating) {
                    Rating.AGAIN -> themeColors.ratingAgain
                    Rating.HARD -> themeColors.ratingHard
                    Rating.GOOD -> themeColors.ratingGood
                    Rating.EASY -> themeColors.ratingEasy
                }
                OutlinedButton(
                    onClick = {
                        haptics(if (rating == Rating.AGAIN) KanjiHaptic.REJECT else KanjiHaptic.CONFIRM)
                        onRate(rating)
                    },
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.75.dp, accent.copy(alpha = if (enabled) 0.45f else 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = accent.copy(alpha = if (enabled) 0.08f else 0.03f),
                        contentColor = accent,
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                ) {
                    Text(
                        rating.displayLabel(),
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
