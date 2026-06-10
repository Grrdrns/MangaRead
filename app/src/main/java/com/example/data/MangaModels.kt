package com.example.data

data class Manga(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    val coverUrl: String,
    val author: String = "Unknown Author",
    val artist: String = "Unknown Artist",
    val tags: List<String> = emptyList(),
    val lastUpdated: String = ""
)

data class Chapter(
    val id: String,
    val mangaId: String,
    val title: String,
    val chapterNumber: String,
    val volume: String = "",
    val publishDate: String = "",
    val scanlator: String = "Unknown Scanlator",
    val externalUrl: String = ""
)

data class ChapterPages(
    val baseUrl: String,
    val hash: String,
    val pageFiles: List<String>
) {
    fun getPageUrls(): List<String> {
        return pageFiles.map { "$baseUrl/data/$hash/$it" }
    }
}
