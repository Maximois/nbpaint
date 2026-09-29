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

const val MIN_SCALE = 1f
const val MAX_SCALE = 8f

/** Zoom al que salta el doble toque para ver detalle. */
private const val DOUBLE_TAP_SCALE = 2.5f

/** Qué tan rápido se frena el lienzo después de soltar el dedo. */
private const val FRICTION = 4.5f

/** Duración de cada paso de la inercia. */
private const val FRAME_TIME_MS = 16L

/**
 * Vista del lienzo (zoom + desplazamiento) con las reglas de una app de dibujo de verdad:
 * el arte nunca se pierde de la pantalla y siempre queda algo de margen mientras se mueve.
 *
 * El desplazamiento va acotado a propósito. El lienzo se crea del mismo tamaño que el
 * visor, así que a escala 1 no hay holgura para mover nada; y si el tope no existiera se
 * podría arrastrar el dibujo fuera de la pantalla sin ninguna referencia de dónde está,
 * que es justo lo que hace que mover la hoja se sienta pesado.
 */
@Stable
class CanvasTransformState(
    private val scope: CoroutineScope,
    initialViewport: IntSize
) {
    var viewport by mutableStateOf(initialViewport)
        private set

    var scale by mutableFloatStateOf(1f)
        private set

    var pan by mutableStateOf(Offset.Zero)
        private set

    private var inertiaJob: Job? = null

    val isZoomed: Boolean get() = scale != 1f || pan != Offset.Zero

    val zoomPercent: Int get() = (scale * 100).toInt()

    fun onViewportSize(size: IntSize) {
        viewport = size
        pan = clampPan(pan, scale)
    }

    /**
     * Con el origen de la transformación en (0,0) el contenido ocupa
     * `[pan, pan + tamaño * escala]`. Para que siga cubriendo el visor hace falta
     * `pan <= 0` y `pan + tamaño * escala >= tamaño`, o sea un rango de un solo sentido.
     */
    private fun clampPan(candidate: Offset, atScale: Float): Offset {
        val slackX = viewport.width * (atScale - 1f)
        val slackY = viewport.height * (atScale - 1f)
        return Offset(
            x = if (slackX <= 0f) 0f else candidate.x.coerceIn(-slackX, 0f),
            y = if (slackY <= 0f) 0f else candidate.y.coerceIn(-slackY, 0f)
        )
    }

    /** Zoom anclado al centroide: lo que está bajo los dedos se queda quieto. */
    fun applyTransform(transform: CanvasTransform) {
        inertiaJob?.cancel()
        val oldScale = scale
        val newScale = (oldScale * transform.zoom).coerceIn(MIN_SCALE, MAX_SCALE)
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
        if (speed < 150f || scale <= 1f) return

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
        if (scale > 1.05f) {
            scale = 1f
            pan = Offset.Zero
            return
        }
        scale = DOUBLE_TAP_SCALE
        val focus = if (viewport.width > 0 && viewport.height > 0) {
            Offset(
                x = point.x.coerceIn(0f, viewport.width.toFloat()),
                y = point.y.coerceIn(0f, viewport.height.toFloat())
            )
        } else {
            Offset.Zero
        }
        pan = clampPan(focus * (1f - DOUBLE_TAP_SCALE), DOUBLE_TAP_SCALE)
    }

    fun reset() {
        inertiaJob?.cancel()
        scale = 1f
        pan = Offset.Zero
    }
}

@Composable
fun rememberCanvasTransformState(): CanvasTransformState {
    val scope = rememberCoroutineScope()
    return remember(scope) { CanvasTransformState(scope, IntSize.Zero) }
}
