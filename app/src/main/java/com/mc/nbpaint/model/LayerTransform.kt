package com.mc.nbpaint.model

import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min

/**
 * Dónde está y qué tamaño tiene el contenido de una capa dentro de la hoja.
 *
 * Una capa no es solo un dibujo plano del tamaño del papel: también puede tener su propia
 * posición y su propio tamaño, como cuando se recorta una zona y se luego estira, o cuando se
 * trabaja en una parte de la imagen sin cubrir las demás. Sin esto, todas las capas comparten
 * la misma escala y el mismo sitio, y mover una mueve todo: no hay manera de agrandar una capa
 * sin mover el resto, ni de combinar una con otra si no es al revés.
 *
 * Se ancla en la esquina superior izquierda de la hoja, no en el centro: es lo que espera
 * quien recorta una zona y la estira hacia un lado, y hace que el cálculo sea una suma y una
 * multiplicación.
 *
 * Puro a propósito, sin `Bitmap` ni Android, para poder probar en la JVM la matemática de la
 * transformación, que es donde se cuelan los errores de un píxel que luego se ven como un
 * dibujo que se desplaza solo.
 */
data class LayerTransform(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f
) {
    /** Si la capa está como salió: ni movida ni estirada. */
    val esIdentity: Boolean
        get() = offsetX == 0f && offsetY == 0f && scale == 1f

    /**
     * Deja la escala dentro de un rango que se pueda volver a tomar.
     *
     * Con una escala demasiado pequeña la capa se hace ilegible y no hay forma de volver a
     * escogerla; con una demasiado grande se sale de la memoria al pintarla. El rango es
     * amplio a propósito: de 0.02 a 40 la capa todavía se ve y todavía se puede agarrar, y
     * en los dos extremos el contenido sigue estando en la hoja.
     */
    fun acotada(): LayerTransform = copy(scale = scale.coerceIn(MIN_SCALE, MAX_SCALE))

    /** Un punto del contenido de la capa, en la hoja. */
    fun aHoja(x: Float, y: Float): Offset = Offset(
        x = offsetX + x * scale,
        y = offsetY + y * scale
    )

    /** Un punto de la hoja, dentro del contenido de la capa. */
    fun aContenido(x: Float, y: Float): Offset {
        val s = if (scale > 0f) scale else 1f
        return Offset(x = (x - offsetX) / s, y = (y - offsetY) / s)
    }

    /**
     * Escala alrededor de [foco], que está en coordenadas de la hoja, dejando lo que está
     * bajo el dedo donde está.
     *
     * Es la misma cuenta que la pinza de la imagen puesta, y por el mismo motivo: si la
     * escala se aplicara en el centro de la hoja, al ampliar una capa el contenido se iría
     * para el lado contrario y habría que volver a buscarlo.
     */
    fun conZoom(foco: Offset, cambio: Float): LayerTransform {
        if (cambio <= 0f || !cambio.isFinite()) return this
        val nueva = (scale * cambio).coerceIn(MIN_SCALE, MAX_SCALE)
        if (nueva == scale) return this
        val dentroAntes = aContenido(foco.x, foco.y)
        val t = LayerTransform(offsetX = foco.x - dentroAntes.x * nueva, offsetY = foco.y - dentroAntes.y * nueva, scale = nueva)
        return t
    }

    companion object {
        /** Escala mínima: con menos la capa se hace ilegible y no se puede volver a tomar. */
        const val MIN_SCALE = 0.02f

        /** Escala máxima: evita que una capa se infle hasta comerse la memoria. */
        const val MAX_SCALE = 40f
    }
}

/**
 * Cuadro del contenido de una capa dentro de la hoja, ya transformado.
 *
 * Es lo que hay que mirar para saber si una capa toca la hoja: un recorte de documento que
 * solo mirara el papel dejaría fuera el contenido de una capa desplazada, que es justo el
 * error que hace que "encajar la hoja al dibujo" recorte la imagen de una capa que se ve
 * entera en pantalla.
 */
data class SheetRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val isEmpty: Boolean get() = right <= left || bottom <= top
}

/** Cuadro de [ancho] x [alto] con la transformación puesta, en coordenadas de la hoja. */
fun LayerTransform.cuadro(ancho: Int, alto: Int): SheetRect {
    val w = ancho.coerceAtLeast(0).toFloat()
    val h = alto.coerceAtLeast(0).toFloat()
    val a = aHoja(0f, 0f)
    val b = aHoja(w, h)
    return SheetRect(
        left = min(a.x, b.x),
        top = min(a.y, b.y),
        right = max(a.x, b.x),
        bottom = max(a.y, b.y)
    )
}
