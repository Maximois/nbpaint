package com.mc.nbpaint.data

import com.mc.nbpaint.model.LayerTransform
import com.mc.nbpaint.model.ObjectBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del formato del archivo de proyecto.
 *
 * Este archivo es la diferencia entre abrir el proyecto con todo dentro y abrir en blanco,
 * así que sus casos raros tienen que estar probados y no solo posibles.
 */
class ProjectManifestTest {

    private fun capa(
        name: String = "Capa 1",
        visible: Boolean = true,
        opacity: Float = 1f,
        placed: Boolean = false
    ) = ProjectManifest.Layer(
        fileName = "layer_0.png",
        name = name,
        visible = visible,
        opacity = opacity,
        placedFileName = if (placed) "layer_0_placed.png" else null,
        placedX = if (placed) 12f else null,
        placedY = if (placed) 34f else null,
        placedScale = if (placed) 0.5f else null
    )

    @Test
    fun `guarda y recupera lo mismo`() {
        val original = ProjectManifest.Project(
            width = 800,
            height = 600,
            activeIndex = 1,
            layers = listOf(
                capa(name = "Fondo", visible = true, opacity = 1f),
                capa(name = "Foto", visible = false, opacity = 0.4f, placed = true)
            )
        )
        val leido = ProjectManifest.decode(ProjectManifest.encode(original))
        assertNotNull("el proyecto tiene que volver", leido)
        assertEquals(800, leido!!.width)
        assertEquals(600, leido.height)
        assertEquals(1, leido.activeIndex)
        assertEquals(2, leido.layers.size)
        assertEquals("Foto", leido.layers[1].name)
        assertFalse("la opacidad se respeta", leido.layers[1].visible)
        assertEquals(0.4f, leido.layers[1].opacity, 0.0001f)
        assertTrue("la imagen puesta vuelve", leido.layers[1].hasPlaced)
        assertEquals(0.5f, leido.layers[1].placedScale!!, 0.0001f)
    }

    @Test
    fun `un nombre con barras verticales no desalinea lo de atras`() {
        // Este era el fallo grave: un `|` en el nombre movía todos los campos y la capa
        // volvía invisible con la foto perdida.
        val original = ProjectManifest.Project(
            width = 100, height = 100, activeIndex = 0,
            layers = listOf(capa(name = "Foto | con | barras"), capa(name = "Siguiente", placed = true))
        )
        val leido = ProjectManifest.decode(ProjectManifest.encode(original))
        assertNotNull(leido)
        assertEquals(2, leido!!.layers.size)
        assertEquals("Siguiente", leido.layers[1].name)
        assertTrue("la segunda capa conserva su foto", leido.layers[1].hasPlaced)
    }

    @Test
    fun `un nombre con salto de linea no inventa una capa`() {
        val original = ProjectManifest.Project(
            width = 100, height = 100, activeIndex = 0,
            layers = listOf(capa(name = "Salto\nde linea"), capa(name = "Otra"))
        )
        val leido = ProjectManifest.decode(ProjectManifest.encode(original))
        assertNotNull(leido)
        assertEquals("no debe aparecer una capa de más", 2, leido!!.layers.size)
    }

    @Test
    fun `lee los proyectos viejos que no tienen imagen puesta`() {
        val texto = """
            300,400,0
            layer_0.png|Fondo|true|1.0
            layer_1.png|Detalles|false|0.5
        """.trimIndent()
        val leido = ProjectManifest.decode(texto)
        assertNotNull(leido)
        assertEquals(300, leido!!.width)
        assertEquals(2, leido.layers.size)
        assertFalse(leido.layers[1].visible)
        assertEquals(0.5f, leido.layers[1].opacity, 0.0001f)
        assertFalse("no hay imagen puesta", leido.layers[0].hasPlaced)
    }

    @Test
    fun `una cabecera rota devuelve null en vez de reventar`() {
        // Antes esto lanzaba una excepción al leer y se perdía el proyecto entero.
        assertNull(ProjectManifest.decode("esto no son numeros,300,0"))
        assertNull(ProjectManifest.decode("300,400"))
        assertNull(ProjectManifest.decode(""))
        assertNull(ProjectManifest.decode("0,0,0"))
    }

    @Test
    fun `una capa ilegible se salta y se conserva el resto`() {
        val texto = """
            300,400,0
            layer_0.png|Fondo|true|1.0
            capa-rota
            layer_2.png|Foto|true|1.0|layer_2_placed.png|10|20|0.5
        """.trimIndent()
        val leido = ProjectManifest.decode(texto)
        assertNotNull("una capa mala no tira el proyecto", leido)
        assertEquals(2, leido!!.layers.size)
        assertEquals("Fondo", leido.layers[0].name)
        assertEquals("Foto", leido.layers[1].name)
    }

    @Test
    fun `una opacidad que no se entiende se deja en uno`() {
        val texto = "300,400,0\nlayer_0.png|Fondo|true|opacidadRota"
        val leido = ProjectManifest.decode(texto)
        assertNotNull(leido)
        assertEquals(1f, leido!!.layers[0].opacity, 0.0001f)
    }

