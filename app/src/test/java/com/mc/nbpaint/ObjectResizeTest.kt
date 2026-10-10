package com.mc.nbpaint

import com.mc.nbpaint.model.ObjectBounds
import com.mc.nbpaint.ui.ObjectResize
import com.mc.nbpaint.ui.ResizeCorner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Estirar un objeto por un tirador es pura matemática, y es la clase de regla que no se puede
 * comprobar a ojo en un test: si la esquina quieta se mueve un píxel, o la escala sale
 * negativa, en la pantalla solo se ve que "el texto se va raro". Aquí se fija ese
 * comportamiento.
 */
class ObjectResizeTest {

    private val caja = ObjectBounds(left = 100f, top = 50f, right = 300f, bottom = 150f)

    @Test
    fun `estirar por abajo a la derecha deja quieta la esquina opuesta`() {
        val despues = ObjectResize.resize(caja, ResizeCorner.BOTTOM_RIGHT, x = 400f, y = 250f)

        assertEquals(100f, despues.left, 0.01f)
        assertEquals(50f, despues.top, 0.01f)
        assertEquals(400f, despues.right, 0.01f)
        assertEquals(250f, despues.bottom, 0.01f)
    }

    @Test
    fun `estirar por abajo a la izquierda deja quieta la esquina de arriba a la derecha`() {
        val despues = ObjectResize.resize(caja, ResizeCorner.BOTTOM_LEFT, x = 20f, y = 200f)

        assertEquals(300f, despues.right, 0.01f)
        assertEquals(50f, despues.top, 0.01f)
        assertEquals(20f, despues.left, 0.01f)
        assertEquals(200f, despues.bottom, 0.01f)
    }

    @Test
    fun `pasar el dedo de largo pone la caja derecha en vez de al reves`() {
        // Tirando del tirador de abajo a la derecha más allá de la esquina contraria, los bordes
        // se cruzarían: el borde derecho acabaría por la izquierda del izquierdo. La caja tiene
        // que salir ordenada, o el objeto saldría al revés y desaparecería.
        val despues = ObjectResize.resize(caja, ResizeCorner.BOTTOM_RIGHT, x = 40f, y = 10f)

        assertEquals(40f, despues.left, 0.01f)
        assertEquals(10f, despues.top, 0.01f)
        assertEquals(100f, despues.right, 0.01f)
        assertEquals(50f, despues.bottom, 0.01f)
        // Y sale ordenada, que era justo lo que se pedía: con los bordes cruzados, el objeto
        // se dibujaría del revés.
        assertTrue(despues.right > despues.left)
        assertTrue(despues.bottom > despues.top)
    }

    @Test
    fun `el factor sale del lado mayor de los dos`() {
        // Un texto de dos líneas muy desiguales, más alto que ancho: se tira del tirador de
        // abajo y lo que crece de verdad es el alto. Con el ancho como única referencia, el
        // factor saldría 1 y el texto no crecería nada.
        val alto = ObjectBounds(left = 0f, top = 0f, right = 20f, bottom = 100f)
        val estirado = ObjectResize.resize(alto, ResizeCorner.BOTTOM_RIGHT, x = 20f, y = 300f)

        assertEquals(3f, ObjectResize.factor(alto, estirado), 0.01f)
    }

    @Test
    fun `una caja sin medida no cambia de escala`() {
        val vacia = ObjectBounds(left = 10f, top = 10f, right = 10f, bottom = 10f)
        val estirada = ObjectResize.resize(vacia, ResizeCorner.BOTTOM_RIGHT, x = 90f, y = 90f)

        // Dividir por cero aquí devolvía Infinity, y con ella la escala del objeto se iba.
        assertEquals(1f, ObjectResize.factor(vacia, estirada), 0.01f)
    }

    @Test
    fun `la posicion se clava en la esquina quieta al crecer`() {
        val escala = 2f
        val (x, y) = ObjectResize.placementFor(caja, ResizeCorner.BOTTOM_RIGHT, escala)

        // Tirando del tirador de abajo a la derecha, la esquina de arriba a la izquierda se
        // queda puta y la caja crece hacia abajo y a la derecha. Si se anclara al revés, la
        // caja saldría al otro lado y el texto se quedaría colgando de la esquina contraria.
        assertEquals(100f, x, 0.01f)
        assertEquals(50f, y, 0.01f)
    }

    @Test
    fun `la posicion mantiene el lado quieto al encoger desde arriba a la izquierda`() {
        val escala = 0.5f
        val (x, y) = ObjectResize.placementFor(caja, ResizeCorner.TOP_LEFT, escala)

        // Tirando del tirador de arriba a la izquierda, ese lado se mueve y el de abajo a la
        // derecha se queda: la caja mengua hacia dentro sin desplazarse de sitio.
        assertEquals(200f, x, 0.01f)
        assertEquals(100f, y, 0.01f)
    }

    @Test
    fun `el tirador mas cercano se elige aunque dos esten a la misma distancia`() {
        val pequeno = ObjectBounds(left = 0f, top = 0f, right = 10f, bottom = 10f)

        assertEquals(
            ResizeCorner.TOP_RIGHT,
            ObjectResize.cornerNear(pequeno, x = 9f, y = 1f, radius = 12f)
        )
        assertEquals(
            ResizeCorner.BOTTOM_LEFT,
            ObjectResize.cornerNear(pequeno, x = 1f, y = 9f, radius = 12f)
        )
    }

    @Test
    fun `un punto lejos de todos los tiradores no agarra ninguno`() {
        val ancho = ObjectBounds(left = 0f, top = 0f, right = 500f, bottom = 400f)

        // Agarrar el tirador equivocado es peor que no agarrar nada: al mover medio lienzo se
        // estiraría la imagen con el dedo que solo quería dibujarle encima.
        assertEquals(null, ObjectResize.cornerNear(ancho, x = 250f, y = 200f, radius = 20f))
    }
}
