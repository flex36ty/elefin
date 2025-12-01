package com.flex.elefin.jellyfin

import android.content.Context
import android.provider.Settings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AuthenticationRequest(
    val Username: String,
    val Pw: String
)

@Serializable
data class AuthenticationResponse(
    val AccessToken: String,
    val User: UserInfo
)

@Serializable
data class UserInfo(
    val Id: String,
    val Name: String
)

class JellyfinAuthService(
    private val baseUrl: String,
    private val context: Context? = null
) {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
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
        var protocol = "http"
        
        // Check if protocol is already specified
        if (normalized.startsWith("https://")) {
            protocol = "https"
            normalized = normalized.removePrefix("https://")
        } else if (normalized.startsWith("http://")) {
            protocol = "http"
            normalized = normalized.removePrefix("http://")
        } else {
            // No protocol specified - check port to detect HTTPS
            val portIndex = normalized.indexOf(':')
            if (portIndex > 0) {
                val portStr = normalized.substring(portIndex + 1)
                val port = portStr.toIntOrNull()
                
                // Common HTTPS ports: 443 (standard), 8920 (Jellyfin default HTTPS)
                if (port != null && (port == 443 || port == 8920)) {
                    protocol = "https"
                }
            }
        }
        
        // Add protocol back to normalized URL
        normalized = "$protocol://$normalized"
        
        // Add default port if missing
        return try {
            val urlObj = java.net.URL(normalized)
            val port = urlObj.port
            if (port == -1 || port == urlObj.defaultPort) {
                val host = urlObj.host
                val actualProtocol = urlObj.protocol
                // Use appropriate default port based on protocol
                val defaultPort = if (actualProtocol == "https") {
                    // Jellyfin HTTPS default is 8920, but 443 is also common
                    "8920"
                } else {
                    "8096" // Jellyfin HTTP default
                }
                "$actualProtocol://$host:$defaultPort"
            } else {
                normalized
            }
        } catch (e: Exception) {
            // If URL parsing fails, check if it contains a port
            val parts = normalized.replaceFirst("http://", "").replaceFirst("https://", "").split(":")
            if (parts.size == 2) {
                normalized
            } else {
                // Use appropriate default port based on protocol
                val defaultPort = if (protocol == "https") "8920" else "8096"
                "$normalized:$defaultPort"
            }
        }
    }

    suspend fun authenticate(username: String, password: String): AuthenticationResponse? {
        return try {
            val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
            val url = if (normalizedBaseUrl.endsWith("/")) {
                "${normalizedBaseUrl}Users/authenticatebyname"
            } else {
                "$normalizedBaseUrl/Users/authenticatebyname"
            }
            
            val deviceId = getDeviceId()
            val deviceName = "Android TV"
            val clientName = "Elefin"
            val clientVersion = "1.1.5"
            
            val embyAuthHeader = "MediaBrowser Client=\"$clientName\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"$clientVersion\""
            
            println("Authenticating to: $url")
            println("DeviceId: $deviceId")
            println("Username: $username")
            
            val requestBody = AuthenticationRequest(Username = username, Pw = password)
            val jsonBody = Json.encodeToString(AuthenticationRequest.serializer(), requestBody)
            println("Request body: $jsonBody")
            
            val response: HttpResponse = client.post(url) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                header("X-Emby-Authorization", embyAuthHeader)
                setBody(requestBody)
            }
            
            println("Response status: ${response.status}")
            
            if (response.status == HttpStatusCode.OK) {
                response.body<AuthenticationResponse>()
            } else {
                // Log the error response
                val errorBody = try {
                    response.bodyAsText()
                } catch (e: Exception) {
                    "Could not read error body: ${e.message}"
                }
                println("Authentication failed: ${response.status} - $errorBody")
                null
            }
        } catch (e: Exception) {
            println("Authentication exception: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}






