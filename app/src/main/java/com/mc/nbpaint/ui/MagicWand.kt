package com.mc.nbpaint.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.mc.nbpaint.engine.SelectionEngine
import com.mc.nbpaint.model.Selection

/**
 * Relleno inteligente ("varita mágica").
 *
 * A diferencia del bote de pintura, que busca un color contiguo y lo sustituye, esto separa
 * dos pasos: primero selecciona una región y después decide con qué se rellena. Eso permite
 * elegir un color de la paleta o dejar que la app calcule sola el fondo de la zona.
 *
 * La selección usa el barrido de líneas de [SelectionEngine], compartido con la selección
 * inteligente y el bote, para no arriesgar la pila con lienzos grandes.
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
    ): Int {
        val width = bitmap.width
        val height = bitmap.height
        if (startX < 0 || startY < 0 || startX >= width || startY >= height) return 0

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val target = pixels[startY * width + startX]
        val selected = SelectionEngine.contiguousMask(
            pixels, width, height, startX, startY, target, tolerance
        )

        val region = if (mode == SmartFillMode.SAMPLE) {
            SelectionEngine.surroundingColor(
                pixels, Selection.Mask(selected, width, height), width, height
            ) ?: AndroidColor.TRANSPARENT
        } else {
            fillColor
        }

        if (SelectionEngine.colorsClose(target, region, 4)) return 0

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
}
