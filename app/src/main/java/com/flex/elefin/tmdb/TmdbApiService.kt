package com.flex.elefin.tmdb

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * TMDB API Service for fetching trending movies and TV shows
 */
class TmdbApiService(private val apiKey: String) {
    
    companion object {
        private const val TAG = "TmdbApi"
        private const val BASE_URL = "https://api.themoviedb.org/3"
        
        // Movie genre IDs from TMDB
        val MOVIE_GENRES = mapOf(
            28 to "Action",
            12 to "Adventure",
            16 to "Animation",
            35 to "Comedy",
            80 to "Crime",
            99 to "Documentary",
            18 to "Drama",
            10751 to "Family",
            14 to "Fantasy",
            36 to "History",
            27 to "Horror",
            10402 to "Music",
            9648 to "Mystery",
            10749 to "Romance",
            878 to "Science Fiction",
            10770 to "TV Movie",
            53 to "Thriller",
            10752 to "War",
            37 to "Western"
        )
        
        // TV genre IDs from TMDB
        val TV_GENRES = mapOf(
            10759 to "Action & Adventure",
            16 to "Animation",
            35 to "Comedy",
            80 to "Crime",
            99 to "Documentary",
            18 to "Drama",
            10751 to "Family",
            10762 to "Kids",
            9648 to "Mystery",
            10763 to "News",
            10764 to "Reality",
            10765 to "Sci-Fi & Fantasy",
            10766 to "Soap",
            10767 to "Talk",
            10768 to "War & Politics",
            37 to "Western"
        )
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
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
    
    /**
     * Get trending movies (day or week)
     */
    suspend fun getTrendingMovies(timeWindow: String = "week", page: Int = 1): TmdbMovieResponse? {
        return try {
            val response = client.get("$BASE_URL/trending/movie/$timeWindow") {
                parameter("api_key", apiKey)
                parameter("page", page)
            }
            if (response.status.isSuccess()) {
                response.body<TmdbMovieResponse>().also {
                    Log.d(TAG, "Fetched ${it.results.size} trending movies (page $page)")
                }
            } else {
                Log.e(TAG, "Failed to fetch trending movies: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching trending movies", e)
            null
        }
    }
    
    /**
     * Get trending TV shows (day or week)
     */
    suspend fun getTrendingTvShows(timeWindow: String = "week", page: Int = 1): TmdbTvResponse? {
        return try {
            val response = client.get("$BASE_URL/trending/tv/$timeWindow") {
                parameter("api_key", apiKey)
                parameter("page", page)
            }
            if (response.status.isSuccess()) {
                response.body<TmdbTvResponse>().also {
                    Log.d(TAG, "Fetched ${it.results.size} trending TV shows (page $page)")
                }
            } else {
                Log.e(TAG, "Failed to fetch trending TV shows: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching trending TV shows", e)
            null
        }
    }
    
    /**
     * Discover movies by genre
     */
    suspend fun discoverMoviesByGenre(genreId: Int, page: Int = 1, sortBy: String = "popularity.desc"): TmdbMovieResponse? {
        return try {
            val response = client.get("$BASE_URL/discover/movie") {
                parameter("api_key", apiKey)
                parameter("with_genres", genreId)
                parameter("sort_by", sortBy)
                parameter("page", page)
            }
            if (response.status.isSuccess()) {
                response.body<TmdbMovieResponse>().also {
                    Log.d(TAG, "Fetched ${it.results.size} movies for genre $genreId (page $page)")
                }
            } else {
                Log.e(TAG, "Failed to discover movies by genre: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering movies by genre", e)
            null
        }
    }
    
    /**
     * Discover TV shows by genre
     */
    suspend fun discoverTvShowsByGenre(genreId: Int, page: Int = 1, sortBy: String = "popularity.desc"): TmdbTvResponse? {
        return try {
            val response = client.get("$BASE_URL/discover/tv") {
                parameter("api_key", apiKey)
                parameter("with_genres", genreId)
                parameter("sort_by", sortBy)
                parameter("page", page)
            }
            if (response.status.isSuccess()) {
                response.body<TmdbTvResponse>().also {
                    Log.d(TAG, "Fetched ${it.results.size} TV shows for genre $genreId (page $page)")
                }
            } else {
                Log.e(TAG, "Failed to discover TV shows by genre: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering TV shows by genre", e)
            null
        }
    }
    
    /**
     * Get popular movies
     */
    suspend fun getPopularMovies(page: Int = 1): TmdbMovieResponse? {
        return try {
            val response = client.get("$BASE_URL/movie/popular") {
                parameter("api_key", apiKey)
                parameter("page", page)
            }
            if (response.status.isSuccess()) {
                response.body<TmdbMovieResponse>().also {
                    Log.d(TAG, "Fetched ${it.results.size} popular movies (page $page)")
                }
            } else {
                Log.e(TAG, "Failed to fetch popular movies: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching popular movies", e)
            null
        }
    }
    
    /**
     * Get popular TV shows
     */
    suspend fun getPopularTvShows(page: Int = 1): TmdbTvResponse? {
        return try {
            val response = client.get("$BASE_URL/tv/popular") {
                parameter("api_key", apiKey)
                parameter("page", page)
            }
            if (response.status.isSuccess()) {
                response.body<TmdbTvResponse>().also {
                    Log.d(TAG, "Fetched ${it.results.size} popular TV shows (page $page)")
                }
            } else {
                Log.e(TAG, "Failed to fetch popular TV shows: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching popular TV shows", e)
            null
        }
    }
    
    /**
     * Get upcoming movies
     */
    suspend fun getUpcomingMovies(page: Int = 1): TmdbMovieResponse? {
        return try {
            val response = client.get("$BASE_URL/movie/upcoming") {
                parameter("api_key", apiKey)
                parameter("page", page)
            }
            if (response.status.isSuccess()) {
                response.body<TmdbMovieResponse>().also {
                    Log.d(TAG, "Fetched ${it.results.size} upcoming movies (page $page)")
                }
            } else {
                Log.e(TAG, "Failed to fetch upcoming movies: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching upcoming movies", e)
            null
        }
    }
    
    /**
     * Get movie genres list
     */
    suspend fun getMovieGenres(): TmdbGenreResponse? {
        return try {
            val response = client.get("$BASE_URL/genre/movie/list") {
                parameter("api_key", apiKey)
            }
            if (response.status.isSuccess()) {
                response.body<TmdbGenreResponse>().also {
                    Log.d(TAG, "Fetched ${it.genres.size} movie genres")
                }
            } else {
                Log.e(TAG, "Failed to fetch movie genres: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching movie genres", e)
            null
        }
    }
    
    /**
     * Get TV genres list
     */
    suspend fun getTvGenres(): TmdbGenreResponse? {
        return try {
            val response = client.get("$BASE_URL/genre/tv/list") {
                parameter("api_key", apiKey)
            }
            if (response.status.isSuccess()) {
                response.body<TmdbGenreResponse>().also {
                    Log.d(TAG, "Fetched ${it.genres.size} TV genres")
                }
            } else {
                Log.e(TAG, "Failed to fetch TV genres: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TV genres", e)
            null
        }
    }
    
    /**
     * Get trending movies grouped by genre
     * Returns a map of genre name to list of movies
     */
    suspend fun getTrendingMoviesByGenre(): Map<String, List<TmdbMovie>> {
        val trending = getTrendingMovies(page = 1)?.results ?: emptyList()
        val trending2 = getTrendingMovies(page = 2)?.results ?: emptyList()
        val allTrending = trending + trending2
        
        if (allTrending.isEmpty()) return emptyMap()
        
        // Group movies by their first genre
        val genreGroups = mutableMapOf<String, MutableList<TmdbMovie>>()
        
        for (movie in allTrending) {
            val primaryGenreId = movie.genreIds.firstOrNull() ?: continue
            val genreName = MOVIE_GENRES[primaryGenreId] ?: continue
            
            genreGroups.getOrPut(genreName) { mutableListOf() }.add(movie)
        }
        
        // Also add "Trending" as a special category with all movies
        genreGroups["🔥 Trending Now"] = allTrending.take(20).toMutableList()
        
        return genreGroups.mapValues { it.value.distinctBy { m -> m.id } }
    }
    
    /**
     * Get trending TV shows grouped by genre
     * Returns a map of genre name to list of TV shows
     */
    suspend fun getTrendingTvShowsByGenre(): Map<String, List<TmdbTvShow>> {
        val trending = getTrendingTvShows(page = 1)?.results ?: emptyList()
        val trending2 = getTrendingTvShows(page = 2)?.results ?: emptyList()
        val allTrending = trending + trending2
        
        if (allTrending.isEmpty()) return emptyMap()
        
        // Group TV shows by their first genre
        val genreGroups = mutableMapOf<String, MutableList<TmdbTvShow>>()
        
        for (show in allTrending) {
            val primaryGenreId = show.genreIds.firstOrNull() ?: continue
            val genreName = TV_GENRES[primaryGenreId] ?: continue
            
            genreGroups.getOrPut(genreName) { mutableListOf() }.add(show)
        }
        
        // Also add "Trending" as a special category with all shows
        genreGroups["🔥 Trending Now"] = allTrending.take(20).toMutableList()
        
        return genreGroups.mapValues { it.value.distinctBy { s -> s.id } }
    }
    
    fun close() {
        client.close()
    }
}
