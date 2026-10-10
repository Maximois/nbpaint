package com.mc.nbpaint.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import com.mc.nbpaint.model.IntRect
import com.mc.nbpaint.model.Selection
import com.mc.nbpaint.model.editableCopy

/**
 * Motor de recorte.
 *
 * Hay dos recortes y conviene no mezclarlos:
 *
 * - [cropToSelection] recorta la capa a [rect] y devuelve un bitmap del tamaño del recorte.
 *   Lo que queda fuera de esa capa se pierde, así que se deshace con el historial.
 * - [trimTransparent] quita las franjas vacías alrededor del dibujo sin tocar el tamaño,
 *   para arreglar una capa que quedó con mucho margen.
 *
 * Ojo con lo que [cropToSelection] **no** hace: la hoja no cambia de tamaño. Quien lo llama
 * es el que decide, y hoy solo ajusta la capa, dejando la hoja igual. Por eso la capa
 * recortada puede acabar siendo más pequeña que la hoja, y quien recorta tiene que
 * comprobarlo antes (ver `selectionEncajaEnCapa` en el `ViewModel`) en vez de asumir que
 * salen del mismo tamaño.
 *
 * La geometría vive en [SelectionEngine] y en [com.mc.nbpaint.model.IntRect], que son puros
 * y se prueban en la JVM. Aquí solo se junta eso con los `Bitmap` de Android.
 */
object CropEngine {

    /** Por debajo de este tamaño el recorte no vale la pena y solo traería píxeles degenerados. */
    private const val MIN_EDGE = 1

    /**
     * Recorta [bitmap] a [rect] y devuelve un bitmap nuevo del tamaño del recorte.
     *
     * @return null si el rectángulo no deja nada, para que quien llama avise en vez de
     *         dejar la capa con un tamaño inútil.
     */
    fun crop(bitmap: Bitmap, rect: IntRect): Bitmap? {
        val delimitado = rect.clamped(bitmap.width, bitmap.height)
        if (delimitado.isEmpty || delimitado.width < MIN_EDGE || delimitado.height < MIN_EDGE) return null
        if (delimitado.left == 0 && delimitado.top == 0 && delimitado.right == bitmap.width && delimitado.bottom == bitmap.height) {
            return bitmap.editableCopy()
        }
        return Bitmap.createBitmap(bitmap, delimitado.left, delimitado.top, delimitado.width, delimitado.height)
    }

    /**
     * Recorta la capa a la selección.
     *
     * Con una selección de marco ([Selection.Rect]) recorta de verdad y devuelve un bitmap
     * nuevo, más pequeño: un `Bitmap` de Android no puede cambiar de tamaño en sitio, así que
     * quien llama tiene que guardar el devuelto en la capa y dejar el viejo en el historial.
     * Con una región de forma libre no se puede reducir la hoja a una mancha irregular, así
     * que se devuelve el mismo tamaño con todo lo de fuera vaciado: es la forma de "aislar"
     * lo seleccionado sin cambiar el tamaño del documento.
     *
     * @return el bitmap con el que queda la capa, o null si no hay nada que recortar.
     */
    fun cropToSelection(bitmap: Bitmap, selection: Selection, background: Int): Bitmap? {
        if (selection is Selection.All) return null

        if (selection is Selection.Rect) {
            val cropped = crop(bitmap, selection.rect) ?: return null
            if (cropped.width == bitmap.width && cropped.height == bitmap.height) {
                cropped.recycle()
                return null
            }
            return cropped
        }

        // Región de forma libre: no se puede reducir la hoja a una mancha irregular, así que
        // se vacía lo de fuera y se recorta al rectángulo que abarca la región. Se trabaja
        // sobre una copia: si se vaciara el bitmap original en sitio, el historial guardaría
        // como "antes" una capa ya vaciada y deshacer no devolvería lo de fuera.
        val mask = selection as Selection.Mask
        val bounds = mask.bounds() ?: return null
        val limpio = bitmap.editableCopy()
        if (!clearOutside(limpio, selection, background)) {
            if (bounds.width == bitmap.width && bounds.height == bitmap.height) return null
        }
        if (bounds.width == bitmap.width && bounds.height == bitmap.height) return limpio
        return crop(limpio, bounds) ?: limpio
    }

    /**
     * Deja la capa con el tamaño de [rect] y su contenido corrido, de modo que lo que estaba en
     * la esquina ([offsetX], [offsetY]) quede ahora en (0, 0).
     *
     * Es el recorte que hace falta para recortar el **documento**: la hoja se encoge al rectángulo
     * y cada capa tiene que encoger a la vez, con su contenido desplazado lo mismo, o el dibujo se
     * quedaría en su sitio viejo y saldría del papel.
     *
     * No usa `crop(bitmap, rect)` porque ese corta solo la intersección con la capa y deja el
     * bitmap con el tamaño de esa intersección. Aquí el tamaño es el del rectángulo aunque la
     * capa sea más pequeña, y lo que no hay se rellena con el fondo.
     */
    fun cropAt(bitmap: Bitmap, rect: IntRect, offsetX: Int, offsetY: Int, background: Int): Bitmap {
        val destino = Bitmap.createBitmap(rect.width, rect.height, Bitmap.Config.ARGB_8888)
        destino.eraseColor(background)
        Canvas(destino).drawBitmap(bitmap, (rect.left - offsetX).toFloat(), (rect.top - offsetY).toFloat(), null)
        return destino
    }

    /**
     * Vacía el contenido que queda fuera de la selección, dejando la hoja con el mismo
     * tamaño. Lo que estaba dentro se queda donde estaba.
     */
    fun clearOutside(bitmap: Bitmap, selection: Selection, background: Int): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var changed = false
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (selection.contains(x, y)) continue
                if (pixels[row + x] == background) continue
                pixels[row + x] = background
                changed = true
            }
        }
        if (changed) bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return changed
    }

    /**
     * ¿[trimTransparent] cambiaría algo? No toca el bitmap.
     *
     * Va aparte porque el recorte se escribe en sitio, y el historial tiene que guardar la
     * capa ANTES de que se llame. Preguntar después ya no sirve de nada, y preguntar sin
     * mutar es lo que evita gastar un punto de deshacer en un recorte que no recorta: el
     * usuario lo pulsa y no ve que cambie nada, y cuenta como que el historial se comió un
     * paso.
     */
    fun needsTrim(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val bounds = SelectionEngine.contentBounds(pixels, width, height) ?: return false
        return bounds.left != 0 || bounds.top != 0 ||
            bounds.right != width || bounds.bottom != height
    }

    /**
     * Quita las franjas transparentes alrededor del dibujo, sin mover el contenido y sin
     * cambiar el tamaño de la capa.
     */
    fun trimTransparent(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val bounds = SelectionEngine.contentBounds(pixels, width, height) ?: return false
        if (bounds.left == 0 && bounds.top == 0 && bounds.right == width && bounds.bottom == height) {
            return false
        }
        val cleaned = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(cleaned).drawBitmap(
            bitmap,
            android.graphics.Rect(bounds.left, bounds.top, bounds.right, bounds.bottom),
            android.graphics.Rect(bounds.left, bounds.top, bounds.right, bounds.bottom),
            null
        )
        replace(bitmap, cleaned)
        return true
    }

    /**
     * Pasa el contenido de [source] a [target] conservando el objeto bitmap que la capa
     * ya tiene, porque el historial y otras referencias pueden apuntar a él.
     */
    private fun replace(target: Bitmap, source: Bitmap) {
        Canvas(target).drawBitmap(source, 0f, 0f, null)
        source.recycle()
    }
}
