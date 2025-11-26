package com.flex.elefin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.darkColorScheme
import com.flex.elefin.jellyfin.AppSettings
import com.flex.elefin.jellyfin.JellyfinConfig
import com.flex.elefin.theme.ThemeConfig
import com.flex.elefin.theme.ThemeLoader

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun JellyfinAppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val config = remember { JellyfinConfig(context) }
    
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

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = shapes,
        content = content
    )
}





