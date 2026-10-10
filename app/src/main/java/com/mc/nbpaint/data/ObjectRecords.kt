package com.mc.nbpaint.data

import androidx.compose.ui.geometry.Offset
import com.mc.nbpaint.model.CurveNode
import com.mc.nbpaint.model.CurveNodeSnapshot
import com.mc.nbpaint.model.CurveObject
import com.mc.nbpaint.model.CurveSnapshot
import com.mc.nbpaint.model.ObjectBounds
import com.mc.nbpaint.model.TextAlign
import com.mc.nbpaint.model.TextObject
import com.mc.nbpaint.model.TextSnapshot
import com.mc.nbpaint.model.TextTypeface

/**
 * Cómo se escriben en un archivo de texto el contenido de un texto y los nodos de una curva.
 *
 * Van en archivos aparte del manifiesto, y no en su línea, por un motivo concreto: el texto
 * escrito puede llevar saltos de línea, y el manifiesto separa las capas por saltos de línea.
 * Con el texto en la línea de la capa, un "Hola\nAdiós" partía el archivo en dos y al abrir
 * el proyecto se perdía la capa entera. Aquí cada capa tiene un archivo de textos y otro de
 * curvas, y los saltos de línea del texto se escapan.
 *
 * Dentro de esos archivos caben varios objetos, separados por una línea con `#`. La capa puede
 * tener todos los textos y todas las curvas que se le quieran, y en un archivo único por capa
 * no hay que inventarse un nombre por objeto ni renumerarlos al borrar uno del medio. Además un
 * archivo de los de antes, con un solo objeto y sin separadores, se sigue leyendo igual, porque
 * un archivo sin `#` es un archivo con un único registro.
 *
 * Todo es texto y números, sin Android ni Compose, así que se prueba en la JVM. Es
 * justamente lo que hace falta: esto decide si un texto guardado y reabierto sale con la
 * misma fuente, el mismo tamaño y la misma alineación, o si vuelve como un rectángulo vacío.
 */
object ObjectRecords {

    private const val FIELD = '|'
    private const val TEXT_VERSION = 1
    private const val CURVE_VERSION = 1

    /**
     * Línea que separa dos objetos dentro del mismo archivo de capa, con su salto incluido.
     *
     * Es una línea suelta sin campos, y ninguna línea de un registro puede ser así: los
     * registros tienen todos sus campos con `|`, y el texto va escapado, así que un `#` no
     * puede aparecer solo en medio de uno.
     *
     * El salto de línea va dentro del separador a propósito. Si se partiera la cadena por la
     * línea sin llevarse el salto, el registro siguiente empezaría con una línea vacía y se
     * descartaría entero, con lo que solo se leería el primer objeto de la capa.
     */
    private val SEPARADOR = Regex("(?m)^[ \\t]*#[ \\t]*\\r?\\n")

    /**
     * Los textos de una capa, como texto de archivo y en el orden en que se dibujan.
     *
     * El orden es el que importa: al leer se recuperan en el mismo orden, y ese es el orden en
     * el que se dibujan uno sobre otro. Si se desordenaran, un rótulo pondría debajo de un
     * subtítulo que se puso después.
     */
    fun encodeTexts(texts: List<TextSnapshot>): String =
        texts.joinToString("\n#\n") { encodeText(it) }

    /** Lee todos los textos de una capa, saltándose los que no se entiendan. */
    fun decodeTexts(texto: String): List<TextSnapshot> =
        texto.split(SEPARADOR).mapNotNull { decodeText(it) }

    /** Las curvas de una capa, como texto de archivo y en el orden en que se dibujan. */
    fun encodeCurves(curves: List<CurveSnapshot>): String =
        curves.joinToString("\n#\n") { encodeCurve(it) }

    /** Lee todas las curvas de una capa, saltándose las que no se entiendan. */
    fun decodeCurves(texto: String): List<CurveSnapshot> =
        texto.split(SEPARADOR).mapNotNull { decodeCurve(it) }

    /** El texto de un objeto de capa, como texto de archivo. */
    fun encodeText(text: TextSnapshot): String = buildString {
        appendLine("$TEXT_VERSION$FIELD${escape(text.text)}")
        appendLine("${text.offsetX}$FIELD${text.offsetY}$FIELD${text.baseSize}$FIELD${text.colorArgb}")
        appendLine("${text.typeface.name}$FIELD${text.bold}$FIELD${text.italic}$FIELD${text.align.name}")
        appendLine(
            "${text.lineSpacingFactor}$FIELD${text.boxWidth}$FIELD${text.scale}" +
                encodeCrop(text.crop)
        )
    }

