package com.mc.nbpaint

import androidx.compose.ui.geometry.Offset
import com.mc.nbpaint.ui.PlacedImageTransform
import com.mc.nbpaint.ui.PlacedTransform
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Una imagen puesta en una capa se mueve con un dedo y se escala con la pinza. La regla que
 * más se nota cuando está mal es el anclaje: el punto que queda bajo los dedos tiene que
 * seguir bajo los dedos, y no hay forma de comprobarlo con un dedo real en un test, así que
 * estas pruebas fijan esa matemática.
 */
class PlacedImageTransformTest {

    private val centro = Offset(200f, 300f)

    private fun en(ox: Float, oy: Float, escala: Float) = PlacedTransform(ox, oy, escala)

    /** A qué punto de la imagen puesta, en coordenadas de la hoja, cae su esquina. */
    private fun esquina(t: PlacedTransform) = Offset(t.offsetX, t.offsetY)

    @Test
    fun `la escala queda anclada en el punto de foco`() {
        val antes = en(150f, 250f, 1f)
        // El punto de la imagen que está justo bajo el foco, en su propio espacio.
        val dentroDeLaImagen = (centro - esquina(antes)) / antes.scale

        val despues = PlacedImageTransform.zoom(antes, centro, 2f)

        // Tras el zoom, ese mismo punto de la imagen tiene que seguir en el foco.
        val dondeQuedo = esquina(despues) + dentroDeLaImagen * despues.scale
        assertEquals(centro.x, dondeQuedo.x, 0.01f)
        assertEquals(centro.y, dondeQuedo.y, 0.01f)
        assertEquals(2f, despues.scale, 0.01f)
    }

    @Test
    fun `repetir la misma pinza sigue creciendo sin saltar de lugar`() {
        var estado = en(150f, 250f, 1f)
        val dentroDeLaImagen = (centro - esquina(estado)) / estado.scale
        repeat(4) {
            val siguiente = PlacedImageTransform.zoom(estado, centro, 2f)
            val dondeQuedo = esquina(siguiente) + dentroDeLaImagen * siguiente.scale
            assertEquals(centro.x, dondeQuedo.x, 0.01f)
            assertEquals(centro.y, dondeQuedo.y, 0.01f)
            estado = siguiente
        }
        assertEquals(16f, estado.scale, 0.01f)
    }

    @Test
    fun `la escala tiene tope por arriba y por abajo`() {
        var estado = en(0f, 0f, 1f)
        repeat(30) { estado = PlacedImageTransform.zoom(estado, centro, 2f) }
        assertEquals(PlacedImageTransform.MAX_SCALE, estado.scale, 0.01f)

        repeat(60) { estado = PlacedImageTransform.zoom(estado, centro, 0.5f) }
        assertEquals(PlacedImageTransform.MIN_SCALE, estado.scale, 0.01f)
    }

    @Test
    fun `un zoom invalido deja la imagen como estaba`() {
        val antes = en(10f, 20f, 1.5f)
        assertEquals(antes, PlacedImageTransform.zoom(antes, centro, 0f))
        assertEquals(antes, PlacedImageTransform.zoom(antes, centro, -2f))
        assertEquals(antes, PlacedImageTransform.zoom(antes, centro, Float.NaN))
        assertEquals(antes, PlacedImageTransform.zoom(antes, centro, Float.POSITIVE_INFINITY))
    }

    @Test
    fun `una escala ya maxima no se mueve al intentar crecer mas`() {
        val antes = en(0f, 0f, PlacedImageTransform.MAX_SCALE)
        assertEquals(antes, PlacedImageTransform.zoom(antes, centro, 4f))
    }

    @Test
    fun `arrastrar mueve la imagen lo mismo que el dedo`() {
        val antes = en(100f, 100f, 2f)
        val desde = Offset(300f, 400f)

        val despues = PlacedImageTransform.drag(antes, desde, desde + Offset(30f, -12f), 100f, 100f)

        assertEquals(130f, despues.offsetX, 0.01f)
        assertEquals(88f, despues.offsetY, 0.01f)
        // Arrastrar no toca la escala.
        assertEquals(2f, despues.scale, 0.01f)
    }

    @Test
    fun `arrastrar varias veces acumula sin perderse`() {
        var estado = en(0f, 0f, 1f)
        repeat(10) {
            estado = PlacedImageTransform.drag(
                antes = estado,
                desde = Offset(0f, 0f),
                hasta = Offset(20f, 0f),
                origenX = estado.offsetX,
                origenY = estado.offsetY
            )
        }
        assertEquals(200f, estado.offsetX, 0.01f)
        assertEquals(0f, estado.offsetY, 0.01f)
    }
}
