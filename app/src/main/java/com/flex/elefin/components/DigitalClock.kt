package com.flex.elefin.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * A digital clock component that displays the current time and updates every second.
 * Styled to match the refined navigation bars.
 */
@Composable
fun DigitalClock(
    modifier: Modifier = Modifier,
    use24HourFormat: Boolean = false
) {
    var currentTime by remember { mutableStateOf(LocalTime.now().truncatedTo(ChronoUnit.MINUTES)) }

    LaunchedEffect(Unit) {
        while (true) {
            // Poll for clock adjustments, but only invalidate UI when the displayed minute changes.
            currentTime = LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
            delay(1000)
        }
    }

    val pattern = if (use24HourFormat) "HH:mm" else "h:mm a"
    val locale = Locale.getDefault()
    val formatter = remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
    val timeString = currentTime.format(formatter)
    
    // Match the 30% reduction scaling used in navigation tabs (1.17 * 0.7 = 0.819f)
    val scaledFontSize = MaterialTheme.typography.labelLarge.fontSize * 0.82f

    Text(
        text = timeString,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = scaledFontSize
        ),
        color = Color.White,
        modifier = modifier.padding(end = 38.dp) // Match the 38dp start padding for balance
    )
}
