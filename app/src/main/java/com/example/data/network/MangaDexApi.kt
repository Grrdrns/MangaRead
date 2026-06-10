package com.example.data.network

import android.util.Log
import com.example.data.Manga
import com.example.data.Chapter
import com.example.data.ChapterPages
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object MangaDexApi {
    private const val TAG = "MangaDexApi"
    private const val BASE_URL = "https://api.mangadex.org"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Common helper to make a network request and return JSON string
     */
    private fun getRequest(url: String): String {
        Log.d(TAG, "Request URL: $url")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MangaReaderAIApp/1.0 (gerardo.aranasjr@gmail.com)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e(TAG, "HTTP error: ${response.code} ($errorBody)")
                throw IOException("Server returned code ${response.code}: $errorBody")
            }
            return response.body?.string() ?: throw IOException("Empty response body")
        }
    }

    /**
     * Fetches popular manga list
     */
    fun fetchPopularManga(limit: Int = 30, offset: Int = 0, contentRatings: List<String> = listOf("safe")): List<Manga> {
        val ratingsQuery = contentRatings.joinToString("") { "&contentRating[]=$it" }
        val url = "$BASE_URL/manga?limit=$limit&offset=$offset&order[followedCount]=desc&includes[]=cover_art$ratingsQuery"
        return try {
            val jsonString = getRequest(url)
            parseMangaList(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching popular manga", e)
            emptyList()
        }
    }

    /**
     * Searches manga by title
     */
    fun searchManga(query: String, limit: Int = 30, contentRatings: List<String> = listOf("safe")): List<Manga> {
        if (query.isBlank()) return fetchPopularManga(limit, contentRatings = contentRatings)
        val ratingsQuery = contentRatings.joinToString("") { "&contentRating[]=$it" }
        val url = "$BASE_URL/manga?limit=$limit&title=${UriEncoder.encode(query)}&includes[]=cover_art$ratingsQuery"
        return try {
            val jsonString = getRequest(url)
            parseMangaList(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching manga query: $query", e)
            emptyList()
        }
    }

    /**
     * Fetches manga list filtered by a specific tag/genre
     */
    fun fetchMangaByGenre(tagId: String, limit: Int = 20, contentRatings: List<String> = listOf("safe")): List<Manga> {
        val ratingsQuery = contentRatings.joinToString("") { "&contentRating[]=$it" }
        val url = "$BASE_URL/manga?limit=$limit&includedTags[]=$tagId&order[followedCount]=desc&includes[]=cover_art$ratingsQuery"
        return try {
            val jsonString = getRequest(url)
            parseMangaList(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching manga by genre", e)
            emptyList()
        }
    }

    /**
     * Fetches details of a single manga
     */
    fun fetchMangaDetails(mangaId: String): Manga? {
        val url = "$BASE_URL/manga/$mangaId?includes[]=cover_art&includes[]=author&includes[]=artist"
        return try {
            val jsonString = getRequest(url)
            val json = JSONObject(jsonString)
            val dataObj = json.optJSONObject("data") ?: return null
            parseSingleManga(dataObj)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching manga details ID: $mangaId", e)
            null
        }
    }

    /**
     * Fetches chapters list of a single manga (English, ordered descending by chapter number)
     */
    fun fetchMangaChapters(mangaId: String, limit: Int = 100, offset: Int = 0): List<Chapter> {
        val url = "$BASE_URL/manga/$mangaId/feed?limit=$limit&offset=$offset&translatedLanguage[]=en&order[chapter]=desc&order[volume]=desc"
        return try {
            val jsonString = getRequest(url)
            val json = JSONObject(jsonString)
            val dataArray = json.optJSONArray("data") ?: return emptyList()
            val chapters = mutableListOf<Chapter>()
            for (i in 0 until dataArray.length()) {
                val chapterObj = dataArray.optJSONObject(i) ?: continue
                val chap = parseSingleChapter(chapterObj, mangaId)
                if (chap != null) {
                    chapters.add(chap)
                }
            }
            // Sort to ensure clean flow if rate limits or api return items unordered
            chapters
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching manga chapters ID: $mangaId", e)
            emptyList()
        }
    }

    /**
     * Fetches details of a single chapter (to sync properties dynamically in details/reader views)
     */
    fun fetchChapterDetails(chapterId: String, mangaId: String = ""): Chapter? {
        val url = "$BASE_URL/chapter/$chapterId"
        return try {
            val jsonString = getRequest(url)
            val json = JSONObject(jsonString)
            val dataObj = json.optJSONObject("data") ?: return null
            
            var extractedMangaId = mangaId
            if (extractedMangaId.isEmpty()) {
                val relationships = dataObj.optJSONArray("relationships")
                if (relationships != null) {
                    for (j in 0 until relationships.length()) {
                        val rel = relationships.optJSONObject(j) ?: continue
                        if (rel.optString("type") == "manga") {
                            extractedMangaId = rel.optString("id", "")
                            break
                        }
                    }
                }
            }
            parseSingleChapter(dataObj, extractedMangaId)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching chapter details ID: $chapterId", e)
            null
        }
    }

    /**
     * Fetches pages for a specific chapter
     */
    fun fetchChapterPages(chapterId: String): ChapterPages? {
        val url = "$BASE_URL/at-home/server/$chapterId"
        return try {
            val jsonString = getRequest(url)
            val json = JSONObject(jsonString)
            val baseUrl = json.optString("baseUrl", "https://uploads.mangadex.org")
            val chapterObj = json.optJSONObject("chapter") ?: return null
            val hash = chapterObj.optString("hash", "")
            val dataArray = chapterObj.optJSONArray("data") ?: return null
            
            val pages = mutableListOf<String>()
            for (i in 0 until dataArray.length()) {
                pages.add(dataArray.optString(i))
            }
            ChapterPages(baseUrl, hash, pages)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching chapter pages ID: $chapterId", e)
            null
        }
    }

    /**
     * Parses a list of manga from a multi-item search/explore response
     */
    private fun parseMangaList(jsonString: String): List<Manga> {
        val json = JSONObject(jsonString)
        val dataArray = json.optJSONArray("data") ?: return emptyList()
        val list = mutableListOf<Manga>()
        for (i in 0 until dataArray.length()) {
            val mangaObj = dataArray.optJSONObject(i) ?: continue
            val manga = parseSingleManga(mangaObj)
            if (manga != null) {
                list.add(manga)
            }
        }
        return list
    }

    private fun parseSingleManga(obj: JSONObject): Manga? {
        val id = obj.optString("id", "")
        if (id.isEmpty()) return null
        
        val attributes = obj.optJSONObject("attributes") ?: return null
        val titleObj = attributes.optJSONObject("title")
        val title = when {
            titleObj == null -> "Unknown Manga"
            titleObj.has("en") -> titleObj.optString("en")
            titleObj.has("ja") -> titleObj.optString("ja")
            titleObj.has("ja-ro") -> titleObj.optString("ja-ro")
            titleObj.keys().hasNext() -> titleObj.optString(titleObj.keys().next())
            else -> "Unknown Manga"
        }

        val descObj = attributes.optJSONObject("description")
        var description = when {
            descObj == null -> "No description available."
            descObj.has("en") -> descObj.optString("en")
            descObj.keys().hasNext() -> descObj.optString(descObj.keys().next())
            else -> "No description available."
        }
        // Clean up markdown / html elements out of description if any
        description = description.replace(Regex("\\[\\/?[^\\]]+\\]"), "")

        val status = attributes.optString("status", "unknown").replaceFirstChar { it.uppercase() }
        val lastUpdated = attributes.optString("updatedAt", "")

        // Parse relationships to extract cover_art, author, artist names
        val relationships = obj.optJSONArray("relationships")
        var coverFileName = ""
        var authorName = "Unknown Author"
        var artistName = "Unknown Artist"

        if (relationships != null) {
            for (j in 0 until relationships.length()) {
                val rel = relationships.optJSONObject(j) ?: continue
                val type = rel.optString("type")
                if (type == "cover_art") {
                    val relAttr = rel.optJSONObject("attributes")
                    coverFileName = relAttr?.optString("fileName") ?: ""
                } else if (type == "author") {
                    val relAttr = rel.optJSONObject("attributes")
                    authorName = relAttr?.optString("name") ?: authorName
                } else if (type == "artist") {
                    val relAttr = rel.optJSONObject("attributes")
                    artistName = relAttr?.optString("name") ?: artistName
                }
            }
        }

        // Standard MangaDex cover URL format:
        // https://uploads.mangadex.org/covers/{manga-id}/{cover-filename}.256.jpg (using 256px resolution for extremely fast caching and less bandwidth!)
        val coverUrl = if (coverFileName.isNotEmpty()) {
            "https://uploads.mangadex.org/covers/$id/$coverFileName.256.jpg"
        } else {
            ""
        }

        // Fetch genres tags
        val tagsArray = attributes.optJSONArray("tags")
        val tags = mutableListOf<String>()
        if (tagsArray != null) {
            for (k in 0 until tagsArray.length()) {
                val tagObj = tagsArray.optJSONObject(k) ?: continue
                val tagAttr = tagObj.optJSONObject("attributes") ?: continue
                val nameObj = tagAttr.optJSONObject("name")
                val tagName = nameObj?.optString("en") ?: ""
                if (tagName.isNotEmpty()) {
                    tags.add(tagName)
                }
            }
        }

        return Manga(
            id = id,
            title = title,
            description = description,
            status = status,
            coverUrl = coverUrl,
            author = authorName,
            artist = artistName,
            tags = tags,
            lastUpdated = lastUpdated
        )
    }

    private fun parseSingleChapter(obj: JSONObject, mangaId: String): Chapter? {
        val id = obj.optString("id", "")
        if (id.isEmpty()) return null

        val attributes = obj.optJSONObject("attributes") ?: return null
        val chapterNum = attributes.optString("chapter", "0")
        val volume = attributes.optString("volume", "")
        val chapterTitle = attributes.optString("title", "Chapter $chapterNum")
        val publishDate = attributes.optString("publishAt", "")
        val externalUrl = attributes.optString("externalUrl", "")

        // Gather group scanlator scan info if present
        var scanGroup = "Unknown Scanlator"
        val relationships = obj.optJSONArray("relationships")
        if (relationships != null) {
            for (j in 0 until relationships.length()) {
                val rel = relationships.optJSONObject(j) ?: continue
                if (rel.optString("type") == "scanlation_group") {
                    val relAttr = rel.optJSONObject("attributes")
                    scanGroup = relAttr?.optString("name") ?: scanGroup
                }
            }
        }

        return Chapter(
            id = id,
            mangaId = mangaId,
            title = chapterTitle,
            chapterNumber = chapterNum,
            volume = volume,
            publishDate = publishDate,
            scanlator = scanGroup,
            externalUrl = externalUrl
        )
    }
}

/**
 * Super lightweight custom modern URI encoder for safety & dependencies
 */
object UriEncoder {
    fun encode(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append(c)
            } else {
                sb.append(String.format("%%%02X", c.code))
            }
        }
        return sb.toString()
    }
}
