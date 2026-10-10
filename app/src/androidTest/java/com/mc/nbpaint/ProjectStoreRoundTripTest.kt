package com.mc.nbpaint

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mc.nbpaint.data.ProjectManifest
import com.mc.nbpaint.data.ProjectStore
import com.mc.nbpaint.model.LayerSnapshot
import com.mc.nbpaint.model.LayerTransform
import com.mc.nbpaint.model.ObjectBounds
import com.mc.nbpaint.model.PlacedSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Ida y vuelta del proyecto por el disco, con un `Bitmap` de verdad.
 *
 * Vive en instrumentación y no en la JVM por un motivo concreto: guardar y abrir un proyecto
 * necesita leer y escribir imágenes, y eso solo existe en Android. Y no es un detalle: el
 * manifiesto se probaba aparte y salía bien, mientras que el puente entre la capa y el
 * manifiesto se le olvidaba un campo y la transformación de la capa se perdía en silencio al
 * reabrir. Estos casos son los que miran ese puente.
 */
@RunWith(AndroidJUnit4::class)
class ProjectStoreRoundTripTest {

    private fun bitmap(color: Int, ancho: Int = 40, alto: Int = 30) =
        Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun carpeta(): File {
        val base = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "prueba-proyecto"
        )
        base.deleteRecursively()
        base.mkdirs()
        return base
    }

    @Test
    fun laPosicionYElTamanoDeLaCapaVuelvenAlReabrir() {
        val dir = carpeta()
        val capas = listOf(
            LayerSnapshot(bitmap(0xFFFFFFFF.toInt()), "Fondo", true, 1f),
            LayerSnapshot(
                bitmap = bitmap(0xFF3366CC.toInt()),
                name = "Foto",
                visible = true,
                opacity = 0.5f,
                transform = LayerTransform(offsetX = 17.5f, offsetY = -42f, scale = 1.75f)
            )
        )

        assertTrue("el proyecto se tiene que guardar", ProjectStore.writeTo(dir, capas, 200, 150, 1))

        val leido = ProjectStore.readFrom(dir)
        assertNotNull("y se tiene que poder abrir", leido)
        assertEquals(200, leido!!.width)
        assertEquals(150, leido.height)
        assertEquals(1, leido.activeIndex)
        assertEquals(2, leido.layers.size)

        // La capa que no se tocó sigue en su sitio.
        assertTrue(leido.layers[0].transform.esIdentity)
        // Y la que se movió y se estiró vuelve igual, no en blanco ni en su sitio.
        val t = leido.layers[1].transform
        assertEquals(17.5f, t.offsetX, 0.001f)
        assertEquals(-42f, t.offsetY, 0.001f)
        assertEquals(1.75f, t.scale, 0.001f)
        assertEquals(0.5f, leido.layers[1].opacity, 0.001f)
    }

    @Test
    fun laCapaVuelveConSuFotoYSuRecorteYAdemasSuTransformacion() {
        val dir = carpeta()
        val capas = listOf(
            LayerSnapshot(
                bitmap = bitmap(0xFFFFFFFF.toInt()),
                name = "Foto",
                visible = true,
                opacity = 1f,
                placed = PlacedSnapshot(
                    bitmap = bitmap(0xFFAA0000.toInt()),
                    offsetX = 3f,
                    offsetY = 4f,
                    scale = 0.5f,
                    crop = ObjectBounds(1f, 2f, 20f, 22f)
                ),
                transform = LayerTransform(offsetX = 9f, offsetY = 8f, scale = 2f)
            )
        )

        assertTrue(ProjectStore.writeTo(dir, capas, 100, 100, 0))
        val leido = ProjectStore.readFrom(dir)

        val capa = leido!!.layers[0]
        // Lo que hay detrás de la transformación no puede perderse por haberla añadido.
        assertTrue("la foto sigue puesta", capa.placed != null)
        assertEquals(0.5f, capa.placed!!.scale, 0.001f)
        assertEquals(
            ObjectBounds(1f, 2f, 20f, 22f),
            capa.placed!!.crop
        )
        assertEquals(2f, capa.transform.scale, 0.001f)
    }

    @Test
    fun unProyectoViejoSeAbreConLasCapasEnSuSitio() {
        val dir = carpeta()
        // Se escribe a mano un manifiesto como el de antes de que existiera la transformación:
        // diez campos, sin nada detrás. Es el caso de todo proyecto guardado antes de esto.
        val manifiesto = "200,150,0\n" +
            "layer_0.png|Foto|true|1.0|layer_0_placed.png|12.0|34.0|0.5|layer_0_text.txt|layer_0_curve.txt"
        val proyecto = File(dir, "viejo")
        proyecto.mkdirs()
        File(proyecto, "layer_0.png").outputStream().use { out ->
            bitmap(0xFF00FF00.toInt()).compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        File(proyecto, ProjectManifest.MANIFEST_NAME).writeText(manifiesto)

        val leido = ProjectStore.readFrom(proyecto)

        assertNotNull(leido)
        // Se abre entero y con la foto puesta, y con la capa en su sitio: que es como estaba.
        assertNotNull(leido!!.layers[0].bitmap)
        assertTrue(leido.layers[0].transform.esIdentity)
    }
}
