package com.mc.nbpaint.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.jvm.JvmName
import kotlin.math.roundToInt
import kotlin.random.Random

private const val MAX_HISTORY = 20
private const val CANVAS_BACKGROUND = AndroidColor.WHITE
private const val PROJECT_DIR_NAME = "nbpaint_project"
private const val PROJECT_MANIFEST = "manifest.txt"

enum class ExportFormat { PNG, JPEG }

/** Una capa del lienzo: bitmap propio, visibilidad y opacidad. `id` es estable entre operaciones. */
class PaintLayer(
    val id: Int,
    var bitmap: Bitmap,
    var name: String,
    visible: Boolean = true,
    opacity: Float = 1f
) {
    var visible by mutableStateOf(visible)
    var opacity by mutableFloatStateOf(opacity)
}

private data class LayerSnapshot(val bitmap: Bitmap, val name: String, val visible: Boolean, val opacity: Float)

/** Historial unificado: ediciones de píxeles, cambios de estructura de capas y combinar (merge). */
private sealed interface HistoryEntry {
    data class PixelEdit(val layerId: Int, val bitmap: Bitmap) : HistoryEntry
    data class StructuralEdit(val layers: List<PaintLayer>, val activeIndex: Int) : HistoryEntry
    data class MergeEdit(
        val topLayer: PaintLayer,
        val insertIndex: Int,
        val bottomLayerId: Int,
        val bottomBitmapBefore: Bitmap,
        val bottomBitmapAfter: Bitmap,
        val activeIndexBefore: Int,
        val activeIndexAfter: Int,
        val forward: Boolean
    ) : HistoryEntry
}

/**
 * Motor de dibujo de NB-paint, con capas y un historial de deshacer/rehacer
 * unificado que cubre tanto trazos/formas/relleno como agregar, quitar,
 * reordenar y combinar capas.
 */
class DrawingViewModel : ViewModel() {

    // ---- Estado observable para la UI ---------------------------------

    var currentTool by mutableStateOf(PaintTool.BRUSH)
        private set

    var currentColor by mutableIntStateOf(AndroidColor.BLACK)
        private set

    var strokeWidth by mutableFloatStateOf(16f)
        private set

    /** Si las figuras (rectángulo/círculo) se dibujan rellenas o solo con contorno. */
    var shapeFilled by mutableStateOf(false)
        private set

    var revision by mutableIntStateOf(0)
        private set

    var canUndo by mutableStateOf(false)
        private set

    var canRedo by mutableStateOf(false)
        private set

    var shapeStart by mutableStateOf<Offset?>(null)
        private set

    var shapeCurrent by mutableStateOf<Offset?>(null)
        private set

    /** Qué tan distinta puede ser la región para que la varita mágica la selected. */
    var smartFillTolerance by mutableFloatStateOf(32f)
        private set

    var smartFillMode by mutableStateOf(SmartFillMode.COLOR)

    /** Punto donde se.insertará el texto; la UI lo escucha para abrir el diálogo. */
    var textAnchor by mutableStateOf<Offset?>(null)
        private set

    fun setSmartFillTolerance(value: Int) {
        smartFillTolerance = value.coerceIn(0, 128).toFloat()
    }

    fun dismissTextAnchor() {
        textAnchor = null
    }

    val layers = mutableStateListOf<PaintLayer>()

    var activeLayerIndex by mutableIntStateOf(0)
        private set

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    // ---- Motor interno ---------------------------------------------------

    private var canvasWidth = 0
    private var canvasHeight = 0
    private var compositeBitmap: Bitmap? = null

    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()

    private var lastPoint: Offset? = null
    private var idCounter = 0

    private val strokePaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    /**
     * El borrador borra de verdad: pone los píxeles en transparente con CLEAR, en vez de
     * pintar el color de fondo. Así se pueden quitar partes de una imagen importada y queda
     * al descubierto lo que haya debajo.
     */
    private val eraserPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val shapePaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val layerCompositePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---- Ciclo de vida del lienzo -----------------------------------------