    @Test
    fun `una foto puesta a medias no cuenta como foto`() {
        // Faltan los números: mejor perder la foto que devolverla corrida en el 0,0.
        val texto = "300,400,0\nlayer_0.png|Fondo|true|1.0|layer_0_placed.png|10"
        val leido = ProjectManifest.decode(texto)
        assertNotNull(leido)
        assertFalse(leido!!.layers[0].hasPlaced)
    }

    @Test
    fun `el texto y la curva de una capa vuelven a`() {
        // Si estos dos campos no volvieran, un proyecto con texto se abriría con la imagen y
        // nada más: el texto desaparecería sin decir nada, que es la peor forma de perderlo.
        val original = ProjectManifest.Project(
            width = 640, height = 480, activeIndex = 0,
            layers = listOf(
                capa(name = "Rotulo", placed = true).copy(
                    textFileName = "layer_0_text.txt",
                    curveFileName = "layer_0_curve.txt"
                ),
                capa(name = "Fondo")
            )
        )

        val leido = ProjectManifest.decode(ProjectManifest.encode(original))

        assertNotNull(leido)
        assertEquals("layer_0_text.txt", leido!!.layers[0].textFileName)
        assertEquals("layer_0_curve.txt", leido.layers[0].curveFileName)
        assertTrue("la foto de la misma capa no se pierde", leido.layers[0].hasPlaced)
        assertNull(leido.layers[1].textFileName)
        assertNull(leido.layers[1].curveFileName)
    }

    @Test
    fun `lee los proyectos que no tienen texto ni curva`() {
        // Es el caso de todo proyecto guardado antes de esta versión: el manifiesto se queda
        // con los ocho campos de siempre y sigue abriendo igual.
        val texto = "300,400,0\nlayer_0.png|Fondo|true|1.0|layer_0_placed.png|10|20|0.5"
        val leido = ProjectManifest.decode(texto)
        assertNotNull(leido)
        assertTrue(leido!!.layers[0].hasPlaced)
        assertNull(leido.layers[0].textFileName)
        assertNull(leido.layers[0].curveFileName)
    }

    @Test
    fun `una capa con texto y sin curva no se descoloca`() {
        val original = ProjectManifest.Project(
            width = 100, height = 100, activeIndex = 0,
            layers = listOf(capa(name = "Solo texto").copy(textFileName = "layer_0_text.txt"))
        )

        val leido = ProjectManifest.decode(ProjectManifest.encode(original))

        assertNotNull(leido)
        assertEquals("layer_0_text.txt", leido!!.layers[0].textFileName)
        assertNull(leido.layers[0].curveFileName)
    }

    @Test
    fun `el recorte de la imagen vuelve igual despues de guardar el proyecto`() {
        val original = ProjectManifest.Project(
            width = 100, height = 100, activeIndex = 0,
            layers = listOf(
                capa(name = "Foto", placed = true).copy(
                    textFileName = "layer_0_text.txt",
                    curveFileName = "layer_0_curve.txt",
                    placedCrop = ObjectBounds(10f, 20f, 60f, 70f)
                )
            )
        )

        val leido = ProjectManifest.decode(ProjectManifest.encode(original))

        assertEquals(ObjectBounds(10f, 20f, 60f, 70f), leido!!.layers[0].placedCrop)
        assertEquals("layer_0_text.txt", leido.layers[0].textFileName)
        assertEquals("layer_0_curve.txt", leido.layers[0].curveFileName)
    }

    @Test
    fun `un proyecto viejo se sigue abriendo con su foto entera`() {
        // Una línea guardada antes del recorte tiene diez campos. Al leerla, el recorte es
        // null y la foto sigue ahí: lo que no se puede es perder la imagen por no encontrar
        // cuatro números que nunca se escribieron.
        val linea = "layer_0.png|Foto|true|1.0|layer_0_placed.png|12.0|34.0|0.5|layer_0_text.txt|layer_0_curve.txt"
        val proyecto = ProjectManifest.decode("100,100,0\n$linea")

        assertNotNull(proyecto)
        assertTrue("la foto sigue puesta", proyecto!!.layers[0].hasPlaced)
        assertNull("y sin recorte", proyecto.layers[0].placedCrop)
        assertEquals(0.5f, proyecto.layers[0].placedScale)
    }

    @Test
    fun `un recorte con numeros raros no tira la foto`() {
        val linea = "layer_0.png|Foto|true|1.0|layer_0_placed.png|12.0|34.0|0.5||" +
            "layer_0_curve.txt||roto|20.0|60.0|70.0"
        val proyecto = ProjectManifest.decode("100,100,0\n$linea")

        assertNotNull(proyecto)
        assertTrue("la foto se conserva", proyecto!!.layers[0].hasPlaced)
        assertNull("pero el recorte se pierde", proyecto.layers[0].placedCrop)
    }

    // ---- La transformación de la capa ---------------------------------------

