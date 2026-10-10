package com.mc.nbpaint.engine

import com.mc.nbpaint.model.IntRect
import com.mc.nbpaint.model.Selection
import kotlin.math.abs

/**
 * Motor de selección: rectángulo, selección inteligente con tolerancia y cálculo de
 * regiones.
 *
 * Todo lo de aquí trabaja sobre `IntArray` de píxeles y números, sin Android. Por eso se
 * puede probar en la JVM: la parte difícil de un editor (la máscara, la tolerancia, los
 * bordes) queda cubierta por tests sin emulador.
 *
 * Los píxeles van en el mismo formato de Android: 0xAARRGGBB.
 */
object SelectionEngine {

    /** Tolerancia por defecto: ni tan estricta como para partir un degradado, ni tan laxa como para tragarse el fondo entero. */
    const val DEFAULT_TOLERANCE = 32

    /**
     * Hasta cuánto se puede subir la tolerancia.
     *
     * El tope es 255 porque es lo que mide [colorsClose]: la diferencia entre dos canales va
     * de 0 a 255, así que un número mayor ya no significaría "más laxa" sino "cualquier
     * color". Va aquí y no en la vista para que el deslizador y el recorte del ViewModel no
     * puedan quedar con rangos distintos.
     */
    const val MAX_TOLERANCE = 255

    /** Selección vacía: ninguna operación toca píxeles. */
    fun none(): Selection = Selection.Rect(IntRect.EMPTY)

    /** Todo el lienzo como selección explícita, para "seleccionar todo". */
    fun selectAll(width: Int, height: Int): Selection =
        Selection.Rect(IntRect(0, 0, width, height))

    /**
     * Marco rectangular entre dos puntos, ya sea en cualquier orden (se corrige solo) y
     * recortado a la hoja.
     */
    fun rectangle(x0: Int, y0: Int, x1: Int, y1: Int, width: Int, height: Int): Selection.Rect {
        val left = minOf(x0, x1)
        val right = maxOf(x0, x1)
        val top = minOf(y0, y1)
        val bottom = maxOf(y0, y1)
        return Selection.Rect(IntRect(left, top, right, bottom).clamped(width, height))
    }

    /**
     * Selección inteligente: la región de píxeles parecidos al de ([startX], [startY]).
     *
     * Con [contiguous] busca solo la mancha que se toca con ese punto, como la varita
     * mágica; sin él, marca todos los píxeles parecidos de la hoja, aunque estén
     * repartidos, que es lo que hace "seleccionar por color".
     *
     * @param tolerance diferencia máxima por canal (0-255). A 0 solo entra el color exacto.
     * @return la región como máscara, lista para usarla como [Selection].
     */
    fun smartSelect(
        pixels: IntArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        tolerance: Int = DEFAULT_TOLERANCE,
        contiguous: Boolean = true
    ): Selection.Mask {
        if (startX < 0 || startY < 0 || startX >= width || startY >= height) {
            return Selection.Mask(BooleanArray(width * height), width, height)
        }
        val target = pixels[startY * width + startX]
        val mask = if (contiguous) {
            contiguousMask(pixels, width, height, startX, startY, target, tolerance)
        } else {
            BooleanArray(width * height).also { marcados ->
                for (i in pixels.indices) {
                    if (colorsClose(pixels[i], target, tolerance)) marcados[i] = true
                }
            }
        }
        return Selection.Mask(mask, width, height)
    }

    /**
     * Máscara de la mancha conectada al punto ([startX], [startY]) entre los píxeles
     * parecidos al color [target], con [tolerance] por canal.
     *
     * Lo usan la selección inteligente, la varita mágica y el bote de pintura, para que el
     * barrido por líneas viva en un solo sitio.
     */
    fun contiguousMask(
        pixels: IntArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        target: Int,
        tolerance: Int
    ): BooleanArray {
        val mask = BooleanArray(width * height)
        selectContiguous(pixels, width, height, startX, startY, target, tolerance, mask)
        return mask
    }

