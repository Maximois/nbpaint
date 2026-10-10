package com.mc.nbpaint.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.hypot

/**
 * Un nodo de una curva: un punto por el que pasa la línea, con dos tiradores que decidirán
 * hacia dónde se dobla.
 *
 * Los tiradores son simétricos, como los de las curvas de un editor de dibujo: mover uno mueve
 * el otro en la dirección contraria, y la curva queda suave a ambos lados del nodo. [isCorner]
 * marca los nodos donde eso no se cumple, porque los puntos de una línea poligonal normal
 * deben poder doblarse hacia lados distintos.
 */
class CurveNode(
    var point: Offset,
    /** Tirador de salida, en coordenadas de la hoja. */
    var outHandle: Offset = point,
    /** Tirador de entrada, en coordenadas de la hoja. */
    var inHandle: Offset = point,
    var isCorner: Boolean = false
) {
    /** ¿El nodo tiene algún tirador aparte del centro, o sea si la curva se dobla en él? */
    val hasHandles: Boolean get() = inHandle != point || outHandle != point

    /** ¿El punto [target] está sobre el tirador de entrada, con un margen? */
    fun hitsInHandle(target: Offset, radius: Float): Boolean =
        hypot((target.x - inHandle.x).toDouble(), (target.y - inHandle.y).toDouble()) <= radius

    /** ¿El punto [target] está sobre el tirador de salida, con un margen? */
    fun hitsOutHandle(target: Offset, radius: Float): Boolean =
        hypot((target.x - outHandle.x).toDouble(), (target.y - outHandle.y).toDouble()) <= radius

    /** ¿El punto [target] está sobre el nodo, con un margen? */
    fun hitsNode(target: Offset, radius: Float): Boolean =
        hypot((target.x - point.x).toDouble(), (target.y - point.y).toDouble()) <= radius

    /** Distancia del tirador de entrada al punto [target]. Para elegir el tirador más cercano. */
    fun distanciaAEntrada(target: Offset): Float =
        hypot((target.x - inHandle.x).toDouble(), (target.y - inHandle.y).toDouble()).toFloat()

    /** Distancia del tirador de salida al punto [target]. Para elegir el tirador más cercano. */
    fun distanciaASalida(target: Offset): Float =
        hypot((target.x - outHandle.x).toDouble(), (target.y - outHandle.y).toDouble()).toFloat()

    /**
     * Lleva el nodo a [target] con sus dos tiradores encima.
     *
     * Los tiradores se mueven la distancia que se ha movido el nodo, no se recolocan en el
     * destino: los tiradores tienen una forma que vale, y dejarlos clavados en el sitio
     * mientras el nodo se va convertiría cada corrección de un nodo en un estiramiento del
     * tramo de curva que entra y sale de él.
     */
    fun moverA(target: Offset) {
        moveBy(target.x - point.x, target.y - point.y)
    }

    /**
     * Mueve el nodo y sus dos tiradores, que van pegados a él.
     *
     * Sin esto, mover el nodo dejaría los tiradores atrás y la curva se deformaría hacia un
     * lado sin querer, que es justo el efecto contrario del que se busca al recolocar un nodo.
     */
    fun moveBy(dx: Float, dy: Float) {
        val delta = Offset(dx, dy)
        point += delta
        inHandle += delta
        outHandle += delta
    }

    /**
     * Recoloca el tirador de salida y arrastra el de entrada al lado contrario, a la misma
     * distancia, para que la curva siga siendo suave al pasar por el nodo.
     *
     * No se llama [setOutHandle] porque choca con el `setter` de la propiedad `outHandle` en
     * la firma de la JVM, y el error sale en un sitio que no dice nada de esto.
     */
    fun arrastrarSalida(target: Offset) {
        outHandle = target
        if (!isCorner) {
            val largo = hypot(
                (outHandle.x - point.x).toDouble(), (outHandle.y - point.y).toDouble()
            )
            if (largo > 0.0) {
                val ux = ((point.x - outHandle.x) / largo).toFloat()
                val uy = ((point.y - outHandle.y) / largo).toFloat()
                inHandle = Offset(point.x + ux * largo.toFloat(), point.y + uy * largo.toFloat())
            } else {
                inHandle = point
            }
        }
    }

    /** Lo mismo que [arrastrarSalida] para el tirador de entrada. */
    fun arrastrarEntrada(target: Offset) {
        inHandle = target
        if (!isCorner) {
            val largo = hypot(
                (inHandle.x - point.x).toDouble(), (inHandle.y - point.y).toDouble()
            )
            if (largo > 0.0) {
                val ux = ((point.x - inHandle.x) / largo).toFloat()
                val uy = ((point.y - inHandle.y) / largo).toFloat()
                outHandle = Offset(point.x + ux * largo.toFloat(), point.y + uy * largo.toFloat())
            } else {
                outHandle = point
            }
        }
    }

    /** Copia del nodo, para el historial y para no compartir estado al deshacer. */
    fun copy(): CurveNode = CurveNode(point, outHandle, inHandle, isCorner)
}

