package com.kanjiforge.app.data.user

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "favorites", primaryKeys = ["type", "itemId"])
data class FavoriteEntity(val type: String, val itemId: String, val savedAt: Long)

@Entity(tableName = "study_sets", primaryKeys = ["id"])
data class StudySetEntity(val id: String, val name: String, val createdAt: Long)

@Entity(
    tableName = "study_set_items",
    primaryKeys = ["setId", "type", "itemId"],
    foreignKeys = [ForeignKey(entity = StudySetEntity::class, parentColumns = ["id"], childColumns = ["setId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("setId"), Index(value = ["setId", "position"])],
)
data class StudySetItemEntity(val setId: String, val type: String, val itemId: String, val position: Int)

@Entity(tableName = "reviews", primaryKeys = ["type", "itemId"], indices = [Index("dueAt")])
data class ReviewEntity(
    val type: String,
    val itemId: String,
    val repetitions: Int,
    val intervalDays: Double,
    val dueAt: Long,
    val lastReviewedAt: Long,
    val firstReviewedAt: Long,
    val lastRating: String,
)

@Entity(tableName = "sessions", primaryKeys = ["id"], indices = [Index("finishedAt")])
data class SessionEntity(
    val id: String,
    val mode: String,
    val startedAt: Long,
    val finishedAt: Long,
    val total: Int,
    val correct: Int,
)
