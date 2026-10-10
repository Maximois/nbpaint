package com.mc.nbpaint.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Offset
import com.mc.nbpaint.model.CurveNode
import com.mc.nbpaint.model.CurveObject
import kotlin.math.hypot

    /**
     * Dibujo de las curvas de capa.
     *
     * Una curva es una lista de nodos con dos tiradores cada uno, y el tramo entre dos nodos
     * vecinos es una de Bézier con esos tiradores como puntos de control. Se dibuja con
     * `Path.cubicTo`, que es la curva que el propio Android sabe pintar.
     *
     * Aquí vive el dibujo, y no en la pantalla, porque lo necesitan el lienzo, la fusión de
     * objetos y el guardado: si cada uno trazara la curva por su cuenta, la línea que se ve
     * sería una y la que se guarda, otra.
     */
object CurveEngine {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Separación mínima entre dos nodos, en píxeles. Evita nodos repetidos en un dedo quieto. */
    const val MIN_NODE_DISTANCE = 6f

    /**
     * Convierte un trazo del dedo en los nodos de la curva.
     *
     * Se muestrea en lugar de quedarse con todos los puntos: un trazo de dos segundos tiene
     * cientos, y una curva con cientos de nodos no se puede corregir. Se queda con un punto
     * cada [step] píxeles, más o menos.
     *
     * Cada nodo nace sin tiradores, así que la curva sale passando por todos ellos: es el
     * trazo tal cual, sin suavizar. Suavizar de entrada (con los tiradores puestos a lo largo
     * de la línea) enmascararía el temblor del dedo y hace que la curva no pase por donde el
     * usuario dibujó, que es justo lo que se corrige después con los nodos.
     */
    fun fromStroke(points: List<Offset>, step: Float = MIN_NODE_DISTANCE * 3f): CurveObject {
        val curve = CurveObject()
        if (points.isEmpty()) return curve
        var ultimo: Offset? = null
        points.forEach { p ->
            val anterior = ultimo
            if (anterior == null || distancia(anterior, p) >= step) {
                curve.nodes += CurveNode(p)
                ultimo = p
            }
        }
        // Un solo punto no es una curva: sin él no hay nada que dibujar, y arrastrar y soltar
        // sin mover apenas debería dejar nada detrás.
        if (curve.nodes.size < 2) return CurveObject()
        return curve
    }

    /** Construye el `Path` de la curva, lista para pintarse. */
    fun pathOf(curve: CurveObject): Path? {
        if (!curve.isDrawable) return null
        val path = Path()
        val primero = curve.nodes.first()
        path.moveTo(primero.point.x, primero.point.y)
        for (i in 0 until curve.nodes.size - 1) {
            val desde = curve.nodes[i]
            val hasta = curve.nodes[i + 1]
            // El tramo se dibuja con los dos tiradores: el de salida del nodo que se deja y
            // el de entrada del que se llega. Con un nodo sin tiradores, el tramo se
            // enderezaría hacia el centro y la curva se afilaba, así que se sustituye por el
            // propio nodo, que es lo que hace `cubicTo` con un control igual al final.
            path.cubicTo(
                desde.outHandle.x, desde.outHandle.y,
                hasta.inHandle.x, hasta.inHandle.y,
                hasta.point.x, hasta.point.y
            )
        }
        return path
    }

    /** Pinta la curva en un `Canvas` de Android, con su color y su grosor. */
    fun draw(canvas: Canvas, curve: CurveObject, reusePaint: Paint = paint) {
        val path = pathOf(curve) ?: return
        reusePaint.color = curve.colorArgb
        reusePaint.strokeWidth = curve.drawWidth
        canvas.drawPath(path, reusePaint)
    }

    /** Distancia entre dos puntos, en píxeles. */
    fun distancia(a: Offset, b: Offset): Float =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
}
