package com.mc.nbpaint.engine

/**
 * Estado del sello de clonar.
 *
 * El clonado funciona marcando una fuente con un toque y copiándola a donde se vaya
 * después. Lo que hace falta Remember es una sola cosa: saber de qué punto hay que leer
 * para cada punto donde se pinta.
 *
 * La regla es que el desplazamiento se fija en el primer toque del arrastre y no se vuelve
 * a mirar. Si se recalculara en cada movimiento, la copia se iría corriendo sola; y sin
 * fijarlo, partir la fuente y pintar en el sitio de la fuente daría una fuente que se
 * copia a sí misma.
 *
 * No depende de nada de Android, así que se prueba en la JVM.
 */
class CloneStamp {

    /** Punto marcado como fuente, o null si todavía no se ha marcado ninguno. */
    var source: Pair<Float, Float>? = null
        private set

    /** Desplazamiento del dedo respecto a la fuente, fijado al empezar a pintar. */
    var offset: Pair<Float, Float>? = null
        private set

    /** ¿La fuente está ya marcada? */
    val hasSource: Boolean get() = source != null

    /** ¿Hay una fuente Y ya se ha empezado a pintar desde ella? */
    val isStamping: Boolean get() = source != null && offset != null

    /**
     * Marca la fuente con un toque. Devuelve si la fuente cambia, que es lo que el
     * historial necesita saber para no guardar un punto de deshacer por tocar dos veces.
     */
    fun markSource(x: Float, y: Float): Boolean {
        if (source != null && source!!.first == x && source!!.second == y) return false
        source = x to y
        offset = null
        return true
    }

    /**
     * Marca la fuente con un toque suelto, dejando la herramienta lista para pintar.
     *
     * Es lo que pasa cuando se toca sin arrastrar: la fuente es ese punto y no hay nada
     * stamped todavía, así que el siguiente arrastre pinta.
     */
    fun markSourceOnly(x: Float, y: Float) {
        source = x to y
        offset = null
    }

    /**
     * Empieza a estampar en [x], [y]. Fija el desplazamiento con el que se va a copiar.
     *
     * @return false si no hay fuente marcada, en cuyo caso este toque solo la marca y no
     *         pinta. Es lo que pasa con el primer toque después de elegir la herramienta.
     */
    fun beginStamp(x: Float, y: Float): Boolean {
        val from = source ?: run {
            markSourceOnly(x, y)
            return false
        }
        offset = (x - from.first) to (y - from.second)
        return true
    }

    /**
     * De qué punto hay que leer para pintar en [x], [y].
     *
     * @return null si todavía no se ha empezado a estampar, en cuyo caso no hay nada que
     *         copiar en este punto.
     */
    fun sourceFor(x: Float, y: Float): Pair<Float, Float>? {
        val from = source ?: return null
        val shift = offset ?: return from
        return (x - shift.first) to (y - shift.second)
    }

    /** Suelta el desplazamiento y deja la fuente marcada, para el siguiente trazo. */
    fun endStroke() {
        offset = null
    }

    /** Se olvida de todo, incluida la fuente. */
    fun clear() {
        source = null
        offset = null
    }
}
