package com.mc.nbpaint.engine

/**
 * De qué tamaño tiene que ser la hoja cuando la imagen puesta manda.
 *
 * Puro a propósito: la decisión de "la hoja es del tamaño de la foto" tiene que poder
 * comprobarse en la JVM, porque es la que decide si al abrir una imagen aparece con un borde
 * blanco alrededor o llena el papel. Va en su propio archivo, sin `Bitmap` ni Android, por eso.
 */
object ImageHoja {

    /**
     * Tope del lado más largo de la hoja.
     *
     * Una foto de 8000 píxeles no puede ser la hoja: son 256 MB solo en un canal, y con las
     * capas por encima la app se queda sin memoria a mitad de la edición. El tope es alto para
     * que una foto normal salga entera, y solo actúa cuando de verdad hace falta.
     */
    const val LADO_MAXIMO = 4096

    /** Tamaño mínimo de un lado. Una hoja de cero píxeles no se puede pintar ni dibujar. */
    private const val LADO_MINIMO = 1

    /**
     * Tamaño de la hoja para una imagen de [ancho] x [alto], conservando su proporción.
     *
     * Sale el tamaño natural de la imagen, salvo que un lado pase de [LADO_MAXIMO]: entonces se
     * reduce **los dos** lados por el mismo factor, que es lo único que hace que siga siendo la
     * misma foto y no una estirada.
     */
    fun tamanoPara(ancho: Int, alto: Int): SheetSize {
        val w = ancho.coerceAtLeast(LADO_MINIMO)
        val h = alto.coerceAtLeast(LADO_MINIMO)
        val factor = minOf(1f, LADO_MAXIMO.toFloat() / maxOf(w, h))
        return SheetSize(
            ancho = maxOf(LADO_MINIMO, Math.round(w * factor)),
            alto = maxOf(LADO_MINIMO, Math.round(h * factor))
        )
    }
}

/** Tamaño de la hoja en píxeles, como un par de números y sin depender de nada. */
data class SheetSize(val ancho: Int, val alto: Int)
