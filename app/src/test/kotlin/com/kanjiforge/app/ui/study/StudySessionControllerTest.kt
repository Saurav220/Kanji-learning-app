package com.kanjiforge.app.ui.study

import com.kanjiforge.app.FakeReferenceCatalog
import com.kanjiforge.app.FakeStudyStore
import com.kanjiforge.app.MutableClock
import com.kanjiforge.app.data.reference.model.KanjiDetail
import com.kanjiforge.app.data.reference.model.KanjiSummary
import com.kanjiforge.app.data.reference.model.SentenceSummary
import com.kanjiforge.app.data.reference.model.StudySequenceFamily
import com.kanjiforge.app.data.reference.model.WordSummary
import com.kanjiforge.app.domain.CardDirection
import com.kanjiforge.app.domain.QuizPrompt
import com.kanjiforge.app.domain.Rating
import com.kanjiforge.app.domain.ReviewRecord
import com.kanjiforge.app.domain.ReviewType
import com.kanjiforge.app.domain.SessionMode
import com.kanjiforge.app.domain.SessionSpec
import com.kanjiforge.app.domain.StudyPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudySessionControllerTest {

    private fun makeDetail(
        code: Int,
        char: String,
        meaning: String,
        reading: String = "on$code",
        sentenceRaw: String = "",
    ): KanjiDetail {
        val summary = KanjiSummary(code, char, meaning, strokeCount = 4, jlpt = 5, grade = 1, sequence = code)
        return KanjiDetail(
            summary = summary,
            translation = meaning,
            on = listOf(reading),
            kun = emptyList(),
            nanori = emptyList(),
            romanizedReading = reading,
            strokePaths = listOf("M 10 10 L 90 90"),
            radicals = emptyList(),
            decomposition = "",
            classicalRadical = null,
            notes = "",
            words = listOf(WordSummary(code.toLong(), listOf("${char}語"), listOf(reading), listOf(reading), listOf(meaning), listOf("n"), 5, true, "", "")),
            sentences = if (sentenceRaw.isNotEmpty()) listOf(SentenceSummary(code.toLong(), sentenceRaw, "tr")) else emptyList(),
            names = emptyList(),
        )
    }

    @Test
    fun sequenceSetRespectsDailyNewCapOrdersDueBeforeOlderAndResetsRevealOnAdvance() = runTest {
        val clock = MutableClock(1_700_000_000_000L)
        val catalog = FakeReferenceCatalog()
        val store = FakeStudyStore(clock)

        val deck = (1..10).map { i ->
            val ch = Char(0x4E00 + i).toString()
            makeDetail(0x4E00 + i, ch, "meaning-$i").also { catalog.addKanji(it) }.summary
        }
        catalog.sequenceDecks[StudySequenceFamily.JLPT to 5] = deck

        // 2 new kanji already reviewed today, dailyNew = 4 => remainingNew = 2.
        // Cards 1..6 are reviewed (1..3 due, 4..6 older); cards 7..10 are unseen in Set 1 -> 7, 8 picked first.
        val todayStart = clock.millis() - 1_000L
        store._preferences.value = StudyPreferences(dailyNew = 4, cardDirection = CardDirection.MEANING, autoPlayAudio = true)
        store._reviews.value = (1..6).map { i ->
            val ch = Char(0x4E00 + i).toString()
            ReviewRecord(
                type = ReviewType.KANJI,
                id = ch,
                repetitions = 1,
                intervalDays = 1.0,
                dueAt = if (i <= 3) clock.millis() - (10 - i) * 1_000L else clock.millis() + i * 100_000L,
                lastReviewedAt = todayStart + if (i <= 2) 100L else -90_000_000L,
                firstReviewedAt = if (i <= 2) todayStart else todayStart - 90_000_000L,
                lastRating = Rating.GOOD,
            )
        }

        val controller = StudySessionController(
            mode = SessionMode.CARDS,
            spec = SessionSpec.SequenceSet(StudySequenceFamily.JLPT, 5, offset = 0, batchSize = 10),
            catalog = catalog,
            store = store,
            clock = clock,
            scope = this,
        )
        advanceUntilIdle()

        val first = controller.state.value as StudySessionUiState.Active
        // Unseen 7, 8 (2 allowed) -> due 1..3 -> older 4..6 = 8 cards
        assertEquals(8, first.total)
        assertEquals(Char(0x4E00 + 7).toString(), first.card.summary.character)
        assertTrue(first.card.isNewCard)
        assertFalse(first.card.revealed)

        controller.reveal()
        assertTrue((controller.state.value as StudySessionUiState.Active).card.revealed)

        // Rapid double-tap rate only records 1 review for card 0
        controller.rate(Rating.GOOD)
        controller.rate(Rating.EASY)
        advanceUntilIdle()

        val second = controller.state.value as StudySessionUiState.Active
        assertEquals(1, second.index)
        assertEquals(Char(0x4E00 + 8).toString(), second.card.summary.character)
        assertFalse("Advancing must reset revealed before emitting card", second.card.revealed)
    }

    @Test
    fun quizUsesSingleBatchQueryStripsFuriganaAndRetriesMissedCardOnce() = runTest {
        val clock = MutableClock(1_700_000_000_000L)
        val catalog = FakeReferenceCatalog()
        val store = FakeStudyStore(clock)

        val d1 = makeDetail(1, "一", "one", "いち", "{2ことし}今年　は　{ひと}一つ。")
        val d2 = makeDetail(2, "二", "two", "に", "{ふた}二つ　ある。")
        val d3 = makeDetail(3, "三", "three", "さん", "{みっ}三つ。")
        val d4 = makeDetail(4, "四", "four", "よん", "{よっ}四つ。")
        listOf(d1, d2, d3, d4).forEach(catalog::addKanji)
        catalog.sequenceDecks[StudySequenceFamily.JLPT to 5] = listOf(d1.summary, d2.summary)

        store._preferences.value = StudyPreferences(
            quizPrompt = QuizPrompt.SENTENCE,
            repeatWrongAnswers = true,
        )

        val controller = StudySessionController(
            mode = SessionMode.QUIZ,
            spec = SessionSpec.SequenceSet(StudySequenceFamily.JLPT, 5, offset = 0, batchSize = 10),
            catalog = catalog,
            store = store,
            clock = clock,
            randomSeed = 42L,
            scope = this,
        )
        advanceUntilIdle()

        val q1 = controller.state.value as StudySessionUiState.Active
        val quiz1 = q1.card.quiz!!
        assertEquals(QuizPrompt.SENTENCE, quiz1.prompt)
        assertEquals("今年は一つ。", quiz1.correctAnswer)
        assertEquals(1, catalog.getQuizCandidatesCalls)
        assertEquals(true, catalog.lastQuizIncludeSentences)
        assertEquals(false, catalog.lastQuizIncludeWords)
        assertNull(quiz1.selectedAnswer)

        // Miss first card -> should be appended once at end of queue
        controller.selectQuizOption(quiz1.options.first { it != quiz1.correctAnswer })
        advanceUntilIdle()
        controller.advanceAfterQuiz()
        advanceUntilIdle()

        val q2 = controller.state.value as StudySessionUiState.Active
        assertEquals(3, q2.total)
        assertNull("Next card must clear selectedAnswer before emit", q2.card.quiz?.selectedAnswer)

        // Answer card 2 correctly
        controller.selectQuizOption(q2.card.quiz!!.correctAnswer)
        advanceUntilIdle()
        controller.advanceAfterQuiz()
        advanceUntilIdle()

        // Card 3 is the retried card 1; miss it again -> must NOT append a second retry
        val q3 = controller.state.value as StudySessionUiState.Active
        assertEquals("一", q3.card.summary.character)
        controller.selectQuizOption(null)
        advanceUntilIdle()
        controller.advanceAfterQuiz()
        advanceUntilIdle()

        val complete = controller.state.value as StudySessionUiState.Complete
        assertEquals(3, complete.answeredCount)
        assertEquals(1, complete.correctCount)
        assertEquals(1, store._sessions.value.size)
    }

    @Test
    fun finishIfAnsweredIgnoresZeroZeroExitsAndIsIdempotent() = runTest {
        val clock = MutableClock(1_700_000_000_000L)
        val catalog = FakeReferenceCatalog()
        val store = FakeStudyStore(clock)
        catalog.addKanji(makeDetail(1, "日", "day"))

        val controller = StudySessionController(
            mode = SessionMode.WRITING,
            spec = SessionSpec.SingleKanji(1),
            catalog = catalog,
            store = store,
            clock = clock,
            scope = this,
        )
        advanceUntilIdle()

        var exited = false
        controller.finishAndExit { exited = true }
        advanceUntilIdle()

        assertTrue(exited)
        assertEquals("0/0 sessions must not be saved", 0, store._sessions.value.size)

        controller.rate(Rating.GOOD)
        advanceUntilIdle()
        controller.finishIfAnswered()
        advanceUntilIdle()

        assertEquals(1, store._sessions.value.size)
        assertEquals(1, store._sessions.value.first().total)
        assertEquals(1, store._sessions.value.first().correct)
    }
}
