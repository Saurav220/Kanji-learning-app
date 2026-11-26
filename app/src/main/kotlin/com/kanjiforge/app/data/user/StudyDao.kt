package com.kanjiforge.app.data.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    @Query("SELECT * FROM favorites WHERE type=:type ORDER BY savedAt DESC")
    fun observeFavorites(type: String): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY savedAt DESC")
    fun observeAllFavorites(): Flow<List<FavoriteEntity>>

    @Upsert
    suspend fun saveFavorite(item: FavoriteEntity)

    @Query("SELECT * FROM favorites WHERE type=:type AND itemId=:itemId LIMIT 1")
    suspend fun findFavorite(type: String, itemId: String): FavoriteEntity?

    @Query("DELETE FROM favorites WHERE type=:type AND itemId=:itemId")
    suspend fun deleteFavorite(type: String, itemId: String)

    @Query("SELECT * FROM reviews ORDER BY dueAt")
    fun observeReviews(): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews WHERE type=:type AND dueAt<=:now ORDER BY dueAt")
    suspend fun dueReviews(type: String, now: Long): List<ReviewEntity>

    @Upsert
    suspend fun saveReview(item: ReviewEntity)

    @Query("SELECT * FROM reviews WHERE type=:type AND itemId=:itemId LIMIT 1")
    suspend fun findReview(type: String, itemId: String): ReviewEntity?

    @Query("SELECT * FROM study_sets ORDER BY createdAt DESC")
    fun observeSets(): Flow<List<StudySetEntity>>

    @Query("SELECT * FROM study_set_items ORDER BY setId,position,type,itemId")
    fun observeAllSetItems(): Flow<List<StudySetItemEntity>>

    @Query("SELECT setId FROM study_set_items WHERE type=:type AND itemId=:itemId")
    fun observeContainingSetIds(type: String, itemId: String): Flow<List<String>>

    @Query("SELECT * FROM study_set_items WHERE setId=:setId ORDER BY position,type,itemId")
    suspend fun setItems(setId: String): List<StudySetItemEntity>

    @Query("SELECT * FROM study_set_items WHERE setId=:setId ORDER BY position,type,itemId")
    fun observeSetItems(setId: String): Flow<List<StudySetItemEntity>>

    @Query("SELECT id FROM study_sets")
    suspend fun existingSetIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSet(item: StudySetEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSetItems(items: List<StudySetItemEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSetItem(item: StudySetItemEntity)

    @Update
    suspend fun updateSetItems(items: List<StudySetItemEntity>)

    @Query("DELETE FROM study_set_items WHERE setId=:setId AND type=:type AND itemId=:itemId")
    suspend fun deleteSetItem(setId: String, type: String, itemId: String)

    @Query("DELETE FROM study_sets WHERE id=:setId")
    suspend fun deleteSet(setId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(item: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY finishedAt DESC LIMIT :limit")
    fun observeSessions(limit: Int = 200): Flow<List<SessionEntity>>

    @Query("DELETE FROM sessions WHERE id NOT IN (SELECT id FROM sessions ORDER BY finishedAt DESC LIMIT 200)")
    suspend fun trimSessions()

    @Query("SELECT * FROM sessions ORDER BY finishedAt DESC")
    suspend fun allSessions(): List<SessionEntity>

    @Query("SELECT * FROM favorites")
    suspend fun allFavorites(): List<FavoriteEntity>

    @Query("SELECT * FROM study_sets ORDER BY createdAt")
    suspend fun allSets(): List<StudySetEntity>

    @Query("SELECT * FROM study_set_items ORDER BY setId,position")
    suspend fun allSetItems(): List<StudySetItemEntity>

    @Query("SELECT * FROM reviews")
    suspend fun allReviews(): List<ReviewEntity>

    @Query("DELETE FROM favorites")
    suspend fun clearFavorites()

    @Query("DELETE FROM study_sets")
    suspend fun clearSets()

    @Query("DELETE FROM reviews")
    suspend fun clearReviews()

    @Query("DELETE FROM sessions")
    suspend fun clearSessions()
}
