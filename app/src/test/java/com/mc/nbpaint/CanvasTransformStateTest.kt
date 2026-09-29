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
 * El lienzo se crea del mismo tamaño que el visor, así que a escala 1 no hay holgura para
 * desplazarlo. Estos tests fijan las dos reglas que hacen que mover la hoja no se sienta
 * pesada: nunca se puede sacar el dibujo de la pantalla, y el zoom se mantiene anclado bajo
 * los dedos.
 */
class CanvasTransformStateTest {

    private val viewport = IntSize(600, 1000)

    private fun state() = CanvasTransformState(
        scope = CoroutineScope(Dispatchers.Unconfined),
        initialViewport = viewport
    ).apply { onViewportSize(viewport) }

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
}
