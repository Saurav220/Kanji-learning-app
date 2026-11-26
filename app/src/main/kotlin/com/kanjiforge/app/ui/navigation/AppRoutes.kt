package com.kanjiforge.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.kanjiforge.app.data.reference.model.StudySequenceFamily
import com.kanjiforge.app.domain.SessionMode
import com.kanjiforge.app.domain.SessionSpec
import kotlinx.serialization.Serializable

@Serializable data object TodayRoute
@Serializable data class KanjiListRoute(val initialGrade: Int? = null)
@Serializable data class KanjiDetailRoute(val code: Int)
@Serializable data object WordListRoute
@Serializable data class WordDetailRoute(val id: Long)
@Serializable data object StudySequencesRoute
@Serializable data class StudyLevelsRoute(val familyName: String) {
    val family: StudySequenceFamily
        get() = runCatching { StudySequenceFamily.valueOf(familyName) }.getOrDefault(StudySequenceFamily.JLPT)

    companion object {
        fun from(family: StudySequenceFamily) = StudyLevelsRoute(family.name)
    }
}

@Serializable data class StudySetsRoute(val familyName: String, val level: Int) {
    val family: StudySequenceFamily
        get() = runCatching { StudySequenceFamily.valueOf(familyName) }.getOrDefault(StudySequenceFamily.JLPT)

    companion object {
        fun from(family: StudySequenceFamily, level: Int) = StudySetsRoute(family.name, level)
    }
}

@Serializable
data class StudySessionRoute(
    val modeName: String,
    val specKind: String,
    val code: Int? = null,
    val setId: String? = null,
    val familyName: String? = null,
    val level: Int? = null,
    val offset: Int? = null,
    val batchSize: Int? = null,
) {
    val mode: SessionMode
        get() = runCatching { SessionMode.valueOf(modeName) }.getOrDefault(SessionMode.CARDS)

    fun toSpec(): SessionSpec = when (specKind) {
        SPEC_SINGLE -> SessionSpec.SingleKanji(code ?: 0)
        SPEC_COLLECTION -> SessionSpec.Collection(setId.orEmpty())
        SPEC_SEQUENCE -> SessionSpec.SequenceSet(
            family = runCatching { StudySequenceFamily.valueOf(familyName.orEmpty()) }.getOrDefault(StudySequenceFamily.JLPT),
            level = level ?: 5,
            offset = (offset ?: 0).coerceAtLeast(0),
            batchSize = (batchSize ?: 10).coerceIn(1, 100),
        )
        else -> SessionSpec.DailyReview
    }

    companion object {
        private const val SPEC_DAILY = "daily"
        private const val SPEC_SINGLE = "single"
        private const val SPEC_COLLECTION = "collection"
        private const val SPEC_SEQUENCE = "sequence"

        fun from(mode: SessionMode, spec: SessionSpec): StudySessionRoute = when (spec) {
            SessionSpec.DailyReview -> StudySessionRoute(modeName = mode.name, specKind = SPEC_DAILY)
            is SessionSpec.SingleKanji -> StudySessionRoute(modeName = mode.name, specKind = SPEC_SINGLE, code = spec.code)
            is SessionSpec.Collection -> StudySessionRoute(modeName = mode.name, specKind = SPEC_COLLECTION, setId = spec.setId)
            is SessionSpec.SequenceSet -> StudySessionRoute(
                modeName = mode.name,
                specKind = SPEC_SEQUENCE,
                familyName = spec.family.name,
                level = spec.level,
                offset = spec.offset,
                batchSize = spec.batchSize,
            )
        }
    }
}

@Serializable data object CollectionsRoute
@Serializable data class CollectionDetailRoute(val setId: String)
@Serializable data object KanaLibraryRoute
@Serializable data object RadicalsLibraryRoute
@Serializable data object OutlierLibraryRoute
@Serializable data object ProgressRoute
@Serializable data object SettingsRoute

enum class AppRouteKey {
    TODAY,
    KANJI_LIST,
    KANJI_DETAIL,
    WORD_LIST,
    WORD_DETAIL,
    STUDY_SEQUENCES,
    STUDY_LEVELS,
    STUDY_SETS,
    STUDY_SESSION,
    COLLECTIONS,
    COLLECTION_DETAIL,
    KANA_LIBRARY,
    RADICALS_LIBRARY,
    OUTLIER_LIBRARY,
    PROGRESS,
    SETTINGS,
}

