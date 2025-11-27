package com.flex.elefin.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.AppSettings

/**
 * Composable that displays either the title text or logo image based on settings
 */
@Composable
fun TitleOrLogo(
    item: JellyfinItem,
    apiService: JellyfinApiService?,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val useLogo = settings.useLogoForTitle
    val logoTag = item.ImageTags?.get("Logo")
    
    if (useLogo && logoTag != null && apiService != null) {
        // Show logo image - maintain same height as text would be, left-aligned
        // Calculate approximate text height: fontSize * line height multiplier (typically 1.2)
        val fontSizeValue = style.fontSize.value
        val logoHeight = (fontSizeValue * 1.2f).dp // Slightly taller than text for better visibility
        
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(apiService.getImageUrl(item.Id, "Logo", logoTag))
                    .headers(apiService.getImageRequestHeaders())
                    .build(),
                contentDescription = item.Name,
                modifier = Modifier
                    .height(logoHeight)
                    .wrapContentWidth(),
                contentScale = ContentScale.Fit
            )
        }
    } else {
        // Show title text
        Text(
            text = item.Name,
            style = style,
            color = color,
            modifier = modifier
        )
    }
}

/**
 * Reusable composable for displaying item title, metadata, and synopsis
 * Matching the style used on the home screen for uniformity
 */
@Composable
fun ItemDetailsSection(
    item: JellyfinItem,
    apiService: JellyfinApiService? = null,
    modifier: Modifier = Modifier,
    synopsisMaxLines: Int = 3,
    additionalMetadataContent: @Composable () -> Unit = {}
) {
    val runtimeText = formatRuntime(item.RunTimeTicks)
    val yearText = item.ProductionYear?.toString() ?: ""
    val genreText = item.Genres?.take(3)?.joinToString(", ") ?: ""
    
    Column(
        modifier = modifier
    ) {
        // Title or Logo
        TitleOrLogo(
            item = item,
            apiService = apiService,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
            ),
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Metadata: Year, Runtime, Genre + Additional metadata boxes
        if (yearText.isNotEmpty() || runtimeText.isNotEmpty() || genreText.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                if (yearText.isNotEmpty()) {
                    Text(
                        text = yearText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                        ),
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                if (runtimeText.isNotEmpty()) {
                    Text(
                        text = runtimeText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                        ),
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                if (genreText.isNotEmpty()) {
                    Text(
                        text = genreText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                        ),
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                
                // Add spacing before additional metadata boxes
                if (yearText.isNotEmpty() || runtimeText.isNotEmpty() || genreText.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                // Additional metadata boxes (from play button row)
                additionalMetadataContent()
            }
        } else {
            // If no text metadata, still show additional metadata boxes
            Row(
                modifier = Modifier.padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                additionalMetadataContent()
            }
        }
        
        // Synopsis
        item.Overview?.let { synopsis ->
            if (synopsis.isNotEmpty()) {
                Text(
                    text = synopsis,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f,
                        lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 0.8f * 1.1f // Reduced line spacing (10% of font size)
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = synopsisMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
        
        // Director (only for movies)
        if (item.Type == "Movie") {
            val directors = item.People?.filter { it.Type == "Director" }?.mapNotNull { it.Name } ?: emptyList()
            if (directors.isNotEmpty()) {
                Text(
                    text = "Director: ${directors.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }
        }
    }
}

