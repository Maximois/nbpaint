package com.mc.nbpaint.model

/**
 * Rectángulo de píxeles con borde exclusivo, como el de Android pero sin depender de él.
 *
 * Se define aquí para que la geometría de la selección se pueda probar en la JVM, sin
 * emulador ni Robolectric.
 */
data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {

    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val isEmpty: Boolean get() = width <= 0 || height <= 0
    val area: Int get() = width * height

    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom

    fun intersects(other: IntRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun intersects(x: Int, y: Int): Boolean = contains(x, y)

    /** Corta el rectángulo a los límites de una hoja de [width] x [height]. */
    fun clamped(width: Int, height: Int): IntRect = IntRect(
        left.coerceIn(0, width),
        top.coerceIn(0, height),
        right.coerceIn(0, width),
        bottom.coerceIn(0, height)
    )

    /**
     * El rectángulo que abarca a este y a [otro], o null si los dos están vacíos.
     *
     * Es lo que hace falta para "el contenido del documento": cada capa aporta su rectángulo de
     * contenido y el del documento es el que las abarca a todas, no el de la capa visible. Con
     * el de una sola, una capa con algo pintado en una esquina se recortaría y llevaría por
     * delante lo que hubiera en el resto.
     */
    fun union(otro: IntRect): IntRect {
        if (isEmpty) return otro
        if (otro.isEmpty) return this
        return IntRect(
            minOf(left, otro.left),
            minOf(top, otro.top),
            maxOf(right, otro.right),
            maxOf(bottom, otro.bottom)
        )
    }

    companion object {
        val EMPTY = IntRect(0, 0, 0, 0)
    }
}

/**
 * Zona de trabajo de las herramientas.
 *
 * Hay tres estados, y la diferencia importa:
 *
 * - [All]: no hay selección activa, así que todo se puede tocar. Es lo que hay al abrir la
 *   app, para que las herramientas funcionen sin pasos extra.
 * - [Rect]: un rectángulo, el "marco" de los editores de imagen.
 * - [Mask]: una región cualquiera, la que sale de la selección inteligente con tolerancia.
 *
 * Cualquier función de la app que pinte, rellene, borre, parchee, recorte o aplique un
 * filtro consulta [contains] antes de tocar un píxel, así que la selección es general y no
 * un caso suelto de una herramienta.
 */
sealed interface Selection {

    /** ¿Este píxel concreto está dentro de la zona de trabajo? */
    fun contains(x: Int, y: Int): Boolean

    /** Sin selección: se trabaja en toda la hoja. */
    data object All : Selection {
        override fun contains(x: Int, y: Int) = true
    }

    /** Marco rectangular. */
    data class Rect(val rect: IntRect) : Selection {
        override fun contains(x: Int, y: Int) = rect.contains(x, y)
    }

    /**
     * Región con forma libre, guardada como una máscara de un píxel por celda.
     *
     * Es un `BooleanArray` y no un `android.graphics.Bitmap` a propósito: la selección
     * inteligente con tolerancia se puede probar en la JVM sin emulador.
     */
    class Mask(
        val mask: BooleanArray,
        val width: Int,
        val height: Int
    ) : Selection {

        override fun contains(x: Int, y: Int): Boolean =
            if (x < 0 || y < 0 || x >= width || y >= height) false else mask[y * width + x]

        /** Cuántos píxeles cubre la región. */
        val selectedCount: Int by lazy { mask.count { it } }

        /**
         * Rectángulo que abarca la región, o null si no cubre ningún píxel.
         *
         * Sale de recorrer los 1,7 millones de píxeles de una hoja grande, y se consulta en
         * cada frame para dibujar el marco, así que se calcula una sola vez y se guarda. La
         * máscara no se modifica nunca después de construirla, por eso el valor guardado no
         * puede quedarse viejo.
         */
        fun bounds(): IntRect? {
            if (!limitesCalculados) {
                limites = calcularLimites()
                limitesCalculados = true
            }
            return limites
        }

        private var limites: IntRect? = null
        private var limitesCalculados = false

        private fun calcularLimites(): IntRect? {
            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1
            for (y in 0 until height) {
                val row = y * width
                for (x in 0 until width) {
                    if (!mask[row + x]) continue
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
            return if (maxX < 0) null else IntRect(minX, minY, maxX + 1, maxY + 1)
        }

        /** Copia inversa: lo que estaba fuera pasa a dentro y al revés. */
        fun inverted(): Mask {
            val copy = BooleanArray(mask.size)
            for (i in mask.indices) copy[i] = !mask[i]
            return Mask(copy, width, height)
        }

        override fun equals(other: Any?): Boolean =
            other is Mask && other.width == width && other.height == height &&
                other.mask.contentEquals(mask)

        override fun hashCode(): Int =
            (mask.contentHashCode() * 31 + width) * 31 + height
    }
}
