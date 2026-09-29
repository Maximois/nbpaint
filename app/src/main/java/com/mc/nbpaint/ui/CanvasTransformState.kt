package com.mc.nbpaint.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.min

/** Cuántas veces se puede ampliar respecto del encuadre que muestra el documento entero. */
const val MAX_ZOOM_FACTOR = 8f

/** Zoom al que salta el doble toque para ver detalle. */
private const val DOUBLE_TAP_FACTOR = 2.5f

/** Qué tan rápido se frena el lienzo después de soltar el dedo. */
private const val FRICTION = 4.5f

/** Duración de cada paso de la inercia. */
private const val FRAME_TIME_MS = 16L

/**
 * Vista del lienzo (zoom + desplazamiento) con las reglas de una app de dibujo de verdad:
 * el arte nunca se pierde de la pantalla y siempre queda algo de margen mientras se mueve.
 *
 * El documento tiene un tamaño propio e independiente del visor, así que `scale` 1 no
 * significa "sin zoom" sino "el tamaño real del dibujo". El encuadre (`fitScale`) es la
 * escala a la que el documento entero entra en el visor: al abrir o cerrar el dock el
 * encuadre se recalcula, pero los píxeles del dibujo no se tocan.
 */
@Stable
class CanvasTransformState(
    private val scope: CoroutineScope,
    initialViewport: IntSize
) {
    var viewport by mutableStateOf(initialViewport)
        private set

    /** Tamaño real del documento, en píxeles de dibujo. */
    var contentSize by mutableStateOf(IntSize.Zero)
        private set

    var scale by mutableFloatStateOf(1f)
        private set

    var pan by mutableStateOf(Offset.Zero)
        private set

    private var inertiaJob: Job? = null

    val isZoomed: Boolean get() = scale > fitScale * 1.01f

    val zoomPercent: Int
        get() {
            val fit = fitScale
            return if (fit <= 0f) 100 else ((scale / fit) * 100).toInt()
        }

    /**
     * Escala a la que el documento completo entra en el visor. Si el documento todavía no
     * se midió, o el visor está vacío, se cae en 1 para no dividir por cero.
     */
    val fitScale: Float
        get() {
            val cw = contentSize.width
            val ch = contentSize.height
            if (cw <= 0 || ch <= 0) return 1f
            if (viewport.width <= 0 || viewport.height <= 0) return 1f
            return min(viewport.width.toFloat() / cw, viewport.height.toFloat() / ch)
        }

    /**
     * El visor cambia de tamaño cada vez que se abre o cierra el dock. Si se estaba viendo
     * el documento entero hay que volver a encuadrarlo para que siga entrando, pero sin
     * tocar sus píxeles: si no, al encoger el visor el dibujo quedaría más grande que la
     * ventana. Si el usuario estaba ampliando algo, en cambio, se respeta su zoom y solo se
     * acotan los límites del arrastre.
     */
    fun onViewportSize(size: IntSize) {
        // El encuadre anterior decide si reencuadrar: hay que compararlo contra la escala
        // que se tenía antes de cambiar el visor, no contra el nuevo.
        val wasAtFit = scale <= fitScale * 1.01f
        viewport = size
        if (wasAtFit) {
            scale = fitScale
            pan = clampPan(Offset.Zero, scale)
        } else {
            pan = clampPan(pan, scale)
        }
    }

    fun onContentSize(size: IntSize) {
        if (size == contentSize) return
        contentSize = size
        // El contenido se acaba de medir: se reencuadra para que entre entero.
        scale = fitScale
        pan = clampPan(pan, scale)
    }

    /**
     * Con el origen de la transformación en (0,0) el contenido ocupa
     * `[pan, pan + tamañoDocumento * escala]`. Para que siga cubriendo el visor hace
     * falta `pan <= 0` y que el borde derecho no se pase; si el documento es más chico
     * que el visor queda centrado y sin juego en ese eje.
     */
    private fun clampPan(candidate: Offset, atScale: Float): Offset {
        val cw = if (contentSize.width > 0) contentSize.width else viewport.width
        val ch = if (contentSize.height > 0) contentSize.height else viewport.height
        return Offset(
            x = clampAxis(candidate.x, cw * atScale, viewport.width),
            y = clampAxis(candidate.y, ch * atScale, viewport.height)
        )
    }

    private fun clampAxis(value: Float, content: Float, available: Int): Float {
        val room = available.toFloat()
        if (content <= room) {
            // El documento entra entero en este eje: se centra y no se desplaza.
            return (room - content) / 2f
        }
        // Al exceder, se limita el arrastre para no descubrir área vacía.
        return value.coerceIn(room - content, 0f)
    }

    /** Zoom anclado al centroide: lo que está bajo los dedos se queda quieto. */
    fun applyTransform(transform: CanvasTransform) {
        inertiaJob?.cancel()
        val oldScale = scale
        val fit = fitScale
        val newScale = (oldScale * transform.zoom).coerceIn(fit, fit * MAX_ZOOM_FACTOR)
        val base = pan + transform.pan
        val ratio = if (oldScale > 0f) newScale / oldScale else 1f
        val zoomed = transform.centroid - (transform.centroid - base) * ratio
        scale = newScale
        pan = clampPan(zoomed, newScale)
    }

    /**
     * Al soltar se sigue el movimiento con velocidad decreciente. Sin esto el lienzo se
     * frena de golpe al levantar el dedo y la sensación es de una hoja pesada.
     *
     * Se integra a mano el decaimiento en lugar de usar `animateDecay`: hace falta poder
     * frenar en el momento en que se topa con el borde del lienzo.
     */
    fun startInertia(velocity: Offset) {
        inertiaJob?.cancel()
        val speed = hypot(velocity.x.toDouble(), velocity.y.toDouble()).toFloat()
        if (speed < 150f || scale <= fitScale * 1.01f) return

        val atScale = scale
        inertiaJob = scope.launch {
            var position = pan
            var vx = velocity.x
            var vy = velocity.y
            val dt = 1f / 60f

            while (true) {
                delay(FRAME_TIME_MS)
                val candidate = Offset(position.x + vx * dt, position.y + vy * dt)
                val next = clampPan(candidate, atScale)
                // Si ya se tocó el tope, frenar: evita el rebote contra el borde.
                if (next == position) break
                position = next
                pan = next

                val friction = kotlin.math.exp(-FRICTION * dt)
                vx *= friction
                vy *= friction
                if (hypot(vx.toDouble(), vy.toDouble()) < 30f) break
            }
        }
    }

    fun toggleDoubleTap(point: Offset) {
        inertiaJob?.cancel()
        val fit = fitScale
        if (scale > fit * 1.05f) {
            scale = fit
            pan = clampPan(Offset.Zero, fit)
            return
        }
        val target = (fit * DOUBLE_TAP_FACTOR).coerceAtMost(fit * MAX_ZOOM_FACTOR)
        scale = target
        val focus = if (viewport.width > 0 && viewport.height > 0) {
            Offset(
                x = point.x.coerceIn(0f, viewport.width.toFloat()),
                y = point.y.coerceIn(0f, viewport.height.toFloat())
            )
        } else {
            Offset.Zero
        }
        pan = clampPan(focus * (1f - target), target)
    }

    fun reset() {
        inertiaJob?.cancel()
        scale = fitScale
        pan = clampPan(Offset.Zero, scale)
    }
}

@Composable
fun rememberCanvasTransformState(): CanvasTransformState {
    val scope = rememberCoroutineScope()
    return remember(scope) { CanvasTransformState(scope, IntSize.Zero) }
}
