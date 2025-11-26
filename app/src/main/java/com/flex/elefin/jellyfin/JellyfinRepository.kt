package com.flex.elefin.jellyfin

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class JellyfinRepository(
    private val apiService: JellyfinApiService
) {
    private val _continueWatchingItems = MutableStateFlow<List<JellyfinItem>>(emptyList())
    val continueWatchingItems: StateFlow<List<JellyfinItem>> = _continueWatchingItems.asStateFlow()

    private val _nextUpItems = MutableStateFlow<List<JellyfinItem>>(emptyList())
    val nextUpItems: StateFlow<List<JellyfinItem>> = _nextUpItems.asStateFlow()

    private val _recentlyAddedMovies = MutableStateFlow<List<JellyfinItem>>(emptyList())
    val recentlyAddedMovies: StateFlow<List<JellyfinItem>> = _recentlyAddedMovies.asStateFlow()

    private val _recentlyReleasedMovies = MutableStateFlow<List<JellyfinItem>>(emptyList())
    val recentlyReleasedMovies: StateFlow<List<JellyfinItem>> = _recentlyReleasedMovies.asStateFlow()

    private val _recentlyAddedShows = MutableStateFlow<List<JellyfinItem>>(emptyList())
    val recentlyAddedShows: StateFlow<List<JellyfinItem>> = _recentlyAddedShows.asStateFlow()

    private val _recentlyAddedEpisodes = MutableStateFlow<List<JellyfinItem>>(emptyList())
    val recentlyAddedEpisodes: StateFlow<List<JellyfinItem>> = _recentlyAddedEpisodes.asStateFlow()

    private val _libraries = MutableStateFlow<List<JellyfinLibrary>>(emptyList())
    val libraries: StateFlow<List<JellyfinLibrary>> = _libraries.asStateFlow()

    private val _libraryItems = MutableStateFlow<Map<String, List<JellyfinItem>>>(emptyMap())
    val libraryItems: StateFlow<Map<String, List<JellyfinItem>>> = _libraryItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun fetchContinueWatching() {
        _isLoading.value = true
        _error.value = null
        try {
            val items = apiService.getContinueWatching()
            _continueWatchingItems.value = items
            if (items.isEmpty()) {
                _error.value = "No continue watching items found"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _error.value = "Error: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun fetchNextUp() {
        try {
            val items = apiService.getNextUp()
            _nextUpItems.value = items
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error fetching next up items: ${e.message}")
        }
    }

    suspend fun fetchRecentlyAddedMovies() {
        try {
            // First, get the default list (includes all accessible libraries)
            val defaultItems = apiService.getRecentlyAddedMovies()
            
            // Also fetch from all libraries individually to ensure we get items from all libraries
            // including "Movies 4K" or any other movie libraries
            // Fetch libraries if not already loaded
            val libraries = if (_libraries.value.isEmpty()) {
                try {
                    val fetchedLibraries = apiService.getLibraries()
                    _libraries.value = fetchedLibraries.filterNot { 
                        it.Type.equals("livetv", ignoreCase = true) || 
                        it.Name.equals("Live TV", ignoreCase = true)
                    }
                    _libraries.value
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                _libraries.value
            }
            
            val allItems = mutableListOf<JellyfinItem>()
            val seenIds = mutableSetOf<String>()
            
            // Add default items first
            defaultItems.forEach { item ->
                if (seenIds.add(item.Id)) {
                    allItems.add(item)
                }
            }
            
            // Fetch from each library that might contain movies
            libraries.forEach { library ->
                try {
                    // Fetch recently added items from this specific library
                    val libraryItems = apiService.getRecentlyAddedMoviesFromLibrary(library.Id)
                    libraryItems.forEach { item ->
                        if (seenIds.add(item.Id)) {
                            allItems.add(item)
                        }
                    }
                } catch (e: Exception) {
                    // Log but continue with other libraries
                    android.util.Log.w("JellyfinRepository", "Error fetching movies from library ${library.Name}: ${e.message}")
                }
            }
            
            // Sort by DateCreated descending and limit to 20 most recent
            val sortedItems = allItems.sortedByDescending { 
                it.DateCreated ?: ""
            }.take(20)
            
            _recentlyAddedMovies.value = sortedItems
        } catch (e: Exception) {
            e.printStackTrace()
            // Don't set error for recently added movies, just log it
            println("Error fetching recently added movies: ${e.message}")
        }
    }

    suspend fun fetchRecentlyReleasedMovies() {
        try {
            // First, get the default list (includes all accessible libraries)
            val defaultItems = apiService.getRecentlyReleasedMovies()
            
            // Also fetch from all libraries individually to ensure we get items from all libraries
            // including "Movies 4K" or any other movie libraries
            // Fetch libraries if not already loaded
            val libraries = if (_libraries.value.isEmpty()) {
                try {
                    val fetchedLibraries = apiService.getLibraries()
                    _libraries.value = fetchedLibraries.filterNot { 
                        it.Type.equals("livetv", ignoreCase = true) || 
                        it.Name.equals("Live TV", ignoreCase = true)
                    }
                    _libraries.value
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                _libraries.value
            }
            
            val allItems = mutableListOf<JellyfinItem>()
            val seenIds = mutableSetOf<String>()
            
            // Add default items first
            defaultItems.forEach { item ->
                if (seenIds.add(item.Id)) {
                    allItems.add(item)
                }
            }
            
            // Fetch from each library that might contain movies
            libraries.forEach { library ->
                try {
                    // Fetch recently released items from this specific library
                    val libraryItems = apiService.getRecentlyReleasedMoviesFromLibrary(library.Id)
                    libraryItems.forEach { item ->
                        if (seenIds.add(item.Id)) {
                            allItems.add(item)
                        }
                    }
                } catch (e: Exception) {
                    // Log but continue with other libraries
                    android.util.Log.w("JellyfinRepository", "Error fetching released movies from library ${library.Name}: ${e.message}")
                }
            }
            
            // Sort by PremiereDate descending and limit to 20 most recent
            val sortedItems = allItems.sortedByDescending { 
                it.PremiereDate ?: ""
            }.take(20)
            
            _recentlyReleasedMovies.value = sortedItems
        } catch (e: Exception) {
            e.printStackTrace()
            // Don't set error for recently released movies, just log it
            println("Error fetching recently released movies: ${e.message}")
        }
    }

    suspend fun fetchRecentlyAddedShows() {
        try {
            val items = apiService.getRecentlyAddedShows()
            _recentlyAddedShows.value = items
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error fetching recently added shows: ${e.message}")
        }
    }

    suspend fun fetchRecentlyAddedEpisodes() {
        try {
            val items = apiService.getRecentlyAddedEpisodes()
            _recentlyAddedEpisodes.value = items
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error fetching recently added episodes: ${e.message}")
        }
    }

    suspend fun fetchLibraries() {
        try {
            val libraries = apiService.getLibraries()
            // Filter out Live TV library
            _libraries.value = libraries.filterNot { 
                it.Type.equals("livetv", ignoreCase = true) || 
                it.Name.equals("Live TV", ignoreCase = true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error fetching libraries: ${e.message}")
        }
    }

    suspend fun fetchLibraryItems(libraryId: String) {
        try {
            // Fetch all library items using pagination
            val items = apiService.getAllLibraryItems(libraryId)
            _libraryItems.update { currentMap ->
                currentMap.toMutableMap().apply {
                    put(libraryId, items)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error fetching library items: ${e.message}")
        }
    }

    /**
     * Check if there is new media available by comparing recently added items.
     * Returns true if new media is detected.
     */
    suspend fun checkForNewMedia(): Boolean {
        return try {
            // Fetch a small sample of the most recent items (first 5 items)
            val recentMovies = apiService.getRecentlyAddedMovies(limit = 5)
            val recentEpisodes = apiService.getRecentlyAddedEpisodes(limit = 5)
            
            // Check if any of the recent items are not in our current lists
            val currentMovieIds = _recentlyAddedMovies.value.map { it.Id }.toSet()
            val currentEpisodeIds = _recentlyAddedEpisodes.value.map { it.Id }.toSet()
            
            val hasNewMovies = recentMovies.any { it.Id !in currentMovieIds }
            val hasNewEpisodes = recentEpisodes.any { it.Id !in currentEpisodeIds }
            
            // Also check if Continue Watching or Next Up changed
            val currentContinueWatchingIds = _continueWatchingItems.value.map { it.Id }.toSet()
            val currentNextUpIds = _nextUpItems.value.map { it.Id }.toSet()
            
            // Fetch fresh Continue Watching and Next Up to compare
            val freshContinueWatching = apiService.getContinueWatching()
            val freshNextUp = apiService.getNextUp()
            
            val hasNewContinueWatching = freshContinueWatching.map { it.Id }.toSet() != currentContinueWatchingIds
            val hasNewNextUp = freshNextUp.map { it.Id }.toSet() != currentNextUpIds
            
            hasNewMovies || hasNewEpisodes || hasNewContinueWatching || hasNewNextUp
        } catch (e: Exception) {
            e.printStackTrace()
            false // On error, assume no new media to avoid unnecessary refreshes
        }
    }

    /**
     * Check for new media and refresh all rows if new content is detected.
     * Does NOT trigger a server-side library scan - only checks for media already detected by Jellyfin's backend tasks.
     * Used by automatic refresh mechanism.
     * Returns true if refresh was performed.
     */
    suspend fun checkForNewMediaAndRefresh(): Boolean {
        val hasNewMedia = checkForNewMedia()
        if (hasNewMedia) {
            // Refresh all rows
            fetchContinueWatching()
            fetchNextUp()
            fetchRecentlyAddedMovies()
            fetchRecentlyReleasedMovies()
            fetchRecentlyAddedShows()
            fetchRecentlyAddedEpisodes()
            return true
        }
        return false
    }

    /**
     * Trigger a server-side library scan and refresh all media rows.
     * This forces the Jellyfin server to scan for new media immediately.
     * Used by manual refresh button.
     * Returns true if refresh was performed.
     */
    suspend fun triggerLibraryScanAndRefresh(): Boolean {
        // Trigger a library scan on the server to detect new media
        try {
            apiService.refreshLibrary()
            android.util.Log.d("JellyfinRepository", "Library scan triggered on server")
            
            // Wait a bit for the server to process the scan and generate images
            // Library scans are asynchronous, and images need time to be generated
            delay(2000) // Wait 2 seconds for scan to start and initial processing
        } catch (e: Exception) {
            android.util.Log.w("JellyfinRepository", "Failed to trigger library scan, continuing with refresh anyway", e)
        }
        
        // After triggering scan, check for new media and refresh
        val hasNewMedia = checkForNewMedia()
        if (hasNewMedia) {
            // Refresh all rows
            fetchContinueWatching()
            fetchNextUp()
            fetchRecentlyAddedMovies()
            fetchRecentlyReleasedMovies()
            fetchRecentlyAddedShows()
            fetchRecentlyAddedEpisodes()
            return true
        }
        
        // Even if no new media detected, still refresh to get updated data (like images)
        fetchContinueWatching()
        fetchNextUp()
        fetchRecentlyAddedMovies()
        fetchRecentlyReleasedMovies()
        fetchRecentlyAddedShows()
        fetchRecentlyAddedEpisodes()
        return false
    }
}






