package com.flex.elefin.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.flex.elefin.jellyfin.AppSettings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.flex.elefin.jellyfin.JellyfinApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val librarySocketClient = OkHttpClient.Builder()
    .pingInterval(30, TimeUnit.SECONDS).build()

/** Only the visible screen listens; bursts are coalesced and refreshes never overlap. */
@Composable
internal fun LiveLibraryUpdates(api: JellyfinApiService?, onChanged: suspend () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { AppSettings(context) }
    val owner = LocalLifecycleOwner.current
    val callback = rememberUpdatedState(onChanged)
    LaunchedEffect(api, owner) {
        if (api == null) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!settings.autoRefreshEnabled) return@repeatOnLifecycle
            val changes = Channel<Unit>(Channel.CONFLATED)
            val refreshJob = launch {
                for (change in changes) {
                    delay(2000) // Let a scan finish writing artwork/metadata before reading rows.
                    while (changes.tryReceive().isSuccess) { /* combine scan notifications */ }
                    try {
                        callback.value()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        android.util.Log.w("LiveLibraryUpdates", "Library refresh failed; will retry")
                    }
                }
            }
            try {
                while (true) {
                    val disconnected = Channel<Unit>(Channel.CONFLATED)
                    val url = api.serverBaseUrl.trimEnd('/').toHttpUrl().newBuilder()
                        .addPathSegment("socket")
                        .addQueryParameter("api_key", api.apiKey)
                        .addQueryParameter("deviceId", api.getJellyfinConfig()?.deviceId ?: "elefin")
                        .build()
                    val socket = librarySocketClient.newWebSocket(Request.Builder().url(url).build(),
                        object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: Response) {
                                changes.trySend(Unit) // Also catches changes made while away.
                            }
                            override fun onMessage(webSocket: WebSocket, text: String) {
                                val type = runCatching { JSONObject(text).optString("MessageType") }.getOrNull()
                                if (type == "LibraryChanged") changes.trySend(Unit)
                            }
                            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                                disconnected.trySend(Unit) // Never log URLs containing credentials.
                            }
                            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                                webSocket.close(code, null)
                            }
                            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                                disconnected.trySend(Unit)
                            }
                        })
                    try {
                        do {
                            delay(60000)
                            changes.trySend(Unit) // Proxies may not support WebSockets.
                        } while (disconnected.tryReceive().isFailure)
                    } finally {
                        socket.cancel()
                        disconnected.close()
                    }
                }
            } finally {
                refreshJob.cancel()
                changes.close()
            }
        }
    }
}
