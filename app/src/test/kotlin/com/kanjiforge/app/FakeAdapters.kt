package com.kanjiforge.app

import com.kanjiforge.app.data.reference.ReferenceCatalog
import com.kanjiforge.app.data.reference.model.KanaSummary
import com.kanjiforge.app.data.reference.model.KanjiDetail
import com.kanjiforge.app.data.reference.model.KanjiFilter
import com.kanjiforge.app.data.reference.model.KanjiSummary
import com.kanjiforge.app.data.reference.model.OutlierCharacterSummary
import com.kanjiforge.app.data.reference.model.OutlierSummary
import com.kanjiforge.app.data.reference.model.PagedResult
import com.kanjiforge.app.data.reference.model.QuizCandidateInfo
import com.kanjiforge.app.data.reference.model.RadicalSummary
import com.kanjiforge.app.data.reference.model.SequenceKanjiRef
import com.kanjiforge.app.data.reference.model.StudyDeckLevel
import com.kanjiforge.app.data.reference.model.StudySequenceFamily
import com.kanjiforge.app.data.reference.model.WordFilter
import com.kanjiforge.app.data.reference.model.WordSummary
import com.kanjiforge.app.domain.BackupPayload
import com.kanjiforge.app.domain.FavoriteRecord
import com.kanjiforge.app.domain.Rating
import com.kanjiforge.app.domain.ReviewRecord
import com.kanjiforge.app.domain.ReviewScheduler
import com.kanjiforge.app.domain.ReviewType
import com.kanjiforge.app.domain.SessionRecord
import com.kanjiforge.app.domain.StudyPreferences
import com.kanjiforge.app.domain.StudySet
import com.kanjiforge.app.domain.StudySetMember
import com.kanjiforge.app.domain.StudySetSummary
import com.kanjiforge.app.domain.StudyStore
import com.kanjiforge.app.domain.formatFuriganaPlain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class MutableClock(
    var currentMillis: Long,
    private val zoneId: ZoneId = ZoneId.of("UTC"),
) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = MutableClock(currentMillis, zone)
    override fun instant(): Instant = Instant.ofEpochMilli(currentMillis)
    override fun millis(): Long = currentMillis

    fun advanceMinutes(minutes: Long) {
        currentMillis += minutes * 60_000L
    }

    fun advanceDays(days: Long) {
        currentMillis += days * 86_400_000L
    }
}

class FakeReferenceCatalog : ReferenceCatalog {
    var kanjiList: List<KanjiSummary> = emptyList()
    var detailsByChar: MutableMap<String, KanjiDetail> = linkedMapOf()
    var wordsById: MutableMap<Long, WordSummary> = linkedMapOf()
    var sequenceDecks: MutableMap<Pair<StudySequenceFamily, Int>, List<KanjiSummary>> = linkedMapOf()
    var kanaList: List<KanaSummary> = emptyList()
    var radicalsList: List<RadicalSummary> = emptyList()
    var outlierArticles: List<OutlierSummary> = emptyList()
    var outlierCharacters: List<OutlierCharacterSummary> = emptyList()

    var getKanjiCalls = 0
    var getKanjiSummariesCalls = 0
    var getWordsCalls = 0
    var getQuizCandidatesCalls = 0
    var lastQuizIncludeWords: Boolean? = null
    var lastQuizIncludeSentences: Boolean? = null
    var closed = false

    fun addKanji(detail: KanjiDetail) {
        kanjiList = (kanjiList.filterNot { it.code == detail.summary.code } + detail.summary)
        detailsByChar[detail.summary.character] = detail
    }

    override suspend fun listStudyDeckLevels(family: StudySequenceFamily): List<StudyDeckLevel> =
        sequenceDecks.entries
            .filter { it.key.first == family }
            .map { StudyDeckLevel(family, it.key.second, it.value.size) }

    override suspend fun listStudyDeckRefs(family: StudySequenceFamily): Map<Int, List<SequenceKanjiRef>> =
        sequenceDecks.entries
            .filter { it.key.first == family }
            .associate { (key, list) ->
                key.second to list.map { SequenceKanjiRef(key.second, it.code, it.character) }
            }