data class TopBarConfig(
    val title: String,
    val isTopLevel: Boolean,
    val showSettings: Boolean,
    val showSearchFab: Boolean,
    val showBottomNav: Boolean = isTopLevel,
)

enum class DrawerSection(val title: String) {
    STUDY("STUDY"),
    REFERENCE("LIBRARIES & SETS"),
    APP("APP"),
}

data class PrimaryTabDestination(
    val key: AppRouteKey,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val route: Any,
)

data class DrawerDestination(
    val key: AppRouteKey,
    val label: String,
    val icon: ImageVector,
    val route: Any,
    val section: DrawerSection,
)

val primaryTabDestinations: List<PrimaryTabDestination> = listOf(
    PrimaryTabDestination(AppRouteKey.TODAY, "Today", Icons.Outlined.Home, Icons.Filled.Home, TodayRoute),
    PrimaryTabDestination(AppRouteKey.STUDY_SEQUENCES, "Study", Icons.Outlined.School, Icons.Filled.School, StudySequencesRoute),
    PrimaryTabDestination(AppRouteKey.KANJI_LIST, "Kanji", Icons.Outlined.GridView, Icons.Filled.GridView, KanjiListRoute()),
    PrimaryTabDestination(AppRouteKey.WORD_LIST, "Words", Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Filled.MenuBook, WordListRoute),
)

val drawerDestinations: List<DrawerDestination> = listOf(
    DrawerDestination(AppRouteKey.TODAY, "Today", Icons.Outlined.Home, TodayRoute, DrawerSection.STUDY),
    DrawerDestination(AppRouteKey.STUDY_SEQUENCES, "Study", Icons.Outlined.School, StudySequencesRoute, DrawerSection.STUDY),
    DrawerDestination(AppRouteKey.KANJI_LIST, "Kanji", Icons.Outlined.GridView, KanjiListRoute(), DrawerSection.STUDY),
    DrawerDestination(AppRouteKey.WORD_LIST, "Words", Icons.AutoMirrored.Outlined.MenuBook, WordListRoute, DrawerSection.STUDY),
    DrawerDestination(AppRouteKey.COLLECTIONS, "Saved sets", Icons.Outlined.Bookmarks, CollectionsRoute, DrawerSection.REFERENCE),
    DrawerDestination(AppRouteKey.KANA_LIBRARY, "Kana charts", Icons.Outlined.TextFields, KanaLibraryRoute, DrawerSection.REFERENCE),
    DrawerDestination(AppRouteKey.RADICALS_LIBRARY, "Radicals", Icons.Outlined.Tune, RadicalsLibraryRoute, DrawerSection.REFERENCE),
    DrawerDestination(AppRouteKey.OUTLIER_LIBRARY, "Outlier reference", Icons.Outlined.TravelExplore, OutlierLibraryRoute, DrawerSection.REFERENCE),
    DrawerDestination(AppRouteKey.PROGRESS, "Progress", Icons.Outlined.Insights, ProgressRoute, DrawerSection.APP),
    DrawerDestination(AppRouteKey.SETTINGS, "Settings", Icons.Outlined.Settings, SettingsRoute, DrawerSection.APP),
)

