package com.kanjiforge.app.data.user

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.kanjiforge.app.domain.BackupPayload
import com.kanjiforge.app.domain.CardDirection
import com.kanjiforge.app.domain.FavoriteRecord
import com.kanjiforge.app.domain.QuizPrompt
import com.kanjiforge.app.domain.Rating
import com.kanjiforge.app.domain.ReviewRecord
import com.kanjiforge.app.domain.ReviewScheduler
import com.kanjiforge.app.domain.ReviewType
import com.kanjiforge.app.domain.SessionMode
import com.kanjiforge.app.domain.SessionRecord
import com.kanjiforge.app.domain.StudyPreferences
import com.kanjiforge.app.domain.StudySet
import com.kanjiforge.app.domain.StudySetMember
import com.kanjiforge.app.domain.StudySetSummary
import com.kanjiforge.app.domain.StudyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID

private val Context.studyPreferences by preferencesDataStore(name = "study_preferences")

class StudyRepository(
    private val database: AppDatabase,
    private val context: Context,
    private val scheduler: ReviewScheduler = ReviewScheduler(),
    private val clock: Clock = Clock.systemDefaultZone(),
) : StudyStore {
    private val dao = database.studyDao()
    private val dailyNewKey = intPreferencesKey("daily_new")
    private val dailyNewDefaultMigrationKey = booleanPreferencesKey("daily_new_default_100_migrated")
    private val cardDirectionKey = stringPreferencesKey("card_direction")
    private val quizPromptKey = stringPreferencesKey("quiz_prompt")
    private val repeatWrongKey = booleanPreferencesKey("repeat_wrong_answers")
    private val autoPlayAudioKey = booleanPreferencesKey("auto_play_audio")
    private val amoledDarkKey = booleanPreferencesKey("amoled_dark")

    override val preferences: Flow<StudyPreferences> = context.studyPreferences.data.map(::readPreferences)

    override val favorites: Flow<List<FavoriteRecord>> = dao.observeAllFavorites().map { rows ->
        rows.map { it.toModel() }
    }

    override val reviews: Flow<List<ReviewRecord>> = dao.observeReviews().map { rows ->
        rows.map { it.toModel() }
    }

    override val sessions: Flow<List<SessionRecord>> = dao.observeSessions(200).map { rows ->
        rows.map { it.toModel() }
    }

    override val sets: Flow<List<StudySetSummary>> =
        combine(dao.observeSets(), dao.observeAllSetItems()) { setRows, itemRows ->
            val bySet = itemRows.groupBy { it.setId }
            setRows.map { set ->
                val members = bySet[set.id].orEmpty()
                StudySetSummary(
                    id = set.id,
                    name = set.name,
                    createdAt = set.createdAt,
                    kanjiCount = members.count { it.type == "kanji" },
                    wordCount = members.count { it.type == "word" },
                )
            }
        }

    override fun observeSetItems(setId: String): Flow<List<StudySetMember>> =
        dao.observeSetItems(setId).map { rows -> rows.map { it.toModel() } }

    override fun observeContainingSetIds(type: ReviewType, itemId: String): Flow<Set<String>> =
        dao.observeContainingSetIds(type.name.lowercase(), itemId).map { it.toSet() }

    override suspend fun getSetItems(setId: String): List<StudySetMember> =
        dao.setItems(setId).map { it.toModel() }

    override suspend fun recordReview(
        type: ReviewType,
        id: String,
        rating: Rating,
        now: Long?,
    ): ReviewRecord {
        val timestamp = now ?: clock.millis()
        return database.withTransaction {
            val kind = type.name.lowercase()
            val previous = dao.findReview(kind, id)?.toModel()
            val updated = scheduler.schedule(previous, rating, timestamp, type = type, id = id)
            dao.saveReview(updated.toEntity())
            updated
        }
    }

    override suspend fun finishSession(session: SessionRecord) {
        database.withTransaction {
            dao.insertSession(session.toEntity())
            dao.trimSessions()
        }
    }

    override suspend fun toggleFavorite(type: ReviewType, id: String, saved: Boolean, now: Long?) {
        val kind = type.name.lowercase()
        val timestamp = now ?: clock.millis()
        if (saved) dao.saveFavorite(FavoriteEntity(kind, id, timestamp))
        else dao.deleteFavorite(kind, id)
    }

    override suspend fun createSet(name: String, kanjiIds: List<String>, wordIds: List<Long>): String {
        require(name.isNotBlank()) { "Collection name cannot be empty." }
        val id = UUID.randomUUID().toString()
        val distinctKanji = kanjiIds.distinct()
        val distinctWords = wordIds.distinct()
        database.withTransaction {
            dao.insertSet(StudySetEntity(id, name.trim(), clock.millis()))
            val entries = buildList {
                distinctKanji.forEachIndexed { index, itemId ->
                    add(StudySetItemEntity(id, "kanji", itemId, index))
                }
                distinctWords.forEachIndexed { index, itemId ->
                    add(StudySetItemEntity(id, "word", itemId.toString(), distinctKanji.size + index))
                }
            }
            dao.insertSetItems(entries)
        }
        return id
    }

    override suspend fun addSetItem(setId: String, type: ReviewType, itemId: String) {
        require(itemId.isNotBlank())
        val kind = type.name.lowercase()
        database.withTransaction {
            require(setId in dao.existingSetIds()) { "This collection no longer exists." }
            val items = dao.setItems(setId)
            if (items.none { it.type == kind && it.itemId == itemId }) {
                dao.insertSetItem(
                    StudySetItemEntity(setId, kind, itemId, (items.maxOfOrNull { it.position } ?: -1) + 1),
                )
            }
        }
    }

    override suspend fun removeSetItem(setId: String, type: ReviewType, itemId: String) {
        dao.deleteSetItem(setId, type.name.lowercase(), itemId)
    }

    override suspend fun moveSetItem(setId: String, type: ReviewType, itemId: String, direction: Int) {
        require(direction == -1 || direction == 1)
        val kind = type.name.lowercase()
        database.withTransaction {
            val items = dao.setItems(setId).toMutableList()
            val current = items.indexOfFirst { it.type == kind && it.itemId == itemId }
            val target = current + direction
            if (current >= 0 && target in items.indices) {
                val moved = items.removeAt(current)
                items.add(target, moved)
                dao.updateSetItems(items.mapIndexed { index, item -> item.copy(position = index) })
            }
        }
    }

    override suspend fun deleteSet(setId: String) {
        dao.deleteSet(setId)
    }

    override suspend fun updatePreferences(transform: (StudyPreferences) -> StudyPreferences) {
        context.studyPreferences.edit { settings ->
            val updated = transform(readPreferences(settings))
            settings[dailyNewKey] = updated.dailyNew.coerceIn(1, 100)
            settings[dailyNewDefaultMigrationKey] = true
            settings[cardDirectionKey] = updated.cardDirection.name
            settings[quizPromptKey] = updated.quizPrompt.name
            settings[repeatWrongKey] = updated.repeatWrongAnswers
            settings[autoPlayAudioKey] = updated.autoPlayAudio
            settings[amoledDarkKey] = updated.amoledDark
        }
    }

    override suspend fun exportSnapshot(): BackupPayload {
        val prefs = preferences.first()
        return database.withTransaction {
            val saved = dao.allFavorites()
            val sets = dao.allSets()
            val itemsBySet = dao.allSetItems().groupBy { it.setId }
            val reviewRows = dao.allReviews().associate { row ->
                val model = row.toModel()
                "${model.type.name.lowercase()}:${model.id}" to model
            }
            BackupPayload(
                favorites = saved.filter { it.type == "kanji" }.map { it.itemId }.distinct(),
                favoriteWords = saved.filter { it.type == "word" }.mapNotNull { it.itemId.toLongOrNull() }.distinct(),
                sets = sets.map { set ->
                    val members = itemsBySet[set.id].orEmpty()
                    StudySet(
                        set.id,
                        set.name,
                        members.filter { it.type == "kanji" }.map { it.itemId },
                        members.filter { it.type == "word" }.mapNotNull { it.itemId.toLongOrNull() },
                        set.createdAt,
                    )
                },
                reviews = reviewRows,
                sessions = dao.allSessions().take(200).map { it.toModel() },
                preferences = prefs,
            )
        }
    }

    override suspend fun mergeSnapshot(value: BackupPayload) {
        val now = clock.millis()
        database.withTransaction {
            value.favorites.forEach { dao.saveFavorite(FavoriteEntity("kanji", it, now)) }
            value.favoriteWords.forEach { dao.saveFavorite(FavoriteEntity("word", it.toString(), now)) }
            val existingSetIds = dao.existingSetIds().toHashSet()
            value.sets.filter { it.id !in existingSetIds }.forEach { set ->
                dao.insertSet(StudySetEntity(set.id, set.name, set.createdAt))
                val distinctKanji = set.kanji.distinct()
                val distinctWords = set.words.distinct()
                val members = buildList {
                    distinctKanji.forEachIndexed { index, itemId ->
                        add(StudySetItemEntity(set.id, "kanji", itemId, index))
                    }
                    distinctWords.forEachIndexed { index, itemId ->
                        add(StudySetItemEntity(set.id, "word", itemId.toString(), distinctKanji.size + index))
                    }
                }
                dao.insertSetItems(members)
            }
            val existingReviews = dao.allReviews().associateBy { "${it.type}:${it.itemId}" }
            value.reviews.values.forEach { incoming ->
                val key = "${incoming.type.name.lowercase()}:${incoming.id}"
                val existing = existingReviews[key]
                if (existing == null || incoming.lastReviewedAt >= existing.lastReviewedAt) {
                    dao.saveReview(incoming.toEntity())
                }
            }
            val sessionRows = (dao.allSessions().map { it.toModel() } + value.sessions)
                .distinctBy { it.id }
                .sortedByDescending { it.finishedAt }
                .take(200)
            dao.clearSessions()
            sessionRows.forEach { dao.insertSession(it.toEntity()) }
        }
        updatePreferences { value.preferences }
    }

    private fun readPreferences(values: Preferences): StudyPreferences = StudyPreferences(
        dailyNew = if (values[dailyNewDefaultMigrationKey] == true) {
            values[dailyNewKey]?.coerceIn(1, 100) ?: 100
        } else {
            100
        },
        cardDirection = runCatching {
            CardDirection.valueOf(values[cardDirectionKey] ?: "MEANING")
        }.getOrDefault(CardDirection.MEANING),
        quizPrompt = runCatching {
            QuizPrompt.valueOf(values[quizPromptKey] ?: QuizPrompt.MEANING.name)
        }.getOrDefault(QuizPrompt.MEANING),
        repeatWrongAnswers = values[repeatWrongKey] ?: false,
        autoPlayAudio = values[autoPlayAudioKey] ?: false,
        amoledDark = values[amoledDarkKey] ?: false,
    )

    private fun FavoriteEntity.toModel() = FavoriteRecord(
        type = if (type.equals("word", ignoreCase = true)) ReviewType.WORD else ReviewType.KANJI,
        id = itemId,
        savedAt = savedAt,
    )

    private fun StudySetItemEntity.toModel() = StudySetMember(
        setId = setId,
        type = if (type.equals("word", ignoreCase = true)) ReviewType.WORD else ReviewType.KANJI,
        itemId = itemId,
        position = position,
    )

    private fun ReviewEntity.toModel() = ReviewRecord(
        type = runCatching { ReviewType.valueOf(type.uppercase()) }.getOrDefault(ReviewType.KANJI),
        id = itemId,
        repetitions = repetitions,
        intervalDays = intervalDays,
        dueAt = dueAt,
        lastReviewedAt = lastReviewedAt,
        firstReviewedAt = firstReviewedAt,
        lastRating = runCatching { Rating.valueOf(lastRating.uppercase()) }.getOrDefault(Rating.AGAIN),
    )

    private fun ReviewRecord.toEntity() = ReviewEntity(
        type.name.lowercase(), id, repetitions, intervalDays, dueAt, lastReviewedAt, firstReviewedAt, lastRating.name.lowercase(),
    )

    private fun SessionEntity.toModel() = SessionRecord(
        id,
        runCatching { SessionMode.valueOf(mode.uppercase()) }.getOrDefault(SessionMode.CARDS),
        startedAt,
        finishedAt,
        total,
        correct,
    )

    private fun SessionRecord.toEntity() = SessionEntity(
        id, mode.name.lowercase(), startedAt, finishedAt, total, correct,
    )
}
