package com.mc.nbpaint.engine

import com.mc.nbpaint.model.IntRect
import com.mc.nbpaint.model.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la clase [Selection.Mask], sobre todo de los cálculos que se cachean.
 *
 * [Selection.Mask.bounds] y [Selection.Mask.selectedCount] recorren la máscara entera, y en
 * una hoja grande eso son 1,7 millones de píxeles. Como la vista los consulta en cada frame
 * para dibujar la selección, en la app se calculan una sola vez y se guardan. Estos tests
 * fijan que el resultado sea el correcto, que es lo que hace seguro cachearlo: la máscara no
 * se vuelve a modificar, así que el valor guardado no puede quedarse viejo.
 */
class SelectionMaskTest {

    private fun maskDe(w: Int, h: Int, activar: (x: Int, y: Int) -> Boolean): Selection.Mask {
        val mask = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) if (activar(x, y)) mask[y * w + x] = true
        return Selection.Mask(mask, w, h)
    }

    @Test
    fun `los limites abarcan toda la region`() {
        val m = maskDe(10, 10) { x, y -> x in 2..5 && y in 3..7 }
        assertEquals(IntRect(2, 3, 6, 8), m.bounds())
    }

    @Test
    fun `un solo pixel da unos limites de un pixel`() {
        val m = maskDe(10, 10) { x, y -> x == 4 && y == 6 }
        assertEquals(IntRect(4, 6, 5, 7), m.bounds())
    }

    @Test
    fun `una mascara vacia no tiene limites`() {
        val m = maskDe(10, 10) { _, _ -> false }
        assertNull(m.bounds())
    }

    @Test
    fun `cuenta los pixeles elegidos`() {
        assertEquals(0, maskDe(10, 10) { _, _ -> false }.selectedCount)
        assertEquals(100, maskDe(10, 10) { _, _ -> true }.selectedCount)
        assertEquals(4, maskDe(10, 10) { x, y -> x < 2 && y < 2 }.selectedCount)
    }

    @Test
    fun `los limites y el conteo se pueden pedir varias veces y dan lo mismo`() {
        // Es lo que hace la vista en cada frame: si esto no fuera estable, el valor guardado
        // estaría mal desde la segunda llamada.
        val m = maskDe(50, 50) { x, y -> x in 10..20 && y in 5..40 }
        val primera = m.bounds()
        val primeraCuenta = m.selectedCount
        repeat(5) {
            assertEquals(primera, m.bounds())
            assertEquals(primeraCuenta, m.selectedCount)
        }
        assertTrue(primera!!.width > 0 && primera.height > 0)
    }

    @Test
    fun `invertir cambia los limites y el conteo sin tocar la original`() {
        // La inversa crea una máscara nueva, así que no puede compartir el valor cacheado.
        val m = maskDe(4, 4) { x, y -> x < 2 && y < 2 }
        val inv = m.inverted()
        assertEquals(4, m.selectedCount)
        assertEquals(12, inv.selectedCount)
        assertEquals(IntRect(0, 0, 2, 2), m.bounds())
        assertEquals(IntRect(0, 0, 4, 4), inv.bounds())
    }
}
