package com.flex.elefin.music.player

import android.util.Log
import com.flex.elefin.music.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AudioQueueManager"

/**
 * Manages the music playback queue.
 * Singleton object that holds the current playlist and playback position.
 */
object AudioQueueManager {

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    // Original queue order (for shuffle toggle)
    private var originalQueue: List<Track> = emptyList()

    enum class RepeatMode {
        OFF,      // No repeat
        ALL,      // Repeat entire queue
        ONE       // Repeat current track
    }

    /**
     * Set a new queue and start from the specified index
     */
    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        Log.d(TAG, "Setting queue with ${tracks.size} tracks, starting at index $startIndex")
        originalQueue = tracks
        _queue.value = tracks
        _currentIndex.value = startIndex.coerceIn(0, tracks.size - 1)
        _currentTrack.value = tracks.getOrNull(_currentIndex.value)
    }

    /**
     * Add tracks to the end of the queue
     */
    fun addToQueue(tracks: List<Track>) {
        Log.d(TAG, "Adding ${tracks.size} tracks to queue")
        val newQueue = _queue.value + tracks
        originalQueue = originalQueue + tracks
        _queue.value = newQueue
    }

    /**
     * Add a single track to play next
     */
    fun playNext(track: Track) {
        Log.d(TAG, "Adding track to play next: ${track.name}")
        val currentQueue = _queue.value.toMutableList()
        val insertIndex = (_currentIndex.value + 1).coerceAtMost(currentQueue.size)
        currentQueue.add(insertIndex, track)
        _queue.value = currentQueue
        
        // Also update original queue
        val originalMutable = originalQueue.toMutableList()
        originalMutable.add(insertIndex, track)
        originalQueue = originalMutable
    }

    /**
     * Get the current track
     */
    fun current(): Track? = _currentTrack.value

    /**
     * Move to the next track
     * @return The next track, or null if at end of queue (and repeat is off)
     */
    fun next(): Track? {
        val queue = _queue.value
        if (queue.isEmpty()) return null

        return when (_repeatMode.value) {
            RepeatMode.ONE -> {
                // Stay on current track
                _currentTrack.value
            }
            RepeatMode.ALL -> {
                val nextIndex = (_currentIndex.value + 1) % queue.size
                _currentIndex.value = nextIndex
                _currentTrack.value = queue[nextIndex]
                Log.d(TAG, "Next track (repeat all): ${_currentTrack.value?.name}")
                _currentTrack.value
            }
            RepeatMode.OFF -> {
                if (_currentIndex.value + 1 < queue.size) {
                    _currentIndex.value = _currentIndex.value + 1
                    _currentTrack.value = queue[_currentIndex.value]
                    Log.d(TAG, "Next track: ${_currentTrack.value?.name}")
                    _currentTrack.value
                } else {
                    Log.d(TAG, "End of queue reached")
                    null
                }
            }
        }
    }

    /**
     * Move to the previous track
     * @return The previous track, or null if at start of queue
     */
    fun previous(): Track? {
        val queue = _queue.value
        if (queue.isEmpty()) return null

        return when (_repeatMode.value) {
            RepeatMode.ONE -> {
                // Stay on current track
                _currentTrack.value
            }
            RepeatMode.ALL -> {
                val prevIndex = if (_currentIndex.value - 1 < 0) queue.size - 1 else _currentIndex.value - 1
                _currentIndex.value = prevIndex
                _currentTrack.value = queue[prevIndex]
                Log.d(TAG, "Previous track (repeat all): ${_currentTrack.value?.name}")
                _currentTrack.value
            }
            RepeatMode.OFF -> {
                if (_currentIndex.value - 1 >= 0) {
                    _currentIndex.value = _currentIndex.value - 1
                    _currentTrack.value = queue[_currentIndex.value]
                    Log.d(TAG, "Previous track: ${_currentTrack.value?.name}")
                    _currentTrack.value
                } else {
                    Log.d(TAG, "Start of queue reached")
                    null
                }
            }
        }
    }

    /**
     * Skip to a specific index in the queue
     */
    fun skipTo(index: Int): Track? {
        val queue = _queue.value
        if (index < 0 || index >= queue.size) return null

        _currentIndex.value = index
        _currentTrack.value = queue[index]
        Log.d(TAG, "Skipped to track $index: ${_currentTrack.value?.name}")
        return _currentTrack.value
    }

    /**
     * Toggle shuffle mode
     */
    fun toggleShuffle() {
        _shuffleEnabled.value = !_shuffleEnabled.value
        Log.d(TAG, "Shuffle ${if (_shuffleEnabled.value) "enabled" else "disabled"}")

        if (_shuffleEnabled.value) {
            // Shuffle the queue, keeping current track at current position
            val currentTrack = _currentTrack.value
            val shuffled = _queue.value.toMutableList()
            shuffled.shuffle()
            
            // Move current track to current index
            if (currentTrack != null) {
                shuffled.remove(currentTrack)
                shuffled.add(_currentIndex.value, currentTrack)
            }
            
            _queue.value = shuffled
        } else {
            // Restore original order
            val currentTrack = _currentTrack.value
            _queue.value = originalQueue
            
            // Find current track in original queue
            if (currentTrack != null) {
                val newIndex = originalQueue.indexOfFirst { it.id == currentTrack.id }
                if (newIndex >= 0) {
                    _currentIndex.value = newIndex
                }
            }
        }
    }

    /**
     * Cycle through repeat modes: OFF -> ALL -> ONE -> OFF
     */
    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        Log.d(TAG, "Repeat mode: ${_repeatMode.value}")
    }

    /**
     * Set specific repeat mode
     */
    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
        Log.d(TAG, "Repeat mode set to: $mode")
    }

    /**
     * Remove a track from the queue by index
     */
    fun removeFromQueue(index: Int) {
        if (index < 0 || index >= _queue.value.size) return

        val newQueue = _queue.value.toMutableList()
        newQueue.removeAt(index)
        _queue.value = newQueue

        // Adjust current index if needed
        if (index < _currentIndex.value) {
            _currentIndex.value = _currentIndex.value - 1
        } else if (index == _currentIndex.value && _currentIndex.value >= newQueue.size) {
            _currentIndex.value = (newQueue.size - 1).coerceAtLeast(0)
            _currentTrack.value = newQueue.getOrNull(_currentIndex.value)
        }

        Log.d(TAG, "Removed track at index $index from queue")
    }

    /**
     * Clear the entire queue
     */
    fun clearQueue() {
        Log.d(TAG, "Clearing queue")
        _queue.value = emptyList()
        originalQueue = emptyList()
        _currentIndex.value = 0
        _currentTrack.value = null
    }

    /**
     * Check if there's a next track available
     */
    fun hasNext(): Boolean {
        return when (_repeatMode.value) {
            RepeatMode.OFF -> _currentIndex.value + 1 < _queue.value.size
            RepeatMode.ALL, RepeatMode.ONE -> _queue.value.isNotEmpty()
        }
    }

    /**
     * Check if there's a previous track available
     */
    fun hasPrevious(): Boolean {
        return when (_repeatMode.value) {
            RepeatMode.OFF -> _currentIndex.value > 0
            RepeatMode.ALL, RepeatMode.ONE -> _queue.value.isNotEmpty()
        }
    }

    /**
     * Get queue size
     */
    fun queueSize(): Int = _queue.value.size
}

