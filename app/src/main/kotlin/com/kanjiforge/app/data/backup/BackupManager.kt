package com.kanjiforge.app.data.backup

import com.kanjiforge.app.domain.BackupPayload
import com.kanjiforge.app.domain.CardDirection
import com.kanjiforge.app.domain.QuizPrompt
import com.kanjiforge.app.domain.Rating
import com.kanjiforge.app.domain.ReviewRecord
import com.kanjiforge.app.domain.ReviewType
import com.kanjiforge.app.domain.SessionMode
import com.kanjiforge.app.domain.SessionRecord
import com.kanjiforge.app.domain.StudyPreferences
import com.kanjiforge.app.domain.StudySet
import com.kanjiforge.app.domain.StudyStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

data class BackupResult(val success: Boolean, val message: String)

class BackupManager(
    private val store: StudyStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    @Serializable
    private data class BackupDto(
        val version: Int,
        val favorites: List<String>,
        val favoriteWords: List<Long> = emptyList(),
        val sets: List<StudySetDto>,
        val reviews: Map<String, ReviewDto>,
        val sessions: List<SessionDto> = emptyList(),
        val preferences: PreferencesDto = PreferencesDto(),
    )

    @Serializable
    private data class StudySetDto(
        val id: String,
        val name: String,
        val kanji: List<String>,
        val words: List<Long> = emptyList(),
        val createdAt: Long,
    )

    @Serializable
    private data class ReviewDto(
        val type: String,
        val id: String,
        val repetitions: Int,
        val intervalDays: Double,
        val dueAt: Long,
        val lastReviewedAt: Long,
        val firstReviewedAt: Long? = null,
        val lastRating: String,
    )

    @Serializable
    private data class SessionDto(
        val id: String,
        val mode: String,
        val startedAt: Long,
        val finishedAt: Long,
        val total: Int,
        val correct: Int,
    )

    @Serializable
    private data class PreferencesDto(
        val dailyNew: Double = 100.0,
        val cardDirection: String = "meaning",
        val quizPrompt: String = "meaning",
        val repeatWrongAnswers: Boolean = false,
        val autoPlayAudio: Boolean = false,
        val amoledDark: Boolean = false,
    )

    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private val strictJson = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    suspend fun exportVersion2(): ByteArray = withContext(ioDispatcher) {
        val snapshot = store.exportSnapshot()
        val dto = BackupDto(
            version = 2,
            favorites = snapshot.favorites,
            favoriteWords = snapshot.favoriteWords,
            sets = snapshot.sets.map { set ->
                StudySetDto(set.id, set.name, set.kanji, set.words, set.createdAt)
            },
            reviews = snapshot.reviews.mapValues { (_, record) ->
                ReviewDto(
                    type = record.type.name.lowercase(),
                    id = record.id,
                    repetitions = record.repetitions,
                    intervalDays = record.intervalDays,
                    dueAt = record.dueAt,
                    lastReviewedAt = record.lastReviewedAt,
                    firstReviewedAt = record.firstReviewedAt,
                    lastRating = record.lastRating.name.lowercase(),
                )
            },
            sessions = snapshot.sessions.map { session ->
                SessionDto(
                    id = session.id,
                    mode = session.mode.name.lowercase(),
                    startedAt = session.startedAt,
                    finishedAt = session.finishedAt,
                    total = session.total,
                    correct = session.correct,
                )
            },
            preferences = PreferencesDto(
                dailyNew = snapshot.preferences.dailyNew.toDouble(),
                cardDirection = snapshot.preferences.cardDirection.name.lowercase(),
                quizPrompt = snapshot.preferences.quizPrompt.name.lowercase(),
                repeatWrongAnswers = snapshot.preferences.repeatWrongAnswers,
                autoPlayAudio = snapshot.preferences.autoPlayAudio,
                amoledDark = snapshot.preferences.amoledDark,
            ),
        )
        prettyJson.encodeToString(dto).toByteArray(Charsets.UTF_8)
    }

    suspend fun exportTo(output: OutputStream): BackupResult = withContext(ioDispatcher) {
        try {
            val bytes = exportVersion2()
            output.use { it.write(bytes) }
            BackupResult(true, "Backup exported.")
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            BackupResult(false, error.message ?: "Could not export backup.")
        }
    }

    suspend fun mergeVersion2(bytes: ByteArray): BackupResult = withContext(ioDispatcher) {
        try {
            val payload = parseAndValidate(bytes)
            store.mergeSnapshot(payload)
            BackupResult(true, "Backup merged.")
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            BackupResult(false, error.message ?: "This file is not a valid KanjiForge backup.")
        }
    }

    suspend fun importFrom(input: InputStream): BackupResult = withContext(ioDispatcher) {
        try {
            val bytes = input.use { readBounded(it, MAX_BACKUP_BYTES) }
            mergeVersion2(bytes)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            BackupResult(false, error.message ?: "Could not read backup file.")
        }
    }

    private fun parseAndValidate(bytes: ByteArray): BackupPayload {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BACKUP_BYTES) { "Backup file is empty or too large." }
        val dto = try {
            strictJson.decodeFromString<BackupDto>(bytes.toString(Charsets.UTF_8))
        } catch (error: Exception) {
            throw IllegalArgumentException("This file is not a valid KanjiForge version 2 backup.", error)
        }
        require(dto.version == 2) { "This file is not a KanjiForge version 2 backup." }

        val favorites = dto.favorites.filter(String::isNotBlank).distinct()
        val favoriteWords = dto.favoriteWords.filter { it > 0 }.distinct()
        val sets = dto.sets.map { set ->
            val trimmed = set.name.trim()
            require(trimmed.isNotBlank()) { "A saved set is missing its name." }
            StudySet(
                id = set.id,
                name = trimmed,
                kanji = set.kanji.filter(String::isNotBlank).distinct(),
                words = set.words.filter { it > 0 }.distinct(),
                createdAt = set.createdAt,
            )
        }.distinctBy { it.id }

        val reviews = linkedMapOf<String, ReviewRecord>()
        dto.reviews.forEach { (key, item) ->
            val type = enumValue<ReviewType>(item.type)
            val prefix = type.name.lowercase()
            require(key == "$prefix:${item.id}" && item.id.isNotBlank()) { "A review key does not match its record." }
            require(item.intervalDays >= 0.0 && item.intervalDays.isFinite()) { "A review interval is invalid." }
            require(item.repetitions >= 0) { "A review count is invalid." }
            reviews[key] = ReviewRecord(
                type = type,
                id = item.id,
                repetitions = item.repetitions,
                intervalDays = item.intervalDays,
                dueAt = item.dueAt,
                lastReviewedAt = item.lastReviewedAt,
                firstReviewedAt = item.firstReviewedAt ?: item.lastReviewedAt,
                lastRating = enumValue(item.lastRating),
            )
        }

        val sessions = dto.sessions.map { item ->
            require(item.total >= 0 && item.correct in 0..item.total) { "A session score is invalid." }
            SessionRecord(
                id = item.id,
                mode = enumValue(item.mode),
                startedAt = item.startedAt,
                finishedAt = item.finishedAt,
                total = item.total,
                correct = item.correct,
            )
        }.distinctBy { it.id }

        val dailyNewRaw = dto.preferences.dailyNew
        require(dailyNewRaw.isFinite() && dailyNewRaw % 1.0 == 0.0 && dailyNewRaw in 1.0..100.0) {
            "The daily new-character preference is invalid."
        }
        val preferences = StudyPreferences(
            dailyNew = dailyNewRaw.toInt(),
            cardDirection = enumValue(dto.preferences.cardDirection),
            quizPrompt = enumValue(dto.preferences.quizPrompt),
            repeatWrongAnswers = dto.preferences.repeatWrongAnswers,
            autoPlayAudio = dto.preferences.autoPlayAudio,
            amoledDark = dto.preferences.amoledDark,
        )
        return BackupPayload(favorites, favoriteWords, sets, reviews, sessions, preferences)
    }

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            require(total + count <= maxBytes) { "Backup file exceeds the 20 MiB limit." }
            output.write(buffer, 0, count)
            total += count
        }
        return output.toByteArray()
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String): T =
        enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("A backup contains an unsupported value: $value")

    companion object {
        const val MAX_BACKUP_BYTES = 20 * 1024 * 1024
    }
}
