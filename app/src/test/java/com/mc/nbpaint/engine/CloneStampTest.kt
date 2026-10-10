package com.mc.nbpaint.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El clonado copia desde una fuente corrida con el mismo desplazamiento que hizo el dedo.
 * Eso es justo lo que puede salir mal sin que se note: si el desplazamiento se recalcula en
 * cada movimiento la copia se corre sola, y si no se fija nunca la copia se queda clavada
 * en su sitio.
 */
class CloneStampTest {

    @Test
    fun `sin fuente no hay nada que copiar`() {
        val sello = CloneStamp()
        assertFalse(sello.hasSource)
        assertFalse(sello.isStamping)
        assertNull(sello.sourceFor(10f, 10f))
    }

    @Test
    fun `el primer toque marca la fuente y no pinta`() {
        val sello = CloneStamp()
        assertFalse("el primer toque solo marca", sello.beginStamp(5f, 7f))
        assertTrue(sello.hasSource)
        assertFalse("aún no se está estampando", sello.isStamping)
    }

    @Test
    fun `tras marcar la fuente, el primer toque de pintado si estampa`() {
        val sello = CloneStamp()
        sello.markSourceOnly(5f, 7f)
        assertTrue(sello.beginStamp(5f, 7f))
        assertTrue(sello.isStamping)
    }

    @Test
    fun `el desplazamiento queda fijo durante todo el trazo`() {
        val sello = CloneStamp()
        sello.markSourceOnly(10f, 20f)
        sello.beginStamp(30f, 50f)          // el dedo está 20 a la derecha y 30 abajo

        // Si el desplazamiento se recalculara en cada movimiento, estos puntos irían
        // saliendo cada vez más desplazados. Fijado una vez, la copia sale siempre corrida
        // con la misma distancia, que es lo que hace que el clon no se salga de la mano.
        assertEquals(10f to 20f, sello.sourceFor(30f, 50f))
        assertEquals(11f to 21f, sello.sourceFor(31f, 51f))
        assertEquals(99f to 89f, sello.sourceFor(119f, 119f))
    }

    @Test
    fun `el trazo siguiente vuelve a estampar desde el punto donde se toca`() {
        val sello = CloneStamp()
        sello.markSourceOnly(10f, 20f)
        sello.beginStamp(30f, 50f)
        sello.endStroke()

        // La fuente se conserva entre trazos: el usuario marca una vez y va pintando varios
        // ratos con el mismo sello. Lo que se suelta es el desplazamiento.
        assertTrue(sello.hasSource)
        assertEquals(10f to 20f, sello.sourceFor(30f, 50f))
    }

    @Test
    fun `sin desplazamiento fijo copia justo donde se toca`() {
        val sello = CloneStamp()
        sello.markSourceOnly(10f, 20f)
        // sourceFor sin beginStamp devuelve la propia fuente, que es lo que se necesita para
        // pintar sin arrastrar.
        assertEquals(10f to 20f, sello.sourceFor(10f, 20f))
    }

    @Test
    fun `volver a marcar en el mismo sitio no cambia nada`() {
        val sello = CloneStamp()
        assertTrue("la primera vez sí cambia", sello.markSource(4f, 4f))
        assertFalse("la segunda, en el mismo sitio, no", sello.markSource(4f, 4f))
    }

    @Test
    fun `olvidarse de la fuente la deja como si no hubiera ninguna`() {
        val sello = CloneStamp()
        sello.markSourceOnly(3f, 3f)
        sello.beginStamp(9f, 9f)
        sello.clear()
        assertFalse(sello.hasSource)
        assertNull(sello.sourceFor(9f, 9f))
    }
}
