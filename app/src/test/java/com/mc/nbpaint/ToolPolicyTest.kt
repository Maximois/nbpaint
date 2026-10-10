package com.mc.nbpaint

import com.mc.nbpaint.ui.PaintTool
import com.mc.nbpaint.ui.ToolPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El reparto de herramientas estaba duplicado en cuatro sitios (inicio, movimiento, final y
 * toque). El síntoma de olvidarse en uno de ellos era que la herramienta no hacía nada en ese
 * gesto, sin error ni aviso, y desde fuera parecía que el pincel no pintaba.
 *
 * Estos tests no comprueban que se pinte, sino que ninguna herramienta se escape sin
 * clasificar. Una herramienta nueva obliga a tocar esta lista, que es el punto.
 */
class ToolPolicyTest {

    @Test
    fun `ninguna herramienta se queda sin hacer nada`() {
        // El fallo que motivó este archivo era una herramienta olvidada en una de las
        // listas: sin error, sin aviso, y sin efecto. Este test lo convierte en un fallo de
        // compilación de tests, que es donde debería haber salido.
        val inertes = PaintTool.entries.filter {
            !ToolPolicy.followsDrag(it) && !ToolPolicy.actsOnTap(it)
        }
        assertTrue("herramientas que no responden a ningún gesto: $inertes", inertes.isEmpty())
    }

    @Test
    fun `el pincel y el borrador pintan al arrastrar y con el dedo`() {
        listOf(PaintTool.BRUSH, PaintTool.ERASER, PaintTool.SPRAY).forEach {
            assertTrue("$it sigue el dedo", ToolPolicy.followsDrag(it))
            assertFalse("$it no es solo un toque", ToolPolicy.actsOnTap(it))
        }
    }

    @Test
    fun `las figuras se dibujan provisionalmente y se confirman al soltar`() {
        listOf(PaintTool.LINE, PaintTool.RECTANGLE, PaintTool.CIRCLE).forEach {
            assertTrue("$it es figura", ToolPolicy.isShape(it))
            assertTrue("$it sigue el dedo", ToolPolicy.followsDrag(it))
        }
    }

    @Test
    fun `el relleno, la varita, el texto y el cuentagotas solo responden al toque`() {
        listOf(PaintTool.BUCKET, PaintTool.MAGIC_WAND, PaintTool.TEXT, PaintTool.EYEDROPPER).forEach {
            assertTrue("$it necesita un toque", ToolPolicy.actsOnTap(it))
            assertFalse("$it no sigue al dedo", ToolPolicy.followsDrag(it))
        }
    }

    @Test
    fun `el clonado y el parche responden a toque y a arrastre`() {
        // El toque les sirve para marcar la fuente o aplicar algo puntual, pero siguen
        // funcionando arrastrando. Si se olvidaran en alguno de los dos lados, el clonado
        // no marcaría fuente y el parche no se aplicaría.
        listOf(PaintTool.CLONE, PaintTool.PATCH).forEach {
            assertTrue("$it necesita un toque", ToolPolicy.actsOnTap(it))
            assertTrue("$it sigue el dedo", ToolPolicy.followsDrag(it))
        }
    }

    @Test
    fun `las herramientas de seleccion no piden una capa donde pintar`() {
        listOf(PaintTool.SELECTION, PaintTool.SMART_SELECTION).forEach {
            assertTrue("$it elige una zona", ToolPolicy.isSelection(it))
            assertFalse("$it no necesita capa", ToolPolicy.needsLayer(it))
            assertFalse("$it no sigue el dedo pintando", ToolPolicy.followsDrag(it))
        }
    }

    @Test
    fun `mover la imagen puesta sigue el dedo y necesita capa`() {
        assertTrue(ToolPolicy.followsDrag(PaintTool.MOVE_IMAGE))
        assertTrue(ToolPolicy.needsLayer(PaintTool.MOVE_IMAGE))
    }
}
