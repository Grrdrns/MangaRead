package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Manga
import com.example.data.Chapter
import com.example.data.ChapterPages
import com.example.data.MangaRepository
import com.example.data.database.FavoriteEntity
import com.example.data.database.MangaDatabase
import com.example.data.database.ReadHistoryEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MangaViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MangaViewModel"

    private val database = MangaDatabase.getDatabase(application)
    private val mangaDao = database.mangaDao()
    private val repository = MangaRepository(mangaDao)

    // --- Explore & Search States ---
    private val _popularManga = MutableStateFlow<List<Manga>>(emptyList())
    val popularManga: StateFlow<List<Manga>> = _popularManga.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Manga>>(emptyList())
    val searchResults: StateFlow<List<Manga>> = _searchResults.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _exploreLoading = MutableStateFlow(false)
    val exploreLoading: StateFlow<Boolean> = _exploreLoading.asStateFlow()

    private val _exploreErrorMessage = MutableStateFlow<String?>(null)
    val exploreErrorMessage: StateFlow<String?> = _exploreErrorMessage.asStateFlow()

    // --- Manga Details State ---
    private val _selectedManga = MutableStateFlow<Manga?>(null)
    val selectedManga: StateFlow<Manga?> = _selectedManga.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    private val _chaptersLoading = MutableStateFlow(false)
    val chaptersLoading: StateFlow<Boolean> = _chaptersLoading.asStateFlow()

    val currentMangaHistory = _selectedManga
        .flatMapLatest { manga ->
            if (manga == null) flowOf(null)
            else repository.getLatestMangaHistoryFlow(manga.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Reader State ---
    private val _activeChapter = MutableStateFlow<Chapter?>(null)
    val activeChapter: StateFlow<Chapter?> = _activeChapter.asStateFlow()

    private val _chapterPages = MutableStateFlow<ChapterPages?>(null)
    val chapterPages: StateFlow<ChapterPages?> = _chapterPages.asStateFlow()

    private val _pagesLoading = MutableStateFlow(false)
    val pagesLoading: StateFlow<Boolean> = _pagesLoading.asStateFlow()

    private val _readerCurrentPage = MutableStateFlow(0)
    val readerCurrentPage: StateFlow<Int> = _readerCurrentPage.asStateFlow()

    // --- Favorites & Database Lists ---
    val favoritesList: StateFlow<List<FavoriteEntity>> = repository.favoriteMangaList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val readHistoryList: StateFlow<List<ReadHistoryEntity>> = repository.readHistoryList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates.asStateFlow()

    private val _updateNotification = MutableStateFlow<String?>(null)
    val updateNotification: StateFlow<String?> = _updateNotification.asStateFlow()

    // --- Preferences: Content Ratings ---
    private val prefs = getApplication<Application>().getSharedPreferences("manga_reader_prefs", android.content.Context.MODE_PRIVATE)

    private val _contentRatings = MutableStateFlow<Set<String>>(
        prefs.getStringSet("content_ratings", setOf("safe", "suggestive")) ?: setOf("safe", "suggestive")
    )
    val contentRatings: StateFlow<Set<String>> = _contentRatings.asStateFlow()

    init {
        // Initial popular load (loadPopularManga will utilize the content ratings initialized above)
        loadPopularManga()
        // Run update checking of favorites automatically on app opening
        checkFavoritesForUpdates(silent = true)
    }

    fun updateContentRatings(ratings: Set<String>) {
        if (ratings.isEmpty()) return
        _contentRatings.value = ratings
        prefs.edit().putStringSet("content_ratings", ratings).apply()
        
        // Refresh popular manga list with new ratings choice
        loadPopularManga()
        // If there is an active search query, re-run search with the updated ratings
        val query = _searchQuery.value
        if (query.isNotBlank()) {
            searchManga(query)
        }
    }

    fun loadPopularManga() {
        viewModelScope.launch {
            _exploreLoading.value = true
            _exploreErrorMessage.value = null
            try {
                val ratingsList = _contentRatings.value.toList()
                val list = repository.getPopularManga(forceRefresh = true, contentRatings = ratingsList)
                _popularManga.value = list
                if (list.isEmpty()) {
                    _exploreErrorMessage.value = "No manga found. Try adjusting content filtration preferences."
                }
            } catch (e: Exception) {
                _exploreErrorMessage.value = "Failed to connect to MangaDex server."
                Log.e(TAG, "Error loading popular", e)
            } finally {
                _exploreLoading.value = false
            }
        }
    }

    fun searchManga(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            if (query.isBlank()) {
                _searchResults.value = emptyList()
                return@launch
            }
            _exploreLoading.value = true
            _exploreErrorMessage.value = null
            try {
                val ratingsList = _contentRatings.value.toList()
                val results = repository.searchManga(query, contentRatings = ratingsList)
                _searchResults.value = results
                if (results.isEmpty()) {
                    _exploreErrorMessage.value = "No matches found for '$query'. Try adjusting content filtration preferences."
                }
            } catch (e: Exception) {
                _exploreErrorMessage.value = "Failed search request cleanly. Retrying..."
                Log.e(TAG, "Search failure", e)
            } finally {
                _exploreLoading.value = false
            }
        }
    }

    fun selectManga(mangaId: String) {
        viewModelScope.launch {
            _chaptersLoading.value = true
            _selectedManga.value = null
            _chapters.value = emptyList()
            _exploreErrorMessage.value = null
            
            // Clear "New" update badge as the user opened it
            repository.markFavoriteUpdateClear(mangaId)

            try {
                val details = repository.getMangaDetails(mangaId)
                _selectedManga.value = details
                if (details != null) {
                    val chaptersList = repository.getMangaChapters(mangaId)
                    _chapters.value = chaptersList
                } else {
                    _exploreErrorMessage.value = "Manga not found or unavailable."
                }
            } catch (e: Exception) {
                _exploreErrorMessage.value = "Cannot fetch details from MangaDex. Refreshing..."
            } finally {
                _chaptersLoading.value = false
            }
        }
    }

    fun loadChapter(chapter: Chapter) {
        viewModelScope.launch {
            _pagesLoading.value = true
            _activeChapter.value = chapter
            _chapterPages.value = null
            _readerCurrentPage.value = 0
            try {
                // If this is a dummy chapter, refresh/load full chapter details first
                var resolvedChapter = chapter
                if (chapter.title == "Loading..." || chapter.externalUrl.isEmpty()) {
                    val fullDetails = repository.getChapterDetails(chapter.id, chapter.mangaId)
                    if (fullDetails != null) {
                        resolvedChapter = fullDetails
                        _activeChapter.value = fullDetails
                    }
                }

                // Load parent manga info if not in VM memory (e.g. from history resume/direct deep links)
                if (_selectedManga.value == null && resolvedChapter.mangaId.isNotEmpty()) {
                    val mangaDetails = repository.getMangaDetails(resolvedChapter.mangaId)
                    if (mangaDetails != null) {
                        _selectedManga.value = mangaDetails
                    }
                }

                val pages = repository.getChapterPages(resolvedChapter.id)
                _chapterPages.value = pages
                
                // See if user has pre-existing history for this chapter and load page index to resume reading!
                val pastProgress = repository.getChapterHistory(resolvedChapter.id)
                if (pastProgress != null) {
                    _readerCurrentPage.value = pastProgress.lastReadPage
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading chapter pages", e)
            } finally {
                _pagesLoading.value = false
            }
        }
    }

    fun updateProgress(pageIndex: Int) {
        _readerCurrentPage.value = pageIndex
        val activeChap = _activeChapter.value ?: return
        val pages = _chapterPages.value ?: return
        val selectedM = _selectedManga.value ?: return
        
        viewModelScope.launch {
            repository.updateReadingProgress(
                mangaId = activeChap.mangaId,
                mangaTitle = selectedM.title,
                coverUrl = selectedM.coverUrl,
                chapterId = activeChap.id,
                chapterNumber = activeChap.chapterNumber,
                chapterTitle = activeChap.title,
                currentPageIndex = pageIndex,
                totalPages = pages.pageFiles.size
            )
        }
    }

    fun toggleFavorite(manga: Manga) {
        viewModelScope.launch {
            repository.toggleFavorite(manga)
        }
    }

    fun isFavoriteFlow(mangaId: String): Flow<Boolean> {
        return repository.isFavorite(mangaId)
    }

    /**
     * Checks favorites for updates live from sites
     */
    fun checkFavoritesForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            _isCheckingUpdates.value = true
            _updateNotification.value = if (silent) null else "Checking manga updates live..."
            try {
                val updatedCount = repository.checkFavoritesForUpdates()
                if (updatedCount > 0) {
                    _updateNotification.value = "Checked! $updatedCount favorited manga updated automatically."
                } else {
                    _updateNotification.value = if (silent) null else "Checked. Favorited titles are fully up to date."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                _updateNotification.value = if (silent) null else "Update check failing. Retrying in background..."
            } finally {
                _isCheckingUpdates.value = false
            }
        }
    }

    fun clearNotification() {
        _updateNotification.value = null
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }
}
