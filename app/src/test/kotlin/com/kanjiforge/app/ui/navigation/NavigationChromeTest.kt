package com.kanjiforge.app.ui.navigation

import com.kanjiforge.app.data.reference.model.StudySequenceFamily
import com.kanjiforge.app.domain.QuizPrompt
import com.kanjiforge.app.domain.SessionMode
import com.kanjiforge.app.domain.SessionSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationChromeTest {

    @Test
    fun studySessionRouteRoundTripsAllSessionSpecs() {
        val specs = listOf(
            SessionSpec.DailyReview,
            SessionSpec.SingleKanji(26085),
            SessionSpec.Collection("set-42"),
            SessionSpec.SequenceSet(StudySequenceFamily.KLC, level = 3, offset = 20, batchSize = 10),
        )
        specs.forEach { spec ->
            val route = StudySessionRoute.from(SessionMode.QUIZ, spec)
            assertEquals(SessionMode.QUIZ, route.mode)
            assertEquals(spec, route.toSpec())
            assertNull(route.quizPrompt)

            val focusedRoute = StudySessionRoute.from(SessionMode.QUIZ, spec, QuizPrompt.READING)
            assertEquals(QuizPrompt.READING, focusedRoute.quizPrompt)
            assertEquals(spec, focusedRoute.toSpec())

            val cardRoute = StudySessionRoute.from(SessionMode.CARDS, spec, QuizPrompt.READING)
            assertNull(cardRoute.quizPrompt)
        }
    }

    @Test
    fun chromeAndDrawerSelectionMapAllRoutesConsistently() {
        val todayChrome = resolveChrome(AppRouteKey.TODAY)
        assertTrue(todayChrome.isTopLevel)
        assertTrue(todayChrome.showSearchFab)
        assertTrue(todayChrome.showSettings)

        val sessionChrome = resolveChrome(AppRouteKey.STUDY_SESSION)
        assertFalse(sessionChrome.isTopLevel)
        assertFalse(sessionChrome.showSettings)
        assertFalse(sessionChrome.showSearchFab)

        assertEquals(AppRouteKey.KANA_LIBRARY, drawerSelectionFor(AppRouteKey.KANA_LIBRARY))
        assertEquals(AppRouteKey.RADICALS_LIBRARY, drawerSelectionFor(AppRouteKey.RADICALS_LIBRARY))
        assertEquals(AppRouteKey.OUTLIER_LIBRARY, drawerSelectionFor(AppRouteKey.OUTLIER_LIBRARY))
        assertEquals(AppRouteKey.STUDY_SEQUENCES, drawerSelectionFor(AppRouteKey.STUDY_LEVELS))
        assertEquals(AppRouteKey.STUDY_SEQUENCES, drawerSelectionFor(AppRouteKey.STUDY_SETS))
        assertEquals(AppRouteKey.COLLECTIONS, drawerSelectionFor(AppRouteKey.COLLECTION_DETAIL))
    }

    @Test
    fun hybridBottomNavShowsOnlyOnTopLevelRoutesAndMapsCoreTabs() {
        val topLevelRoutes = listOf(
            AppRouteKey.TODAY,
            AppRouteKey.STUDY_SEQUENCES,
            AppRouteKey.KANJI_LIST,
            AppRouteKey.WORD_LIST,
            AppRouteKey.COLLECTIONS,
            AppRouteKey.KANA_LIBRARY,
            AppRouteKey.RADICALS_LIBRARY,
            AppRouteKey.OUTLIER_LIBRARY,
            AppRouteKey.PROGRESS,
            AppRouteKey.SETTINGS,
        )
        topLevelRoutes.forEach { key ->
            assertTrue("Expected bottom nav on $key", resolveChrome(key).showBottomNav)
        }

        val childRoutes = listOf(
            AppRouteKey.KANJI_DETAIL,
            AppRouteKey.WORD_DETAIL,
            AppRouteKey.STUDY_LEVELS,
            AppRouteKey.STUDY_SETS,
            AppRouteKey.STUDY_SESSION,
            AppRouteKey.COLLECTION_DETAIL,
        )
        childRoutes.forEach { key ->
            assertFalse("Expected no bottom nav on $key", resolveChrome(key).showBottomNav)
        }

        assertEquals(4, primaryTabDestinations.size)
        assertEquals(AppRouteKey.TODAY, primaryTabSelectionFor(AppRouteKey.TODAY))
        assertEquals(AppRouteKey.STUDY_SEQUENCES, primaryTabSelectionFor(AppRouteKey.STUDY_SEQUENCES))
        assertEquals(AppRouteKey.STUDY_SEQUENCES, primaryTabSelectionFor(AppRouteKey.STUDY_LEVELS))
        assertEquals(AppRouteKey.STUDY_SEQUENCES, primaryTabSelectionFor(AppRouteKey.STUDY_SETS))
        assertEquals(AppRouteKey.STUDY_SEQUENCES, primaryTabSelectionFor(AppRouteKey.STUDY_SESSION))
        assertEquals(AppRouteKey.KANJI_LIST, primaryTabSelectionFor(AppRouteKey.KANJI_LIST))
        assertEquals(AppRouteKey.KANJI_LIST, primaryTabSelectionFor(AppRouteKey.KANJI_DETAIL))
        assertEquals(AppRouteKey.WORD_LIST, primaryTabSelectionFor(AppRouteKey.WORD_LIST))
        assertEquals(AppRouteKey.WORD_LIST, primaryTabSelectionFor(AppRouteKey.WORD_DETAIL))

        assertNull(primaryTabSelectionFor(AppRouteKey.KANA_LIBRARY))
        assertNull(primaryTabSelectionFor(AppRouteKey.RADICALS_LIBRARY))
        assertNull(primaryTabSelectionFor(AppRouteKey.OUTLIER_LIBRARY))
        assertNull(primaryTabSelectionFor(AppRouteKey.COLLECTIONS))
        assertNull(primaryTabSelectionFor(AppRouteKey.COLLECTION_DETAIL))
        assertNull(primaryTabSelectionFor(AppRouteKey.PROGRESS))
        assertNull(primaryTabSelectionFor(AppRouteKey.SETTINGS))
    }
}
