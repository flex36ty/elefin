package com.flex.elefin.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.flex.elefin.updater.GitHubRelease
import com.flex.elefin.updater.UpdateService
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    release: GitHubRelease,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var installationStarted by remember { mutableStateOf(false) }
    
    Dialog(
        onDismissRequest = {
            if (!isDownloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .width(600.dp)
                .fillMaxHeight(0.8f)
                .padding(32.dp),
            tonalElevation = 8.dp,
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Update Available",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "A new version is available: ${release.name}\n\n${release.body ?: "Bug fixes and improvements."}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
                
                // Download progress or error message
                if (installationStarted) {
                    Text(
                        text = "Installation started. The system installer will appear shortly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (isDownloading) {
                    Text(
                        text = "Downloading update... $downloadProgress%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (downloadError != null) {
                    Text(
                        text = "Error: $downloadError",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val apkUrl = release.assets.firstOrNull()?.browserDownloadUrl
                            if (apkUrl != null && !isDownloading) {
                                isDownloading = true
                                downloadProgress = 0
                                downloadError = null
                                
                                // Download APK in a coroutine
                                scope.launch {
                                    try {
                                        val apkUri = UpdateService.downloadApk(
                                            context = context,
                                            apkUrl = apkUrl,
                                            progressCallback = { progress ->
                                                downloadProgress = progress
                                            }
                                        )
                                        
                                        if (apkUri != null) {
                                            // Install APK using UpdateService (handles both regular Android and Android TV)
                                            try {
                                                val installed = UpdateService.installApk(context, apkUri)
                                                if (installed) {
                                                    // Installation started successfully
                                                    isDownloading = false
                                                    installationStarted = true
                                                    // Keep dialog open for a moment to show the message
                                                    kotlinx.coroutines.delay(2000)
                                                    onUpdate()
                                                } else {
                                                    downloadError = "Failed to start installation"
                                                    isDownloading = false
                                                }
                                            } catch (e: Exception) {
                                                Log.e("UpdateDialog", "Error installing APK", e)
                                                downloadError = "Installation failed: ${e.message}"
                                                isDownloading = false
                                            }
                                        } else {
                                            downloadError = "Download failed"
                                            isDownloading = false
                                        }
                                    } catch (e: Exception) {
                                        Log.e("UpdateDialog", "Error downloading APK", e)
                                        downloadError = "Download failed: ${e.message}"
                                        isDownloading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isDownloading
                    ) {
                        Text(
                            text = if (isDownloading) "Downloading..." else "Update Now",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isDownloading
                    ) {
                        Text(
                            text = "Later",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

