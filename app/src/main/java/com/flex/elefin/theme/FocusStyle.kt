package com.flex.elefin.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Applies theme-defined focus behavior to a composable
 * Similar to Jellyfin Mobile's focus styles but adapted for TV
 */
fun Modifier.focusStyle(themeConfig: ThemeConfig?): Modifier = composed {
    val focusConfig = themeConfig?.focus ?: ThemeFocus()
    val shapes = themeConfig?.shapes
    
    var isFocused by remember { mutableStateOf(false) }
    
    this
        .onFocusChanged { focusState ->
            isFocused = focusState.isFocused
        }
        .graphicsLayer {
            scaleX = if (isFocused) focusConfig.scale else 1f
            scaleY = if (isFocused) focusConfig.scale else 1f
        }
        .then(
            if (isFocused) {
                border(
                    width = focusConfig.borderWidth,
                    color = focusConfig.borderColor,
                    shape = shapes?.let { RoundedCornerShape(it.cornerRadius) } ?: RoundedCornerShape(8.dp)
                )
            } else {
                Modifier
            }
        )
}

