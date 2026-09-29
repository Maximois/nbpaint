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
}
