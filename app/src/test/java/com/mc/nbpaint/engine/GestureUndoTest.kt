package com.mc.nbpaint.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mover o escalar con el dedo genera un evento por movimiento. Si cada uno guardara su
 * estado, una arrastrada dejaría decenas de pasos de deshacer y el usuario no podría
 * deshacer "a medias": o volvía al principio del gesto o tenía que pulsarlo todo.
 */
class GestureUndoTest {

    @Test
    fun `el primer movimiento del gesto guarda el estado previo`() {
        val undo = GestureUndo()
        assertTrue(undo.pushOnce())
    }

    @Test
    fun `los siguientes movimientos del mismo gesto no guardan nada`() {
        val undo = GestureUndo()
        assertTrue(undo.pushOnce())
        assertFalse(undo.pushOnce())
        assertFalse(undo.pushOnce())
        assertFalse(undo.pushOnce())
    }

    @Test
    fun `soltar el dedo rearma para el siguiente gesto`() {
        val undo = GestureUndo()
        undo.pushOnce()
        undo.rearm()
        assertTrue("un gesto nuevo sí guarda su estado previo", undo.pushOnce())
    }

    @Test
    fun `rearmar sin haber empujado no rompe nada`() {
        val undo = GestureUndo()
        undo.rearm()
        assertTrue(undo.pushOnce())
    }

    @Test
    fun `una pinza de diez movimientos deja un solo paso de deshacer`() {
        val undo = GestureUndo()
        var pasos = 0
        repeat(10) { if (undo.pushOnce()) pasos++ }
        undo.rearm()
        assertTrue("diez movimientos, un paso de deshacer", pasos == 1)
    }
}
