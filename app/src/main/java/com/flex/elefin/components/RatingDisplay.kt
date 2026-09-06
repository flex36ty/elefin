package com.flex.elefin.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flex.elefin.R
import com.flex.elefin.jellyfin.JellyfinItem

enum class RatingType {
    RottenTomatoesFresh,
    RottenTomatoesRotten,
    RottenTomatoesAudience,
    IMDb,
    Generic
}

@Composable
fun RatingDisplay(
    item: JellyfinItem,
    communityRating: Float?,
    criticRating: Float?
) {
    fun calculatePercentage(rating: Float): Int {
        return if (rating > 10) {
            rating.toInt()
        } else {
            (rating * 10).toInt()
        }
    }

    val criticRatingType = if (criticRating != null && criticRating > 0f) {
        determineRatingType(item.ProviderIds, null, criticRating, preferCommunity = false)
    } else {
        null
    }

    val communityRatingType = if (communityRating != null && communityRating > 0f) {
        determineRatingType(item.ProviderIds, communityRating, criticRating, preferCommunity = true)
    } else {
        null
    }

    // 1. Show Critic Rating (Rotten Tomatoes Fresh / Rotten)
    if (criticRating != null && criticRating > 0f) {
        val percentage = calculatePercentage(criticRating)
        when (criticRatingType) {
            RatingType.RottenTomatoesFresh -> {
                RatingBoxWithIcon(
                    percentage = percentage,
                    iconRes = R.drawable.ic_rt_fresh,
                    label = "RT"
                )
            }
            RatingType.RottenTomatoesRotten -> {
                RatingBoxWithIcon(
                    percentage = percentage,
                    iconRes = R.drawable.ic_rt_rotten,
                    label = "RT"
                )
            }
            RatingType.IMDb -> {
                RatingBoxWithIcon(
                    percentage = percentage,
                    iconRes = R.drawable.ic_imdb,
                    label = "IMDb"
                )
            }
            else -> {
                RatingGenericBox(text = "${percentage}%")
            }
        }
    }

    // 2. Show Community Rating (Rotten Tomatoes Popcorn Audience / IMDb)
    if (communityRating != null && communityRating > 0f && (criticRating == null || communityRating != criticRating)) {
        val percentage = calculatePercentage(communityRating)
        when (communityRatingType) {
            RatingType.RottenTomatoesAudience -> {
                RatingBoxWithIcon(
                    percentage = percentage,
                    iconRes = R.drawable.ic_rt_popcorn,
                    label = "RT"
                )
            }
            RatingType.IMDb -> {
                if (criticRatingType != RatingType.IMDb) {
                    RatingBoxWithIcon(
                        percentage = percentage,
                        iconRes = R.drawable.ic_imdb,
                        label = "IMDb"
                    )
                }
            }
            else -> {
                if (criticRating == null || criticRating <= 0f) {
                    RatingGenericBox(text = "${percentage}%")
                }
            }
        }
    }
}

fun determineRatingType(
    providerIds: Map<String, String>?,
    communityRating: Float?,
    criticRating: Float?,
    preferCommunity: Boolean = false
): RatingType {
    val rtId = providerIds?.get("RottenTomatoes") ?: providerIds?.get("rottentomatoes") ?:
               providerIds?.get("Rotten Tomatoes") ?: providerIds?.get("RottenTomatoes.tomato") ?:
               providerIds?.get("RottenTomatoes.audience") ?: providerIds?.get("rt")

    val imdbId = providerIds?.get("Imdb") ?: providerIds?.get("imdb") ?: providerIds?.get("IMDb") ?: providerIds?.get("imdbid")

    if (preferCommunity && communityRating != null && communityRating > 0f) {
        if (rtId != null) {
            return RatingType.RottenTomatoesAudience
        }
        if (imdbId != null) {
            return RatingType.IMDb
        }
        if (criticRating != null && criticRating > 0f) {
            return RatingType.RottenTomatoesAudience
        }
        return RatingType.IMDb
    }

    if (criticRating != null && criticRating > 0f && !preferCommunity) {
        return if (criticRating >= 60.0f || (criticRating in 6.0f..10.0f && criticRating >= 6.0f)) {
            RatingType.RottenTomatoesFresh
        } else {
            RatingType.RottenTomatoesRotten
        }
    }

    if (rtId != null && communityRating != null && communityRating > 0f) {
        return RatingType.RottenTomatoesAudience
    }

    if (imdbId != null) {
        return RatingType.IMDb
    }

    return RatingType.Generic
}

@Composable
fun RatingBoxWithIcon(
    percentage: Int,
    iconRes: Int,
    label: String
) {
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.height(12.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = "${percentage}%",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun RatingGenericBox(text: String) {
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}
