package com.mc.nbpaint.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.mc.nbpaint.model.ObjectBounds
import com.mc.nbpaint.model.PaintLayer

/**
 * Composición de capas para mostrar en pantalla.
 *
 * La regla del documento es que la hoja es blanca, así que la composición arranca siempre con
 * el color de fondo. Si una capa de arriba queda con huecos, lo que se ve es el papel, no el
 * fondo oscuro de la app: por eso el borrador de las capas de arriba usa `CLEAR` y no blanco,
 * y el de la capa 0 sí usa blanco. Aquí no se decide eso, solo se compone.
 */
object Compositor {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * Dibuja una capa completa en [canvas]: lo pintado y, encima, sus objetos.
     *
     * Los objetos son la imagen puesta con su desplazamiento y su escala, el texto y la curva.
     * Se dibujan en ese orden, de abajo arriba, que es como se apilan: el texto encima de la
     * foto y la curva encima del texto. Lo que se ve en pantalla, lo que se aplana para
     * guardar y lo que sale al combinar capas son exactamente lo mismo, porque las tres cosas
     * pasan por aquí.
     *
     * Cada objeto se recorta a su propia zona antes de dibujarse. El recorte va en coordenadas
     * de la hoja, así que se recorta **antes** de trasladar y escalar: aplicado después, la
     * ventana iría escalada con la imagen y la zona elegida no sería la que se ve.
     *
     * La opacidad de la capa se aplica a las tres cosas, y a cada una con su propia pintura.
     * El texto y la curva llevan su propio color y su propia opacidad, así que bajarla es
     * multiplicar por la de la capa, no volver a taparlos. Lo que importa es que se apliquen
     * las dos: como el horneado mete el texto en el bitmap del que se pinta, un texto al que
     * se le pinta encima se dibuja después desde los píxeles y salía al 50 %, mientras que
     * sin pintar encima salía al 100 %. El mismo texto cambiaba de aspecto según lo que se
     * hubiera hecho alrededor.
     */
    fun drawLayerContent(canvas: Canvas, layer: PaintLayer, reusePaint: Paint = paint) {
        val alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
        reusePaint.alpha = alpha
        // La transformación de la capa envuelve a TODO su contenido, lo pintado, la foto y los
        // textos y curvas, y va antes de nada. Si se aplicara solo a cada trozo, el texto se
        // quedaría en el sitio viejo al mover la capa, que es justo el efecto de "las capas
        // no tienen independencia" que se quería quitar.
        //
        // Cada trozo aplica su propia transformación y cierra la suya: los píxeles, aquí, y los
        // objetos dentro de [dibujarObjetos]. Así los dos caminos que dibujan una capa, con y
        // sin completar desde abajo, ponen la capa en el mismo sitio.
        val transform = layer.transform
        if (!transform.esIdentity) {
            canvas.save()
            canvas.translate(transform.offsetX, transform.offsetY)
            canvas.scale(transform.scale, transform.scale)
        }
        canvas.drawBitmap(layer.bitmap, 0f, 0f, reusePaint)
        if (!transform.esIdentity) canvas.restore()
        dibujarObjetos(canvas, layer, alpha)
    }

    /**
     * Foto puesta, textos y curvas de una capa, ya dentro de su transformación.
     *
     * Va aparte del bitmap porque hay dos caminos que dibujan una capa y solo uno lleva los
     * píxeles: al aplanar una capa más pequeña que la hoja, los píxeles vienen de una copia
     * completada con lo de abajo y los objetos se dibujan encima.
     */
    private fun dibujarObjetos(canvas: Canvas, layer: PaintLayer, alpha: Int) {
        val transform = layer.transform
        val transformada = !transform.esIdentity
        if (transformada) {
            canvas.save()
            canvas.translate(transform.offsetX, transform.offsetY)
            canvas.scale(transform.scale, transform.scale)
        }
        layer.placed?.let { placed ->
            canvas.save()
            placed.crop?.let { recorta(canvas, it) }
            canvas.translate(placed.offsetX, placed.offsetY)
            canvas.scale(placed.scale, placed.scale)
            canvas.drawBitmap(placed.bitmap, 0f, 0f, paint)
            canvas.restore()
        }
        layer.texts.forEach { texto ->
            // El texto se mide antes de dibujarse para que su caja sea la de verdad: de ella
            // dependen la mano de mover, el marco que se ve y la posición de los tiradores.
            // Se mide con la pintura a opacidad plena: la medida no depende del alfa.
            val medida = TextRenderer.measure(texto, textPaint)
            texto.setMeasured(medida.width, medida.height)
            textPaint.alpha = alpha
            canvas.save()
            texto.crop?.let { recorta(canvas, it) }
            TextRenderer.draw(canvas, texto, textPaint)
            canvas.restore()
        }
        layer.curves.forEach { curva ->
            curvePaint.alpha = alpha
            canvas.save()
            curva.crop?.let { recorta(canvas, it) }
            CurveEngine.draw(canvas, curva, curvePaint)
            canvas.restore()
        }
        if (transformada) canvas.restore()
    }

