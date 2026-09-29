package com.mc.nbpaint.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.roundToInt

/**
 * Filtros de imagen aplicables a una capa.
 *
 * Los ajustes de color se resuelven con [ColorMatrix] y un [ColorMatrixColorFilter]: es una
 * operación con una sola pasada de la GPU/CPU y no toca píxeles uno por uno. Solo el
 * desenfoque y la nitidez, que son convoluciones, trabajan sobre el array de píxeles.
 */
enum class ImageFilter(val label: String) {
    BRIGHTNESS("Brillo"),
    CONTRAST("Contraste"),
    SATURATION("Saturación"),
    TEMPERATURE("Temperatura"),
    SHARPNESS("Nitidez"),
    BLUR("Desenfoque"),
    GRAYSCALE("Blanco y negro"),
    SEPIA("Sepia"),
    INVERT("Invertir");

    /** Los filtros sin cantidad no dependen del deslizador. */
    val hasAmount: Boolean
        get() = this != GRAYSCALE && this != SEPIA && this != INVERT

    companion object {
        /**
         * Copia reducida de [bitmap] con el filtro ya aplicado, para mostrar la vista previa
         * sin pagar el costo de un filtro a resolución completa en cada movimiento del
         * deslizador.
         */
        fun preview(bitmap: Bitmap, filter: ImageFilter, amount: Float, maxSize: Int = 320): Bitmap {
            val scale = minOf(
                1f,
                maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
            )
            val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
            val result = small.copy(Bitmap.Config.ARGB_8888, true)
            if (small !== result) small.recycle()
            apply(result, filter, amount)
            return result
        }

        /** Aplica [filter] sobre [bitmap] modifying sus píxeles en el lugar. */
        fun apply(bitmap: Bitmap, filter: ImageFilter, amount: Float) {
            when (filter) {
                ImageFilter.BLUR -> blur(bitmap, amount)
                ImageFilter.SHARPNESS -> sharpen(bitmap, amount)
                else -> colorMatrix(bitmap, filter, amount)
            }
        }

        private fun colorMatrix(bitmap: Bitmap, filter: ImageFilter, amount: Float) {
            val level = amount.coerceIn(-1f, 1f)

            // Toda matriz se arma con los 20 valores en orden de columna, y las correcciones
            // se componen con postConcat. Es la forma más explícita de dejar claro qué hace
            // cada filtro.
            val matrix = when (filter) {
                ImageFilter.BRIGHTNESS -> {
                    // El término de traslación mueve el rango 0..255.
                    val shift = level * 100f
                    translation(shift, shift, shift)
                }
                ImageFilter.CONTRAST -> {
                    val c = 1f + level
                    val shift = (1f - c) * 128f
                    scaleThenTranslate(c, c, c, shift, shift, shift)
                }
                ImageFilter.SATURATION -> ColorMatrix().apply { setSaturation(1f + level) }
                ImageFilter.TEMPERATURE -> {
                    // Más rojo y menos azul si la cantidad es positiva; al revés si es negativa.
                    val r = 1f + level * 0.35f
                    val b = 1f - level * 0.35f
                    scaleThenTranslate(r, 1f, b, 0f, 0f, 0f)
                }
                ImageFilter.GRAYSCALE -> ColorMatrix().apply { setSaturation(0f) }
                ImageFilter.SEPIA -> ColorMatrix().apply {
                    setSaturation(0f)
                    postConcat(
                        ColorMatrix(
                            floatArrayOf(
                                1.07f, 0f, 0f, 0f, 0f,
                                0f, 0.95f, 0f, 0f, 0f,
                                0f, 0f, 0.82f, 0f, 0f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    )
                }
                ImageFilter.INVERT -> ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                else -> return
            }

            val source = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            }
            canvas.drawBitmap(source, 0f, 0f, paint)
            source.recycle()
        }

        /** Matriz identidad con traslación: mueve el brillo sin tocar la saturación. */
        private fun translation(x: Float, y: Float, z: Float) = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, x,
                0f, 1f, 0f, 0f, y,
                0f, 0f, 1f, 0f, z,
                0f, 0f, 0f, 1f, 0f
            )
        )

        /** Escala cada canal y después desplaza, que es como se define el contraste. */
        private fun scaleThenTranslate(
            rs: Float,
            gs: Float,
            bs: Float,
            tx: Float,
            ty: Float,
            tz: Float
        ) = ColorMatrix(
            floatArrayOf(
                rs, 0f, 0f, 0f, tx,
                0f, gs, 0f, 0f, ty,
                0f, 0f, bs, 0f, tz,
                0f, 0f, 0f, 1f, 0f
            )
        )