    @Test
    fun `la posicion y el tamano de la capa vuelven al reabrir`() {
        val original = ProjectManifest.Project(
            width = 100, height = 100, activeIndex = 0,
            layers = listOf(
                capa(name = "Fondo"),
                capa(name = "Foto", placed = true).copy(
                    transform = LayerTransform(offsetX = 12.5f, offsetY = -30f, scale = 1.75f)
                )
            )
        )

        val leido = ProjectManifest.decode(ProjectManifest.encode(original))

        assertNotNull(leido)
        assertTrue("la capa de abajo no se mueve", leido!!.layers[0].transform.esIdentity)
        val t = leido.layers[1].transform
        assertEquals(12.5f, t.offsetX, 0.001f)
        assertEquals(-30f, t.offsetY, 0.001f)
        assertEquals(1.75f, t.scale, 0.001f)
    }

    @Test
    fun `la transformacion se guarda con recorte y con placed, sin descolocar nada`() {
        val original = ProjectManifest.Project(
            width = 100, height = 100, activeIndex = 0,
            layers = listOf(
                capa(name = "Foto", placed = true).copy(
                    textFileName = "layer_0_text.txt",
                    curveFileName = "layer_0_curve.txt",
                    placedCrop = ObjectBounds(10f, 20f, 60f, 70f),
                    transform = LayerTransform(offsetX = 5f, offsetY = 6f, scale = 3f)
                )
            )
        )

        val leido = ProjectManifest.decode(ProjectManifest.encode(original))

        val capaLeida = leido!!.layers[0]
        // Lo de detrás de la transformación no se ha movido un campo: es lo que rompía la
        // lectura de la imagen puesta y su recorte.
        assertTrue(capaLeida.hasPlaced)
        assertEquals(0.5f, capaLeida.placedScale!!, 0.0001f)
        assertEquals(ObjectBounds(10f, 20f, 60f, 70f), capaLeida.placedCrop)
        assertEquals("layer_0_text.txt", capaLeida.textFileName)
        assertEquals("layer_0_curve.txt", capaLeida.curveFileName)
        assertEquals(3f, capaLeida.transform.scale, 0.001f)
    }

    @Test
    fun `una capa sin mover no escribe su transformacion`() {
        // Si se escribiera, las líneas viejas y las nuevas divergirían por lo mismo sin que
        // haya ninguna diferencia real entre ellas. La transformación no ocupa ningún campo
        // cuando es la identidad: la capa se abre en su sitio sin tocar nada.
        val texto = ProjectManifest.encode(
            ProjectManifest.Project(100, 100, 0, listOf(capa(name = "Fondo")))
        )

        val linea = texto.lines().last { it.isNotBlank() }
        val campos = linea.split("|")
        assertEquals("Fondo", campos[1])
        // Detrás de la opacidad no se escribe ningún número: ni la foto, ni el texto, ni la
        // curva, ni el recorte, ni la transformación. Es la misma línea de antes de que
        // existiera la transformación de capa.
        assertTrue(
            "una capa sin tocar no debe escribir nada detrás: ${campos.drop(4)}",
            campos.drop(4).all { it.isEmpty() }
        )
    }

    @Test
    fun `un proyecto viejo se abre con las capas en su sitio`() {
        // Una línea guardada antes de que existiera la transformación: se abre con la
        // identidad, que es como estaba entonces.
        val linea = "layer_0.png|Fondo|true|1.0|layer_0_placed.png|12.0|34.0|0.5||"
        val proyecto = ProjectManifest.decode("100,100,0\n$linea")

        assertNotNull(proyecto)
        assertTrue(proyecto!!.layers[0].transform.esIdentity)
        assertTrue("y la foto sigue puesta", proyecto.layers[0].hasPlaced)
    }

    @Test
    fun `una transformacion con numeros raros no tira la capa`() {
        // Con recorte detrás, la transformación son los campos 14 a 16: aquí hay un número
        // roto y otro que ni es número, así que la capa se queda como estaba.
        val linea = "layer_0.png|Foto|true|1.0|layer_0_placed.png|12.0|34.0|0.5||" +
            "layer_0_curve.txt|10.0|20.0|60.0|70.0|roto|y|2.0"
        val proyecto = ProjectManifest.decode("100,100,0\n$linea")

        assertNotNull(proyecto)
        val capaLeida = proyecto!!.layers[0]
        assertTrue("la capa se queda en su sitio", capaLeida.transform.esIdentity)
        assertTrue("y no se pierde lo de detrás", capaLeida.hasPlaced)
        assertEquals(ObjectBounds(10f, 20f, 60f, 70f), capaLeida.placedCrop)
    }

    @Test
    fun `una escala de cero no se cuela guardada`() {
        val original = ProjectManifest.Project(
            width = 100, height = 100, activeIndex = 0,
            layers = listOf(capa(name = "Rota").copy(transform = LayerTransform(scale = 0f)))
        )

        val leido = ProjectManifest.decode(ProjectManifest.encode(original))

        // Con escala cero la capa no se vería y no habría forma de volver a escogerla.
        assertTrue(leido!!.layers[0].transform.scale > 0f)
    }
}
