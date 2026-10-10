package com.mc.nbpaint.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * Un texto colocado en una capa, como objeto: se mueve, se estira y se reescribe sin
 * perderse lo que ya está pintado debajo.
 *
 * Antes el texto se rasterizaba directamente sobre el bitmap en cuanto se confirmaba, y a
 * partir de ahí era tan solo pintura: no se podía volver a tomar, cambiarle el color ni
 * corregir su posición. Ahora es un objeto de la capa, como [PlacedImage].
 *
 * El tamaño se guarda como [baseSize] más [scale], y no como un tamaño final. Así el texto se
 * dibuja siempre con el tamaño con el que se escribió y estirar la caja multiplica en vez de
 * recalcular: sale lo mismo que escribirlo de otro tamaño, y no hay que volver a medir ni a
 * comprobar que los puntos de control de la fuente siguen valiendo.
 *
 * La medida de la caja la rellena [com.mc.nbpaint.engine.TextRenderer] al medir, y se guarda
 * aquí para que el compositor y la mano de mover la tengan sin medir otra vez. Antes de la
 * primera medida vale el tamaño de una línea, que es lo que ocupa justo después de colocarse.
 */
class TextObject(
    var text: String,
    /** Esquina superior izquierda de la caja del texto, en coordenadas de la hoja. */
    var offsetX: Float,
    var offsetY: Float,
    /** Tamaño con el que se escribió, en píxeles de la hoja. */
    var baseSize: Float,
    var colorArgb: Int,
    var typeface: TextTypeface = TextTypeface.SANS,
    var bold: Boolean = false,
    var italic: Boolean = false,
    var align: TextAlign = TextAlign.LEFT,
    var lineSpacingFactor: Float = 1.1f,
    /**
     * Ancho en el que se parte el texto en varias líneas, o 0 si se deja el ancho que
     * tengan las líneas escritas.
     *
     * Es lo que hace que [align] signifique algo: con el ancho automático, la línea más
     * larga define la caja y todas quedan a la izquierda de todos modos.
     */
    var boxWidth: Float = 0f,
    scale: Float = 1f
) {
    var scale by mutableFloatStateOf(scale)

    /**
     * Zona del texto que se sigue viendo, en coordenadas de la hoja, o null si se ve entero.
     *
     * Sirve para acortar un rótulo: se recorta la caja y desaparece lo que sobra, sin reescribir
     * el texto ni perder la fuente, el tamaño o la alineación. Va en coordenadas de la hoja y
     * la mantiene al día [TextObjectRef], que es quien mueve y escala el objeto.
     */
    var crop: ObjectBounds? = null

    /** Tamaño al que se dibuja ahora mismo: el de escritura por la escala de la caja. */
    val sizePx: Float get() = baseSize * scale

    /** Ancho medido de la caja, en píxeles de la hoja. La rellena el motor de texto. */
    var measuredWidth by mutableFloatStateOf(0f)
        private set

    /** Alto medido de la caja, en píxeles de la hoja. La rellena el motor de texto. */
    var measuredHeight by mutableFloatStateOf(0f)
        private set

    /** Ancho de la caja: el medido, o el de una línea mientras no se ha medido. */
    val boxWidthNow: Float get() = if (measuredWidth > 0f) measuredWidth else sizePx

    /** Alto de la caja: el medido, o el de una línea mientras no se ha medido. */
    val boxHeightNow: Float get() = if (measuredHeight > 0f) measuredHeight else sizePx * 1.2f

    /** Mueve el texto. [dx] y [dy] van en píxeles de la hoja. */
    fun moveBy(dx: Float, dy: Float) {
        offsetX += dx
        offsetY += dy
    }

    /** Escala acotada a un rango usable, para que ni desaparezca ni se infle sin fin. */
    fun scaleCoherente(value: Float): Float = value.coerceIn(MIN_SCALE, MAX_SCALE)
    /** ¿El punto (en coordenadas de la hoja) cae dentro de la caja del texto? */
    fun contains(x: Float, y: Float): Boolean =
        x >= offsetX && x <= offsetX + boxWidthNow && y >= offsetY && y <= offsetY + boxHeightNow

    /** Guarda la medida que devuelve el motor de texto. */
    fun setMeasured(width: Float, height: Float) {
        measuredWidth = width
        measuredHeight = height
    }

    /**
     * Olvida la medida, para que el siguiente repintado la vuelva a hacer.
     *
     * Hace falta al cambiar el tamaño: la caja que hay guardada es la del tamaño viejo, y
     * con ella los tiradores de la mano de mover se quedan donde estaban y se agarran en un
     * sitio donde ya no hay letra. La estimación de una línea ([boxWidthNow]) sustituye a la
     * medida solo hasta el repintado, que es lo que tarda una fracción de milisegundo.
     */
    fun clearMeasured() {
        measuredWidth = 0f
        measuredHeight = 0f
    }

    companion object {
        /** Escala mínima: por debajo el texto deja de leerse y no se puede volver a tomar. */
        const val MIN_SCALE = 0.1f

        /** Escala máxima: evita que el texto crezca hasta comerse la memoria al medirlo. */
        const val MAX_SCALE = 12f
    }
}

/** Familia de la fuente del texto. Son familias del sistema: la app no empaqueta fuentes. */
enum class TextTypeface(val label: String) {
    SANS("Palo seco"),
    SERIF("Serif"),
    MONO("Monoespaciada"),
    CURSIVE("Manuscrita")
}

/** Alineación horizontal del texto respecto de su caja. */
enum class TextAlign(val label: String) {
    LEFT("Izquierda"),
    CENTER("Centro"),
    RIGHT("Derecha")
}

/** Un texto de capa, tal como queda en disco. */
data class TextSnapshot(
    val text: String,
    val offsetX: Float,
    val offsetY: Float,
    val baseSize: Float,
    val colorArgb: Int,
    val typeface: TextTypeface,
    val bold: Boolean,
    val italic: Boolean,
    val align: TextAlign,
    val lineSpacingFactor: Float,
    val boxWidth: Float,
    val scale: Float,
    val crop: ObjectBounds? = null
)
