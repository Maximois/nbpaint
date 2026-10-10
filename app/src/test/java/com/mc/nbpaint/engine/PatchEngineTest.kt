package com.mc.nbpaint.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del parche.
 *
 * Lo interesante aquí son los bordes: el caso que fallaba era tocar arriba del todo, donde no
 * hay nada sano que copiar. Con el motor aparte eso se puede comprobar sin dibujar a mano.
 */
class PatchEngineTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private val transparente = argb(0, 0, 0, 0)
    private val negro = argb(255, 0, 0, 0)
    private val blanco = argb(255, 255, 255, 255)
    private val verde = argb(255, 0, 200, 0)

    @Test
    fun `la ventana del pincel se recorta a la hoja`() {
        // Radio 4 alrededor de (2,2): de -2 a 6, y lo que se sale por la izquierda se corta.
        val w = PatchEngine.windowAt(2, 2, 4, 20, 20)!!
        assertEquals(0, w.left)
        assertEquals(0, w.top)
        assertEquals(6, w.right)
        assertEquals(6, w.bottom)
        assertEquals(7, w.width)
        assertEquals(7, w.height)

        val esquina = PatchEngine.windowAt(0, 0, 4, 20, 20)!!
        assertEquals(0, esquina.left)
        assertEquals(0, esquina.top)
        // Abajo y a la derecha llegan al borde, no lo pasan.
        assertEquals(4, esquina.right)
        assertEquals(4, esquina.bottom)
    }

    @Test
    fun `una hoja mas pequena que el pincel no rompe nada`() {
        val w = PatchEngine.windowAt(0, 0, 50, 3, 3)!!
        assertTrue("no puede ser más ancha que la hoja", w.width <= 3)
        assertTrue(w.height <= 3)
        assertNull(PatchEngine.windowAt(0, 0, 5, 0, 0))
    }

    @Test
    fun `la ventana no se sale por arriba de la hoja`() {
        // Tocar pegado al borde superior: la muestra tiene que bajar a un sitio que exista.
        val ventana = PatchEngine.windowAt(5, 2, 6, 40, 40)!!
        val fila = PatchEngine.sampleRow(2, 6f, ventana.height, 40)
        assertTrue("la fila de muestra no puede ser negativa", fila >= 0)
        assertTrue("ni salirse de la hoja", fila + ventana.height <= 40)
    }

    @Test
    fun `por arriba de todo se copia de una fila mas abajo`() {
        val ventana = PatchEngine.windowAt(5, 0, 3, 40, 40)!!
        val fila = PatchEngine.sampleRow(0, 3f, ventana.height, 40)
        assertEquals(0, fila)
    }

    @Test
    fun `copia lo de arriba acercandolo al tono que ya habia`() {
        val ventana = PatchEngine.Window(left = 10, top = 10, width = 9, height = 9)
        val actual = IntArray(81) { negro }
        val donado = IntArray(81) { verde }
        val salida = PatchEngine.patchWindow(actual, donado, ventana, 14f, 14f, 4f)
        val centro = salida[4 * 9 + 4]
        // El centro, lejos del borde, ya no es negro: se trajo el verde de arriba.
        assertTrue("tiene que verse el verde de arriba", chanelGreen(centro) > 0)
        // Pero no llega al verde puro: el parche se acerca al tono del contexto, que es negro.
        // Por eso un parche sobre negro no deja una mancha verde, queda oscura.
        assertTrue("debe acercarse al tono de abajo", chanelGreen(centro) < 200)
        // La esquina, fuera del radio, no se toca.
        assertEquals(negro, salida[0])
    }

    @Test
    fun `una muestra transparente deja el pixel como estaba`() {
        // Este es el fallo que borra la franja de arriba en vez de curarla.
        val ventana = PatchEngine.Window(left = 10, top = 10, width = 3, height = 3)
        val actual = IntArray(9) { blanco }
        val donado = IntArray(9) { transparente }
        val salida = PatchEngine.patchWindow(actual, donado, ventana, 11f, 11f, 1f)
        assertTrue("no se debe borrar nada", salida.all { it == blanco })
    }

    @Test
    fun `el radio chico no cambia nada`() {
        val ventana = PatchEngine.Window(left = 0, top = 0, width = 3, height = 3)
        val actual = IntArray(9) { negro }
        val salida = PatchEngine.patchWindow(actual, IntArray(9) { blanco }, ventana, 1f, 1f, 0.2f)
        assertTrue(salida.all { it == negro })
    }

    @Test
    fun `el tono promedio ignora lo transparente`() {
        val pixeles = intArrayOf(rojo(), transparente, azul())
        val (r, g, b) = PatchEngine.averageColor(pixeles)
        // Solo cuenta el rojo y el azul: el medio cae en 127.
        assertEquals(127f, r, 1f)
        assertEquals(0f, g, 1f)
        assertEquals(127f, b, 1f)
    }

    @Test
    fun `una ventana toda transparente no rompe el promedio`() {
        val (r, g, b) = PatchEngine.averageColor(IntArray(4) { transparente })
        assertEquals(0f, r, 0.001f)
        assertEquals(0f, g, 0.001f)
        assertEquals(0f, b, 0.001f)
    }

    @Test
    fun `el suavizado deja los pixeles planos igual`() {
        val plano = IntArray(25) { verde }
        val salida = IntArray(25)
        PatchEngine.blurBox(plano, salida, 5, 5)
        assertTrue("un color uniforme no se debe mover", salida.all { it == verde })
    }

    @Test
    fun `el suavizado promedia con los vecinos`() {
        // Un blanco en el centro de una ventana transparente: el blanco se derrama a los
        // vecinos, así que el centro baja y los de alrededor suben.
        val entrada = IntArray(9).also { it[4] = blanco }
        val salida = IntArray(9)
        PatchEngine.blurBox(entrada, salida, 3, 3)
        assertTrue("el centro tiene que perder color", chanelRed(salida[4]) < 255)
        assertTrue("el vecino tiene que ganar color", chanelRed(salida[0]) > 0)
    }

    @Test
    fun `suavizar una ventana vacia no revienta`() {
        PatchEngine.blurBox(IntArray(0), IntArray(0), 0, 0)
    }

    private fun rojo() = argb(255, 255, 0, 0)
    private fun azul() = argb(255, 0, 0, 255)

    private fun chanelRed(color: Int) = color ushr 16 and 0xFF
    private fun chanelGreen(color: Int) = color ushr 8 and 0xFF
}