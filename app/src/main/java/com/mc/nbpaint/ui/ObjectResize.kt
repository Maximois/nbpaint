package com.mc.nbpaint.ui

import com.mc.nbpaint.model.ObjectBounds

/**
 * Qué se está haciendo con un objeto: nada, moverlo, estirarlo, corregir un tirador o recolocar
 * un nodo de la curva.
 *
 * Es un estado y no un `Boolean` por operación porque el dedo decide en un solo gesto más de
 * una cosa, y volver a mirar el objeto en cada movimiento era la forma de que al cambiar de
 * intención a mitad de arrastre la caja se quedara a medio camino.
 */
enum class ObjectDragMode {
    /** Solo se ha tocado el objeto, sin haberlo movido. */
    IDLE,

    /** Se está arrastrando el objeto entero. */
    MOVE,

    /** Se está estirando desde un tirador de la caja. */
    RESIZE,

    /** Se está corrigiendo un tirador de la curva. */
    HANDLE,

    /**
     * Se está recolocando un nodo suelto de la curva.
     *
     * Va aparte de [HANDLE] porque mover un nodo y doblar la curva son gestos distintos: el nodo
     * se lleva consigo sus dos tiradores para que la forma del tramo no se estire, y el tirador
     * solo dobla lo que sale de ese punto.
     */
    NODE
}

/** Las cuatro esquinas de la caja, que son los cuatro tiradores que se pueden agarrar. */
enum class ResizeCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    /** La esquina de la caja que queda quieta mientras se estira desde este tirador. */
    val opposite: ResizeCorner
        get() = when (this) {
            TOP_LEFT -> BOTTOM_RIGHT
            TOP_RIGHT -> BOTTOM_LEFT
            BOTTOM_LEFT -> TOP_RIGHT
            BOTTOM_RIGHT -> TOP_LEFT
        }
}

/** Las dos esquinas de la caja, como coordenadas sueltas. */
private data class Point(val x: Float, val y: Float)

private fun cornerOf(box: ObjectBounds, corner: ResizeCorner): Point = when (corner) {
    ResizeCorner.TOP_LEFT -> Point(box.left, box.top)
    ResizeCorner.TOP_RIGHT -> Point(box.right, box.top)
    ResizeCorner.BOTTOM_LEFT -> Point(box.left, box.bottom)
    ResizeCorner.BOTTOM_RIGHT -> Point(box.right, box.bottom)
}

/**
 * Cómo se estira la caja de un objeto desde un tirador.
 *
 * Sin Android ni Compose, para poder probarlo en la JVM: la matemática de un tirador es
 * justo el tipo de cosa que se rompe en silencio cuando algo cambia, y no hay forma de verlo
 * en la pantalla salvo con el dedo delante.
 *
 * La regla es la de los editores de dibujo: el tirador que se agarra va hasta el dedo, y la
 * esquina contraria se queda clavada. Por eso el resultado no es solo un tamaño, sino también
 * una posición nueva: estirar desde abajo hacia arriba tiene que subir el objeto, o la caja
 * crecería hacia abajo mientras el texto se quedaba arriba y el tirador se soltaría de la
 * letra.
 */
object ObjectResize {

    /** La caja que sale de arrastrar [corner] hasta el punto ([x], [y]). */
    fun resize(antes: ObjectBounds, corner: ResizeCorner, x: Float, y: Float): ObjectBounds {
        val quieta = cornerOf(antes, corner.opposite)
        val izquierda = if (corner == ResizeCorner.TOP_LEFT || corner == ResizeCorner.BOTTOM_LEFT) {
            x
        } else {
            quieta.x
        }
        val arriba = if (corner == ResizeCorner.TOP_LEFT || corner == ResizeCorner.TOP_RIGHT) {
            y
        } else {
            quieta.y
        }
        val derecha = if (corner == ResizeCorner.TOP_RIGHT || corner == ResizeCorner.BOTTOM_RIGHT) {
            x
        } else {
            quieta.x
        }
        val abajo = if (corner == ResizeCorner.BOTTOM_LEFT || corner == ResizeCorner.BOTTOM_RIGHT) {
            y
        } else {
            quieta.y
        }
        // La caja se pone buena al final: al pasar el dedo de largo al lado contrario, los
        // bordes se cruzan y saldrían al revés. Con la normalización, el objeto se da la vuelta
        // y sigue en su sitio, que es lo que se ve al estirar más allá del tamaño natural.
        return ObjectBounds(
            left = minOf(izquierda, derecha),
            top = minOf(arriba, abajo),
            right = maxOf(izquierda, derecha),
            bottom = maxOf(arriba, abajo)
        )
    }

    /**
     * La escala que lleva la caja [antes] a la medida de [despues].
     *
     * Se mide por el lado mayor de los dos, y no solo por el ancho. En un texto de dos líneas
     * muy desiguales, tirar del tirador de abajo agranda sobre todo el alto; con el ancho como
     * única referencia, la escala saldría pequeña y el texto encogerseía al intentar
     * agrandarlo. Con el lado mayor, crece lo que se está tocando.
     */
    fun factor(antes: ObjectBounds, despues: ObjectBounds): Float {
        val ladoAntes = maxOf(antes.width, antes.height)
        val ladoDespues = maxOf(despues.width, despues.height)
        if (ladoAntes <= 0f) return 1f
        return ladoDespues / ladoAntes
    }

    /**
     * Dónde tiene que quedar la esquina superior izquierda del objeto para que su caja mida
     * lo que dice [escala], dejando clavada la esquina que no se arrastra.
     *
     * Los objetos de la capa guardan un desplazamiento y una escala, y de los dos sale la
     * caja: la escala multiplica el ancho y el alto por igual, así que estirar solo en
     * vertical no se puede expresar con la escala sola. Por eso se devuelve también la
     * posición, y se calcula desde la esquina quieta, restándole el tamaño nuevo por el lado
     * en el que se ha estirado.
     *
     * El lado del que se resta es el del tirador agarrado, no el contrario: tirando del tirador
     * de abajo a la derecha, la esquina de arriba a la izquierda es la que se queda, y con el
     * orden equivocado la caja salía al revés y el texto se quedaba colgando de la esquina
     * contraria.
     */
    fun placementFor(
        antes: ObjectBounds,
        esquina: ResizeCorner,
        escala: Float
    ): Pair<Float, Float> {
        val quieta = cornerOf(antes, esquina.opposite)
        val anchoNuevo = antes.width * escala
        val altoNuevo = antes.height * escala
        val x = if (esquina == ResizeCorner.TOP_LEFT || esquina == ResizeCorner.BOTTOM_LEFT) {
            quieta.x - anchoNuevo
        } else {
            quieta.x
        }
        val y = if (esquina == ResizeCorner.TOP_LEFT || esquina == ResizeCorner.TOP_RIGHT) {
            quieta.y - altoNuevo
        } else {
            quieta.y
        }
        return Pair(x, y)
    }

    /** El tirador más cercano a ([x], [y]), o null si los cuatro están más lejos que [radius]. */
    fun cornerNear(antes: ObjectBounds, x: Float, y: Float, radius: Float): ResizeCorner? {
        var mejor: ResizeCorner? = null
        var mejorDistancia = radius
        ResizeCorner.entries.forEach { corner ->
            val p = cornerOf(antes, corner)
            val d = kotlin.math.hypot((p.x - x).toDouble(), (p.y - y).toDouble()).toFloat()
            if (d <= mejorDistancia) {
                mejorDistancia = d
                mejor = corner
            }
        }
        return mejor
    }
}