    fun ensureCanvas(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (canvasWidth == width && canvasHeight == height && layers.isNotEmpty()) return

        if (layers.isEmpty()) {
            canvasWidth = width
            canvasHeight = height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawColor(CANVAS_BACKGROUND)
            layers.add(PaintLayer(nextId(), bmp, nextLayerName()))
            activeLayerIndex = 0
        } else {
            val src = android.graphics.Rect(0, 0, canvasWidth, canvasHeight)
            val dst = android.graphics.Rect(0, 0, width, height)
            layers.forEach { layer ->
                val resized = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                Canvas(resized).drawBitmap(layer.bitmap, src, dst, null)
                layer.bitmap = resized
            }
            canvasWidth = width
            canvasHeight = height
        }
        compositeBitmap = null
        revision++
    }

    private fun nextId(): Int = idCounter++
    private var layerCounter = 0
    private fun nextLayerName(): String {
        layerCounter++
        return "Capa $layerCounter"
    }

    private fun activeLayer(): PaintLayer? = layers.getOrNull(activeLayerIndex)
    private fun activeCanvas(): Canvas? = activeLayer()?.let { Canvas(it.bitmap) }

    // ---- Selección de herramienta y estilo --------------------------------

    fun selectTool(tool: PaintTool) {
        currentTool = tool
        shapeStart = null
        shapeCurrent = null
    }

    fun selectColor(colorArgb: Int) {
        currentColor = colorArgb
    }

    // El nombre JVM difiere porque `strokeWidth` genera su propio setStrokeWidth(F)V.
    @JvmName("applyStrokeWidth")
    fun setStrokeWidth(width: Float) {
        strokeWidth = width.coerceIn(2f, 60f)
    }

    fun toggleShapeFilled() {
        shapeFilled = !shapeFilled
    }

    // ---- Historial (undo/redo) --------------------------------------------

    private fun pushHistory(entry: HistoryEntry) {
        undoStack.addLast(entry)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        canUndo = true
        canRedo = false
    }

    private fun pushPixelUndo() {
        val layer = activeLayer() ?: return
        pushHistory(HistoryEntry.PixelEdit(layer.id, layer.bitmap.copy(Bitmap.Config.ARGB_8888, false)))
    }

    private fun pushStructuralUndo() {
        pushHistory(HistoryEntry.StructuralEdit(layers.toList(), activeLayerIndex))
    }

    fun undo() {
        val entry = undoStack.removeLastOrNull() ?: return
        when (entry) {
            is HistoryEntry.PixelEdit -> {
                val layer = layers.find { it.id == entry.layerId } ?: return
                redoStack.addLast(HistoryEntry.PixelEdit(entry.layerId, layer.bitmap.copy(Bitmap.Config.ARGB_8888, false)))
                layer.bitmap = entry.bitmap
                activeLayerIndex = layers.indexOf(layer).coerceAtLeast(0)
            }
            is HistoryEntry.StructuralEdit -> {
                redoStack.addLast(HistoryEntry.StructuralEdit(layers.toList(), activeLayerIndex))
                layers.clear()
                layers.addAll(entry.layers)
                activeLayerIndex = entry.activeIndex.coerceIn(0, layers.lastIndex)
            }
            is HistoryEntry.MergeEdit -> {
                val bottom = layers.find { it.id == entry.bottomLayerId } ?: return
                bottom.bitmap = entry.bottomBitmapBefore
                layers.add(entry.insertIndex, entry.topLayer)
                activeLayerIndex = entry.activeIndexBefore
                redoStack.addLast(entry.copy(forward = true))
            }
        }
        capRedoHistory()
        canUndo = undoStack.isNotEmpty()
        canRedo = true
        compositeBitmap = null
        revision++
    }

