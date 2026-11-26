package com.kanjiforge.app.domain

import com.kanjiforge.app.FakeReferenceCatalog
import com.kanjiforge.app.FakeStudyStore
import com.kanjiforge.app.MutableClock
import com.kanjiforge.app.data.reference.SqliteReferenceCatalog
import com.kanjiforge.app.data.reference.model.KanjiDetail
import com.kanjiforge.app.data.reference.model.KanjiSummary
import com.kanjiforge.app.data.reference.model.PartOfSpeech
import com.kanjiforge.app.data.reference.model.StudySequenceFamily
import com.kanjiforge.app.data.reference.model.WordSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudyCatalogTest {

    @Test
    fun todayDashboardEmitsLoadingThenReadyAndRefreshesOnClockRollover() = runTest {
        val clock = MutableClock(1_700_000_000_000L) // 2023-11-14T22:13:20Z
        val catalog = FakeReferenceCatalog()
        val store = FakeStudyStore(clock)
        val studyCatalog = StudyCatalog(catalog, store, clock, tickIntervalMs = 0L)

        // Review a kanji with AGAIN -> due in 10 minutes
        store.recordReview(ReviewType.KANJI, "日", Rating.AGAIN, clock.millis())

        val collected = mutableListOf<TodayDashboardState>()
        val job = backgroundScope.launch {
            studyCatalog.observeTodayDashboard().collect { collected += it }
        }
        runCurrent()

        assertTrue(collected.first() is TodayDashboardState.Loading)
        val initialReady = collected.last() as TodayDashboardState.Ready
        assertEquals(0, initialReady.dueKanji)
        assertEquals(1, initialReady.newKanjiToday)
        assertFalse(initialReady.showBeginnerPrompt)

        // Advance 11 minutes -> 10-minute AGAIN review becomes due
        clock.advanceMinutes(11)
        studyCatalog.refreshClock()
        runCurrent()
        val afterTenMin = collected.last() as TodayDashboardState.Ready
        assertEquals(1, afterTenMin.dueKanji)

        // Advance 1 day -> newKanjiToday resets to 0 for the new local date
        clock.advanceDays(1)
        studyCatalog.refreshClock()
        runCurrent()
        val nextDay = collected.last() as TodayDashboardState.Ready
        assertEquals(0, nextDay.newKanjiToday)
        job.cancel()
    }

    @Test
    fun collectionDetailCachesLabelsAcrossReordersAndDetailStatesHandleMissing() = runTest {
        val clock = MutableClock(1_700_000_000_000L)
        val catalog = FakeReferenceCatalog()
        val store = FakeStudyStore(clock)
        val studyCatalog = StudyCatalog(catalog, store, clock, tickIntervalMs = 0L)

        val d1 = KanjiDetail(
            KanjiSummary(1, "日", "day", 4, 5, 1, 1), "", listOf("ニチ"), emptyList(), emptyList(),
            "", emptyList(), emptyList(), "", null, "", emptyList(), emptyList(), emptyList(),
        )
        val d2 = KanjiDetail(
            KanjiSummary(2, "月", "month", 4, 5, 1, 2), "", listOf("ゲツ"), emptyList(), emptyList(),
            "", emptyList(), emptyList(), "", null, "", emptyList(), emptyList(), emptyList(),
        )
        catalog.addKanji(d1)
        catalog.addKanji(d2)
        catalog.wordsById[10L] = WordSummary(10L, listOf("日本"), listOf("にほん"), listOf("nihon"), listOf("Japan"), listOf("n"), 5, true, "", "")

        val setId = store.createSet("Core", listOf("日", "月"), listOf(10L))

        val states = mutableListOf<CollectionDetailState>()
        val job = backgroundScope.launch {
            studyCatalog.observeCollectionDetail(setId).collect { states += it }
        }
        runCurrent()

        val initial = states.last() as CollectionDetailState.Ready
        assertEquals(listOf("日", "月", "日本"), initial.members.map { it.title })
        val lookupsBeforeReorder = catalog.getKanjiSummariesCalls + catalog.getWordsCalls

        store.moveSetItem(setId, ReviewType.KANJI, "月", -1)
        runCurrent()

        val reordered = states.last() as CollectionDetailState.Ready
        assertEquals(listOf("月", "日", "日本"), reordered.members.map { it.title })
        assertEquals(
            "Reordering existing members must reuse cached labels without querying SQLite again",
            lookupsBeforeReorder,
            catalog.getKanjiSummariesCalls + catalog.getWordsCalls,
        )

        val missingKanji = studyCatalog.observeKanjiDetail(999999).take(2).toList()
        assertEquals(KanjiDetailState.Loading, missingKanji[0])
        assertEquals(KanjiDetailState.NotFound, missingKanji[1])

        val missingWord = studyCatalog.observeWordDetail(999999L).take(2).toList()
        assertEquals(WordDetailState.Loading, missingWord[0])
        assertEquals(WordDetailState.NotFound, missingWord[1])
        job.cancel()
    }

    @Test
    fun sequenceLevelsComputeLearnedAndDueCountsFromRefs() = runTest {
        val clock = MutableClock(1_700_000_000_000L)
        val catalog = FakeReferenceCatalog()
        val store = FakeStudyStore(clock)
        val studyCatalog = StudyCatalog(catalog, store, clock, tickIntervalMs = 0L)

        catalog.sequenceDecks[StudySequenceFamily.JLPT to 5] = listOf(
            KanjiSummary(1, "一", "one", 1, 5, 1, 1),
            KanjiSummary(2, "二", "two", 2, 5, 1, 2),
        )
        store.recordReview(ReviewType.KANJI, "一", Rating.AGAIN, clock.millis() - 700_000L)

        val states = studyCatalog.observeSequenceLevels(StudySequenceFamily.JLPT).take(2).toList()
        val ready = states[1] as SequenceLevelsState.Ready
        assertEquals(1, ready.levels.size)
        assertEquals(1, ready.levels.first().learned)
        assertEquals(1, ready.levels.first().due)
    }

    @Test
    fun partOfSpeechTagsMatchActualCatalogTagsAndRejectFalsePositives() {
        assertTrue(PartOfSpeech.NOUN.matchesTags(listOf("n", "k1")))
        assertTrue(PartOfSpeech.VERB.matchesTags(listOf("v1", "vt")))
        assertTrue(PartOfSpeech.VERB.matchesTags(listOf("v5r", "vi")))
        assertTrue(PartOfSpeech.VERB.matchesTags(listOf("vs")))
        assertFalse(PartOfSpeech.VERB.matchesTags(listOf("proverb", "vulg")))
        assertTrue(PartOfSpeech.ADJ_I.matchesTags(listOf("adji")))
        assertTrue(PartOfSpeech.ADJ_NA.matchesTags(listOf("adjna")))
        assertTrue(PartOfSpeech.ADVERB.matchesTags(listOf("adv")))

        assertFalse("1-char queries must bypass 2-gram FTS", SqliteReferenceCatalog.wordSearchUsesFts("日"))
        assertFalse("1-char ASCII queries must bypass 2-gram FTS", SqliteReferenceCatalog.wordSearchUsesFts("a"))
        assertTrue("2+ char queries should use FTS", SqliteReferenceCatalog.wordSearchUsesFts("日本"))
    }
}
