package com.mc.nbpaint.model

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del recorte de un objeto: la imagen puesta, el texto o la curva.
 *
 * Lo que se prueba aquí es la regla, que es pura: la zona se acota al objeto, y al moverlo o
 * estirarlo la zona va con él. Si eso falla, el recorte acaba quitando una parte que no es la
 * que eligió quien lo hizo, y no hay forma de quitarlo más que deshaciendo.
 *
 * La imagen puesta lleva un `Bitmap` dentro, así que su vista no se puede construir en la JVM
 * sin Android. La regla que importa no cambia de un tipo a otro, y el texto y la curva cubren
 * la misma.
 */
class ObjectCropTest {

    private fun texto(x: Float = 10f, y: Float = 20f, escala: Float = 1f) = TextObject(
        text = "Hola",
        offsetX = x,
        offsetY = y,
        baseSize = 40f,
        colorArgb = 0xFF000000.toInt(),
        scale = escala
    ).also { it.setMeasured(200f, 100f) }

    private fun curva(from: Offset, to: Offset, escala: Float = 1f) = CurveObject(
        nodes = mutableListOf(CurveNode(from), CurveNode(to)),
        colorArgb = 0xFF000000.toInt(),
        width = 10f,
        scale = escala
    )

    @Test
    fun `sin recorte el objeto se ve entero`() {
        val ref = TextObjectRef(texto())
        assertNull(ref.crop())
        assertEquals(ref.fullBounds(), ref.bounds())
    }

    @Test
    fun `el recorte acota la zona que se ve`() {
        val ref = TextObjectRef(texto())
        ref.setCrop(ObjectBounds(20f, 30f, 120f, 80f))
        assertEquals(ObjectBounds(20f, 30f, 120f, 80f), ref.bounds())
    }

    @Test
    fun `una zona que se sale del objeto se acota a lo que hay`() {
        val ref = TextObjectRef(texto())
        // El texto va de (10, 20) a (210, 120). La zona empieza antes y acaba después.
        val aplicada = ref.recorteValido(ObjectBounds(-50f, -50f, 400f, 400f))
        assertEquals(ObjectBounds(10f, 20f, 210f, 120f), aplicada)
        ref.setCrop(aplicada)
        assertEquals("acotada al objeto, no a lo pedido", ref.fullBounds(), ref.crop())
    }

    @Test
    fun `una zona que no pica nada se descarta y no borra el recorte anterior`() {
        val ref = TextObjectRef(texto())
        ref.setCrop(ObjectBounds(20f, 30f, 120f, 80f))
        val valida = ref.recorteValido(ObjectBounds(500f, 500f, 600f, 600f))
        assertNull("una zona que no pica nada no es un recorte", valida)
        assertEquals("el recorte anterior sigue puesto", ObjectBounds(20f, 30f, 120f, 80f), ref.crop())
    }

    @Test
    fun `mover el objeto se lleva el recorte`() {
        val ref = TextObjectRef(texto())
        ref.setCrop(ObjectBounds(20f, 30f, 120f, 80f))
        ref.moveBy(100f, 5f)
        assertEquals(ObjectBounds(120f, 35f, 220f, 85f), ref.crop())
    }

    @Test
    fun `llevar el objeto a otro sitio tambien se lleva el recorte`() {
        val ref = TextObjectRef(texto())
        ref.setCrop(ObjectBounds(20f, 30f, 120f, 80f))
        ref.moveTo(0f, 0f)
        assertEquals(ObjectBounds(10f, 10f, 110f, 60f), ref.crop())
    }

    @Test
    fun `estirar el objeto estira el recorte desde la esquina quieta`() {
        val ref = TextObjectRef(texto())
        ref.setCrop(ObjectBounds(20f, 30f, 120f, 80f))
        ref.applyScale(2f)
        // La esquina superior izquierda (10, 20) se queda clavada, así que la zona se estira
        // a lo largo de ella y no se desplaza.
        assertEquals(ObjectBounds(30f, 40f, 230f, 140f), ref.crop())
    }

    @Test
    fun `quitar el recorte devuelve el objeto entero`() {
        val ref = TextObjectRef(texto())
        ref.setCrop(ObjectBounds(20f, 30f, 120f, 80f))
        ref.setCrop(null)
        assertNull(ref.crop())
        assertEquals(ref.fullBounds(), ref.bounds())
    }

    @Test
    fun `encoger un texto no lo reescribe ni le cambia la fuente`() {
        val original = texto()
        val ref = TextObjectRef(original)
        ref.setCrop(ObjectBounds(10f, 20f, 110f, 60f))
        assertEquals("Hola", original.text)
        assertEquals(40f, original.baseSize, 0.001f)
        assertEquals(TextTypeface.SANS, original.typeface)
    }

    @Test
    fun `recortar una curva deja los nodos donde estaban`() {
        val c = curva(Offset(10f, 10f), Offset(110f, 90f))
        val ref = CurveObjectRef(c)
        val zona = ref.recorteValido(ObjectBounds(0f, 0f, 60f, 50f))
        // La caja de una curva lleva medio grosor de trazo por cada lado, así que la zona se
        // acota a eso: por debajo del nodo no hay dibujo, y recortarlo no quita nada.
        assertEquals(ObjectBounds(5f, 5f, 60f, 50f), zona)
        ref.setCrop(zona)
        assertEquals("el nodo sigue en su sitio", Offset(10f, 10f), c.nodes[0].point)
        assertEquals("y el otro también", Offset(110f, 90f), c.nodes[1].point)
    }

    @Test
    fun `mover una curva se lleva el recorte`() {
        val ref = CurveObjectRef(curva(Offset(10f, 10f), Offset(110f, 90f)))
        ref.setCrop(ObjectBounds(10f, 10f, 60f, 50f))
        ref.moveBy(5f, 5f)
        assertEquals(ObjectBounds(15f, 15f, 65f, 55f), ref.crop())
    }

    @Test
    fun `estirar una curva estira el recorte con la misma geometria`() {
        val c = curva(Offset(10f, 10f), Offset(110f, 90f))
        val ref = CurveObjectRef(c)
        ref.setCrop(ObjectBounds(20f, 20f, 60f, 50f))
        ref.applyScale(2f)
        // El punto fijo es el nodo más a la izquierda y arriba, que aquí está en (10, 10). La
        // distancia de la esquina del recorte a ese punto se dobla, y no se desplaza: con otro
        // ancla la zona recortada se separaría de la línea y se vería un corte descuadrado.
        assertEquals(30f, ref.crop()!!.left, 0.001f)
        assertEquals(30f, ref.crop()!!.top, 0.001f)
        assertEquals("el nodo fijo no se mueve", 10f, c.nodes[0].point.x, 0.001f)
        val caja = ref.fullBounds()
        val zona = ref.crop()!!
        assertTrue(
            "el recorte sigue dentro de la curva",
            zona.left >= caja.left && zona.right <= caja.right
        )
    }

    @Test
    fun `un objeto recortado ya no se toma por fuera de la zona`() {
        val ref = TextObjectRef(texto())
        ref.setCrop(ObjectBounds(20f, 30f, 120f, 80f))
        assertTrue("dentro de la zona se toma", ref.contains(60f, 50f))
        assertEquals("fuera de la zona no", false, ref.contains(200f, 100f))
    }
}
