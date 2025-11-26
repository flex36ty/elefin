package com.flex.elefin.screens

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatCurrentTime(use24Hour: Boolean): String {
    val pattern = if (use24Hour) "HH:mm" else "h:mm a"
    val formatter = SimpleDateFormat(pattern, Locale.getDefault())
    return formatter.format(Date())
}

/**
 * Format runtime from ticks to "1h 30m" format
 * @param runTimeTicks Runtime in ticks (10,000,000 ticks = 1 second)
 * @return Formatted string like "1h 30m" or "45m" or empty string if null
 */
fun formatRuntime(runTimeTicks: Long?): String {
    if (runTimeTicks == null) return ""
    
    val totalMinutes = runTimeTicks / 10_000_000 / 60
    if (totalMinutes <= 0) return ""
    
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> ""
    }
}

/**
 * Format date from ISO string to "Nov 1, 2025" format
 * @param dateString ISO date string (e.g., "2025-11-01T12:00:00Z")
 * @return Formatted string like "Nov 1, 2025" or empty string if null/invalid
 */
fun formatDate(dateString: String?): String {
    if (dateString.isNullOrBlank()) return ""
    
    return try {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
        )
        
        val parsedDate = formats.firstNotNullOfOrNull { format ->
            try {
                format.parse(dateString)
            } catch (e: Exception) {
                null
            }
        }
        
        parsedDate?.let {
            SimpleDateFormat("MMM d, yyyy", Locale.US).format(it)
        } ?: ""
    } catch (e: Exception) {
        ""
    }
}

