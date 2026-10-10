package com.mc.nbpaint.engine

import android.graphics.Canvas
import android.graphics.Path
import com.mc.nbpaint.model.Selection

/**
 * Convierte una [Selection] en algo que Android pueda usar para recortar el dibujo.
 *
 * Esta es la pieza que hace que la selección sea una función **general** y no un detalle de
 * una herramienta: en vez de que cada función pregunte píxel a píxel, se recorta el `Canvas`
 * una vez y debajo se puede pintar, rellenar, borrar, parchear, pintar formas o texto. Todo
 * lo que se dibuje mientras el recorte está puesto cae dentro de la selección por lo mismo.
 */
object SelectionClip {

    /**
     * Recorta [canvas] a la selección. Devuelve `true` si dejó recorte puesto, para que
     * quien llama haga `restore()` solo cuando hizo falta.
     */
    fun apply(canvas: Canvas, selection: Selection): Boolean {
        val path = pathOf(selection) ?: return false
        canvas.save()
        canvas.clipPath(path)
        return true
    }

    /**
     * La selección como camino de Android, o null si no recorta nada (sin selección, o una
     * selección vacía).
     *
     * La máscara se convierte en franjas horizontales en vez de un rectángulo por píxel:
     * un camino con 200 000 segmentos de un píxel es lentísimo de recortar, y con franjas
     * el resultado es el mismo.
     */
    fun pathOf(selection: Selection): Path? = when (selection) {
        is Selection.All -> null
        is Selection.Rect -> {
            if (selection.rect.isEmpty) Path() else Path().apply {
                addRect(
                    selection.rect.left.toFloat(),
                    selection.rect.top.toFloat(),
                    selection.rect.right.toFloat(),
                    selection.rect.bottom.toFloat(),
                    Path.Direction.CW
                )
            }
        }
        is Selection.Mask -> Path().apply {
            val w = selection.width
            val h = selection.height
            for (y in 0 until h) {
                val row = y * w
                var x = 0
                while (x < w) {
                    if (!selection.mask[row + x]) {
                        x++
                        continue
                    }
                    val start = x
                    while (x < w && selection.mask[row + x]) x++
                    addRect(
                        start.toFloat(),
                        y.toFloat(),
                        x.toFloat(),
                        (y + 1).toFloat(),
                        Path.Direction.CW
                    )
                }
            }
        }
    }
}
