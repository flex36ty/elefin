package com.flex.elefin.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.RemoteSubtitleInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Uses only the user's Jellyfin session; provider credentials stay on the server. */
@Composable
fun ServerSubtitleSearchDialog(
    itemId: String,
    api: JellyfinApiService,
    onDownloaded: suspend (ServerSubtitleSelection) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var language by remember(itemId) { mutableStateOf<String?>(null) }
    var results by remember(itemId) { mutableStateOf<List<RemoteSubtitleInfo>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val first = remember { FocusRequester() }
    val close = { job?.cancel(); onDismiss() }
    LaunchedEffect(language, busy) {
        if (!busy) first.requestFocus()
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.9f).fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (language == null) "Find subtitles" else "Select subtitle", style = MaterialTheme.typography.headlineSmall)
                if (busy) {
                    Text(if (downloading) "Downloading subtitle to this device…" else "Searching Jellyfin's subtitle providers…")
                } else {
                    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (language == null) {
                            items(SubtitleLanguages.languages) { (code, name) ->
                                ListItem(
                                    selected = false,
                                    modifier = if (code == SubtitleLanguages.languages.first().first) Modifier.focusRequester(first) else Modifier,
                                    headlineContent = { Text(name) },
                                    onClick = {
                                        language = code
                                        busy = true
                                        message = null
                                        job = scope.launch {
                                            try {
                                                results = api.searchRemoteSubtitles(itemId, subtitleServerLanguage(code))
                                                    .sortedByDescending { it.IsHashMatch == true }
                                                if (results.isEmpty()) message = "No subtitles found. Try another language. The OpenSubtitles plugin must be installed and configured on your Jellyfin server."
                                            } catch (e: CancellationException) { throw e
                                            } catch (e: Exception) {
                                                results = emptyList()
                                                message = e.message ?: "Subtitle search failed. Check your Jellyfin server."
                                            } finally { busy = false }
                                        }
                                    }
                                )
                            }
                        } else {
                            item {
                                ListItem(selected = false, modifier = Modifier.focusRequester(first),
                                    headlineContent = { Text("Change language / search again") },
                                    onClick = { language = null; message = null; results = emptyList() })
                            }
                            items(results) { result ->
                                ListItem(
                                    selected = false,
                                    headlineContent = { Text(result.Name ?: "Subtitle", maxLines = 2) },
                                    supportingContent = { Text(listOfNotNull(
                                        result.ProviderName, result.Format,
                                        "Exact match".takeIf { result.IsHashMatch == true },
                                        "SDH".takeIf { result.HearingImpaired == true },
                                        "Forced".takeIf { result.Forced == true }
                                    ).joinToString(" · ")) },
                                    onClick = {
                                        busy = true
                                        downloading = true
                                        message = null
                                        job = scope.launch {
                                            try {
                                                val selection = downloadLocalProviderSubtitle(context, api, itemId, result)
                                                onDownloaded(selection)
                                                onDismiss()
                                            } catch (e: CancellationException) { throw e
                                            } catch (e: Exception) {
                                                message = e.message ?: "Subtitle download failed. Check your Jellyfin server."
                                            } finally { busy = false; downloading = false }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                Button(onClick = close) { Text("Close") }
            }
        }
    }
}
