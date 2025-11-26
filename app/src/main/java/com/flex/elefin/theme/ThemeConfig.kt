package com.flex.elefin.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Theme configuration loaded from Jellyfin server
 * Extracted from /Branding/CustomCss.css CSS variables
 */
data class ThemeConfig(
    val colors: ThemeColors,
    val shapes: ThemeShapes? = null,
    val typography: ThemeTypography? = null,
    val focus: ThemeFocus? = null
)

data class ThemeColors(
    val primary: Color,
    val primaryVariant: Color? = null,
    val onPrimary: Color,
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val onSurface: Color,
    val error: Color? = null,
    val onError: Color? = null
)

data class ThemeShapes(
    val cornerRadius: Dp = 8.dp
)

data class ThemeTypography(
    val bodyFont: String = "Roboto",
    val headerFont: String = "Montserrat"
)

data class ThemeFocus(
    val scale: Float = 1.10f,
    val borderColor: Color = Color(0xFF4C9BE8),
    val borderWidth: Dp = 3.dp
)


