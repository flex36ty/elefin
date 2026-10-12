package com.flex.elefin.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier

/** Keyed lazy items animate only dataset changes, without animating normal scrolling. */
fun LazyItemScope.shelfUpdateModifier(enabled: Boolean): Modifier =
    if (enabled) Modifier.animateItem(
        fadeInSpec = tween(220),
        placementSpec = tween(280, easing = FastOutSlowInEasing),
        fadeOutSpec = null
    ) else Modifier
