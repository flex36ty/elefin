package com.flex.elefin.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListPrefetchScope
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope

/** Bounded idle-time preparation for D-pad shelves, including direction reversals. */
@OptIn(ExperimentalFoundationApi::class)
internal class HomeShelfPrefetchStrategy : LazyListPrefetchStrategy {
    private val pending = mutableMapOf<Int, LazyLayoutPrefetchState.PrefetchHandle>()

    override fun LazyListPrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyListLayoutInfo) {
        val visible = layoutInfo.visibleItemsInfo
        val next = visible.lastOrNull()?.index?.plus(1) ?: -1
        val previous = visible.firstOrNull()?.index?.minus(1) ?: -1
        val wanted = listOf(next, previous).filter { it in 0 until layoutInfo.totalItemsCount }
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in wanted) {
                entry.value.cancel()
                iterator.remove()
            }
        }
        for (index in wanted) {
            if (index !in pending) pending[index] = schedulePrefetch(index)
        }
    }

    // Visibility changes maintain the window. Never promote speculative row work
    // to urgent work that could consume a scrolling frame's entire time budget.
    override fun LazyListPrefetchScope.onScroll(delta: Float, layoutInfo: LazyListLayoutInfo) = Unit
    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) = Unit
}
