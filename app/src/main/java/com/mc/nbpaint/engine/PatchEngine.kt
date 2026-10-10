package com.mc.nbpaint.engine

import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * El parche: tapa lo que hay abajo con lo que hay un poco más arriba, sin configurar nada.
 *
 * Todo el cálculo va sobre arreglos de píxeles, sin `Bitmap` ni `Canvas`, así que se puede
 * probar en la JVM exactamente en los casos que fallan: la franja de arriba del todo, donde
 * no hay nada sano que copiar, y los bordes, donde la ventana se sale de la hoja.
 *
 * La idea es que el resultado "coincida con el contexto": se copia de arriba, se suaviza un
 * poco para que no se note el empaste y se acerca al tono que ya había, con el borde
 * desvaneciéndose para no dejar un aro duro.
 */
object PatchEngine {

    /** A qué distancia se toma la muestra, medido en radios del pincel. */
    const val SAMPLE_DISTANCE_IN_RADII = 1.6f

    /** Cuánto se acerca el contenido copiado al tono que ya había abajo. */
    private const val TONE_MATCH = 0.35f

    /** A partir de esta distancia del centro la mezcla ya es total. */
    private const val SOLID_FROM = 0.4f

    /** El cuadrado que cubre el pincel: izquierda, arriba, ancho y alto. Null si no cabe nada. */
    data class Window(val left: Int, val top: Int, val width: Int, val height: Int) {
        val right: Int get() = left + width - 1
        val bottom: Int get() = top + height - 1
    }

    /**
     * El cuadrado que cubre el pincel alrededor de (cx, cy), recortado a la hoja.
     *
     * Es lo primero que tiene que estar bien: si el cuadrado se sale, `getPixels` copia de
     * otro lado y el parche termina curando la zona equivocada. Cerca del borde la ventana
     * se achica en vez de salirse.
     */
    fun windowAt(cx: Int, cy: Int, radius: Int, sheetWidth: Int, sheetHeight: Int): Window? {
        if (sheetWidth <= 0 || sheetHeight <= 0) return null
        val r = radius.coerceAtLeast(1)
        val left = (cx - r).coerceIn(0, sheetWidth - 1)
        val top = (cy - r).coerceIn(0, sheetHeight - 1)
        val right = (cx + r).coerceIn(0, sheetWidth - 1)
        val bottom = (cy + r).coerceIn(0, sheetHeight - 1)
        val w = right - left + 1
        val h = bottom - top + 1
        if (w <= 0 || h <= 0) return null
        return Window(left, top, w, h)
    }

    /**
     * A qué fila de la hoja se mira para copiar, dado el punto tocado.
     *
     * Cerca del borde superior no hay nada sano arriba, así que quien llama la baja a un
     * sitio que sí esté dentro: antes se rellenaba con transparente y el parche terminaba
     * borrando la franja en vez de curarla.
     */
    fun sampleRow(
        centerY: Int,
        radius: Float,
        windowHeight: Int,
        sheetHeight: Int,
        offsetInRadii: Float = SAMPLE_DISTANCE_IN_RADII
    ): Int {
        val deseada = centerY - (radius * offsetInRadii).roundToInt()
        return deseada.coerceIn(0, (sheetHeight - windowHeight).coerceAtLeast(0))
    }

