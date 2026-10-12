package com.flex.elefin.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.flex.elefin.ui.TvBringIntoViewSpec
import kotlinx.coroutines.launch

internal class RecommendationDrawerReturn(private val fallback: FocusRequester) {
    var returnToFirstCard: (() -> Unit)? = null
    fun requestFocus() {
        returnToFirstCard?.invoke() ?: fallback.requestFocus()
    }
}
internal val LocalRecommendationDrawerReturn = staticCompositionLocalOf<RecommendationDrawerReturn?> { null }

private class RecommendationFocusMemory(val lastId: MutableState<String?>) {
    var onCardFocused: () -> Unit = {}
    val targets = mutableMapOf<String, FocusRequester>()
    var horizontalNavigation = false
}

private val LocalRecommendationFocus = staticCompositionLocalOf<RecommendationFocusMemory?> { null }

/** Preserve each keyed shelf's focus and scroll offset independently of adjacent shelves. */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun RecommendationLazyRow(
    state: LazyListState,
    contentPadding: PaddingValues,
    horizontalArrangement: Arrangement.Horizontal,
    flingBehavior: FlingBehavior,
    modifier: Modifier,
    content: LazyListScope.() -> Unit
) {
    val lastId = rememberSaveable { mutableStateOf<String?>(null) }
    val memory = remember { RecommendationFocusMemory(lastId) }
    val drawerReturn = LocalRecommendationDrawerReturn.current
    val scope = rememberCoroutineScope()
    val returnToFirstCard: () -> Unit = remember(state, memory, scope) {
        {
            scope.launch {
                memory.horizontalNavigation = false
                state.scrollToItem(0)
                // Wait for the first card to be composed before requesting its focus.
                repeat(10) {
                    withFrameNanos { }
                    val first = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
                    val target = memory.targets[first?.key as? String]
                    if (target != null) {
                        // Do not let the row's normal entry policy restore a later card.
                        memory.lastId.value = first?.key as? String
                        target.requestFocus()
                        return@launch
                    }
                }
            }
            Unit
        }
    }
    memory.onCardFocused = { drawerReturn?.returnToFirstCard = returnToFirstCard }
    DisposableEffect(drawerReturn, returnToFirstCard) {
        onDispose {
            if (drawerReturn?.returnToFirstCard === returnToFirstCard) drawerReturn.returnToFirstCard = null
        }
    }
    val horizontalSpec = remember { TvBringIntoViewSpec(vertical = false, horizontalPivotEnabled = { memory.horizontalNavigation }) }
    CompositionLocalProvider(LocalRecommendationFocus provides memory, LocalBringIntoViewSpec provides horizontalSpec) {
        LazyRow(state = state, contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement, flingBehavior = flingBehavior,
            modifier = modifier.onPreviewKeyEvent { event ->
                when (event.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> memory.horizontalNavigation = true
                    android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN -> memory.horizontalNavigation = false
                }
                false
            }.focusProperties {
                enter = {
                    // Restoring vertical focus must not move this row back to the horizontal pivot.
                    memory.horizontalNavigation = false
                    memory.targets[lastId.value]
                        ?: state.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { memory.targets[it.key as? String] }
                        ?: FocusRequester.Default
                }
            }.focusGroup(), content = content)
    }
}

@Composable
internal fun RecommendationFocusCard(id: String, content: @Composable () -> Unit) {
    val memory = LocalRecommendationFocus.current
    val requester = remember { FocusRequester() }
    DisposableEffect(memory, id) {
        memory?.targets?.set(id, requester)
        onDispose { memory?.targets?.remove(id) }
    }
    Box(Modifier.focusRequester(requester).onFocusChanged {
        if (it.hasFocus) {
            memory?.lastId?.value = id
            memory?.onCardFocused?.invoke()
        }
    }) { content() }
}
