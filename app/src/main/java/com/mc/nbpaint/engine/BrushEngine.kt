package com.mc.nbpaint.engine

import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Qué figura se está arrastrando. */
enum class ShapeKind {
    LINE,
    RECTANGLE,
    CIRCLE
}

/**
 * La figura ya calculada, en coordenadas de la hoja.
 *
 * Existe para que la vista previa y el trazo final usen **la misma** geometría. Antes cada
 * uno calculaba la suya por su cuenta y cuando se tocaban no coincidían: se veía un círculo
 * mientras se arrastraba y salía un óvalo al soltar. Si la cuenta está aquí una sola vez,
 * los dos dibujan exactamente lo mismo por construcción.
 */
sealed interface ShapeGeometry {
    data class Segment(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : ShapeGeometry
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) : ShapeGeometry
    data class Disc(val centerX: Float, val centerY: Float, val radius: Float) : ShapeGeometry
}

/**
 * Motor de las primitivas de dibujo.
 *
 * Solo pinta: no sabe qué herramienta está activa, ni de qué capa se trata, ni nada del
 * historial. Eso lo decide la fachada. Aquí vive únicamente "cómo se calcula y se pinta una
 * línea, un rectángulo, un círculo y un Spray", que es lo que se puede probar en la JVM.
 */
object BrushEngine {

    /** Radio del círculo que sale de arrastrar entre dos puntos: la mitad de la diagonal. */
    fun radiusOf(sx: Float, sy: Float, ex: Float, ey: Float): Float =
        hypot((ex - sx).toDouble(), (ey - sy).toDouble()).toFloat() / 2f

    /** Calcula la figura. El rectángulo sale con las esquinas en orden, se arrastre hacia donde se arrastre. */
    fun geometryOf(kind: ShapeKind, sx: Float, sy: Float, ex: Float, ey: Float): ShapeGeometry = when (kind) {
        ShapeKind.LINE -> ShapeGeometry.Segment(sx, sy, ex, ey)
        ShapeKind.RECTANGLE -> ShapeGeometry.Box(
            min(sx, ex), min(sy, ey), maxOf(sx, ex), maxOf(sy, ey)
        )
        ShapeKind.CIRCLE -> ShapeGeometry.Disc(
            (sx + ex) / 2f, (sy + ey) / 2f, radiusOf(sx, sy, ex, ey)
        )
    }

    /** Pinta la figura ya calculada. */
    fun draw(canvas: Canvas, geometry: ShapeGeometry, paint: Paint) {
        when (geometry) {
            is ShapeGeometry.Segment -> canvas.drawLine(geometry.x1, geometry.y1, geometry.x2, geometry.y2, paint)
            is ShapeGeometry.Box -> canvas.drawRect(
                geometry.left, geometry.top, geometry.right, geometry.bottom, paint
            )
            is ShapeGeometry.Disc -> canvas.drawCircle(
                geometry.centerX, geometry.centerY, geometry.radius, paint
            )
        }
    }

    /**
     * Una ráfaga de puntos redondos alrededor de [center], como un aerógrafo.
     *
     * El [random] se puede pasar para que un test pueda repetir el mismo resultado; por
     * defecto usa el del sistema, que es lo que hace que dos pasadas no salgan iguales.
     */
    fun spray(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        paint: Paint,
        dots: Int = 18,
        dotRadius: Float = 1.6f,
        random: Random = Random.Default
    ) {
        val sprayPaint = Paint(paint).apply {
            style = Paint.Style.FILL
            strokeWidth = 0f
        }
        repeat(dots) {
            val angle = random.nextDouble(0.0, 2 * Math.PI)
            val r = random.nextDouble(0.0, radius.toDouble())
            canvas.drawCircle(
                centerX + (cos(angle) * r).toFloat(),
                centerY + (sin(angle) * r).toFloat(),
                dotRadius,
                sprayPaint
            )
        }
    }

    /**
     * Pinta un tramo de pincel estampando un toque cada [BrushTip.ESPACIADO_MINIMO] o más.
     *
     * Antes el trazo era un `drawLine` a secas. Eso obliga a que el borde sea duro: `drawLine`
     * no sabe de degradados, y el truco de pintar la misma línea varias veces con opacidad
     * cada vez menor —que es como se hace a mano— cuesta un canvas entero por trazo y aun
     * así deja un borde de escalones. Estampando, el degradado lo pone el `shader` de cada
     * toque y sale limpio.
     *
     * El degradado se construye **una vez por trazo**, no uno por toque. Es lo que hace
     * viable el pincel suave: construir un `RadialGradient` es de lo más caro que hace
     * `Paint`, y hacerlo por toque con el dedo moviéndose es la diferencia entre pintar
     * fluido y ir a trompicones.
     *
     * @param celosia número de puntos de grano que se sortean por toque, solo para el lápiz.
     */
    fun pincelSegmento(
        canvas: Canvas,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        settings: BrushSettings,
        paint: Paint,
        color: Int,
        alpha: Float,
        celosia: Int = 0,
        random: Random = Random.Default
    ) {
        val radio = max(settings.size / 2f, 0.5f)
        val puntos = BrushTip.toques(fromX, fromY, toX, toY, settings)
        val pincel = Paint(paint).apply {
            style = Paint.Style.FILL
            strokeWidth = 0f
            isAntiAlias = true
        }

        if (settings.usaDegradado) {
            val paradas = BrushTip.paradasGradiente(settings.radioNucleo, radio)
            val colores = IntArray(paradas.opacidades.size) { i ->
                ColorUtils.setAlphaComponent(color, (paradas.opacidades[i] * 255f * alpha).toInt().coerceIn(0, 255))
            }
            pincel.shader = android.graphics.RadialGradient(
                0f, 0f, radio,
                colores, paradas.posiciones,
                android.graphics.Shader.TileMode.CLAMP
            )
        } else {
            pincel.color = ColorUtils.setAlphaComponent(color, (255f * alpha).toInt().coerceIn(0, 255))
        }

        // El shader va en coordenadas del lienzo: sin esta matriz el degradado se repetiría
        // en cada toque en vez de seguir al pincel, y el trazo saldría con un patrón de
        // círculos concéntricos en vez de una mancha continua.
        val matriz = android.graphics.Matrix()
        var i = 0
        while (i < puntos.size) {
            val x = puntos[i]
            val y = puntos[i + 1]
            matriz.setTranslate(x, y)
            pincel.shader?.setLocalMatrix(matriz)
            canvas.drawCircle(x, y, radio, pincel)
            if (celosia > 0) {
                repeat(celosia) {
                    val angulo = random.nextDouble(0.0, 2 * Math.PI)
                    val r = random.nextDouble(0.0, radio.toDouble())
                    canvas.drawCircle(
                        x + (cos(angulo) * r).toFloat(),
                        y + (sin(angulo) * r).toFloat(),
                        radio * 0.22f,
                        pincel
                    )
                }
            }
            i += 2
        }
    }
}
