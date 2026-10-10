package com.mc.nbpaint.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de las reglas puras de [LayerStack].
 *
 * Son las que se corren en la JVM: no hay `Bitmap` ni `Canvas` de por medio, solo aritmética
 * de índices. La parte que sí usa bitmaps se verifica a mano en el dispositivo.
 *
 * Los escenarios se nombran con la lista de capas y cuál estaba activa, porque "borrar una
 * capa" a secas no dice nada: lo que importa es qué capa era la activa y dónde estaba la que
 * se borra. Por eso hay listas explícitas en cada ejemplo.
 */
class LayerStackTest {

    // ---- activeIndexAfterRemove ---------------------------------------------

    @Test
    fun `borrar una capa de arriba deja la activa en su lugar`() {
        // [Fondo, A, B, C] con B activa; se borra C (índice 3) y queda [Fondo, A, B].
        assertEquals(2, LayerStack.activeIndexAfterRemove(active = 2, removed = 3, newSize = 3))
    }

    @Test
    fun `borrar una capa de abajo hace bajar la activa para que siga siendo la misma`() {
        // [Fondo, A, B, C] con B activa; se borra A (índice 1) y B pasa a estar en el 1.
        assertEquals(1, LayerStack.activeIndexAfterRemove(active = 2, removed = 1, newSize = 3))
    }

    @Test
    fun `borrar la capa activa deja activa la que le quedó en su lugar`() {
        // [Fondo, A, B] con B activa; se borra B (índice 2) y queda [Fondo, A].
        assertEquals(1, LayerStack.activeIndexAfterRemove(active = 2, removed = 2, newSize = 2))
    }

    @Test
    fun `borrar la ultima capa deja la activa en la ultima que queda`() {
        // [Fondo, A] con A activa; se borra A y solo queda [Fondo].
        assertEquals(0, LayerStack.activeIndexAfterRemove(active = 1, removed = 1, newSize = 1))
    }

    @Test
    fun `borrar el fondo mueve la activa al inicio`() {
        // [Fondo, A, B, C] con el Fondo activo; se borra el Fondo y A queda en su lugar.
        assertEquals(0, LayerStack.activeIndexAfterRemove(active = 0, removed = 0, newSize = 3))
    }

    @Test
    fun `borrar con una sola capa deja la activa en cero`() {
        // El panel no deja borrar la última capa, pero la regla no debe romperse si se llama.
        assertEquals(0, LayerStack.activeIndexAfterRemove(active = 0, removed = 0, newSize = 1))
    }

    @Test
    fun `tras borrar la activa nunca queda apuntando fuera de la lista`() {
        for (activa in 0..4) {
            for (borrada in 0..4) {
                for (quedan in 1..5) {
                    val resultado = LayerStack.activeIndexAfterRemove(activa, borrada, quedan)
                    assertTrue(
                        "activa=$activa borrada=$borrada quedan=$quedan dio $resultado",
                        resultado in 0 until quedan
                    )
                }
            }
        }
    }

    // ---- activeIndexAfterMoveUp / MoveDown ----------------------------------

    @Test
    fun `subir una capa arrastra la activa si era esa`() {
        // [Fondo, A, B, C] con B activa; B sube y queda [Fondo, A, C, B].
        assertEquals(3, LayerStack.activeIndexAfterMoveUp(active = 2, index = 2))
    }

    @Test
    fun `subir una capa baja la que estaba justo encima`() {
        // [Fondo, A, B, C] con C activa; B sube y C queda en su lugar.
        assertEquals(2, LayerStack.activeIndexAfterMoveUp(active = 3, index = 2))
    }

    @Test
    fun `subir una capa que no toca la activa la deja como estaba`() {
        // [Fondo, A, B, C] con C activa; A sube y C no se mueve.
        assertEquals(3, LayerStack.activeIndexAfterMoveUp(active = 3, index = 1))
    }

    @Test
    fun `bajar una capa arrastra la activa si era esa`() {
        // [Fondo, A, B, C] con B activa; B baja y queda [Fondo, B, A, C].
        assertEquals(1, LayerStack.activeIndexAfterMoveDown(active = 2, index = 2))
    }

    @Test
    fun `bajar una capa sube la que estaba justo debajo`() {
        // [Fondo, A, B, C] con A activa; B baja a su lugar y A queda arriba: [Fondo, B, A, C].
        assertEquals(2, LayerStack.activeIndexAfterMoveDown(active = 1, index = 2))
    }

    @Test
    fun `bajar una capa que no toca la activa la deja como estaba`() {
        // [Fondo, A, B, C] con B activa; C baja y B sube a su lugar: [Fondo, A, C, B].
        assertEquals(3, LayerStack.activeIndexAfterMoveDown(active = 2, index = 3))
    }

    @Test
    fun `mover una capa nunca saca la activa de la lista`() {
        // Solo se prueban los movimientos que la fachada permite: subir no puede ser la
        // última capa ni bajar la primera. La regla no decide si el movimiento tiene sentido,
        // solo dónde queda la activa, así que fuera de ese rango no dice nada.
        for (total in 1..5) {
            for (activa in 0 until total) {
                for (indice in 0 until total - 1) {
                    val arriba = LayerStack.activeIndexAfterMoveUp(activa, indice)
                    assertTrue("subir: $activa en $indice de $total dio $arriba", arriba in 0 until total)
                }
                for (indice in 1 until total) {
                    val abajo = LayerStack.activeIndexAfterMoveDown(activa, indice)
                    assertTrue("bajar: $activa en $indice de $total dio $abajo", abajo in 0 until total)
                }
            }
        }
    }

    // ---- defaultLayerName ----------------------------------------------------

    @Test
    fun `las capas nuevas se numeran desde uno`() {
        assertEquals("Capa 1", LayerStack.defaultLayerName(1))
        assertEquals("Capa 2", LayerStack.defaultLayerName(2))
        assertEquals("Capa 10", LayerStack.defaultLayerName(10))
    }

    // ---- isEmpty -------------------------------------------------------------

    @Test
    fun `una capa con un solo pixel pintado no esta vacia`() {
        val pixeles = IntArray(4 * 4)
        pixeles[0] = 0xFF000000.toInt()
        assertFalse(LayerStack.isEmpty(pixeles, 4, 4))
    }

    @Test
    fun `una capa totalmente transparente esta vacia`() {
        assertTrue(LayerStack.isEmpty(IntArray(4 * 4), 4, 4))
    }

    // ---- needsSheetExpansion -------------------------------------------------

    @Test
    fun `una capa del tamano de la hoja no necesita ampliarse`() {
        assertFalse(LayerStack.needsSheetExpansion(600, 400, 600, 400))
    }

    @Test
    fun `una capa mas chica que la hoja necesita ampliarse`() {
        // El caso del recorte: 81x221 sobre una hoja de 600x400.
        assertTrue(LayerStack.needsSheetExpansion(81, 221, 600, 400))
    }

    @Test
    fun `una capa solo mas angosta necesita ampliarse`() {
        assertTrue(LayerStack.needsSheetExpansion(300, 400, 600, 400))
    }

    @Test
    fun `una capa solo mas baja necesita ampliarse`() {
        assertTrue(LayerStack.needsSheetExpansion(600, 200, 600, 400))
    }

    @Test
    fun `una capa mas grande que la hoja no se recorta al pintar`() {
        assertFalse(LayerStack.needsSheetExpansion(900, 700, 600, 400))
    }
}