    /**
     * Dibuja solo el bitmap de una capa, con su transformación puesta.
     *
     * Lo usa el aplanado cuando la capa tiene transformación: los textos y las curvas se
     * dibujan aparte, cada uno con su transformación, y meterlos aquí también los dibujaría
     * dos veces.
     */
    fun drawLayerBitmap(canvas: Canvas, layer: PaintLayer, reusePaint: Paint = paint) {
        reusePaint.alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
        val transform = layer.transform
        if (!transform.esIdentity) {
            canvas.save()
            canvas.translate(transform.offsetX, transform.offsetY)
            canvas.scale(transform.scale, transform.scale)
            canvas.drawBitmap(layer.bitmap, 0f, 0f, reusePaint)
            canvas.restore()
        } else {
            canvas.drawBitmap(layer.bitmap, 0f, 0f, reusePaint)
        }
    }

    /** Deja pasar solo la zona del recorte. Va en coordenadas del lienzo, sin transformar. */
    private fun recorta(canvas: Canvas, zona: ObjectBounds) {
        canvas.clipRect(zona.left, zona.top, zona.right, zona.bottom)
    }

    /**
     * Crea el bitmap de todo el documento con las capas encima, en orden de abajo hacia arriba.
     *
     * @param previewLayerId en lugar de los píxeles de esa capa se usa [previewBitmap], que es
     *   como entra la previsualización de un filtro sin tocar la capa de verdad. El id puede
     *   ser null cuando no hay filtro abierto, que es lo normal.
     */
    fun composite(
        layers: List<PaintLayer>,
        width: Int,
        height: Int,
        background: Int,
        previewLayerId: Int? = null,
        previewBitmap: Bitmap? = null,
        reusePaint: Paint = paint
    ): Bitmap {
        val composite = Bitmap.createBitmap(
            width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888
        )
        composite.eraseColor(background)
        val canvas = Canvas(composite)
        layers.forEachIndexed { indice, layer ->
            if (!layer.visible) return@forEachIndexed
            if (previewLayerId != null && previewBitmap != null && previewLayerId == layer.id) {
                reusePaint.alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(previewBitmap, 0f, 0f, reusePaint)
            } else if (layer.bitmap.width < width || layer.bitmap.height < height) {
                // Una capa más pequeña que la hoja se completa con lo que hay debajo antes de
                // dibujarse. Sin esto, el rectángulo que le falta se queda con el color del
                // papel y tapa lo que tuviera debajo: en pantalla la capa se ve bien porque
                // el lienzo también la recorta, pero al exportar aparecía un rectángulo
                // blanco que no estaba en la app.
                val completa = completaDesdeAbajo(layers, indice, width, height, background)
                // La copia se dibuja a opacidad plena porque cada capa ya se compuso con la
                // suya al meterla dentro. Volver a aplicarle aquí la de esta capa la
                // multiplicaría dos veces —y se llevaría por delante la opacidad de las de
                // abajo—, que es justo lo que se ve al bajar el canal de una capa de arriba y
                // notar que las de debajo se aclaran también.
                reusePaint.alpha = 255
                canvas.drawBitmap(completa, 0f, 0f, reusePaint)
                completa.recycle()
                // Y encima los objetos de la capa, con su transformación: la copia completada
                // solo trae los píxeles, así que sin esto la foto y el texto de una capa
                // pequeña se perdían al exportar.
                dibujarObjetos(canvas, layer, (layer.opacity * 255).toInt().coerceIn(0, 255))
            } else {
                drawLayerContent(canvas, layer, reusePaint)
            }
        }
        return composite
    }

    /**
     * Bitmap del tamaño de la hoja con lo de las capas de [hasta] menos [indice] y encima los
     * píxeles de esa capa.
     *
     * Solo se usa al aplanar, nunca en la capa de verdad: la capa sigue siendo pequeña y
     * editable, y lo de debajo se le pone solo en la copia que se exporta.
     */
    private fun completaDesdeAbajo(
        layers: List<PaintLayer>,
        indice: Int,
        width: Int,
        height: Int,
        background: Int
    ): Bitmap {
        val completa = Bitmap.createBitmap(
            width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(completa)
        canvas.drawColor(background)
        layers.take(indice).forEach { debajo ->
            if (debajo.visible) drawLayerBitmap(canvas, debajo)
        }
        drawLayerBitmap(canvas, layers[indice])
        return completa
    }
}
