package com.flex.elefin.jellyfin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import okhttp3.Headers
import androidx.compose.runtime.Stable

@Stable
@Serializable
data class JellyfinItem(
    val Id: String,
    val Name: String,
    val Overview: String? = null,
    val PremiereDate: String? = null, // ISO date string for premiere date
    val DateCreated: String? = null, // ISO date string for date created/added
    val DateScanned: String? = null, // ISO date string for date scanned into library
    val ProductionYear: Int? = null,
    val ImageTags: Map<String, String>? = null,
    val SeriesName: String? = null,
    val SeriesId: String? = null, // Parent series ID for episodes
    val Type: String? = null,
    val UserData: UserData? = null,
    val MediaSources: List<MediaSource>? = null,
    val RunTimeTicks: Long? = null,
    val OfficialRating: String? = null,
    val CommunityRating: Float? = null,
    val Genres: List<String>? = null, // Genres is an array of strings in Jellyfin API
    val ProviderIds: Map<String, String>? = null,
    val CriticRating: Float? = null, // Critic rating if available
    val People: List<Person>? = null, // Cast and crew members
    val IndexNumber: Int? = null, // Episode number for episodes
    val ParentIndexNumber: Int? = null, // Season number for episodes
    val ChildCount: Int? = null, // Number of child items (e.g., episodes for Series)
    val NextEpisodeId: String? = null // ID of the next episode for autoplay
)

@Stable
@Serializable
data class Person(
    val Id: String? = null,
    val Name: String,
    val Type: String? = null, // "Actor", "Director", "Writer", etc.
    val Role: String? = null, // Character name played
    val PrimaryImageTag: String? = null // Image tag for person's photo
)

// Full person details from /Users/{userId}/Items/{personId}
@Stable
@Serializable
data class PersonDetails(
    val Id: String,
    val Name: String,
    val Overview: String? = null, // Biography
    val PremiereDate: String? = null, // Birth date (Jellyfin uses PremiereDate for persons)
    val EndDate: String? = null, // Death date
    val ProductionLocations: List<String>? = null, // Place of birth
    val ImageTags: Map<String, String>? = null,
    val BackdropImageTags: List<String>? = null,
    val Type: String? = null,
    // Alternative field names that Jellyfin might use
    val BirthDate: String? = null,
    val DeathDate: String? = null
) {
    // Helper to get birth date from either field
    val birthDateValue: String? get() = PremiereDate ?: BirthDate
    // Helper to get death date from either field  
    val deathDateValue: String? get() = EndDate ?: DeathDate
}

@Stable
@Serializable
data class JellyfinPlaybackInfo(
    val MediaSources: List<MediaSource>? = null
)

// Media Segments for Skip Intro / Skip Credits (Jellyfin 10.10+)
@Stable
@Serializable
data class MediaSegment(
    val Id: String? = null,
    val ItemId: String? = null,
    val Type: String? = null, // "Intro", "Outro", "Recap", "Preview", "Commercial"
    val StartTicks: Long? = null, // Start time in ticks (1 tick = 100 nanoseconds)
    val EndTicks: Long? = null // End time in ticks
) {
    // Convert ticks to milliseconds (1 tick = 100 nanoseconds = 0.0001 ms)
    val startMs: Long get() = (StartTicks ?: 0) / 10000
    val endMs: Long get() = (EndTicks ?: 0) / 10000
}

@Stable
@Serializable
data class MediaSegmentsResponse(
    val Items: List<MediaSegment>? = null
)

// Simplified skip markers for the video player
data class SkipMarkers(
    val introStartMs: Long? = null,
    val introEndMs: Long? = null,
    val creditsStartMs: Long? = null
)

@Stable
@Serializable
data class MediaSource(
    val Id: String? = null,
    val Protocol: String? = null,
    val Container: String? = null,
    val MediaStreams: List<MediaStream>? = null
)

@Stable
@Serializable
data class MediaStream(
    val Index: Int? = null,
    val Type: String? = null, // "Video", "Audio", "Subtitle"
    val Codec: String? = null,
    val Language: String? = null,
    val DisplayLanguage: String? = null, // Human-readable language name (e.g., "Turkish")
    val DisplayTitle: String? = null,
    val IsExternal: Boolean? = null,
    val SupportsExternalStream: Boolean? = null, // Whether this subtitle can be streamed via /Subtitles/{index}/Stream
    val DeliveryUrl: String? = null,
    val DeliveryMethod: String? = null, // "External", "Encode", "Embed", "Hls"
    val Path: String? = null, // File system path for external subtitles
    val IsDefault: Boolean? = null,
    val IsForced: Boolean? = null,
    val IsTextSubtitleStream: Boolean? = null, // True for text (SRT, VTT, ASS), false for bitmap (PGS, VOBSUB)
    val CodecTag: String? = null, // Codec tag for advanced format detection
    val IsHearingImpaired: Boolean? = null, // Closed captions / SDH subtitles
    val Title: String? = null, // Subtitle track title
    val Width: Int? = null, // Video/subtitle width
    val Height: Int? = null, // Video/subtitle height
    val ChannelLayout: String? = null // Audio channel layout (e.g., "5.1", "7.1", "stereo")
)

@Stable
@Serializable
data class UserData(
    val PlayedPercentage: Double? = null,
    @SerialName("PlaybackPositionTicks")
    val PositionTicks: Long? = null,
    val Played: Boolean? = null,
    val UnplayedItemCount: Int? = null // Number of unwatched episodes for Series
)

@Serializable
data class QuickConnectInitiateResponse(
    val Secret: String,
    val Code: String
)

@Serializable
data class QuickConnectStateResponse(
    val Authenticated: Boolean,
    val Code: String? = null,
    val Secret: String? = null,
    val Authentication: QuickConnectAuthentication? = null
)

@Serializable
data class QuickConnectAuthentication(
    val AccessToken: String,
    val User: QuickConnectUser
)

@Serializable
data class QuickConnectUser(
    val Id: String,
    val Name: String
)

