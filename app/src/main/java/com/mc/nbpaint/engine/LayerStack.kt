package com.mc.nbpaint.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import com.mc.nbpaint.model.PaintLayer
import kotlin.math.max

/**
 * Motor de capas: agregar, quitar, reordenar, combinar y vaciar.
 *
 * Todas las funciones reciben y devuelven la lista y la posición activa, sin estado propio.
 * Así se pueden probar y, sobre todo, razonar: si algo cambia las capas, pasa por aquí.
 *
 * Índice 0 es la capa de fondo y el final es la de arriba, igual que en la lista del panel
 * (que se muestra invertida para que la de arriba se vea primero).
 *
 * Mezcla dos clases de funciones, a propósito. Las que llevan `Bitmap` o `Canvas` no se
 * pueden probar en la JVM, así que se apoyan en las de aquí: las reglas puras (a qué índice
 * queda activa la capa, cómo se llama, cuándo está vacía) son las que fallan en silencio
 * cuando algo se corre de lugar, y por eso están todas juntas y cubiertas por tests.
 */
object LayerStack {

    // ---- Reglas puras, testeables en la JVM ---------------------------------

    /**
     * A qué capa activa queda el panel después de borrar la que estaba en [removed].
     *
     * Borrar de arriba deja un hueco, así que la activa baja una posición para no apuntar a
     * otra capa. Borrar la de abajo deja la activa donde estaba. Y si se borró la última,
     * la activa se queda en la última que quede en vez de quedar fuera de la lista.
     */
    fun activeIndexAfterRemove(active: Int, removed: Int, newSize: Int): Int = when {
        active >= newSize -> newSize - 1
        active > removed -> active - 1
        else -> active
    }

    /**
     * A qué capa activa se sigue la capa que estaba en [index] al subirla una posición.
     *
     * Si la activa era esa, sube con ella; si era la que tenía justo encima, esa baja a su
     * lugar. En cualquier otro caso la activa no se mueve.
     *
     * Quien llama tiene que rechazar antes el movimiento imposible: [index] no puede ser la
     * última, o la activa quedaría una posición más allá de la última capa. Aquí la regla
     * solo dice dónde cae la activa, no si el movimiento tiene sentido.
     */
    fun activeIndexAfterMoveUp(active: Int, index: Int): Int = when (active) {
        index -> index + 1
        index + 1 -> index
        else -> active
    }

    /**
     * Igual que [activeIndexAfterMoveUp], pero bajando una posición.
     *
     * [index] no puede ser 0, o la activa quedaría en -1.
     */
    fun activeIndexAfterMoveDown(active: Int, index: Int): Int = when (active) {
        index -> index - 1
        index - 1 -> index
        else -> active
    }

    /** Nombre que se le pone a la enésima capa nueva: "Capa 1", "Capa 2", ... */
    fun defaultLayerName(counter: Int): String = "Capa $counter"

    /**
     * Si una capa no tiene nada que mostrar, para no gastar un punto de deshacer en vaciar lo
     * que ya estaba vacío.
     *
     * Se recibe el bitmap ya en píxeles porque en la JVM no se puede crear uno.
     */
    fun isEmpty(pixels: IntArray, width: Int, height: Int): Boolean =
        SelectionEngine.contentBounds(pixels, width, height) == null

    /**
     * ¿La capa queda corta para cubrir la hoja? Es decir, ¿es más chica en ancho o en alto?
     *
     * Una capa así solo ocupa su trozo del lienzo: lo que se pinta fuera se recorta al bitmap
     * y desaparece sin avisar. Antes de editar píxeles hay que [ensureSheetSize]arla.
     *
     * Solo mira si queda corta, nunca si sobra: una capa más grande que la hoja no se recorta
     * aquí, o se perdería contenido por el simple hecho de pintar.
     */
    fun needsSheetExpansion(bitmapWidth: Int, bitmapHeight: Int, width: Int, height: Int): Boolean =
        bitmapWidth < width || bitmapHeight < height

    // ---- Trabajo con bitmaps, verificado a mano en el dispositivo -----------

    /** Rellena una capa con el color de fondo de la hoja. */
    fun fill(layer: PaintLayer, color: Int) {
        Canvas(layer.bitmap).drawColor(color)
    }

    /** Vacía una capa. La de fondo se rellena con el color de la hoja, no con transparente. */
    fun clear(layer: PaintLayer, isBackground: Boolean, background: Int) {
        if (isBackground) {
            fill(layer, background)
        } else {
            Canvas(layer.bitmap).drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        }
    }

    /**
     * Combina [index] con la de abajo ([index] - 1).
     *
     * La mezcla de píxeles se hace aquí, pero la baja de la capa y el punto de deshacer los
     * lleva la fachada, porque dependen del historial y de cuál era la capa activa: si el
     * mismo motor cerrara el cambio entero, deshacer un combinar dejaría medio estado.
     *
     * @return la capa resultante, o null si no se puede combinar.
     */
    fun mergeDownInto(
        layers: List<PaintLayer>,
        index: Int
    ): PaintLayer? {
        if (index <= 0 || index >= layers.size) return null
        return layers[index - 1]
    }

    /**
     * Copia la geometría de [top] dentro de [bottom] y deja la de abajo con el tamaño de la
     * hoja, que es la regla del documento: todas las capas miden lo mismo.
     */
    fun ensureSheetSize(layer: PaintLayer, width: Int, height: Int, background: Int) {
        if (layer.bitmap.width == width && layer.bitmap.height == height) return
        layer.bitmap = fitToSheet(layer.bitmap, width, height, background)
    }

    /**
     * Reajusta un bitmap al tamaño de la hoja sin deformarlo: misma escala, alineado en la
     * esquina, y lo que sobra se rellena con el fondo.
     *
     * Estirar aquí es lo que convertía las fotos en rectángulos al importar.
     */
    fun fitToSheet(bitmap: Bitmap, width: Int, height: Int, background: Int): Bitmap {
        if (bitmap.width == width && bitmap.height == height) return bitmap
        val adjusted = Bitmap.createBitmap(max(1, width), max(1, height), Bitmap.Config.ARGB_8888)
        Canvas(adjusted).apply {
            drawColor(background)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        return adjusted
    }
}
