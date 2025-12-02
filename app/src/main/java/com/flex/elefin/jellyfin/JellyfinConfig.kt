package com.flex.elefin.jellyfin

import android.content.Context
import android.content.SharedPreferences

class JellyfinConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "jellyfin_config",
        Context.MODE_PRIVATE
    )

    var serverUrl: String
        get() = prefs.getString("server_url", "") ?: ""
        set(value) {
            // Store the URL as-is - ServerDiscovery handles normalization
            // Remove trailing slash for consistency
            val cleanUrl = value.trim().removeSuffix("/")
            prefs.edit().putString("server_url", cleanUrl).apply()
        }

    var accessToken: String
        get() = prefs.getString("access_token", "") ?: ""
        set(value) = prefs.edit().putString("access_token", value).apply()

    var userId: String
        get() = prefs.getString("user_id", "") ?: ""
        set(value) = prefs.edit().putString("user_id", value).apply()

    var username: String
        get() = prefs.getString("username", "") ?: ""
        set(value) = prefs.edit().putString("username", value).apply()

    var password: String
        get() = prefs.getString("password", "") ?: ""
        set(value) = prefs.edit().putString("password", value).apply()
    
    var deviceId: String
        get() = prefs.getString("device_id", "") ?: ""
        set(value) = prefs.edit().putString("device_id", value).apply()

    fun isConfigured(): Boolean {
        val url = serverUrl
        // Validate that server URL is properly formatted
        val isValidUrl = url.isNotEmpty() && 
            (url.startsWith("http://") || url.startsWith("https://"))
        return isValidUrl && accessToken.isNotEmpty() && userId.isNotEmpty()
    }

    fun hasCredentials(): Boolean {
        return serverUrl.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()
    }

    fun clearAuth(): Unit {
        prefs.edit().apply {
            remove("access_token")
            remove("user_id")
            apply()
        }
    }
}






