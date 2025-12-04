package com.flex.elefin.jellyfin

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
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
 * ✅ Local network auto-discovery via UDP broadcast
 */
object ServerDiscovery {
    
    private const val TAG = "ServerDiscovery"
    
    // Jellyfin UDP discovery port
    private const val DISCOVERY_PORT = 7359
    private const val DISCOVERY_MESSAGE = "who is JellyfinServer?"
    private const val DISCOVERY_TIMEOUT_MS = 5000L
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    
    /**
     * Discovered server information from UDP broadcast
     */
    @Serializable
    data class DiscoveredServer(
        val Address: String? = null,
        val Id: String? = null,
        val Name: String? = null,
        val EndpointAddress: String? = null,
        val LocalAddress: String? = null
    )
    
    /**
     * Result of local network discovery
     */
    data class LocalServer(
        val name: String,
        val address: String,
        val id: String
    )
    
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
    
    /**
     * Discover Jellyfin servers on the local network using UDP broadcast.
     * This uses Jellyfin's built-in discovery protocol on port 7359.
     * 
     * Per Jellyfin docs: https://jellyfin.org/docs/general/post-install/networking/
     * "Client Discovery (7359/UDP): Allows clients to discover Jellyfin on the local network.
     *  A broadcast message to this port will return detailed information about your server
     *  that includes name, ip-address and ID."
     * 
     * @param onServerFound Callback invoked for each server found (allows real-time updates)
     * @return List of discovered servers
     */
    suspend fun discoverLocalServers(
        onServerFound: ((LocalServer) -> Unit)? = null
    ): List<LocalServer> = withContext(Dispatchers.IO) {
        val servers = mutableListOf<LocalServer>()
        val seenIds = mutableSetOf<String>()
        
        Log.d(TAG, "Starting local network discovery on port $DISCOVERY_PORT")
        
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = 1000 // 1 second timeout for each receive attempt
            
            // Send discovery broadcast to global broadcast address
            val message = DISCOVERY_MESSAGE.toByteArray(Charsets.UTF_8)
            val broadcastAddress = InetAddress.getByName("255.255.255.255")
            val sendPacket = DatagramPacket(message, message.size, broadcastAddress, DISCOVERY_PORT)
            
            Log.d(TAG, "Sending UDP broadcast to 255.255.255.255:$DISCOVERY_PORT - \"$DISCOVERY_MESSAGE\"")
            socket.send(sendPacket)
            
            // Listen for responses with timeout
            val buffer = ByteArray(4096)
            val startTime = System.currentTimeMillis()
            
            while (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT_MS) {
                try {
                    val receivePacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(receivePacket)
                    
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    Log.d(TAG, "Received response from ${receivePacket.address}: $response")
                    
                    // Parse the JSON response
                    try {
                        val serverInfo = json.decodeFromString<DiscoveredServer>(response)
                        
                        // Get the best address to use
                        val address = serverInfo.LocalAddress 
                            ?: serverInfo.Address 
                            ?: "http://${receivePacket.address.hostAddress}:8096"
                        
                        val serverId = serverInfo.Id ?: receivePacket.address.hostAddress ?: ""
                        
                        // Avoid duplicates
                        if (serverId !in seenIds) {
                            seenIds.add(serverId)
                            
                            val server = LocalServer(
                                name = serverInfo.Name ?: "Jellyfin Server",
                                address = address.removeSuffix("/"),
                                id = serverId
                            )
                            
                            servers.add(server)
                            Log.i(TAG, "✅ Found server: ${server.name} at ${server.address}")
                            
                            onServerFound?.invoke(server)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse server response: ${e.message}")
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    // Timeout is expected, continue listening until overall timeout
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during local discovery: ${e.message}")
        } finally {
            socket?.close()
        }
        
        Log.d(TAG, "Local discovery complete. Found ${servers.size} server(s)")
        return@withContext servers
    }
}
