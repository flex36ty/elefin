package com.flex.elefin.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.animateLottieCompositionAsState

/**
 * Animated play button with Lottie glow effect and icon morphing.
 * Can be used for both Play and Resume buttons.
 */
@Composable
fun AnimatedPlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Play",
    icon: ImageVector = Icons.Default.PlayArrow,
    size: androidx.compose.ui.unit.Dp = 85.dp, // Match current button size (100 * 0.85)
    iconSize: androidx.compose.ui.unit.Dp = 20.4.dp, // Match current icon size
    showLabel: Boolean = true,
    labelTextStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge.copy(
        fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f
    ),
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimary,
    glowAnimationResId: Int? = null // R.raw.button_glow - pass when Lottie file is added
) {
    var focused by remember { mutableStateOf(false) }
    
    // Lottie glow animation composition (optional - will skip if resource not found)
    val glowComposition by rememberLottieComposition(
        spec = if (glowAnimationResId != null && glowAnimationResId != 0) {
            LottieCompositionSpec.RawRes(glowAnimationResId)
        } else {
            LottieCompositionSpec.RawRes(0) // Will return null gracefully
        }
    )
    
    // Animate glow based on focus (only if composition is loaded)
    val glowProgress by animateLottieCompositionAsState(
        composition = glowComposition,
        iterations = if (glowComposition != null) LottieConstants.IterateForever else 1,
        isPlaying = focused && glowComposition != null,
        restartOnPlay = true
    )
    
    // Icon scale animation on focus
    val iconScale by animateFloatAsState(
        targetValue = if (focused) 1.1f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "iconScale"
    )
    
    // Box with glow animation and icon
    Box(
        modifier = modifier
            .size(size)
            .background(containerColor, androidx.compose.foundation.shape.CircleShape)
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onFocusChanged { focusState ->
                focused = focusState.isFocused
            }
    ) {
        // Glow expanding circle (Lottie) - only show if composition loaded
        if (glowComposition != null) {
            LottieAnimation(
                composition = glowComposition,
                progress = { glowProgress },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Icon with morph animation
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                tint = contentColor
            )
            
            // Show label when focused
            if (focused && showLabel) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = labelTextStyle,
                    color = contentColor,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    }
}
