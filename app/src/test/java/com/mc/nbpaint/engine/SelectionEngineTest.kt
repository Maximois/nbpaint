package com.mc.nbpaint.engine

import com.mc.nbpaint.model.IntRect
import com.mc.nbpaint.model.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del motor de selección. Al trabajar sobre `IntArray` y números, corren en la JVM
 * sin emulador, que es justo lo que hace falta para la parte que más falla: la máscara, la
 * tolerancia y los bordes.
 */
class SelectionEngineTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) = SelectionEngine.argb(a, r, g, b)

    private val transparente = argb(0, 0, 0, 0)
    private val blanco = argb(255, 255, 255, 255)
    private val negro = argb(255, 0, 0, 0)
    private val rojo = argb(255, 220, 30, 30)

    /** Lienzo de [w] x [h] pintado con [color]. */
    private fun lienzo(w: Int, h: Int, color: Int) = IntArray(w * h) { color }

    @Test
    fun `el rectangulo respeta el borde exclusivo`() {
        val r = SelectionEngine.rectangle(2, 3, 6, 8, 20, 20).rect
        assertEquals(IntRect(2, 3, 6, 8), r)
        assertEquals(4, r.width)
        assertEquals(5, r.height)
        assertTrue(r.contains(2, 3))
        assertTrue(r.contains(5, 7))
        assertFalse("el borde derecho queda fuera", r.contains(6, 3))
        assertFalse("el borde inferior queda fuera", r.contains(2, 8))
    }

    @Test
    fun `el rectangulo se corrige si se arrastra al reves`() {
        val r = SelectionEngine.rectangle(9, 11, 4, 2, 20, 20).rect
        assertEquals(IntRect(4, 2, 9, 11), r)
    }

    @Test
    fun `el rectangulo se recorta a la hoja`() {
        val r = SelectionEngine.rectangle(-5, -5, 100, 100, 20, 30).rect
        assertEquals(IntRect(0, 0, 20, 30), r)
    }

    @Test
    fun `sin seleccion se toca todo y la vacia no toca nada`() {
        assertTrue(Selection.All.contains(123, 456))
        assertFalse(SelectionEngine.none().contains(0, 0))
        assertFalse(SelectionEngine.none().contains(9, 9))
    }

    @Test
    fun `seleccionar todo cubre la hoja entera`() {
        val s = SelectionEngine.selectAll(20, 30)
        assertTrue(s.contains(0, 0))
        assertTrue(s.contains(19, 29))
        assertFalse(s.contains(20, 29))
    }

    @Test
    fun `la seleccion inteligente con tolerancia 0 solo toma el color exacto`() {
        val w = 10; val h = 10
        val px = lienzo(w, h, blanco)
        px[5 * w + 5] = negro
        val s = SelectionEngine.smartSelect(px, w, h, 5, 5, tolerance = 0)
        assertEquals(1, s.selectedCount)
        assertTrue(s.contains(5, 5))
        assertFalse(s.contains(0, 0))
    }

    @Test
    fun `la tolerancia amplia agarra los colores parecidos`() {
        val w = 10; val h = 10
        val px = lienzo(w, h, blanco)
        // Gris medio con un vecino más oscuro: los dos caen dentro de la tolerancia, pero
        // el blanco de fondo queda fuera y por eso la mancha se detiene ahí.
        px[5 * w + 5] = argb(255, 200, 200, 200)
        px[5 * w + 6] = argb(255, 185, 185, 185)
        val s = SelectionEngine.smartSelect(px, w, h, 5, 5, tolerance = 40)
        assertTrue("el tono de partida entra", s.contains(5, 5))
        assertTrue("el tono cercano en x+1 también", s.contains(6, 5))
        assertFalse("el blanco no entra", s.contains(0, 0))
    }

    @Test
    fun `la mancha se detiene en el borde del objeto`() {
        val w = 8; val h = 8
        val px = lienzo(w, h, blanco)
        // Cuadrado negro de 4x4 en el centro.
        for (y in 2 until 6) for (x in 2 until 6) px[y * w + x] = negro
        val s = SelectionEngine.smartSelect(px, w, h, 3, 3, tolerance = 0)
        assertEquals(16, s.selectedCount)
        assertEquals(IntRect(2, 2, 6, 6), s.bounds())
    }

    @Test
    fun `la seleccion no conectada agarra los mismos colores separados`() {
        val w = 9; val h = 9
        val px = lienzo(w, h, blanco)
        px[0] = negro
        px[8 * w + 8] = negro
        val s = SelectionEngine.smartSelect(px, w, h, 0, 0, tolerance = 0, contiguous = false)
        assertEquals(2, s.selectedCount)
        assertTrue(s.contains(0, 0))
        assertTrue(s.contains(8, 8))
    }

    @Test
    fun `un lienzo de un solo color se selecciona entero`() {
        val w = 6; val h = 6
        val s = SelectionEngine.smartSelect(lienzo(w, h, rojo), w, h, 0, 0, tolerance = 10)
        assertEquals(36, s.selectedCount)
    }

    @Test
    fun `un punto fuera del lienzo no selecciona nada`() {
        val w = 5; val h = 5
        val s = SelectionEngine.smartSelect(lienzo(w, h, blanco), w, h, 99, 99, tolerance = 10)
        assertEquals(0, s.selectedCount)
        assertNull(s.bounds())
    }

    @Test
    fun `invertir la seleccion cambia lo de dentro por lo de fuera`() {
        val w = 4; val h = 4
        val px = lienzo(w, h, blanco)
        px[0] = negro
        val s = SelectionEngine.smartSelect(px, w, h, 0, 0, tolerance = 0)
        val inv = s.inverted()
        assertFalse(inv.contains(0, 0))
        assertTrue(inv.contains(3, 3))
    }

    @Test
    fun `el color de fondo se promedia con los pixeles que rodean la seleccion`() {
        val w = 5; val h = 5
        val px = lienzo(w, h, blanco)
        // Centro rojo: el relleno debe ser el blanco de alrededor, no el rojo.
        px[2 * w + 2] = rojo
        val s = SelectionEngine.smartSelect(px, w, h, 2, 2, tolerance = 0)
        val fondo = SelectionEngine.surroundingColor(px, s, w, h)
        assertEquals(blanco, fondo)
    }

    @Test
    fun `el contenido dibujado da un rectangulo util para recortar`() {
        val w = 10; val h = 10
        val px = IntArray(w * h) { transparente }
        for (y in 4 until 7) for (x in 3 until 9) px[y * w + x] = negro
        assertEquals(IntRect(3, 4, 9, 7), SelectionEngine.contentBounds(px, w, h))
    }

    @Test
    fun `una capa vacia no da contenido que recortar`() {
        val w = 8; val h = 8
        assertNull(SelectionEngine.contentBounds(IntArray(w * h) { transparente }, w, h))
    }

    @Test
    fun `los pixeles casi transparentes no cuentan como contenido`() {
        val w = 4; val h = 4
        val px = IntArray(w * h) { transparente }
        px[0] = argb(1, 0, 0, 0) // casi invisible
        assertNull("no debe contar como contenido", SelectionEngine.contentBounds(px, w, h, minAlpha = 8))
    }

    @Test
    fun `la tolerancia cero exige igualdad exacta`() {
        assertTrue(SelectionEngine.colorsClose(rojo, rojo, 0))
        assertFalse(SelectionEngine.colorsClose(rojo, blanco, 0))
        assertTrue(SelectionEngine.colorsClose(rojo, argb(255, 221, 31, 31), 2))
    }

    @Test
    fun `unir dos zonas deja todo lo que estaba en alguna de las dos`() {
        val a = SelectionEngine.rectangle(0, 0, 3, 3, 10, 10)
        val b = SelectionEngine.rectangle(7, 7, 10, 10, 10, 10)
        val unida = SelectionEngine.union(a, b, 10, 10)
        assertTrue("la primera zona sigue dentro", unida.contains(1, 1))
        assertTrue("la segunda zona se suma", unida.contains(8, 8))
        assertFalse("lo de en medio no se inventa", unida.contains(5, 5))
    }

    @Test
    fun `unir con toda la hoja deja toda la hoja`() {
        val todo = SelectionEngine.selectAll(6, 6)
        val trozo = SelectionEngine.rectangle(1, 1, 2, 2, 6, 6)
        assertEquals(36, (SelectionEngine.union(todo, trozo, 6, 6) as Selection.Mask).selectedCount)
    }

    @Test
    fun `unir dos marcos lejos del origen da una mascara del tamano de la hoja`() {
        // Este es el caso que se escapaba: antes el tamaño salia de la esquina inferior
        // derecha de los marcos, y con dos zonas en la esquina opuesta la mascara salia
        // mas pequena que la hoja. Todo lo de abajo del rectangulo de la mascara contaba
        // como fuera, y un recorte a esa seleccion vaciaba de mas esas franjas.
        val a = SelectionEngine.rectangle(2, 2, 4, 4, 10, 10)
        val b = SelectionEngine.rectangle(6, 6, 8, 8, 10, 10)
        val unida = SelectionEngine.union(a, b, 10, 10) as Selection.Mask

        assertEquals("la mascara mide lo mismo que la hoja", 10, unida.width)
        assertEquals("la mascara mide lo mismo que la hoja", 10, unida.height)
        assertTrue(unida.contains(3, 3))
        assertTrue(unida.contains(7, 7))
        // Lo que queda debajo y a la derecha de las dos zonas existe en la hoja, asi que
        // "no seleccionado" significa "fuera de las dos zonas", no "fuera de la mascara".
        assertFalse("lo de en medio sigue fuera", unida.contains(5, 5))
        assertEquals(8, unida.selectedCount)
    }

    @Test
    fun `unir con una mascara mas pequeña no pierde lo de la otra zona`() {
        // Máscara de 4x4 pegada al origen, como la que deja una selección inteligente
        // hecha sobre una capa más pequeña que la hoja. Antes la unión tomaba el tamaño de
        // esta, y lo que quedara fuera de su caja desaparecía de la selección.
        val chica = Selection.Mask(BooleanArray(16) { it % 4 < 2 }, 4, 4)
        val marco = SelectionEngine.rectangle(6, 6, 8, 8, 10, 10)
        val unida = SelectionEngine.union(marco, chica, 10, 10) as Selection.Mask

        assertEquals("la union mide lo mismo que la hoja", 10, unida.width)
        assertEquals("la union mide lo mismo que la hoja", 10, unida.height)
        assertTrue("lo que cubria la mascara pequeña se conserva", unida.contains(0, 0))
        assertTrue("lo que cubria la mascara pequeña se conserva", unida.contains(1, 0))
        assertFalse("lo que la mascara no cubria no se inventa", unida.contains(3, 0))
        assertTrue("el marco se suma", unida.contains(7, 7))
    }

    @Test
    fun `unir con una hoja de tamano cero no revienta`() {
        // La hoja vacía solo pasa antes de medir el visor, pero `contains` indexa la
        // máscara, así que tiene que quedar con al menos una celda.
        val marco = SelectionEngine.rectangle(0, 0, 2, 2, 10, 10)
        val unida = SelectionEngine.union(marco, marco, 0, 0) as Selection.Mask
        assertEquals(1, unida.width)
        assertEquals(1, unida.height)
    }

    @Test
    fun `invertir el marco deja fuera lo elegido`() {
        val marco = SelectionEngine.rectangle(2, 2, 5, 5, 8, 8)
        val invertido = SelectionEngine.invertRect(marco.rect, 8, 8)
        assertFalse("lo de dentro pasa a estar fuera", invertido.contains(3, 3))
        assertTrue("lo de fuera pasa a estar dentro", invertido.contains(0, 0))
        assertEquals(64 - 9, invertido.selectedCount)
    }

    @Test
    fun `lo de fuera de la seleccion vuelve a su sitio`() {
        val w = 4; val h = 4
        val previo = lienzo(w, h, blanco)
        val actual = lienzo(w, h, rojo)          // el relleno se pasó de la raya
        val seleccion = SelectionEngine.rectangle(1, 1, 3, 3, w, h)
        val devueltos = SelectionEngine.restoreOutside(actual, previo, seleccion, w, h)
        assertEquals("solo lo de fuera", 12, devueltos)
        assertEquals("dentro queda el relleno", rojo, actual[1 * w + 1])
        assertEquals("fuera vuelve al blanco", blanco, actual[0])
    }

    @Test
    fun `sin seleccion no se toca nada`() {
        val actual = lienzo(4, 4, rojo)
        assertEquals(0, SelectionEngine.restoreOutside(actual, lienzo(4, 4, blanco), Selection.All, 4, 4))
        assertEquals(rojo, actual[0])
    }

    @Test
    fun `un filtro solo cambia lo que hay dentro de la seleccion`() {
        // Es lo que hace la previsualización de filtros: se filtra la capa entera y
        // después se devuelve a su sitio lo que cae fuera. Antes el filtro se comía la
        // capa completa aunque la pantalla prometiese que el trabajo se quedaba en la
        // zona sombreada.
        val w = 4
        val h = 4
        val original = lienzo(w, h, blanco)
        val filtrado = lienzo(w, h, negro)          // como si el filtro lo hubiera dado vuelta
        val marco = SelectionEngine.rectangle(0, 0, 2, 4, w, h)   // mitad izquierda

        val devueltos = SelectionEngine.restoreOutside(filtrado, original, marco, w, h)

        assertEquals("solo se devuelve la mitad derecha", 8, devueltos)
        assertEquals("dentro de la seleccion manda el filtro", negro, filtrado[0 * w + 0])
        assertEquals("dentro de la seleccion manda el filtro", negro, filtrado[1 * w + 1])
        assertEquals("fuera vuelve al color original", blanco, filtrado[0 * w + 2])
        assertEquals("fuera vuelve al color original", blanco, filtrado[3 * w + 3])
    }

    @Test
    fun `un filtro con una mascara irregular solo pisa la mancha`() {
        val w = 6
        val h = 6
        val original = lienzo(w, h, blanco)
        val filtrado = lienzo(w, h, negro)
        val mascara = Selection.Mask(
            BooleanArray(w * h) { it % w in 0..2 && it / w in 0..1 }, w, h
        )

        val devueltos = SelectionEngine.restoreOutside(filtrado, original, mascara, w, h)

        assertEquals("solo lo de fuera de la mancha", 30, devueltos)
        assertEquals(negro, filtrado[0])
        assertEquals(negro, filtrado[1 * w + 2])
        assertEquals(blanco, filtrado[2 * w + 2])
        assertEquals(blanco, filtrado[3 * w + 0])
    }

    @Test
    fun `devolver lo de fuera no toca una mascara mas pequena que la hoja`() {
        // Una máscara más pequeña que la capa hace que `contains` dé falso fuera de su
        // caja. Con eso todo lo de abajo y a la derecha vuelve, que es lo correcto: la
        // selección no cubre esa zona, así que ahí manda el contenido original.
        val w = 6
        val h = 6
        val original = lienzo(w, h, blanco)
        val filtrado = lienzo(w, h, negro)
        val chica = Selection.Mask(BooleanArray(16) { true }, 4, 4)

        SelectionEngine.restoreOutside(filtrado, original, chica, w, h)

        assertEquals("dentro de la caja manda el filtro", negro, filtrado[3 * w + 3])
        assertEquals("fuera de la caja manda el original", blanco, filtrado[4 * w + 4])
        assertEquals("fuera de la caja en horizontal", blanco, filtrado[0 * w + 5])
    }

    @Test
    fun `la tolerancia llega hasta el tope de un canal`() {
        // El tope es 255 porque es lo que mide la diferencia entre dos canales. Si bajara,
        // la mitad de las combinaciones de colores quedarían fuera de alcance para siempre.
        assertEquals(255, SelectionEngine.MAX_TOLERANCE)
        assertTrue(SelectionEngine.DEFAULT_TOLERANCE < SelectionEngine.MAX_TOLERANCE)
    }

    @Test
    fun `con la tolerancia al tope se alcanza cualquier color`() {
        val blanco = argb(255, 255, 255, 255)
        val negro = argb(255, 0, 0, 0)
        assertTrue("a tope todo entra", SelectionEngine.colorsClose(blanco, negro, 255))
        assertFalse("a 0 solo el color exacto", SelectionEngine.colorsClose(blanco, negro, 0))
        assertTrue("a 0 el mismo color entra", SelectionEngine.colorsClose(blanco, blanco, 0))
    }
}
