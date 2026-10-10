package com.mc.nbpaint

import androidx.compose.ui.geometry.Offset
import com.mc.nbpaint.model.CurveNode
import com.mc.nbpaint.model.CurveObject
import com.mc.nbpaint.model.CurveObjectRef
import com.mc.nbpaint.model.ObjectKind
import com.mc.nbpaint.model.TextObject
import com.mc.nbpaint.model.TextObjectRef
import com.mc.nbpaint.model.objectsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Qué hay bajo el dedo cuando una capa tiene varios objetos.
 *
 * Con un texto y una curva por capa esto era trivial, y con listas deja de serlo: hay que
 * elegir el objeto correcto, y hay que elegirlo entre objetos que se pisan. Un objeto encima
 * de otro no se coge por turnos, sino por el que se ve.
 *
 * Se prueba con [objectsOf] y no con una capa entera porque la capa lleva un `Bitmap` dentro, y
 * un `Bitmap` no se puede construir en un test de JVM. La regla de qué va encima de qué no
 * tiene nada que ver con los píxeles.
 */
class LayerObjectsTest {

    /** Un texto de 100x50 con la esquina en (x, y), que es su caja. */
    private fun texto(x: Float, y: Float) = TextObject(
        text = "Texto",
        offsetX = x,
        offsetY = y,
        baseSize = 40f,
        colorArgb = 0xFFFFFFFF.toInt()
    )

    /** Una curva en línea recta de (x, y) a (x + 100, y), con la caja de 100x10. */
    private fun curva(x: Float, y: Float) = CurveObject(
        nodes = mutableListOf(
            CurveNode(point = Offset(x, y)),
            CurveNode(point = Offset(x + 100f, y))
        )
    )

    /** El objeto de [objectsOf] que hay bajo el punto, con la misma regla que el lienzo. */
    private fun objetoEn(objetos: List<com.mc.nbpaint.model.LayerObjectRef>, x: Float, y: Float) =
        objetos.lastOrNull { it.contains(x, y) }

    @Test
    fun `una capa puede tener varios textos y varias curvas a la vez`() {
        val objetos = objectsOf(
            placed = null,
            texts = listOf(texto(10f, 10f), texto(200f, 200f)),
            curves = listOf(curva(10f, 300f), curva(200f, 300f))
        )

        assertEquals(4, objetos.size)
    }

    @Test
    fun `los objetos salen en el orden en que se dibujan`() {
        val objetos = objectsOf(
            placed = null,
            texts = listOf(texto(10f, 10f), texto(200f, 200f)),
            curves = listOf(curva(10f, 300f), curva(200f, 300f))
        )

        // Foto, luego textos en su orden, luego curvas en el suyo: los de arriba, al final.
        assertEquals(
            listOf(ObjectKind.TEXT, ObjectKind.TEXT, ObjectKind.CURVE, ObjectKind.CURVE),
            objetos.map { it.kind }
        )
    }

    @Test
    fun `se toma el texto de arriba de los que se pisan`() {
        val debajo = texto(0f, 0f)
        val encima = texto(0f, 0f)
        val objetos = objectsOf(null, listOf(debajo, encima), emptyList())

        val tomado = objetoEn(objetos, 20f, 20f)

        // Lo de arriba es lo último que se puso, y es lo que tapa al otro.
        assertSame(encima, (tomado as TextObjectRef).text)
    }

    @Test
    fun `se toma la curva de arriba de las que se pisan`() {
        val debajo = curva(0f, 100f)
        val encima = curva(0f, 100f)
        val objetos = objectsOf(null, emptyList(), listOf(debajo, encima))

        val tomado = objetoEn(objetos, 50f, 100f)

        assertSame(encima, (tomado as CurveObjectRef).curve)
    }

    @Test
    fun `una curva tapa a un texto aunque se cruzaran`() {
        val encima = curva(0f, 100f)
        val objetos = objectsOf(null, listOf(texto(0f, 90f)), listOf(encima))

        val tomado = objetoEn(objetos, 50f, 100f)

        // El orden de dibujo pone las curvas encima de los textos, y el que se coge es el que
        // se ve, no el que se puso antes.
        assertSame(encima, (tomado as CurveObjectRef).curve)
    }

    @Test
    fun `mover un texto de una lista no toca los demas`() {
        val quieto = texto(0f, 0f)
        val queSeMueve = texto(100f, 100f)

        TextObjectRef(queSeMueve).moveBy(50f, 0f)

        assertEquals(0f, quieto.offsetX, 0.01f)
        assertEquals(150f, queSeMueve.offsetX, 0.01f)
    }

    @Test
    fun `quitar un texto de la lista deja a los demas`() {
        val primero = texto(0f, 0f)
        val segundo = texto(0f, 100f)
        val tercero = texto(0f, 200f)
        val lista = mutableListOf(primero, segundo, tercero)

        lista.remove(segundo)

        assertEquals(listOf(primero, tercero), lista.toList())
    }

    @Test
    fun `en un sitio sin nada no hay objeto`() {
        val objetos = objectsOf(null, listOf(texto(0f, 0f)), emptyList())

        assertNull(objetoEn(objetos, 300f, 350f))
    }
}