    /**
     * Calcula el trozo de píxeles ya parcheado.
     *
     * @param actual lo que hay ahora en la ventana, en fila mayor.
     * @param donado lo que hay en la ventana de arriba, del mismo tamaño.
     * @param centerX,centerY dónde se tocó, en píxeles de la hoja (no de la ventana).
     */
    fun patchWindow(
        actual: IntArray,
        donado: IntArray,
        window: Window,
        centerX: Float,
        centerY: Float,
        radius: Float
    ): IntArray {
        if (radius < 1f) return actual
        val w = window.width
        val h = window.height
        // Se difumina un poco la muestra para que no se note el empaste.
        val difuminado = IntArray(w * h)
        blurBox(donado, difuminado, w, h)
        // Tono promedio de lo que ya hay: el parche se acerca a ese color para que la
        // transición no se corte. Es lo que hace que "coincida con el contexto".
        val (avgR, avgG, avgB) = averageColor(actual)

        val salida = IntArray(w * h)
        for (y in 0 until h) {
            val py = window.top + y
            for (x in 0 until w) {
                val i = y * w + x
                val px = window.left + x
                val muestra = difuminado[i]
                // Una muestra transparente no aporta nada: mejor dejar el píxel como estaba
                // que borrarlo con el sitio de arriba que no tiene nada.
                if (alphaOf(muestra) == 0) {
                    salida[i] = actual[i]
                    continue
                }
                val dist = hypot((px - centerX).toDouble(), (py - centerY).toDouble()).toFloat()
                // Centro fuerte, borde que se desvanece: sin aro duro alrededor.
                val t = 1f - (dist / radius)
                val fuerza = when {
                    t <= 0f -> 0f
                    t >= SOLID_FROM -> 1f
                    else -> t / SOLID_FROM
                }
                if (fuerza <= 0f) {
                    salida[i] = actual[i]
                    continue
                }
                val ajustado = packColor(
                    lerp(redOf(muestra).toFloat(), avgR, TONE_MATCH),
                    lerp(greenOf(muestra).toFloat(), avgG, TONE_MATCH),
                    lerp(blueOf(muestra).toFloat(), avgB, TONE_MATCH),
                    alphaOf(muestra)
                )
                salida[i] = mix(actual[i], ajustado, fuerza)
            }
        }
        return salida
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun packColor(r: Float, g: Float, b: Float, a: Int): Int =
        (a.coerceIn(0, 255) shl 24) or
            (r.toInt().coerceIn(0, 255) shl 16) or
            (g.toInt().coerceIn(0, 255) shl 8) or
            b.toInt().coerceIn(0, 255)

    private fun mix(base: Int, top: Int, amount: Float): Int {
        val inv = 1f - amount
        val a = ((base ushr 24 and 0xFF) * inv + (top ushr 24 and 0xFF) * amount)
        val r = ((base ushr 16 and 0xFF) * inv + (top ushr 16 and 0xFF) * amount)
        val g = ((base ushr 8 and 0xFF) * inv + (top ushr 8 and 0xFF) * amount)
        val b = ((base and 0xFF) * inv + (top and 0xFF) * amount)
        return packColor(r, g, b, a.toInt())
    }

    /** Tono promedio de lo visible; los píxeles transparentes no cuentan. */
    fun averageColor(pixels: IntArray): Triple<Float, Float, Float> {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (p in pixels) {
            if (alphaOf(p) == 0) continue
            r += p ushr 16 and 0xFF
            g += p ushr 8 and 0xFF
            b += p and 0xFF
            n++
        }
        if (n == 0) return Triple(0f, 0f, 0f)
        return Triple(r.toFloat() / n, g.toFloat() / n, b.toFloat() / n)
    }

    /** Caja 3x3 separable. Sale apenas suavizada, que es lo que busca el parche. */
    fun blurBox(src: IntArray, dst: IntArray, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val tmp = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0; var a = 0; var n = 0
                for (d in -1..1) {
                    val c = src[y * w + (x + d).coerceIn(0, w - 1)]
                    r += c ushr 16 and 0xFF
                    g += c ushr 8 and 0xFF
                    b += c and 0xFF
                    a += c ushr 24 and 0xFF
                    n++
                }
                tmp[y * w + x] = (a / n shl 24) or (r / n shl 16) or (g / n shl 8) or (b / n)
            }
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0; var a = 0; var n = 0
                for (d in -1..1) {
                    val c = tmp[(y + d).coerceIn(0, h - 1) * w + x]
                    r += c ushr 16 and 0xFF
                    g += c ushr 8 and 0xFF
                    b += c and 0xFF
                    a += c ushr 24 and 0xFF
                    n++
                }
                dst[y * w + x] = (a / n shl 24) or (r / n shl 16) or (g / n shl 8) or (b / n)
            }
        }
    }

    // Los canales se leen con aritmética de bits y no con `android.graphics.Color` a
    // propósito: así este motor es Java puro y se puede probar en la JVM, que es lo único
    // que permite cubrir los bordes sin dibujar a mano.
    private fun alphaOf(color: Int): Int = color ushr 24 and 0xFF

    private fun redOf(color: Int): Int = color ushr 16 and 0xFF

    private fun greenOf(color: Int): Int = color ushr 8 and 0xFF

    private fun blueOf(color: Int): Int = color and 0xFF
}