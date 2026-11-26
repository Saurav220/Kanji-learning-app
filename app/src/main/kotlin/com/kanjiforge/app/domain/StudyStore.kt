package com.kanjiforge.app.domain

import kotlinx.coroutines.flow.Flow

interface StudyStore {
    val preferences: Flow<StudyPreferences>
    val favorites: Flow<List<FavoriteRecord>>
    val reviews: Flow<List<ReviewRecord>>
    val sessions: Flow<List<SessionRecord>>
    val sets: Flow<List<StudySetSummary>>

    fun observeSetItems(setId: String): Flow<List<StudySetMember>>
    fun observeContainingSetIds(type: ReviewType, itemId: String): Flow<Set<String>>
    suspend fun getSetItems(setId: String): List<StudySetMember>

    suspend fun recordReview(type: ReviewType, id: String, rating: Rating, now: Long? = null): ReviewRecord
    suspend fun finishSession(session: SessionRecord)
    suspend fun toggleFavorite(type: ReviewType, id: String, saved: Boolean, now: Long? = null)

    suspend fun createSet(name: String, kanjiIds: List<String>, wordIds: List<Long>): String
    suspend fun addSetItem(setId: String, type: ReviewType, itemId: String)
    suspend fun removeSetItem(setId: String, type: ReviewType, itemId: String)
    suspend fun moveSetItem(setId: String, type: ReviewType, itemId: String, direction: Int)
    suspend fun deleteSet(setId: String)

    suspend fun updatePreferences(transform: (StudyPreferences) -> StudyPreferences)
    suspend fun exportSnapshot(): BackupPayload
    suspend fun mergeSnapshot(value: BackupPayload)
}
