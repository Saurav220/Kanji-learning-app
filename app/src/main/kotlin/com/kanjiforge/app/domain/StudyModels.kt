package com.kanjiforge.app.domain

import java.time.LocalDate

enum class Rating { AGAIN, HARD, GOOD, EASY }
enum class ReviewType { KANJI, WORD }
enum class SessionMode { CARDS, QUIZ, WRITING }
enum class CardDirection { MEANING, READING, KANJI }
enum class QuizPrompt { MEANING, READING, WORD, SENTENCE, MIXED }

data class ReviewRecord(
    val type: ReviewType,
    val id: String,
    val repetitions: Int,
    val intervalDays: Double,
    val dueAt: Long,
    val lastReviewedAt: Long,
    val firstReviewedAt: Long,
    val lastRating: Rating,
)

data class FavoriteRecord(
    val type: ReviewType,
    val id: String,
    val savedAt: Long,
)

data class SessionRecord(
    val id: String,
    val mode: SessionMode,
    val startedAt: Long,
    val finishedAt: Long,
    val total: Int,
    val correct: Int,
)

data class StudyPreferences(
    val dailyNew: Int = 100,
    val cardDirection: CardDirection = CardDirection.MEANING,
    val quizPrompt: QuizPrompt = QuizPrompt.MIXED,
    val repeatWrongAnswers: Boolean = false,
    val autoPlayAudio: Boolean = false,
    val amoledDark: Boolean = false,
)

data class StudySet(
    val id: String,
    val name: String,
    val kanji: List<String>,
    val words: List<Long>,
    val createdAt: Long,
)

data class StudySetSummary(
    val id: String,
    val name: String,
    val createdAt: Long,
    val kanjiCount: Int,
    val wordCount: Int,
)

data class StudySetMember(
    val setId: String,
    val type: ReviewType,
    val itemId: String,
    val position: Int,
)

data class IntervalBucket(
    val label: String,
    val count: Int,
)

data class DayActivity(
    val date: LocalDate,
    val count: Int,
    val isToday: Boolean,
)

data class BackupPayload(
    val favorites: List<String>,
    val favoriteWords: List<Long>,
    val sets: List<StudySet>,
    val reviews: Map<String, ReviewRecord>,
    val sessions: List<SessionRecord>,
    val preferences: StudyPreferences,
)
