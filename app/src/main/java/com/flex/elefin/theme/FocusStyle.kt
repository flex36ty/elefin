package com.flex.elefin.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Applies theme-defined focus behavior to a composable
 * Adapted for TV & touch focus states with smooth spring scaling & halo glow
 */
fun Modifier.focusStyle(themeConfig: ThemeConfig?): Modifier = composed {
    val focusConfig = themeConfig?.focus ?: ThemeFocus()
    val shapes = themeConfig?.shapes
    
    var isFocused by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) focusConfig.scale else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "focusScale"
    )
    
    this
        .onFocusChanged { focusState ->
            isFocused = focusState.isFocused
        }
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
            shadowElevation = if (isFocused) 16f else 0f
            spotShadowColor = if (isFocused) focusConfig.borderColor.copy(alpha = 0.6f) else Color.Transparent
            ambientShadowColor = if (isFocused) focusConfig.borderColor.copy(alpha = 0.4f) else Color.Transparent
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

