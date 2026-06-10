package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {
    // --- Favorites Queries ---
    @Query("SELECT * FROM favorite_manga ORDER BY addedTime DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorite_manga ORDER BY addedTime DESC")
    suspend fun getFavoritesSync(): List<FavoriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_manga WHERE mangaId = :mangaId LIMIT 1)")
    fun isFavoriteFlow(mangaId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_manga WHERE mangaId = :mangaId LIMIT 1)")
    suspend fun isFavoriteSync(mangaId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorite_manga WHERE mangaId = :mangaId")
    suspend fun deleteFavoriteById(mangaId: String)

    @Query("UPDATE favorite_manga SET latestChapterNum = :latestChapter, hasUpdates = :hasUpdates, lastCheckedTime = :lastChecked WHERE mangaId = :mangaId")
    suspend fun updateFavoriteStatus(mangaId: String, latestChapter: String, hasUpdates: Boolean, lastChecked: Long = System.currentTimeMillis())

    // --- History Queries ---
    @Query("SELECT * FROM read_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ReadHistoryEntity>>

    @Query("SELECT * FROM read_history WHERE mangaId = :mangaId ORDER BY timestamp DESC LIMIT 1")
    fun getLatestHistoryForManga(mangaId: String): Flow<ReadHistoryEntity?>

    @Query("SELECT * FROM read_history WHERE chapterId = :chapterId LIMIT 1")
    suspend fun getHistoryForChapter(chapterId: String): ReadHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ReadHistoryEntity)

    @Query("DELETE FROM read_history WHERE chapterId = :chapterId")
    suspend fun deleteHistory(chapterId: String)

    @Query("DELETE FROM read_history WHERE mangaId = :mangaId")
    suspend fun clearHistoryForManga(mangaId: String)

    @Query("DELETE FROM read_history")
    suspend fun clearAllHistory()
}
