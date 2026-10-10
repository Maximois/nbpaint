package com.mc.nbpaint.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la pila de deshacer/rehacer.
 *
 * La pila es genérica sobre el tipo de entrada justamente para poder probarla así: lo que
 * importa aquí son las reglas de la pila (tope, invalidación del redo, entradas que no se
 * pueden aplicar) y no qué significa deshacer un filtro o combinar capas, que es lo que
 * decide el `DrawingViewModel`. Por eso se usan entradas de texto inventadas en vez de las
 * de la app, que llevan `Bitmap` y no se pueden tocar en la JVM.
 */
class HistoryControllerTest {

    /**
     * Pila de prueba: anota qué entradas se van aplicando y puede configurarse para que
     * aplicar una entrada falle, como pasa cuando la capa que guardaba ya no existe.
     */
    private class Banco(
        private val max: Int = 20,
        private val falla: (String) -> Boolean = { false }
    ) {
        val aplicadas = mutableListOf<String>()
        val direcciones = mutableListOf<HistoryDirection>()
        lateinit var pila: HistoryController<String>

        fun armar() {
            pila = HistoryController(max) { entrada, direccion ->
                if (falla(entrada)) return@HistoryController null
                aplicadas += entrada
                direcciones += direccion
                entrada
            }
        }
    }

    @Test
    fun `arranca sin nada que deshacer ni rehacer`() {
        val banco = Banco()
        banco.armar()
        assertFalse(banco.pila.canUndo)
        assertFalse(banco.pila.canRedo)
        assertFalse(banco.pila.undo())
        assertFalse(banco.pila.redo())
    }

    @Test
    fun `deshacer aplica la entrada y habilita el redo`() {
        val banco = Banco()
        banco.armar()
        banco.pila.push("a")

        assertTrue(banco.pila.undo())
        assertEquals(listOf("a"), banco.aplicadas)
        assertTrue(banco.pila.canRedo)
        assertFalse(banco.pila.canUndo)
    }

    @Test
    fun `rehacer vuelve al punto anterior`() {
        val banco = Banco()
        banco.armar()
        banco.pila.push("a")
        banco.pila.undo()
        assertTrue(banco.pila.redo())
        // Se aplicó dos veces la misma entrada: al deshacer y al rehacer.
        assertEquals(listOf("a", "a"), banco.aplicadas)
        assertTrue(banco.pila.canUndo)
        assertFalse(banco.pila.canRedo)
    }

    @Test
    fun `los puntos se deshacen en orden inverso`() {
        val banco = Banco()
        banco.armar()
        banco.pila.push("primero")
        banco.pila.push("segundo")
        banco.pila.push("tercero")

        banco.pila.undo()
        banco.pila.undo()
        banco.pila.undo()
        assertEquals(listOf("tercero", "segundo", "primero"), banco.aplicadas)
    }

    @Test
    fun `hacer algo nuevo borra el redo`() {
        val banco = Banco()
        banco.armar()
        banco.pila.push("a")
        banco.pila.undo()
        assertTrue(banco.pila.canRedo)

        banco.pila.push("b")
        assertFalse("el redo ya no aplica: el camino cambió", banco.pila.canRedo)
    }

    @Test
    fun `tira lo mas viejo cuando se pasa del tope`() {
        val banco = Banco(max = 3)
        banco.armar()
        repeat(5) { banco.pila.push("p$it") }

        banco.pila.undo()
        banco.pila.undo()
        banco.pila.undo()
        assertFalse("no debería quedar un cuarto punto", banco.pila.canUndo)
    }

    @Test
    fun `el redo tambien respeta el tope`() {
        val banco = Banco(max = 2)
        banco.armar()
        repeat(4) { banco.pila.push("p$it") }
        repeat(4) { banco.pila.undo() }
        repeat(3) { banco.pila.redo() }
        assertFalse("no debería quedar un cuarto punto de redo", banco.pila.canRedo)
    }

    @Test
    fun `una entrada que no se puede aplicar se descarta y no traba la pila`() {
        // Simula deshacer un punto de una capa que después fue borrada: ya no se puede.
        val banco = Banco(falla = { it == "perdida" })
        banco.armar()
        banco.pila.push("perdida")
        banco.pila.push("sana")

        assertTrue(banco.pila.undo())
        assertEquals("solo se aplicó la sana", listOf("sana"), banco.aplicadas)
        assertTrue("la pila sigue viva para el siguiente punto", banco.pila.canUndo)

        assertTrue(banco.pila.undo())
        assertFalse("y se puede seguir hasta el principio", banco.pila.canUndo)
        assertFalse(banco.pila.undo())
    }

    @Test
    fun `descartar una entrada que no aplica no la manda al redo`() {
        val banco = Banco(falla = { it == "perdida" })
        banco.armar()
        banco.pila.push("perdida")
        banco.pila.undo()

        assertFalse("lo que no se aplicó no se puede rehacer", banco.pila.canRedo)
    }

    @Test
    fun `vaciar tira el historial entero`() {
        val banco = Banco()
        banco.armar()
        banco.pila.push("a")
        banco.pila.undo()
        banco.pila.clear()
        assertFalse(banco.pila.canUndo)
        assertFalse(banco.pila.canRedo)
    }

    @Test
    fun `deshacer y rehacer en cadena deja las pilas como estaban`() {
        val banco = Banco()
        banco.armar()
        banco.pila.push("a")
        banco.pila.push("b")

        banco.pila.undo()
        banco.pila.redo()
        assertTrue(banco.pila.canUndo)
        assertFalse(banco.pila.canRedo)
        assertEquals(listOf("b", "b"), banco.aplicadas)
    }

    @Test
    fun `la pila dice en que sentido se aplica cada entrada`() {
        val banco = Banco()
        banco.armar()
        banco.pila.push("a")
        banco.pila.undo()
        banco.pila.redo()
        banco.pila.undo()

        // Deshacer, rehacer y volver a deshacer: la pila no puede adivinarlo, se lo avisamos.
        assertEquals(
            listOf(HistoryDirection.Undo, HistoryDirection.Redo, HistoryDirection.Undo),
            banco.direcciones
        )
    }
}
