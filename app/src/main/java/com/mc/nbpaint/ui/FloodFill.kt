package com.mc.nbpaint.ui

import android.graphics.Bitmap
import com.mc.nbpaint.engine.SelectionEngine

/**
 * Relleno tipo "bote de pintura": sustituye la mancha contigua del color tocado por
 * [fillColor].
 *
 * La mancha se busca con el barrido de líneas compartido de [SelectionEngine] (el mismo que
 * usan la varita mágica y la selección inteligente), que no recurre y por eso no revienta la
 * pila en lienzos grandes.
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
    if (SelectionEngine.colorsClose(targetColor, fillColor, 4)) return

    val mask = SelectionEngine.contiguousMask(
        pixels, width, height, startX, startY, targetColor, tolerance
    )
    var changed = 0
    for (i in mask.indices) {
        if (mask[i]) {
            pixels[i] = fillColor
            changed++
        }
    }
    if (changed == 0) return
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
}
