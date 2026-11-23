package com.flex.elefin.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Bake the artwork and its static scrims once on a worker, not on every display frame. */
internal fun renderHomeBackdrop(source: Bitmap, palette: HomeArtworkPalette, width: Int, height: Int): Bitmap {
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawColor(0xFF090A0D.toInt())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val aspect = width.toFloat() / height
    val cropWidth = minOf(source.width, (source.height * aspect).toInt()).coerceAtLeast(1)
    val cropHeight = minOf(source.height, (source.width / aspect).toInt()).coerceAtLeast(1)
    val left = (source.width - cropWidth) / 2
    val top = (source.height - cropHeight) / 2
    canvas.drawBitmap(source, Rect(left, top, left + cropWidth, top + cropHeight), Rect(0, 0, width, height), paint)
    fun horizontal(a: Color, b: Color) = LinearGradient(0f, 0f, width.toFloat(), 0f,
        a.toArgb(), b.toArgb(), Shader.TileMode.CLAMP)
    val mix = LinearGradient(0f, 0f, 0f, height.toFloat(), 0x00FFFFFF, -1, Shader.TileMode.CLAMP)
    val bottom = ComposeShader(horizontal(palette.bottomLeft, palette.bottomRight), mix, PorterDuff.Mode.DST_IN)
    val field = ComposeShader(horizontal(palette.topLeft, palette.topRight), bottom, PorterDuff.Mode.SRC_OVER)
    val opacity = LinearGradient(0f, 0f, 0f, height.toFloat(),
        intArrayOf(0x0AFFFFFF, 0x26FFFFFF, 0xE6FFFFFF.toInt(), -1, -1),
        floatArrayOf(0f, .24f, .46f, .66f, 1f), Shader.TileMode.CLAMP)
    paint.shader = ComposeShader(field, opacity, PorterDuff.Mode.DST_IN)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f,
        intArrayOf(0xC7000000.toInt(), 0x94000000.toInt(), 0x14000000, 0),
        floatArrayOf(0f, .42f, .78f, 1f), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
        intArrayOf(0x7A000000, 0), floatArrayOf(0f, .18f), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    result.prepareToDraw()
    return result
}