@Serializable
data class QuickConnectAuthenticateRequest(
    val Secret: String
)

@Serializable
data class QuickConnectAuthenticationResponse(
    val AccessToken: String,
    val User: QuickConnectUser
)

@Serializable
data class ItemsResponse(
    val Items: List<JellyfinItem> = emptyList(),
    val TotalRecordCount: Int = 0
)

@Serializable
data class JellyfinLibrary(
    val Id: String,
    val Name: String,
    val Type: String? = null,
    val ImageTags: Map<String, String>? = null
)

class JellyfinApiService(
    private val baseUrl: String,
    private val accessToken: String,
    private val userId: String,
    private val config: JellyfinConfig? = null
) {
    // Expose baseUrl, accessToken, userId for external use (e.g., MPV URL selector)
    val serverBaseUrl: String get() = baseUrl
    val apiKey: String get() = accessToken
    fun getUserId(): String = userId
    fun getJellyfinConfig(): JellyfinConfig? = config
    
    // In-memory cache for episodes (keyed by seasonId)
    private val episodeCache = mutableMapOf<String, Pair<Long, List<JellyfinItem>>>()
    private val seasonCache = mutableMapOf<String, Pair<Long, List<JellyfinItem>>>()
    private val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes cache
    
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        engine {
            connectTimeout = 10_000
            socketTimeout = 15_000
        }
    }

    suspend fun getContinueWatching(): List<JellyfinItem> {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items/Resume").apply {
                parameters.append("Fields", "ImageTags,UserData,SeriesName,SeriesId") // Request ImageTags to get Thumb images
                parameters.append("SortBy", "DatePlayed") // Sort by when the item was last played
                parameters.append("SortOrder", "Descending") // Most recently played first
            }.buildString()
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            response.Items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getNextUp(limit: Int = 50): List<JellyfinItem> {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Shows/NextUp").apply {
                parameters.append("UserId", userId)
                parameters.append("Limit", limit.toString())
                parameters.append("Fields", "ImageTags,UserData,SeriesName,SeriesId") // Request ImageTags to get Thumb images
                parameters.append("EnableResumable", "false") // Next Up shows episodes you haven't started yet
            }.buildString()
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            response.Items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getRecentlyAddedMovies(limit: Int = 20): List<JellyfinItem> {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("IncludeItemTypes", "Movie")
                parameters.append("SortBy", "DateCreated")
                parameters.append("SortOrder", "Descending")
                parameters.append("Limit", limit.toString())
                parameters.append("Recursive", "true")
                parameters.append("Fields", "ImageTags") // Request ImageTags for image loading
            }.buildString()
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            response.Items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getRecentlyReleasedMovies(limit: Int = 20): List<JellyfinItem> {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("IncludeItemTypes", "Movie")
                parameters.append("SortBy", "PremiereDate")
                parameters.append("SortOrder", "Descending")
                parameters.append("Limit", limit.toString())
                parameters.append("Recursive", "true")
                parameters.append("Fields", "ImageTags") // Request ImageTags for image loading
            }.buildString()
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            response.Items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun getRecentlyAddedMoviesFromLibrary(libraryId: String, limit: Int = 20): List<JellyfinItem> {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("IncludeItemTypes", "Movie")
                parameters.append("ParentId", libraryId)
                parameters.append("SortBy", "DateCreated")
                parameters.append("SortOrder", "Descending")
                parameters.append("Limit", limit.toString())
                parameters.append("Recursive", "true")
                parameters.append("Fields", "ImageTags,DateCreated") // Request ImageTags and DateCreated for image loading and sorting
            }.buildString()
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            response.Items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun getRecentlyReleasedMoviesFromLibrary(libraryId: String, limit: Int = 20): List<JellyfinItem> {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("IncludeItemTypes", "Movie")
                parameters.append("ParentId", libraryId)
                parameters.append("SortBy", "PremiereDate")
                parameters.append("SortOrder", "Descending")
                parameters.append("Limit", limit.toString())
                parameters.append("Recursive", "true")
                parameters.append("Fields", "ImageTags,PremiereDate") // Request ImageTags and PremiereDate for image loading and sorting
            }.buildString()
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            response.Items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getRecentlyAddedShows(limit: Int = 20): List<JellyfinItem> {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("IncludeItemTypes", "Series")
                parameters.append("SortBy", "DateCreated")
                parameters.append("SortOrder", "Descending")
                parameters.append("Limit", limit.toString())
                parameters.append("Recursive", "true")
                parameters.append("Fields", "ImageTags,ChildCount") // Request ImageTags and ChildCount
            }.buildString()
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            // Return all items - filtering based on settings will be done in UI layer
            response.Items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getRecentlyAddedEpisodes(limit: Int = 20): List<JellyfinItem> {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("IncludeItemTypes", "Episode")
                parameters.append("SortBy", "DateCreated")
                parameters.append("SortOrder", "Descending")
                parameters.append("Limit", limit.toString())
                parameters.append("Recursive", "true")
                // Request SeriesId, SeriesName, IndexNumber, ParentIndexNumber, and ImageTags fields for episodes
                parameters.append("Fields", "SeriesId,SeriesName,IndexNumber,ParentIndexNumber,ImageTags")
            }.buildString()
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            response.Items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun getRecentlyAddedShowsFromLibrary(libraryId: String, limit: Int = 20): List<JellyfinItem> {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("ParentId", libraryId)
                parameters.append("IncludeItemTypes", "Series")
                parameters.append("SortBy", "DateCreated")
                parameters.append("SortOrder", "Descending")
                parameters.append("Limit", limit.toString())
                parameters.append("Recursive", "true")
                parameters.append("Fields", "ImageTags,ChildCount")
            }.buildString()
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            response.Items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun getRecentlyAddedEpisodesFromLibrary(libraryId: String, limit: Int = 20): List<JellyfinItem> {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("ParentId", libraryId)
                parameters.append("IncludeItemTypes", "Episode")
                parameters.append("SortBy", "DateCreated")
                parameters.append("SortOrder", "Descending")
                parameters.append("Limit", limit.toString())
                parameters.append("Recursive", "true")
                parameters.append("Fields", "SeriesId,SeriesName,IndexNumber,ParentIndexNumber,ImageTags")
            }.buildString()
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            response.Items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getImageUrl(itemId: String, imageType: String = "Primary", imageTag: String? = null, maxWidth: Int? = null, maxHeight: Int? = null, quality: Int? = null): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        // Default to highest resolution for detail views, but allow smaller sizes for thumbnails
        val defaultMaxWidth = maxWidth ?: 7680
        val defaultMaxHeight = maxHeight ?: 4320
        val defaultQuality = quality ?: 100
        val urlBuilder = URLBuilder().takeFrom("${base}Items/$itemId/Images/$imageType").apply {
            parameters.append("maxWidth", defaultMaxWidth.toString())
            parameters.append("maxHeight", defaultMaxHeight.toString())
            parameters.append("quality", defaultQuality.toString())
            // Add image tag if provided (for person images)
            imageTag?.let { tag ->
                parameters.append("tag", tag)
            }
        }
        return urlBuilder.buildString()
    }
    
    fun getImageRequestHeaders(): Headers {
        return Headers.Builder()
            .add("Authorization", "MediaBrowser Token=\"$accessToken\"")
            .add("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            .build()
    }

    suspend fun getItemDetails(itemId: String): JellyfinItem? {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            // Request full item details including MediaSources for video playback and UserData for resume functionality
            val url = URLBuilder().takeFrom("${base}Items/$itemId").apply {
                parameters.append("UserId", userId)
                // Request UserData fields to get PositionTicks for resume functionality, and IndexNumber/ParentIndexNumber for episodes
                parameters.append("Fields", "MediaSources,Genres,Overview,People,ProviderIds,UserData,ImageTags,IndexNumber,ParentIndexNumber,NextEpisodeId")
            }.buildString()
            android.util.Log.d("JellyfinAPI", "Fetching item details from: $url")
            
            val response = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }
            val item: JellyfinItem = response.body()
            android.util.Log.d("JellyfinAPI", "Item details fetched: ${item.Name}, Type: ${item.Type}, MediaSources: ${item.MediaSources?.size ?: 0}")
            android.util.Log.d("JellyfinAPI", "Genres: ${item.Genres}, CommunityRating: ${item.CommunityRating}, CriticRating: ${item.CriticRating}")
            android.util.Log.d("JellyfinAPI", "ProviderIds: ${item.ProviderIds}")
            android.util.Log.d("JellyfinAPI", "ProductionYear: ${item.ProductionYear}, OfficialRating: ${item.OfficialRating}, RunTimeTicks: ${item.RunTimeTicks}")
            // Log UserData for debugging resume functionality
            android.util.Log.d("JellyfinAPI", "UserData: PlayedPercentage=${item.UserData?.PlayedPercentage}, PositionTicks=${item.UserData?.PositionTicks}")
            if (item.UserData == null) {
                android.util.Log.w("JellyfinAPI", "WARNING: UserData is null for item ${item.Id}. Resume functionality may not work.")
            } else if (item.UserData?.PositionTicks == null || item.UserData?.PositionTicks == 0L) {
                android.util.Log.d("JellyfinAPI", "Item ${item.Id} has no resume position (PositionTicks is null or 0)")
            } else {
                val seconds = (item.UserData?.PositionTicks ?: 0L) / 10_000_000L
                android.util.Log.d("JellyfinAPI", "Item ${item.Id} is resumable at position ${item.UserData?.PositionTicks} ticks (${seconds} seconds)")
            }
            item
        } catch (e: Exception) {
            android.util.Log.e("JellyfinAPI", "Error fetching item details", e)
            e.printStackTrace()
            null
        }
    }

    // Get person details (biography, birthdate, etc.)
    // Uses /Users/{userId}/Items/{personId} endpoint which returns full item details
    suspend fun getPersonDetails(personId: String): PersonDetails? {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            // Use the Items endpoint with userId for full details including Overview
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items/$personId").buildString()
            android.util.Log.d("JellyfinAPI", "Fetching person details from: $url")
            
            val response = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }
            val person: PersonDetails = response.body()
            android.util.Log.d("JellyfinAPI", "Person details fetched: ${person.Name}, Overview length: ${person.Overview?.length ?: 0}, Type: ${person.Type}")
            android.util.Log.d("JellyfinAPI", "Person birth: ${person.birthDateValue}, death: ${person.deathDateValue}, locations: ${person.ProductionLocations}")
            person
        } catch (e: Exception) {
            android.util.Log.e("JellyfinAPI", "Error fetching person details", e)
            e.printStackTrace()
            null
        }
    }

    // Get all items (movies, series) that a person appears in (filmography)
    suspend fun getPersonFilmography(personId: String, limit: Int = 50): List<JellyfinItem> {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Items").apply {
                parameters.append("UserId", userId)
                parameters.append("PersonIds", personId)
                parameters.append("Recursive", "true")
                parameters.append("IncludeItemTypes", "Movie,Series")
                parameters.append("SortBy", "PremiereDate,ProductionYear,SortName")
                parameters.append("SortOrder", "Descending")
                parameters.append("Fields", "PrimaryImageAspectRatio,MediaSourceCount,Overview,Genres,ProductionYear")
                parameters.append("Limit", limit.toString())
            }.buildString()
            android.util.Log.d("JellyfinAPI", "Fetching person filmography from: $url")
            
            val response = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }
            val itemsResponse: ItemsResponse = response.body()
            android.util.Log.d("JellyfinAPI", "Person filmography fetched: ${itemsResponse.Items.size} items")
            itemsResponse.Items
        } catch (e: Exception) {
            android.util.Log.e("JellyfinAPI", "Error fetching person filmography", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // Get person image URL
    fun getPersonImageUrl(personId: String, imageType: String = "Primary", tag: String? = null, maxWidth: Int? = null, maxHeight: Int? = null): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return URLBuilder().takeFrom("${base}Items/$personId/Images/$imageType").apply {
            tag?.let { parameters.append("tag", it) }
            maxWidth?.let { parameters.append("maxWidth", it.toString()) }
            maxHeight?.let { parameters.append("maxHeight", it.toString()) }
        }.buildString()
    }

    fun getVideoPlaybackUrl(
        itemId: String,
        mediaSourceId: String? = null,
        subtitleStreamIndex: Int? = null,
        preserveQuality: Boolean = false, // Set to true for HDR videos to preserve quality
        transcodeAudio: Boolean = false, // Set to true to transcode audio (for unsupported codecs like TrueHD)
        audioCodec: String? = null // Target audio codec for transcoding (e.g., "ac3", "aac")
    ): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        // Jellyfin video playback URL format: /Videos/{itemId}/stream
        // Use MediaSourceId if provided, otherwise use itemId
        val sourceId = mediaSourceId ?: itemId
        // IMPORTANT: MPV/FFmpeg requires correct parameter casing
        // - mediaSourceId (camelCase, not MediaSourceId)
        // - static (lowercase, not Static)
        // Order: static first, then mediaSourceId, then api_key (conventional order)
        val url = URLBuilder().takeFrom("${base}Videos/$itemId/stream").apply {
            subtitleStreamIndex?.let { 
                // Add subtitle stream index if provided
                parameters.append("SubtitleStreamIndex", it.toString())
                // Don't set SubtitleDeliveryMethod - let Jellyfin handle it
                // We'll add the subtitle URL separately to MediaItem for better compatibility
                // Set a very high max bitrate to preserve quality if transcoding is needed
                parameters.append("maxStreamingBitrate", "1000000000") // 1 Gbps - effectively no limit
                // Copy timestamps to avoid re-encoding when possible
                parameters.append("CopyTimestamps", "true")
                // Don't set static=true when subtitles are needed (allows transcoding)
            } ?: run {
                // For HDR/high-quality videos, try direct play first if audio codec is supported
                // Only use remuxing/transcoding when audio needs to be transcoded
                if (preserveQuality && transcodeAudio) {
                    // Audio needs transcoding - use HLS for progressive playback (avoids long initial buffering)
                    // HLS allows playback to start while transcoding continues
                    val targetAudioCodec = audioCodec?.lowercase() ?: "aac"
                    val hlsUrl = URLBuilder().takeFrom("${base}Videos/$itemId/master.m3u8").apply {
                        // Set maximum resolution (8K support for future-proofing)
                        parameters.append("MaxWidth", "7680")
                        parameters.append("MaxHeight", "4320")
                        // Set very high bitrate to preserve quality (1 Gbps - effectively no limit)
                        parameters.append("maxStreamingBitrate", "1000000000")
                        // Try to preserve video codec when possible (remux instead of transcode)
                        parameters.append("VideoCodec", "copy")
                        // Transcode to specified audio codec (AC3 for universal compatibility, or AAC)
                        parameters.append("AudioCodec", targetAudioCodec)
                        if (targetAudioCodec == "ac3") {
                            // AC3 supports up to 5.1 channels, use 640 kbps for high quality
                            parameters.append("AudioBitrate", "640000") // 640 kbps
                        } else {
                            // AAC supports higher bitrates and more channels
                            parameters.append("AudioBitrate", "640000") // 640 kbps for high quality audio
                        }
                        // Copy timestamps to avoid re-encoding
                        parameters.append("CopyTimestamps", "true")
                        parameters.append("mediaSourceId", sourceId)
                        parameters.append("api_key", accessToken)
                    }.buildString()
                    android.util.Log.d("JellyfinAPI", "Using HLS for HDR video with audio transcoding to $targetAudioCodec (progressive playback): $hlsUrl")
                    return hlsUrl
                } else if (transcodeAudio && audioCodec != null) {
                    // Non-HDR but audio transcoding requested (e.g., AAC to AC3)
                    // Use HLS for progressive playback
                    val targetAudioCodec = audioCodec.lowercase()
                    val hlsUrl = URLBuilder().takeFrom("${base}Videos/$itemId/master.m3u8").apply {
                        parameters.append("VideoCodec", "copy")
                        parameters.append("AudioCodec", targetAudioCodec)
                        if (targetAudioCodec == "ac3") {
                            parameters.append("AudioBitrate", "640000") // 640 kbps for AC3 (5.1 max)
                        } else {
                            parameters.append("AudioBitrate", "640000")
                        }
                        parameters.append("CopyTimestamps", "true")
                        parameters.append("maxStreamingBitrate", "1000000000")
                        parameters.append("mediaSourceId", sourceId)
                        parameters.append("api_key", accessToken)
                    }.buildString()
                    android.util.Log.d("JellyfinAPI", "Using HLS for audio transcoding to $targetAudioCodec: $hlsUrl")
                    return hlsUrl
                } else if (preserveQuality && !transcodeAudio) {
                    // HDR video with supported audio - try direct play first for instant startup
                    // Use static=true for direct play (fastest startup, no remuxing delay)
                    parameters.append("static", "true")
                    android.util.Log.d("JellyfinAPI", "Using direct play for HDR video with supported audio (instant startup)")
                } else {
                    // Set static=true for direct play (no transcoding) when no subtitles and not HDR
                    // Use lowercase "static" for MPV/FFmpeg compatibility
                    parameters.append("static", "true")
                }
            }
            // Add mediaSourceId with correct casing (camelCase, not MediaSourceId)
            parameters.append("mediaSourceId", sourceId)
            // Add api_key last
            parameters.append("api_key", accessToken)
        }.buildString()
        android.util.Log.d("JellyfinAPI", "Generated video playback URL: $url")
        return url
    }
    
    /**
     * Get video playback URL for MPV player.
     * 
     * MPV playback strategy (in order of preference):
     * 1. Direct Play: /original endpoint (bypasses transcoder completely)
     * 2. Direct Stream: /stream with copy codecs (remux only, no transcode)
     * 3. MP4 Transcode: /stream.mp4 (more stable than HLS transcoder)
     * 4. HLS: /master.m3u8 (last resort, can crash Jellyfin's transcoder)
     * 
     * This follows best practices to avoid Jellyfin transcoder crashes:
     * - Direct play/stream is preferred over transcoding
     * - MP4 transcoding is more stable than HLS transcoding
     * - HLS is only used when absolutely necessary
     * 
     * @param itemId The item ID
     * @param mediaSourceId Optional media source ID
     * @param subtitleStreamIndex Optional subtitle stream index
     * @param preferredMethod Optional preferred playback method (null = auto-detect)
     * @return URL for MPV playback using the best available method
     */
    /**
     * Build correct Jellyfin subtitle URL based on subtitle type
     * Production-safe URL builder matching official Jellyfin clients
     * 
     * @param itemId The Jellyfin item ID
     * @param mediaSourceId The media source ID
     * @param streamIndex The REAL Jellyfin stream index (NOT array position!)
     * @param isExternal Whether this is an external subtitle file on disk
     * @param path The filesystem path (for external subtitles)
     * @param codec The subtitle codec (e.g., "subrip", "ass", "pgs")
     * @return The correct subtitle URL for this subtitle type
     */
    fun buildJellyfinSubtitleUrl(
        itemId: String,
        mediaSourceId: String?,
        streamIndex: Int,
        isExternal: Boolean,
        codec: String?,
        path: String? = null
    ): String {
        val server = if (baseUrl.endsWith("/")) baseUrl.removeSuffix("/") else baseUrl
        
        // Determine file extension from codec or path
        val extension = when (codec?.lowercase()) {
            "srt", "subrip" -> "srt"
            "vtt", "webvtt" -> "vtt"
            "ass", "ssa", "substationalpha" -> "ass"
            "ttml" -> "ttml"
            "pgs", "hdmv_pgs_subtitle" -> "sup"
            else -> {
                // Fallback: extract from file path if available
                path?.substringAfterLast('.')?.lowercase() ?: "srt"
            }
        }
        
        // ✅ CORRECT URL FORMAT (CONFIRMED WORKING)
        // Example: /Videos/{itemId}/{mediaSourceId}/Subtitles/{index}/Stream.srt?api_key=xxx
        // Works for: external sidecar .srt files, embedded subtitles, forced subtitles
        val url = "$server/Videos/$itemId/$mediaSourceId/Subtitles/$streamIndex/Stream.$extension?api_key=$accessToken"
        
        android.util.Log.d("JellyfinAPI", "✅ Subtitle URL (isExternal=$isExternal, codec=$codec, ext=$extension): $url")
        return url
    }
    
    /**
     * Converts a relative DeliveryUrl from Jellyfin into an absolute URL
     */
    fun resolveDeliveryUrl(deliveryUrl: String): String {
        return if (deliveryUrl.startsWith("http")) {
            // Already absolute
            deliveryUrl
        } else {
            // Relative URL, prepend base
            val base = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
            "$base$deliveryUrl"
        }
    }
    
    /**
     * Get PlaybackInfo to retrieve correct subtitle DeliveryUrl mapping
     * This is how official Jellyfin clients resolve subtitle stream indices
     * Note: Simplified version - just re-fetch the item details which should have updated DeliveryUrl
     */
    suspend fun getPlaybackInfo(
        itemId: String,
        mediaSourceId: String,
        subtitleStreamIndex: Int? = null
    ): JellyfinPlaybackInfo? {
        return try {
            android.util.Log.d("JellyfinAPI", "Fetching PlaybackInfo (re-requesting item details for updated DeliveryUrl)...")
            android.util.Log.d("JellyfinAPI", "  ItemId: $itemId")
            android.util.Log.d("JellyfinAPI", "  MediaSourceId: $mediaSourceId")
            android.util.Log.d("JellyfinAPI", "  SubtitleStreamIndex: $subtitleStreamIndex")
            
            // Re-fetch item details which should contain updated MediaStreams with DeliveryUrl
            val itemDetails = getItemDetails(itemId)
            
            if (itemDetails?.MediaSources != null) {
                android.util.Log.d("JellyfinAPI", "✅ PlaybackInfo (ItemDetails) received with ${itemDetails.MediaSources.size} MediaSource(s)")
                JellyfinPlaybackInfo(MediaSources = itemDetails.MediaSources)
            } else {
                android.util.Log.w("JellyfinAPI", "⚠️ No MediaSources in ItemDetails")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("JellyfinAPI", "❌ Failed to get PlaybackInfo: ${e.message}", e)
            null
        }
    }

    fun getVideoRequestHeaders(): Map<String, String> {
        // Get DeviceId from config (should be stored during login)
        // If not available, use fallback (but it should be stored)
        val deviceId = config?.deviceId?.takeIf { it.isNotEmpty() } 
            ?: "56be65b97eb43eca" // Fallback DeviceId - should match what's used in authentication
        
        // Build X-Emby-Authorization header with Token and DeviceId
        // Format: MediaBrowser Client="...", Device="...", DeviceId="...", Version="...", Token="..."
        // CRITICAL: Token MUST be included in X-Emby-Authorization header for MPV/FFmpeg
        val embyAuthHeader = "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"$deviceId\", Version=\"1.1.5\", Token=\"$accessToken\""
        
        return mapOf(
            "Authorization" to "MediaBrowser Token=\"$accessToken\"",
            "X-Emby-Authorization" to embyAuthHeader
        )
    }
    
    /**
     * Get Media Segments for an item (Skip Intro / Skip Credits)
     * Requires Jellyfin 10.10+ with Intro Skipper plugin
     * Returns skip markers for intro and credits if available
     */
    suspend fun getMediaSegments(itemId: String): SkipMarkers {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}MediaSegments/$itemId").apply {
                parameters.append("IncludeSegmentTypes", "Intro,Outro")
            }.buildString()
            
            android.util.Log.d("JellyfinAPI", "Fetching MediaSegments for item: $itemId")
            
            val response: MediaSegmentsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            
            val segments = response.Items ?: emptyList()
            android.util.Log.d("JellyfinAPI", "Found ${segments.size} media segments")
            
            // Extract intro and credits (outro) segments
            val introSegment = segments.find { it.Type?.equals("Intro", ignoreCase = true) == true }
            val outroSegment = segments.find { it.Type?.equals("Outro", ignoreCase = true) == true }
            
            val markers = SkipMarkers(
                introStartMs = introSegment?.startMs,
                introEndMs = introSegment?.endMs,
                creditsStartMs = outroSegment?.startMs
            )
            
            android.util.Log.d("JellyfinAPI", "Skip markers: intro=${markers.introStartMs}-${markers.introEndMs}ms, credits=${markers.creditsStartMs}ms")
            markers
        } catch (e: Exception) {
            android.util.Log.d("JellyfinAPI", "MediaSegments not available (server may not support it): ${e.message}")
            // Return empty markers if not supported
            SkipMarkers()
        }
    }
    
    /**
     * Build correct Jellyfin subtitle URL for streaming subtitles.
     * Format: /Videos/{itemId}/{mediaSourceId}/Subtitles/{index}/Stream?api_key=xxx
     */
    fun buildSubtitleUrl(itemId: String, mediaSourceId: String, index: Int): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return "${base}Videos/$itemId/$mediaSourceId/Subtitles/$index/Stream?api_key=$accessToken"
    }

    suspend fun getLibraries(): List<JellyfinLibrary> {
        return try {
            val url = if (baseUrl.endsWith("/")) {
                "${baseUrl}Users/$userId/Views"
            } else {
                "$baseUrl/Users/$userId/Views"
            }
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            
            // Convert JellyfinItems to JellyfinLibraries
            response.Items.map { item ->
                JellyfinLibrary(
                    Id = item.Id,
                    Name = item.Name,
                    Type = item.Type,
                    ImageTags = item.ImageTags
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getLibraryItems(libraryId: String, limit: Int = 100, startIndex: Int = 0): ItemsResponse {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("ParentId", libraryId)
                parameters.append("Recursive", "false")
                parameters.append("IncludeItemTypes", "Movie,Series,Episode")
                parameters.append("Limit", limit.toString())
                parameters.append("StartIndex", startIndex.toString())
                parameters.append("Fields", "DateCreated,PremiereDate,Overview,UserData,ImageTags,ChildCount") // Include DateCreated and ChildCount
            }.buildString()
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            // Return all items - filtering based on settings will be done in UI layer
            response
        } catch (e: Exception) {
            e.printStackTrace()
            ItemsResponse(Items = emptyList(), TotalRecordCount = 0)
        }
    }
    
    suspend fun getCollections(): List<JellyfinItem> {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("IncludeItemTypes", "BoxSet")
                parameters.append("Recursive", "true")
                parameters.append("Fields", "ImageTags,ChildCount")
            }.buildString()
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            
            response.Items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun getAllLibraryItems(libraryId: String, limit: Int = 100): List<JellyfinItem> {
        val allItems = mutableListOf<JellyfinItem>()
        var startIndex = 0
        var totalCount = 0
        
        do {
            val response = getLibraryItems(libraryId, limit, startIndex)
            allItems.addAll(response.Items)
            
            // Update total count from first response
            if (totalCount == 0) {
                totalCount = response.TotalRecordCount
            }
            
            // Move to next page
            startIndex += limit
        } while (allItems.size < totalCount && response.Items.isNotEmpty())
        
        // Return all items - filtering based on settings will be done in UI layer
        return allItems
    }
    
    suspend fun getSeasons(seriesId: String, forceRefresh: Boolean = false): List<JellyfinItem> {
        // Check cache first
        if (!forceRefresh) {
            seasonCache[seriesId]?.let { (timestamp, seasons) ->
                if (System.currentTimeMillis() - timestamp < CACHE_DURATION_MS) {
                    android.util.Log.d("JellyfinAPI", "Using cached seasons for series $seriesId")
                    return seasons
                }
            }
        }
        
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Shows/${seriesId}/Seasons").apply {
                parameters.append("UserId", userId)
                parameters.append("Fields", "Overview,UserData,ImageTags")
            }.buildString()
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            val seasons = response.Items.sortedBy { it.IndexNumber ?: 0 }
            
            // Cache the result
            seasonCache[seriesId] = System.currentTimeMillis() to seasons
            seasons
        } catch (e: Exception) {
            android.util.Log.e("JellyfinAPI", "Error fetching seasons for series $seriesId", e)
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun getEpisodes(seriesId: String, seasonId: String, forceRefresh: Boolean = false): List<JellyfinItem> {
        // Check cache first
        if (!forceRefresh) {
            episodeCache[seasonId]?.let { (timestamp, episodes) ->
                if (System.currentTimeMillis() - timestamp < CACHE_DURATION_MS) {
                    android.util.Log.d("JellyfinAPI", "Using cached episodes for season $seasonId")
                    return episodes
                }
            }
        }
        
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Shows/${seriesId}/Episodes").apply {
                parameters.append("UserId", userId)
                parameters.append("SeasonId", seasonId)
                parameters.append("Fields", "Overview,UserData,SeriesName,SeriesId,ImageTags")
            }.buildString()
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            val episodes = response.Items.sortedBy { it.IndexNumber ?: 0 }
            
            // Cache the result
            episodeCache[seasonId] = System.currentTimeMillis() to episodes
            episodes
        } catch (e: Exception) {
            android.util.Log.e("JellyfinAPI", "Error fetching episodes for season $seasonId", e)
            e.printStackTrace()
            emptyList()
        }
    }
    
    // Clear episode cache for a specific season (useful after playback state changes)
    fun invalidateEpisodeCache(seasonId: String? = null) {
        if (seasonId != null) {
            episodeCache.remove(seasonId)
        } else {
            episodeCache.clear()
        }
    }
    
    /**
     * Get next episodes starting from a specific episode index in a season
     * This is similar to the official Jellyfin Android TV app's createNextEpisodesRequest
     * @param seasonId The season ID
     * @param startIndex The episode index number to start from (1-based, but API uses 0-based for startIndex)
     * @param limit Maximum number of episodes to return
     */
    suspend fun getNextEpisodes(seasonId: String, startIndex: Int, limit: Int = 20): List<JellyfinItem> {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            // Use Users/{userId}/Items endpoint with parentId=seasonId and startIndex
            // Note: startIndex in API is 0-based, but episode IndexNumber is 1-based
            // We need to convert: if episode IndexNumber is 5, we want episodes starting from index 4 (0-based)
            val apiStartIndex = (startIndex - 1).coerceAtLeast(0) // Convert 1-based to 0-based
            
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("ParentId", seasonId)
                parameters.append("IncludeItemTypes", "Episode")
                parameters.append("StartIndex", apiStartIndex.toString())
                parameters.append("Limit", limit.toString())
                parameters.append("Fields", "Overview,UserData,SeriesName,SeriesId,ImageTags,IndexNumber,ParentIndexNumber")
            }.buildString()
            
            android.util.Log.d("JellyfinAPI", "Fetching next episodes: seasonId=$seasonId, startIndex=$startIndex (API: $apiStartIndex)")
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            
            android.util.Log.d("JellyfinAPI", "Found ${response.Items.size} episodes starting from index $startIndex")
            response.Items.sortedBy { it.IndexNumber ?: 0 }
        } catch (e: Exception) {
            android.util.Log.e("JellyfinAPI", "Error fetching next episodes for season $seasonId", e)
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Get the next episode directly using the simpler StartIndex approach
     * This is the recommended approach: /Shows/{seriesId}/Episodes?StartIndex={currentIndex + 1}&Limit=1
     * @param seriesId The series ID
     * @param currentEpisodeIndex The current episode's IndexNumber (1-based)
     * @return The next episode, or null if not found
     */
    suspend fun getNextEpisode(seriesId: String, currentEpisodeIndex: Int): JellyfinItem? {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            // Use StartIndex = currentIndex + 1 (API uses 0-based, but IndexNumber is 1-based)
            // If current episode is IndexNumber 5, we want StartIndex=5 (which is index 5 in 0-based, meaning episode 6)
            val startIndex = currentEpisodeIndex // API StartIndex matches 1-based IndexNumber for episodes
            
            val url = URLBuilder().takeFrom("${base}Shows/$seriesId/Episodes").apply {
                parameters.append("UserId", userId)
                parameters.append("StartIndex", startIndex.toString())
                parameters.append("Limit", "1")
                parameters.append("Fields", "MediaSources,Overview,UserData,SeriesName,SeriesId,ImageTags,IndexNumber,ParentIndexNumber")
            }.buildString()
            
            android.util.Log.d("JellyfinAPI", "Fetching next episode: seriesId=$seriesId, StartIndex=$startIndex (current episode index=$currentEpisodeIndex)")
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            
            val nextEpisode = response.Items.firstOrNull()
            if (nextEpisode != null) {
                android.util.Log.d("JellyfinAPI", "✅ Found next episode: ${nextEpisode.Name} (IndexNumber=${nextEpisode.IndexNumber})")
            } else {
                android.util.Log.d("JellyfinAPI", "No next episode found (this might be the last episode)")
            }
            nextEpisode
        } catch (e: Exception) {
            android.util.Log.e("JellyfinAPI", "Error fetching next episode", e)
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Get the count of unwatched episodes for a TV series
     * Returns the number of unwatched episodes across all seasons
     */
    suspend fun getUnwatchedEpisodeCount(seriesId: String): Int {
        return try {
            val seasons = getSeasons(seriesId)
            var unwatchedCount = 0
            
            seasons.forEach { season ->
                val episodes = getEpisodes(seriesId, season.Id)
                episodes.forEach { episode ->
                    val isWatched = episode.UserData?.Played == true || 
                                   episode.UserData?.PlayedPercentage == 100.0
                    if (!isWatched) {
                        unwatchedCount++
                    }
                }
            }
            
            unwatchedCount
        } catch (e: Exception) {
            android.util.Log.e("JellyfinAPI", "Error getting unwatched episode count for series $seriesId", e)
            0
        }
    }
    
    suspend fun getMoviesByGenre(genre: String, excludeItemId: String? = null, limit: Int = 20): List<JellyfinItem> {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("IncludeItemTypes", "Movie")
                parameters.append("Genres", genre)
                parameters.append("Recursive", "true")
                parameters.append("Limit", limit.toString())
                excludeItemId?.let { parameters.append("ExcludeItemIds", it) }
            }.buildString()
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            response.Items
        } catch (e: Exception) {
            android.util.Log.e("JellyfinAPI", "Error fetching movies by genre", e)
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun getSeriesByGenre(genre: String, excludeItemId: String? = null, limit: Int = 20): List<JellyfinItem> {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("IncludeItemTypes", "Series")
                parameters.append("Genres", genre)
                parameters.append("Recursive", "true")
                parameters.append("Limit", limit.toString())
                excludeItemId?.let { parameters.append("ExcludeItemIds", it) }
            }.buildString()
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            response.Items
        } catch (e: Exception) {
            android.util.Log.e("JellyfinAPI", "Error fetching series by genre", e)
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun getMoviesByPerson(personId: String, excludeItemId: String? = null, limit: Int = 20): List<JellyfinItem> {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("IncludeItemTypes", "Movie")
                parameters.append("PersonIds", personId)
                parameters.append("Recursive", "true")
                parameters.append("Limit", limit.toString())
                excludeItemId?.let { parameters.append("ExcludeItemIds", it) }
            }.buildString()
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            response.Items
        } catch (e: Exception) {
            android.util.Log.e("JellyfinAPI", "Error fetching movies by person", e)
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Mark an item as watched
     * POST /Users/{UserId}/PlayedItems/{ItemId}
     * Reference: https://api.jellyfin.org/
     */
    suspend fun markAsWatched(itemId: String): Boolean {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = "${base}Users/$userId/PlayedItems/$itemId"
            
            val deviceId = config?.deviceId ?: ""
            val authHeader = if (deviceId.isNotEmpty()) {
                "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"$deviceId\", Token=\"$accessToken\", Version=\"1.1.5\""
            } else {
                "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\""
            }
            
            client.post(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", authHeader)
            }
            android.util.Log.d("JellyfinAPI", "Marked item $itemId as watched")
            true
        } catch (e: Exception) {
            android.util.Log.e("JellyfinAPI", "Error marking item as watched", e)
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Mark an item as unwatched
     * DELETE /Users/{UserId}/PlayedItems/{ItemId}
     * Reference: https://api.jellyfin.org/
     */
    suspend fun markAsUnwatched(itemId: String): Boolean {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = "${base}Users/$userId/PlayedItems/$itemId"
            
            val deviceId = config?.deviceId ?: ""
            val authHeader = if (deviceId.isNotEmpty()) {
                "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"$deviceId\", Token=\"$accessToken\", Version=\"1.1.5\""
            } else {
                "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\""
            }
            
            client.delete(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", authHeader)
            }
            android.util.Log.d("JellyfinAPI", "Marked item $itemId as unwatched")
            true
        } catch (e: Exception) {
            android.util.Log.e("JellyfinAPI", "Error marking item as unwatched", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Report playback progress to Jellyfin
     * POST /Sessions/Playing/Progress
     * Reference: https://api.jellyfin.org/
     * 
     * @param itemId The item ID being played
     * @param positionTicks Current playback position in ticks (100-nanosecond intervals: 10,000,000 ticks = 1 second)
     * @param isPaused Whether playback is paused
     * @param isMuted Whether audio is muted
     * @param volumeLevel Volume level (0-100)
     * @param playbackRate Playback rate (1.0 = normal speed)
     */
    suspend fun reportPlaybackProgress(
        itemId: String,
        positionTicks: Long,
        isPaused: Boolean = false,
        isMuted: Boolean = false,
        volumeLevel: Int = 100,
        playbackRate: Double = 1.0
    ): Boolean {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = "${base}Sessions/Playing/Progress"
            
            val deviceId = config?.deviceId ?: ""
            val authHeader = if (deviceId.isNotEmpty()) {
                "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"$deviceId\", Token=\"$accessToken\", Version=\"1.1.5\""
            } else {
                "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\""
            }
            
            // Build request body as JSON string
            val requestBody = buildString {
                append("{")
                append("\"ItemId\":\"$itemId\",")
                append("\"PositionTicks\":$positionTicks,")
                append("\"IsPaused\":$isPaused,")
                append("\"IsMuted\":$isMuted,")
                append("\"VolumeLevel\":$volumeLevel,")
                append("\"PlayMethod\":\"DirectStream\",")
                append("\"PlaybackRate\":$playbackRate")
                append("}")
            }
            
            client.post(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", authHeader)
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("JellyfinAPI", "Error reporting playback progress", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Report playback stopped
     * POST /Sessions/Playing/Stopped
     * Reference: https://api.jellyfin.org/
     */
    suspend fun reportPlaybackStopped(
        itemId: String,
        positionTicks: Long
    ): Boolean {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = "${base}Sessions/Playing/Stopped"
            
            val deviceId = config?.deviceId ?: ""
            val authHeader = if (deviceId.isNotEmpty()) {
                "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"$deviceId\", Token=\"$accessToken\", Version=\"1.1.5\""
            } else {
                "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\""
            }
            
            // Build request body as JSON string
            val requestBody = buildString {
                append("{")
                append("\"ItemId\":\"$itemId\",")
                append("\"PositionTicks\":$positionTicks")
                append("}")
            }
            
            client.post(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", authHeader)
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            android.util.Log.d("JellyfinAPI", "Reported playback stopped for item $itemId at position $positionTicks ticks")
            true
        } catch (e: Exception) {
            android.util.Log.e("JellyfinAPI", "Error reporting playback stopped", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Refresh library scan on the Jellyfin server
     * POST /Library/Refresh
     * Triggers a library scan to detect new or updated media
     * Reference: https://api.jellyfin.org/
     */
    suspend fun refreshLibrary(libraryId: String? = null): Boolean {
        return try {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = if (libraryId != null) {
                URLBuilder().takeFrom("${base}Library/Refresh").apply {
                    parameters.append("libraryId", libraryId)
                }.buildString()
            } else {
                "${base}Library/Refresh"
            }
            
            android.util.Log.d("JellyfinAPI", "Triggering library refresh${if (libraryId != null) " for library $libraryId" else ""}")
            
            client.post(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }
            android.util.Log.d("JellyfinAPI", "Library refresh triggered successfully")
            true
        } catch (e: Exception) {
            android.util.Log.e("JellyfinAPI", "Error triggering library refresh", e)
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Search for items (movies, TV shows, episodes) by query
     * GET /Users/{UserId}/Items?SearchTerm={query}
     * Reference: https://api.jellyfin.org/
     */
    suspend fun searchItems(query: String, limit: Int = 50): List<JellyfinItem> {
        return try {
            if (query.isBlank()) return emptyList()
            
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("SearchTerm", query)
                parameters.append("Recursive", "true")
                parameters.append("IncludeItemTypes", "Movie,Series,Episode")
                parameters.append("Limit", limit.toString())
                parameters.append("Fields", "ImageTags,UserData,SeriesName,SeriesId,ChildCount")
            }.buildString()
            
            val response: ItemsResponse = client.get(url) {
                header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                header("X-Emby-Authorization", "MediaBrowser Client=\"Elefin\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.1.5\"")
            }.body()
            
            response.Items
        } catch (e: Exception) {
            android.util.Log.e("JellyfinAPI", "Error searching for items", e)
            e.printStackTrace()
            emptyList()
        }
    }

}
