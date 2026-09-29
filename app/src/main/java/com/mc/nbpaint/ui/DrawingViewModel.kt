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
import kotlinx.coroutines.delay
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

/** Tope del lado mayor de la hoja, para que una foto enorme no quede imposible de dibujar. */
private const val MAX_DOCUMENT_EDGE = 2048
private const val PROJECT_DIR_NAME = "nbpaint_project"
private const val PROJECT_MANIFEST = "manifest.txt"

/** Carpeta con las versiones del autoguardado, una subcarpeta numerada por versión. */
private const val AUTOSAVE_DIR_NAME = "nbpaint_autosaves"

/** Cuántos puntos de autoguardado se conservan. */
private const val MAX_AUTOSAVES = 5

/**
 * Archivo dentro de la carpeta de autoguardados que dice "había trabajo sin terminar".
 * Solo se borra cuando el usuario cierra la app o el proyecto a propósito.
 */
private const val SESSION_MARKER = ".sesion_abierta"

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

/** Un proyecto completo tal como queda en disco. */
private data class LayerSet(
    val width: Int,
    val height: Int,
    val activeIndex: Int,
    val layers: List<LayerSnapshot>
)

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

    // Tamaño del documento en píxeles. Es fijo hasta que se elige otro tamaño de hoja:
    // ver [initDocument] y [resizeDocument].
    var canvasWidth by mutableIntStateOf(0)
        private set
    var canvasHeight by mutableIntStateOf(0)
        private set
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

    /**
     * Cambia el tamaño de la hoja estirando el contenido actual. Se guarda el estado previo
     * para que sea reversible con deshacer.
     */
    fun resizeDocument(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width == canvasWidth && height == canvasHeight) return
        if (layers.isEmpty()) {
            canvasWidth = width
            canvasHeight = height
            initDocument(width, height)
            return
        }

        pushStructuralUndo()
        val src = android.graphics.Rect(0, 0, canvasWidth, canvasHeight)
        val dst = android.graphics.Rect(0, 0, width, height)
        layers.forEach { layer ->
            val resized = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(resized).apply {
                drawColor(AndroidColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
                drawBitmap(layer.bitmap, src, dst, null)
            }
            // El bitmap viejo se suelta pero no se recicla: el historial puede tener una
            // capa apuntando a él y reciclarlo deja el lienzo con píxeles muertos.
            layer.bitmap = resized
        }
        canvasWidth = width
        canvasHeight = height
        compositeBitmap = null
        revision++
    }

    /**
     * Crea el documento una sola vez, con el tamaño que tenía el visor en ese momento.
     *
     * Importante: el documento **no** se vuelve a redimensionar nunca. Antes sí lo hacía, y
     * como el dock inferior cambia la altura del visor, cada apertura o cierre estiraba los
     * píxeles de todas las capas: un cuadrado se volvía rectángulo y el dibujo se deformaba
     * mientras se movía el panel. Ahora el tamaño del documento es fijo y lo que cambia al
     * mover los paneles es únicamente el encuadre (zoom y desplazamiento).
     */
    fun initDocument(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            // El primer tamaño medido del visor sirve de hoja por defecto para los
            // proyectos nuevos, así "cerrar proyecto" no depende de cómo esté la pantalla.
            viewportWidth = width
            viewportHeight = height
        }
        if (layers.isNotEmpty()) return

        canvasWidth = width
        canvasHeight = height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(CANVAS_BACKGROUND)
        layers.add(PaintLayer(nextId(), bmp, nextLayerName()))
        activeLayerIndex = 0
        compositeBitmap = null
        revision++
    }

    /**
     * Cierra el proyecto actual: deja una hoja nueva en blanco y suelta el historial de
     * deshacer, para poder abrir otro proyecto sin que quede pegado lo anterior.
     */
    fun nuevoProyecto(context: Context) {
        viewModelScope.launch {
            val width = viewportWidth.coerceAtLeast(1)
            val height = viewportHeight.coerceAtLeast(1)
            layers.clear()
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawColor(CANVAS_BACKGROUND)
            layers.add(PaintLayer(nextId(), bmp, nextLayerName()))
            canvasWidth = width
            canvasHeight = height
            activeLayerIndex = 0
            undoStack.clear()
            redoStack.clear()
            canUndo = false
            canRedo = false
            compositeBitmap = null
            lastAutosavedSignature = null
            lastAutosavedSlot = null
            // La sesión vieja ya no está en curso: si la app muere ahora, la próxima abre limpia.
            closeSession(context)
            revision++
            _messages.emit("Proyecto cerrado")
        }
    }

    private var viewportWidth = 0
    private var viewportHeight = 0

    private fun nextId(): Int = idCounter++
    private var layerCounter = 0
    private fun nextLayerName(): String {
        layerCounter++
        return "Capa $layerCounter"
    }

    private fun activeLayer(): PaintLayer? = layers.getOrNull(activeLayerIndex)
    private fun activeCanvas(): Canvas? = activeLayer()?.let { Canvas(it.bitmap) }

    /**
     * Posición actual de una capa por su id, o -1 si ya no existe. La interfaz lo usa
     * para no guardar posiciones viejas: si las capas cambian mientras se dibuja la
     * lista, el índice que se capturó antes puede apuntar a otra capa o no existir.
     */
    fun indexOfLayer(id: Int): Int = layers.indexOfFirst { it.id == id }

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
        capUndoHistory()
        // El redo deja de tener sentido en cuanto se hace algo nuevo.
        redoStack.forEach { dropEntry(it) }
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

    /**
     * Descarta una entrada del historial.
     *
     * Cada entrada guarda una copia completa de la capa, así que el historial sí acaparaba
     * memoria. No se recyclean los bitmaps porque una misma copia puede haberse pasado a una
     * capa con `undo` y quedarse ahí: reciclarla desde el historial deja el lienzo apuntando
     * a píxeles muertos y la app revienta al toque siguiente. El recolector los libera solo
     * cuando ya no quedan referencias.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun dropEntry(entry: HistoryEntry) {
        // Nada que hacer: se deja que el GC recoja la copia cuando quede huérfana.
    }

    private fun capUndoHistory() {
        while (undoStack.size > MAX_HISTORY) dropEntry(undoStack.removeFirst())
    }

    private fun capRedoHistory() {
        while (redoStack.size > MAX_HISTORY) dropEntry(redoStack.removeFirst())
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
        capUndoHistory()
        canUndo = true
        canRedo = redoStack.isNotEmpty()
        compositeBitmap = null
        revision++
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
                runCatching { decodeForSheet(context, uri) }.getOrNull()
            }
            if (imported == null) {
                _messages.emit("No se pudo abrir la imagen")
                return@launch
            }

            pushStructuralUndo()
            if (imported.width != canvasWidth || imported.height != canvasHeight) {
                canvasWidth = imported.width
                canvasHeight = imported.height
            }
            layers.add(activeLayerIndex + 1, PaintLayer(nextId(), imported, "Imagen"))
            activeLayerIndex += 1
            compositeBitmap = null
            revision++
            _messages.emit("Imagen importada: la hoja tomó su tamaño")
        }
    }

    /**
     * Lee la imagen para que ella misma sea el tamaño de la hoja, sin estirarla ni dejar
     * bandas. Solo se recorta por [MAX_DOCUMENT_EDGE] para que una foto enorme no vuelva
     * la hoja imposible de dibujar.
     */
    private fun decodeForSheet(context: Context, uri: android.net.Uri): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
        val target = minOf(MAX_DOCUMENT_EDGE, longestEdge)

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

        val scale = minOf(
            1f,
            MAX_DOCUMENT_EDGE.toFloat() / maxOf(decoded.width, decoded.height)
        )
        if (scale >= 1f) return decoded

        val w = (decoded.width * scale).roundToInt().coerceAtLeast(1)
        val h = (decoded.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(scaled).drawBitmap(
            decoded,
            null,
            android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()),
            null
        )
        decoded.recycle()
        return scaled
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

    /**
     * Escribe el proyecto en una carpeta propia. Cada autoguardado va a una carpeta
     * numerada, así se pueden conservar varias versiones en vez de pisar siempre la misma.
     */
    private suspend fun writeProjectTo(dir: File, snapshot: List<LayerSnapshot>, width: Int, height: Int, activeIndex: Int): Boolean =
        runCatching {
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

    private fun currentSnapshot() = layers.map {
        LayerSnapshot(it.bitmap.copy(Bitmap.Config.ARGB_8888, false), it.name, it.visible, it.opacity)
    }

    fun saveProject(context: Context) {
        if (layers.isEmpty()) return
        val snapshot = currentSnapshot()
        val width = canvasWidth
        val height = canvasHeight
        val activeIndex = activeLayerIndex
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                writeProjectTo(File(context.filesDir, PROJECT_DIR_NAME), snapshot, width, height, activeIndex)
            }
            _messages.emit(if (success) "Proyecto guardado" else "No se pudo guardar el proyecto")
        }
    }

    /**
     * Autoguardado: escribe una copia del estado actual y conserva las últimas
     * [MAX_AUTOSAVES] para poder volver a cualquiera. Se dispara al dejar de estar en
     * primer plano, que es cuando el sistema puede matar el proceso.
     *
     * Si no se cambió nada desde el último autoguardado no se escribe otro: abrir y cerrar
     * la app sin dibujar llenaría el historial de copias idénticas.
     */
    fun autosave(context: Context) {
        if (layers.isEmpty()) return
        if (layers.none { layerHasContent(it) }) return

        val signature = autosaveSignature()
        if (signature == lastAutosavedSignature) {
            // No hay nada nuevo, pero la sesión sigue abierta: se refresca el marcador
            // para que si la app muere más adelante se pueda recuperar lo último.
            markSessionOpen(context, lastAutosavedSlot)
            return
        }
        lastAutosavedSignature = signature

        val snapshot = currentSnapshot()
        val width = canvasWidth
        val height = canvasHeight
        val activeIndex = activeLayerIndex
        viewModelScope.launch {
            val slot = withContext(Dispatchers.IO) {
                val root = File(context.filesDir, AUTOSAVE_DIR_NAME)
                if (!root.exists()) root.mkdirs()
                // Se numera a partir de las carpetas que ya hay, así el más reciente queda
                // al final y se puede mostrar como historial.
                val used = root.listFiles()?.filter { it.isDirectory }?.mapNotNull { it.name.toIntOrNull() }?.toMutableSet() ?: mutableSetOf()
                var next = 1
                while (next in used) next++
                writeProjectTo(File(root, next.toString()), snapshot, width, height, activeIndex)
                used.add(next)
                // Se descartan las más viejas.
                used.sorted().take((used.size - MAX_AUTOSAVES).coerceAtLeast(0)).forEach { old ->
                    File(root, old.toString()).deleteRecursively()
                    used.remove(old)
                }
                next
            }
            lastAutosavedSlot = slot
            markSessionOpen(context, slot)
        }
    }

    /**
     * Huella del estado del lienzo. Si dos veces seguidas coincide, no se guarda de nuevo.
     * Incluye el contador de revisiones del motor, que cambia con cada trazo, filtro o
     * cambio de capa.
     */
    private fun autosaveSignature(): String {
        val names = layers.joinToString(";") { "${it.name}:${it.visible}:${it.opacity}" }
        return "$canvasWidth x $canvasHeight | activo=$activeLayerIndex | $names | rev=$revision"
    }

    private var lastAutosavedSignature: String? = null
    private var lastAutosavedSlot: Int? = null

    // ---- Sesión en curso -------------------------------------------------------
    //
    // Android destruye la pantalla (y con ella el ViewModel) cuando apaga la pantalla o
    // aprieta la memoria, aunque el proceso siga vivo, y también mata el proceso entero
    // en segundo plano. En los dos casos lo que había en el lienzo se pierde y el usuario
    // vuelve a un Hoja en blanco. Para eso está el marcador: si existe, la app se cerró
    // sola y lo último se recupera al abrir. Si el usuario cerró la app a propósito, el
    // marcador se borra y la próxima vez arranca limpio, con el historial a mano.

    private fun markSessionOpen(context: Context, slot: Int?) {
        if (slot == null) return
        val root = File(context.filesDir, AUTOSAVE_DIR_NAME)
        if (!root.exists()) root.mkdirs()
        File(root, SESSION_MARKER).writeText(slot.toString())
    }

    /** Llamado cuando el usuario cierra la app a propósito: la próxima vez abre limpio. */
    fun closeSession(context: Context) {
        File(File(context.filesDir, AUTOSAVE_DIR_NAME), SESSION_MARKER).delete()
        lastAutosavedSignature = null
        lastAutosavedSlot = null
    }

    /**
     * Recupera lo último solo si la app se cerró sola. Si el usuario la cerró a propósito
     * no se toca nada: abre en blanco y el historial queda disponible desde el menú.
     */
    fun restoreSessionIfInterrupted(context: Context) {
        val marker = File(File(context.filesDir, AUTOSAVE_DIR_NAME), SESSION_MARKER)
        if (!marker.exists()) return
        val slot = marker.readText().trim().toIntOrNull()
        if (slot == null || !File(File(File(context.filesDir, AUTOSAVE_DIR_NAME), slot.toString()), PROJECT_MANIFEST).exists()) {
            marker.delete()
            return
        }
        lastAutosavedSlot = slot
        restoreAutosave(context, slot)
    }

    /** Borra un punto concreto del historial de autoguardado. */
    fun deleteAutosave(context: Context, slot: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                File(File(context.filesDir, AUTOSAVE_DIR_NAME), slot.toString()).deleteRecursively()
            }
            // Si el usuario ya no tiene historial, el próximo autoguardado tiene que rehacerlo.
            lastAutosavedSignature = null
        }
    }

    /** Borra todo el historial de autoguardado. */
    fun clearAutosaves(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                File(context.filesDir, AUTOSAVE_DIR_NAME).deleteRecursively()
            }
            lastAutosavedSignature = null
        }
    }

    /** Las versiones guardadas por autoguardado, de la más reciente a la más antigua. */
    fun autosaveSlots(context: Context): List<Int> {
        val root = File(context.filesDir, AUTOSAVE_DIR_NAME)
        if (!root.exists()) return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory && File(it, PROJECT_MANIFEST).exists() }
            ?.mapNotNull { it.name.toIntOrNull() }
            ?.sortedDescending()
            .orEmpty()
    }

    /**
     * Carga un punto guardado del historial de autoguardado. Ya no se restaura solo al
     * abrir: el usuario elige cuál recuperar, así abrir la app nunca pisa lo que está
     * dibujando en ese momento.
     */
    fun restoreAutosave(context: Context, slot: Int) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { readProjectFrom(File(File(context.filesDir, AUTOSAVE_DIR_NAME), slot.toString())) }.getOrNull()
            } ?: return@launch
            val (w, h, active, snapshots) = loaded
            if (w <= 0 || h <= 0 || snapshots.isEmpty()) return@launch
            applyLoadedProject(w, h, active, snapshots, "Se recuperó tu trabajo")
        }
    }

    private suspend fun applyLoadedProject(
        w: Int,
        h: Int,
        active: Int,
        snapshots: List<LayerSnapshot>,
        message: String
    ) {
        layers.clear()
        snapshots.forEach { s -> layers.add(PaintLayer(nextId(), s.bitmap, s.name, s.visible, s.opacity)) }
        canvasWidth = w
        canvasHeight = h
        activeLayerIndex = active.coerceIn(0, layers.lastIndex)
        undoStack.clear()
        redoStack.clear()
        canUndo = false
        canRedo = false
        compositeBitmap = null
        revision++
        _messages.emit(message)
    }

    /**
     * ¿La capa tiene algo dibujado o sigue limpia?
     *
     * La primera capa nace con fondo blanco y las de arriba transparentes, así que
     * "vacía" es cualquier píxel que sea blanco o transparente. Sin esto, la capa en blanco
     * siempre contaría como contenido y el autoguardado nunca se restauraría.
     */
    private fun layerHasContent(layer: PaintLayer): Boolean {
        if (!layer.visible) return false
        val bitmap = layer.bitmap
        val stepX = (bitmap.width / 24).coerceAtLeast(1)
        val stepY = (bitmap.height / 24).coerceAtLeast(1)
        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val isEmpty = (pixel ushr 24) == 0 ||
                    (pixel and 0x00FFFFFF) == (CANVAS_BACKGROUND and 0x00FFFFFF)
                if (!isEmpty) return true
            }
        }
        return false
    }

    /** Lee el proyecto del disco. Devuelve null si no hay nada guardado o está corrupto. */
    private fun readProject(context: Context): LayerSet? =
        readProjectFrom(File(context.filesDir, PROJECT_DIR_NAME))

    private fun readProjectFrom(dir: File): LayerSet? {
        val manifestFile = File(dir, PROJECT_MANIFEST)
        if (!manifestFile.exists()) return null
        val lines = manifestFile.readLines()
        if (lines.isEmpty()) return null
        val (w, h, active) = lines[0].split(",").map { it.trim().toInt() }
        val snapshots = mutableListOf<LayerSnapshot>()
        for (i in 1 until lines.size) {
            val parts = lines[i].split("|")
            if (parts.size < 4) continue
            val (fileName, name, visibleStr, opacityStr) = parts
            val bmp = BitmapFactory.decodeFile(File(dir, fileName).absolutePath) ?: continue
            snapshots.add(LayerSnapshot(bmp, name, visibleStr.toBoolean(), opacityStr.toFloatOrNull() ?: 1f))
        }
        return if (snapshots.isEmpty()) null else LayerSet(w, h, active, snapshots)
    }

    fun loadProject(context: Context) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { readProject(context) }.getOrNull()
            }

            if (result == null) {
                _messages.emit("No se encontró ningún proyecto guardado")
                return@launch
            }

            val (w, h, active, loadedSnapshots) = result
            applyLoadedProject(w, h, active, loadedSnapshots, "Proyecto cargado")
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