    fun redo() {
        val entry = redoStack.removeLastOrNull() ?: return
        when (entry) {
            is HistoryEntry.PixelEdit -> {
                val layer = layers.find { it.id == entry.layerId } ?: return
                undoStack.addLast(HistoryEntry.PixelEdit(entry.layerId, layer.bitmap.copy(Bitmap.Config.ARGB_8888, false)))
                layer.bitmap = entry.bitmap
                activeLayerIndex = layers.indexOf(layer).coerceAtLeast(0)
            }
            is HistoryEntry.StructuralEdit -> {
                undoStack.addLast(HistoryEntry.StructuralEdit(layers.toList(), activeLayerIndex))
                layers.clear()
                layers.addAll(entry.layers)
                activeLayerIndex = entry.activeIndex.coerceIn(0, layers.lastIndex)
            }
            is HistoryEntry.MergeEdit -> {
                val bottom = layers.find { it.id == entry.bottomLayerId } ?: return
                bottom.bitmap = entry.bottomBitmapAfter
                val idx = layers.indexOfFirst { it.id == entry.topLayer.id }
                if (idx >= 0) layers.removeAt(idx)
                activeLayerIndex = entry.activeIndexAfter
                undoStack.addLast(entry.copy(forward = false))
            }
        }
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        canUndo = true
        canRedo = redoStack.isNotEmpty()
        compositeBitmap = null
        revision++
    }

    private fun capRedoHistory() {
        if (redoStack.size > MAX_HISTORY) redoStack.removeFirst()
    }

