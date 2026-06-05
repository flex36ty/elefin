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
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color

import com.flex.elefin.ui.DeviceUtils

@Composable
fun UpdateDialog(
    release: GitHubRelease,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isTv = remember(context) { DeviceUtils.isTvDevice(context) }
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
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            val content = @Composable {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(if (isTv) 32.dp else 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text(
                        text = "Update Available",
                        style = if (isTv) MaterialTheme.typography.headlineMedium else androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        color = if (isTv) MaterialTheme.colorScheme.onSurface else androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                    )
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "A new version is available: ${release.name}\n\n${release.body ?: "Bug fixes and improvements."}",
                            style = if (isTv) MaterialTheme.typography.bodyLarge else androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = if (isTv) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) else androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                    
                    // Download progress or error message
                    if (installationStarted) {
                        Text(
                            text = "Installation started. The system installer will appear shortly.",
                            style = if (isTv) MaterialTheme.typography.bodyMedium else androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = if (isTv) MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.primary
                        )
                    } else if (isDownloading) {
                        Text(
                            text = "Downloading update... $downloadProgress%",
                            style = if (isTv) MaterialTheme.typography.bodyMedium else androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = if (isTv) MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.primary
                        )
                    } else if (downloadError != null) {
                        Text(
                            text = "Error: $downloadError",
                            style = if (isTv) MaterialTheme.typography.bodyMedium else androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = if (isTv) MaterialTheme.colorScheme.error else androidx.compose.material3.MaterialTheme.colorScheme.error
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val triggerDownload = {
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
                        }

                        if (isTv) {
                            Button(
                                onClick = triggerDownload,
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
                        } else {
                            androidx.compose.material3.Button(
                                onClick = triggerDownload,
                                modifier = Modifier.weight(1f),
                                enabled = !isDownloading
                            ) {
                                androidx.compose.material3.Text(
                                    text = if (isDownloading) "Downloading..." else "Update Now",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            
                            androidx.compose.material3.OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                enabled = !isDownloading
                            ) {
                                androidx.compose.material3.Text(
                                    text = "Later",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (isTv) {
                Surface(
                    modifier = Modifier
                        .width(600.dp)
                        .fillMaxHeight(0.8f)
                        .padding(32.dp),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 8.dp,
                    colors = SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    content = { content() }
                )
            } else {
                androidx.compose.material3.Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .fillMaxHeight(0.7f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    content = content
                )
            }
        }
    }
}
