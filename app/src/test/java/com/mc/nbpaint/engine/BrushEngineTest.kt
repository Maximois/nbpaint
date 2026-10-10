package com.mc.nbpaint.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de la geometría de las figuras.
 *
 * Esta es la parte que más ha dado problemas: la vista previa y el trazo final calculaban cada
 * uno la suya, así que podían describir figuras distintas. Ahora hay una sola cuenta, y esto
 * la fija.
 */
class BrushEngineTest {

    @Test
    fun `el circulo sale de la diagonal del arrastre`() {
        // De (0,0) a (6,8): la diagonal mide 10, y el radio es la mitad.
        assertEquals(5f, BrushEngine.radiusOf(0f, 0f, 6f, 8f), 0.001f)
    }

    @Test
    fun `da igual arrastrar en diagonal que al reves`() {
        assertEquals(
            BrushEngine.radiusOf(0f, 0f, 6f, 8f),
            BrushEngine.radiusOf(6f, 8f, 0f, 0f),
            0.001f
        )
    }

    @Test
    fun `el rectangulo sale con las esquinas en orden se arrastre como se arrastre`() {
        val normal = BrushEngine.geometryOf(ShapeKind.RECTANGLE, 1f, 2f, 5f, 7f) as ShapeGeometry.Box
        val inverso = BrushEngine.geometryOf(ShapeKind.RECTANGLE, 5f, 7f, 1f, 2f) as ShapeGeometry.Box
        assertEquals(1f, normal.left, 0.001f)
        assertEquals(2f, normal.top, 0.001f)
        assertEquals(5f, normal.right, 0.001f)
        assertEquals(7f, normal.bottom, 0.001f)
        // Sin esto, arrastrar de abajo a arriba salía cuadrado y en blanco.
        assertEquals(normal, inverso)
    }

    @Test
    fun `el rectangulo nunca sale al reves`() {
        val caja = BrushEngine.geometryOf(ShapeKind.RECTANGLE, 9f, 9f, 2f, 1f) as ShapeGeometry.Box
        assertEquals(2f, caja.left, 0.001f)
        assertEquals(1f, caja.top, 0.001f)
        assertEquals(9f, caja.right, 0.001f)
        assertEquals(9f, caja.bottom, 0.001f)
    }

    @Test
    fun `el circulo se centra en el medio de los dos puntos`() {
        val disco = BrushEngine.geometryOf(ShapeKind.CIRCLE, 2f, 2f, 8f, 10f) as ShapeGeometry.Disc
        assertEquals(5f, disco.centerX, 0.001f)
        assertEquals(6f, disco.centerY, 0.001f)
    }

    @Test
    fun `la linea guarda los puntos tal cual llegan`() {
        val linea = BrushEngine.geometryOf(ShapeKind.LINE, 3f, 4f, 9f, 1f) as ShapeGeometry.Segment
        assertEquals(3f, linea.x1, 0.001f)
        assertEquals(4f, linea.y1, 0.001f)
        assertEquals(9f, linea.x2, 0.001f)
        assertEquals(1f, linea.y2, 0.001f)
    }
}
