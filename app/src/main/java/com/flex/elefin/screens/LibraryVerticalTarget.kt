package com.flex.elefin.screens

/** Null means leave the grid at its top, or stop at its bottom. */
internal fun libraryVerticalTarget(index: Int, direction: Int, columns: Int, count: Int): Int? {
    if (columns <= 0 || index !in 0 until count || direction !in listOf(-1, 1)) return null
    val row = index / columns + direction
    if (row < 0 || row > (count - 1) / columns) return null
    return (row * columns + index % columns).coerceAtMost(count - 1)
}
