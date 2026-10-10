package com.mc.nbpaint

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.mc.nbpaint.ui.CanvasTransform
import com.mc.nbpaint.ui.CanvasTransformState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El documento tiene un tamaño propio, independiente del visor, así que "escala 1" quiere
 * decir "el tamaño real del dibujo" y la escala de encuadre es la que lo hace entrar entero
 * en el espacio disponible. Estos tests fijan las reglas que hacen que mover la hoja no se
 * sienta pesada: nunca se puede sacar el dibujo de la pantalla, y el zoom se mantiene
 * anclado bajo los dedos.
 */
class CanvasTransformStateTest {

    private val viewport = IntSize(600, 1000)

    /** Documento del mismo tamaño que el visor: el caso en el que el encuadre es 1. */
    private fun state() = CanvasTransformState(
        scope = CoroutineScope(Dispatchers.Unconfined),
        initialViewport = viewport
    ).apply {
        onViewportSize(viewport)
        onContentSize(viewport)
    }

    @Test
    fun `a escala 1 el desplazamiento queda siempre en el origen`() {
        val s = state()
        s.applyTransform(CanvasTransform(Offset(300f, 500f), Offset(400f, -250f), 1f))

        assertEquals(0f, s.pan.x, 0.01f)
        assertEquals(0f, s.pan.y, 0.01f)
        assertEquals(1f, s.scale, 0.01f)
    }

    @Test
    fun `a escala 1 el zoom se ignora para no ver menos que el lienzo entero`() {
        val s = state()
        s.applyTransform(CanvasTransform(Offset(300f, 500f), Offset.Zero, 0.5f))
        assertEquals(1f, s.scale, 0.01f)
    }

    @Test
    fun `el desplazamiento no puede dejar huecos en los bordes`() {
        val s = state()
        s.applyTransform(CanvasTransform(Offset(300f, 500f), Offset.Zero, 2f))

        // Empujones exagerados en las cuatro direcciones.
        s.applyTransform(CanvasTransform(Offset.Zero, Offset(10_000f, 10_000f), 1f))
        assertTrue("no debe pasar de 0", s.pan.x <= 0.01f)
        assertTrue("no debe pasar de 0", s.pan.y <= 0.01f)

        s.applyTransform(CanvasTransform(Offset.Zero, Offset(-10_000f, -10_000f), 1f))
        assertTrue("no debe ir más lejos que -ancho*(escala-1)", s.pan.x >= -600f - 0.01f)
        assertTrue("no debe ir más lejos que -alto*(escala-1)", s.pan.y >= -1000f - 0.01f)
    }

    @Test
    fun `el zoom queda anclado en el punto donde estan los dedos`() {
        val s = state()
        s.applyTransform(CanvasTransform(Offset(300f, 400f), Offset.Zero, 2f))

        // Lo que hay justo bajo el centroide del gesto no se debe mover de lugar.
        val centroid = Offset(100f, 100f)
        val contentBefore = (centroid - s.pan) / s.scale
        s.applyTransform(CanvasTransform(centroid, Offset.Zero, 1.5f))
        val contentAfter = (centroid - s.pan) / s.scale

        assertEquals(contentBefore.x, contentAfter.x, 0.5f)
        assertEquals(contentBefore.y, contentAfter.y, 0.5f)
    }

    @Test
    fun `el zoom se limita al rango util`() {
        val s = state()
        repeat(20) { s.applyTransform(CanvasTransform(Offset(300f, 500f), Offset.Zero, 2f)) }
        assertEquals(8f, s.scale, 0.01f)

        s.reset()
        repeat(20) { s.applyTransform(CanvasTransform(Offset(300f, 500f), Offset.Zero, 0.5f)) }
        assertEquals(1f, s.scale, 0.01f)
    }

    @Test
    fun `el doble toque alterna entre detalle y vista completa`() {
        val s = state()
        assertEquals(1f, s.scale, 0.01f)
        assertTrue(!s.isZoomed)

        s.toggleDoubleTap(Offset(300f, 500f))
        assertEquals(2.5f, s.scale, 0.01f)
        assertTrue(s.isZoomed)

        s.toggleDoubleTap(Offset(300f, 500f))
        assertEquals(1f, s.scale, 0.01f)
        assertEquals(Offset.Zero, s.pan)
    }

