package com.flex.elefin.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.flex.elefin.AddServerActivity
import com.flex.elefin.MainActivity
import com.flex.elefin.jellyfin.SavedServerAccount
import com.flex.elefin.jellyfin.ServerAccounts

@Composable
internal fun ServerAccountsSection(onSignOut: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ServerAccounts(context) }
    var accounts by remember { mutableStateOf(store.list()) }
    var managing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<SavedServerAccount?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, store) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) accounts = store.list()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val activeId = store.activeId()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Switch servers without signing in again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f),
            modifier = Modifier.padding(bottom = 8.dp))
        accounts.forEach { account ->
            key(account.id) {
                AccountRow(
                    title = account.serverName,
                    subtitle = listOf(account.username, account.serverUrl).filter { it.isNotBlank() }.joinToString(" · "),
                    icon = Icons.Default.Dns,
                    active = account.id == activeId
                ) {
                    if (account.id != activeId && store.activate(account.id)) {
                        context.startActivity(Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                    }
                }
            }
        }
        AccountRow("Add server", "Connect another Jellyfin server", Icons.Default.Add) {
            context.startActivity(Intent(context, AddServerActivity::class.java))
        }
        Spacer(Modifier.height(8.dp))
        AccountRow("Manage connections", "Remove saved server logins", Icons.Default.Settings) { managing = true }
        AccountRow("Sign out of current server", icon = Icons.Default.Logout, onClick = onSignOut)
    }
    if (managing) {
        Dialog(onDismissRequest = { managing = false }) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
                .padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Manage connections", style = MaterialTheme.typography.headlineSmall)
                Text("Choose a saved connection to remove it from this device.",
                    style = MaterialTheme.typography.bodyMedium)
                accounts.filter { it.id != activeId }.forEach { account ->
                    key(account.id) {
                        AccountRow(account.serverName,
                            listOf(account.username, account.serverUrl).filter { it.isNotBlank() }.joinToString(" · "),
                            Icons.Default.Dns) { removing = account }
                    }
                }
                if (accounts.none { it.id != activeId }) Text("No other saved connections.")
                Text("To remove the active connection, use Sign out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
                AccountRow("Done", icon = Icons.Default.Check) { managing = false }
            }
        }
    }
    removing?.let { account ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { removing = null },
            title = { androidx.compose.material3.Text("Remove " + account.serverName + "?") },
            text = { androidx.compose.material3.Text("This forgets the saved login on this device. The server and its media are unchanged.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    store.remove(account.id)
                    accounts = store.list()
                    removing = null
                }) { androidx.compose.material3.Text("Remove") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { removing = null }) {
                    androidx.compose.material3.Text("Cancel")
                }
            })
    }
}

/** One D-pad target per row; focus and active-server state stay visually distinct. */
@Composable
internal fun AccountRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    active: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val foreground = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(foreground.copy(alpha = if (focused) .13f else if (active) .08f else .04f))
            .border(if (focused) 2.dp else 1.dp,
                if (focused) Color.White else foreground.copy(alpha = .12f), shape)
            .semantics { if (active) stateDescription = "Active server" }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = foreground,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = foreground.copy(alpha = .65f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (active) {
            Text("Active", style = MaterialTheme.typography.labelMedium, color = foreground.copy(alpha = .75f))
            Icon(Icons.Default.Check, contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
        }
    }
}
