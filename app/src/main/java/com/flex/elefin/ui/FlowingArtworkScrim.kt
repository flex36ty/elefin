package com.flex.elefin.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Dissolve the artwork tone in place without a directional edge or extra rendering layers. */
@Composable
fun Modifier.flowingArtworkScrim(target: Color, disabled: Boolean): Modifier {
    // Retarget from the currently displayed color when focus changes during a dissolve.
    val animatedTone = animateColorAsState(
        targetValue = target,
        animationSpec = tween(if (disabled) 0 else 1860, easing = FastOutSlowInEasing),
        label = "artwork_color_dissolve")
    return drawWithCache {
        val tone = animatedTone.value
        val scrim = Brush.verticalGradient(
            0f to tone.copy(alpha = 0.04f),
            0.24f to tone.copy(alpha = 0.15f),
            0.46f to tone.copy(alpha = 0.90f),
            0.66f to tone,
            1f to tone,
            endY = size.height)
        onDrawWithContent {
            drawContent()
            drawRect(scrim)
        }
    }
}