    @Test
    fun `reiniciar devuelve la vista a su estado inicial`() {
        val s = state()
        s.applyTransform(CanvasTransform(Offset(300f, 500f), Offset(80f, 60f), 3f))
        assertTrue(s.isZoomed)

        s.reset()
        assertEquals(1f, s.scale, 0.01f)
        assertEquals(Offset.Zero, s.pan)
        assertTrue(!s.isZoomed)
    }

    @Test
    fun `un documento mas chico que el visor se centra y no se desplaza`() {
        val s = CanvasTransformState(CoroutineScope(Dispatchers.Unconfined), viewport)
        s.onViewportSize(viewport)
        // Documento apaisado: sobra alto, así que en vertical queda sobre y debajo.
        s.onContentSize(IntSize(600, 500))

        assertEquals(1f, s.fitScale, 0.01f)
        assertEquals(1f, s.scale, 0.01f)
        assertEquals(0f, s.pan.x, 0.01f)
        assertEquals(250f, s.pan.y, 0.01f)

        // No hay juego en el eje con holgura: el arrastre no lo mueve.
        s.applyTransform(CanvasTransform(Offset(300f, 500f), Offset(5_000f, 5_000f), 1f))
        assertEquals(0f, s.pan.x, 0.01f)
        assertEquals(250f, s.pan.y, 0.01f)
    }

    @Test
    fun `cambiar el alto del visor reencuadra sin tocar el tamano del dibujo`() {
        val s = state()
        val document = s.contentSize

        // El dock se abre y el visor queda 200 px más bajo.
        s.onViewportSize(IntSize(600, 800))

        // El documento sigue igual: esto es lo que evita que el dibujo se deforme.
        assertEquals(document, s.contentSize)
        // Y el encuadre se recalcula para que el documento siga entrando entero.
        assertEquals(0.8f, s.fitScale, 0.01f)
        assertEquals(0.8f, s.scale, 0.01f)
    }

    @Test
    fun `al reencuadrar despues de ampliar se conserva la parte importa`() {
        val s = state()
        s.applyTransform(CanvasTransform(Offset(300f, 500f), Offset.Zero, 3f))
        val before = (Offset(100f, 100f) - s.pan) / s.scale

        s.onViewportSize(IntSize(600, 800))

        val after = (Offset(100f, 100f) - s.pan) / s.scale
        assertEquals(before.x, after.x, 1f)
        assertEquals(before.y, after.y, 1f)
    }

    @Test
    fun `el visor no se reencuadra a mitad de gesto`() {
        val s = state()

        // Empieza un arrastre (una selección, por ejemplo) y, mientras el dedo está
        // apoyado, nace el grupo "Zona" del dock y el visor queda 200 px más bajo.
        s.beginGesture()
        s.onViewportSize(IntSize(600, 800))

        // El encuadre NO se puede rehacer todavía: movería la hoja bajo el dedo.
        assertEquals(1f, s.scale, 0.01f)
        assertTrue("el visor no debe encogerse aún", s.viewport == IntSize(600, 1000))

        // Al soltar, el tamaño guardado ya entra y el encuadre se recalcula.
        s.endGesture()
        assertEquals(0.8f, s.scale, 0.01f)
        assertTrue(s.viewport == IntSize(600, 800))
    }

    @Test
    fun `al soltar sin cambio de tamano no pasa nada`() {
        val s = state()
        s.beginGesture()
        s.endGesture()
        assertEquals(1f, s.scale, 0.01f)
        assertEquals(Offset.Zero, s.pan)
    }

    @Test
    fun `el zoom se mide sobre el encuadre y no sobre el tamano del documento`() {
        val s = CanvasTransformState(CoroutineScope(Dispatchers.Unconfined), viewport)
        s.onViewportSize(viewport)
        // Documento chico: entra ampleado, con encuadre 2.
        s.onContentSize(IntSize(300, 500))

        s.toggleDoubleTap(Offset(300f, 500f))
        // El doble toque es 2.5x el encuadre, así que se lee 250% y no 1250%.
        assertEquals(250, s.zoomPercent)

        // Y el tope sigue siendo 8 veces el encuadre, no 8 veces el documento.
        repeat(20) { s.applyTransform(CanvasTransform(Offset(300f, 500f), Offset.Zero, 2f)) }
        assertEquals(16f, s.scale, 0.01f)
        assertEquals(800, s.zoomPercent)
    }

