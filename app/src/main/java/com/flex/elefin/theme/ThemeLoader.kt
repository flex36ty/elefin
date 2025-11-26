package com.flex.elefin.theme

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads theme from Jellyfin server using official endpoints:
 * - /Branding/CustomCss.css (CSS variables)
 * - /Branding/Configuration (branding JSON)
 */
class ThemeLoader(
    private val baseUrl: String,
    private val accessToken: String
) {
    private val client = HttpClient(Android)

    /**
     * Fetches and parses theme from Jellyfin server
     * Uses /Branding/CustomCss.css to extract CSS variables
     */
    suspend fun loadThemeFromServer(): ThemeConfig? {
        return try {
            withContext(Dispatchers.IO) {
                // Fetch CustomCss.css
                val cssUrl = if (baseUrl.endsWith("/")) {
                    "${baseUrl}Branding/CustomCss.css"
                } else {
                    "$baseUrl/Branding/CustomCss.css"
                }

                android.util.Log.d("ThemeLoader", "Fetching CSS from: $cssUrl")
                
                val cssResponse: HttpResponse = client.get(cssUrl) {
                    header(HttpHeaders.Authorization, "MediaBrowser Token=\"$accessToken\"")
                    header("X-Emby-Authorization", "MediaBrowser Client=\"Android TV\", Device=\"Android TV\", DeviceId=\"\", Version=\"1.0.0\"")
                }
                
                val cssStatus = cssResponse.status
                android.util.Log.d("ThemeLoader", "CSS response status: $cssStatus")
                
                if (cssStatus != HttpStatusCode.OK) {
                    android.util.Log.d("ThemeLoader", "CustomCss.css not available (status $cssStatus). Using default theme.")
                    return@withContext null
                }
                
                var cssText = cssResponse.body<String>()
                
                if (cssText.isBlank()) {
                    android.util.Log.d("ThemeLoader", "CustomCss.css is empty. Using default theme.")
                    return@withContext null
                }
                
                android.util.Log.d("ThemeLoader", "Received CustomCss.css (${cssText.length} characters)")
                
                // Check for @import statements and fetch imported CSS
                cssText = handleCssImports(cssText)
                
                if (cssText.isBlank()) {
                    android.util.Log.d("ThemeLoader", "No CSS content after processing imports. Using default theme.")
                    return@withContext null
                }
                
                android.util.Log.d("ThemeLoader", "CSS ready for parsing (${cssText.length} characters)")
                
                // Parse CSS variables and create theme config
                parseCssTheme(cssText)
            }
        } catch (e: Exception) {
            android.util.Log.e("ThemeLoader", "Failed to fetch theme from server: ${e.message}", e)
            null
        }
    }

    /**
     * Handles @import statements in CSS
     * Fetches imported CSS files from URLs and combines them
     */
    private suspend fun handleCssImports(cssText: String): String {
        // Match @import url("...") or @import "..." formats
        val importRegex = Regex("""@import\s+(?:url\()?["']([^"']+)["']\)?\s*;""", RegexOption.IGNORE_CASE)
        val imports = importRegex.findAll(cssText)
        
        if (!imports.any()) {
            android.util.Log.d("ThemeLoader", "No @import statements found, using CSS as-is")
            return cssText
        }
        
        var combinedCss = StringBuilder()
        
        imports.forEach { matchResult ->
            val importUrl = matchResult.groupValues[1].trim()
            android.util.Log.d("ThemeLoader", "Found @import: $importUrl")
            
            try {
                // Fetch the imported CSS file
                val importedResponse: HttpResponse = client.get(importUrl)
                val importedStatus = importedResponse.status
                
                if (importedStatus == HttpStatusCode.OK) {
                    val importedCss = importedResponse.body<String>()
                    if (importedCss.isNotBlank()) {
                        android.util.Log.d("ThemeLoader", "Successfully fetched imported CSS from $importUrl (${importedCss.length} characters)")
                        combinedCss.append(importedCss)
                        combinedCss.append("\n")
                    } else {
                        android.util.Log.w("ThemeLoader", "Imported CSS from $importUrl is empty")
                    }
                } else {
                    android.util.Log.w("ThemeLoader", "Failed to fetch imported CSS from $importUrl: status $importedStatus")
                }
            } catch (e: Exception) {
                android.util.Log.e("ThemeLoader", "Error fetching imported CSS from $importUrl: ${e.message}", e)
            }
        }
        
        // If we successfully fetched imported CSS, return it (may contain variables)
        // Otherwise, return original CSS
        return if (combinedCss.isNotEmpty()) {
            android.util.Log.d("ThemeLoader", "Combined imported CSS (${combinedCss.length} characters)")
            combinedCss.toString()
        } else {
            android.util.Log.d("ThemeLoader", "No imported CSS fetched, using original CSS")
            cssText
        }
    }

    /**
     * Parses CSS text to extract CSS custom properties (variables)
     * Maps common Jellyfin CSS variables to Material3 colors
     */
    private fun parseCssTheme(cssText: String): ThemeConfig? {
        return try {
            // Extract CSS variables using regex
            val cssVariables = extractCssVariables(cssText)
            
            if (cssVariables.isEmpty()) {
                android.util.Log.d("ThemeLoader", "No CSS variables found in CustomCss.css")
                return null
            }
            
            android.util.Log.d("ThemeLoader", "Found ${cssVariables.size} CSS variables")
            
            // Map CSS variables to theme colors
            val colors = mapCssVariablesToColors(cssVariables)
            
            // Extract corner radius if available
            val cornerRadius = cssVariables["--corner-radius"]?.let { value ->
                try {
                    value.replace("px", "").replace("rem", "").trim().toIntOrNull()?.dp
                } catch (e: Exception) {
                    null
                }
            } ?: 8.dp
            
            val shapes = ThemeShapes(cornerRadius = cornerRadius)
            
            // Create focus config (can be customized via CSS later)
            val focus = ThemeFocus(
                scale = 1.10f,
                borderColor = colors.primary,
                borderWidth = 3.dp
            )
            
            ThemeConfig(
                colors = colors,
                shapes = shapes,
                typography = null, // CSS doesn't provide font info typically
                focus = focus
            )
        } catch (e: Exception) {
            android.util.Log.e("ThemeLoader", "Failed to parse CSS theme: ${e.message}", e)
            null
        }
    }

    /**
     * Extracts CSS custom properties from CSS text
     * Returns a map of variable names to their values
     */
    private fun extractCssVariables(cssText: String): Map<String, String> {
        val variables = mutableMapOf<String, String>()
        
        // Regex to match CSS custom properties: --variable-name: value;
        val regex = Regex("--([a-zA-Z0-9-]+)\\s*:\\s*([^;]+);")
        
        regex.findAll(cssText).forEach { matchResult ->
            val variableName = matchResult.groupValues[1]
            val variableValue = matchResult.groupValues[2].trim()
            variables[variableName] = variableValue
        }
        
        return variables
    }

    /**
     * Maps CSS variables to Material3 colors
     * Supports common Jellyfin CSS variable names
     */
    private fun mapCssVariablesToColors(variables: Map<String, String>): ThemeColors {
        // Helper to parse color from CSS value (returns nullable)
        fun parseColorOrNull(varName: String): Color? {
            val value = variables[varName] ?: return null
            return parseCssColor(value)
        }
        
        // Helper to parse color with fallback
        fun parseColorWithFallback(varNames: List<String>, defaultColor: Color): Color {
            for (varName in varNames) {
                parseColorOrNull(varName)?.let { return it }
            }
            return defaultColor
        }
        
        // Common Jellyfin CSS variable mappings
        // Primary color variations
        val primary = parseColorWithFallback(
            listOf("--primary-color", "--theme-primary-color", "--accent-color"),
            Color(0xFF5E44D3)
        )
        
        val primaryVariant = parseColorOrNull("--primary-variant-color")
            ?: parseColorOrNull("--theme-primary-variant")
        
        // Background colors
        val background = parseColorWithFallback(
            listOf("--background-color", "--theme-background-color", "--bg-color"),
            Color(0xFF000000)
        )
        
        val surface = parseColorWithFallback(
            listOf("--surface-color", "--card-background-color", "--card-bg-color"),
            background.copy(alpha = 0.9f)
        )
        
        // Text colors
        val onPrimary = parseColorWithFallback(
            listOf("--on-primary-color", "--primary-text-color"),
            Color(0xFFFFFFFF)
        )
        
        val onBackground = parseColorWithFallback(
            listOf("--text-color", "--on-background-color", "--theme-text-color"),
            Color(0xFFFFFFFF)
        )
        
        val onSurface = parseColorOrNull("--on-surface-color") ?: onBackground
        
        return ThemeColors(
            primary = primary,
            primaryVariant = primaryVariant,
            onPrimary = onPrimary,
            background = background,
            surface = surface,
            onBackground = onBackground,
            onSurface = onSurface,
            error = parseColorOrNull("--error-color"),
            onError = parseColorOrNull("--on-error-color")
        )
    }

    /**
     * Parses CSS color value to Android Color
     * Supports hex (#rgb, #rrggbb), rgb(), rgba(), hsl(), named colors
     */
    private fun parseCssColor(cssValue: String): Color? {
        if (cssValue.isBlank()) return null
        
        val trimmed = cssValue.trim().lowercase()
        
        return try {
            when {
                // Hex colors: #rgb or #rrggbb
                trimmed.startsWith("#") -> {
                    val hex = trimmed.removePrefix("#")
                    when (hex.length) {
                        3 -> {
                            // Expand #rgb to #rrggbb
                            val expanded = hex.map { "$it$it" }.joinToString("")
                            Color(AndroidColor.parseColor("#$expanded"))
                        }
                        6 -> Color(AndroidColor.parseColor("#$hex"))
                        8 -> {
                            // #rrggbbaa format
                            Color(AndroidColor.parseColor("#$hex"))
                        }
                        else -> null
                    }
                }
                
                // rgb() or rgba()
                trimmed.startsWith("rgb") -> {
                    val rgbRegex = Regex("rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*([\\d.]+))?\\s*\\)")
                    rgbRegex.find(trimmed)?.let { match ->
                        val r = match.groupValues[1].toInt()
                        val g = match.groupValues[2].toInt()
                        val b = match.groupValues[3].toInt()
                        val alpha = match.groupValues.getOrNull(4)?.toFloatOrNull() ?: 1.0f
                        Color(
                            red = r / 255f,
                            green = g / 255f,
                            blue = b / 255f,
                            alpha = alpha
                        )
                    }
                }
                
                // hsl() or hsla()
                trimmed.startsWith("hsl") -> {
                    // Simple HSL parsing (can be enhanced)
                    // For now, return null and fall back to defaults
                    null
                }
                
                // Named colors (basic support)
                else -> {
                    when (trimmed) {
                        "white" -> Color.White
                        "black" -> Color.Black
                        "red" -> Color.Red
                        "green" -> Color.Green
                        "blue" -> Color.Blue
                        else -> {
                            // Try parsing as hex without #
                            try {
                                Color(AndroidColor.parseColor(trimmed))
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ThemeLoader", "Failed to parse CSS color: $cssValue - ${e.message}")
            null
        }
    }

    fun close() {
        client.close()
    }
}
