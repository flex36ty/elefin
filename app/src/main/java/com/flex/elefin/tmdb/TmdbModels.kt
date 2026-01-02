package com.flex.elefin.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * TMDB Movie model - matches TMDB API v3 exactly
 */
@Serializable
data class TmdbMovie(
    val id: Int,
    val adult: Boolean? = null,
    @SerialName("backdrop_path")
    val backdropPath: String? = null,
    @SerialName("genre_ids")
    val genreIds: List<Int> = emptyList(),
    @SerialName("original_language")
    val originalLanguage: String? = null,
    @SerialName("original_title")
    val originalTitle: String? = null,
    val overview: String? = null,
    val popularity: Double? = null,
    @SerialName("poster_path")
    val posterPath: String? = null,
    @SerialName("release_date")
    val releaseDate: String? = null,
    val title: String,
    val video: Boolean? = null,
    @SerialName("vote_average")
    val voteAverage: Double? = null,
    @SerialName("vote_count")
    val voteCount: Int? = null
)

/**
 * TMDB Movie list response
 */
@Serializable
data class TmdbMovieResponse(
    val page: Int,
    val results: List<TmdbMovie>,
    @SerialName("total_pages")
    val totalPages: Int,
    @SerialName("total_results")
    val totalResults: Int
)

/**
 * TMDB TV Show model - matches TMDB API v3 exactly
 */
@Serializable
data class TmdbTvShow(
    val id: Int,
    @SerialName("backdrop_path")
    val backdropPath: String? = null,
    @SerialName("genre_ids")
    val genreIds: List<Int> = emptyList(),
    @SerialName("original_language")
    val originalLanguage: String? = null,
    @SerialName("original_name")
    val originalName: String? = null,
    val overview: String? = null,
    val popularity: Double? = null,
    @SerialName("poster_path")
    val posterPath: String? = null,
    @SerialName("first_air_date")
    val firstAirDate: String? = null,
    val name: String,
    @SerialName("vote_average")
    val voteAverage: Double? = null,
    @SerialName("vote_count")
    val voteCount: Int? = null,
    @SerialName("origin_country")
    val originCountry: List<String> = emptyList()
)

/**
 * TMDB TV list response
 */
@Serializable
data class TmdbTvResponse(
    val page: Int,
    val results: List<TmdbTvShow>,
    @SerialName("total_pages")
    val totalPages: Int,
    @SerialName("total_results")
    val totalResults: Int
)

/**
 * TMDB Genre model
 */
@Serializable
data class TmdbGenre(
    val id: Int,
    val name: String
)

/**
 * TMDB Genre list response
 */
@Serializable
data class TmdbGenreResponse(
    val genres: List<TmdbGenre>
)

/**
 * Normalized discover item for UI display
 * Maps both movies and TV shows into a common format
 */
sealed class DiscoverItem {
    abstract val id: Int
    abstract val title: String
    abstract val posterPath: String?
    abstract val backdropPath: String?
    abstract val popularity: Double?
    abstract val voteAverage: Double?
    abstract val date: String?
    abstract val overview: String?
    abstract val genreIds: List<Int>
    
    data class Movie(
        override val id: Int,
        override val title: String,
        override val posterPath: String?,
        override val backdropPath: String?,
        override val popularity: Double?,
        override val voteAverage: Double?,
        override val date: String?,
        override val overview: String?,
        override val genreIds: List<Int>
    ) : DiscoverItem()
    
    data class TvShow(
        override val id: Int,
        override val title: String,
        override val posterPath: String?,
        override val backdropPath: String?,
        override val popularity: Double?,
        override val voteAverage: Double?,
        override val date: String?,
        override val overview: String?,
        override val genreIds: List<Int>
    ) : DiscoverItem()
}

/**
 * TMDB Video model (Trailers, Teasers, etc.)
 */
@Serializable
data class TmdbVideo(
    val id: String,
    @SerialName("iso_639_1")
    val iso6391: String? = null,
    @SerialName("iso_3166_1")
    val iso31661: String? = null,
    val key: String,
    val name: String,
    val site: String,
    val size: Int,
    val type: String, // "Trailer", "Teaser", "Clip", "Featurette", "Behind the Scenes", "Bloopers"
    val official: Boolean,
    @SerialName("published_at")
    val publishedAt: String? = null
)

/**
 * TMDB Video list response
 */
@Serializable
data class TmdbVideoResponse(
    val id: Int,
    val results: List<TmdbVideo>
)

/**
 * Extension to convert TmdbMovie to DiscoverItem
 */
fun TmdbMovie.toDiscoverItem(): DiscoverItem.Movie = DiscoverItem.Movie(
    id = id,
    title = title,
    posterPath = posterPath,
    backdropPath = backdropPath,
    popularity = popularity,
    voteAverage = voteAverage,
    date = releaseDate,
    overview = overview,
    genreIds = genreIds
)

/**
 * Extension to convert TmdbTvShow to DiscoverItem
 */
fun TmdbTvShow.toDiscoverItem(): DiscoverItem.TvShow = DiscoverItem.TvShow(
    id = id,
    title = name,
    posterPath = posterPath,
    backdropPath = backdropPath,
    popularity = popularity,
    voteAverage = voteAverage,
    date = firstAirDate,
    overview = overview,
    genreIds = genreIds
)

/**
 * TMDB image URL builder
 */
object TmdbImageUrl {
    private const val BASE_URL = "https://image.tmdb.org/t/p/"
    
    // Poster sizes: w92, w154, w185, w342, w500, w780, original
    fun poster(path: String?, size: String = "w500"): String? {
        return path?.let { "$BASE_URL$size$it" }
    }
    
    // Backdrop sizes: w300, w780, w1280, original
    fun backdrop(path: String?, size: String = "w1280"): String? {
        return path?.let { "$BASE_URL$size$it" }
    }
}

