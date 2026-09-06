package com.flex.elefin.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.flex.elefin.jellyfin.AppSettings

// Pitch black background & surfaces
val JetcasterBackground = Color(0xFF09090B)      // Pitch black / Dark charcoal
val JetcasterSurface = Color(0xFF18181B)         // Clean dark surface
val JetcasterOnBackground = Color(0xFFECEFF1)    // Crisp soft white
val JetcasterOnSurface = Color(0xFFF8F9FA)       // High contrast white
val JetcasterSurfaceVariant = Color(0xFF27272A)  // Charcoal accent

// Dynamic primary theme color state (defaults to White, updated on app start / settings change)
var JetcasterPrimaryColorState by mutableStateOf(Color(0xFFFFFFFF))

val JetcasterPrimary: Color
    get() = JetcasterPrimaryColorState

val JetcasterSecondary: Color
    get() = JetcasterPrimaryColorState

val JetcasterTertiary: Color
    get() = JetcasterPrimaryColorState

@Composable
fun JetcasterMobileTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }

    // Sync the dynamic state color from settings on start / preference changes
    LaunchedEffect(settings.themeColorHex) {
        val hex = settings.themeColorHex
        try {
            JetcasterPrimaryColorState = Color(android.graphics.Color.parseColor(hex))
        } catch (e: Exception) {
            JetcasterPrimaryColorState = Color.White
        }
    }

    val onPrimaryColor = remember(JetcasterPrimaryColorState) {
        if (ColorUtils.calculateLuminance(JetcasterPrimaryColorState.toArgb()) < 0.45) {
            Color.White
        } else {
            Color(0xFF09090B)
        }
    }

    val dynamicColorScheme = remember(JetcasterPrimaryColorState, onPrimaryColor) {
        darkColorScheme(
            primary = JetcasterPrimaryColorState,
            onPrimary = onPrimaryColor,
            primaryContainer = Color(0xFF27272A),
            onPrimaryContainer = Color.White,
            secondary = JetcasterPrimaryColorState,
            onSecondary = onPrimaryColor,
            secondaryContainer = Color(0xFF27272A),
            onSecondaryContainer = Color.White,
            tertiary = JetcasterPrimaryColorState,
            onTertiary = onPrimaryColor,
            tertiaryContainer = Color(0xFF27272A),
            onTertiaryContainer = Color.White,
            background = JetcasterBackground,
            onBackground = JetcasterOnBackground,
            surface = JetcasterSurface,
            onSurface = JetcasterOnSurface,
            surfaceVariant = JetcasterSurfaceVariant,
            onSurfaceVariant = Color(0xFFB0B0B5),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6)
        )
    }

    MaterialTheme(
        colorScheme = dynamicColorScheme,
        content = content
    )
}
