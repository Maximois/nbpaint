package com.mc.nbpaint.data

import com.mc.nbpaint.model.CurveNode
import com.mc.nbpaint.model.CurveNodeSnapshot
import com.mc.nbpaint.model.CurveObject
import com.mc.nbpaint.model.CurveSnapshot
import com.mc.nbpaint.model.ObjectBounds
import com.mc.nbpaint.model.TextAlign
import com.mc.nbpaint.model.TextObject
import com.mc.nbpaint.model.TextSnapshot
import com.mc.nbpaint.model.TextTypeface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardar un proyecto y volverlo a abrir tiene que devolver lo mismo que había. Con el texto
 * y la curva esto no es un detalle: los dos son objetos con estilo, y un solo campo que se
 * pierda en el camino convierte el texto en un rectángulo vacío al otro lado.
 *
 * Estas pruebas fijan el viaje completo: construir el objeto, escribirlo, leerlo y comparar.
 */
class ObjectRecordsTest {

    private fun texto() = TextSnapshot(
        text = "Hola",
        offsetX = 40f,
        offsetY = 60f,
        baseSize = 72f,
        colorArgb = 0xFF3366CC.toInt(),
        typeface = TextTypeface.SERIF,
        bold = true,
        italic = false,
        align = TextAlign.CENTER,
        lineSpacingFactor = 1.4f,
        boxWidth = 500f,
        scale = 1.25f
    )

    private fun curva() = CurveSnapshot(
        nodes = listOf(
            CurveNodeSnapshot(10f, 20f, 40f, 5f, 0f, 35f, false),
            CurveNodeSnapshot(120f, 90f, 150f, 90f, 90f, 90f, true)
        ),
        colorArgb = 0xFFE0A020.toInt(),
        width = 12f,
        scale = 1.5f
    )

    @Test
    fun `el texto vuelve igual despues de guardarlo`() {
        val original = texto()

        val recuperado = ObjectRecords.decodeText(ObjectRecords.encodeText(original))

        assertEquals(original, recuperado)
    }

    @Test
    fun `un texto de varias lineas no parte el archivo`() {
        // El motivo de que el texto vaya en su propio archivo: aquí, si se metiera en la línea
        // del manifiesto, el salto de línea lo partiría y la capa se perdería al abrir.
        val original = texto().copy(text = "Primera\nSegunda\nTercera")

        val recuperado = ObjectRecords.decodeText(ObjectRecords.encodeText(original))

        assertEquals("Primera\nSegunda\nTercera", recuperado?.text)
    }

    @Test
    fun `las barras verticales del texto sobreviven`() {
        // La barra es el separador de campos. Si no se escapa, un texto con una barra
        // desalinearía el estilo y volvería con otra fuente.
        val original = texto().copy(text = "a|b\\c|d")

        val recuperado = ObjectRecords.decodeText(ObjectRecords.encodeText(original))

        assertEquals("a|b\\c|d", recuperado?.text)
    }

    @Test
    fun `un texto vacio o con espacios rare se conserva`() {
        listOf("", "   ", "\n", " ").forEach { contenido ->
            val original = texto().copy(text = contenido)
            val recuperado = ObjectRecords.decodeText(ObjectRecords.encodeText(original))
            assertEquals(contenido, recuperado?.text)
        }
    }

    @Test
    fun `la curva vuelve igual con sus nodos y sus tiradores`() {
        val original = curva()

        val recuperado = ObjectRecords.decodeCurve(ObjectRecords.encodeCurve(original))

        assertEquals(original, recuperado)
    }

    @Test
    fun `una curva con muchos nodos conserva el orden`() {
        val original = curva().copy(
            nodes = (0 until 60).map {
                CurveNodeSnapshot(it * 7f, it * 3f, it * 7f + 5f, it * 3f - 2f, it * 7f - 5f, it * 3f + 2f, it % 3 == 0)
            }
        )

        val recuperado = ObjectRecords.decodeCurve(ObjectRecords.encodeCurve(original))

        assertEquals(60, recuperado?.nodes?.size)
        assertEquals(original.nodes, recuperado?.nodes)
    }

    @Test
    fun `un archivo que no es nuestro se ignora en vez de romper la lectura`() {
        // Un archivo corrupto no puede impedir abrir el proyecto: se pierde ese objeto y se
        // conservan las capas con sus imágenes.
        assertNull(ObjectRecords.decodeText("esto no es un archivo de texto"))
        assertNull(ObjectRecords.decodeText(""))
        assertNull(ObjectRecords.decodeCurve(""))
        assertNull(ObjectRecords.decodeCurve("9|1|2|3"))
    }