    override suspend fun searchStudyDeck(
        family: StudySequenceFamily,
        level: Int,
        offset: Int,
        limit: Int,
    ): List<KanjiSummary> = sequenceDecks[family to level].orEmpty().drop(offset).take(limit)

    override suspend fun searchKanji(
        query: String,
        filters: KanjiFilter,
        offset: Int,
        limit: Int,
        savedCodes: Set<Int>,
        dueCodes: Set<Int>,
        reviewCodesByRating: Map<Rating, Set<Int>>,
        reviewedCodes: Set<Int>,
    ): PagedResult<KanjiSummary> {
        val filtered = kanjiList.filter { item ->
            val q = query.trim()
            val matchesQuery = q.isEmpty() || item.character.contains(q) || item.meaning.contains(q, true)
            val matchesJlpt = filters.jlpt == null || item.jlpt == filters.jlpt
            val matchesGrade = filters.grade == null || item.grade == filters.grade
            val matchesSaved = !filters.savedOnly || item.code in savedCodes
            val matchesDue = !filters.dueOnly || item.code in dueCodes
            matchesQuery && matchesJlpt && matchesGrade && matchesSaved && matchesDue
        }
        val slice = filtered.drop(offset).take(limit + 1)
        return PagedResult(slice.take(limit), slice.size > limit)
    }

    override suspend fun getKanji(character: String): KanjiDetail? {
        getKanjiCalls += 1
        return detailsByChar[character]
    }

    override suspend fun getKanjiByCode(code: Int): KanjiDetail? {
        getKanjiCalls += 1
        return detailsByChar.values.firstOrNull { it.summary.code == code }
    }

    override suspend fun getKanjiSummaries(characters: Collection<String>): Map<String, KanjiSummary> {
        getKanjiSummariesCalls += 1
        val result = linkedMapOf<String, KanjiSummary>()
        characters.forEach { ch ->
            detailsByChar[ch]?.summary?.let { result[ch] = it }
                ?: kanjiList.firstOrNull { it.character == ch }?.let { result[ch] = it }
        }
        return result
    }

    override suspend fun getQuizCandidates(
        codes: Collection<Int>,
        includeWords: Boolean,
        includeSentences: Boolean,
    ): Map<Int, QuizCandidateInfo> {
        getQuizCandidatesCalls += 1
        lastQuizIncludeWords = includeWords
        lastQuizIncludeSentences = includeSentences
        val result = linkedMapOf<Int, QuizCandidateInfo>()
        codes.forEach { code ->
            val detail = detailsByChar.values.firstOrNull { it.summary.code == code }
            val summary = detail?.summary ?: kanjiList.firstOrNull { it.code == code } ?: return@forEach
            val word = if (includeWords) {
                detail?.words?.asSequence()?.flatMap { it.forms.asSequence() }
                    ?.firstOrNull { it.contains(summary.character) }
            } else null
            val sentence = if (includeSentences) {
                detail?.sentences?.firstOrNull { it.text.isNotBlank() }?.let { formatFuriganaPlain(it.text) }
            } else null
            result[code] = QuizCandidateInfo(
                summary = summary,
                readings = detail?.let { it.on + it.kun }.orEmpty(),
                sampleWord = word,
                sampleSentence = sentence,
            )
        }
        return result
    }

    override suspend fun searchWords(query: String, filters: WordFilter, offset: Int, limit: Int): PagedResult<WordSummary> {
        val filtered = wordsById.values.filter { word ->
            val matchesPos = filters.partOfSpeech?.matchesTags(word.tags) != false
            val matchesJlpt = filters.jlpt == null || word.jlpt == filters.jlpt
            val matchesCommon = !filters.commonOnly || word.common
            matchesPos && matchesJlpt && matchesCommon
        }
        val slice = filtered.drop(offset).take(limit + 1)
        return PagedResult(slice.take(limit), slice.size > limit)
    }

    override suspend fun getWord(id: Long): WordSummary? = wordsById[id]

    override suspend fun getWords(ids: Collection<Long>): Map<Long, WordSummary> {
        getWordsCalls += 1
        return ids.mapNotNull { id -> wordsById[id]?.let { id to it } }.toMap()
    }

