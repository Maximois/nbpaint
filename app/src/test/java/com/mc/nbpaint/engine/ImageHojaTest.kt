package com.mc.nbpaint.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El tamaño de la hoja cuando lo manda una imagen.
 *
 * Lo que se comprueba aquí es justo lo que se quejó el usuario: al abrir una imagen tiene que
 * quedar solo la imagen, sin la hoja alrededor.
 */
class ImageHojaTest {

    @Test
    fun `una imagen normal deja la hoja de su tamano`() {
        val tamano = ImageHoja.tamanoPara(1600, 1200)

        assertEquals(1600, tamano.ancho)
        assertEquals(1200, tamano.alto)
    }

    @Test
    fun `una imagen vertical tambien`() {
        val tamano = ImageHoja.tamanoPara(900, 2000)

        assertEquals(900, tamano.ancho)
        assertEquals(2000, tamano.alto)
    }

    @Test
    fun `una imagen enorme deja la hoja con el tope del lado largo`() {
        val tamano = ImageHoja.tamanoPara(5000, 3000)

        assertEquals(ImageHoja.LADO_MAXIMO, tamano.ancho)
        // Los dos lados se reducen por el mismo factor: si solo se tocara el grande, la hoja
        // dejaría de tener la proporción de la foto.
        assertEquals(2458, tamano.alto)
    }

    @Test
    fun `reducir una imagen enorme no la deforma`() {
        val original = 8000 to 4000
        val tamano = ImageHoja.tamanoPara(original.first, original.second)

        // Si se redujera un solo lado, la hoja dejaría de tener la proporción de la foto y la
        // imagen aparecería estirada al llenar el papel.
        val proporcionHoja = tamano.ancho.toFloat() / tamano.alto
        val proporcionFoto = original.first.toFloat() / original.second
        assertTrue(
            "proporción de la hoja $proporcionHoja contra $proporcionFoto de la foto",
            kotlin.math.abs(proporcionHoja - proporcionFoto) < 0.01f
        )
    }

    @Test
    fun `una imagen de un solo pixel deja una hoja dibujable`() {
        val tamano = ImageHoja.tamanoPara(0, 0)

        assertTrue(tamano.ancho >= 1)
        assertTrue(tamano.alto >= 1)
    }
}
