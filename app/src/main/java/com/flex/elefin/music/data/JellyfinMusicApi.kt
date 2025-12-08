package com.flex.elefin.music.data

import android.util.Log
import com.flex.elefin.music.model.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "JellyfinMusicApi"

@Serializable
data class MusicItemsResponse(
    val Items: List<MusicItem> = emptyList(),
    val TotalRecordCount: Int = 0
)

@Serializable
data class MusicItem(
    val Id: String,
    val Name: String,
    val Overview: String? = null,
    val Type: String? = null,
    val Album: String? = null,
    val AlbumId: String? = null,
    val AlbumArtist: String? = null,
    val AlbumArtists: List<ArtistInfo>? = null,
    val ArtistItems: List<ArtistInfo>? = null,
    val Artists: List<String>? = null,
    val ProductionYear: Int? = null,
    val IndexNumber: Int? = null, // Track number
    val ParentIndexNumber: Int? = null, // Disc number
    val RunTimeTicks: Long? = null,
    val ImageTags: Map<String, String>? = null,
    val MediaSources: List<MusicMediaSource>? = null,
    val ChildCount: Int? = null, // Number of tracks in album
    val SongCount: Int? = null,
    val AlbumCount: Int? = null
)

@Serializable
data class ArtistInfo(
    val Id: String,
    val Name: String
)

@Serializable
data class MusicMediaSource(
    val Id: String? = null,
    val Container: String? = null,
    val Bitrate: Int? = null,
    val MediaStreams: List<MusicMediaStream>? = null
)

@Serializable
data class MusicMediaStream(
    val Type: String? = null,
    val Codec: String? = null,
    val BitRate: Int? = null,
    val SampleRate: Int? = null,
    val Channels: Int? = null
)