/**
 * Una curva dibujada en una capa, como objeto.
 *
 * Nace de un trazo del dedo: se toma una muestra de los puntos por los que pasó y se
 * convierten en nodos. A partir de ahí se corrige con los tiradores de cada nodo, sin volver
 * a dibujar la línea.
 *
 * Es un objeto de la capa, igual que el texto y la imagen puesta, y por eso el compositor lo
 * dibuja encima de lo pintado y la mano de mover objetos lo puede tomar.
 */
class CurveObject(
    val nodes: MutableList<CurveNode> = mutableListOf(),
    var colorArgb: Int = 0xFFFFFFFF.toInt(),
    /** Grosor del trazo en píxeles de la hoja. */
    var width: Float = 8f,
    scale: Float = 1f
) {
    /**
     * Escala de la caja, como en el texto y en la imagen puesta.
     *
     * Va en el constructor para poder rehidratar una curva desde el historial o de un proyecto
     * guardado. Si se dejara fuera, cada sitio que reconstruye una curva tendría que acordarse
     * de asignarla después, y basta uno que se olvide para que la línea salga del grosor
     * equivocado sin que nada avise.
     */
    var scale by mutableFloatStateOf(scale)

    /**
     * Zona de la curva que se sigue viendo, en coordenadas de la hoja, o null si se ve entera.
     *
     * Lo que se guarda es la ventana, no una curva nueva: los nodos y los tiradores siguen
     * siendo los mismos, así que después de recortar se puede seguir corrigiendo la línea y,
     * si hace falta, volver a abrir la zona recortada con el mismo trazo. La mantiene al día
     * [CurveObjectRef], que es quien mueve y escala el objeto.
     */
    var crop: ObjectBounds? = null

    /** ¿La curva tiene lo mínimo para poder dibujarse? */
    val isDrawable: Boolean get() = nodes.size >= 2

    /** Tamaño de la línea una vez aplicada la escala de la caja. */
    val drawWidth: Float get() = width * scale

    /** Mueve la curva entera. Los nodos y sus tiradores van juntos. */
    fun moveBy(dx: Float, dy: Float) {
        nodes.forEach { it.moveBy(dx, dy) }
    }

    /**
     * Multiplica la distancia entre nodos y tiradores por [factor], sin mover el grupo de sitio.
     *
     * Hace falta porque la geometría de la curva está en coordenadas de la hoja, y no hay forma
     * de deducirla de una escala como en el texto o en la imagen: con la escala puesta y los
     * nodos igual, estirar solo engordaba la línea y la caja se quedaba como estaba, así que
     * el tirador se iba de la letra.
     *
     * El punto fijo es la esquina de arriba a la izquierda del grupo, que es la que la mano
     * deja clavada al estirar hacia abajo y a la derecha. Se escala alrededor de ella, y quien
     * llama recoloca la curva después con el anclaje al dedo, así que aquí no hace falta
     * mantener nada en su sitio.
     */
    fun scaleGeometry(factor: Float) {
        if (nodes.isEmpty()) return
        if (factor <= 0f || !factor.isFinite() || factor == 1f) return
        val izquierda = nodes.minOf { minOf(it.point.x, it.inHandle.x, it.outHandle.x) }
        val arriba = nodes.minOf { minOf(it.point.y, it.inHandle.y, it.outHandle.y) }
        nodes.forEach { n ->
            n.point = escalar(n.point, izquierda, arriba, factor)
            n.inHandle = escalar(n.inHandle, izquierda, arriba, factor)
            n.outHandle = escalar(n.outHandle, izquierda, arriba, factor)
        }
    }

    /** Escala acotada a un rango usable. */
    fun scaleCoherente(value: Float): Float = value.coerceIn(MIN_SCALE, MAX_SCALE)

    /**
     * Caja que ocupa la curva, contando también los tiradores.
     *
     * Los tiradores se incluyen porque al corregir la línea se salen de la caja de los nodos,
     * y con una caja que no los cuenta el tirador de un nodo del borde queda fuera de la zona
     * donde se puede tomar y parece que no hay nada que mover.
     */
    fun bounds(): Rect {
        if (nodes.isEmpty()) return Rect.Zero
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        fun incluir(x: Float, y: Float) {
            if (x < left) left = x
            if (y < top) top = y
            if (x > right) right = x
            if (y > bottom) bottom = y
        }
        nodes.forEach { n ->
            incluir(n.point.x, n.point.y)
            incluir(n.inHandle.x, n.inHandle.y)
            incluir(n.outHandle.x, n.outHandle.y)
        }
        val margen = drawWidth / 2f
        // El margen va **sumado** a la derecha y abajo, y restado arriba y a la izquierda.
        // Restarlo también en la derecha y abajo recortaba la caja: una curva en horizontal
        // quedaba con alto cero, y el extremo derecho de cualquier curva se quedaba fuera de
        // la zona donde se puede tomar, que es justo para lo que se cuenta el margen.
        return Rect(left - margen, top - margen, right + margen, bottom + margen)
    }

    /** ¿El punto (en coordenadas de la hoja) cae dentro de la caja de la curva? */
    fun contains(x: Float, y: Float): Boolean = bounds().contains(Offset(x, y))

    /**
     * Tirador más cercano a [target], con el nodo al que pertenece, o null.
     *
     * Se miran solo los tiradores que están de verdad aparte del nodo. Un tirador pegado al
     * nodo está a distancia cero de él, así que entrarían todos en el radio y tocar el nodo
     * agarrar el tirador, que además se quedaría donde estaba: mover el nodo no se podía
     * nunca, y siempre se acababa doblando la curva sin querer.
     *
     * De los que caen en el radio gana el **más cercano**, no el primero de la lista. Con el
     * orden de la lista ganaba el nodo más antiguo, y en una curva larga con dos tiradores
     * cerca uno del otro se agarraba el que no se quería.
     */
    fun handleNear(target: Offset, radius: Float): Pair<CurveNode, Boolean>? {
        var mejorNodo: CurveNode? = null
        var mejorEntrada = true
        var mejorDistancia = Float.MAX_VALUE
        nodes.forEach { n ->
            if (!n.hasHandles) return@forEach
            if (n.hitsInHandle(target, radius)) {
                val d = n.distanciaAEntrada(target)
                if (d < mejorDistancia) {
                    mejorDistancia = d
                    mejorNodo = n
                    mejorEntrada = true
                }
            }
            if (n.hitsOutHandle(target, radius)) {
                val d = n.distanciaASalida(target)
                if (d < mejorDistancia) {
                    mejorDistancia = d
                    mejorNodo = n
                    mejorEntrada = false
                }
            }
        }
        val nodo = mejorNodo ?: return null
        return nodo to mejorEntrada
    }

    /**
     * Nodo más cercano a [target], o null si no hay ninguno a distancia [radius].
     *
     * Es lo que hace falta para recolocar un nodo suelto, que antes no se podía: sin esto, un
     * nodo sin tiradores no tenía donde agarrarse y tocarlo movía la curva entera.
     *
     * Gana el más cercano por la misma razón que en [handleNear].
     */
    fun nodeNear(target: Offset, radius: Float): CurveNode? {
        var mejor: CurveNode? = null
        var mejorDistancia = Float.MAX_VALUE
        nodes.forEach { n ->
            if (!n.hitsNode(target, radius)) return@forEach
            val d = hypot((n.point.x - target.x).toDouble(), (n.point.y - target.y).toDouble()).toFloat()
            if (d < mejorDistancia) {
                mejorDistancia = d
                mejor = n
            }
        }
        return mejor
    }

    companion object {
        /** Escala mínima: por debajo la línea desaparece y no se puede volver a tomar. */
        const val MIN_SCALE = 0.1f

        /** Escala máxima: evita que la curva crezca hasta comerse la memoria al medirla. */
        const val MAX_SCALE = 12f
    }
}

/** Aleja o acerca [p] del punto fijo ([x], [y]) por [factor], sin mover el punto fijo. */
private fun escalar(p: Offset, x: Float, y: Float, factor: Float): Offset =
    Offset(x + (p.x - x) * factor, y + (p.y - y) * factor)

/** Un nodo de curva, tal como queda en disco. */
data class CurveNodeSnapshot(
    val x: Float,
    val y: Float,
    val outHandleX: Float,
    val outHandleY: Float,
    val inHandleX: Float,
    val inHandleY: Float,
    val isCorner: Boolean
)

/** Una curva de capa, tal como queda en disco. */
data class CurveSnapshot(
    val nodes: List<CurveNodeSnapshot>,
    val colorArgb: Int,
    val width: Float,
    val scale: Float,
    val crop: ObjectBounds? = null
)
