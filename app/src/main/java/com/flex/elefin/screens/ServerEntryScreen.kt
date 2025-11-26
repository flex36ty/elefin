package com.flex.elefin.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.flex.elefin.jellyfin.JellyfinAuthService
import com.flex.elefin.jellyfin.JellyfinConfig

@Composable
fun ServerEntryScreen(
    onServerConnected: (String) -> Unit,
    prefillAddress: String? = null
) {
    val context = LocalContext.current
    val config = remember { JellyfinConfig(context) }
    
    var serverAddress by remember { mutableStateOf(prefillAddress ?: config.serverUrl.ifEmpty { "" }) }
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    
    val addressFocusRequester = remember { FocusRequester() }
    val connectButtonFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var addressFocused by remember { mutableStateOf(false) }
    var connectButtonFocused by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    
    // Auto-connect if address is prefilled
    LaunchedEffect(prefillAddress) {
        if (prefillAddress != null && prefillAddress.isNotBlank()) {
            connectToServer(serverAddress, context, config, coroutineScope) { success, message ->
                if (success) {
                    onServerConnected(serverAddress.trim())
                } else {
                    errorMessage = message
                    isConnecting = false
                }
            }
        } else {
            addressFocusRequester.requestFocus()
        }
    }
    
    fun connect() {
        if (serverAddress.isBlank()) {
            errorMessage = "Server address cannot be empty"
            return
        }
        
        isConnecting = true
        errorMessage = null
        statusMessage = "Connecting to ${serverAddress.trim()}..."
        
        connectToServer(serverAddress, context, config, coroutineScope) { success, message ->
            isConnecting = false
            if (success) {
                statusMessage = null
                onServerConnected(serverAddress.trim())
            } else {
                statusMessage = null
                errorMessage = message
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Left side content area (50% width, matches Jellyfin AndroidTV layout)
        Column(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 27.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                text = "Enter Server Address",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Label
            Text(
                text = "Valid server address",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
            
            // Address input field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        color = if (addressFocused)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = if (addressFocused) 2.dp else 1.dp,
                        color = if (addressFocused)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .focusRequester(addressFocusRequester)
                    .focusable()
                    .onFocusChanged { addressFocused = it.isFocused }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = serverAddress,
                    onValueChange = { serverAddress = it },
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter) {
                                // Show keyboard when Enter is pressed
                                keyboardController?.show()
                                true
                            } else if (keyEvent.key == Key.Tab) {
                                if (serverAddress.isNotBlank()) {
                                    connectButtonFocusRequester.requestFocus()
                                } else {
                                    focusManager.moveFocus(FocusDirection.Down)
                                }
                                true
                            } else {
                                false
                            }
                        },
                    enabled = !isConnecting && prefillAddress == null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            if (serverAddress.isNotBlank()) {
                                connectButtonFocusRequester.requestFocus()
                                connect()
                            }
                        }
                    )
                )
            }
            
            // Connect button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { connect() },
                    enabled = !isConnecting && prefillAddress == null,
                    modifier = Modifier
                        .focusRequester(connectButtonFocusRequester)
                        .onFocusChanged { connectButtonFocused = it.isFocused }
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter && !isConnecting && prefillAddress == null) {
                                connect()
                                true
                            } else {
                                false
                            }
                        },
                    colors = ButtonDefaults.colors(
                        containerColor = if (connectButtonFocused) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (connectButtonFocused)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text(
                        text = "Connect",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                
                // Status/Error message
                if (statusMessage != null) {
                    Text(
                        text = statusMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Normalize server URL to include protocol and port if missing
 * Examples:
 * - "192.168.1.181" -> "http://192.168.1.181:8096"
 * - "192.168.1.181:8096" -> "http://192.168.1.181:8096"
 * - "http://192.168.1.181" -> "http://192.168.1.181:8096"
 * - "http://192.168.1.181:8096" -> "http://192.168.1.181:8096"
 */
private fun normalizeServerUrl(address: String): String {
    var url = address.trim().removeSuffix("/")
    
    // Add protocol if missing
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        url = "http://$url"
    }
    
    // Extract host and port
    val urlObj = try {
        java.net.URL(url)
    } catch (e: Exception) {
        // If URL parsing fails, assume it's just an IP/hostname
        // Check if it contains a port
        val parts = url.replaceFirst("http://", "").replaceFirst("https://", "").split(":")
        if (parts.size == 2) {
            java.net.URL("http://${parts[0]}:${parts[1]}")
        } else {
            java.net.URL("http://$url:8096")
        }
    }
    
    // Add default port if missing
    val port = urlObj.port
    if (port == -1 || port == urlObj.defaultPort) {
        val host = urlObj.host
        val protocol = urlObj.protocol
        return "$protocol://$host:8096"
    }
    
    return url
}

private fun connectToServer(
    address: String,
    context: android.content.Context,
    config: JellyfinConfig,
    scope: kotlinx.coroutines.CoroutineScope,
    onResult: (Boolean, String) -> Unit
) {
    scope.launch {
        try {
            if (address.isBlank()) {
                onResult(false, "Server address cannot be empty")
                return@launch
            }
            
            // Normalize the URL (add protocol and port if missing)
            val normalizedUrl = normalizeServerUrl(address)
            android.util.Log.d("ServerEntry", "Normalized server URL: $address -> $normalizedUrl")
            
            // Save the normalized server address
            config.serverUrl = normalizedUrl
            
            // Test connection by trying to get public system info
            try {
                val authService = JellyfinAuthService(normalizedUrl, context)
                // Just verify the URL is accessible - we'll do actual auth in login screen
                onResult(true, "")
            } catch (e: Exception) {
                android.util.Log.e("ServerEntry", "Connection test failed", e)
                onResult(false, "Unable to connect: ${e.message ?: "Connection failed"}")
            }
        } catch (e: Exception) {
            android.util.Log.e("ServerEntry", "Error connecting to server", e)
            onResult(false, "Error: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}

