package com.flex.elefin.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * TV-optimized BringIntoViewSpec that handles vertical and horizontal scrolling differently.
 * 
 * - VERTICAL (between rows): Positions focused rows at a fixed offset from the top,
 *   leaving room for row titles to be visible.
 * - HORIZONTAL (within a row): Pivot-style scrolling - keeps the focused card at a fixed
 *   position (around the 3rd card slot) while the row scrolls underneath, until reaching
 *   the start or end of the row.
 */
@OptIn(ExperimentalFoundationApi::class)
class TvBringIntoViewSpec : BringIntoViewSpec {
    
    // Target position from top where the focused card should be placed (for vertical scrolling)
    // This leaves room above for the row title to be visible
    private val verticalTargetPosition = 120f
    
    // Horizontal pivot position - where the focused card should be positioned
    // This is approximately the 3rd card position (accounting for padding and card width)
    // Card width ~200px + spacing ~26px = ~226px per card, so 3rd card starts around 452px
    // But we want some left margin, so ~400px from left edge
    private val horizontalPivotPosition = 400f
    
    /**
     * Smooth spring animation for TV navigation.
     */
    override val scrollAnimationSpec: AnimationSpec<Float> = spring(
        dampingRatio = 0.85f,
        stiffness = 350f
    )
    
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float
    ): Float {
        // Detect if this is vertical or horizontal scrolling based on container size
        // - Vertical (LazyColumn): containerSize is screen HEIGHT (~400-800px for the row area)
        // - Horizontal (LazyRow): containerSize is screen WIDTH (~1200-1920px)
        
        val itemStart = offset
        val itemEnd = offset + size
        
        // Heuristic: If containerSize is less than 1000, it's likely the vertical viewport
        val isLikelyVerticalScrolling = containerSize < 1000f
        
        if (isLikelyVerticalScrolling) {
            // VERTICAL SCROLLING: Position the row at the target position
            val scrollNeeded = offset - verticalTargetPosition
            return if (kotlin.math.abs(scrollNeeded) > 5f) scrollNeeded else 0f
        } else {
            // HORIZONTAL SCROLLING: Pivot-style - keep focused card at fixed position
            // The card should stay at the pivot position while the row scrolls underneath
            
            val targetPosition = horizontalPivotPosition
            
            // Calculate how much we need to scroll to bring the card to the pivot position
            val scrollNeeded = offset - targetPosition
            
            // Always try to position the card at the pivot
            // The scroll will naturally stop at the row edges
            return if (kotlin.math.abs(scrollNeeded) > 5f) {
                scrollNeeded
            } else {
                0f
            }
        }
    }
}

/**
 * Provides TV-optimized bring-into-view behavior for child composables.
 * Wrap your LazyRow/LazyColumn content with this to get better TV focus handling.
 * 
 * Usage:
 * ```
 * TvBringIntoViewProvider {
 *     LazyRow {
 *         items(items) { item ->
 *             // Your item content
 *         }
 *     }
 * }
 * ```
 * 
 * @param content The composable content that will use TV bring-into-view behavior
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvBringIntoViewProvider(
    content: @Composable () -> Unit
) {
    val bringIntoViewSpec = remember { TvBringIntoViewSpec() }
    CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
        content()
    }
}

