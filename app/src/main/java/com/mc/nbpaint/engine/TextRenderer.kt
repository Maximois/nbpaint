package com.mc.nbpaint.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.mc.nbpaint.model.TextAlign
import com.mc.nbpaint.model.TextObject
import com.mc.nbpaint.model.TextTypeface
import kotlin.math.max

/**
 * Tamaño medido de un texto, en píxeles de la hoja.
 *
 * Va aparte de la caja que lo dibuja porque al estirar un texto la caja es la que manda y la
 * medida se recalcula: si [TextObject.boxWidth] está fijado, la caja manda; si no, la medida
 * manda y la caja la define la línea más larga.
 */
data class TextMetrics(
    val width: Float,
    val height: Float,
    val lines: List<String>,
    val ascent: Float
)

/**
 * Cómo se mide y se dibuja un texto de capa.
 *
 * Vive aquí, y no en la pantalla, porque lo necesitan tres sitios distintos que tienen que
 * coincidir píxel a píxel: el que se ve en el lienzo, el que se guarda en un proyecto y el
 * que se rasteriza al fusionar un objeto. Si cada uno midiera a su manera, el texto se vería
 * en un sitio y se guardaría en otro.
 *
 * La medida y el dibujo comparten la misma [Paint] en [paintOf], y por eso la caja que sale de
 * [measure] es exactamente la que ocupa [draw].
 */
object TextRenderer {

    /** Familias del sistema que se ofrecen. Se eligen por nombre, como hace `Typeface.create`. */
    fun typefaceOf(text: TextObject): Typeface {
        val familia = when (text.typeface) {
            TextTypeface.SANS -> Typeface.SANS_SERIF
            TextTypeface.SERIF -> Typeface.SERIF
            TextTypeface.MONO -> Typeface.MONOSPACE
            TextTypeface.CURSIVE -> Typeface.create("cursive", Typeface.NORMAL)
        }
        var estilo = Typeface.NORMAL
        if (text.bold) estilo = estilo or Typeface.BOLD
        if (text.italic) estilo = estilo or Typeface.ITALIC
        return Typeface.create(familia, estilo)
    }

    /**
     * Prepara la pintura con la que se mide y se dibuja un texto.
     *
     * El interlineado y el ajuste de línea los lleva [lineasDe], no Android: dejar que el
     * sistema reparta las líneas con su propio criterio haría que la medida dependiera de la
     * versión instalada, y con ella la caja y la posición de lo que hay alrededor.
     */
    fun paintOf(text: TextObject, reuse: Paint? = null): Paint {
        val paint = reuse ?: Paint(Paint.ANTI_ALIAS_FLAG)
        paint.isAntiAlias = true
        paint.color = text.colorArgb
        paint.textSize = text.sizePx
        paint.typeface = typefaceOf(text)
        paint.textAlign = when (text.align) {
            TextAlign.LEFT, TextAlign.RIGHT -> Paint.Align.LEFT
            TextAlign.CENTER -> Paint.Align.CENTER
        }
        return paint
    }

    /**
     * Parte el texto en las líneas con las que se va a dibujar.
     *
     * Sin ancho de caja, cada salto de línea del texto es una línea. Con ancho, se parte cada
     * párrafo en trozos que quepan: partir por palabras y no por letras es lo que evita que
     * una palabra larga se corte en mitad.
     */
    fun lineasDe(text: TextObject, paint: Paint): List<String> {
        val ancho = if (text.boxWidth > 0f) text.boxWidth * text.scale else 0f
        val parrafos = text.text.split('\n')
        if (ancho <= 0f) return parrafos
        val lineas = mutableListOf<String>()
        parrafos.forEach { parrafo ->
            if (parrafo.isEmpty()) {
                lineas += ""
                return@forEach
            }
            var actual = StringBuilder()
            parrafo.split(' ').forEach { palabra ->
                val candidata = if (actual.isEmpty()) palabra else "$actual $palabra"
                if (actual.isNotEmpty() && paint.measureText(candidata) > ancho) {
                    lineas += actual.toString()
                    actual = StringBuilder(palabra)
                } else {
                    actual = StringBuilder(candidata)
                }
            }
            lineas += actual.toString()
        }
        return lineas
    }

    /**
     * Mide el texto tal y como se dibujaría ahora mismo.
     *
     * La caja sale del ancho de las líneas y de cuántas hay. La altura de línea incluye el
     * interlineado, pero la caja se termina de cerrar con la altura de la fuente: sin eso,
     * una sola línea mide lo mismo que un interlineado entero y el texto parece más bajo de
     * lo que es, y el marco de mover queda por encima de las letras.
     */
    fun measure(text: TextObject, reuse: Paint? = null): TextMetrics {
        val paint = paintOf(text, reuse)
        val lineas = lineasDe(text, paint)
        val ancho = if (text.boxWidth > 0f) {
            text.boxWidth * text.scale
        } else {
            lineas.maxOfOrNull { paint.measureText(it) } ?: 0f
        }
        val metricas = paint.fontMetrics
        val altoFuente = metricas.descent - metricas.ascent
        val altoLinea = text.sizePx * text.lineSpacingFactor
        val ascent = -metricas.ascent
        val alto = if (lineas.size <= 1) {
            altoFuente
        } else {
            altoFuente + altoLinea * (lineas.size - 1)
        }
        return TextMetrics(
            width = max(ancho, 1f),
            height = max(alto, 1f),
            lines = lineas,
            ascent = ascent
        )
    }

    /**
     * Dibuja el texto con su esquina superior izquierda en ([text].offsetX, [text].offsetY).
     *
     * La primera línea se apoya en la altura de la fuente y no en el alto de línea, para que
     * el texto quede pegado a la caja por arriba en vez de flotar dentro de ella.
     */
    fun draw(canvas: Canvas, text: TextObject, reuse: Paint? = null) {
        if (text.text.isEmpty()) return
        val paint = paintOf(text, reuse)
        val metricas = measure(text, paint)
        val altoLinea = text.sizePx * text.lineSpacingFactor
        val dx = when (text.align) {
            TextAlign.LEFT -> text.offsetX
            TextAlign.CENTER -> text.offsetX + metricas.width / 2f
            TextAlign.RIGHT -> text.offsetX + metricas.width
        }
        var y = text.offsetY + metricas.ascent
        metricas.lines.forEach { linea ->
            canvas.drawText(linea, dx, y, paint)
            y += altoLinea
        }
    }
}