    fun clearCanvas() {
        val canvas = activeCanvas() ?: return
        pushPixelUndo()
        if (activeLayerIndex == 0) {
            canvas.drawColor(CANVAS_BACKGROUND, PorterDuff.Mode.SRC)
        } else {
            canvas.drawColor(AndroidColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }
        compositeBitmap = null
        revision++
    }

    // ---- Capas --------------------------------------------------------------

    fun addLayer() {
        if (canvasWidth <= 0 || canvasHeight <= 0) return
        pushStructuralUndo()
        val bmp = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        layers.add(activeLayerIndex + 1, PaintLayer(nextId(), bmp, nextLayerName()))
        activeLayerIndex += 1
        compositeBitmap = null
        revision++
    }

    fun removeLayer(index: Int) {
        if (layers.size <= 1 || index !in layers.indices) return
        pushStructuralUndo()
        layers.removeAt(index)
        activeLayerIndex = if (activeLayerIndex >= layers.size) layers.size - 1
        else if (activeLayerIndex > index) activeLayerIndex - 1
        else activeLayerIndex
        compositeBitmap = null
        revision++
    }

    fun selectLayer(index: Int) {
        if (index in layers.indices) activeLayerIndex = index
    }

    fun toggleLayerVisibility(index: Int) {
        val layer = layers.getOrNull(index) ?: return
        layer.visible = !layer.visible
        compositeBitmap = null
        revision++
    }

    fun setLayerOpacity(index: Int, opacity: Float) {
        val layer = layers.getOrNull(index) ?: return
        layer.opacity = opacity.coerceIn(0f, 1f)
        compositeBitmap = null
        revision++
    }

    fun renameLayer(index: Int, name: String) {
        val layer = layers.getOrNull(index) ?: return
        if (name.isNotBlank()) layer.name = name
    }

    fun moveLayerUp(index: Int) {
        if (index !in layers.indices || index == layers.lastIndex) return
        pushStructuralUndo()
        layers.add(index + 1, layers.removeAt(index))
        activeLayerIndex = if (activeLayerIndex == index) index + 1 else if (activeLayerIndex == index + 1) index else activeLayerIndex
        compositeBitmap = null
        revision++
    }

    fun moveLayerDown(index: Int) {
        if (index !in layers.indices || index == 0) return
        pushStructuralUndo()
        layers.add(index - 1, layers.removeAt(index))
        activeLayerIndex = if (activeLayerIndex == index) index - 1 else if (activeLayerIndex == index - 1) index else activeLayerIndex
        compositeBitmap = null
        revision++
    }

    /** Combina la capa `index` con la que tiene debajo, respetando su opacidad. Deshacer atómico. */
    fun mergeDown(index: Int) {
        if (index <= 0 || index !in layers.indices) return
        val top = layers[index]
        val bottom = layers[index - 1]
        val before = bottom.bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val canvas = Canvas(bottom.bitmap)
        layerCompositePaint.alpha = (top.opacity * 255).toInt().coerceIn(0, 255)
        canvas.drawBitmap(top.bitmap, 0f, 0f, layerCompositePaint)
        val after = bottom.bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val activeBefore = activeLayerIndex

        layers.removeAt(index)
        activeLayerIndex = index - 1

        pushHistory(
            HistoryEntry.MergeEdit(
                topLayer = top,
                insertIndex = index,
                bottomLayerId = bottom.id,
                bottomBitmapBefore = before,
                bottomBitmapAfter = after,
                activeIndexBefore = activeBefore,
                activeIndexAfter = activeLayerIndex,
                forward = false
            )
        )
        compositeBitmap = null
        revision++
    }

    // ---- Trazo libre: pincel, borrador y aerógrafo ------------------------

    fun onStrokeStart(point: Offset) {
        val canvas = activeCanvas() ?: return
        when (currentTool) {
            PaintTool.BRUSH, PaintTool.ERASER, PaintTool.SPRAY -> {
                pushPixelUndo()
                lastPoint = point
                configureStrokePaint()
                if (currentTool == PaintTool.SPRAY) sprayAt(canvas, point) else canvas.drawPoint(point.x, point.y, strokePaint)
                compositeBitmap = null
                revision++
            }
            PaintTool.LINE, PaintTool.RECTANGLE, PaintTool.CIRCLE -> {
                shapeStart = point
                shapeCurrent = point
            }
            PaintTool.BUCKET, PaintTool.MAGIC_WAND, PaintTool.TEXT -> Unit
        }
    }

    fun onStrokeMove(point: Offset) {
        val canvas = activeCanvas() ?: return
        when (currentTool) {
            PaintTool.BRUSH, PaintTool.ERASER -> {
                val from = lastPoint ?: point
                canvas.drawLine(from.x, from.y, point.x, point.y, activeStrokePaint())
                lastPoint = point
                compositeBitmap = null
                revision++
            }
            PaintTool.SPRAY -> {
                sprayAt(canvas, point)
                lastPoint = point
                compositeBitmap = null
                revision++
            }
            PaintTool.LINE, PaintTool.RECTANGLE, PaintTool.CIRCLE -> shapeCurrent = point
            PaintTool.BUCKET, PaintTool.MAGIC_WAND, PaintTool.TEXT -> Unit
        }
    }

    fun onStrokeEnd(point: Offset) {
        val canvas = activeCanvas() ?: return
        when (currentTool) {
            PaintTool.BRUSH, PaintTool.ERASER, PaintTool.SPRAY -> lastPoint = null
            PaintTool.LINE, PaintTool.RECTANGLE, PaintTool.CIRCLE -> {
                val start = shapeStart
                if (start != null) {
                    pushPixelUndo()
                    configureShapePaint()
                    drawCommittedShape(canvas, start, point)
                    compositeBitmap = null
                    revision++
                }
                shapeStart = null
                shapeCurrent = null
            }
            PaintTool.BUCKET, PaintTool.MAGIC_WAND, PaintTool.TEXT -> Unit
        }
    }

    fun onTap(point: Offset) {
        val layer = activeLayer() ?: return
        val x = point.x.toInt()
        val y = point.y.toInt()

        when (currentTool) {
            PaintTool.BUCKET -> {
                if (x < 0 || y < 0 || x >= layer.bitmap.width || y >= layer.bitmap.height) return
                pushPixelUndo()
                floodFill(layer.bitmap, x, y, currentColor)
            }
            PaintTool.MAGIC_WAND -> {
                if (x < 0 || y < 0 || x >= layer.bitmap.width || y >= layer.bitmap.height) return
                // La copia se toma antes de tocar los píxeles: el historial guarda el
                // estado previo, así que hace falta guardarlo aunque el relleno no cambie nada.
                val before = layer.bitmap.copy(Bitmap.Config.ARGB_8888, false)
                val touched = MagicWand.fill(
                    bitmap = layer.bitmap,
                    startX = x,
                    startY = y,
                    fillColor = currentColor,
                    tolerance = smartFillTolerance.toInt(),
                    mode = smartFillMode
                )
                if (touched == 0) return
                pushHistory(HistoryEntry.PixelEdit(layer.id, before))
                _messages.tryEmit(
                    if (smartFillMode == SmartFillMode.COLOR) "Región rellenada"
                    else "Fondo reconstruido en la región"
                )
            }
            PaintTool.TEXT -> {
                textAnchor = Offset(x.toFloat(), y.toFloat())
            }
            else -> return
        }
        compositeBitmap = null
        revision++
    }

    /** Inserta el texto confirmado en el diálogo sobre la capa activa. */
    fun commitText(anchor: Offset, text: String, sizeSp: Float, colorArgb: Int) {
        val canvas = activeCanvas() ?: return
        if (text.isBlank()) return
        pushPixelUndo()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = colorArgb
            this.textSize = sizeSp
            this.textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(text, anchor.x, anchor.y, paint)
        compositeBitmap = null
        revision++
    }

    private fun drawCommittedShape(canvas: Canvas, start: Offset, end: Offset) {
        when (currentTool) {
            PaintTool.LINE -> canvas.drawLine(start.x, start.y, end.x, end.y, shapePaint)
            PaintTool.RECTANGLE -> canvas.drawRect(
                minOf(start.x, end.x), minOf(start.y, end.y),
                maxOf(start.x, end.x), maxOf(start.y, end.y),
                shapePaint
            )
            PaintTool.CIRCLE -> {
                val cx = (start.x + end.x) / 2f
                val cy = (start.y + end.y) / 2f
                val radius = kotlin.math.hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble()).toFloat() / 2f
                canvas.drawCircle(cx, cy, radius, shapePaint)
            }
            else -> Unit
        }
    }

