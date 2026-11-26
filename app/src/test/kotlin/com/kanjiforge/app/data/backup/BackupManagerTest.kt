package com.kanjiforge.app.data.backup

import com.kanjiforge.app.FakeStudyStore
import com.kanjiforge.app.MutableClock
import com.kanjiforge.app.domain.CardDirection
import com.kanjiforge.app.domain.QuizPrompt
import com.kanjiforge.app.domain.Rating
import com.kanjiforge.app.domain.ReviewRecord
import com.kanjiforge.app.domain.ReviewType
import com.kanjiforge.app.domain.SessionMode
import com.kanjiforge.app.domain.SessionRecord
import com.kanjiforge.app.domain.StudyPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class BackupManagerTest {

    @Test
    fun exportAndImportStreamRoundTripPreservesState() = runTest {
        val clock = MutableClock(1_700_000_000_000L)
        val sourceStore = FakeStudyStore(clock)
        sourceStore.toggleFavorite(ReviewType.KANJI, "日", true)
        sourceStore.toggleFavorite(ReviewType.WORD, "42", true)
        sourceStore.createSet("JLPT Core", listOf("日"), listOf(42L))
        sourceStore.recordReview(ReviewType.KANJI, "日", Rating.GOOD)
        sourceStore.finishSession(SessionRecord("s1", SessionMode.CARDS, 100L, 200L, 5, 4))
        sourceStore.updatePreferences {
            StudyPreferences(
                dailyNew = 20,
                cardDirection = CardDirection.READING,
                quizPrompt = QuizPrompt.WORD,
                repeatWrongAnswers = true,
                autoPlayAudio = true,
                amoledDark = true,
            )
        }

        val out = ByteArrayOutputStream()
        val exportResult = BackupManager(sourceStore).exportTo(out)
        assertTrue(exportResult.success)

        val targetStore = FakeStudyStore(clock)
        val importResult = BackupManager(targetStore).importFrom(ByteArrayInputStream(out.toByteArray()))
        assertTrue(importResult.success)

        assertEquals(listOf("日"), targetStore._favorites.first().filter { it.type == ReviewType.KANJI }.map { it.id })
        assertEquals(listOf("42"), targetStore._favorites.first().filter { it.type == ReviewType.WORD }.map { it.id })
        assertEquals(1, targetStore.sets.first().size)
        assertEquals(1, targetStore._reviews.first().size)
        assertEquals(20, targetStore._preferences.first().dailyNew)
        assertEquals(QuizPrompt.WORD, targetStore._preferences.first().quizPrompt)
        assertTrue(targetStore._preferences.first().autoPlayAudio)
        assertTrue(targetStore._preferences.first().amoledDark)
    }

    @Test
    fun supportsBrowserV2BackupDefaultsAndTimestampAwareReviewMerge() = runTest {
        val clock = MutableClock(1_700_000_000_000L)
        val store = FakeStudyStore(clock)
        // Local review is newer than backup review
        store._reviews.value = listOf(
            ReviewRecord(ReviewType.KANJI, "日", 3, 4.0, 2_000_000L, lastReviewedAt = 5_000L, firstReviewedAt = 1_000L, lastRating = Rating.EASY),
        )

        val browserV2Json = """
            {
              "version": 2,
              "favorites": ["木"],
              "sets": [],
              "reviews": {
                "kanji:日": {
                  "type": "kanji",
                  "id": "日",
                  "repetitions": 1,
                  "intervalDays": 1.0,
                  "dueAt": 9999,
                  "lastReviewedAt": 3000,
                  "lastRating": "hard"
                },
                "kanji:木": {
                  "type": "kanji",
                  "id": "木",
                  "repetitions": 2,
                  "intervalDays": 2.0,
                  "dueAt": 19999,
                  "lastReviewedAt": 6000,
                  "lastRating": "good"
                }
              },
              "preferences": {
                "dailyNew": 10,
                "cardDirection": "meaning"
              }
            }
        """.trimIndent()

        val result = BackupManager(store).importFrom(ByteArrayInputStream(browserV2Json.toByteArray(Charsets.UTF_8)))
        assertTrue(result.success)

        val reviews = store._reviews.first().associateBy { it.id }
        assertEquals("Newer local review must not be overwritten by older backup", Rating.EASY, reviews["日"]?.lastRating)
        assertEquals("Missing firstReviewedAt should fall back to lastReviewedAt", 6_000L, reviews["木"]?.firstReviewedAt)
        assertEquals(10, store._preferences.first().dailyNew)
        assertEquals(QuizPrompt.MIXED, store._preferences.first().quizPrompt)
        assertFalse(store._preferences.first().repeatWrongAnswers)
        assertFalse(store._preferences.first().amoledDark)
    }

    @Test
    fun rejectsMalformedOrInvalidBackupsCleanly() = runTest {
        val store = FakeStudyStore()
        val manager = BackupManager(store)

        val wrongVersion = """{"version":1,"favorites":[],"sets":[],"reviews":{}}""".toByteArray()
        assertFalse(manager.importFrom(ByteArrayInputStream(wrongVersion)).success)

        val fractionalDailyNew = """
            {"version":2,"favorites":[],"sets":[],"reviews":{},"preferences":{"dailyNew":10.5}}
        """.trimIndent().toByteArray()
        assertFalse(manager.importFrom(ByteArrayInputStream(fractionalDailyNew)).success)

        val mismatchedKey = """
            {"version":2,"favorites":[],"sets":[],"reviews":{"kanji:wrong":{"type":"kanji","id":"日","repetitions":1,"intervalDays":1,"dueAt":1,"lastReviewedAt":1,"lastRating":"good"}}}
        """.trimIndent().toByteArray()
        assertFalse(manager.importFrom(ByteArrayInputStream(mismatchedKey)).success)
    }
}
