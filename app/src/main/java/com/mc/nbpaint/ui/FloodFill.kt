package com.mc.nbpaint.ui

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Relleno tipo "bote de pintura" (flood fill) por escaneo de líneas (scanline).
 * Mucho más rápido y con menor uso de pila que un flood fill recursivo,
 * lo que evita StackOverflow en lienzos grandes.
 *
 * @param tolerance diferencia máxima por canal (0-255) para considerar dos
 *                  colores "iguales". 0 = solo el color exacto.
 */
fun floodFill(bitmap: Bitmap, startX: Int, startY: Int, fillColor: Int, tolerance: Int = 24) {
    val width = bitmap.width
    val height = bitmap.height
    if (startX < 0 || startY < 0 || startX >= width || startY >= height) return

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val targetColor = pixels[startY * width + startX]
    if (colorsClose(targetColor, fillColor, 4) ) return // ya es ese color

    val visited = BooleanArray(width * height)
    val stack = ArrayDeque<Int>()
    stack.addLast(startY * width + startX)

    while (stack.isNotEmpty()) {
        val idx = stack.removeLast()
        if (visited[idx]) continue

        var x = idx % width
        val y = idx / width

        // Retroceder hasta el borde izquierdo del segmento a rellenar
        while (x > 0 && !visited[y * width + (x - 1)] && colorsClose(pixels[y * width + (x - 1)], targetColor, tolerance)) {
            x--
        }

        var spanAbove = false
        var spanBelow = false

        while (x < width && !visited[y * width + x] && colorsClose(pixels[y * width + x], targetColor, tolerance)) {
            val cur = y * width + x
            pixels[cur] = fillColor
            visited[cur] = true

            if (y > 0) {
                val above = (y - 1) * width + x
                val aboveMatches = !visited[above] && colorsClose(pixels[above], targetColor, tolerance)
                if (!spanAbove && aboveMatches) {
                    stack.addLast(above)
                    spanAbove = true
                } else if (spanAbove && !aboveMatches) {
                    spanAbove = false
                }
            }

            if (y < height - 1) {
                val below = (y + 1) * width + x
                val belowMatches = !visited[below] && colorsClose(pixels[below], targetColor, tolerance)
                if (!spanBelow && belowMatches) {
                    stack.addLast(below)
                    spanBelow = true
                } else if (spanBelow && !belowMatches) {
                    spanBelow = false
                }
            }

            x++
        }
    }

    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
}

private fun colorsClose(a: Int, b: Int, tolerance: Int): Boolean {
    if (tolerance <= 0) return a == b
    val aA = (a shr 24) and 0xFF
    val rA = (a shr 16) and 0xFF
    val gA = (a shr 8) and 0xFF
    val bA = a and 0xFF

    val aB = (b shr 24) and 0xFF
    val rB = (b shr 16) and 0xFF
    val gB = (b shr 8) and 0xFF
    val bB = b and 0xFF

    return abs(aA - aB) <= tolerance &&
        abs(rA - rB) <= tolerance &&
        abs(gA - gB) <= tolerance &&
        abs(bA - bB) <= tolerance
}
