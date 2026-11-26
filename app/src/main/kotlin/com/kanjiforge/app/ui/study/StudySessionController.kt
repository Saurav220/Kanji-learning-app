package com.kanjiforge.app.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanjiforge.app.data.reference.ReferenceCatalog
import com.kanjiforge.app.data.reference.model.KanjiDetail
import com.kanjiforge.app.data.reference.model.KanjiSummary
import com.kanjiforge.app.data.reference.model.QuizCandidateInfo
import com.kanjiforge.app.domain.CardDirection
import com.kanjiforge.app.domain.QuizPrompt
import com.kanjiforge.app.domain.Rating
import com.kanjiforge.app.domain.ReviewRecord
import com.kanjiforge.app.domain.ReviewType
import com.kanjiforge.app.domain.SessionMode
import com.kanjiforge.app.domain.SessionRecord
import com.kanjiforge.app.domain.SessionSpec
import com.kanjiforge.app.domain.StudyPreferences
import com.kanjiforge.app.domain.StudyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

data class QuizQuestionState(
    val prompt: QuizPrompt,
    val options: List<String>,
    val correctAnswer: String,
    val selectedAnswer: String? = null,
    val didNotKnow: Boolean = false,
    val wasCorrect: Boolean = false,
)

data class StudyCardState(
    val summary: KanjiSummary,
    val detail: KanjiDetail,
    val isNewCard: Boolean,
    val cardDirection: CardDirection,
    val spokenReading: String,
    val revealed: Boolean = false,
    val quiz: QuizQuestionState? = null,
)

sealed interface StudySessionUiState {
    data object Loading : StudySessionUiState
    data class Active(
        val card: StudyCardState,
        val index: Int,
        val total: Int,
        val mode: SessionMode,
        val headerSubtitle: String?,
        val autoPlayAudio: Boolean,
        val isSubmitting: Boolean = false,
    ) : StudySessionUiState

    data class Empty(
        val title: String,
        val message: String,
        val backLabel: String,
    ) : StudySessionUiState

    data class Complete(
        val title: String,
        val message: String,
        val mode: SessionMode,
        val answeredCount: Int,
        val correctCount: Int,
        val backLabel: String,
    ) : StudySessionUiState

    data class Error(val message: String) : StudySessionUiState
}

