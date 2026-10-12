package com.flex.elefin.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

internal class DrawerFocusHighlight {
    var focusedId by mutableStateOf<String?>(null)
    var target by mutableStateOf(Rect.Zero)
    var corner by mutableStateOf(22.dp)
}

/** One drawn shape moves between targets; focus and layout themselves remain immediate. */
@Composable
internal fun Modifier.drawerFocusHighlight(highlight: DrawerFocusHighlight, expanded: Boolean, animate: Boolean): Modifier {
    val position = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var wasExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(highlight.target, expanded, animate) {
        if (!animate || !expanded || !wasExpanded || position.value == Rect.Zero) {
            position.snapTo(highlight.target)
        } else {
            position.animateTo(highlight.target, tween(190, easing = FastOutSlowInEasing))
        }
        wasExpanded = expanded
    }
    return onGloballyPositioned { origin = it.positionInRoot() }.drawBehind {
        if (expanded && highlight.focusedId != null) {
            val rect = position.value
            drawRoundRect(
                color = Color(0xFFE7E2EA),
                topLeft = rect.topLeft - origin,
                size = rect.size,
                cornerRadius = CornerRadius(highlight.corner.toPx())
            )
        }
    }
}

@Composable
internal fun Modifier.drawerFocusTarget(highlight: DrawerFocusHighlight, id: String, corner: Dp = 22.dp): Modifier {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    return onGloballyPositioned {
        bounds = Rect(it.positionInRoot(), it.size.toSize())
        if (highlight.focusedId == id) highlight.target = bounds
    }.onFocusChanged {
        if (it.hasFocus) {
            highlight.focusedId = id
            highlight.target = bounds
            highlight.corner = corner
        } else if (highlight.focusedId == id) {
            highlight.focusedId = null
        }
    }
}
