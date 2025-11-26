package com.flex.elefin.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.core.content.ContextCompat
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(
    apiService: JellyfinApiService?,
    onItemClick: (JellyfinItem) -> Unit,
    onBack: () -> Unit,
    showDebugOutlines: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchBoxFocused by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val voiceButtonFocusRequester = remember { FocusRequester() }
    
    // Voice recognition launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (spokenText != null) {
                searchQuery = spokenText
                // Trigger search automatically after voice input
                scope.launch {
                    performSearch(spokenText, apiService) { results ->
                        searchResults = results
                        isLoading = false
                    }
                }
            }
        }
    }
    
    // Helper function to launch voice recognition
    val launchVoiceRecognition: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your search query")
        }
        
        // Check if voice recognition is available
        if (intent.resolveActivity(context.packageManager) != null) {
            voiceLauncher.launch(intent)
        } else {
            android.util.Log.w("SearchScreen", "Voice recognition not available on this device")
        }
    }
    
    // Permission launcher for RECORD_AUDIO (Android 13+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Launch voice recognition after permission is granted
            launchVoiceRecognition()
        }
    }
    
    // Perform search when query changes (with debounce)
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        
        isLoading = true
        delay(500) // Debounce search
        
        if (searchQuery.isNotBlank()) {
            performSearch(searchQuery, apiService) { results ->
                searchResults = results
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }
    
    // Focus search field when screen opens
    LaunchedEffect(Unit) {
        delay(200)
        searchFocusRequester.requestFocus()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp)
    ) {
        // Header with back button and title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyUp) {
                        if (keyEvent.key == Key.DirectionRight) {
                            // Move focus to search box
                            searchFocusRequester.requestFocus()
                            true
                        } else if (keyEvent.key == Key.Back) {
                            onBack()
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
            
            Text(
                text = "Search",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Search bar with voice button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (showDebugOutlines) {
                        Modifier.border(2.dp, Color.Cyan)
                    } else {
                        Modifier
                    }
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search text field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                // Background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = if (searchBoxFocused)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = if (searchBoxFocused) 5.dp else 2.dp,
                            color = if (searchBoxFocused)
                                Color.White // Bright white border when focused
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                )
                
                // Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(searchFocusRequester)
                        .focusable()
                        .onFocusChanged { focusState ->
                            searchBoxFocused = focusState.isFocused
                        }
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            if (keyEvent.key == Key.DirectionDown) {
                                // Move focus to results grid or voice button if results are available
                                if (searchResults.isNotEmpty() && !isLoading) {
                                    focusManager.clearFocus()
                                    // Focus will move to first item in grid automatically
                                } else {
                                    voiceButtonFocusRequester.requestFocus()
                                }
                                true
                            } else if (keyEvent.key == Key.DirectionRight) {
                                // Move focus to voice button
                                voiceButtonFocusRequester.requestFocus()
                                true
                            } else if (keyEvent.key == Key.Enter) {
                                // Trigger search if query is not blank
                                if (searchQuery.isNotBlank()) {
                                    scope.launch {
                                        isLoading = true
                                        performSearch(searchQuery, apiService) { results ->
                                            searchResults = results
                                            isLoading = false
                                        }
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (searchQuery.isNotBlank()) {
                                        scope.launch {
                                            isLoading = true
                                            performSearch(searchQuery, apiService) { results ->
                                                searchResults = results
                                                isLoading = false
                                            }
                                        }
                                    }
                                }
                            ),
                            singleLine = true
                        )
                    }
                    
                        // Placeholder when empty (overlay)
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search movies and TV shows...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(start = 40.dp) // Offset for icon + spacing
                            )
                        }
                    }
                }
            }
            
            // Voice search button
            val hasRecordAudioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Android 12 and below don't need runtime permission for RECORD_AUDIO
            }
            
            IconButton(
                onClick = {
                    if (hasRecordAudioPermission) {
                        launchVoiceRecognition()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // Request permission for Android 13+
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                colors = IconButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .size(56.dp)
                    .focusRequester(voiceButtonFocusRequester)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            if (keyEvent.key == Key.DirectionLeft) {
                                // Move focus back to search box
                                searchFocusRequester.requestFocus()
                                true
                            } else if (keyEvent.key == Key.DirectionDown) {
                                // Move focus to results grid if available
                                if (searchResults.isNotEmpty() && !isLoading) {
                                    focusManager.clearFocus()
                                }
                                true
                            } else if (keyEvent.key == Key.Enter) {
                                // Trigger voice search
                                if (hasRecordAudioPermission) {
                                    launchVoiceRecognition()
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Search",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Search results
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Searching...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else if (searchQuery.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Enter a search term or use voice search",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        } else if (searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No results found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 200.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(
                        if (showDebugOutlines) {
                            Modifier.border(2.dp, Color.Green)
                        } else {
                            Modifier
                        }
                    )
            ) {
                items(searchResults) { item ->
                    JellyfinHorizontalCard(
                        item = item,
                        apiService = apiService,
                        onClick = {
                            onItemClick(item)
                        },
                        onFocusChanged = { },
                        enableCaching = true,
                        reducePosterResolution = false
                    )
                }
            }
        }
    }
}

private suspend fun performSearch(
    query: String,
    apiService: JellyfinApiService?,
    onResults: (List<JellyfinItem>) -> Unit
) {
    if (apiService == null || query.isBlank()) {
        onResults(emptyList())
        return
    }
    
    withContext(Dispatchers.IO) {
        try {
            val results = apiService.searchItems(query, limit = 50)
            onResults(results)
        } catch (e: Exception) {
            android.util.Log.e("SearchScreen", "Error performing search", e)
        onResults(emptyList())
    }
}
}
