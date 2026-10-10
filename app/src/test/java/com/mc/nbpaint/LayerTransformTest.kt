package com.mc.nbpaint

import androidx.compose.ui.geometry.Offset
import com.mc.nbpaint.model.LayerTransform
import com.mc.nbpaint.model.cuadro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La matemática de la transformación de una capa.
 *
 * Es lo que separa "las capas tienen independencia" de "todas las capas van igual": si esta
 * cuenta se equivoca por un píxel, al mover o estirar una capa el dibujo se desplaza solo y
 * el toque acaba en un sitio que no es el que se ve. Por eso está sin `Bitmap` y se prueba
 * sola en la JVM.
 */
class LayerTransformTest {

    @Test
    fun `una capa sin tocar esta en su sitio`() {
        val t = LayerTransform()

        assertTrue(t.esIdentity)
        assertEquals(Offset(37f, 91f), t.aHoja(37f, 91f))
        assertEquals(Offset(37f, 91f), t.aContenido(37f, 91f))
    }

    @Test
    fun `mover la capa mueve su contenido y nada mas`() {
        val t = LayerTransform(offsetX = 40f, offsetY = -15f)

        assertEquals(Offset(52f, 5f), t.aHoja(12f, 20f))
        // Y el punto de la hoja se devuelve al punto del contenido que le corresponde.
        assertEquals(Offset(12f, 20f), t.aContenido(52f, 5f))
        assertFalse(t.esIdentity)
    }

    @Test
    fun `estirar la capa estira desde su esquina`() {
        val t = LayerTransform(offsetX = 10f, offsetY = 20f, scale = 2f)

        // El contenido no se mueve de la esquina: la hoja es la que se estira.
        assertEquals(Offset(10f, 20f), t.aHoja(0f, 0f))
        assertEquals(Offset(110f, 220f), t.aHoja(50f, 100f))
    }

    @Test
    fun `ir y volver por la hoja devuelve el mismo punto`() {
        val t = LayerTransform(offsetX = -33.5f, offsetY = 71.25f, scale = 1.7f)

        val enHoja = t.aHoja(120f, 45f)
        val vuelta = t.aContenido(enHoja.x, enHoja.y)

        assertEquals(120f, vuelta.x, 0.001f)
        assertEquals(45f, vuelta.y, 0.001f)
    }

    @Test
    fun `el zoom deja quieto lo que esta bajo el dedo`() {
        val t = LayerTransform(offsetX = 25f, offsetY = 60f, scale = 1.5f)
        val foco = Offset(300f, 400f)
        val antes = t.aContenido(foco.x, foco.y)

        val ampliado = t.conZoom(foco, 2f)

        // Lo que estaba bajo el dedo sigue bajo el dedo: es lo que evita que al ampliar una
        // capa el contenido se vaya para el lado contrario.
        assertEquals(foco.x, ampliado.aHoja(antes.x, antes.y).x, 0.001f)
        assertEquals(foco.y, ampliado.aHoja(antes.x, antes.y).y, 0.001f)
        assertEquals(3f, ampliado.scale, 0.001f)
    }

    @Test
    fun `el zoom se acota para que la capa siempre se pueda volver a tomar`() {
        val t = LayerTransform()
        val muyChica = t.conZoom(Offset(100f, 100f), 0.0001f)
        val muyGrande = t.conZoom(Offset(100f, 100f), 10_000f)

        assertTrue(muyChica.scale >= LayerTransform.MIN_SCALE)
        assertTrue(muyGrande.scale <= LayerTransform.MAX_SCALE)
    }

    @Test
    fun `un cambio de zoom invalido no mueve la capa`() {
        val t = LayerTransform(offsetX = 30f, scale = 2f)

        assertEquals(t, t.conZoom(Offset(10f, 10f), 0f))
        assertEquals(t, t.conZoom(Offset(10f, 10f), -2f))
        assertEquals(t, t.conZoom(Offset(10f, 10f), Float.NaN))
    }

    @Test
    fun `el cuadro de la capa dice cuanto ocupa en la hoja`() {
        val estirada = LayerTransform(offsetX = 100f, offsetY = 50f, scale = 2f)

        val c = estirada.cuadro(ancho = 300, alto = 200)

        assertEquals(100f, c.left, 0.001f)
        assertEquals(50f, c.top, 0.001f)
        assertEquals(700f, c.right, 0.001f)
        assertEquals(450f, c.bottom, 0.001f)
        assertFalse(c.isEmpty)
    }

    @Test
    fun `el cuadro se acota a la escala utilizable`() {
        assertTrue(LayerTransform(scale = 0f).acotada().scale >= LayerTransform.MIN_SCALE)
        assertTrue(LayerTransform(scale = -3f).acotada().scale >= LayerTransform.MIN_SCALE)
        assertTrue(LayerTransform(scale = 1_000f).acotada().scale <= LayerTransform.MAX_SCALE)
    }
}