class JellyfinMusicApi(
    private val baseUrl: String,
    private val accessToken: String,
    private val userId: String
) {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private val base: String
        get() = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    private fun imageUrl(itemId: String): String {
        return "${base}Items/$itemId/Images/Primary?fillHeight=300&fillWidth=300&quality=90"
    }

    /**
     * Get all music artists from the library
     */
    suspend fun getArtists(limit: Int = 100, startIndex: Int = 0): List<Artist> {
        return try {
            val url = URLBuilder().takeFrom("${base}Artists").apply {
                parameters.append("UserId", userId)
                parameters.append("Recursive", "true")
                parameters.append("IncludeItemTypes", "MusicArtist")
                parameters.append("SortBy", "SortName")
                parameters.append("SortOrder", "Ascending")
                parameters.append("Limit", limit.toString())
                parameters.append("StartIndex", startIndex.toString())
                parameters.append("Fields", "Overview,SongCount,AlbumCount")
            }.buildString()

            Log.d(TAG, "Fetching artists: $url")

            val response: MusicItemsResponse = client.get(url) {
                header("X-Emby-Token", accessToken)
            }.body()

            response.Items.map { item ->
                Artist(
                    id = item.Id,
                    name = item.Name,
                    overview = item.Overview,
                    imageUrl = if (item.ImageTags?.containsKey("Primary") == true) imageUrl(item.Id) else null,
                    albumCount = item.AlbumCount ?: 0,
                    songCount = item.SongCount ?: 0
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching artists", e)
            emptyList()
        }
    }

    /**
     * Get albums for a specific artist
     */
    suspend fun getAlbumsForArtist(artistId: String): List<Album> {
        return try {
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("ArtistIds", artistId)
                parameters.append("IncludeItemTypes", "MusicAlbum")
                parameters.append("Recursive", "true")
                parameters.append("SortBy", "ProductionYear,SortName")
                parameters.append("SortOrder", "Descending")
                parameters.append("Fields", "Overview,ChildCount,RunTimeTicks")
            }.buildString()

            Log.d(TAG, "Fetching albums for artist $artistId: $url")

            val response: MusicItemsResponse = client.get(url) {
                header("X-Emby-Token", accessToken)
            }.body()

            response.Items.map { item ->
                Album(
                    id = item.Id,
                    name = item.Name,
                    artist = item.AlbumArtist ?: item.Artists?.firstOrNull() ?: "Unknown Artist",
                    artistId = item.AlbumArtists?.firstOrNull()?.Id ?: item.ArtistItems?.firstOrNull()?.Id,
                    year = item.ProductionYear,
                    overview = item.Overview,
                    imageUrl = if (item.ImageTags?.containsKey("Primary") == true) imageUrl(item.Id) else null,
                    trackCount = item.ChildCount ?: 0,
                    durationTicks = item.RunTimeTicks ?: 0
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching albums for artist $artistId", e)
            emptyList()
        }
    }

    /**
     * Get all albums from the music library
     */
    suspend fun getAllAlbums(limit: Int = 100, startIndex: Int = 0): List<Album> {
        return try {
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("IncludeItemTypes", "MusicAlbum")
                parameters.append("Recursive", "true")
                parameters.append("SortBy", "SortName")
                parameters.append("SortOrder", "Ascending")
                parameters.append("Limit", limit.toString())
                parameters.append("StartIndex", startIndex.toString())
                parameters.append("Fields", "Overview,ChildCount,RunTimeTicks")
            }.buildString()

            Log.d(TAG, "Fetching all albums: $url")

            val response: MusicItemsResponse = client.get(url) {
                header("X-Emby-Token", accessToken)
            }.body()

            response.Items.map { item ->
                Album(
                    id = item.Id,
                    name = item.Name,
                    artist = item.AlbumArtist ?: item.Artists?.firstOrNull() ?: "Unknown Artist",
                    artistId = item.AlbumArtists?.firstOrNull()?.Id ?: item.ArtistItems?.firstOrNull()?.Id,
                    year = item.ProductionYear,
                    overview = item.Overview,
                    imageUrl = if (item.ImageTags?.containsKey("Primary") == true) imageUrl(item.Id) else null,
                    trackCount = item.ChildCount ?: 0,
                    durationTicks = item.RunTimeTicks ?: 0
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching all albums", e)
            emptyList()
        }
    }

    /**
     * Get recently added albums
     */
    suspend fun getRecentlyAddedAlbums(limit: Int = 20): List<Album> {
        return try {
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("IncludeItemTypes", "MusicAlbum")
                parameters.append("Recursive", "true")
                parameters.append("SortBy", "DateCreated")
                parameters.append("SortOrder", "Descending")
                parameters.append("Limit", limit.toString())
                parameters.append("Fields", "Overview,ChildCount,RunTimeTicks")
            }.buildString()

            Log.d(TAG, "Fetching recently added albums: $url")

            val response: MusicItemsResponse = client.get(url) {
                header("X-Emby-Token", accessToken)
            }.body()

            response.Items.map { item ->
                Album(
                    id = item.Id,
                    name = item.Name,
                    artist = item.AlbumArtist ?: item.Artists?.firstOrNull() ?: "Unknown Artist",
                    artistId = item.AlbumArtists?.firstOrNull()?.Id ?: item.ArtistItems?.firstOrNull()?.Id,
                    year = item.ProductionYear,
                    overview = item.Overview,
                    imageUrl = if (item.ImageTags?.containsKey("Primary") == true) imageUrl(item.Id) else null,
                    trackCount = item.ChildCount ?: 0,
                    durationTicks = item.RunTimeTicks ?: 0
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching recently added albums", e)
            emptyList()
        }
    }

    /**
     * Get tracks for a specific album
     */
    suspend fun getTracksForAlbum(albumId: String): List<Track> {
        return try {
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("ParentId", albumId)
                parameters.append("IncludeItemTypes", "Audio")
                parameters.append("SortBy", "ParentIndexNumber,IndexNumber")
                parameters.append("SortOrder", "Ascending")
                parameters.append("Fields", "MediaSources,MediaStreams")
            }.buildString()

            Log.d(TAG, "Fetching tracks for album $albumId: $url")

            val response: MusicItemsResponse = client.get(url) {
                header("X-Emby-Token", accessToken)
            }.body()

            response.Items.map { item ->
                val audioStream = item.MediaSources?.firstOrNull()?.MediaStreams
                    ?.firstOrNull { it.Type == "Audio" }

                Track(
                    id = item.Id,
                    name = item.Name,
                    album = item.Album ?: "Unknown Album",
                    albumId = item.AlbumId,
                    artist = item.AlbumArtist ?: item.Artists?.firstOrNull() ?: "Unknown Artist",
                    artistId = item.AlbumArtists?.firstOrNull()?.Id ?: item.ArtistItems?.firstOrNull()?.Id,
                    trackNumber = item.IndexNumber ?: 0,
                    discNumber = item.ParentIndexNumber ?: 1,
                    durationMs = (item.RunTimeTicks ?: 0) / 10000,
                    imageUrl = if (item.ImageTags?.containsKey("Primary") == true) {
                        imageUrl(item.Id)
                    } else if (item.AlbumId != null) {
                        imageUrl(item.AlbumId)
                    } else null,
                    streamUrl = "${base}Audio/${item.Id}/universal?UserId=$userId&Container=opus,webm|opus,mp3,aac,m4a|aac,m4b|aac,flac,webma,webm|webma,wav,ogg&TranscodingContainer=ts&TranscodingProtocol=hls&AudioCodec=aac&api_key=$accessToken",
                    codec = audioStream?.Codec,
                    bitrate = audioStream?.BitRate ?: item.MediaSources?.firstOrNull()?.Bitrate,
                    sampleRate = audioStream?.SampleRate,
                    channels = audioStream?.Channels
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching tracks for album $albumId", e)
            emptyList()
        }
    }

    /**
     * Get all tracks by an artist
     */
    suspend fun getTracksByArtist(artistId: String, limit: Int = 100): List<Track> {
        return try {
            val url = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("ArtistIds", artistId)
                parameters.append("IncludeItemTypes", "Audio")
                parameters.append("Recursive", "true")
                parameters.append("SortBy", "Album,ParentIndexNumber,IndexNumber")
                parameters.append("SortOrder", "Ascending")
                parameters.append("Limit", limit.toString())
                parameters.append("Fields", "MediaSources,MediaStreams")
            }.buildString()

            Log.d(TAG, "Fetching tracks for artist $artistId: $url")

            val response: MusicItemsResponse = client.get(url) {
                header("X-Emby-Token", accessToken)
            }.body()

            response.Items.map { item ->
                val audioStream = item.MediaSources?.firstOrNull()?.MediaStreams
                    ?.firstOrNull { it.Type == "Audio" }

                Track(
                    id = item.Id,
                    name = item.Name,
                    album = item.Album ?: "Unknown Album",
                    albumId = item.AlbumId,
                    artist = item.AlbumArtist ?: item.Artists?.firstOrNull() ?: "Unknown Artist",
                    artistId = item.AlbumArtists?.firstOrNull()?.Id ?: item.ArtistItems?.firstOrNull()?.Id,
                    trackNumber = item.IndexNumber ?: 0,
                    discNumber = item.ParentIndexNumber ?: 1,
                    durationMs = (item.RunTimeTicks ?: 0) / 10000,
                    imageUrl = if (item.ImageTags?.containsKey("Primary") == true) {
                        imageUrl(item.Id)
                    } else if (item.AlbumId != null) {
                        imageUrl(item.AlbumId)
                    } else null,
                    streamUrl = "${base}Audio/${item.Id}/universal?UserId=$userId&Container=opus,webm|opus,mp3,aac,m4a|aac,m4b|aac,flac,webma,webm|webma,wav,ogg&TranscodingContainer=ts&TranscodingProtocol=hls&AudioCodec=aac&api_key=$accessToken",
                    codec = audioStream?.Codec,
                    bitrate = audioStream?.BitRate ?: item.MediaSources?.firstOrNull()?.Bitrate,
                    sampleRate = audioStream?.SampleRate,
                    channels = audioStream?.Channels
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching tracks for artist $artistId", e)
            emptyList()
        }
    }

    /**
     * Search for music items (artists, albums, tracks)
     */
    suspend fun searchMusic(query: String, limit: Int = 50): Triple<List<Artist>, List<Album>, List<Track>> {
        if (query.isBlank()) return Triple(emptyList(), emptyList(), emptyList())

        return try {
            // Search artists
            val artistsUrl = URLBuilder().takeFrom("${base}Artists").apply {
                parameters.append("UserId", userId)
                parameters.append("SearchTerm", query)
                parameters.append("Limit", limit.toString())
                parameters.append("Fields", "Overview")
            }.buildString()

            val artistsResponse: MusicItemsResponse = client.get(artistsUrl) {
                header("X-Emby-Token", accessToken)
            }.body()

            val artists = artistsResponse.Items.map { item ->
                Artist(
                    id = item.Id,
                    name = item.Name,
                    overview = item.Overview,
                    imageUrl = if (item.ImageTags?.containsKey("Primary") == true) imageUrl(item.Id) else null
                )
            }

            // Search albums
            val albumsUrl = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("SearchTerm", query)
                parameters.append("IncludeItemTypes", "MusicAlbum")
                parameters.append("Recursive", "true")
                parameters.append("Limit", limit.toString())
                parameters.append("Fields", "Overview,ChildCount")
            }.buildString()

            val albumsResponse: MusicItemsResponse = client.get(albumsUrl) {
                header("X-Emby-Token", accessToken)
            }.body()

            val albums = albumsResponse.Items.map { item ->
                Album(
                    id = item.Id,
                    name = item.Name,
                    artist = item.AlbumArtist ?: "Unknown Artist",
                    year = item.ProductionYear,
                    overview = item.Overview,
                    imageUrl = if (item.ImageTags?.containsKey("Primary") == true) imageUrl(item.Id) else null,
                    trackCount = item.ChildCount ?: 0
                )
            }

            // Search tracks
            val tracksUrl = URLBuilder().takeFrom("${base}Users/$userId/Items").apply {
                parameters.append("SearchTerm", query)
                parameters.append("IncludeItemTypes", "Audio")
                parameters.append("Recursive", "true")
                parameters.append("Limit", limit.toString())
                parameters.append("Fields", "MediaSources")
            }.buildString()

            val tracksResponse: MusicItemsResponse = client.get(tracksUrl) {
                header("X-Emby-Token", accessToken)
            }.body()

            val tracks = tracksResponse.Items.map { item ->
                Track(
                    id = item.Id,
                    name = item.Name,
                    album = item.Album ?: "Unknown Album",
                    albumId = item.AlbumId,
                    artist = item.AlbumArtist ?: "Unknown Artist",
                    trackNumber = item.IndexNumber ?: 0,
                    durationMs = (item.RunTimeTicks ?: 0) / 10000,
                    imageUrl = if (item.AlbumId != null) imageUrl(item.AlbumId) else null,
                    streamUrl = "${base}Audio/${item.Id}/universal?UserId=$userId&Container=opus,webm|opus,mp3,aac,m4a|aac,m4b|aac,flac,webma,webm|webma,wav,ogg&TranscodingContainer=ts&TranscodingProtocol=hls&AudioCodec=aac&api_key=$accessToken",
                    codec = null,
                    bitrate = null,
                    sampleRate = null
                )
            }

            Triple(artists, albums, tracks)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching music", e)
            Triple(emptyList(), emptyList(), emptyList())
        }
    }
}

