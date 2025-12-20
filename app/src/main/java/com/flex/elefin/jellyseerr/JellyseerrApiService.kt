package com.flex.elefin.jellyseerr

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Authentication type for Jellyseerr
 */
enum class JellyseerrAuthType {
    API_KEY,
    COOKIE // From username/password login
}

/**
 * Jellyseerr/Overseerr API Service
 * Fetches trending, popular, and upcoming movies/TV shows from Jellyseerr
 * 
 * Supports two authentication methods:
 * 1. API Key (X-Api-Key header)
 * 2. Username/Password (session cookie from /api/v1/auth/local or /api/v1/auth/jellyfin)
 * 
 * API Reference: https://api-docs.overseerr.dev/
 */
class JellyseerrApiService private constructor(
    private val baseUrl: String,
    private val authType: JellyseerrAuthType,
    private val apiKey: String? = null,
    private var sessionCookie: String? = null
) {
    companion object {
        private const val TAG = "JellyseerrApi"
        
        /**
         * Create service with API key authentication
         */
        fun withApiKey(baseUrl: String, apiKey: String): JellyseerrApiService {
            return JellyseerrApiService(
                baseUrl = baseUrl,
                authType = JellyseerrAuthType.API_KEY,
                apiKey = apiKey
            )
        }
        
        /**
         * Create service with session cookie (from login)
         */
        fun withCookie(baseUrl: String, cookie: String): JellyseerrApiService {
            return JellyseerrApiService(
                baseUrl = baseUrl,
                authType = JellyseerrAuthType.COOKIE,
                sessionCookie = cookie
            )
        }
        
        /**
         * Authenticate with local email/password and return session cookie
         */
        suspend fun loginWithEmail(baseUrl: String, email: String, password: String): Result<String> {
            val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
            val jsonConfig = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
            val tempClient = HttpClient(Android) {
                install(ContentNegotiation) {
                    json(jsonConfig)
                }
                install(HttpCookies) {
                    storage = AcceptAllCookiesStorage()
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 30000
                    connectTimeoutMillis = 15000
                }
            }
            
            return try {
                val response = tempClient.post("$normalizedUrl/api/v1/auth/local") {
                    contentType(ContentType.Application.Json)
                    setBody(mapOf("email" to email, "password" to password))
                }
                
                if (response.status.isSuccess()) {
                    // Extract session cookie from response
                    val cookies = response.headers.getAll("Set-Cookie")
                    val connectSid = cookies?.find { it.startsWith("connect.sid=") }
                        ?.substringBefore(";")
                    
                    if (connectSid != null) {
                        Log.d(TAG, "Login successful, got session cookie")
                        Result.success(connectSid)
                    } else {
                        Log.e(TAG, "Login successful but no session cookie found")
                        Result.failure(Exception("No session cookie returned"))
                    }
                } else {
                    Log.e(TAG, "Login failed: ${response.status}")
                    Result.failure(Exception("Login failed: ${response.status}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                Result.failure(e)
            } finally {
                tempClient.close()
            }
        }
        
        /**
         * Authenticate with Jellyfin credentials and return session cookie
         */
        suspend fun loginWithJellyfin(baseUrl: String, username: String, password: String): Result<String> {
            val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
            val jsonConfig = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
            val tempClient = HttpClient(Android) {
                install(ContentNegotiation) {
                    json(jsonConfig)
                }
                install(HttpCookies) {
                    storage = AcceptAllCookiesStorage()
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 30000
                    connectTimeoutMillis = 15000
                }
            }
            
            return try {
                val response = tempClient.post("$normalizedUrl/api/v1/auth/jellyfin") {
                    contentType(ContentType.Application.Json)
                    setBody(mapOf("username" to username, "password" to password))
                }
                
                if (response.status.isSuccess()) {
                    // Extract session cookie from response
                    val cookies = response.headers.getAll("Set-Cookie")
                    val connectSid = cookies?.find { it.startsWith("connect.sid=") }
                        ?.substringBefore(";")
                    
                    if (connectSid != null) {
                        Log.d(TAG, "Jellyfin login successful, got session cookie")
                        Result.success(connectSid)
                    } else {
                        Log.e(TAG, "Jellyfin login successful but no session cookie found")
                        Result.failure(Exception("No session cookie returned"))
                    }
                } else {
                    Log.e(TAG, "Jellyfin login failed: ${response.status}")
                    Result.failure(Exception("Login failed: ${response.status}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Jellyfin login error", e)
                Result.failure(e)
            } finally {
                tempClient.close()
            }
        }
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }
    
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
        }
    }
    
    private val normalizedBaseUrl: String
        get() = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    
    /**
     * Add authentication header based on auth type
     */
    private fun io.ktor.client.request.HttpRequestBuilder.addAuth() {
        when (authType) {
            JellyseerrAuthType.API_KEY -> {
                apiKey?.let { header("X-Api-Key", it) }
            }
            JellyseerrAuthType.COOKIE -> {
                sessionCookie?.let { header("Cookie", it) }
            }
        }
    }
    
    /**
     * Get trending movies and TV shows
     */
    suspend fun getTrending(page: Int = 1): List<TrendingItem> {
        return try {
            val response = client.get("$normalizedBaseUrl/api/v1/discover/trending") {
                addAuth()
                parameter("page", page)
            }
            if (response.status.isSuccess()) {
                val result = response.body<TrendingResponse>()
                Log.d(TAG, "Fetched ${result.results.size} trending items (page $page)")
                result.results
            } else {
                Log.e(TAG, "Failed to fetch trending: ${response.status}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching trending", e)
            emptyList()
        }
    }
    
    /**
     * Get popular movies
     */
    suspend fun getPopularMovies(page: Int = 1): List<JellyseerrMovie> {
        return try {
            val response = client.get("$normalizedBaseUrl/api/v1/discover/movies") {
                addAuth()
                parameter("page", page)
            }
            if (response.status.isSuccess()) {
                val result = response.body<DiscoverMoviesResponse>()
                Log.d(TAG, "Fetched ${result.results.size} popular movies (page $page)")
                result.results
            } else {
                Log.e(TAG, "Failed to fetch popular movies: ${response.status}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching popular movies", e)
            emptyList()
        }
    }
    
    /**
     * Get upcoming movies
     */
    suspend fun getUpcomingMovies(page: Int = 1): List<JellyseerrMovie> {
        return try {
            val response = client.get("$normalizedBaseUrl/api/v1/discover/movies/upcoming") {
                addAuth()
                parameter("page", page)
            }
            if (response.status.isSuccess()) {
                val result = response.body<DiscoverMoviesResponse>()
                Log.d(TAG, "Fetched ${result.results.size} upcoming movies (page $page)")
                result.results
            } else {
                Log.e(TAG, "Failed to fetch upcoming movies: ${response.status}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching upcoming movies", e)
            emptyList()
        }
    }
    
    /**
     * Get popular TV shows
     */
    suspend fun getPopularTvShows(page: Int = 1): List<JellyseerrTvShow> {
        return try {
            val response = client.get("$normalizedBaseUrl/api/v1/discover/tv") {
                addAuth()
                parameter("page", page)
            }
            if (response.status.isSuccess()) {
                val result = response.body<DiscoverTvResponse>()
                Log.d(TAG, "Fetched ${result.results.size} popular TV shows (page $page)")
                result.results
            } else {
                Log.e(TAG, "Failed to fetch popular TV shows: ${response.status}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching popular TV shows", e)
            emptyList()
        }
    }
    
    /**
     * Get upcoming TV shows
     */
    suspend fun getUpcomingTvShows(page: Int = 1): List<JellyseerrTvShow> {
        return try {
            val response = client.get("$normalizedBaseUrl/api/v1/discover/tv/upcoming") {
                addAuth()
                parameter("page", page)
            }
            if (response.status.isSuccess()) {
                val result = response.body<DiscoverTvResponse>()
                Log.d(TAG, "Fetched ${result.results.size} upcoming TV shows (page $page)")
                result.results
            } else {
                Log.e(TAG, "Failed to fetch upcoming TV shows: ${response.status}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching upcoming TV shows", e)
            emptyList()
        }
    }
    
    
    /**
     * Get all movie categories for the Discover tab
     * Returns a map of category name to list of movies
     */
    suspend fun getAllMovieCategories(): Map<String, List<JellyseerrMovie>> {
        val categories = mutableMapOf<String, List<JellyseerrMovie>>()
        
        // Trending movies (filter from trending endpoint)
        val trending = getTrending()
        val trendingMovies = trending
            .filter { it.mediaType == "movie" }
            .map { item ->
                JellyseerrMovie(
                    id = item.id,
                    mediaType = "movie",
                    popularity = item.popularity,
                    posterPath = item.posterPath,
                    backdropPath = item.backdropPath,
                    voteCount = item.voteCount,
                    voteAverage = item.voteAverage,
                    genreIds = item.genreIds,
                    overview = item.overview,
                    originalLanguage = item.originalLanguage,
                    title = item.title,
                    originalTitle = item.originalTitle,
                    releaseDate = item.releaseDate,
                    adult = item.adult,
                    video = item.video,
                    mediaInfo = item.mediaInfo
                )
            }
        if (trendingMovies.isNotEmpty()) {
            categories["🔥 Trending"] = trendingMovies
        }
        
        // Popular movies
        val popular = getPopularMovies()
        if (popular.isNotEmpty()) {
            categories["Popular"] = popular
        }
        
        // Upcoming movies
        val upcoming = getUpcomingMovies()
        if (upcoming.isNotEmpty()) {
            categories["Upcoming"] = upcoming
        }
        
        return categories
    }
    
    /**
     * Get all TV show categories for the Discover tab
     * Returns a map of category name to list of TV shows
     */
    suspend fun getAllTvCategories(): Map<String, List<JellyseerrTvShow>> {
        val categories = mutableMapOf<String, List<JellyseerrTvShow>>()
        
        // Trending TV shows (filter from trending endpoint)
        val trending = getTrending()
        val trendingShows = trending
            .filter { it.mediaType == "tv" }
            .map { item ->
                JellyseerrTvShow(
                    id = item.id,
                    mediaType = "tv",
                    popularity = item.popularity,
                    posterPath = item.posterPath,
                    backdropPath = item.backdropPath,
                    voteCount = item.voteCount,
                    voteAverage = item.voteAverage,
                    genreIds = item.genreIds,
                    overview = item.overview,
                    originalLanguage = item.originalLanguage,
                    name = item.name,
                    originalName = item.originalName,
                    originCountry = item.originCountry,
                    firstAirDate = item.firstAirDate,
                    mediaInfo = item.mediaInfo
                )
            }
        if (trendingShows.isNotEmpty()) {
            categories["🔥 Trending"] = trendingShows
        }
        
        // Popular TV shows
        val popular = getPopularTvShows()
        if (popular.isNotEmpty()) {
            categories["Popular"] = popular
        }
        
        // Upcoming TV shows
        val upcoming = getUpcomingTvShows()
        if (upcoming.isNotEmpty()) {
            categories["Upcoming"] = upcoming
        }
        
        return categories
    }
    
    /**
     * Test the connection to Jellyseerr
     */
    suspend fun testConnection(): Boolean {
        return try {
            val response = client.get("$normalizedBaseUrl/api/v1/status") {
                addAuth()
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            false
        }
    }
    
    /**
     * Request a movie by TMDB ID
     * @param tmdbId The TMDB ID of the movie to request
     * @return Result containing the MediaRequest on success, or an exception on failure
     */
    suspend fun requestMovie(tmdbId: Int): Result<MediaRequest> {
        return try {
            // Build the request body as a simple map to ensure correct JSON serialization
            // Use the Serializable data class to ensure correct JSON serialization
            val requestBody = MovieRequestBody(
                mediaType = "movie",
                mediaId = tmdbId
            )
            
            Log.d(TAG, "Requesting movie with TMDB ID: $tmdbId, body: $requestBody")
            
            val response = client.post("$normalizedBaseUrl/api/v1/request") {
                addAuth()
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            
            if (response.status.isSuccess()) {
                val request = response.body<MediaRequest>()
                Log.d(TAG, "Movie request created successfully: ID ${request.id}, status ${request.status}")
                Result.success(request)
            } else {
                // Try to get error message from response
                val errorBody = try { response.bodyAsText() } catch (e: Exception) { "" }
                val errorMsg = "Request failed with status: ${response.status}, body: $errorBody"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting movie", e)
            Result.failure(e)
        }
    }

    /**
     * Get full details for a TV show by TMDB ID
     */
    suspend fun getTvShowDetails(tmdbId: Int): JellyseerrTvShow? {
        return try {
            Log.d(TAG, "Fetching TV show details for TMDB ID: $tmdbId")
            val response = client.get("$normalizedBaseUrl/api/v1/tv/$tmdbId") {
                addAuth()
            }
            
            if (response.status.isSuccess()) {
                response.body<JellyseerrTvShow>()
            } else {
                Log.e(TAG, "Failed to fetch TV show details: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TV show details", e)
            null
        }
    }

    /**
     * Request a TV show (specific seasons or all)
     */
    suspend fun requestTvShow(tmdbId: Int, seasons: List<Int>? = null): Result<MediaRequest> {
        return try {
            // Build the request body
            val requestBody = TvRequestBody(
                mediaType = "tv",
                mediaId = tmdbId,
                seasons = seasons
            )
            
            Log.d(TAG, "Requesting TV show with TMDB ID: $tmdbId, seasons: $seasons")
            
            val response = client.post("$normalizedBaseUrl/api/v1/request") {
                addAuth()
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            
            if (response.status.isSuccess()) {
                val request = response.body<MediaRequest>()
                Log.d(TAG, "TV show request created successfully: ID ${request.id}, status ${request.status}")
                Result.success(request)
            } else {
                // Try to get error message from response
                val errorBody = try { response.bodyAsText() } catch (e: Exception) { "" }
                val errorMsg = "Request failed with status: ${response.status}, body: $errorBody"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting TV show", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get movie details from Jellyseerr (includes request status)
     * @param tmdbId The TMDB ID of the movie
     * @return JellyseerrMovie with mediaInfo populated
     */
    suspend fun getMovieDetails(tmdbId: Int): JellyseerrMovie? {
        return try {
            val response = client.get("$normalizedBaseUrl/api/v1/movie/$tmdbId") {
                addAuth()
            }
            if (response.status.isSuccess()) {
                response.body<JellyseerrMovie>()
            } else {
                Log.e(TAG, "Failed to fetch movie details: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching movie details", e)
            null
        }
    }
    
    fun close() {
        client.close()
    }
}

