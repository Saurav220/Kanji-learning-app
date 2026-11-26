package com.kanjiforge.app.domain

import com.kanjiforge.app.data.reference.ReferenceCatalog
import com.kanjiforge.app.data.reference.model.KanjiDetail
import com.kanjiforge.app.data.reference.model.KanjiFilter
import com.kanjiforge.app.data.reference.model.KanjiSummary
import com.kanjiforge.app.data.reference.model.PagedResult
import com.kanjiforge.app.data.reference.model.StudyDeckLevel
import com.kanjiforge.app.data.reference.model.StudySequenceFamily
import com.kanjiforge.app.data.reference.model.WordSummary
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant

data class CollectionChoice(
    val id: String,
    val name: String,
    val containsItem: Boolean,
)

sealed interface TodayDashboardState {
    data object Loading : TodayDashboardState
    data class Ready(
        val dueKanji: Int,
        val dueWords: Int,
        val reviewedToday: Int,
        val newKanjiToday: Int,
        val dailyNew: Int,
        val remainingNew: Int,
        val dailyProgress: Float,
        val totalReviewedKanji: Int,
        val showBeginnerPrompt: Boolean,
        val intervals: List<IntervalBucket>,
        val weekActivity: List<DayActivity>,
    ) : TodayDashboardState
}

sealed interface ProgressState {
    data object Loading : ProgressState
    data class Ready(
        val dueKanji: Int,
        val favoriteKanji: Int,
        val favoriteWords: Int,
        val reviewedToday: Int,
        val sessions: List<SessionRecord>,
    ) : ProgressState
}

data class LevelProgressSummary(
    val deck: StudyDeckLevel,
    val learned: Int,
    val due: Int,
)

sealed interface SequenceLevelsState {
    data object Loading : SequenceLevelsState
    data class Ready(
        val family: StudySequenceFamily,
        val levels: List<LevelProgressSummary>,
        val remainingNew: Int,
        val dailyNew: Int,
    ) : SequenceLevelsState
    data class Error(val message: String) : SequenceLevelsState
}

data class DeckSetSummary(
    val setNumber: Int,
    val offset: Int,
    val batchSize: Int,
    val totalInSet: Int,
    val learned: Int,
    val due: Int,
    val newAvailable: Int,
    val canStudy: Boolean,
    val actionLabel: String,
    val leadCharacter: String = "",
)

sealed interface SequenceSetsState {
    data object Loading : SequenceSetsState
    data class Ready(
        val family: StudySequenceFamily,
        val level: Int,
        val levelTitle: String,
        val totalKanji: Int,
        val sets: List<DeckSetSummary>,
        val remainingNew: Int,
        val dailyNew: Int,
    ) : SequenceSetsState
    data class Error(val message: String) : SequenceSetsState
}

sealed interface CollectionsState {
    data object Loading : CollectionsState
    data class Ready(
        val sets: List<StudySetSummary>,
        val favoriteKanjiCount: Int,
        val favoriteWordCount: Int,
    ) : CollectionsState
}

data class ResolvedSetMember(
    val type: ReviewType,
    val itemId: String,
    val title: String,
    val subtitle: String,
    val kanjiCode: Int? = null,
    val wordId: Long? = null,
)

sealed interface CollectionDetailState {
    data object Loading : CollectionDetailState
    data object NotFound : CollectionDetailState
    data class Ready(
        val set: StudySetSummary,
        val members: List<ResolvedSetMember>,
    ) : CollectionDetailState
}

sealed interface KanjiDetailState {
    data object Loading : KanjiDetailState
    data object NotFound : KanjiDetailState
    data class Ready(
        val detail: KanjiDetail,
        val isSaved: Boolean,
        val review: ReviewRecord?,
        val dueText: String?,
        val collectionChoices: List<CollectionChoice>,
    ) : KanjiDetailState
    data class Error(val message: String) : KanjiDetailState
}

sealed interface WordDetailState {
    data object Loading : WordDetailState
    data object NotFound : WordDetailState
    data class Ready(
        val word: WordSummary,
        val isSaved: Boolean,
        val collectionChoices: List<CollectionChoice>,
    ) : WordDetailState
    data class Error(val message: String) : WordDetailState
}

