package com.flex.elefin.jellyfin

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Server discovery and validation for Jellyfin servers.
 * 
 * Handles:
 * ✅ Reverse proxies (Nginx, Caddy, Traefik, Cloudflare)
 * ✅ HTTPS-only servers
 * ✅ Subpath installs (/jellyfin)
 * ✅ Root installs (/)
 * ✅ Non-standard ports
 * ✅ Path-rewriting proxies
 * ✅ Custom domain hosting (orbyt.link, duckdns, etc.)
 */
object ServerDiscovery {
    
    private const val TAG = "ServerDiscovery"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    
    /**
     * Build all possible URL candidates from user input.
     * Handles reverse proxies, subpaths, ports, and protocol variations.
     */
    private fun buildUrlCandidates(input: String): List<String> {
        val normalized = input
            .removeSuffix("/")
            .replace(" ", "")
            .trim()
        
        if (normalized.isBlank()) return emptyList()
        
        // Check if input is just a domain/IP without port or path
        val domainOnly = !normalized.contains(":") && !normalized.contains("/")
        
        // Check if it already has a port specified
        val hasExplicitPort = normalized.contains(Regex(":\\d+"))
        
        val baseVariants = mutableListOf<String>()
        
        // Detect scheme
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            baseVariants += normalized
        } else {
            // Try HTTPS first (most proxies require it)
            baseVariants += "https://$normalized"
            baseVariants += "http://$normalized"
        }
        
        val candidates = mutableListOf<String>()
        
        for (base in baseVariants) {
            // Try root install
            candidates += "$base/System/Info/Public"
            
            // Try HTTPS enforcer (Cloudflare, Caddy)
            if (base.startsWith("http://")) {
                candidates += base.replace("http://", "https://") + "/System/Info/Public"
            }
            
            // Try subpath `/jellyfin` (common setup)
            candidates += "$base/jellyfin/System/Info/Public"
            
            // Try other common non-root paths used by proxies
            candidates += "$base/media/System/Info/Public"
            candidates += "$base/api/System/Info/Public"
            
            // Only add port variations if no explicit port was given
            if (domainOnly && !hasExplicitPort) {
                // Extract the base without any existing port for port variations
                val baseForPorts = if (base.startsWith("https://")) {
                    "https://" + base.removePrefix("https://").split("/")[0]
                } else {
                    "http://" + base.removePrefix("http://").split("/")[0]
                }
                
                // Try Jellyfin default LAN port (8096)
                candidates += "$baseForPorts:8096/System/Info/Public"
                
                // Try HTTPS reverse proxy common port
                if (base.startsWith("https://")) {
                    candidates += "$baseForPorts:443/System/Info/Public"
                    candidates += "$baseForPorts:8920/System/Info/Public" // Jellyfin HTTPS default
                }
                
                // Try port 80 fallback
                if (base.startsWith("http://")) {
                    candidates += "$baseForPorts:80/System/Info/Public"
                }
                
                // Also try subpaths with ports
                candidates += "$baseForPorts:8096/jellyfin/System/Info/Public"
            }
        }
        
        return candidates.distinct()
    }
    
    /**
     * Attempt to discover a Jellyfin server from user input.
     * Tries multiple URL variations to handle reverse proxies and subpaths.
     * 
     * @param userInput The user-provided server address
     * @return The working server base URL, or null if not found
     */
    suspend fun discoverServer(userInput: String): String? = withContext(Dispatchers.IO) {
        val candidates = buildUrlCandidates(userInput)
        
        if (candidates.isEmpty()) {
            Log.w(TAG, "No URL candidates generated from input: $userInput")
            return@withContext null
        }
        
        Log.d(TAG, "Trying ${candidates.size} URL variations for: $userInput")
        
        for (url in candidates) {
            try {
                Log.d(TAG, "Trying: $url")
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("Accept", "application/json")
                    .build()
                
                val response = client.newCall(request).execute()
                
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (body != null && body.contains("Jellyfin", ignoreCase = true)) {
                            Log.i(TAG, "✅ Success: $url")
                            
                            // Remove /System/Info/Public to get the base URL
                            val baseUrl = url
                                .replace("/System/Info/Public", "")
                                .removeSuffix("/")
                            
                            Log.i(TAG, "Discovered server base URL: $baseUrl")
                            return@withContext baseUrl
                        } else {
                            Log.d(TAG, "Response OK but not Jellyfin: ${body?.take(100)}")
                        }
                    } else {
                        Log.d(TAG, "HTTP ${resp.code}: $url")
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed: $url → ${e.message}")
            }
        }
        
        Log.w(TAG, "❌ Server not found after trying ${candidates.size} URLs")
        return@withContext null
    }
    
    /**
     * Validate that an existing server URL is still accessible.
     */
    suspend fun validateServer(serverUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = serverUrl.removeSuffix("/")
            val infoUrl = "$cleanUrl/System/Info/Public"
            
            Log.d(TAG, "Validating server: $infoUrl")
            
            val request = Request.Builder()
                .url(infoUrl)
                .get()
                .header("Accept", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            
            response.use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    val isValid = body != null && body.contains("Jellyfin", ignoreCase = true)
                    Log.d(TAG, "Server validation: ${if (isValid) "✅ Valid" else "❌ Invalid"}")
                    return@withContext isValid
                }
            }
            
            false
        } catch (e: Exception) {
            Log.w(TAG, "Server validation failed: ${e.message}")
            false
        }
    }
}
