package com.mc.nbpaint.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import kotlin.math.abs

/**
 * Relleno inteligente ("varita mágica").
 *
 * A diferencia de [floodFill], que busca un color contiguo y lo sustituye, esto separa dos
 * pasos: primero selecciona una región y después decide con qué se rellena. Eso permite
 * elegir un color de la paleta o dejar que la app calcule sola el fondo de la zona.
 *
 * La selección usa el mismo barrido de líneas que [floodFill] para no arriesgar la pila
 * con lienzos grandes.
 */
object MagicWand {

    /**
     * Rellena la región alrededor de ([startX], [startY]) cuyos píxeles son parecidos al de
     * ese punto.
     *
     * @param tolerance diferencia máxima por canal (0-255). Valores altos selectionan
     *                  regiones más grandes.
     * @param mode con [SmartFillMode.COLOR] usa [fillColor]; con [SmartFillMode.SAMPLE]
     *             promedia los píxeles que rodean la regiónSelected, lo que sirve para
     *             quitar un objeto dejando el fondo tal cual.
     * @return cantidad de píxeles modificados, o 0 si la región era del mismo color que el
     *         relleno (no había nada que hacer).
     */
    fun fill(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        fillColor: Int,
        tolerance: Int,
        mode: SmartFillMode
    ): Int {        val width = bitmap.width
        val height = bitmap.height
        if (startX < 0 || startY < 0 || startX >= width || startY >= height) return 0

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val target = pixels[startY * width + startX]
        val selected = selectContiguous(pixels, width, height, startX, startY, target, tolerance)

        val region = if (mode == SmartFillMode.SAMPLE) {
            sampleSurroundingColor(pixels, selected, width, height)
        } else {
            fillColor
        }

        if (colorsClose(target, region, 4)) return 0

        var changed = 0
        for (i in selected.indices) {
            if (selected[i]) {
                pixels[i] = region
                changed++
            }
        }
        if (changed == 0) return 0
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return changed
    }

    /**
     * Máscara de la región contigua por líneas. Evita recursión para no tocar el límite
     * de la pila en imágenes grandes.
     */
    private fun selectContiguous(
        pixels: IntArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        target: Int,
        tolerance: Int
    ): BooleanArray {
        val selected = BooleanArray(width * height)
        val stack = ArrayDeque<Int>()
        stack.addLast(startY * width + startX)

        while (stack.isNotEmpty()) {
            val idx = stack.removeLast()
            if (selected[idx]) continue

            var x = idx % width
            val y = idx / width

            while (x > 0 && !selected[y * width + (x - 1)] &&
                colorsClose(pixels[y * width + (x - 1)], target, tolerance)
            ) {
                x--
            }

            var spanAbove = false
            var spanBelow = false

            while (x < width && !selected[y * width + x] &&
                colorsClose(pixels[y * width + x], target, tolerance)
            ) {
                val cur = y * width + x
                selected[cur] = true

                if (y > 0) {
                    val above = (y - 1) * width + x
                    val matches = !selected[above] && colorsClose(pixels[above], target, tolerance)
                    if (!spanAbove && matches) {
                        stack.addLast(above)
                        spanAbove = true
                    } else if (spanAbove && !matches) {
                        spanAbove = false
                    }
                }

                if (y < height - 1) {
                    val below = (y + 1) * width + x
                    val matches = !selected[below] && colorsClose(pixels[below], target, tolerance)
                    if (!spanBelow && matches) {
                        stack.addLast(below)
                        spanBelow = true
                    } else if (spanBelow && !matches) {
                        spanBelow = false
                    }
                }

                x++
            }
        }
        return selected
    }

    /**
     * Promedia los píxeles que tocan la región desde afuera. Ese vecindario es, por
     * definición, el "fondo" que hay alrededor del objeto, así que al pintar con ese
     * promedio el objeto desaparece en lugar de quedar con un color plano.
     */
    private fun sampleSurroundingColor(
        pixels: IntArray,
        selected: BooleanArray,
        width: Int,
        height: Int
    ): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var a = 0L
        var n = 0

        fun accumulate(x: Int, y: Int) {
            if (x < 0 || y < 0 || x >= width || y >= height) return
            val i = y * width + x
            if (selected[i]) return
            val p = pixels[i]
            a += (p ushr 24) and 0xFF
            r += (p ushr 16) and 0xFF
            g += (p ushr 8) and 0xFF
            b += p and 0xFF
            n++
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!selected[y * width + x]) continue
                accumulate(x - 1, y)
                accumulate(x + 1, y)
                accumulate(x, y - 1)
                accumulate(x, y + 1)
            }
        }

        if (n == 0) return AndroidColor.TRANSPARENT
        return AndroidColor.argb(
            (a / n).toInt().coerceIn(0, 255),
            (r / n).toInt().coerceIn(0, 255),
            (g / n).toInt().coerceIn(0, 255),
            (b / n).toInt().coerceIn(0, 255)
        )
    }

    private fun colorsClose(a: Int, b: Int, tolerance: Int): Boolean {
        if (tolerance <= 0) return a == b
        return abs(((a ushr 24) and 0xFF) - ((b ushr 24) and 0xFF)) <= tolerance &&
            abs(((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF)) <= tolerance &&
            abs(((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF)) <= tolerance &&
            abs((a and 0xFF) - (b and 0xFF)) <= tolerance
    }
}