    /**
     * Barrido por líneas de la mancha conectada. Se recorre horizontalmente y se apilan las
     * líneas vecinas, sin recursión: con imágenes grandes una versión recursiva revienta
     * la pila.
     */
    private fun selectContiguous(
        pixels: IntArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        target: Int,
        tolerance: Int,
        mask: BooleanArray
    ) {
        val stack = ArrayDeque<Int>()
        stack.addLast(startY * width + startX)

        while (stack.isNotEmpty()) {
            val idx = stack.removeLast()
            if (mask[idx]) continue

            var x = idx % width
            val y = idx / width

            // Hacia la izquierda mientras siga siendo del mismo color.
            while (x > 0 && !mask[y * width + (x - 1)] &&
                colorsClose(pixels[y * width + (x - 1)], target, tolerance)
            ) {
                x--
            }

            var spanAbove = false
            var spanBelow = false

            // La línea actual, hacia la derecha, apilando las líneas de arriba y abajo.
            while (x < width && !mask[y * width + x] &&
                colorsClose(pixels[y * width + x], target, tolerance)
            ) {
                val cur = y * width + x
                mask[cur] = true

                if (y > 0) {
                    val above = (y - 1) * width + x
                    val matches = !mask[above] && colorsClose(pixels[above], target, tolerance)
                    if (!spanAbove && matches) {
                        stack.addLast(above)
                        spanAbove = true
                    } else if (spanAbove && !matches) {
                        spanAbove = false
                    }
                }

                if (y < height - 1) {
                    val below = (y + 1) * width + x
                    val matches = !mask[below] && colorsClose(pixels[below], target, tolerance)
                    if (!spanBelow && matches) {
                        stack.addLast(below)
                        spanBelow = true
                    } else if (spanBelow && !matches) {
                        spanBelow = false
                    }
                }

                x++
            }
        }
    }

