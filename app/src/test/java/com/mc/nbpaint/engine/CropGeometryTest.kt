package com.mc.nbpaint.engine

import com.mc.nbpaint.model.IntRect
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de la geometría del recorte.
 *
 * Lo que decide **dónde** acaba cada píxel va en [IntRect], que es pura y se prueba aquí sin
 * emulador. Lo que mueve píxeles de verdad (`CropEngine.cropAt`) necesita `android.graphics` y
 * solo se comprueba en el dispositivo.
 */
class CropGeometryTest {

    @Test
    fun `unir dos rectangulos abarca los dos`() {
        val a = IntRect(0, 0, 3, 3)
        val b = IntRect(7, 7, 10, 10)
        assertEquals(IntRect(0, 0, 10, 10), a.union(b))
    }

    @Test
    fun `unir no encoge el rectangulo que ya era mas grande`() {
        val grande = IntRect(0, 0, 10, 10)
        assertEquals(grande, grande.union(IntRect(2, 2, 4, 4)))
    }

    @Test
    fun `unir con uno vacio devuelve el otro`() {
        val r = IntRect(4, 5, 9, 11)
        assertEquals(r, r.union(IntRect.EMPTY))
        assertEquals(r, IntRect.EMPTY.union(r))
    }

    @Test
    fun `el rectangulo del documento abarca el contenido de todas las capas`() {
        // Una capa con algo en la esquina de arriba y otra con algo en la de abajo: el papel
        // tiene que llegar a las dos, o la de abajo se quedaría fuera del documento.
        val arriba = IntRect(0, 0, 2, 2)
        val abajo = IntRect(38, 38, 40, 40)
        var contenido: IntRect? = null
        listOf(arriba, abajo).forEach { contenido = contenido?.union(it) ?: it }
        assertEquals(IntRect(0, 0, 40, 40), contenido)
    }

    @Test
    fun `recortar la hoja da el tamano del rectangulo`() {
        val hoja = IntRect(0, 0, 100, 80)
        val zona = IntRect(10, 20, 50, 60)
        val recortada = zona.clamped(hoja.width, hoja.height)
        assertEquals("la hoja nueva es la zona", 40, recortada.width)
        assertEquals(40, recortada.height)
    }

    @Test
    fun `una zona que se sale de la hoja se corta por el borde`() {
        val recortada = IntRect(-10, -10, 30, 20).clamped(100, 80)
        assertEquals(IntRect(0, 0, 30, 20), recortada)
        assertEquals("nunca puede quedar al reves", 30, recortada.width)
    }

    @Test
    fun `una zona enteramente fuera de la hoja no deja nada`() {
        assertEquals(true, IntRect(200, 200, 300, 300).clamped(100, 80).isEmpty)
    }

    @Test
    fun `la zona completa de la hoja no recorta nada`() {
        val hoja = IntRect(0, 0, 100, 80)
        val destino = IntRect(0, 0, 100, 80).clamped(hoja.width, hoja.height)
        val sinCambio = destino.left == 0 && destino.top == 0 &&
            destino.right == hoja.width && destino.bottom == hoja.height
        assertEquals("sin cambio no se toca el historial", true, sinCambio)
    }

    @Test
    fun `el contenido desplazado de una capa se mantiene dentro de la zona`() {
        // Al recortar la hoja, lo que estaba en la esquina de la zona pasa a (0, 0) y lo de
        // fuera se pierde. Esto es la regla que sigue el dibujo de cada capa.
        val zona = IntRect(20, 10, 60, 50)
        val pixelEnHoja = IntRect(25, 12, 26, 13)
        val dentroDeLaZona = zona.contains(pixelEnHoja.left, pixelEnHoja.top)
        assertEquals(true, dentroDeLaZona)
        val movido = IntRect(
            pixelEnHoja.left - zona.left,
            pixelEnHoja.top - zona.top,
            pixelEnHoja.right - zona.left,
            pixelEnHoja.bottom - zona.top
        )
        assertEquals(IntRect(5, 2, 6, 3), movido)
        assertEquals("tiene que caber en la hoja nueva", true, movedDentro(movido, zona))
    }

    private fun movedDentro(movido: IntRect, zona: IntRect): Boolean =
        movido.left >= 0 && movido.top >= 0 &&
            movido.right <= zona.width && movido.bottom <= zona.height
}
