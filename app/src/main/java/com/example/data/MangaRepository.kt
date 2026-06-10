package com.example.data

import android.util.Log
import com.example.data.database.FavoriteEntity
import com.example.data.database.MangaDao
import com.example.data.database.ReadHistoryEntity
import com.example.data.network.MangaDexApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MangaRepository(private val mangaDao: MangaDao) {
    private val TAG = "MangaRepository"

    val favoriteMangaList: Flow<List<FavoriteEntity>> = mangaDao.getAllFavorites()
    val readHistoryList: Flow<List<ReadHistoryEntity>> = mangaDao.getAllHistory()

    // Cache to hold popular and searched items during app session for responsiveness
    private val cachedPopular = mutableListOf<Manga>()
    private var lastCachedRatings = listOf<String>()

    suspend fun getPopularManga(forceRefresh: Boolean = false, contentRatings: List<String> = listOf("safe")): List<Manga> = withContext(Dispatchers.IO) {
        if (cachedPopular.isNotEmpty() && !forceRefresh && lastCachedRatings == contentRatings) {
            return@withContext cachedPopular
        }
        val fetchedList = MangaDexApi.fetchPopularManga(limit = 30, contentRatings = contentRatings)
        if (fetchedList.isNotEmpty()) {
            cachedPopular.clear()
            cachedPopular.addAll(fetchedList)
            lastCachedRatings = contentRatings
        }
        fetchedList
    }

    suspend fun searchManga(query: String, contentRatings: List<String> = listOf("safe")): List<Manga> = withContext(Dispatchers.IO) {
        MangaDexApi.searchManga(query, limit = 30, contentRatings = contentRatings)
    }

    suspend fun getMangaDetails(mangaId: String): Manga? = withContext(Dispatchers.IO) {
        MangaDexApi.fetchMangaDetails(mangaId)
    }

    suspend fun getMangaChapters(mangaId: String): List<Chapter> = withContext(Dispatchers.IO) {
        MangaDexApi.fetchMangaChapters(mangaId, limit = 100)
    }

    suspend fun getChapterDetails(chapterId: String, mangaId: String = ""): Chapter? = withContext(Dispatchers.IO) {
        MangaDexApi.fetchChapterDetails(chapterId, mangaId)
    }

    suspend fun getChapterPages(chapterId: String): ChapterPages? = withContext(Dispatchers.IO) {
        MangaDexApi.fetchChapterPages(chapterId)
    }

    // --- Favorites Control ---
    fun isFavorite(mangaId: String): Flow<Boolean> {
        return mangaDao.isFavoriteFlow(mangaId)
    }

    suspend fun toggleFavorite(manga: Manga) = withContext(Dispatchers.IO) {
        val alreadyFav = mangaDao.isFavoriteSync(manga.id)
        if (alreadyFav) {
            mangaDao.deleteFavoriteById(manga.id)
            Log.d(TAG, "Removed from favorites: ${manga.title}")
        } else {
            // Check latest chapter first to store as current baseline
            val chapters = MangaDexApi.fetchMangaChapters(manga.id, limit = 1)
            val latestNum = if (chapters.isNotEmpty()) chapters[0].chapterNumber else ""
            
            val favEntity = FavoriteEntity(
                mangaId = manga.id,
                title = manga.title,
                coverUrl = manga.coverUrl,
                description = manga.description,
                status = manga.status,
                latestChapterNum = latestNum,
                hasUpdates = false,
                lastCheckedTime = System.currentTimeMillis()
            )
            mangaDao.insertFavorite(favEntity)
            Log.d(TAG, "Added to favorites: ${manga.title} with baseline latest chapter $latestNum")
        }
    }

    /**
     * Scans favorited manga in the database, fetching their newest chapters 
     * from live MangaDex feeds, updating Room, and triggering dynamic UI notifications.
     */
    suspend fun checkFavoritesForUpdates(): Int = withContext(Dispatchers.IO) {
        Log.d(TAG, "Checking favorites for author updates...")
        val favorites = mangaDao.getFavoritesSync()
        var updatedCount = 0

        for (fav in favorites) {
            try {
                // Get highest chapter (first chapter in the descending feed list)
                val chapters = MangaDexApi.fetchMangaChapters(fav.mangaId, limit = 1)
                if (chapters.isNotEmpty()) {
                    val latestExtractedNum = chapters[0].chapterNumber
                    
                    // If the online latest chapter is newer than what we recorded
                    if (latestExtractedNum.isNotEmpty() && latestExtractedNum != fav.latestChapterNum) {
                        val isFirstRun = fav.latestChapterNum.isEmpty()
                        
                        mangaDao.updateFavoriteStatus(
                            mangaId = fav.mangaId,
                            latestChapter = latestExtractedNum,
                            hasUpdates = !isFirstRun // Only trigger flag if they had a previous baseline
                        )
                        if (!isFirstRun) {
                            updatedCount++
                            Log.d(TAG, "Manga updated: '${fav.title}' has a new chapter $latestExtractedNum (Old: ${fav.latestChapterNum})")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking update for favorited manga '${fav.title}'", e)
            }
        }
        updatedCount
    }

    suspend fun markFavoriteUpdateClear(mangaId: String) = withContext(Dispatchers.IO) {
        // Find favorite and clear update flag
        val favorites = mangaDao.getFavoritesSync()
        val fav = favorites.find { it.mangaId == mangaId }
        if (fav != null) {
            mangaDao.updateFavoriteStatus(
                mangaId = mangaId,
                latestChapter = fav.latestChapterNum,
                hasUpdates = false
            )
        }
    }

    // --- History Control ---
    fun getLatestMangaHistoryFlow(mangaId: String): Flow<ReadHistoryEntity?> {
        return mangaDao.getLatestHistoryForManga(mangaId)
    }

    suspend fun getChapterHistory(chapterId: String): ReadHistoryEntity? = withContext(Dispatchers.IO) {
        mangaDao.getHistoryForChapter(chapterId)
    }

    suspend fun updateReadingProgress(
        mangaId: String,
        mangaTitle: String,
        coverUrl: String,
        chapterId: String,
        chapterNumber: String,
        chapterTitle: String,
        currentPageIndex: Int,
        totalPages: Int
    ) = withContext(Dispatchers.IO) {
        val history = ReadHistoryEntity(
            chapterId = chapterId,
            mangaId = mangaId,
            mangaTitle = mangaTitle,
            coverUrl = coverUrl,
            chapterNumber = chapterNumber,
            chapterTitle = chapterTitle,
            lastReadPage = currentPageIndex,
            totalPages = totalPages,
            timestamp = System.currentTimeMillis()
        )
        mangaDao.insertHistory(history)
    }

    suspend fun clearHistoryForManga(mangaId: String) = withContext(Dispatchers.IO) {
        mangaDao.clearHistoryForManga(mangaId)
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        mangaDao.clearAllHistory()
    }
}
