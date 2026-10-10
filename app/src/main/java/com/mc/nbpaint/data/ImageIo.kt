package com.mc.nbpaint.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.mc.nbpaint.model.LayerSnapshot
import com.mc.nbpaint.model.PlacedSnapshot
import com.mc.nbpaint.model.editableCopy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** En qué formato se manda una imagen a la galería. */
enum class ExportFormat { PNG, JPEG }

/**
 * Leer imágenes de otros sitios y escribirlas en la galería.
 *
 * Vive aparte de la pantalla y del ViewModel porque es lo único que habla con la galería de
 * Android y con los `Uri`. Lo que hay adentro es sobre todo un cuidado: las fotos llegan
 * enormes y hay que reducirlas antes de meterlas en memoria, y una foto que entra estirada o
 * inmutable rompe el dibujo para siempre, porque ya no se puede modificar.
 */
object ImageIo {

    /**
     * Abre la imagen para que ella misma sea el tamaño de la hoja, sin estirarla.
     *
     * Se recorta por [maxEdge] para que una foto enorme no vuelva la hoja imposible de
     * dibujar, y se devuelve una copia mutable: `BitmapFactory` devuelve inmutables, y una
     * capa con un bitmap inmutable no se puede modificar, así que el pincel, el clonado y los
     * filtros dejarían de hacer nada.
     */
    fun decodeForSheet(context: Context, uri: Uri, maxEdge: Int = StorageLayout.MAX_DOCUMENT_EDGE): Bitmap? {
        val resolver = context.contentResolver

        // Primera pasada: solo las medidas, sin cargar píxeles. Es la que permite decidir
        // cuánto reducir antes de gastar la memoria.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val longestEdge = max(bounds.outWidth, bounds.outHeight)
        val target = min(maxEdge, longestEdge)

        // Se va bajando de a saltos de dos hasta quedar por debajo del objetivo, que es lo
        // que hace `inSampleSize`: Android divide la imagen entre 2, 4, 8...
        var sample = 1
        while (longestEdge / (sample * 2) >= target) sample *= 2

        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        } ?: return null

        val scale = min(1f, maxEdge.toFloat() / max(decoded.width, decoded.height))
        if (scale >= 1f) return decoded.editableCopy()

        val w = (decoded.width * scale).roundToInt().coerceAtLeast(1)
        val h = (decoded.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(scaled).drawBitmap(decoded, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), null)
        decoded.recycle()
        return scaled
    }

    /**
     * Manda el dibujo a la galería del teléfono.
     *
     * En JPEG se aplana antes sobre blanco porque el formato no tiene canal alfa: sin esto
     * las zonas transparentes se guardan en negro.
     */
    fun writeToGallery(context: Context, bitmap: Bitmap, format: ExportFormat = ExportFormat.PNG): Boolean {
        val extension = if (format == ExportFormat.PNG) "png" else "jpg"
        val mimeType = if (format == ExportFormat.PNG) "image/png" else "image/jpeg"
        val compressFormat =
            if (format == ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val filename = "NB-paint_${timestamp()}.$extension"
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NB-paint")
                // IS_PENDING evita que la galería muestre una imagen a medio escribir.
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, values) ?: return false

        val bitmapToWrite = if (format == ExportFormat.JPEG) {
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { flat ->
                Canvas(flat).apply {
                    drawColor(AndroidColor.WHITE)
                    drawBitmap(bitmap, 0f, 0f, null)
                }
            }
        } else {
            bitmap
        }

        resolver.openOutputStream(uri)?.use { out -> bitmapToWrite.compress(compressFormat, 92, out) }
            ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return true
    }

    /** Nombre con fecha y hora, para que los archivos salgan en orden en la galería. */
    fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}
