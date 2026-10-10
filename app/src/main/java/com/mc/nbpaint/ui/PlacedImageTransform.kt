package com.mc.nbpaint.ui

import androidx.compose.ui.geometry.Offset

/**
 * Dónde está y qué tamaño tiene una imagen puesta en una capa.
 *
 * [scale] multiplica el tamaño natural del bitmap, y [offsetX]/[offsetY] son del borde
 * superior izquierdo de la imagen ya escalada, en coordenadas de la hoja.
 */
data class PlacedTransform(
    val offsetX: Float,
    val offsetY: Float,
    val scale: Float
)

/**
 * Las reglas de mover y escalar una imagen puesta, sin Android ni Compose: así se pueden
 * probar en un test de JVM, que es la única forma de revisar la matemática de la pinza sin
 * depender de un dedo real en la pantalla.
 */
object PlacedImageTransform {

    /** Escala mínima: con menos la imagen desaparece y no se puede volver a tomar. */
    const val MIN_SCALE = 0.05f

    /** Escala máxima: evita que se infle hasta comerse la memoria. */
    const val MAX_SCALE = 20f

    /**
     * Devuelve el estado que deja la imagen puesta después de una pinza.
     *
     * La escala se ancla en [focus]: el punto que está bajo los dedos se queda bajo los
     * dedos, en vez de empujar la imagen hacia el otro lado. También la deja en escala
     * negativa, se deja estar como estaba.
     */
    fun zoom(antes: PlacedTransform, focus: Offset, zoomChange: Float): PlacedTransform {
        if (zoomChange <= 0f || !zoomChange.isFinite()) return antes
        val despues = (antes.scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        if (despues == antes.scale) return antes
        // Dónde cae el foco dentro de la imagen, medido en su propio espacio.
        val relativaX = (focus.x - antes.offsetX) / antes.scale
        val relativaY = (focus.y - antes.offsetY) / antes.scale
        return PlacedTransform(
            offsetX = focus.x - relativaX * despues,
            offsetY = focus.y - relativaY * despues,
            scale = despues
        )
    }

    /** Estado que deja la imagen después de arrastrarla desde [origen] hasta [destino]. */
    fun drag(
        antes: PlacedTransform,
        desde: Offset,
        hasta: Offset,
        origenX: Float,
        origenY: Float
    ): PlacedTransform = antes.copy(
        offsetX = origenX + (hasta.x - desde.x),
        offsetY = origenY + (hasta.y - desde.y)
    )
}
