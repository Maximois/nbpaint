package com.mc.nbpaint.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.hypot

/** Cambio de vista del lienzo en un evento: centro del gesto, desplazamiento y factor de zoom. */
data class CanvasTransform(val centroid: Offset, val pan: Offset, val zoom: Float)

/**
 * Reconocedor único para el lienzo: un dedo dibuja, dos dedos desplazan y agrandan, y con
 * el modo Mover un solo dedo también desplaza.
 *
 * Se evita a propósito apilar un `pointerInput` para dibujar y otro para transformar. Con
 * dos reconocedores el primer dedo sigue marcando un trazo mientras se hace zoom con el
 * segundo, y esos trazos fantasma son justo lo que hace que mover la hoja se sienta pesado.
 * Acá el trazo se cancela en el momento en que aparece el segundo dedo.
 */
suspend fun PointerInputScope.detectCanvasGestures(
    singleFingerPans: Boolean,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    onTap: (Offset) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onDrawStart: (Offset) -> Unit,
    onDrawMove: (Offset) -> Unit,
    onDrawEnd: () -> Unit,
    onTransform: (CanvasTransform) -> Unit,
    onZoomEnd: (velocity: Offset) -> Unit,
    onImageTransform: (CanvasTransform) -> Unit
) {
    val touchSlop = viewConfiguration.touchSlop
    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
    val doubleTapMinTime = viewConfiguration.doubleTapMinTimeMillis
    var lastTapUptime = 0L

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // El gesto empieza en el primer dedo y termina cuando se levantan todos. Se avisa
        // aparte de `onDrawStart`/`onDrawEnd` porque aquellos solo cubren el trazo: aquí
        // interesa todo gesto que toque el lienzo, incluido el pellizco de dos dedos.
        onGestureStart()
        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        var previous = down.position
        var drawStarted = false
        var transformed = false
        var pastSlop = false
        var lastCentroid = down.position
        var lastDistance = 0f

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break

            if (pressed.size >= 2) {
                if (!transformed) {
                    transformed = true
                    if (drawStarted) {
                        onDrawEnd()
                        drawStarted = false
                    }
                    lastCentroid = midpoint(pressed[0].position, pressed[1].position)
                    lastDistance = distance(pressed[0].position, pressed[1].position)
                }
                val centroid = midpoint(pressed[0].position, pressed[1].position)
                val spread = distance(pressed[0].position, pressed[1].position)
                val zoomChange = if (lastDistance > 0f) spread / lastDistance else 1f
                val gesture = CanvasTransform(centroid, centroid - lastCentroid, zoomChange)
                // Con la herramienta de mover imagen los dos dedos escalan la imagen de la
                // capa; el resto de las herramientas escalan la vista del lienzo. Quien
                // decide es onImageTransform, que mira la herramienta activa en el momento.
                onImageTransform(gesture)
                lastCentroid = centroid
                lastDistance = spread
                previous = centroid
                event.changes.forEach { it.consume() }
                continue
            }

            val change = pressed.first()
            val position = change.position
            velocityTracker.addPosition(change.uptimeMillis, position)

            val totalX = position.x - down.position.x
            val totalY = position.y - down.position.y
            if (!pastSlop && hypot(totalX, totalY) > touchSlop) pastSlop = true

            if (!transformed) {
                if (singleFingerPans) {
                    if (pastSlop) {
                        onTransform(
                            CanvasTransform(position, position - previous, 1f)
                        )
                    }
                } else {
                    if (pastSlop && !drawStarted) {
                        drawStarted = true
                        onDrawStart(down.position)
                    }
                    if (drawStarted) onDrawMove(position)
                }
            }
            previous = position
        }

        onGestureEnd()
        when {
            transformed -> {
                val v = velocityTracker.calculateVelocity()
                onZoomEnd(Offset(v.x, v.y))
            }
            drawStarted -> onDrawEnd()
            !pastSlop -> {
                val now = down.uptimeMillis
                val isDoubleTap = now - lastTapUptime in doubleTapMinTime..doubleTapTimeout
                if (isDoubleTap) {
                    onDoubleTap(down.position)
                    lastTapUptime = 0L
                } else {
                    onTap(down.position)
                    lastTapUptime = now
                }
            }
        }
    }
}

private fun midpoint(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)

private fun distance(a: Offset, b: Offset) = hypot(b.x - a.x, b.y - a.y)
