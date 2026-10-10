package com.mc.nbpaint.model

/** Caja en coordenadas de la hoja, sin depender de las clases de Compose ni de Android. */
data class ObjectBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** Caja que no tiene área: no se puede recortar a esto ni se puede tomar. */
    val isEmpty: Boolean get() = right <= left || bottom <= top

    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom

    /**
     * La misma caja corrida por ([dx], [dy]).
     *
     * Es lo que hace falta al mover un objeto recortado: el recorte va en coordenadas de la
     * hoja, así que si el objeto se mueve y la zona no, el recorte se queda atrás y acaba
     * quitando una parte distinta de la que el usuario eligió.
     */
    fun trasladada(dx: Float, dy: Float): ObjectBounds =
        ObjectBounds(left + dx, top + dy, right + dx, bottom + dy)

    /**
     * La misma caja estirada por [factor] alrededor de ([anclaX], [anclaY]), sin mover el ancla.
     *
     * El ancla es la esquina por la que se estira el objeto, que es la que la mano de mover deja
     * clavada. Sin esto, estirar un objeto recortado dejaría la zona quieta mientras el dibujo
     * se separaba de ella, y lo que se ve recortado ya no sería lo que se eligió.
     */
    fun escaladaSobre(anclaX: Float, anclaY: Float, factor: Float): ObjectBounds = ObjectBounds(
        anclaX + (left - anclaX) * factor,
        anclaY + (top - anclaY) * factor,
        anclaX + (right - anclaX) * factor,
        anclaY + (bottom - anclaY) * factor
    )

    /** La parte que [otra] y esta comparten, o null si no se tocan. */
    fun interseccion(otra: ObjectBounds): ObjectBounds? {
        val l = maxOf(left, otra.left)
        val t = maxOf(top, otra.top)
        val r = minOf(right, otra.right)
        val b = minOf(bottom, otra.bottom)
        if (r <= l || b <= t) return null
        return ObjectBounds(l, t, r, b)
    }
}

/** Pasa un rectángulo de píxeles a caja en coordenadas de la hoja, que es lo que guardan los objetos. */
fun objectBoundsDe(rect: IntRect): ObjectBounds =
    ObjectBounds(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat())