    @Test
    fun `un texto con numeros raros se rechaza en vez de dejar un objeto imposible`() {
        // Una escala de cero haría que el texto se midiera con un tamaño cero y no se pudiera
        // volver a tomar: mejor no leerlo que abrir un objeto que no se puede agarrar.
        assertNull(ObjectRecords.decodeText("1|Hola|10|10|0|255\nSANS|false|false|LEFT\n1.1|0|0"))
        assertNull(ObjectRecords.decodeText("1|Hola|10|10|40|255\nSANS|false|false|LEFT\n1.1|0|-2"))
        assertNull(ObjectRecords.decodeText("1|Hola|10|10|40|255\nNINGUNA|false|false|LEFT\n1.1|0|1"))
    }

    @Test
    fun `una curva con menos de dos nodos no se dibuja y no se lee`() {
        val unNodo = curva().copy(nodes = listOf(curva().nodes.first()))

        assertNull(ObjectRecords.decodeCurve(ObjectRecords.encodeCurve(unNodo)))
    }

    @Test
    fun `el objeto se reconstruye con su estilo puesto`() {
        val objeto = texto().toObject()

        assertEquals(TextTypeface.SERIF, objeto.typeface)
        assertTrue(objeto.bold)
        assertEquals(TextAlign.CENTER, objeto.align)
        // El tamaño final sale de la base por la escala, no de la base sola.
        assertEquals(90f, objeto.sizePx, 0.01f)
    }

    @Test
    fun `el objeto reconstruido vuelve a dar el mismo archivo`() {
        // El viaje de ida y vuelta tiene que ser estable: si al rehidratar y volver a escribir
        // el archivo cambiara algo, el autoguardado iría degradando el proyecto cada vez.
        val unaVez = ObjectRecords.encodeText(texto())
        val otraVez = ObjectRecords.encodeText(ObjectRecords.decodeText(unaVez)!!.toObject().toSnapshot())

        assertEquals(unaVez, otraVez)
    }

    @Test
    fun `la curva se reconstruye con los nodos`() {
        val objeto = curva().toObject()

        assertEquals(2, objeto.nodes.size)
        // El grosor guardado es el de escritura; al dibujar se le aplica la escala de la caja.
        assertEquals(12f, objeto.width, 0.01f)
        assertEquals(18f, objeto.drawWidth, 0.01f)
        assertNotNull(objeto.nodes[0])
        assertEquals(0f, objeto.nodes[0].inHandle.x, 0.01f)
    }

    @Test
    fun `varios textos de una capa vuelven todos y en su orden`() {
        // El orden es el de dibujo, así que no es un detalle: si al leer se desordenaran, un
        // rótulo acabaría debajo del subtítulo que se puso después.
        val original = listOf(
            texto(),
            texto().copy(text = "Subtítulo", offsetY = 300f, typeface = TextTypeface.CURSIVE),
            texto().copy(text = "Tercero", offsetY = 500f, bold = false, align = TextAlign.RIGHT)
        )

        val recuperados = ObjectRecords.decodeTexts(ObjectRecords.encodeTexts(original))

        assertEquals(original, recuperados)
    }

    @Test
    fun `varias curvas de una capa vuelven todas y en su orden`() {
        val original = listOf(
            curva(),
            curva().copy(colorArgb = 0xFF2050E0.toInt(), nodes = curva().nodes.map { n ->
                CurveNodeSnapshot(n.x + 500f, n.y + 40f, n.outHandleX, n.outHandleY, n.inHandleX, n.inHandleY, n.isCorner)
            })
        )

        val recuperados = ObjectRecords.decodeCurves(ObjectRecords.encodeCurves(original))

        assertEquals(original, recuperados)
    }

    @Test
    fun `un texto de una capa con lineas y barras no rompe a los de al lado`() {
        // El separador entre registros es una línea suelta, y el texto de cada registro lleva
        // sus saltos escapados. Un texto con saltos no puede tragarse al siguiente, que es
        // justo lo que pasaba cuando el contenido iba en la línea del manifiesto.
        val original = listOf(
            texto().copy(text = "Primera\nSegunda"),
            texto().copy(text = "a|b\\c", offsetY = 400f)
        )

        val recuperados = ObjectRecords.decodeTexts(ObjectRecords.encodeTexts(original))

        assertEquals(listOf("Primera\nSegunda", "a|b\\c"), recuperados.map { it.text })
    }

