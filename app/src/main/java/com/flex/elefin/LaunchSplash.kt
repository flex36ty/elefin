package com.flex.elefin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay

/** Home loads underneath; the system starting window alone vanishes on the first frame. */
@Composable
internal fun LaunchSplash(enabled: Boolean, content: @Composable () -> Unit) {
    var visible by rememberSaveable { mutableStateOf(enabled) }
    LaunchedEffect(Unit) {
        delay(1500)
        visible = false
    }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { visible }) {
        content()
        AnimatedVisibility(visible = visible, exit = fadeOut(tween(220))) {
            Image(
                painter = painterResource(R.drawable.elefin_splash),
                contentDescription = "Elefin",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().background(Color(0xFF00191C))
            )
        }
    }
}