    // ---- Una vista por capa ------------------------------------------------

    @Test
    fun `cada capa vuelve a la vista que tenia`() {
        val s = state()

        // En la primera capa se amplía a 3x y se desplaza.
        s.setActiveLayer(1)
        s.applyTransform(CanvasTransform(Offset(300f, 500f), Offset(40f, 30f), 3f))
        val primera = s.scale to s.pan

        // En la segunda, en cambio, se deja como estaba: la vista es de cada capa.
        s.setActiveLayer(2)
        assertEquals(1f, s.scale, 0.01f)
        assertEquals(Offset.Zero, s.pan)

        // Y al volver a la primera aparece tal cual la dejó, sin venir a rastras.
        s.setActiveLayer(1)
        assertEquals(primera.first, s.scale, 0.01f)
        assertEquals(primera.second.x, s.pan.x, 0.01f)
        assertEquals(primera.second.y, s.pan.y, 0.01f)
    }

    @Test
    fun `una capa que no se ha mirado nunca se abre con el documento entero`() {
        val s = state()
        s.setActiveLayer(1)
        s.applyTransform(CanvasTransform(Offset(300f, 500f), Offset.Zero, 4f))

        // Una capa nueva no aparece endurecida en un rincón: se abre a la vista.
        s.setActiveLayer(2)
        assertEquals(s.fitScale, s.scale, 0.01f)
        assertEquals(Offset.Zero, s.pan)
        assertTrue(!s.isZoomed)
    }

    @Test
    fun `la vista de cada capa se guarda con su id y no con su posicion`() {
        val s = state()

        s.setActiveLayer(7)
        s.applyTransform(CanvasTransform(Offset(300f, 500f), Offset.Zero, 2f))
        val conSiete = s.scale

        // Cambiar de capa en la pila es lo de menos: lo que manda es el id.
        s.setActiveLayer(9)
        s.setActiveLayer(7)
        assertEquals(conSiete, s.scale, 0.01f)
    }

    @Test
    fun `la vista guardada se acota al cambiar el tamano de la hoja`() {
        val s = state()
        s.setActiveLayer(1)
        s.applyTransform(CanvasTransform(Offset(300f, 500f), Offset(50f, 50f), 4f))
        val antes = s.pan

        // La hoja crece y el encuadre baja: la vista de la capa no puede quedarse
        // desplazada a un sitio donde ya no hay nada que mirar.
        s.onContentSize(IntSize(1200, 2000))
        s.setActiveLayer(2)
        s.setActiveLayer(1)

        assertTrue("no puede haber desplazamiento positivo", s.pan.x <= 0.01f)
        assertTrue("no puede haber desplazamiento positivo", s.pan.y <= 0.01f)
        assertTrue("tiene que haber cambiado de sitio", s.pan != antes || s.scale < 4f)
    }

    @Test
    fun `borrar una capa suelta su vista`() {
        val s = state()
        s.setActiveLayer(1)
        s.applyTransform(CanvasTransform(Offset(300f, 500f), Offset.Zero, 3f))
        s.setActiveLayer(2)

        // La capa 1 desaparece de la pila.
        s.retainLayers(setOf(2))

        // Y si algún día vuelve a haber una con ese id, no hereda la vista vieja.
        s.setActiveLayer(1)
        assertEquals(s.fitScale, s.scale, 0.01f)
    }

    @Test
    fun `cambiar de capa corta la inercia de la vista que se deja`() {
        val s = state()
        s.setActiveLayer(1)
        s.applyTransform(CanvasTransform(Offset(300f, 500f), Offset.Zero, 3f))
        s.setActiveLayer(2)
        val alCambiar = s.pan

        // La inercia se había pedido para la capa 1: si siguiera, movería el papel de la 2.
        s.startInertia(Offset(900f, 900f))

        assertEquals(alCambiar.x, s.pan.x, 0.01f)
        assertEquals(alCambiar.y, s.pan.y, 0.01f)
    }
}
