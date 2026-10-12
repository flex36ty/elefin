package com.flex.elefin.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.flex.elefin.AddServerActivity
import com.flex.elefin.MainActivity
import com.flex.elefin.jellyfin.JellyfinConfig
import com.flex.elefin.jellyfin.ServerAccounts

@Composable
internal fun DrawerServerHeader(expanded: Boolean, modifier: Modifier = Modifier, sharedFocusHighlight: Boolean = false) {
    val context = LocalContext.current
    val config = remember(context) { JellyfinConfig(context) }
    val store = remember(context) { ServerAccounts(context) }
    var showPicker by remember { mutableStateOf(false) }
    val serverName = config.serverName.ifBlank { "Jellyfin Server" }
    val branding = remember(context, config.serverUrl, config.accessToken) {
        ImageRequest.Builder(context)
            .data(config.serverUrl.trimEnd('/') + "/Branding/Splashscreen")
            .addHeader("X-Emby-Token", config.accessToken)
            .size(96, 96)
            .crossfade(false)
            .build()
    }
    Surface(
        onClick = { if (expanded) showPicker = true },
        modifier = modifier.width(if (expanded) 176.dp else 44.dp).height(52.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color(0xFFE7E2EA),
            focusedContainerColor = if (sharedFocusHighlight) Color.Transparent else Color(0xFFE7E2EA),
            focusedContentColor = Color(0xFF202025)
        )
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SubcomposeAsyncImage(
                model = branding,
                contentDescription = "Server branding",
                loading = { Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.padding(4.dp)) },
                error = { Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.padding(4.dp)) },
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
            )
            if (expanded) {
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(serverName, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Switch server", fontSize = 11.sp, color = LocalContentColor.current.copy(alpha = .7f))
                }
            }
        }
    }
    if (showPicker) {
        val accounts = remember(store) { store.list() }
        val activeId = store.activeId()
        val initialFocus = remember { FocusRequester() }
        var error by remember { mutableStateOf<String?>(null) }
        Dialog(onDismissRequest = { showPicker = false }) {
            Column(
                Modifier.widthIn(max = 500.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)).background(Color(0xFF19191F))
                    .padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Switch server", style = MaterialTheme.typography.headlineSmall)
                accounts.forEachIndexed { index, account ->
                    key(account.id) {
                        val initial = if (accounts.any { it.id == activeId }) account.id == activeId else index == 0
                        Box(if (initial) Modifier.focusRequester(initialFocus) else Modifier) {
                            AccountRow(
                                title = account.serverName,
                                subtitle = listOf(account.serverUrl, account.username).filter { it.isNotBlank() }.joinToString("\n"),
                                icon = Icons.Default.Dns,
                                active = account.id == activeId
                            ) {
                                if (account.id == activeId) {
                                    showPicker = false
                                } else if (store.activate(account.id)) {
                                    showPicker = false
                                    context.startActivity(Intent(context, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    })
                                } else {
                                    error = "Unable to switch. Reconnect this server in Settings → Accounts."
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, fontSize = 13.sp) }
                Box(if (accounts.isEmpty()) Modifier.focusRequester(initialFocus) else Modifier) {
                    AccountRow("Add server", "Connect another Jellyfin server", Icons.Default.Add) {
                        showPicker = false
                        context.startActivity(Intent(context, AddServerActivity::class.java))
                    }
                }
            }
            LaunchedEffect(Unit) {
                withFrameNanos { }
                initialFocus.requestFocus()
            }
        }
    }
}
