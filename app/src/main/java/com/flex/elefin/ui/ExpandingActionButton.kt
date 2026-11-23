package com.flex.elefin.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Clip the animated bounds, so both capsule ends stay rounded while the label expands. */
fun Modifier.expandingActionButton(focused: Boolean, expandLeft: Boolean = false): Modifier =
    background(if (focused) Color.White else Color.White.copy(alpha = 0.15f), CircleShape)
        .clip(CircleShape)
        .animateContentSize(
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            alignment = if (expandLeft) Alignment.CenterEnd else Alignment.CenterStart
        )
        .then(
            if (focused) Modifier.height(28.dp).wrapContentWidth()
            else Modifier.size(28.dp)
        )