class StudySessionController(
    private val mode: SessionMode,
    private val spec: SessionSpec,
    private val catalog: ReferenceCatalog,
    private val store: StudyStore,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val randomSeed: Long = clock.millis(),
    private val scope: CoroutineScope,
    private val saveScope: CoroutineScope = scope,
) {
    private val _state = MutableStateFlow<StudySessionUiState>(StudySessionUiState.Loading)
    val state: StateFlow<StudySessionUiState> = _state.asStateFlow()

    private val sessionId = UUID.randomUUID().toString()
    private val startedAt = clock.millis()
    private val finishMutex = Mutex()

    private var queue = mutableListOf<KanjiSummary>()
    private var currentIndex = 0
    private var reviewedAtStart = emptySet<String>()
    private var missedCardIds = emptySet<String>()
    private var answeredCount = 0
    private var correctCount = 0
    private var sessionSaved = false
    private var completed = false
    private var exitTriggered = false

    init {
        loadSession()
    }

    fun retry() {
        loadSession()
    }

    private fun loadSession() {
        scope.launch {
            _state.value = StudySessionUiState.Loading
            currentIndex = 0
            missedCardIds = emptySet()
            answeredCount = 0
            correctCount = 0
            completed = false
            sessionSaved = false

            try {
                val now = clock.millis()
                val prefs = store.preferences.first()
                val allReviews = store.reviews.first()
                val kanjiReviews = allReviews.filter { it.type == ReviewType.KANJI }
                reviewedAtStart = kanjiReviews.asSequence().map { it.id }.toSet()

                val zone = clock.zone
                val localStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                    .atStartOfDay(zone).toInstant().toEpochMilli()
                val newToday = kanjiReviews.count { it.firstReviewedAt >= localStart }
                val remainingNew = (prefs.dailyNew - newToday).coerceAtLeast(0)

                val builtQueue = buildQueue(spec, kanjiReviews, remainingNew, now)
                queue = builtQueue.toMutableList()

                if (queue.isEmpty()) {
                    _state.value = emptyStateFor(spec)
                    return@launch
                }

                val firstCard = prepareCard(index = 0, summary = queue[0], prefs = prefs)
                _state.value = StudySessionUiState.Active(
                    card = firstCard,
                    index = 0,
                    total = queue.size,
                    mode = mode,
                    headerSubtitle = headerSubtitleFor(spec, queue.size),
                    autoPlayAudio = prefs.autoPlayAudio,
                    isSubmitting = false,
                )
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.value = StudySessionUiState.Error(
                    error.message ?: "Could not build this study session.",
                )
            }
        }
    }

    fun reveal() {
        val current = _state.value as? StudySessionUiState.Active ?: return
        if (current.card.revealed || current.isSubmitting) return
        _state.value = current.copy(card = current.card.copy(revealed = true))
    }

    fun rate(rating: Rating) {
        val current = _state.value as? StudySessionUiState.Active ?: return
        if (current.isSubmitting) return
        _state.value = current.copy(isSubmitting = true)
        scope.launch {
            try {
                store.recordReview(ReviewType.KANJI, current.card.summary.character, rating, clock.millis())
                val wasCorrect = rating == Rating.GOOD || rating == Rating.EASY
                advanceInternal(wasCorrect = wasCorrect, currentCard = current.card.summary)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                val latest = _state.value as? StudySessionUiState.Active
                if (latest != null) _state.value = latest.copy(isSubmitting = false)
            }
        }
    }

    fun selectQuizOption(option: String?) {
        val current = _state.value as? StudySessionUiState.Active ?: return
        val quiz = current.card.quiz ?: return
        if (quiz.selectedAnswer != null || current.isSubmitting) return
        _state.value = current.copy(isSubmitting = true)

        scope.launch {
            try {
                val isCorrect = option != null && option == quiz.correctAnswer
                store.recordReview(
                    ReviewType.KANJI,
                    current.card.summary.character,
                    if (isCorrect) Rating.GOOD else Rating.AGAIN,
                    clock.millis(),
                )
                val updatedQuiz = quiz.copy(
                    selectedAnswer = option.orEmpty(),
                    didNotKnow = option.isNullOrBlank(),
                    wasCorrect = isCorrect,
                )
                _state.value = current.copy(
                    card = current.card.copy(quiz = updatedQuiz),
                    isSubmitting = false,
                )
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                val latest = _state.value as? StudySessionUiState.Active
                if (latest != null) _state.value = latest.copy(isSubmitting = false)
            }
        }
    }

    fun advanceAfterQuiz() {
        val current = _state.value as? StudySessionUiState.Active ?: return
        val quiz = current.card.quiz ?: return
        if (quiz.selectedAnswer == null || current.isSubmitting) return
        _state.value = current.copy(isSubmitting = true)
        scope.launch {
            try {
                advanceInternal(wasCorrect = quiz.wasCorrect, currentCard = current.card.summary)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                val latest = _state.value as? StudySessionUiState.Active
                if (latest != null) _state.value = latest.copy(isSubmitting = false)
            }
        }
    }

    suspend fun finishIfAnswered() {
        finishMutex.withLock {
            if (!sessionSaved && answeredCount > 0) {
                sessionSaved = true
                withContext(NonCancellable) {
                    store.finishSession(
                        SessionRecord(
                            id = sessionId,
                            mode = mode,
                            startedAt = startedAt,
                            finishedAt = clock.millis(),
                            total = answeredCount,
                            correct = correctCount,
                        ),
                    )
                }
            }
        }
    }

    fun finishAndExit(onDone: () -> Unit) {
        if (exitTriggered) return
        exitTriggered = true
        scope.launch {
            try {
                finishIfAnswered()
            } finally {
                onDone()
            }
        }
    }

    fun saveProgressOnDestroy() {
        if (!sessionSaved && answeredCount > 0) {
            saveScope.launch { finishIfAnswered() }
        }
    }

    private suspend fun advanceInternal(wasCorrect: Boolean, currentCard: KanjiSummary) {
        val prefs = store.preferences.first()
        if (mode == SessionMode.QUIZ && !wasCorrect) {
            val updated = appendMissedCard(
                existingQueue = queue,
                card = currentCard,
                retriedIds = missedCardIds,
                shouldRepeat = prefs.repeatWrongAnswers,
            )
            queue = updated.first.toMutableList()
            missedCardIds = updated.second
        }
        answeredCount += 1
        if (wasCorrect) correctCount += 1

        val nextIndex = currentIndex + 1
        if (nextIndex >= queue.size) {
            completed = true
            finishIfAnswered()
            _state.value = completeStateFor(spec, answeredCount, correctCount)
        } else {
            currentIndex = nextIndex
            val nextCard = prepareCard(index = nextIndex, summary = queue[nextIndex], prefs = prefs)
            _state.value = StudySessionUiState.Active(
                card = nextCard,
                index = nextIndex,
                total = queue.size,
                mode = mode,
                headerSubtitle = headerSubtitleFor(spec, queue.size),
                autoPlayAudio = prefs.autoPlayAudio,
                isSubmitting = false,
            )
        }
    }

    private suspend fun buildQueue(
        spec: SessionSpec,
        kanjiReviews: List<ReviewRecord>,
        remainingNew: Int,
        now: Long,
    ): List<KanjiSummary> = when (spec) {
        is SessionSpec.Collection -> {
            val items = store.getSetItems(spec.setId).filter { it.type == ReviewType.KANJI }
            val summaries = catalog.getKanjiSummaries(items.map { it.itemId })
            items.mapNotNull { summaries[it.itemId] }
        }
        is SessionSpec.SingleKanji -> {
            catalog.getKanjiByCode(spec.code)?.summary?.let(::listOf).orEmpty()
        }
        is SessionSpec.SequenceSet -> {
            val deck = catalog.searchStudyDeck(
                family = spec.family,
                level = spec.level,
                offset = spec.offset,
                limit = spec.batchSize.coerceIn(1, 100),
            )
            buildStudyDeckBatch(
                deck = deck,
                reviews = kanjiReviews,
                remainingNew = remainingNew,
                now = now,
                batchSize = spec.batchSize.coerceIn(1, 100),
            )
        }
        SessionSpec.DailyReview -> {
            val due = kanjiReviews.filter { it.dueAt <= now }.sortedBy { it.dueAt }.take(30)
            val dueByChar = catalog.getKanjiSummaries(due.map { it.id })
            val dueCards = due.mapNotNull { dueByChar[it.id] }
            val seen = kanjiReviews.asSequence().map { it.id }.toSet()
            val unseen = mutableListOf<KanjiSummary>()
            var pageOffset = 0
            while (unseen.size < remainingNew && pageOffset < 7071) {
                val page = catalog.searchKanji("", offset = pageOffset, limit = 100)
                if (page.items.isEmpty()) break
                unseen += page.items.filter { it.character !in seen }.take(remainingNew - unseen.size)
                if (!page.hasMore) break
                pageOffset += page.items.size
            }
            (dueCards + unseen).distinctBy { it.code }
        }
    }

    private suspend fun prepareCard(
        index: Int,
        summary: KanjiSummary,
        prefs: StudyPreferences,
    ): StudyCardState {
        val detail = catalog.getKanji(summary.character) ?: minimalDetail(summary)
        val isNewCard = summary.character !in reviewedAtStart
        val direction = if (isNewCard) CardDirection.KANJI else prefs.cardDirection
        val spokenReading = (detail.on + detail.kun).firstOrNull().orEmpty()

        val quiz = if (mode == SessionMode.QUIZ) {
            buildQuizQuestion(index = index, target = summary, detail = detail, preferred = prefs.quizPrompt)
        } else {
            null
        }

        return StudyCardState(
            summary = summary,
            detail = detail,
            isNewCard = isNewCard,
            cardDirection = direction,
            spokenReading = spokenReading,
            revealed = false,
            quiz = quiz,
        )
    }

    private suspend fun buildQuizQuestion(
        index: Int,
        target: KanjiSummary,
        detail: KanjiDetail,
        preferred: QuizPrompt,
    ): QuizQuestionState {
        val pool = when (spec) {
            is SessionSpec.SequenceSet -> catalog.searchStudyDeck(spec.family, spec.level, limit = 100)
            else -> catalog.searchKanji("", offset = (index * 11) % 6500, limit = 100).items
        }
        val distractors = pool.filter { it.code != target.code }
            .shuffled(Random(randomSeed + index))
            .take(11)
        val allCandidates = (distractors + target).distinctBy { it.code }

        val includeWords = preferred == QuizPrompt.WORD || preferred == QuizPrompt.MIXED
        val includeSentences = preferred == QuizPrompt.SENTENCE || preferred == QuizPrompt.MIXED
        val candidateInfos = catalog.getQuizCandidates(
            codes = allCandidates.map { it.code },
            includeWords = includeWords,
            includeSentences = includeSentences,
        )

        val targetInfo = candidateInfos[target.code]
        val hasWord = !targetInfo?.sampleWord.isNullOrBlank() ||
            detail.words.any { word -> word.forms.any { it.contains(target.character) } }
        val hasSentence = !targetInfo?.sampleSentence.isNullOrBlank() ||
            detail.sentences.any { it.plainText.isNotBlank() }

        val prompt = resolveQuizPrompt(preferred, index, hasWord, hasSentence)

        fun answerText(item: KanjiSummary): String {
            val info: QuizCandidateInfo? = candidateInfos[item.code]
            return when (prompt) {
                QuizPrompt.MEANING, QuizPrompt.MIXED -> item.meaning.ifBlank { item.character }
                QuizPrompt.READING -> {
                    val reading = info?.readings?.firstOrNull()
                        ?: if (item.code == target.code) (detail.on + detail.kun).firstOrNull() else null
                    reading.orEmpty().ifBlank { item.meaning }
                }
                QuizPrompt.WORD -> {
                    val word = info?.sampleWord ?: if (item.code == target.code) {
                        detail.words.asSequence().flatMap { it.forms.asSequence() }
                            .firstOrNull { it.contains(item.character) }
                    } else null
                    word.orEmpty().ifBlank { item.meaning }
                }
                QuizPrompt.SENTENCE -> {
                    val sentence = info?.sampleSentence ?: if (item.code == target.code) {
                        detail.sentences.firstOrNull { it.plainText.isNotBlank() }?.plainText
                    } else null
                    sentence.orEmpty().ifBlank { item.meaning }
                }
            }
        }

        val correct = answerText(target)
        val options = makeQuizOptions(
            correct = correct,
            distractors = distractors.map(::answerText),
            limit = 4,
            random = Random(randomSeed + 31L * index),
        )
        return QuizQuestionState(
            prompt = prompt,
            options = options,
            correctAnswer = correct,
        )
    }

    private fun buildStudyDeckBatch(
        deck: List<KanjiSummary>,
        reviews: List<ReviewRecord>,
        remainingNew: Int,
        now: Long,
        batchSize: Int,
    ): List<KanjiSummary> {
        if (batchSize <= 0) return emptyList()
        val reviewed = reviews.asSequence()
            .filter { it.type == ReviewType.KANJI }
            .associateBy { it.id }
        val unseen = deck.filter { it.character !in reviewed }.take(remainingNew.coerceAtLeast(0))
        val practiced = deck.mapNotNull { item -> reviewed[item.character]?.let { it to item } }
        val due = practiced.filter { it.first.dueAt <= now }.sortedBy { it.first.dueAt }.map { it.second }
        val older = practiced.sortedBy { it.first.lastReviewedAt }.map { it.second }
        return (unseen + due + older).distinctBy { it.character }.take(batchSize)
    }

    private fun makeQuizOptions(
        correct: String,
        distractors: List<String>,
        limit: Int,
        random: Random,
    ): List<String> {
        if (correct.isBlank() || limit <= 0) return emptyList()
        val answers = (distractors + correct).filter(String::isNotBlank).distinct()
        if (answers.size <= limit) return answers.shuffled(random)
        return (answers.filterNot { it == correct }.shuffled(random).take(limit - 1) + correct).shuffled(random)
    }

    private fun resolveQuizPrompt(
        preferred: QuizPrompt,
        position: Int,
        hasWord: Boolean,
        hasSentence: Boolean,
    ): QuizPrompt {
        val available = buildList {
            add(QuizPrompt.MEANING)
            add(QuizPrompt.READING)
            if (hasWord) add(QuizPrompt.WORD)
            if (hasSentence) add(QuizPrompt.SENTENCE)
        }
        return when (preferred) {
            QuizPrompt.MIXED -> available[position.mod(available.size)]
            QuizPrompt.WORD -> if (hasWord) QuizPrompt.WORD else QuizPrompt.READING
            QuizPrompt.SENTENCE -> if (hasSentence) QuizPrompt.SENTENCE else QuizPrompt.READING
            else -> preferred
        }
    }

    private fun appendMissedCard(
        existingQueue: List<KanjiSummary>,
        card: KanjiSummary,
        retriedIds: Set<String>,
        shouldRepeat: Boolean,
    ): Pair<List<KanjiSummary>, Set<String>> {
        if (!shouldRepeat || card.character in retriedIds) return existingQueue to retriedIds
        return (existingQueue + card) to (retriedIds + card.character)
    }

    private fun headerSubtitleFor(spec: SessionSpec, size: Int): String? = when (spec) {
        is SessionSpec.SequenceSet -> {
            val unit = if (size == 1) "CARD" else "CARDS"
            "${spec.family.levelLabel(spec.level)} · SET ${spec.offset / 10 + 1} · $size $unit"
        }
        else -> null
    }

    private fun emptyStateFor(spec: SessionSpec): StudySessionUiState.Empty = when (spec) {
        is SessionSpec.SequenceSet -> StudySessionUiState.Empty(
            title = "No cards are ready in this set right now.",
            message = "No reviews are due here, and today's new-kanji limit may be reached. Choose another set or come back tomorrow.",
            backLabel = "Back to sets",
        )
        is SessionSpec.Collection -> StudySessionUiState.Empty(
            title = "No characters to study.",
            message = "Add kanji to this collection first, then start a study session.",
            backLabel = "Back to collection",
        )
        is SessionSpec.SingleKanji -> StudySessionUiState.Empty(
            title = "Character unavailable.",
            message = "This kanji could not be loaded from the local catalog.",
            backLabel = "Back to character",
        )
        SessionSpec.DailyReview -> StudySessionUiState.Empty(
            title = "You’re all caught up.",
            message = "No due or new kanji are waiting right now. Choose a character from the library to practice it directly.",
            backLabel = "Back to study",
        )
    }

    private fun completeStateFor(
        spec: SessionSpec,
        answered: Int,
        correct: Int,
    ): StudySessionUiState.Complete = when (spec) {
        is SessionSpec.SequenceSet -> StudySessionUiState.Complete(
            title = "${spec.family.levelLabel(spec.level)} set ${spec.offset / 10 + 1} complete.",
            message = "$answered cards practiced.",
            mode = mode,
            answeredCount = answered,
            correctCount = correct,
            backLabel = "Back to sets",
        )
        is SessionSpec.Collection -> StudySessionUiState.Complete(
            title = "Collection session complete.",
            message = "$answered cards practiced.",
            mode = mode,
            answeredCount = answered,
            correctCount = correct,
            backLabel = "Back to collection",
        )
        is SessionSpec.SingleKanji -> StudySessionUiState.Complete(
            title = "Practice complete.",
            message = "$answered card practiced.",
            mode = mode,
            answeredCount = answered,
            correctCount = correct,
            backLabel = "Back to character",
        )
        SessionSpec.DailyReview -> StudySessionUiState.Complete(
            title = "Session complete.",
            message = "$answered cards practiced.",
            mode = mode,
            answeredCount = answered,
            correctCount = correct,
            backLabel = "Back to study",
        )
    }

    private fun minimalDetail(summary: KanjiSummary) = KanjiDetail(
        summary = summary,
        translation = "",
        on = emptyList(),
        kun = emptyList(),
        nanori = emptyList(),
        romanizedReading = "",
        strokePaths = emptyList(),
        radicals = emptyList(),
        decomposition = "",
        classicalRadical = null,
        notes = "",
        words = emptyList(),
        sentences = emptyList(),
        names = emptyList(),
    )
}

class StudySessionViewModel(
    mode: SessionMode,
    spec: SessionSpec,
    catalog: ReferenceCatalog,
    store: StudyStore,
    clock: Clock,
    applicationScope: CoroutineScope,
) : ViewModel() {
    private val controller = StudySessionController(
        mode = mode,
        spec = spec,
        catalog = catalog,
        store = store,
        clock = clock,
        scope = viewModelScope,
        saveScope = applicationScope,
    )

    val state: StateFlow<StudySessionUiState> = controller.state

    fun reveal() = controller.reveal()
    fun rate(rating: Rating) = controller.rate(rating)
    fun selectQuizOption(option: String?) = controller.selectQuizOption(option)
    fun advanceAfterQuiz() = controller.advanceAfterQuiz()
    fun finishAndExit(onDone: () -> Unit) = controller.finishAndExit(onDone)
    fun retry() = controller.retry()

    override fun onCleared() {
        controller.saveProgressOnDestroy()
        super.onCleared()
    }
}