fun resolveChrome(routeKey: AppRouteKey): TopBarConfig = when (routeKey) {
    AppRouteKey.TODAY -> TopBarConfig("KanjiForge", isTopLevel = true, showSettings = true, showSearchFab = true, showBottomNav = true)
    AppRouteKey.KANJI_LIST -> TopBarConfig("Kanji", isTopLevel = true, showSettings = true, showSearchFab = false, showBottomNav = true)
    AppRouteKey.KANJI_DETAIL -> TopBarConfig("Character", isTopLevel = false, showSettings = true, showSearchFab = false, showBottomNav = false)
    AppRouteKey.WORD_LIST -> TopBarConfig("Words", isTopLevel = true, showSettings = true, showSearchFab = false, showBottomNav = true)
    AppRouteKey.WORD_DETAIL -> TopBarConfig("Dictionary entry", isTopLevel = false, showSettings = true, showSearchFab = false, showBottomNav = false)
    AppRouteKey.STUDY_SEQUENCES -> TopBarConfig("Study", isTopLevel = true, showSettings = true, showSearchFab = false, showBottomNav = true)
    AppRouteKey.STUDY_LEVELS -> TopBarConfig("Choose level", isTopLevel = false, showSettings = true, showSearchFab = false, showBottomNav = false)
    AppRouteKey.STUDY_SETS -> TopBarConfig("Study sets", isTopLevel = false, showSettings = true, showSearchFab = false, showBottomNav = false)
    AppRouteKey.STUDY_SESSION -> TopBarConfig("Study session", isTopLevel = false, showSettings = false, showSearchFab = false, showBottomNav = false)
    AppRouteKey.COLLECTIONS -> TopBarConfig("Saved sets", isTopLevel = true, showSettings = true, showSearchFab = false, showBottomNav = true)
    AppRouteKey.COLLECTION_DETAIL -> TopBarConfig("Collection", isTopLevel = false, showSettings = true, showSearchFab = false, showBottomNav = false)
    AppRouteKey.KANA_LIBRARY -> TopBarConfig("Kana charts", isTopLevel = true, showSettings = true, showSearchFab = false, showBottomNav = true)
    AppRouteKey.RADICALS_LIBRARY -> TopBarConfig("Radicals", isTopLevel = true, showSettings = true, showSearchFab = false, showBottomNav = true)
    AppRouteKey.OUTLIER_LIBRARY -> TopBarConfig("Outlier reference", isTopLevel = true, showSettings = true, showSearchFab = false, showBottomNav = true)
    AppRouteKey.PROGRESS -> TopBarConfig("Progress", isTopLevel = true, showSettings = true, showSearchFab = false, showBottomNav = true)
    AppRouteKey.SETTINGS -> TopBarConfig("Settings", isTopLevel = true, showSettings = false, showSearchFab = false, showBottomNav = true)
}

fun primaryTabSelectionFor(routeKey: AppRouteKey): AppRouteKey? = when (routeKey) {
    AppRouteKey.TODAY -> AppRouteKey.TODAY
    AppRouteKey.STUDY_SEQUENCES,
    AppRouteKey.STUDY_LEVELS,
    AppRouteKey.STUDY_SETS,
    AppRouteKey.STUDY_SESSION,
    -> AppRouteKey.STUDY_SEQUENCES
    AppRouteKey.KANJI_LIST, AppRouteKey.KANJI_DETAIL -> AppRouteKey.KANJI_LIST
    AppRouteKey.WORD_LIST, AppRouteKey.WORD_DETAIL -> AppRouteKey.WORD_LIST
    else -> null
}

fun drawerSelectionFor(routeKey: AppRouteKey): AppRouteKey = when (routeKey) {
    AppRouteKey.TODAY -> AppRouteKey.TODAY
    AppRouteKey.KANJI_LIST, AppRouteKey.KANJI_DETAIL -> AppRouteKey.KANJI_LIST
    AppRouteKey.WORD_LIST, AppRouteKey.WORD_DETAIL -> AppRouteKey.WORD_LIST
    AppRouteKey.STUDY_SEQUENCES, AppRouteKey.STUDY_LEVELS, AppRouteKey.STUDY_SETS, AppRouteKey.STUDY_SESSION -> AppRouteKey.STUDY_SEQUENCES
    AppRouteKey.PROGRESS -> AppRouteKey.PROGRESS
    AppRouteKey.COLLECTIONS, AppRouteKey.COLLECTION_DETAIL -> AppRouteKey.COLLECTIONS
    AppRouteKey.KANA_LIBRARY -> AppRouteKey.KANA_LIBRARY
    AppRouteKey.RADICALS_LIBRARY -> AppRouteKey.RADICALS_LIBRARY
    AppRouteKey.OUTLIER_LIBRARY -> AppRouteKey.OUTLIER_LIBRARY
    AppRouteKey.SETTINGS -> AppRouteKey.SETTINGS
}
