package com.flex.elefin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.darkColorScheme
import com.flex.elefin.jellyfin.AppSettings
import com.flex.elefin.jellyfin.JellyfinConfig
import com.flex.elefin.theme.ThemeConfig
import com.flex.elefin.theme.ThemeLoader
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import android.view.KeyEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun JellyfinAppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val settings = remember { AppSettings(context) }
    val config = remember { JellyfinConfig(context) }
    
    // Sync navigation sounds setting with system view
    LaunchedEffect(settings.navigationSoundsEnabled) {
        view.isSoundEffectsEnabled = settings.navigationSoundsEnabled
    }
    
    // Default theme values
    val defaultDarkColorScheme = darkColorScheme(
        primary = Color(0xFF5E44D3),
        background = Color(0xFF000000),
        surface = Color(0xFF1C1B1F),
        onPrimary = Color(0xFFFFFFFF),
        onBackground = Color(0xFFFFFFFF),
        onSurface = Color(0xFFFFFFFF)
    )
    
    var themeConfig by remember { mutableStateOf<ThemeConfig?>(null) }
    
    // Load remote theme if enabled
    LaunchedEffect(settings.remoteThemingEnabled, config.isConfigured()) {
        if (settings.remoteThemingEnabled && config.isConfigured()) {
            try {
                val loader = ThemeLoader(
                    baseUrl = config.serverUrl,
                    accessToken = config.accessToken
                )
                val loadedTheme = loader.loadThemeFromServer()
                if (loadedTheme != null) {
                    themeConfig = loadedTheme
                }
                loader.close()
            } catch (e: Exception) {
                android.util.Log.e("JellyfinAppTheme", "Failed to load remote theme: ${e.message}", e)
                // On error, use default theme
                themeConfig = null
            }
        } else {
            themeConfig = null
        }
    }
    
    // Use remote theme colors if available, otherwise use defaults
    val colorScheme = if (themeConfig != null) {
        darkColorScheme(
            primary = themeConfig!!.colors.primary,
            background = themeConfig!!.colors.background,
            surface = themeConfig!!.colors.surface,
            onPrimary = themeConfig!!.colors.onPrimary,
            onBackground = themeConfig!!.colors.onBackground,
            onSurface = themeConfig!!.colors.onSurface
        )
    } else {
        defaultDarkColorScheme
    }
    
    // Use remote theme shapes if available
    val shapes = if (themeConfig?.shapes != null) {
        Shapes(
            medium = androidx.compose.foundation.shape.RoundedCornerShape(themeConfig!!.shapes!!.cornerRadius)
        )
    } else {
        Shapes()
    }

    // Wrap FocusManager to play sounds
    val focusManager = LocalFocusManager.current
    val soundFocusManager = remember(focusManager, view, settings) {
        object : FocusManager by focusManager {
            override fun moveFocus(focusDirection: FocusDirection): Boolean {
                val success = focusManager.moveFocus(focusDirection)
                if (success && settings.navigationSoundsEnabled) {
                    val soundConstant = when (focusDirection) {
                        FocusDirection.Left -> SoundEffectConstants.NAVIGATION_LEFT
                        FocusDirection.Right -> SoundEffectConstants.NAVIGATION_RIGHT
                        FocusDirection.Up -> SoundEffectConstants.NAVIGATION_UP
                        FocusDirection.Down -> SoundEffectConstants.NAVIGATION_DOWN
                        else -> SoundEffectConstants.NAVIGATION_UP
                    }
                    view.playSoundEffect(soundConstant)
                }
                return success
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = shapes,
        typography = AppTypography
    ) {
        CompositionLocalProvider(LocalFocusManager provides soundFocusManager) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { keyEvent ->
                        if (settings.navigationSoundsEnabled && 
                            keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                    view.playSoundEffect(SoundEffectConstants.CLICK)
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    view.playSoundEffect(SoundEffectConstants.NAVIGATION_UP)
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    view.playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT)
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    view.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT)
                                }
                            }
                        }
                        false
                    }
            ) {
                content()
            }
        }
    }
}





