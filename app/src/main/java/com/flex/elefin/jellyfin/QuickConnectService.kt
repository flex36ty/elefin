package com.flex.elefin.jellyfin

import android.content.Context
import android.provider.Settings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class QuickConnectService(
    private val baseUrl: String,
    private val context: Context? = null
) {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private fun getDeviceId(): String {
        return try {
            context?.let {
                Settings.Secure.getString(it.contentResolver, Settings.Secure.ANDROID_ID)
            } ?: "android-tv-device"
        } catch (e: Exception) {
            "android-tv-device"
        }
    }

    private fun normalizeBaseUrl(url: String): String {
        var normalized = url.trim().removeSuffix("/")
        
        // Add protocol if missing
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }
        
        // Add default port if missing
        return try {
            val urlObj = java.net.URL(normalized)
            val port = urlObj.port
            if (port == -1 || port == urlObj.defaultPort) {
                val host = urlObj.host
                val protocol = urlObj.protocol
                "$protocol://$host:8096"
            } else {
                normalized
            }
        } catch (e: Exception) {
            // If URL parsing fails, check if it contains a port
            val parts = normalized.replaceFirst("http://", "").replaceFirst("https://", "").split(":")
            if (parts.size == 2) {
                normalized
            } else {
                "$normalized:8096"
            }
        }
    }

    suspend fun initiateQuickConnect(): QuickConnectInitiateResponse? {
        return try {
            val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
            val url = if (normalizedBaseUrl.endsWith("/")) {
                "${normalizedBaseUrl}QuickConnect/Initiate"
            } else {
                "$normalizedBaseUrl/QuickConnect/Initiate"
            }
            
            val deviceId = getDeviceId()
            val deviceName = "Android TV"
            val clientName = "Elefin"
            val clientVersion = "1.0.0"
            
            val embyAuthHeader = "MediaBrowser Client=\"$clientName\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"$clientVersion\""
            
            android.util.Log.d("QuickConnect", "Initiating QuickConnect at: $url")
            android.util.Log.d("QuickConnect", "DeviceId: $deviceId")
            android.util.Log.d("QuickConnect", "Auth header: $embyAuthHeader")
            
            val response: HttpResponse = client.post(url) {
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.ContentType, "application/json")
                header("X-Emby-Authorization", embyAuthHeader)
            }
            
            android.util.Log.d("QuickConnect", "Response status: ${response.status.value} (${response.status})")
            
            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.Created -> {
                    val result = response.body<QuickConnectInitiateResponse>()
                    android.util.Log.d("QuickConnect", "QuickConnect initiated successfully. Code: ${result.Code}, Secret: ${result.Secret}")
                    result
                }
                HttpStatusCode.Unauthorized -> {
                    android.util.Log.w("QuickConnect", "QuickConnect returned 401 - may not be enabled on server")
                    null
                }
                HttpStatusCode.NotFound -> {
                    android.util.Log.w("QuickConnect", "QuickConnect endpoint not found (404) - server may not support it")
                    null
                }
                else -> {
                    val errorBody = try {
                        response.body<String>()
                    } catch (e: Exception) {
                        "Could not read error body: ${e.message}"
                    }
                    android.util.Log.e("QuickConnect", "QuickConnect initiation failed. Status: ${response.status.value}, Error: $errorBody")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("QuickConnect", "Exception initiating QuickConnect", e)
            e.printStackTrace()
            null
        }
    }

    suspend fun getQuickConnectState(secret: String): QuickConnectStateResponse? {
        return try {
            val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
            val url = if (normalizedBaseUrl.endsWith("/")) {
                "${normalizedBaseUrl}QuickConnect/Connect?secret=$secret"
            } else {
                "$normalizedBaseUrl/QuickConnect/Connect?secret=$secret"
            }
            
            val deviceId = getDeviceId()
            val deviceName = "Android TV"
            val clientName = "Elefin"
            val clientVersion = "1.0.0"
            
            val embyAuthHeader = "MediaBrowser Client=\"$clientName\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"$clientVersion\""
            
            android.util.Log.d("QuickConnect", "Polling QuickConnect state at: $url")
            android.util.Log.d("QuickConnect", "Secret: $secret")
            
            val response = client.get(url) {
                header(HttpHeaders.Accept, "application/json")
                header("X-Emby-Authorization", embyAuthHeader)
            }
            
            android.util.Log.d("QuickConnect", "Response status: ${response.status.value} (${response.status})")
            
            when (response.status) {
                HttpStatusCode.OK -> {
                    val state = response.body<QuickConnectStateResponse>()
                    android.util.Log.d("QuickConnect", "State response: Authenticated=${state.Authenticated}, HasAuth=${state.Authentication != null}")
                    if (state.Authenticated && state.Authentication != null) {
                        android.util.Log.d("QuickConnect", "✅ Authentication successful! AccessToken: ${state.Authentication.AccessToken.take(20)}..., UserId: ${state.Authentication.User.Id}")
                    }
                    state
                }
                HttpStatusCode.Unauthorized -> {
                    android.util.Log.w("QuickConnect", "QuickConnect returned 401 Unauthorized")
                    null
                }
                HttpStatusCode.NotFound -> {
                    android.util.Log.w("QuickConnect", "QuickConnect endpoint not found (404)")
                    null
                }
                else -> {
                    val errorBody = try {
                        response.body<String>()
                    } catch (e: Exception) {
                        "Could not read error body: ${e.message}"
                    }
                    android.util.Log.e("QuickConnect", "QuickConnect state check failed. Status: ${response.status.value}, Error: $errorBody")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("QuickConnect", "Exception getting QuickConnect state", e)
            e.printStackTrace()
            null
        }
    }

    suspend fun authenticateWithQuickConnect(secret: String): QuickConnectAuthenticationResponse? {
        return try {
            val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
            val url = if (normalizedBaseUrl.endsWith("/")) {
                "${normalizedBaseUrl}Users/authenticateWithQuickConnect"
            } else {
                "$normalizedBaseUrl/Users/authenticateWithQuickConnect"
            }
            
            val deviceId = getDeviceId()
            val deviceName = "Android TV"
            val clientName = "Elefin"
            val clientVersion = "1.0.0"
            
            val embyAuthHeader = "MediaBrowser Client=\"$clientName\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"$clientVersion\""
            
            android.util.Log.d("QuickConnect", "Authenticating with QuickConnect at: $url")
            android.util.Log.d("QuickConnect", "Secret: $secret")
            
            val response: HttpResponse = client.post(url) {
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.ContentType, "application/json")
                header("X-Emby-Authorization", embyAuthHeader)
                setBody(QuickConnectAuthenticateRequest(Secret = secret))
            }
            
            android.util.Log.d("QuickConnect", "Auth response status: ${response.status.value} (${response.status})")
            
            when (response.status) {
                HttpStatusCode.OK -> {
                    val result = response.body<QuickConnectAuthenticationResponse>()
                    android.util.Log.d("QuickConnect", "✅ QuickConnect authentication successful! AccessToken: ${result.AccessToken.take(20)}..., UserId: ${result.User.Id}")
                    result
                }
                HttpStatusCode.Unauthorized -> {
                    android.util.Log.w("QuickConnect", "QuickConnect authentication returned 401 Unauthorized")
                    null
                }
                HttpStatusCode.NotFound -> {
                    android.util.Log.w("QuickConnect", "QuickConnect authentication endpoint not found (404)")
                    null
                }
                else -> {
                    val errorBody = try {
                        response.body<String>()
                    } catch (e: Exception) {
                        "Could not read error body: ${e.message}"
                    }
                    android.util.Log.e("QuickConnect", "QuickConnect authentication failed. Status: ${response.status.value}, Error: $errorBody")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("QuickConnect", "Exception authenticating with QuickConnect", e)
            e.printStackTrace()
            null
        }
    }

}