    /**
     * Lee un texto de objeto. Devuelve null si no se entiende, para que una capa con un
     * archivo roto siga abriéndose con su imagen.
     */
    fun decodeText(texto: String): TextSnapshot? {
        // Las líneas vacías del principio se saltan. Nunca hay una de verdad en un registro,
        // porque la primera línea es la de la versión y el texto va escapado, así que esto
        // solo recoge los restos que deja el separador entre dos objetos del mismo archivo.
        val lineas = texto.lines().dropWhile { it.isBlank() }
        if (lineas.size < 4) return null
        if (lineas[0].substringBefore(FIELD).trim().toIntOrNull() != TEXT_VERSION) return null

        val contenido = unescape(lineas[0].substringAfter(FIELD, ""))
        val pos = lineas[1].split(FIELD)
        if (pos.size < 4) return null
        val offsetX = pos[0].trim().toFloatOrNull() ?: return null
        val offsetY = pos[1].trim().toFloatOrNull() ?: return null
        val baseSize = pos[2].trim().toFloatOrNull() ?: return null
        val colorArgb = pos[3].trim().toLongOrNull()?.toInt() ?: return null

        val estilo = lineas[2].split(FIELD)
        if (estilo.size < 4) return null
        val typeface = runCatching { TextTypeface.valueOf(estilo[0].trim()) }.getOrNull() ?: return null
        val align = runCatching { TextAlign.valueOf(estilo[3].trim()) }.getOrNull() ?: return null

        val medidas = lineas[3].split(FIELD)
        if (medidas.size < 3) return null
        val interlineado = medidas[0].trim().toFloatOrNull() ?: return null
        val boxWidth = medidas[1].trim().toFloatOrNull() ?: return null
        val escala = medidas[2].trim().toFloatOrNull() ?: return null
        if (offsetX.isNaN() || offsetY.isNaN() || baseSize <= 0f || escala <= 0f) return null
        if (boxWidth < 0f || !interlineado.isFinite()) return null
        // El recorte son cuatro campos más, al final, y se leen solo si están: un texto
        // guardado antes de que existiera el recorte se sigue abriendo entero.
        val crop = decodeCrop(medidas, 3)

        return TextSnapshot(
            text = contenido,
            offsetX = offsetX,
            offsetY = offsetY,
            baseSize = baseSize,
            colorArgb = colorArgb,
            typeface = typeface,
            bold = estilo[1].trim() == "true",
            italic = estilo[2].trim() == "true",
            align = align,
            lineSpacingFactor = interlineado,
            boxWidth = boxWidth,
            scale = escala,
            crop = crop
        )
    }

    /** La curva de un objeto de capa, como texto de archivo. */
    fun encodeCurve(curve: CurveSnapshot): String = buildString {
        appendLine(
            "$CURVE_VERSION$FIELD${curve.colorArgb}$FIELD${curve.width}$FIELD${curve.scale}" +
                encodeCrop(curve.crop)
        )
        curve.nodes.forEach { n ->
            appendLine(
                "${n.x}$FIELD${n.y}$FIELD${n.outHandleX}$FIELD${n.outHandleY}" +
                    "$FIELD${n.inHandleX}$FIELD${n.inHandleY}$FIELD${n.isCorner}"
            )
        }
    }

    /** Lee una curva de objeto, o null si el archivo no se entiende. */
    fun decodeCurve(texto: String): CurveSnapshot? {
        val lineas = texto.lines().filter { it.isNotBlank() }
        if (lineas.isEmpty()) return null
        val cabecera = lineas[0].split(FIELD)
        if (cabecera.size < 4) return null
        if (cabecera[0].trim().toIntOrNull() != CURVE_VERSION) return null
        val colorArgb = cabecera[1].trim().toLongOrNull()?.toInt() ?: return null
        val grosor = cabecera[2].trim().toFloatOrNull() ?: return null
        val escala = cabecera[3].trim().toFloatOrNull() ?: return null
        if (grosor <= 0f || escala <= 0f || !grosor.isFinite() || !escala.isFinite()) return null
        val crop = decodeCrop(cabecera, 4)

        val nodos = lineas.drop(1).mapNotNull { leerNodo(it) }
        if (nodos.size < 2) return null
        return CurveSnapshot(nodos, colorArgb, grosor, escala, crop)
    }

    /**
     * Escribe la zona recortada al final de una línea de campos.
     *
     * Sin recorte no se escribe **nada**, ni siquiera los separadores. Es lo que mantiene
     * compatible un archivo viejo: la línea se queda igual de larga y al leerlo no hay ningún
     * campo raro que interpretar. Con recorte son cuatro números más, y siempre los cuatro:
     * escribir solo algunos desalinearía lo que venga detrás.
     */
    private fun encodeCrop(crop: ObjectBounds?): String {
        if (crop == null) return ""
        return "$FIELD${crop.left}$FIELD${crop.top}$FIELD${crop.right}$FIELD${crop.bottom}"
    }

