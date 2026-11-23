package com.flex.elefin.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

object HomeStyle {
    val shelfInset = 16.dp
    val shelfGap = 20.dp
    val shelfVerticalPadding = 12.dp
    val cardShape = RoundedCornerShape(12.dp)
    val badgeShape = RoundedCornerShape(4.dp)
    val progressHeight = 3.dp
    val badgeBackground = Color(0xDD101114)
    val secondaryText = Color(0xFFBFC0C5)
}

/** Draw-only focus animation: no card measurement or row spacing changes. */
fun Modifier.homeCardFocus(reduceMotion: Boolean) = composed {
    var focused by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(
        if (focused && !reduceMotion) 1.045f else 1f,
        tween(if (reduceMotion) 0 else 140), label = "home_card_focus")
    this.onFocusChanged { focused = it.hasFocus }
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .then(if (focused) Modifier.border(2.dp, Color.White, HomeStyle.cardShape) else Modifier)
}
