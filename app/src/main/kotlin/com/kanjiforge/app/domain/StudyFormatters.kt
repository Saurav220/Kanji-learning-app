package com.kanjiforge.app.domain

import com.kanjiforge.app.data.reference.model.StudyRatingFilter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

fun formatInterval(intervalDays: Double): String {
    if (!intervalDays.isFinite() || intervalDays <= 0.0) return "1 min"
    return when {
        intervalDays < 1.0 / 24.0 -> {
            val minutes = (intervalDays * 1440.0).roundToInt().coerceAtLeast(1)
            "$minutes min"
        }
        intervalDays < 1.0 -> {
            val hours = (intervalDays * 24.0).roundToInt().coerceAtLeast(1)
            "$hours hr"
        }
        intervalDays < 30.0 -> {
            val days = intervalDays.roundToInt().coerceAtLeast(1)
            if (days == 1) "1 day" else "$days days"
        }
        intervalDays < 365.0 -> "${trimTrailingZero(intervalDays / 30.0)} mo"
        else -> "${trimTrailingZero(intervalDays / 365.0)} yr"
    }
}

private fun trimTrailingZero(value: Double): String {
    val rounded = (value * 10.0).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else "%.1f".format(Locale.US, rounded)
}

fun formatDueStatus(dueAt: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    if (dueAt <= now) return "Due now"
    val day = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return if (day == today) {
        val time = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        "Next review · Today $time"
    } else {
        "Next review · $day"
    }
}

fun formatSessionTimestamp(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d · HH:mm", Locale.getDefault())
    return Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime().format(formatter)
}

fun CardDirection.displayLabel(): String = when (this) {
    CardDirection.MEANING -> "Meaning → kanji"
    CardDirection.READING -> "Reading → kanji"
    CardDirection.KANJI -> "Kanji → meaning"
}

fun QuizPrompt.displayLabel(): String = when (this) {
    QuizPrompt.MEANING -> "Meanings"
    QuizPrompt.READING -> "Readings"
    QuizPrompt.WORD -> "Vocabulary"
    QuizPrompt.SENTENCE -> "Sentences"
    QuizPrompt.MIXED -> "Mixed questions"
}

fun Rating.displayLabel(): String = when (this) {
    Rating.AGAIN -> "Again"
    Rating.HARD -> "Hard"
    Rating.GOOD -> "Good"
    Rating.EASY -> "Easy"
}

fun StudyRatingFilter.displayLabel(): String = when (this) {
    StudyRatingFilter.UNSTUDIED -> "Unstudied"
    StudyRatingFilter.AGAIN -> "Again"
    StudyRatingFilter.HARD -> "Hard"
    StudyRatingFilter.GOOD -> "Good"
    StudyRatingFilter.EASY -> "Easy"
}

fun gradeLabel(grade: Int): String = when (grade) {
    in 1..6 -> "Primary $grade"
    in 7..9 -> "Secondary ${grade - 6}"
    10 -> "Advanced"
    else -> "Grade $grade"
}
