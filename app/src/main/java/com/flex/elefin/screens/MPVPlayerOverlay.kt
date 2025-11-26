package com.flex.elefin.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import `is`.xyz.mpv.MPVView
import kotlinx.coroutines.delay

@Composable
fun MPVPlayerOverlay(
    mpvView: MPVView?,
    visible: Boolean,
    onClose: () -> Unit
) {
    if (mpvView == null || !visible) return
    
    val currentPosition = remember { mutableStateOf(0.0) }
    val duration = remember { mutableStateOf(0.0) }
    val isPaused = remember { mutableStateOf(false) }
    
    // Update position and pause state periodically
    LaunchedEffect(visible, mpvView) {
        if (!visible || mpvView == null) return@LaunchedEffect
        
        while (visible && mpvView != null) {
            try {
                val view = mpvView ?: break
                currentPosition.value = view.getCurrentPosition()
                duration.value = view.getDuration()
                isPaused.value = view.isPaused()
            } catch (e: Exception) {
                android.util.Log.w("MPVOverlay", "Error updating overlay state: ${e.message}", e)
                break
            }
            delay(500) // Update every 500ms
        }
    }
    
    // Use AnimatedVisibility for smooth show/hide animations (like ExoPlayer's controller)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row - Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    var closeFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = onClose,
                        modifier = Modifier
                            .onFocusChanged { closeFocused = it.isFocused },
                        colors = if (closeFocused) {
                            ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            ButtonDefaults.colors()
                        }
                    ) {
                        Text("Close")
                    }
                }

                // Center play/pause button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var playPauseFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            if (isPaused.value) {
                                mpvView?.resume()
                            } else {
                                mpvView?.pause()
                            }
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .onFocusChanged { playPauseFocused = it.isFocused },
                        colors = if (playPauseFocused) {
                            ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            ButtonDefaults.colors()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = if (isPaused.value) "Play" else "Pause",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Bottom scrub bar and time
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Scrub bar
                    val progress = if (duration.value > 0) {
                        (currentPosition.value / duration.value).coerceIn(0.0, 1.0)
                    } else {
                        0.0
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color.Gray.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.toFloat())
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }

                    // Time display
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition.value.toLong()),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formatTime(duration.value.toLong()),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

fun formatTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%d:%02d", minutes, secs)
    }
}