class StudyCatalog(
    private val reference: ReferenceCatalog,
    private val store: StudyStore,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val tickIntervalMs: Long = 60_000L,
) {
    private val manualTick = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val clockTick: Flow<Long> = merge(
        flow {
            emit(clock.millis())
            if (tickIntervalMs in 1 until Long.MAX_VALUE) {
                while (currentCoroutineContext().isActive) {
                    delay(tickIntervalMs)
                    emit(clock.millis())
                }
            }
        },
        manualTick.map { clock.millis() },
    )

    fun refreshClock() {
        manualTick.tryEmit(Unit)
    }

    fun observeTodayDashboard(): Flow<TodayDashboardState> = combine(
        store.reviews,
        store.sessions,
        store.preferences,
        clockTick,
    ) { reviews, sessions, prefs, now ->
        val zone = clock.zone
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val localStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val kanjiReviews = reviews.filter { it.type == ReviewType.KANJI }
        val newTodayRaw = kanjiReviews.count { it.firstReviewedAt >= localStart }
        val newTodayCapped = newTodayRaw.coerceAtMost(prefs.dailyNew)
        val remainingNew = (prefs.dailyNew - newTodayCapped).coerceAtLeast(0)
        val dailyProgress = (newTodayCapped.toFloat() / prefs.dailyNew.coerceAtLeast(1)).coerceIn(0f, 1f)

        val intervals = listOf(
            IntervalBucket("<1d", kanjiReviews.count { it.intervalDays < 1.0 }),
            IntervalBucket("1–7d", kanjiReviews.count { it.intervalDays >= 1.0 && it.intervalDays < 8.0 }),
            IntervalBucket("8–30d", kanjiReviews.count { it.intervalDays >= 8.0 && it.intervalDays < 31.0 }),
            IntervalBucket("31–120d", kanjiReviews.count { it.intervalDays >= 31.0 && it.intervalDays < 121.0 }),
            IntervalBucket("121d+", kanjiReviews.count { it.intervalDays >= 121.0 }),
        )

        val monday = today.with(DayOfWeek.MONDAY)
        val weekActivity = (0..6).map { offset ->
            val date = monday.plusDays(offset.toLong())
            val count = sessions
                .filter { Instant.ofEpochMilli(it.finishedAt).atZone(zone).toLocalDate() == date }
                .sumOf { it.total }
            DayActivity(date = date, count = count, isToday = date == today)
        }

        TodayDashboardState.Ready(
            dueKanji = kanjiReviews.count { it.dueAt <= now },
            dueWords = reviews.count { it.type == ReviewType.WORD && it.dueAt <= now },
            reviewedToday = reviews.count { it.lastReviewedAt >= localStart },
            newKanjiToday = newTodayRaw,
            dailyNew = prefs.dailyNew,
            remainingNew = remainingNew,
            dailyProgress = dailyProgress,
            totalReviewedKanji = kanjiReviews.size,
            showBeginnerPrompt = kanjiReviews.isEmpty(),
            intervals = intervals,
            weekActivity = weekActivity,
        ) as TodayDashboardState
    }.emitLoadingOnce(TodayDashboardState.Loading)

    fun observeProgress(): Flow<ProgressState> = combine(
        store.reviews,
        store.favorites,
        store.sessions,
        clockTick,
    ) { reviews, favorites, sessions, now ->
        val zone = clock.zone
        val localStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        ProgressState.Ready(
            dueKanji = reviews.count { it.type == ReviewType.KANJI && it.dueAt <= now },
            favoriteKanji = favorites.count { it.type == ReviewType.KANJI },
            favoriteWords = favorites.count { it.type == ReviewType.WORD },
            reviewedToday = reviews.count { it.lastReviewedAt >= localStart },
            sessions = sessions,
        ) as ProgressState
    }.emitLoadingOnce(ProgressState.Loading)

    fun observeSequenceLevels(family: StudySequenceFamily): Flow<SequenceLevelsState> {
        val refsFlow = cachedOneShot { reference.listStudyDeckRefs(family) }
        return combine(refsFlow, store.reviews, store.preferences, clockTick) { refsByLevel, reviews, prefs, now ->
            val zone = clock.zone
            val localStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val kanjiReviews = reviews.filter { it.type == ReviewType.KANJI }.associateBy { it.id }
            val newToday = kanjiReviews.values.count { it.firstReviewedAt >= localStart }
            val remainingNew = (prefs.dailyNew - newToday).coerceAtLeast(0)
            val summaries = refsByLevel.entries.map { (level, refs) ->
                val learned = refs.count { it.character in kanjiReviews }
                val due = refs.count { kanjiReviews[it.character]?.dueAt?.let { dueAt -> dueAt <= now } == true }
                LevelProgressSummary(
                    deck = StudyDeckLevel(family, level, refs.size),
                    learned = learned,
                    due = due,
                )
            }
            SequenceLevelsState.Ready(
                family = family,
                levels = summaries,
                remainingNew = remainingNew,
                dailyNew = prefs.dailyNew,
            ) as SequenceLevelsState
        }.emitLoadingOnce(SequenceLevelsState.Loading)
            .catch { emit(SequenceLevelsState.Error(it.message ?: "Could not load sequence levels.")) }
    }

    fun observeSequenceSets(family: StudySequenceFamily, level: Int): Flow<SequenceSetsState> {
        val cardsFlow = cachedOneShot { reference.searchStudyDeck(family, level, offset = 0, limit = 7071) }
        return combine(cardsFlow, store.reviews, store.preferences, clockTick) { cards, reviews, prefs, now ->
            val zone = clock.zone
            val localStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val kanjiReviews = reviews.filter { it.type == ReviewType.KANJI }.associateBy { it.id }
            val newToday = kanjiReviews.values.count { it.firstReviewedAt >= localStart }
            val remainingNew = (prefs.dailyNew - newToday).coerceAtLeast(0)
            val sets = cards.chunked(10).mapIndexed { index, batch ->
                val learned = batch.count { it.character in kanjiReviews }
                val due = batch.count { kanjiReviews[it.character]?.dueAt?.let { dueAt -> dueAt <= now } == true }
                val newAvailable = minOf(batch.count { it.character !in kanjiReviews }, remainingNew)
                val canStudy = batch.isNotEmpty() && (due > 0 || learned > 0 || newAvailable > 0)
                val actionLabel = when {
                    !canStudy -> "Limit reached"
                    due > 0 -> "Review"
                    newAvailable == 0 -> "Review set"
                    else -> "Study"
                }
                DeckSetSummary(
                    setNumber = index + 1,
                    offset = index * 10,
                    batchSize = batch.size,
                    totalInSet = batch.size,
                    learned = learned,
                    due = due,
                    newAvailable = newAvailable,
                    canStudy = canStudy,
                    actionLabel = actionLabel,
                    leadCharacter = batch.firstOrNull()?.character.orEmpty(),
                )
            }
            SequenceSetsState.Ready(
                family = family,
                level = level,
                levelTitle = family.deckTitle(level),
                totalKanji = cards.size,
                sets = sets,
                remainingNew = remainingNew,
                dailyNew = prefs.dailyNew,
            ) as SequenceSetsState
        }.emitLoadingOnce(SequenceSetsState.Loading)
            .catch { emit(SequenceSetsState.Error(it.message ?: "Could not load study sets.")) }
    }

    fun observeCollections(): Flow<CollectionsState> = combine(store.sets, store.favorites) { sets, favorites ->
        CollectionsState.Ready(
            sets = sets,
            favoriteKanjiCount = favorites.count { it.type == ReviewType.KANJI },
            favoriteWordCount = favorites.count { it.type == ReviewType.WORD },
        ) as CollectionsState
    }.emitLoadingOnce(CollectionsState.Loading)

    fun observeCollectionDetail(setId: String): Flow<CollectionDetailState> {
        val labelCache = mutableMapOf<String, ResolvedSetMember>()
        return combine(store.sets, store.observeSetItems(setId)) { sets, members ->
            val set = sets.firstOrNull { it.id == setId } ?: return@combine CollectionDetailState.NotFound
            val missingKanji = mutableListOf<String>()
            val missingWordIds = mutableListOf<Long>()
            for (member in members) {
                val key = "${member.type.name}:${member.itemId}"
                if (key !in labelCache) {
                    when (member.type) {
                        ReviewType.KANJI -> missingKanji += member.itemId
                        ReviewType.WORD -> member.itemId.toLongOrNull()?.let { missingWordIds += it }
                    }
                }
            }
            if (missingKanji.isNotEmpty()) {
                val fetched = reference.getKanjiSummaries(missingKanji)
                missingKanji.forEach { ch ->
                    val summary = fetched[ch]
                    val key = "${ReviewType.KANJI.name}:$ch"
                    labelCache[key] = ResolvedSetMember(
                        type = ReviewType.KANJI,
                        itemId = ch,
                        title = summary?.character ?: ch,
                        subtitle = summary?.meaning?.ifBlank { "Meaning unavailable" } ?: "Reference entry unavailable",
                        kanjiCode = summary?.code,
                    )
                }
            }
            if (missingWordIds.isNotEmpty()) {
                val fetched = reference.getWords(missingWordIds)
                missingWordIds.forEach { wordId ->
                    val word = fetched[wordId]
                    val key = "${ReviewType.WORD.name}:$wordId"
                    labelCache[key] = ResolvedSetMember(
                        type = ReviewType.WORD,
                        itemId = wordId.toString(),
                        title = word?.let { it.forms.firstOrNull() ?: it.readings.firstOrNull() } ?: wordId.toString(),
                        subtitle = word?.let { it.glosses.firstOrNull() ?: it.translation } ?: "Reference entry unavailable",
                        wordId = if (word != null) wordId else null,
                    )
                }
            }
            val resolved = members.map { member ->
                val key = "${member.type.name}:${member.itemId}"
                labelCache[key] ?: ResolvedSetMember(
                    type = member.type,
                    itemId = member.itemId,
                    title = member.itemId,
                    subtitle = "Reference entry unavailable",
                )
            }
            CollectionDetailState.Ready(set = set, members = resolved) as CollectionDetailState
        }.emitLoadingOnce(CollectionDetailState.Loading)
    }

    private data class KanjiUserSnapshot(
        val favorites: List<FavoriteRecord>,
        val reviews: List<ReviewRecord>,
        val sets: List<StudySetSummary>,
        val memberSetIds: Set<String>,
    )

    fun observeKanjiDetail(code: Int): Flow<KanjiDetailState> {
        val character = String(Character.toChars(code))
        val detailFlow = cachedOneShot { reference.getKanjiByCode(code) }
        val userSnapshotFlow = combine(
            store.favorites,
            store.reviews,
            store.sets,
            store.observeContainingSetIds(ReviewType.KANJI, character),
        ) { favorites, reviews, sets, memberSetIds ->
            KanjiUserSnapshot(favorites, reviews, sets, memberSetIds)
        }
        return combine(detailFlow, userSnapshotFlow, clockTick) { detail, user, now ->
            if (detail == null) return@combine KanjiDetailState.NotFound
            val isSaved = user.favorites.any { it.type == ReviewType.KANJI && it.id == detail.summary.character }
            val review = user.reviews.firstOrNull { it.type == ReviewType.KANJI && it.id == detail.summary.character }
            val dueText = review?.let { formatDueStatus(it.dueAt, now, clock.zone) }
            val choices = user.sets.map { set ->
                CollectionChoice(id = set.id, name = set.name, containsItem = set.id in user.memberSetIds)
            }
            KanjiDetailState.Ready(
                detail = detail,
                isSaved = isSaved,
                review = review,
                dueText = dueText,
                collectionChoices = choices,
            ) as KanjiDetailState
        }.emitLoadingOnce(KanjiDetailState.Loading)
            .catch { emit(KanjiDetailState.Error(it.message ?: "Could not open this character.")) }
    }

    fun observeWordDetail(id: Long): Flow<WordDetailState> {
        val wordFlow = cachedOneShot { reference.getWord(id) }
        return combine(
            wordFlow,
            store.favorites,
            store.sets,
            store.observeContainingSetIds(ReviewType.WORD, id.toString()),
        ) { word, favorites, sets, memberSetIds ->
            if (word == null) return@combine WordDetailState.NotFound
            val isSaved = favorites.any { it.type == ReviewType.WORD && it.id == id.toString() }
            val choices = sets.map { set ->
                CollectionChoice(id = set.id, name = set.name, containsItem = set.id in memberSetIds)
            }
            WordDetailState.Ready(
                word = word,
                isSaved = isSaved,
                collectionChoices = choices,
            ) as WordDetailState
        }.emitLoadingOnce(WordDetailState.Loading)
            .catch { emit(WordDetailState.Error(it.message ?: "Could not open this word.")) }
    }

    suspend fun searchKanjiPage(
        query: String,
        filter: KanjiFilter,
        offset: Int,
        limit: Int,
    ): PagedResult<KanjiSummary> {
        val now = clock.millis()
        val favorites = store.favorites.first()
        val reviews = store.reviews.first()
        val savedCodes = favorites.asSequence()
            .filter { it.type == ReviewType.KANJI }
            .mapNotNull { it.id.codePointAtOrNull() }
            .toSet()
        val kanjiReviews = reviews.filter { it.type == ReviewType.KANJI }
        val dueCodes = kanjiReviews.asSequence()
            .filter { it.dueAt <= now }
            .mapNotNull { it.id.codePointAtOrNull() }
            .toSet()
        val reviewCodesByRating = kanjiReviews.groupBy { it.lastRating }
            .mapValues { (_, items) -> items.mapNotNull { it.id.codePointAtOrNull() }.toSet() }
        val reviewedCodes = kanjiReviews.asSequence()
            .mapNotNull { it.id.codePointAtOrNull() }
            .toSet()
        return reference.searchKanji(
            query = query,
            filters = filter,
            offset = offset,
            limit = limit,
            savedCodes = savedCodes,
            dueCodes = dueCodes,
            reviewCodesByRating = reviewCodesByRating,
            reviewedCodes = reviewedCodes,
        )
    }

    suspend fun toggleFavorite(type: ReviewType, id: String, saved: Boolean) =
        store.toggleFavorite(type, id, saved)

    suspend fun setCollectionMembership(setId: String, type: ReviewType, itemId: String, member: Boolean) {
        if (member) store.addSetItem(setId, type, itemId)
        else store.removeSetItem(setId, type, itemId)
    }

    suspend fun createCollectionWithItem(name: String, type: ReviewType, itemId: String): String = when (type) {
        ReviewType.KANJI -> store.createSet(name, listOf(itemId), emptyList())
        ReviewType.WORD -> store.createSet(name, emptyList(), listOf(itemId.toLong()))
    }

    suspend fun createCollectionFromFavorites(name: String): String {
        val favorites = store.favorites.first()
        val kanji = favorites.filter { it.type == ReviewType.KANJI }.map { it.id }
        val words = favorites.filter { it.type == ReviewType.WORD }.mapNotNull { it.id.toLongOrNull() }
        return store.createSet(name, kanji, words)
    }

    suspend fun moveCollectionMember(setId: String, type: ReviewType, itemId: String, direction: Int) =
        store.moveSetItem(setId, type, itemId, direction)

    suspend fun removeCollectionMember(setId: String, type: ReviewType, itemId: String) =
        store.removeSetItem(setId, type, itemId)

    suspend fun deleteCollection(setId: String) = store.deleteSet(setId)

    private fun <T> Flow<T>.emitLoadingOnce(loading: T): Flow<T> {
        var emitted = false
        return onStart {
            if (!emitted) {
                emitted = true
                emit(loading)
            }
        }
    }

    private fun <T> cachedOneShot(loader: suspend () -> T): Flow<T> {
        var loaded = false
        var cached: T? = null
        return flow {
            if (!loaded) {
                cached = loader()
                loaded = true
            }
            @Suppress("UNCHECKED_CAST")
            emit(cached as T)
        }
    }

    private fun String.codePointAtOrNull(): Int? =
        if (isEmpty()) null else codePointAt(0).takeIf { it > 0 }
}
