package com.flex.elefin.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.flex.elefin.jellyfin.JellyfinAuthService
import com.flex.elefin.jellyfin.JellyfinConfig
import com.flex.elefin.jellyfin.QuickConnectService
import kotlinx.coroutines.isActive

enum class LoginMethod {
    CREDENTIALS,
    QUICKCONNECT
}

@Composable
fun JellyfinLoginScreen(
    serverUrl: String,
    serverName: String = "Jellyfin",
    onLoginSuccess: () -> Unit,
    onCancel: (() -> Unit)? = null,
    forcedUsername: String? = null,
    skipQuickConnect: Boolean = false
) {
    val context = LocalContext.current
    val config = remember { JellyfinConfig(context) }

    var loginMethod by remember { mutableStateOf<LoginMethod>(
        if (skipQuickConnect) LoginMethod.CREDENTIALS else LoginMethod.QUICKCONNECT
    ) }
    var username by remember { mutableStateOf(forcedUsername ?: config.username.ifEmpty { "" }) }
    var password by remember { mutableStateOf(config.password.ifEmpty { "" }) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // QuickConnect state (shared between instructions and code box)
    var quickConnectCode by remember { mutableStateOf<String?>(null) }
    var quickConnectSecret by remember { mutableStateOf<String?>(null) }
    var isPolling by remember { mutableStateOf(false) }
    var isUnavailable by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Left side content area (matches Jellyfin AndroidTV layout)
        Column(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 27.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Title and subtitle section
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Sign In",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Connecting to $serverName",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Login method content (switches between credentials and QuickConnect)
            Crossfade(
                targetState = loginMethod,
                modifier = Modifier.weight(2f)
            ) { method ->
                when (method) {
                    LoginMethod.CREDENTIALS -> CredentialsLoginContent(
                        username = username,
                        password = password,
                        onUsernameChange = { username = it },
                        onPasswordChange = { password = it },
                        errorMessage = errorMessage,
                        isAuthenticating = isAuthenticating,
                        usernameEditable = forcedUsername == null,
                        onLogin = {
                            errorMessage = null
                            isAuthenticating = true
                            performCredentialsLogin(
                                serverUrl = serverUrl,
                                username = username,
                                password = password,
                                config = config,
                                context = context,
                                onSuccess = {
                                    isAuthenticating = false
                                    onLoginSuccess()
                                },
                                onError = { error ->
                                    isAuthenticating = false
                                    errorMessage = error
                                }
                            )
                        }
                    )
                    LoginMethod.QUICKCONNECT -> {
                        QuickConnectLoginContent(
                            serverUrl = serverUrl,
                            errorMessage = errorMessage,
                            isAuthenticating = isAuthenticating,
                            onError = { errorMessage = it },
                            onSuccess = {
                                isAuthenticating = false
                                onLoginSuccess()
                            },
                            onAuthenticatingChange = { isAuthenticating = it },
                            quickConnectCode = quickConnectCode,
                            isPolling = isPolling,
                            isUnavailable = isUnavailable,
                            onQuickConnectCodeChange = { quickConnectCode = it },
                            onQuickConnectSecretChange = { quickConnectSecret = it },
                            onIsPollingChange = { isPolling = it },
                            onIsUnavailableChange = { isUnavailable = it }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action buttons at bottom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Other options:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )

                AnimatedVisibility(visible = loginMethod != LoginMethod.CREDENTIALS) {
                    Button(
                        onClick = { loginMethod = LoginMethod.CREDENTIALS },
                        enabled = !isAuthenticating,
                        modifier = Modifier.onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter && !isAuthenticating) {
                                loginMethod = LoginMethod.CREDENTIALS
                                true
                            } else {
                                false
                            }
                        }
                    ) {
                        Text("Use Password")
                    }
                }

                AnimatedVisibility(visible = loginMethod != LoginMethod.QUICKCONNECT) {
                    Button(
                        onClick = { loginMethod = LoginMethod.QUICKCONNECT },
                        enabled = !isAuthenticating,
                        modifier = Modifier.onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter && !isAuthenticating) {
                                loginMethod = LoginMethod.QUICKCONNECT
                                true
                            } else {
                                false
                            }
                        }
                    ) {
                        Text("Use QuickConnect")
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (onCancel != null) {
                            onCancel()
                        } else {
                            username = ""
                            password = ""
                            errorMessage = null
                        }
                    },
                    enabled = !isAuthenticating,
                    modifier = Modifier.onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter && !isAuthenticating) {
                            if (onCancel != null) {
                                onCancel()
                            } else {
                                username = ""
                                password = ""
                                errorMessage = null
                            }
                            true
                        } else {
                            false
                        }
                    }
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun CredentialsLoginContent(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    errorMessage: String?,
    isAuthenticating: Boolean,
    usernameEditable: Boolean,
    onLogin: () -> Unit
) {
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val loginButtonFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var usernameFocused by remember { mutableStateOf(false) }
    var passwordFocused by remember { mutableStateOf(false) }
    var loginButtonFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (usernameEditable && username.isBlank()) {
            usernameFocusRequester.requestFocus()
        } else {
            passwordFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Username Field
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Username",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            // Outer box for border - matching IP address field style
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(
                        color = if (usernameFocused)
                            Color(0xFFDDDDDD) // Light background when focused (input_default_highlight_background)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), // Visible darker background when not focused
                        shape = RoundedCornerShape(3.dp)
                    )
                    .border(
                        width = if (usernameFocused) 3.dp else 2.dp, // Thicker border when focused for better visibility
                        color = if (usernameFocused)
                            Color(0xFF9C27B0) // Purple border when focused for better visibility
                        else
                            Color(0xB3747474), // input_default_stroke color when not focused
                        shape = RoundedCornerShape(3.dp)
                    )
            ) {
            // Inner box for content with focus
            BasicTextField(
                value = username,
                onValueChange = onUsernameChange,
                textStyle = TextStyle(
                    color = if (usernameFocused)
                        Color(0xFF444444) // Dark text when focused (input_default_highlight_text)
                    else
                        Color(0xFFDDDDDD), // Light text when not focused (input_default_normal_text)
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .focusRequester(usernameFocusRequester)
                    .focusable() // Always focusable for navigation, even if not editable
                    .onFocusChanged { usernameFocused = it.isFocused }
                    .padding(8.dp) // 8dp padding all around (matching Jellyfin style)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            when (keyEvent.key) {
                                Key.Enter -> {
                                    if (usernameEditable) {
                                        // Show keyboard when Enter is pressed (only if editable)
                                        keyboardController?.show()
                                    } else {
                                        // If not editable, move to password field
                                        passwordFocusRequester.requestFocus()
                                    }
                                    true
                                }
                                Key.DirectionDown -> {
                                    // Move to password field
                                    passwordFocusRequester.requestFocus()
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
                enabled = !isAuthenticating && usernameEditable,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { passwordFocusRequester.requestFocus() }
                )
            )
            }
        }

        // Password Field
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Password",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            // Outer box for border - matching IP address field style
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(
                        color = if (passwordFocused)
                            Color(0xFFDDDDDD) // Light background when focused (input_default_highlight_background)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), // Visible darker background when not focused
                        shape = RoundedCornerShape(3.dp)
                    )
                    .border(
                        width = if (passwordFocused) 3.dp else 2.dp, // Thicker border when focused for better visibility
                        color = if (passwordFocused)
                            Color(0xFF9C27B0) // Purple border when focused for better visibility
                        else
                            Color(0xB3747474), // input_default_stroke color when not focused
                        shape = RoundedCornerShape(3.dp)
                    )
            ) {
            // Inner box for content with focus
            BasicTextField(
                value = password,
                onValueChange = onPasswordChange,
                textStyle = TextStyle(
                    color = if (passwordFocused)
                        Color(0xFF444444) // Dark text when focused (input_default_highlight_text)
                    else
                        Color(0xFFDDDDDD), // Light text when not focused (input_default_normal_text)
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                ),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .focusRequester(passwordFocusRequester)
                    .focusable() // Always focusable
                    .onFocusChanged { passwordFocused = it.isFocused }
                    .padding(8.dp) // 8dp padding all around (matching Jellyfin style)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            when (keyEvent.key) {
                                Key.Enter -> {
                                    if (!isAuthenticating) {
                                        // Show keyboard when Enter is pressed
                                        keyboardController?.show()
                                    }
                                    true
                                }
                                Key.DirectionDown -> {
                                    // Move to login button
                                    loginButtonFocusRequester.requestFocus()
                                    true
                                }
                                Key.DirectionUp -> {
                                    // Move back to username field (if editable) or skip to login button
                                    if (usernameEditable) {
                                        usernameFocusRequester.requestFocus()
                                    } else {
                                        // If username not editable, move directly to login button
                                        loginButtonFocusRequester.requestFocus()
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
                enabled = !isAuthenticating, // Always enabled (not disabled when not authenticating)
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (username.isNotBlank() && password.isNotBlank()) {
                            keyboardController?.hide()
                            loginButtonFocusRequester.requestFocus()
                            onLogin()
                        }
                    }
                )
            )
            }
        }

        // Login Button and Error
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onLogin,
                enabled = !isAuthenticating && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .focusRequester(loginButtonFocusRequester)
                    .onFocusChanged { loginButtonFocused = it.isFocused }
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            when (keyEvent.key) {
                                Key.Enter -> {
                                    if (!isAuthenticating && username.isNotBlank() && password.isNotBlank()) {
                                        onLogin()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                Key.DirectionUp -> {
                                    // Move back to password field
                                    passwordFocusRequester.requestFocus()
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
                colors = ButtonDefaults.colors(
                    containerColor = if (loginButtonFocused) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (loginButtonFocused)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text(
                    text = if (isAuthenticating) "Authenticating..." else "Login",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickConnectLoginContent(
    serverUrl: String,
    errorMessage: String?,
    isAuthenticating: Boolean,
    onError: (String) -> Unit,
    onSuccess: () -> Unit,
    onAuthenticatingChange: (Boolean) -> Unit,
    quickConnectCode: String?,
    isPolling: Boolean,
    isUnavailable: Boolean,
    onQuickConnectCodeChange: (String?) -> Unit,
    onQuickConnectSecretChange: (String?) -> Unit,
    onIsPollingChange: (Boolean) -> Unit,
    onIsUnavailableChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authScope = rememberCoroutineScope() // Separate scope for authentication to avoid cancellation
    val config = remember { JellyfinConfig(context) }
    var quickConnectSecret by remember { mutableStateOf<String?>(null) }

    // Initiate QuickConnect when composable is first shown
    LaunchedEffect(Unit) {
        if (quickConnectSecret == null && !isUnavailable) {
            onAuthenticatingChange(true)
            try {
                val trimmedUrl = serverUrl.trim().removeSuffix("/")
                android.util.Log.d("QuickConnectLogin", "Attempting QuickConnect with server: $trimmedUrl")
                val quickConnectService = QuickConnectService(trimmedUrl, context)
                val response = quickConnectService.initiateQuickConnect()
                
                if (response != null) {
                    android.util.Log.d("QuickConnectLogin", "QuickConnect initiated successfully")
                    quickConnectSecret = response.Secret
                    onQuickConnectSecretChange(response.Secret)
                    onQuickConnectCodeChange(response.Code.formatCode())
                    onIsPollingChange(true)
                    onAuthenticatingChange(false)
                } else {
                    android.util.Log.w("QuickConnectLogin", "QuickConnect initiation returned null - may be unavailable")
                    onIsUnavailableChange(true)
                    onError("QuickConnect is not available on this server. Please ensure QuickConnect is enabled in server settings.")
                    onAuthenticatingChange(false)
                }
            } catch (e: Exception) {
                android.util.Log.e("QuickConnectLogin", "Exception during QuickConnect initiation", e)
                onIsUnavailableChange(true)
                onError("Error: ${e.message ?: e.javaClass.simpleName}. Please check server settings and ensure QuickConnect is enabled.")
                onAuthenticatingChange(false)
            }
        }
    }

    // Poll for QuickConnect status
    LaunchedEffect(isPolling, quickConnectSecret) {
        if (!isPolling || quickConnectSecret == null) return@LaunchedEffect
        
        android.util.Log.d("QuickConnectLogin", "Starting QuickConnect polling with secret: ${quickConnectSecret?.take(10)}...")
        
        while (isPolling && quickConnectSecret != null) {
            delay(5000) // Poll every 5 seconds
            
            if (!isPolling) {
                android.util.Log.d("QuickConnectLogin", "Polling stopped (isPolling=false)")
                break
            }
            
            try {
                val trimmedUrl = serverUrl.trim().removeSuffix("/")
                val quickConnectService = QuickConnectService(trimmedUrl, context)
                val state = quickConnectService.getQuickConnectState(quickConnectSecret!!)
                
                if (state != null) {
                    android.util.Log.d("QuickConnectLogin", "Poll response: Authenticated=${state.Authenticated}, Code=${state.Code}")
                    
                    if (state.Authenticated) {
                        // User has authorized - now get the access token via authenticateWithQuickConnect
                        android.util.Log.d("QuickConnectLogin", "✅ User authorized! Getting access token...")
                        
                        // Stop polling first
                        onIsPollingChange(false)
                        onAuthenticatingChange(true)
                        
                        // Normalize the URL before saving (add protocol and port if missing)
                        val normalizedUrl = normalizeServerUrl(trimmedUrl)
                        
                        // Use a separate coroutine scope for authentication to avoid cancellation
                        // Launch in authScope which won't be cancelled when LaunchedEffect recomposes
                        authScope.launch {
                            try {
                                // Call authenticateWithQuickConnect to get the access token
                                val authResult = quickConnectService.authenticateWithQuickConnect(quickConnectSecret!!)
                                
                                if (authResult != null) {
                                    android.util.Log.d("QuickConnectLogin", "✅ QuickConnect authentication successful! AccessToken: ${authResult.AccessToken.take(20)}..., UserId: ${authResult.User.Id}, UserName: ${authResult.User.Name}")
                                    
                                    config.serverUrl = normalizedUrl
                                    config.accessToken = authResult.AccessToken
                                    config.userId = authResult.User.Id
                                    
                                    // Store device ID
                                    val deviceId = try {
                                        android.provider.Settings.Secure.getString(
                                            context.contentResolver,
                                            android.provider.Settings.Secure.ANDROID_ID
                                        ) ?: "56be65b97eb43eca"
                                    } catch (e: Exception) {
                                        "56be65b97eb43eca"
                                    }
                                    config.deviceId = deviceId
                                    
                                    android.util.Log.d("QuickConnectLogin", "✅ Configuration saved: serverUrl=${config.serverUrl}, userId=${config.userId}, deviceId=$deviceId")
                                    
                                    onSuccess()
                                } else {
                                    android.util.Log.e("QuickConnectLogin", "❌ Failed to get access token from authenticateWithQuickConnect")
                                    onError("Failed to authenticate with QuickConnect. Please try again.")
                                    onAuthenticatingChange(false)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("QuickConnectLogin", "Exception during QuickConnect authentication", e)
                                onError("Error authenticating: ${e.message ?: e.javaClass.simpleName}")
                                onAuthenticatingChange(false)
                            }
                        }
                        
                        // Exit the polling loop
                        break
                    } else {
                        // Still waiting - update code if it changed
                        state.Code?.let { code ->
                            val formattedCode = code.formatCode()
                            if (formattedCode != quickConnectCode) {
                                android.util.Log.d("QuickConnectLogin", "Code updated: $formattedCode")
                                onQuickConnectCodeChange(formattedCode)
                            }
                        }
                        android.util.Log.d("QuickConnectLogin", "Still waiting for authentication... (Authenticated=${state.Authenticated})")
                    }
                } else {
                    android.util.Log.w("QuickConnectLogin", "Poll returned null state - may indicate error or server issue")
                    // Continue polling - null might be temporary
                }
            } catch (e: Exception) {
                android.util.Log.e("QuickConnectLogin", "Exception during QuickConnect polling", e)
                onIsPollingChange(false)
                onError("Error polling QuickConnect: ${e.message ?: e.javaClass.simpleName}")
                break
            }
        }
        
        android.util.Log.d("QuickConnectLogin", "Polling loop ended")
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Left side: Instructions and code box
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Instructions
            Text(
                text = "Step 1: Open the Jellyfin app on your phone or browser",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Step 2: Navigate to Quick Connect in user settings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Step 3: Enter the code below",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Code box on the left side
            QuickConnectCodeBox(
                quickConnectCode = quickConnectCode,
                isUnavailable = isUnavailable,
                isPolling = isPolling
            )

            if (isPolling && quickConnectCode != null) {
                Text(
                    text = "Waiting for authorization...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickConnectCodeBox(
    quickConnectCode: String?,
    isUnavailable: Boolean,
    isPolling: Boolean
) {
    if (quickConnectCode != null) {
        Box(
            modifier = Modifier
                .width(300.dp)
                .height(120.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 32.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = quickConnectCode ?: "",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = MaterialTheme.typography.displayMedium.fontSize * 0.6f // 40% smaller
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1
            )
        }
    } else if (isUnavailable) {
        Text(
            text = "QuickConnect unavailable",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    } else {
        Box(
            modifier = Modifier
                .width(300.dp)
                .height(120.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Initializing...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun String.formatCode(): String {
    return buildString {
        this@formatCode.forEachIndexed { index, character ->
            if (index != 0 && index % 3 == 0) append(" ")
            append(character)
        }
    }
}

private fun normalizeServerUrl(url: String): String {
    var normalized = url.trim().removeSuffix("/")
    
    // Add protocol if missing
    if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
        normalized = "http://$normalized"
    }
    
    // Add default port if missing
    return try {
        val urlObj = java.net.URL(normalized)
        val port = urlObj.port
        if (port == -1 || port == urlObj.defaultPort) {
            val host = urlObj.host
            val protocol = urlObj.protocol
            "$protocol://$host:8096"
        } else {
            normalized
        }
    } catch (e: Exception) {
        // If URL parsing fails, check if it contains a port
        val parts = normalized.replaceFirst("http://", "").replaceFirst("https://", "").split(":")
        if (parts.size == 2) {
            normalized
        } else {
            "$normalized:8096"
        }
    }
}

private fun performCredentialsLogin(
    serverUrl: String,
    username: String,
    password: String,
    config: JellyfinConfig,
    context: android.content.Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
    scope.launch {
        try {
            if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                onError("Please fill in all fields")
                return@launch
            }

            val trimmedUrl = serverUrl.trim().removeSuffix("/")
            val authService = JellyfinAuthService(trimmedUrl, context)
            val authResponse = authService.authenticate(username.trim(), password.trim())

            if (authResponse != null) {
                config.serverUrl = trimmedUrl
                config.username = username.trim()
                config.password = password.trim()
                config.accessToken = authResponse.AccessToken
                config.userId = authResponse.User.Id
                // Store DeviceId used during authentication
                val deviceId = try {
                    android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "56be65b97eb43eca"
                } catch (e: Exception) {
                    "56be65b97eb43eca"
                }
                config.deviceId = deviceId
                onSuccess()
            } else {
                onError("Authentication failed. Please check your credentials.")
            }
        } catch (e: Exception) {
            onError("Error: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