    override suspend fun listKana(): List<KanaSummary> = kanaList
    override suspend fun listAllRadicals(): List<RadicalSummary> = radicalsList
    override suspend fun searchOutlier(query: String, offset: Int, limit: Int): PagedResult<OutlierSummary> {
        val slice = outlierArticles.drop(offset).take(limit + 1)
        return PagedResult(slice.take(limit), slice.size > limit)
    }

    override suspend fun listAllOutlierCharacters(): List<OutlierCharacterSummary> = outlierCharacters

    override fun close() {
        closed = true
    }
}

class FakeStudyStore(
    private val clock: MutableClock = MutableClock(1_700_000_000_000L),
    private val scheduler: ReviewScheduler = ReviewScheduler(),
) : StudyStore {
    val _preferences = MutableStateFlow(StudyPreferences())
    val _favorites = MutableStateFlow<List<FavoriteRecord>>(emptyList())
    val _reviews = MutableStateFlow<List<ReviewRecord>>(emptyList())
    val _sessions = MutableStateFlow<List<SessionRecord>>(emptyList())
    private val _setDefinitions = MutableStateFlow<List<Pair<String, Pair<String, Long>>>>(emptyList())
    private val _setItems = MutableStateFlow<List<StudySetMember>>(emptyList())

    override val preferences: Flow<StudyPreferences> = _preferences
    override val favorites: Flow<List<FavoriteRecord>> = _favorites
    override val reviews: Flow<List<ReviewRecord>> = _reviews
    override val sessions: Flow<List<SessionRecord>> = _sessions
    override val sets: Flow<List<StudySetSummary>> =
        combine(_setDefinitions, _setItems) { defs, items ->
            val bySet = items.groupBy { it.setId }
            defs.map { (id, nameAndTime) ->
                val members = bySet[id].orEmpty()
                StudySetSummary(
                    id = id,
                    name = nameAndTime.first,
                    createdAt = nameAndTime.second,
                    kanjiCount = members.count { it.type == ReviewType.KANJI },
                    wordCount = members.count { it.type == ReviewType.WORD },
                )
            }
        }

    override fun observeSetItems(setId: String): Flow<List<StudySetMember>> =
        _setItems.map { rows -> rows.filter { it.setId == setId }.sortedBy { it.position } }

    override fun observeContainingSetIds(type: ReviewType, itemId: String): Flow<Set<String>> =
        _setItems.map { rows -> rows.filter { it.type == type && it.itemId == itemId }.map { it.setId }.toSet() }

    override suspend fun getSetItems(setId: String): List<StudySetMember> =
        _setItems.value.filter { it.setId == setId }.sortedBy { it.position }

    override suspend fun recordReview(type: ReviewType, id: String, rating: Rating, now: Long?): ReviewRecord {
        val ts = now ?: clock.millis()
        val existing = _reviews.value.firstOrNull { it.type == type && it.id == id }
        val updated = scheduler.schedule(existing, rating, ts, type, id)
        _reviews.value = _reviews.value.filterNot { it.type == type && it.id == id } + updated
        return updated
    }

    override suspend fun finishSession(session: SessionRecord) {
        _sessions.value = (listOf(session) + _sessions.value.filterNot { it.id == session.id })
            .sortedByDescending { it.finishedAt }
            .take(200)
    }

    override suspend fun toggleFavorite(type: ReviewType, id: String, saved: Boolean, now: Long?) {
        val ts = now ?: clock.millis()
        _favorites.value = _favorites.value.filterNot { it.type == type && it.id == id } +
            if (saved) listOf(FavoriteRecord(type, id, ts)) else emptyList()
    }

    override suspend fun createSet(name: String, kanjiIds: List<String>, wordIds: List<Long>): String {
        require(name.isNotBlank())
        val id = UUID.randomUUID().toString()
        _setDefinitions.value = listOf(id to (name.trim() to clock.millis())) + _setDefinitions.value
        val distinctKanji = kanjiIds.distinct()
        val distinctWords = wordIds.distinct()
        val added = buildList {
            distinctKanji.forEachIndexed { i, k -> add(StudySetMember(id, ReviewType.KANJI, k, i)) }
            distinctWords.forEachIndexed { i, w -> add(StudySetMember(id, ReviewType.WORD, w.toString(), distinctKanji.size + i)) }
        }
        _setItems.value = _setItems.value + added
        return id
    }

    override suspend fun addSetItem(setId: String, type: ReviewType, itemId: String) {
        val existing = _setItems.value.filter { it.setId == setId }
        if (existing.none { it.type == type && it.itemId == itemId }) {
            val pos = (existing.maxOfOrNull { it.position } ?: -1) + 1
            _setItems.value = _setItems.value + StudySetMember(setId, type, itemId, pos)
        }
    }

    override suspend fun removeSetItem(setId: String, type: ReviewType, itemId: String) {
        _setItems.value = _setItems.value.filterNot { it.setId == setId && it.type == type && it.itemId == itemId }
    }

    override suspend fun moveSetItem(setId: String, type: ReviewType, itemId: String, direction: Int) {
        val inSet = _setItems.value.filter { it.setId == setId }.sortedBy { it.position }.toMutableList()
        val idx = inSet.indexOfFirst { it.type == type && it.itemId == itemId }
        val target = idx + direction
        if (idx >= 0 && target in inSet.indices) {
            val moved = inSet.removeAt(idx)
            inSet.add(target, moved)
            val reindexed = inSet.mapIndexed { pos, m -> m.copy(position = pos) }
            _setItems.value = _setItems.value.filterNot { it.setId == setId } + reindexed
        }
    }

    override suspend fun deleteSet(setId: String) {
        _setDefinitions.value = _setDefinitions.value.filterNot { it.first == setId }
        _setItems.value = _setItems.value.filterNot { it.setId == setId }
    }

    override suspend fun updatePreferences(transform: (StudyPreferences) -> StudyPreferences) {
        _preferences.value = transform(_preferences.value)
    }

    override suspend fun exportSnapshot(): BackupPayload = BackupPayload(
        favorites = _favorites.value.filter { it.type == ReviewType.KANJI }.map { it.id },
        favoriteWords = _favorites.value.filter { it.type == ReviewType.WORD }.mapNotNull { it.id.toLongOrNull() },
        sets = _setDefinitions.value.map { (id, nameAndTime) ->
            val members = _setItems.value.filter { it.setId == id }.sortedBy { it.position }
            StudySet(
                id = id,
                name = nameAndTime.first,
                kanji = members.filter { it.type == ReviewType.KANJI }.map { it.itemId },
                words = members.filter { it.type == ReviewType.WORD }.mapNotNull { it.itemId.toLongOrNull() },
                createdAt = nameAndTime.second,
            )
        },
        reviews = _reviews.value.associateBy { "${it.type.name.lowercase()}:${it.id}" },
        sessions = _sessions.value,
        preferences = _preferences.value,
    )

    override suspend fun mergeSnapshot(value: BackupPayload) {
        val now = clock.millis()
        value.favorites.forEach { toggleFavorite(ReviewType.KANJI, it, true, now) }
        value.favoriteWords.forEach { toggleFavorite(ReviewType.WORD, it.toString(), true, now) }

        val existingIds = _setDefinitions.value.map { it.first }.toSet()
        value.sets.filter { it.id !in existingIds }.forEach { set ->
            _setDefinitions.value = _setDefinitions.value + (set.id to (set.name to set.createdAt))
            val added = buildList {
                set.kanji.forEachIndexed { i, k -> add(StudySetMember(set.id, ReviewType.KANJI, k, i)) }
                set.words.forEachIndexed { i, w -> add(StudySetMember(set.id, ReviewType.WORD, w.toString(), set.kanji.size + i)) }
            }
            _setItems.value = _setItems.value + added
        }

        val byKey = _reviews.value.associateBy { "${it.type.name.lowercase()}:${it.id}" }.toMutableMap()
        value.reviews.forEach { (key, incoming) ->
            val current = byKey[key]
            if (current == null || incoming.lastReviewedAt >= current.lastReviewedAt) {
                byKey[key] = incoming
            }
        }
        _reviews.value = byKey.values.toList()
        _sessions.value = (_sessions.value + value.sessions).distinctBy { it.id }.sortedByDescending { it.finishedAt }.take(200)
        _preferences.value = value.preferences
    }
}