        /** Desenfoque de caja separable, dos o tres pasadas para acercarse a una gaussiana. */
        private fun blur(bitmap: Bitmap, amount: Float) {
            val radius = (amount.coerceIn(0f, 1f) * 6f).roundToInt()
            if (radius < 1) return

            val width = bitmap.width
            val height = bitmap.height
            var src = IntArray(width * height)
            var dst = IntArray(width * height)
            bitmap.getPixels(src, 0, width, 0, 0, width, height)

            repeat(3) {
                boxBlurH(src, dst, width, height, radius)
                boxBlurV(dst, src, width, height, radius)
            }
            bitmap.setPixels(src, 0, width, 0, 0, width, height)
        }

        private fun boxBlurH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
            val div = r * 2 + 1
            for (y in 0 until h) {
                var a = 0L
                var rr = 0L
                var g = 0L
                var b = 0L
                for (i in -r..r) {
                    val p = src[y * w + i.coerceIn(0, w - 1)]
                    a += (p ushr 24) and 0xFF
                    rr += (p ushr 16) and 0xFF
                    g += (p ushr 8) and 0xFF
                    b += p and 0xFF
                }
                for (x in 0 until w) {
                    dst[y * w + x] =
                        ((a / div).toInt() shl 24) or ((rr / div).toInt() shl 16) or
                            ((g / div).toInt() shl 8) or (b / div).toInt()
                    val outP = src[y * w + (x - r).coerceIn(0, w - 1)]
                    val inP = src[y * w + (x + r + 1).coerceIn(0, w - 1)]
                    a += ((inP ushr 24) and 0xFF) - ((outP ushr 24) and 0xFF)
                    rr += ((inP ushr 16) and 0xFF) - ((outP ushr 16) and 0xFF)
                    g += ((inP ushr 8) and 0xFF) - ((outP ushr 8) and 0xFF)
                    b += (inP and 0xFF) - (outP and 0xFF)
                }
            }
        }

        private fun boxBlurV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
            val div = r * 2 + 1
            for (x in 0 until w) {
                var a = 0L
                var rr = 0L
                var g = 0L
                var b = 0L
                for (i in -r..r) {
                    val p = src[i.coerceIn(0, h - 1) * w + x]
                    a += (p ushr 24) and 0xFF
                    rr += (p ushr 16) and 0xFF
                    g += (p ushr 8) and 0xFF
                    b += p and 0xFF
                }
                for (y in 0 until h) {
                    dst[y * w + x] =
                        ((a / div).toInt() shl 24) or ((rr / div).toInt() shl 16) or
                            ((g / div).toInt() shl 8) or (b / div).toInt()
                    val outP = src[(y - r).coerceIn(0, h - 1) * w + x]
                    val inP = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                    a += ((inP ushr 24) and 0xFF) - ((outP ushr 24) and 0xFF)
                    rr += ((inP ushr 16) and 0xFF) - ((outP ushr 16) and 0xFF)
                    g += ((inP ushr 8) and 0xFF) - ((outP ushr 8) and 0xFF)
                    b += (inP and 0xFF) - (outP and 0xFF)
                }
            }
        }

        /** Convolución 3x3 de nitidez; `amount` 0 no hace nada, 1 deja la imagen "cruda". */
        private fun sharpen(bitmap: Bitmap, amount: Float) {
            val k = amount.coerceIn(0f, 1f)
            if (k < 0.01f) return

            val width = bitmap.width
            val height = bitmap.height
            val src = IntArray(width * height)
            bitmap.getPixels(src, 0, width, 0, 0, width, height)
            val dst = IntArray(width * height)

            val center = 1f + 4f * k
            val side = -k

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val i = y * width + x
                    if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                        dst[i] = src[i]
                        continue
                    }
                    var r = 0f
                    var g = 0f
                    var b = 0f
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val p = src[(y + dy) * width + (x + dx)]
                            val wgt = if (dx == 0 && dy == 0) center else side
                            r += wgt * ((p ushr 16) and 0xFF)
                            g += wgt * ((p ushr 8) and 0xFF)
                            b += wgt * (p and 0xFF)
                        }
                    }
                    val alpha = (src[i] ushr 24) and 0xFF
                    dst[i] = (alpha shl 24) or
                        (r.roundToInt().coerceIn(0, 255) shl 16) or
                        (g.roundToInt().coerceIn(0, 255) shl 8) or
                        b.roundToInt().coerceIn(0, 255)
                }
            }
            bitmap.setPixels(dst, 0, width, 0, 0, width, height)
        }
    }
}
