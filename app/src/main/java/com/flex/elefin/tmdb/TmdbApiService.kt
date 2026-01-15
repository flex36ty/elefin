package com.flex.elefin.tmdb

import android.util.Log
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.decodeFromString

object TmdbApiService {
    private const val BASE_URL = "https://api.themoviedb.org/3"
    private val json = Json { ignoreUnknownKeys = true }

    fun getVideos(tmdbId: Int, type: String, apiKey: String, language: String? = null): List<TmdbVideo> {
        // type: "movie" or "tv"
        // Build URL with language support
        // include_video_language: Comma separated list of ISO 639-1 codes. 
        // We include the requested language, "en" (English), and empty string (original/no language) as fallback.
        var endpoint = "$BASE_URL/$type/$tmdbId/videos?api_key=$apiKey"
        
        if (!language.isNullOrEmpty()) {
            endpoint += "&language=$language&include_video_language=$language,en,null"
        } else {
            // Default to English + Original
            endpoint += "&include_video_language=en,null"
        }
        
        Log.d("TmdbApiService", "Requesting TMDB videos: $endpoint")

        return try {
            val url = URL(endpoint)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                val response = json.decodeFromString<TmdbVideoResponse>(responseString)
                response.results
            } else {
                Log.e("TmdbApiService", "Error fetching videos: ${connection.responseCode} ${connection.responseMessage}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("TmdbApiService", "Exception fetching videos", e)
            emptyList()
        }
    }
    sealed class VerificationResult {
        object Success : VerificationResult()
        data class Error(val message: String) : VerificationResult()
    }

    suspend fun verifyKey(apiKey: String): VerificationResult {
        val trimmedKey = apiKey.trim()
        val endpoint = "$BASE_URL/configuration?api_key=$trimmedKey"
        Log.d("TmdbApiService", "Verifying key with: $endpoint")

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = URL(endpoint)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> VerificationResult.Success
                    HttpURLConnection.HTTP_UNAUTHORIZED -> VerificationResult.Error("Invalid API Key")
                    else -> VerificationResult.Error("Error: ${connection.responseCode}")
                }
            } catch (e: javax.net.ssl.SSLHandshakeException) {
                Log.e("TmdbApiService", "SSL Handshake failed", e)
                VerificationResult.Error("SSL Error: Check Device Date/Time")
            } catch (e: java.io.IOException) {
                Log.e("TmdbApiService", "Network error during verification", e)
                VerificationResult.Error("Network Error: ${e.message}")
            } catch (e: Exception) {
                Log.e("TmdbApiService", "Verification exception", e)
                VerificationResult.Error("Error: ${e.message}")
            }
        }
    }
}