    private fun sprayAt(canvas: Canvas, center: Offset) {
        val radius = strokeWidth * 2.2f
        val density = 18
        val dotPaint = Paint(activeStrokePaint()).apply {
            style = Paint.Style.FILL
            strokeWidth = 0f
        }
        repeat(density) {
            val angle = Random.nextDouble(0.0, 2 * Math.PI)
            val r = Random.nextDouble(0.0, radius.toDouble())
            val dx = (kotlin.math.cos(angle) * r).toFloat()
            val dy = (kotlin.math.sin(angle) * r).toFloat()
            canvas.drawCircle(center.x + dx, center.y + dy, 1.6f, dotPaint)
        }
    }

    /** Devuelve el paint de trazo configurado según la herramienta activa. */
    private fun activeStrokePaint(): Paint {
        val paint = if (currentTool == PaintTool.ERASER) eraserPaint else strokePaint
        paint.strokeWidth = strokeWidth
        if (currentTool != PaintTool.ERASER) paint.color = currentColor
        return paint
    }

    private fun configureStrokePaint() {
        activeStrokePaint()
    }

    private fun configureShapePaint() {
        shapePaint.strokeWidth = strokeWidth
        shapePaint.color = currentColor
        shapePaint.style = if (shapeFilled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
    }

    // ---- Composición de capas para mostrar en pantalla -----------------------

    fun currentImageBitmap(): androidx.compose.ui.graphics.ImageBitmap? {
        if (canvasWidth <= 0 || canvasHeight <= 0 || layers.isEmpty()) return null
        var composite = compositeBitmap
        if (composite == null || composite.width != canvasWidth || composite.height != canvasHeight) {
            composite = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            compositeBitmap = composite
        } else {
            composite.eraseColor(AndroidColor.TRANSPARENT)
        }
        val canvas = Canvas(composite)
        layers.forEach { layer ->
            if (layer.visible) {
                layerCompositePaint.alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(layer.bitmap, 0f, 0f, layerCompositePaint)
            }
        }
        return composite.asImageBitmap()
    }

    private fun flattenedBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        layers.forEach { layer ->
            if (layer.visible) {
                layerCompositePaint.alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
                c.drawBitmap(layer.bitmap, 0f, 0f, layerCompositePaint)
            }
        }
        return bmp
    }