    @Test
    fun `un archivo de los de antes con un solo objeto se sigue leyendo`() {
        // Los proyectos que había antes de las listas tienen un archivo por capa con un único
        // objeto y sin separadores. Un archivo sin `#` es un archivo con un solo registro, así
        // que esos proyectos tienen que abrir igual.
        assertEquals(listOf(texto()), ObjectRecords.decodeTexts(ObjectRecords.encodeText(texto())))
        assertEquals(listOf(curva()), ObjectRecords.decodeCurves(ObjectRecords.encodeCurve(curva())))
    }

    @Test
    fun `un registro roto no se lleva por delante los que estan a su lado`() {
        // Si el segundo texto de tres no se entiende, los otros dos tienen que salir igual: es
        // lo mismo que ya se hacía con un archivo entero roto, pero ahora registro a registro.
        val archivo = listOf(texto(), texto().copy(offsetX = 200f), texto().copy(offsetY = 700f))
            .joinToString("\n#\n") { t ->
                if (t.offsetX == 200f) "basura que no se entiende" else ObjectRecords.encodeText(t)
            }

        val recuperados = ObjectRecords.decodeTexts(archivo)

        assertEquals(listOf(texto().text, texto().copy(offsetY = 700f).text), recuperados.map { it.text })
    }

    @Test
    fun `una capa sin textos ni curvas no da error al leer`() {
        // Una lista vacía se lee como una lista vacía, no como un fallo. El archivo ni siquiera
        // llega a escribirse cuando no hay nada, pero si aparece uno vacío tiene que ser inofensivo.
        assertEquals(emptyList<TextSnapshot>(), ObjectRecords.decodeTexts(""))
        assertEquals(emptyList<CurveSnapshot>(), ObjectRecords.decodeCurves(""))
    }

    @Test
    fun `el recorte de un texto vuelve igual despues de guardarlo`() {
        val original = texto().copy(crop = ObjectBounds(50f, 70f, 300f, 200f))

        val recuperado = ObjectRecords.decodeText(ObjectRecords.encodeText(original))

        assertEquals(original.crop, recuperado?.crop)
        assertEquals(original, recuperado)
    }

    @Test
    fun `el recorte de una curva vuelve igual despues de guardarlo`() {
        val original = curva().copy(crop = ObjectBounds(5f, 5f, 90f, 70f))

        val recuperado = ObjectRecords.decodeCurve(ObjectRecords.encodeCurve(original))

        assertEquals(original.crop, recuperado?.crop)
    }

    @Test
    fun `un texto guardado sin recorte se sigue leyendo entero`() {
        // Es el archivo de antes de que el recorte existiera: tres campos en la última línea y
        // ni uno más. Si al leerlo se buscara un cuarto campo a la fuerza, el texto se perdería.
        val archivo = texto().let { t ->
            ObjectRecords.encodeText(t).lines().let { lineas ->
                val ultima = lineas.last().split('|').take(3).joinToString("|")
                (lineas.dropLast(1) + ultima).joinToString("\n")
            }
        }

        val recuperado = ObjectRecords.decodeText(archivo)

        assertNotNull("el texto tiene que salir", recuperado)
        assertNull("y sin recorte", recuperado?.crop)
        assertEquals("Hola", recuperado?.text)
    }

    @Test
    fun `una curva guardada sin recorte se sigue leyendo entera`() {
        // La cabecera de una curva de antes del recorte tiene cuatro campos, no ocho.
        val archivo = curva().let { c ->
            ObjectRecords.encodeCurve(c).lines().let { lineas ->
                val cabecera = lineas.first().split('|').take(4).joinToString("|")
                (listOf(cabecera) + lineas.drop(1)).joinToString("\n")
            }
        }

        val recuperado = ObjectRecords.decodeCurve(archivo)

        assertNotNull("la curva tiene que salir", recuperado)
        assertNull("y sin recorte", recuperado?.crop)
    }

    @Test
    fun `un recorte con numeros raros no tira el texto entero`() {
        // Perder el recorte es un Cosmetics; perder el texto entero porque el recorte traía un
        // número raro sería un desastre. El objeto se abre entero.
        val archivo = texto().let { t ->
            val sucio = t.copy(crop = ObjectBounds(1f, 2f, 3f, 4f))
            ObjectRecords.encodeText(sucio).replace("|4.0", "|cuatro")
        }

        val recuperado = ObjectRecords.decodeText(archivo)

        assertNotNull("el texto tiene que salir", recuperado)
        assertNull("y sin recorte", recuperado?.crop)
    }

    @Test
    fun `un recorte de tamaño cero no se guarda`() {
        val original = texto().copy(crop = ObjectBounds(10f, 10f, 10f, 10f))
        val recuperado = ObjectRecords.decodeText(ObjectRecords.encodeText(original))
        assertNull("una zona sin área no recorta nada", recuperado?.crop)
    }
}
