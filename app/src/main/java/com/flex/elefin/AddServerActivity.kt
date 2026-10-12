package com.flex.elefin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.flex.elefin.jellyfin.JellyfinConfig
import com.flex.elefin.jellyfin.ServerAccounts
import com.flex.elefin.screens.JellyfinLoginScreen
import com.flex.elefin.screens.ServerEntryScreen

/** Additional login never writes into the currently active connection. */
class AddServerActivity : ComponentActivity() {
    private lateinit var staging: JellyfinConfig
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServerAccounts(this).save()
        staging = JellyfinConfig(this, "jellyfin_pending_account")
        if (savedInstanceState == null) staging.replace(emptyMap())
        setContent {
            JellyfinAppTheme {
                var url by remember { mutableStateOf(staging.serverUrl) }
                BackHandler {
                    if (url.isNotBlank()) { url = ""; staging.replace(emptyMap()) } else finish()
                }
                if (url.isBlank()) {
                    ServerEntryScreen(sessionConfig = staging, onServerConnected = { url = it })
                } else {
                    JellyfinLoginScreen(serverUrl = url, sessionConfig = staging,
                        onCancel = { url = ""; staging.replace(emptyMap()) },
                        onLoginSuccess = {
                            val accounts = ServerAccounts(this)
                            val sameAccount = accounts.activeId() ==
                                com.flex.elefin.jellyfin.serverAccountId(staging.serverUrl, staging.userId)
                            if (sameAccount) JellyfinConfig(this).replace(staging.snapshot())
                            accounts.save(staging)
                            staging.replace(emptyMap())
                            if (sameAccount) {
                                startActivity(Intent(this, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                })
                            }
                            // A different server stays signed in without replacing the current one.
                            finish()
                        })
                }
            }
        }
    }
}
