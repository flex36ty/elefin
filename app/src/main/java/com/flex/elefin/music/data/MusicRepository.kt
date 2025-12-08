package com.flex.elefin.music.data

import com.flex.elefin.music.model.*

class MusicRepository(private val api: JellyfinMusicApi) {

    suspend fun getArtists(limit: Int = 100, startIndex: Int = 0): List<Artist> = 
        api.getArtists(limit, startIndex)

    suspend fun getAlbumsForArtist(artistId: String): List<Album> =
        api.getAlbumsForArtist(artistId)

    suspend fun getAllAlbums(limit: Int = 100, startIndex: Int = 0): List<Album> =
        api.getAllAlbums(limit, startIndex)

    suspend fun getRecentlyAddedAlbums(limit: Int = 20): List<Album> =
        api.getRecentlyAddedAlbums(limit)

    suspend fun getTracksForAlbum(albumId: String): List<Track> =
        api.getTracksForAlbum(albumId)

    suspend fun getTracksByArtist(artistId: String, limit: Int = 100): List<Track> =
        api.getTracksByArtist(artistId, limit)

    suspend fun searchMusic(query: String, limit: Int = 50): Triple<List<Artist>, List<Album>, List<Track>> =
        api.searchMusic(query, limit)
}

