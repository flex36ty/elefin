package com.flex.elefin.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Jellyseerr/Overseerr API response models
 * Based on the Overseerr API: https://api-docs.overseerr.dev/
 */

// Common pagination response wrapper
@Serializable
data class PageInfo(
    val pages: Int,
    val pageSize: Int,
    val results: Int,
    val page: Int
)

// Movie result from discover endpoints
@Serializable
data class JellyseerrMovie(
    val id: Int,
    val mediaType: String? = "movie",
    val popularity: Double? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val voteCount: Int? = null,
    val voteAverage: Double? = null,
    val genreIds: List<Int> = emptyList(),
    val overview: String? = null,
    val originalLanguage: String? = null,
    val title: String? = null,
    val originalTitle: String? = null,
    val releaseDate: String? = null,
    val adult: Boolean? = null,
    val video: Boolean? = null,
    val mediaInfo: MediaInfo? = null,
    val credits: JellyseerrCredits? = null
)

// TV Show result from discover endpoints
@Serializable
data class JellyseerrTvShow(
    val id: Int,
    val mediaType: String? = "tv",
    val popularity: Double? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val voteCount: Int? = null,
    val voteAverage: Double? = null,
    val genreIds: List<Int> = emptyList(),
    val overview: String? = null,
    val originalLanguage: String? = null,
    val name: String? = null,
    val originalName: String? = null,
    val originCountry: List<String> = emptyList(),
    val firstAirDate: String? = null,
    val mediaInfo: MediaInfo? = null
)

// Media info (status in Jellyfin/request status)
@Serializable
data class MediaInfo(
    val id: Int? = null,
    val tmdbId: Int? = null,
    val tvdbId: Int? = null,
    val status: Int? = null, // 1=Unknown, 2=Pending, 3=Processing, 4=Partially Available, 5=Available
    val jellyfinMediaId: String? = null,
    @SerialName("jellyfinMediaID") 
    val jellyfinMediaID2: String? = null // Alternative field name
) {
    val jellyfinId: String?
        get() = jellyfinMediaId ?: jellyfinMediaID2
        
    val isAvailable: Boolean
        get() = status == 5 || status == 4
}

// Discover movies response
@Serializable
data class DiscoverMoviesResponse(
    val page: Int,
    val totalPages: Int,
    val totalResults: Int,
    val results: List<JellyseerrMovie>
)

// Discover TV shows response
@Serializable
data class DiscoverTvResponse(
    val page: Int,
    val totalPages: Int,
    val totalResults: Int,
    val results: List<JellyseerrTvShow>
)


// Trending response (can contain both movies and TV shows)
@Serializable
data class TrendingResponse(
    val page: Int,
    val totalPages: Int,
    val totalResults: Int,
    val results: List<TrendingItem>
)

@Serializable
data class TrendingItem(
    val id: Int,
    val mediaType: String, // "movie" or "tv"
    val popularity: Double? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val voteCount: Int? = null,
    val voteAverage: Double? = null,
    val genreIds: List<Int> = emptyList(),
    val overview: String? = null,
    val originalLanguage: String? = null,
    // Movie fields
    val title: String? = null,
    val originalTitle: String? = null,
    val releaseDate: String? = null,
    val adult: Boolean? = null,
    val video: Boolean? = null,
    // TV fields
    val name: String? = null,
    val originalName: String? = null,
    val originCountry: List<String> = emptyList(),
    val firstAirDate: String? = null,
    val mediaInfo: MediaInfo? = null
) {
    val displayTitle: String
        get() = title ?: name ?: originalTitle ?: originalName ?: "Unknown"
        
    val displayDate: String?
        get() = releaseDate ?: firstAirDate
}

// Genre response
@Serializable
data class GenreResponse(
    val id: Int,
    val name: String
)

// Helper object for image URLs
object JellyseerrImageUrl {
    private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/"
    
    fun poster(path: String?, size: String = "w500"): String? {
        return path?.let { "$TMDB_IMAGE_BASE$size$it" }
    }
    
    fun backdrop(path: String?, size: String = "w1280"): String? {
        return path?.let { "$TMDB_IMAGE_BASE$size$it" }
    }
    
    fun logo(path: String?, size: String = "w500"): String? {
        return path?.let { "$TMDB_IMAGE_BASE$size$it" }
    }
}

// Category types for display
enum class JellyseerrCategory(val displayName: String, val endpoint: String) {
    TRENDING("🔥 Trending", "/api/v1/discover/trending"),
    POPULAR_MOVIES("Popular Movies", "/api/v1/discover/movies"),
    UPCOMING_MOVIES("Upcoming Movies", "/api/v1/discover/movies/upcoming"),
    POPULAR_TV("Popular TV Shows", "/api/v1/discover/tv"),
    UPCOMING_TV("Upcoming TV Shows", "/api/v1/discover/tv/upcoming")
}

// Movie genres (same as TMDB)
object JellyseerrGenres {
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

// Request status enum
enum class JellyseerrRequestStatus(val value: Int) {
    PENDING_APPROVAL(1),
    APPROVED(2),
    DECLINED(3);
    
    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: PENDING_APPROVAL
    }
}

// Media request response
@Serializable
data class MediaRequest(
    val id: Int,
    val status: Int, // 1=Pending Approval, 2=Approved, 3=Declined
    val media: MediaRequestMedia? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val requestedBy: RequestUser? = null,
    val modifiedBy: RequestUser? = null
)

@Serializable
data class MediaRequestMedia(
    val id: Int,
    val tmdbId: Int? = null,
    val mediaType: String? = null,
    val status: Int? = null
)

@Serializable
data class RequestUser(
    val id: Int,
    val displayName: String? = null,
    val email: String? = null
)

// Request body for creating a movie request
@Serializable
data class MovieRequestBody(
    val mediaType: String = "movie",
    val mediaId: Int
)

// Request body for creating a TV show request
@Serializable
data class TvRequestBody(
    val mediaType: String = "tv",
    val mediaId: Int,
    val seasons: List<Int>? = null // If null, requests all seasons
)

@Serializable
data class JellyseerrCredits(
    val cast: List<JellyseerrCast> = emptyList(),
    val crew: List<JellyseerrCast> = emptyList()
)

@Serializable
data class JellyseerrCast(
    val id: Int,
    val name: String,
    val character: String? = null,
    val profilePath: String? = null,
    val gender: Int? = null
)

