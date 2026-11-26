package com.kanjiforge.app.domain

class ReviewScheduler {
    fun schedule(
        previous: ReviewRecord?,
        rating: Rating,
        now: Long,
        type: ReviewType = previous?.type ?: ReviewType.KANJI,
        id: String = previous?.id.orEmpty(),
    ): ReviewRecord {
        var repetitions = previous?.repetitions ?: 0
        var interval = previous?.intervalDays ?: 0.0
        when (rating) {
            Rating.AGAIN -> {
                repetitions = 0
                interval = 10.0 / 1440.0
            }
            Rating.HARD -> {
                repetitions += 1
                interval = maxOf(1.0, interval * 1.2)
            }
            Rating.GOOD -> {
                repetitions += 1
                interval = if (interval < 1.0) 1.0 else interval * 2.0
            }
            Rating.EASY -> {
                repetitions += 1
                interval = maxOf(4.0, interval * 2.5)
            }
        }
        return ReviewRecord(
            type = type,
            id = id,
            repetitions = repetitions,
            intervalDays = interval,
            dueAt = now + (interval * 86_400_000).toLong(),
            lastReviewedAt = now,
            firstReviewedAt = previous?.firstReviewedAt ?: previous?.lastReviewedAt ?: now,
            lastRating = rating,
        )
    }
}
