package com.example.data.database

import androidx.room.*

@Entity(tableName = "favorite_manga")
data class FavoriteEntity(
    @PrimaryKey val mangaId: String,
    val title: String,
    val coverUrl: String,
    val description: String,
    val status: String,
    val latestChapterNum: String = "",
    val hasUpdates: Boolean = false,
    val lastCheckedTime: Long = System.currentTimeMillis(),
    val addedTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "read_history")
data class ReadHistoryEntity(
    @PrimaryKey val chapterId: String,
    val mangaId: String,
    val mangaTitle: String,
    val coverUrl: String,
    val chapterNumber: String,
    val chapterTitle: String,
    val lastReadPage: Int = 0,
    val totalPages: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