    /**
     * Lee la zona recortada de una línea de campos, a partir de [desde].
     *
     * Si no están los cuatro campos, o si alguno no es un número, se devuelve null y el objeto
     * se abre entero. Perder el recorte es molesto; perder el texto entero porque el recorte
     * traía un número raro sería mucho peor.
     */
    private fun decodeCrop(campos: List<String>, desde: Int): ObjectBounds? {
        if (campos.size < desde + 4) return null
        val numeros = campos.subList(desde, desde + 4).map { it.trim().toFloatOrNull() ?: return null }
        if (numeros.any { !it.isFinite() }) return null
        val zona = ObjectBounds(numeros[0], numeros[1], numeros[2], numeros[3])
        return if (zona.isEmpty) null else zona
    }

    private fun leerNodo(linea: String): CurveNodeSnapshot? {
        val p = linea.split(FIELD)
        if (p.size < 7) return null
        val numeros = p.take(6).map { it.trim().toFloatOrNull() ?: return null }
        if (numeros.any { !it.isFinite() }) return null
        return CurveNodeSnapshot(
            x = numeros[0],
            y = numeros[1],
            outHandleX = numeros[2],
            outHandleY = numeros[3],
            inHandleX = numeros[4],
            inHandleY = numeros[5],
            isCorner = p[6].trim() == "true"
        )
    }

    /**
     * Escribe un texto en una sola línea.
     *
     * El salto de línea y la barra vertical son los dos caracteres que romperían el archivo:
     * el primero lo partiría en dos y el segundo desalinearía los campos. Se escapan como
     * `\` y `p` porque el propio `\` también necesita escapar, para que un texto que de verdad
     * lleve la secuencia `\p` no se lea al revés.
     */
    private fun escape(texto: String): String = buildString(texto.length) {
        texto.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '|' -> append("\\p")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
    }

    /** Al revés de [escape]. Un `\q` suelto se deja como está, en vez de fallar la lectura. */
    private fun unescape(texto: String): String = buildString(texto.length) {
        var i = 0
        while (i < texto.length) {
            val c = texto[i]
            if (c != '\\' || i == texto.lastIndex) {
                append(c)
                i++
                continue
            }
            when (texto[i + 1]) {
                '\\' -> append('\\')
                'p' -> append('|')
                'n' -> append('\n')
                'r' -> append('\r')
                else -> append(c).append(texto[i + 1])
            }
            i += 2
        }
    }
}

/** Copia del texto a la forma que se guarda, con la medida descartada. */
fun TextObject.toSnapshot(): TextSnapshot = TextSnapshot(
    text = text,
    offsetX = offsetX,
    offsetY = offsetY,
    baseSize = baseSize,
    colorArgb = colorArgb,
    typeface = typeface,
    bold = bold,
    italic = italic,
    align = align,
    lineSpacingFactor = lineSpacingFactor,
    boxWidth = boxWidth,
    scale = scale,
    crop = crop
)

/** Vuelve a construir el texto a partir de lo que había guardado. */
fun TextSnapshot.toObject(): TextObject = TextObject(
    text = text,
    offsetX = offsetX,
    offsetY = offsetY,
    baseSize = baseSize,
    colorArgb = colorArgb,
    typeface = typeface,
    bold = bold,
    italic = italic,
    align = align,
    lineSpacingFactor = lineSpacingFactor,
    boxWidth = boxWidth,
    scale = scale
).also { it.crop = crop }

/** Copia de la curva a la forma que se guarda. */
fun CurveObject.toSnapshot(): CurveSnapshot = CurveSnapshot(
    nodes = nodes.map {
        CurveNodeSnapshot(it.point.x, it.point.y, it.outHandle.x, it.outHandle.y, it.inHandle.x, it.inHandle.y, it.isCorner)
    },
    colorArgb = colorArgb,
    width = width,
    scale = scale,
    crop = crop
)

/** Vuelve a construir la curva a partir de lo que había guardado. */
fun CurveSnapshot.toObject(): CurveObject = CurveObject(
    nodes = nodes.map {
        CurveNode(
            point = Offset(it.x, it.y),
            outHandle = Offset(it.outHandleX, it.outHandleY),
            inHandle = Offset(it.inHandleX, it.inHandleY),
            isCorner = it.isCorner
        )
    }.toMutableList(),
    colorArgb = colorArgb,
    width = width,
    scale = scale
).also { it.crop = crop }
