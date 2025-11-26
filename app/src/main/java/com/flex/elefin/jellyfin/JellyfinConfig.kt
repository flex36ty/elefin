package com.flex.elefin.jellyfin

import android.content.Context
import android.content.SharedPreferences

class JellyfinConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "jellyfin_config",
        Context.MODE_PRIVATE
    )

    var serverUrl: String
        get() {
            val url = prefs.getString("server_url", "") ?: ""
            // Normalize URL if it exists but is missing protocol/port
            return if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
                // Add http:// and default port if missing
                val normalized = if (url.contains(":")) {
                    "http://$url"
                } else {
                    "http://$url:8096"
                }
                // Save normalized URL back
                prefs.edit().putString("server_url", normalized).apply()
                normalized
            } else {
                url
            }
        }
        set(value) {
            // Normalize URL before saving
            val normalized = if (value.isNotEmpty()) {
                when {
                    value.startsWith("http://") || value.startsWith("https://") -> {
                        // URL already has protocol, check if it has a port
                        val protocolEnd = if (value.startsWith("https://")) 8 else 7
                        val afterProtocol = value.substring(protocolEnd)
                        if (!afterProtocol.contains(":")) {
                            // No port specified, add default
                            if (value.startsWith("https://")) {
                                "$value:443"
                            } else {
                                "$value:8096"
                            }
                        } else {
                            value
                        }
                    }
                    value.contains(":") -> "http://$value" // Has port but no protocol
                    else -> "http://$value:8096" // No protocol or port
                }
            } else {
                value
            }
            prefs.edit().putString("server_url", normalized).apply()
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
            (url.startsWith("http://") || url.startsWith("https://")) &&
            url.contains(":")
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