    /**
     * Une dos selecciones. Sirve para ir sumando zonas con la selección inteligente sin
     * perder lo que ya estaba elegido.
     *
     * Si una de las dos es "todo", el resultado es "todo": no tiene sentido restricting
     * más una hoja entera.
     *
     * El tamaño de la hoja va explícito, y el resultado sale SIEMPRE de ese tamaño, no del
     * de las entradas. Antes se deducía de las máscaras, y para dos marcos se sacaba de su
     * esquina inferior derecha: dos rectángulos lejos del origen (0,0) producían una máscara
     * más pequeña que la hoja, y todo lo que caía fuera de su caja contaba como no
     * seleccionado. Con eso, un recorte a esa selección vaciaba de más las franjas
     * inferiores y derechas de la capa.
     */
    fun union(a: Selection, b: Selection, sheetWidth: Int, sheetHeight: Int): Selection {
        if (a is Selection.All) return a
        if (b is Selection.All) return b
        val width = maxOf(sheetWidth, 1)
        val height = maxOf(sheetHeight, 1)
        val mask = BooleanArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            if (a.contains(x, y) || b.contains(x, y)) mask[y * width + x] = true
        }
        return Selection.Mask(mask, width, height)
    }

    /**
     * Da la vuelta a un marco dentro de la hoja: el rectángulo elegido se queda fuera y el
     * resto pasa a estar dentro. Es lo mismo que "seleccionar lo que no está elegido".
     */
    fun invertRect(rect: IntRect, sheetWidth: Int, sheetHeight: Int): Selection.Mask {
        val width = maxOf(sheetWidth, 1)
        val height = maxOf(sheetHeight, 1)
        val mask = BooleanArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            mask[y * width + x] = !rect.contains(x, y)
        }
        return Selection.Mask(mask, width, height)
    }

    /**
     * Deja la selección como única parte modificada: lo que quede fuera vuelve a su valor
     * anterior.
     *
     * Es lo que hace que el relleno y la varita mágica respeten la selección sin reescribir
     * esos motores: se rellena como siempre y después se deshace el desbordamiento. Cambia
     * [current] en el sitio y devuelve cuántos píxeles devolvió a su sitio.
     *
     * El tamaño de la hoja va explícito porque con un marco no se puede deducir: el
     * rectángulo puede acabar muy lejos del borde y aun así lo de detrás también es de la
     * hoja y hay que devolverlo.
     */
    fun restoreOutside(
        current: IntArray,
        before: IntArray,
        selection: Selection,
        sheetWidth: Int,
        sheetHeight: Int
    ): Int {
        if (selection is Selection.All) return 0
        var restored = 0
        for (y in 0 until sheetHeight) {
            val row = y * sheetWidth
            for (x in 0 until sheetWidth) {
                if (selection.contains(x, y)) continue
                val i = row + x
                if (i >= current.size || i >= before.size) continue
                if (current[i] == before[i]) continue
                current[i] = before[i]
                restored++
            }
        }
        return restored
    }

    /** ¿Dos píxeles están dentro de la tolerancia, canal por canal? A tolerancia 0, igualdad exacta. */
    fun colorsClose(a: Int, b: Int, tolerance: Int): Boolean {
        if (tolerance <= 0) return a == b
        return abs(alphaOf(a) - alphaOf(b)) <= tolerance &&
            abs(redOf(a) - redOf(b)) <= tolerance &&
            abs(greenOf(a) - greenOf(b)) <= tolerance &&
            abs(blueOf(a) - blueOf(b)) <= tolerance
    }

    fun alphaOf(color: Int): Int = (color ushr 24) and 0xFF
    fun redOf(color: Int): Int = (color ushr 16) and 0xFF
    fun greenOf(color: Int): Int = (color ushr 8) and 0xFF
    fun blueOf(color: Int): Int = color and 0xFF

    /**
     * Promedia los píxeles que tocan la selección desde afuera. Ese vecindario es, por
     * definición, el "fondo" alrededor del objeto, así que pintar con ese promedio hace que
     * el objeto desaparezca en vez de quedar tapado con un color plano.
     */
    fun surroundingColor(pixels: IntArray, selection: Selection, width: Int, height: Int): Int? {
        var r = 0L
        var g = 0L
        var b = 0L
        var a = 0L
        var n = 0

        fun accumulate(x: Int, y: Int) {
            if (x < 0 || y < 0 || x >= width || y >= height) return
            if (selection.contains(x, y)) return
            val p = pixels[y * width + x]
            a += alphaOf(p)
            r += redOf(p)
            g += greenOf(p)
            b += blueOf(p)
            n++
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!selection.contains(x, y)) continue
                accumulate(x - 1, y)
                accumulate(x + 1, y)
                accumulate(x, y - 1)
                accumulate(x, y + 1)
            }
        }

        if (n == 0) return null
        return argb(
            (a / n).toInt().coerceIn(0, 255),
            (r / n).toInt().coerceIn(0, 255),
            (g / n).toInt().coerceIn(0, 255),
            (b / n).toInt().coerceIn(0, 255)
        )
    }

    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a and 0xFF shl 24) or (r and 0xFF shl 16) or (g and 0xFF shl 8) or (b and 0xFF)

    /**
     * Rectángulo que abarca lo dibujado en la capa, o null si está vacía.
     *
     * Se lee por franjas porque `getPixel` en un bucle simple es lentísimo en hojas
     * grandes. [minAlpha] descarta los píxeles casi transparentes para no contar como
     * contenido el ruido de una capa recién creada.
     */
    fun contentBounds(
        pixels: IntArray,
        width: Int,
        height: Int,
        minAlpha: Int = 1
    ): IntRect? {
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (alphaOf(pixels[row + x]) < minAlpha) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return null
        return IntRect(minX, minY, maxX + 1, maxY + 1)
    }
}