    // ---- Filtros e importación de imágenes -------------------------------------

    /** Aplica un filtro a la capa activa. Es una edición de píxeles, así que entra al historial. */
    fun applyFilter(filter: ImageFilter, amount: Float) {
        val layer = activeLayer() ?: return
        pushPixelUndo()
        ImageFilter.apply(layer.bitmap, filter, amount)
        compositeBitmap = null
        revision++
        _messages.tryEmit("Filtro ${filter.label.lowercase()} aplicado a ${layer.name}")
    }

    /**
     * Carga una imagen de la galería como capa nueva, justo encima de la activa para poder
     * dibujarle encima. Se la ajusta al lienzo conservando la proporción y se centra.
     */
    fun importImage(context: Context, uri: android.net.Uri) {
        if (canvasWidth <= 0 || canvasHeight <= 0) return
        viewModelScope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching { decodeFitting(context, uri, canvasWidth, canvasHeight) }.getOrNull()
            }
            if (imported == null) {
                _messages.emit("No se pudo abrir la imagen")
                return@launch
            }
            pushStructuralUndo()
            layers.add(activeLayerIndex + 1, PaintLayer(nextId(), imported, "Imagen"))
            activeLayerIndex += 1
            compositeBitmap = null
            revision++
            _messages.emit("Imagen importada: ya podés dibujar encima")
        }
    }

    /**
     * Lee la imagen ya reducida a un tamaño razonable y la escala al lienzo.
     *
     * Se calcula primero el `inSampleSize` con `inJustDecodeBounds` para no decodificar de
     * más: una foto de 12 MP son 48 MB de píxeles y con varias capas el proceso muere.
     */
    private fun decodeFitting(
        context: Context,
        uri: android.net.Uri,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth &&
            bounds.outHeight / (sample * 2) >= targetHeight
        ) {
            sample *= 2
        }

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

        val scale = minOf(
            targetWidth.toFloat() / decoded.width,
            targetHeight.toFloat() / decoded.height
        )
        val w = (decoded.width * scale).roundToInt().coerceAtLeast(1)
        val h = (decoded.height * scale).roundToInt().coerceAtLeast(1)

        val fitted = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        Canvas(fitted).apply {
            drawColor(AndroidColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
            val left = (targetWidth - w) / 2f
            val top = (targetHeight - h) / 2f
            drawBitmap(decoded, null, android.graphics.RectF(left, top, left + w, top + h), null)
        }
        decoded.recycle()
        return fitted
    }

    // ---- Exportar / guardar como imagen -------------------------------------

    fun saveToGallery(context: Context, format: ExportFormat = ExportFormat.PNG) {
        if (layers.isEmpty()) return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching { writeImageToGallery(context, flattenedBitmap(), format) }.getOrElse { false }
            }
            _messages.emit(if (success) "Imagen guardada en la galería" else "No se pudo guardar la imagen")
        }
    }

    private fun writeImageToGallery(context: Context, bitmap: Bitmap, format: ExportFormat): Boolean {
        val extension = if (format == ExportFormat.PNG) "png" else "jpg"
        val mimeType = if (format == ExportFormat.PNG) "image/png" else "image/jpeg"
        val compressFormat = if (format == ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val filename = "NB-paint_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.$extension"
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NB-paint")
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
            val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Canvas(flattened).apply {
                drawColor(AndroidColor.WHITE)
                drawBitmap(bitmap, 0f, 0f, null)
            }
            flattened
        } else {
            bitmap
        }

        resolver.openOutputStream(uri)?.use { out ->
            bitmapToWrite.compress(compressFormat, 92, out)
        } ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return true
    }

    // ---- Guardar / abrir proyecto en progreso (con capas) ---------------------

    fun saveProject(context: Context) {
        if (layers.isEmpty()) return
        val snapshot = layers.map { LayerSnapshot(it.bitmap.copy(Bitmap.Config.ARGB_8888, false), it.name, it.visible, it.opacity) }
        val width = canvasWidth
        val height = canvasHeight
        val activeIndex = activeLayerIndex
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, PROJECT_DIR_NAME)
                    if (dir.exists()) dir.deleteRecursively()
                    dir.mkdirs()
                    val manifest = StringBuilder()
                    manifest.appendLine("$width,$height,$activeIndex")
                    snapshot.forEachIndexed { index, s ->
                        val fileName = "layer_$index.png"
                        File(dir, fileName).outputStream().use { out -> s.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                        manifest.appendLine("$fileName|${s.name}|${s.visible}|${s.opacity}")
                    }
                    File(dir, PROJECT_MANIFEST).writeText(manifest.toString())
                    true
                }.getOrElse { false }
            }
            _messages.emit(if (success) "Proyecto guardado" else "No se pudo guardar el proyecto")
        }
    }

    fun loadProject(context: Context) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, PROJECT_DIR_NAME)
                    val manifestFile = File(dir, PROJECT_MANIFEST)
                    if (!manifestFile.exists()) return@runCatching null
                    val lines = manifestFile.readLines()
                    if (lines.isEmpty()) return@runCatching null
                    val (w, h, active) = lines[0].split(",").map { it.trim().toInt() }
                    val loadedLayers = mutableListOf<LayerSnapshot>()
                    for (i in 1 until lines.size) {
                        val parts = lines[i].split("|")
                        if (parts.size < 4) continue
                        val (fileName, name, visibleStr, opacityStr) = parts
                        val bmp = BitmapFactory.decodeFile(File(dir, fileName).absolutePath) ?: continue
                        loadedLayers.add(LayerSnapshot(bmp, name, visibleStr.toBoolean(), opacityStr.toFloatOrNull() ?: 1f))
                    }
                    if (loadedLayers.isEmpty()) null else Triple(w, h, active) to loadedLayers
                }.getOrNull()
            }

            if (result == null) {
                _messages.emit("No se encontró ningún proyecto guardado")
                return@launch
            }

            val (dims, loadedSnapshots) = result
            val (w, h, active) = dims
            layers.clear()
            loadedSnapshots.forEach { s -> layers.add(PaintLayer(nextId(), s.bitmap, s.name, s.visible, s.opacity)) }
            canvasWidth = w
            canvasHeight = h
            activeLayerIndex = active.coerceIn(0, layers.lastIndex)
            undoStack.clear()
            redoStack.clear()
            canUndo = false
            canRedo = false
            compositeBitmap = null
            revision++
            _messages.emit("Proyecto cargado")
        }
    }

    /** Exporta el proyecto completo (todas las capas) como un .nbpaint (zip) y abre el selector para compartirlo. */
    fun shareProject(context: Context) {
        if (layers.isEmpty() || canvasWidth <= 0) return
        val snapshot = layers.map { LayerSnapshot(it.bitmap.copy(Bitmap.Config.ARGB_8888, false), it.name, it.visible, it.opacity) }
        val width = canvasWidth
        val height = canvasHeight
        val active = activeLayerIndex
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val exportDir = File(context.cacheDir, "project_exports").apply { mkdirs() }
                    val zipFile = File(exportDir, "NB-paint_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.nbpaint")
                    ZipOutputStream(zipFile.outputStream()).use { zip ->
                        val manifest = StringBuilder()
                        manifest.appendLine("$width,$height,$active")
                        snapshot.forEachIndexed { index, s ->
                            val entryName = "layer_$index.png"
                            zip.putNextEntry(ZipEntry(entryName))
                            s.bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)
                            zip.closeEntry()
                            manifest.appendLine("$entryName|${s.name}|${s.visible}|${s.opacity}")
                        }
                        zip.putNextEntry(ZipEntry(PROJECT_MANIFEST))
                        zip.write(manifest.toString().toByteArray())
                        zip.closeEntry()
                    }
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
                }.getOrNull()
            }

            if (uri == null) {
                _messages.emit("No se pudo preparar el proyecto para compartir")
                return@launch
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                context.startActivity(Intent.createChooser(intent, "Compartir proyecto NB-paint"))
            }
        }
    }
}
