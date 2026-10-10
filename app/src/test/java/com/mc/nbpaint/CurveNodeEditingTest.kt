package com.mc.nbpaint

import androidx.compose.ui.geometry.Offset
import com.mc.nbpaint.model.CurveNode
import com.mc.nbpaint.model.CurveObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Corregir una curva a mano: tirar de un tirador y recolocar un nodo.
 *
 * Son las dos cosas que convierten un trazo ya hecho en el trazo que se quería, y las dos son
 * aritmética sobre puntos. Se prueban aquí porque en la pantalla un error suyo se ve como
 * "la línea se va rara", que no dice nada de qué ha pasado.
 *
 * Un nodo suelto que no se puede coger es el caso que más importa: un nodo sin tiradores está
 * exactamente encima de la curva, así que tocarlo tenía que agarrar el tirador que tenía
 * encima, que además se quedaba quieto. El nodo se acababa moviendo entero y nunca se podía
 * colocar.
 */
class CurveNodeEditingTest {

    private val radio = 40f

    /** Una curva recta de (0, 100) a (200, 100), con los dos nodos rectos. */
    private fun recta() = CurveObject(
        nodes = mutableListOf(
            CurveNode(point = Offset(0f, 100f)),
            CurveNode(point = Offset(200f, 100f))
        )
    )

    @Test
    fun `un nodo sin tiradores se puede coger`() {
        val curva = recta()

        val nodo = curva.nodeNear(Offset(5f, 102f), radio)

        // Es el caso que antes no existía: el nodo no tiene tiradores, así que no hay ningún
        // tirador que lo capture, y aun así tiene que poder agarrarse.
        assertSame(curva.nodes.first(), nodo)
    }

    @Test
    fun `a un nodo que no se llega no se agarra nada`() {
        val curva = recta()

        // A media curva no hay ningún nodo dentro del radio.
        assertNull(curva.nodeNear(Offset(100f, 100f), radio))
        // Y con un radio corto tampoco, aunque el punto esté al lado del nodo.
        assertNull(curva.nodeNear(Offset(60f, 100f), radio / 4f))
    }

    @Test
    fun `de varios nodos en el radio se coge el mas cercano`() {
        val curva = recta()

        val nodo = curva.nodeNear(Offset(198f, 100f), radio)

        // Los dos nodos caen en el radio si el radio es enorme; tiene que ganar el de verdad
        // más cercano, no el primero de la lista.
        assertSame(curva.nodes[1], nodo)
    }

    @Test
    fun `mover un nodo se lleva sus tiradores pero no cambia la forma`() {
        val curva = recta()
        val nodo = curva.nodes.first()
        // Un tirador a un lado, como el que deja una curva ya corregida.
        nodo.outHandle = Offset(40f, 80f)
        nodo.inHandle = Offset(-40f, 120f)

        nodo.moverA(Offset(30f, 250f))

        assertEquals(Offset(30f, 250f), nodo.point)
        // La distancia al nodo es la misma que antes, que es lo que significa no deformar el
        // tramo: si el tirador se quedara clavado, la curva se estiraría al recolocar el nodo.
        assertEquals(Offset(70f, 230f), nodo.outHandle)
        assertEquals(Offset(-10f, 270f), nodo.inHandle)
    }

    @Test
    fun `mover un nodo recto lo deja recto`() {
        val curva = recta()
        val nodo = curva.nodes.last()

        nodo.moverA(Offset(500f, 40f))

        // Sin tiradores no hay forma que conservar: los tres puntos siguen siendo el mismo, que
        // es lo que hace que la línea siga siendo recta y no un garabato.
        assertEquals(nodo.point, nodo.inHandle)
        assertEquals(nodo.point, nodo.outHandle)
        assertTrue(!nodo.hasHandles)
    }

    @Test
    fun `un tirador pegado al nodo no lo captura`() {
        val curva = recta()

        // El nodo no tiene tiradores, así que no hay ninguno que lo tape.
        assertNull(curva.handleNear(Offset(0f, 100f), radio))
        // Y con uno pegado tampoco: `hasHandles` es justamente la distancia al nodo.
        val conTirador = CurveObject(
            nodes = mutableListOf(CurveNode(point = Offset(0f, 100f), outHandle = Offset(0f, 100f)))
        )
        assertNull(conTirador.handleNear(Offset(0f, 100f), radio))
    }

    @Test
    fun `un tirador aparte del nodo si se agarra`() {
        val curva = recta()
        val nodo = curva.nodes.first()
        nodo.outHandle = Offset(60f, 40f)

        val agarrado = curva.handleNear(Offset(60f, 40f), radio)

        assertSame(nodo, agarrado?.first)
        // `true` es el de entrada, `false` el de salida. Este es de salida.
        assertEquals(false, agarrado?.second)
    }

    @Test
    fun `de dos tiradores cerca se coge el mas cercano`() {
        val curva = recta()
        val primero = curva.nodes.first()
        primero.inHandle = Offset(0f, 60f)
        primero.outHandle = Offset(0f, 140f)
        // Con el radio grande, los dos tiradores entran.
        val agarrado = curva.handleNear(Offset(0f, 135f), 200f)

        assertSame(primero, agarrado?.first)
        assertEquals(false, agarrado?.second)
    }

    @Test
    fun `tirar de un tirador dobla la curva y deja el otro enfrente`() {
        val curva = recta()
        val nodo = curva.nodes.first()
        nodo.outHandle = Offset(0f, 140f)
        nodo.inHandle = Offset(0f, 60f)

        nodo.arrastrarSalida(Offset(0f, 200f))

        // Los dos tiradores quedan a la misma distancia en sentidos contrarios: por eso la
        // curva sigue siendo suave al pasar por el nodo.
        assertEquals(Offset(0f, 200f), nodo.outHandle)
        assertEquals(Offset(0f, 0f), nodo.inHandle)
    }

    @Test
    fun `tirar de un tirador deja los tiradores donde estaban si el nodo es angular`() {
        val curva = recta()
        val nodo = curva.nodes.first().also { it.isCorner = true }
        nodo.outHandle = Offset(0f, 140f)
        nodo.inHandle = Offset(0f, 60f)

        nodo.arrastrarSalida(Offset(80f, 200f))

        // En un nodo angular los dos lados se mueven por su cuenta, así que el contrario se
        // queda como estaba.
        assertEquals(Offset(0f, 60f), nodo.inHandle)
    }
}
