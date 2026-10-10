package com.mc.nbpaint.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mc.nbpaint.engine.BrushEngine
import com.mc.nbpaint.engine.BrushKind
import com.mc.nbpaint.engine.BrushSettings
import com.mc.nbpaint.engine.BrushTip
import com.mc.nbpaint.engine.CloneStamp
import com.mc.nbpaint.engine.Compositor
import com.mc.nbpaint.engine.CropEngine
import com.mc.nbpaint.engine.ImageHoja
import com.mc.nbpaint.engine.GestureUndo
import com.mc.nbpaint.engine.PatchEngine
import com.mc.nbpaint.engine.ShapeGeometry
import com.mc.nbpaint.engine.ShapeKind
import com.mc.nbpaint.engine.HistoryController
import com.mc.nbpaint.engine.HistoryDirection
import com.mc.nbpaint.engine.LayerStack
import com.mc.nbpaint.engine.SelectionClip
import com.mc.nbpaint.engine.SelectionEngine
import com.mc.nbpaint.engine.SmudgeMode
import com.mc.nbpaint.data.ExportFormat
import com.mc.nbpaint.data.ImageIo
import com.mc.nbpaint.data.ProjectManifest
import com.mc.nbpaint.data.ProjectStore
import com.mc.nbpaint.data.StorageLayout
import com.mc.nbpaint.data.toSnapshot
import com.mc.nbpaint.engine.CurveEngine
import com.mc.nbpaint.engine.TextRenderer
import com.mc.nbpaint.model.CurveNode
import com.mc.nbpaint.model.CurveNodeSnapshot
import com.mc.nbpaint.model.CurveObject
import com.mc.nbpaint.model.CurveObjectRef
import com.mc.nbpaint.model.CurveSnapshot
import com.mc.nbpaint.model.HistoryEntry
import kotlin.math.max
import com.mc.nbpaint.model.IntRect
import com.mc.nbpaint.model.LayerObjectRef
import com.mc.nbpaint.model.LayerSnapshot
import com.mc.nbpaint.model.LayerTransform
import com.mc.nbpaint.model.ObjectKind
import com.mc.nbpaint.model.PaintLayer
import com.mc.nbpaint.model.PlacedImage
import com.mc.nbpaint.model.PlacedSnapshot
import com.mc.nbpaint.model.Selection
import com.mc.nbpaint.model.SheetRect
import com.mc.nbpaint.model.TextAlign
import com.mc.nbpaint.model.TextObject
import com.mc.nbpaint.model.TextObjectRef
import com.mc.nbpaint.model.TextSnapshot
import com.mc.nbpaint.model.TextTypeface
import com.mc.nbpaint.model.cuadro
import com.mc.nbpaint.model.editableCopy
import com.mc.nbpaint.model.objectAt
import com.mc.nbpaint.model.objectBoundsDe
import com.mc.nbpaint.model.objects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.jvm.JvmName

private const val MAX_HISTORY = 20
private const val CANVAS_BACKGROUND = AndroidColor.WHITE

/** Cuánto se queda puesto un aviso antes de desaparecer solo. */
private const val AVISO_MS = 3200L

/** Tope del lado mayor de la hoja, para que una foto enorme no quede imposible de dibujar. */
private const val MAX_DOCUMENT_EDGE = StorageLayout.MAX_DOCUMENT_EDGE

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

    /** Punto donde se insertará el texto; la UI lo escucha para abrir el diálogo. */
    var textAnchor by mutableStateOf<Offset?>(null)
        private set

    fun setSmartFillTolerance(value: Int) {
        smartFillTolerance = value.coerceIn(0, SelectionEngine.MAX_TOLERANCE).toFloat()
    }

    fun dismissTextAnchor() {
        textAnchor = null
    }

    // ---- Selección -------------------------------------------------------

    /**
     * Zona de trabajo de las herramientas.
     *
     * Es una función general, no una más: mientras haya selección, todo lo que se dibuja
     * (pincel, borrador, formas, texto, relleno, parche) y todo lo que se recorta o se
     * filtra queda dentro de ella. Al abrir la app no hay selección, para que las
     * herramientas sirvan sin pasos extra; en cuanto se elige una zona, se dibuja el marco
     * para que se vea dónde se está trabajando.
     */
    var selection by mutableStateOf<Selection>(Selection.All)
        private set

    /** Tolerancia de la selección inteligente. Comparte valor con el relleno inteligente. */
    var selectionTolerance by mutableFloatStateOf(32f)
        private set

    /** Dónde se está arrastrando el marco, en coordenadas de la hoja. */
    var selectionAnchor by mutableStateOf<Offset?>(null)
        private set

    var selectionCurrent by mutableStateOf<Offset?>(null)
        private set

    /** ¿Hay una selección activa? Lo consulta la UI para mostrar las acciones. */
    val hasSelection: Boolean get() = selection !is Selection.All

    /**
     * Capa de la que salió la selección, por id, o null si no hay ninguna.
     *
     * Una máscara es una imagen en miniatura: guarda el tamaño de la capa de la que salió,
     * y `contains` responde que fuera de su caja no hay nada seleccionado. Si después se
     * aplica a otra capa, o la capa de la que salió cambia de tamaño, el recorte se come
     * contenido que el usuario nunca eligió, y como no se ve ningún aviso parece un fallo
     * de los motores. Guardando de qué capa es, cada consumidor puede comprobarlo antes de
     * tocar nada.
     */
    private var selectionOrigen: Int? = null
        private set

    /**
     * Suelta la selección si ya no encaja con la hoja.
     *
     * Se llama en todo lo que cambia la geometría. Un marco se recorta a la hoja al
     * construirse y por eso sobrevive, pero una máscara no: si la hoja pasa de 1008x1687 a
     * otra cosa, su caja queda descolocada y todo lo de alrededor se cuenta como no
     * seleccionado. Preferimos soltarla y decirlo a que dejar una zona de trabajo que ya
     * no corresponde con lo que se ve en pantalla.
     */
    private fun soltarSeleccionDesalineada() {
        val s = selection
        if (s !is Selection.Mask) return
        if (s.width == canvasWidth && s.height == canvasHeight) return
        selection = Selection.All
        selectionOrigen = null
        selectionAnchor = null
        selectionCurrent = null
        compositeBitmap = null
        revision++
        avisar("La selección se soltó porque cambió el tamaño de la hoja")
    }

    /**
     * Para operaciones que van sobre la capa de origen: si la selección no es de esa capa,
     * avisa en vez de recortar o vaciar la capa equivocada.
     */
    private fun selectionEsDeEstaCapa(capa: PaintLayer): Boolean {
        val origen = selectionOrigen
        if (origen == null || origen == capa.id) return true
        avisar("La selección es de otra capa: cámbiate a esa capa primero")
        return false
    }

    /**
     * ¿La selección describe esta capa concreta?
     *
     * Comprueba dos cosas: que venga de esa capa, y que mida lo mismo. Lo segundo es la que
     * evita la pérdida de contenido. `CropEngine.clearOutside` recorre la capa entera y
     * vacía todo lo que `contains` dé falso, así que con una máscara más pequeña que la
     * capa se vacían de más las franjas de abajo y de la derecha. Y no es un caso raro: la
     * hoja puede haber cambiado de tamaño, un filtro haber devuelto el bitmap al tamaño de
     * la hoja, o simplemente la selección venir de otra capa. Sin este chequeo el recorte
     * destruye lo que el usuario nunca eligió y no dice nada.
     */
    private fun selectionEncajaEnCapa(capa: PaintLayer): Boolean {
        if (!selectionEsDeEstaCapa(capa)) return false
        val s = selection
        if (s is Selection.Mask && (s.width != capa.bitmap.width || s.height != capa.bitmap.height)) {
            avisar(
                "La selección mide ${s.width}x${s.height} y la capa ${capa.bitmap.width}x" +
                    "${capa.bitmap.height}: vuelve a elegir la zona"
            )
            return false
        }
        return true
    }

    /** Empieza a arrastrar el marco. */
    fun startSelection(anchor: Offset) {
        selectionAnchor = anchor
        selectionCurrent = anchor
    }

    /** Actualiza el marco mientras se arrastra. */
    fun updateSelection(point: Offset) {
        if (selectionAnchor == null) return
        selectionCurrent = point
        val from = selectionAnchor ?: return
        selection = SelectionEngine.rectangle(
            from.x.toInt(), from.y.toInt(), point.x.toInt(), point.y.toInt(),
            canvasWidth, canvasHeight
        )
        compositeBitmap = null
        revision++
    }

    /** Suelta el marco y lo deja como selección. */
    fun endSelection() {
        val current = selection
        selectionAnchor = null
        selectionCurrent = null
        // Un marco vacío se degrada a "todo", como siempre. Una región de forma libre vacía
        // también, y esto no es cosmético: `contains` daría falso en todas partes, así que
        // el pincel, el relleno y el parche no tocarían ningún píxel y todas las herramientas
        // quedarían inertes sin que nada lo explique en pantalla.
        val vacia = when (val s = current) {
            is Selection.Mask -> s.selectedCount == 0
            is Selection.Rect -> s.rect.isEmpty
            Selection.All -> false
        }
        if (vacia) {
            selection = Selection.All
            compositeBitmap = null
            revision++
            // Sin aviso: que la zona vacía se convierta en "toda la hoja" se ve, porque los
            // tiradores desaparecen y se vuelve a pintar en cualquier parte. Confirmarlo era
            // justo el tipo de aviso que solo tapaba el lienzo para repetir lo que ya pasó.
            // Un recorte que esperaba zona se cancela: si no, el siguiente marco que se hiciera,
            // sin querer, recortaba el objeto.
            if (recortePendiente != null) {
                recortePendiente = null
                avisar("El recorte se canceló: la zona quedó vacía")
            }
            return
        }
        // Si estaba pendiente recortar un objeto, este marco es la zona que se conserva y no
        // una selección para las demás herramientas. Se aplica aquí y no en el gesto de
        // arrastrar para que el recorte solo salga al soltar, y no con cada dedo por medio.
        recortePendiente?.let { ref ->
            val rect = (current as? Selection.Rect)?.rect
            recortePendiente = null
            if (rect == null) {
                avisar("El recorte necesita un rectángulo")
            } else {
                aplicarRecorteAObjeto(ref, rect)
            }
            // El marco que se acaba de hacer era para el recorte, no para pintar. Dejarlo
            // puesto convertiría el recorte en una zona de recorte de la capa, que es otra
            // cosa, y dejaría la herramienta lista para algo que el usuario no pidió.
            clearSelection()
        }
    }

    /** Selecciona toda la hoja. */
    fun selectAll() {
        selection = if (canvasWidth > 0 && canvasHeight > 0) {
            SelectionEngine.selectAll(canvasWidth, canvasHeight)
        } else {
            Selection.All
        }
        selectionOrigen = activeLayer()?.id
        compositeBitmap = null
        revision++
    }

    /** Quita la selección y vuelve a trabajar en toda la hoja. */
    fun clearSelection() {
        selection = Selection.All
        selectionOrigen = null
        selectionAnchor = null
        selectionCurrent = null
        compositeBitmap = null
        revision++
    }

    /** Da la vuelta la selección: lo de dentro pasa a fuera y al revés. */
    fun invertSelection() {
        selection = when (val s = selection) {
            is Selection.Mask -> s.inverted()
            is Selection.Rect -> SelectionEngine.invertRect(s.rect, canvasWidth, canvasHeight)
            Selection.All -> Selection.All
        }
        compositeBitmap = null
        revision++
    }

    /**
     * Selección inteligente sobre la capa activa: elige la región parecida al punto tocado,
     * con la tolerancia fijada. Con [additive] se suma a la que ya había, en vez de cambiarla.
     */
    fun smartSelectAt(point: Offset, additive: Boolean = false) {
        val layer = activeLayer() ?: return
        val bitmap = layer.bitmap
        val x = point.x.toInt()
        val y = point.y.toInt()
        if (x < 0 || y < 0 || x >= bitmap.width || y >= bitmap.height) {
            avisar("Toca dentro de la capa para elegir la zona")
            return
        }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val nueva = SelectionEngine.smartSelect(
            pixels, bitmap.width, bitmap.height, x, y, selectionTolerance.toInt()
        )
        // La unión se hace contra el tamaño de la capa activa, que es de donde salió la
        // selección: si se dejara el tamaño de la anterior, la máscara podría quedar más
        // pequeña que la capa y cuenta como no seleccionada todo lo que quede fuera.
        val previa = if (additive) {
            SelectionEngine.union(selection, nueva, bitmap.width, bitmap.height)
        } else {
            nueva
        }
        selection = previa
        selectionOrigen = layer.id
        compositeBitmap = null
        revision++
    }

    /** Rectángulo que abarca la selección, o null si no hay nada seleccionado. */
    fun selectionBounds(): IntRect? = when (val s = selection) {
        is Selection.All -> null
        is Selection.Rect -> s.rect
        is Selection.Mask -> s.bounds()
    }

    /**
     * Recorta la capa activa a la selección.
     *
     * Con un marco, la capa queda con el tamaño del recorte, como al cortar una foto. Con
     * una región de forma libre se vacía lo que queda fuera.
     */
    fun cropActiveLayerToSelection(): Boolean {
        val layer = activeLayer() ?: return false
        if (!hasSelection) {
            avisar("Primero elige una zona")
            return false
        }
        // Si la capa tiene una imagen puesta, primero hay que fundirla en píxeles. Sin esto el
        // recorte se aplica a `layer.bitmap`, que en una capa con imagen puesta es lo que
        // hubiera debajo (casi siempre nada), y la imagen se seguiría dibujando encima
        // porque `placed` queda vivo. El resultado es que el recorte no parece hacer nada y
        // de paso se pierde lo que se hubiera pintado en esa capa.
        bakePlacedForPixelEdit(layer)
        if (!selectionEncajaEnCapa(layer)) return false
        val anterior = layer.bitmap
        // Se recorta antes de empujar nada al historial. Al revés, un recorte que no cambia
        // nada deja un punto de deshacer que al pulsarlo no deshace nada visible, y el
        // usuario lo cuenta como que el historial se ha comido un paso.
        val fondo = if (activeLayerIndex == 0) CANVAS_BACKGROUND else AndroidColor.TRANSPARENT
        val recortado = CropEngine.cropToSelection(anterior, selection, fondo)
        compositeBitmap = null
        revision++
        if (recortado == null) {
            avisar("La selección coincide con la capa, no hay nada que recortar")
            return false
        }
        if (recortado !== anterior) {
            pushPixelUndo(layer)
            layer.bitmap = recortado
            // El viejo sigue vivo en el historial, así que no se recicla aquí.
            // La selección se suelta porque la capa ya no mide lo que medía: si se
            // dejara, su caja apuntaría a una geometría que no existe.
            soltarSeleccionDesalineada()
            // Y la capa vuelve a medir la hoja entera, con lo que le faltaba tomado de las
            // capas de abajo. Sin esto la capa se quedaba más pequeña que el papel, y
            // cualquier trazo que se saliera de ella se perdía al salirse del bitmap.
            completarCapasDesdeAbajo(CANVAS_BACKGROUND)
        }
        clearSelection()
        return true
    }

    /**
     * Recorta el **documento** entero a la zona elegida: la hoja se encoge al rectángulo y todas
     * las capas se recortan con ella, corridas lo mismo para que el dibujo siga en su sitio.
     *
     * Es distinto de [cropActiveLayerToSelection], que solo toca la capa de arriba. Aquí lo que
     * se decide es el tamaño del papel, que es lo que uno espera al recortar una foto, y hasta
     * ahora no había manera: `resizeDocument` solo agranda y rellena, nunca quita.
     *
     * Necesita un rectángulo y no una región con forma libre: la hoja tiene que seguir siendo un
     * rectángulo, y una mancha irregular no se puede convertir en un papel.
     *
     * Se hornean los objetos de todas las capas antes de recortar. Es lo único que los mete en
     * los píxeles, y hace falta porque un texto o una curva se guardan con su posición en
     * coordenadas de la hoja vieja: al encoger la hoja, sus coordenadas quedarían apuntando a otro
     * sitio. Con lo horneado, todo pasa a estar en los píxeles y se recorta de una vez.
     */
    fun cropDocumentToSelection(): Boolean {
        if (layers.isEmpty()) return false
        val rect = (selection as? Selection.Rect)?.rect
        if (rect == null) {
            avisar("El recorte del documento necesita un rectángulo")
            return false
        }
        val destino = rect.clamped(canvasWidth, canvasHeight)
        if (destino.isEmpty) {
            avisar("La zona queda fuera de la hoja")
            return false
        }
        val yaEsLaHoja = destino.left == 0 && destino.top == 0 &&
            destino.right == canvasWidth && destino.bottom == canvasHeight
        if (yaEsLaHoja) {
            avisar("La zona coincide con la hoja, no hay nada que recortar")
            return false
        }

        // Un solo punto de deshacer para todo el documento. Cada horneado y cada recorte por
        // separado dejarían un historial en el que hay que deshacer ocho veces, y deshacer una
        // vez dejaría la hoja con un tamaño y las capas con otro.
        pushStructuralUndo()
        layers.forEach { layer ->
            // Con `incluirTransformCapa` porque el recorte de la hoja corta por coordenadas del
            // papel: la transformación de cada capa tiene que quedar cocida en sus píxeles
            // antes, o la capa se copiaría corrida un dedo de sitio respecto a como se veía.
            bakePlacedForPixelEdit(layer, apuntarHistorial = false, incluirTransformCapa = true)
            val fondo = if (indexOfLayer(layer.id) == 0) CANVAS_BACKGROUND else AndroidColor.TRANSPARENT
            layer.bitmap = CropEngine.cropAt(
                layer.bitmap,
                destino,
                destino.left,
                destino.top,
                fondo
            )
        }
        canvasWidth = destino.width
        canvasHeight = destino.height
        soltarSeleccionDesalineada()
        soltarConfineBuffers()
        clearSelection()
        compositeBitmap = null
        revision++
        return true
    }

    /**
     * Encoge el documento hasta el rectángulo que abarca **todas** las capas, quitando el margen
     * vacío alrededor del dibujo.
     *
     * Se mira el contenido de todas las capas, y no solo el de las que se ven, porque el
     * documento no puede quedar más pequeño que lo que hay guardado: si una capa escondida
     * tuviera algo en una esquina, recortarla a la vista dejaría ese algo fuera del papel, fuera
     * del alcance del deshacer y sin forma de recuperarlo.
     */
    fun cropDocumentToContent(): Boolean {
        if (layers.isEmpty()) return false
        var contenido: IntRect? = null
        layers.forEach { layer ->
            val bitmap = layer.bitmap
            val ancho = bitmap.width
            val alto = bitmap.height
            if (ancho <= 0 || alto <= 0) return@forEach
            val pixeles = IntArray(ancho * alto)
            bitmap.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)
            val deEstaCapa = SelectionEngine.contentBounds(pixeles, ancho, alto) ?: return@forEach
            // Los píxeles de la capa están en su propio espacio, así que el rectángulo que sale
            // de ellos hay que llevarlo a la hoja con la transformación de esa capa. Sin esto,
            // una capa movida de sitio se recortaba donde no está: "encajar la hoja al dibujo"
            // cortaba la imagen de una capa que se veía entera en pantalla.
            contenido = contenido?.union(alHoja(deEstaCapa, layer.transform)) ?: alHoja(deEstaCapa, layer.transform)
        }
        // Y también el contenido de los objetos, que no está en los píxeles: una capa con solo
        // una foto puesta no tiene nada pintado, y sin esto la hoja se encogía a la nada y se
        // perdía la imagen.
        layers.forEach { layer ->
            val deLosObjetos = objetosEnHoja(layer) ?: return@forEach
            contenido = contenido?.union(deLosObjetos) ?: deLosObjetos
        }
        val destino = contenido?.clamped(canvasWidth, canvasHeight)
        if (destino == null || destino.isEmpty) {
            avisar("No hay nada dibujado que recortar")
            return false
        }
        selection = Selection.Rect(destino)
        return cropDocumentToSelection()
    }

    /** Lleva un rectángulo del espacio de una capa al de la hoja. */
    private fun alHoja(rect: IntRect, transform: LayerTransform): IntRect {
        if (transform.esIdentity) return rect
        val a = transform.aHoja(rect.left.toFloat(), rect.top.toFloat())
        val b = transform.aHoja(rect.right.toFloat(), rect.bottom.toFloat())
        return IntRect(
            left = kotlin.math.floor(kotlin.math.min(a.x, b.x)).toInt(),
            top = kotlin.math.floor(kotlin.math.min(a.y, b.y)).toInt(),
            right = kotlin.math.ceil(kotlin.math.max(a.x, b.x)).toInt(),
            bottom = kotlin.math.ceil(kotlin.math.max(a.y, b.y)).toInt()
        )
    }

    /**
     * Cuadro que ocupan en la hoja los objetos de una capa: la foto, los textos y las curvas.
     *
     * Va por los objetos visibles y no por la caja de la capa entera, porque lo que se ve es
     * lo que tiene que caber en la hoja después del recorte.
     */
    private fun objetosEnHoja(layer: PaintLayer): IntRect? {
        var cuadro: IntRect? = null
        layer.objects().forEach { ref ->
            val b = ref.bounds()
            if (b.isEmpty) return@forEach
            // El rectángulo se pasa de flotantes a enteros hacia fuera, no hacia dentro: al
            // revés se comería medio píxel del borde del objeto y, con la foto o el texto cerca
            // del borde, el recorte lo cortaría.
            val enHoja = alHoja(
                IntRect(
                    left = kotlin.math.floor(b.left).toInt(),
                    top = kotlin.math.floor(b.top).toInt(),
                    right = kotlin.math.ceil(b.right).toInt(),
                    bottom = kotlin.math.ceil(b.bottom).toInt()
                ),
                layer.transform
            )
            cuadro = cuadro?.union(enHoja) ?: enHoja
        }
        return cuadro
    }

    val layers = mutableStateListOf<PaintLayer>()

    var activeLayerIndex by mutableIntStateOf(0)
        private set

    /**
     * Aviso corto para cuando algo **no** ha pasado: la selección es de otra capa, la imagen
     * no se pudo abrir, el recorte no tenía nada que quitar.
     *
     * Antes esto era un `SharedFlow` con snackbar, y ahí está el problema que motivó el
     * cambio: el snackbar se dibuja encima de la pantalla y se queda ahí unos segundos
     * tapando el lienzo y, sobre todo, quedándose con los toques. En una app de dibujo tapar el
     * lienzo es peor que no avisar: el usuario ve que ha pintado un tramo en el sitio
     * equivocado, porque el toque se lo comió el aviso. Y encima la mayoría de los mensajes
     * solo confirmaban lo que ya se veía, que es justo lo que no hacía falta.
     *
     * Ahora es un estado de lectura que la interfaz pinta en la cabecera del panel, dentro
     * del propio diseño y sin nada flotando encima. Además se limpia solo a los pocos
     * segundos, para que un aviso viejo no se quede contando la historia de lo que pasó hace
     * un rato.
     */
    var aviso by mutableStateOf<String?>(null)
        private set

    private var avisoJob: Job? = null

    /** Deja un aviso puesto un rato y luego lo retira solo. */
    private fun avisar(texto: String) {
        aviso = texto
        avisoJob?.cancel()
        avisoJob = viewModelScope.launch {
            delay(AVISO_MS)
            aviso = null
        }
    }

    /** Quita el aviso sin esperar, para cuando el usuario ya ha corregido lo que fosaba. */
    fun limpiarAviso() {
        avisoJob?.cancel()
        aviso = null
    }

    // ---- Motor interno ---------------------------------------------------

    // Tamaño del documento en píxeles. Es fijo hasta que se elige otro tamaño de hoja:
    // ver [initDocument] y [resizeDocument].
    var canvasWidth by mutableIntStateOf(0)
        private set
    var canvasHeight by mutableIntStateOf(0)
        private set
    private var compositeBitmap: Bitmap? = null

    /**
     * Las dos pilas de deshacer/rehacer, con su tope y sus reglas de invalidación.
     *
     * Las entradas descartadas no se recyclean a propósito: una copia puede haberse pasado a
     * una capa con `undo` y quedarse ahí, así que reciclarla desde el historial deja el lienzo
     * apuntando a píxeles muertos. Las deja ir el recolector cuando quedan huérfanas.
     */
    private val history = HistoryController<HistoryEntry>(MAX_HISTORY, ::applyHistoryEntry)

    private var lastPoint: Offset? = null

    /**
     * Dónde estaba el último toque del difuminador, de dónde saca lo que va a arrastrar.
     *
     * Se anula al empezar cada trazo: si no, el primer toque de un trazo nuevo tiraría del
     * último del trazo anterior, que puede estar en otra parte del lienzo, y saldría un
     * salto de color justo donde el dedo acaba de posarse.
     */
    private var difuminadoUltimo: Offset? = null

    /**
     * El difuminador en modo arrastrar apunta el deshacer aquí en el primer toque y no lo
     * cumple hasta que un toque escribe píxeles de verdad ([pushUndoSiPintaDifuminado]).
     */
    private var difuminadoPendienteUndo = false

    private fun pushUndoSiPintaDifuminado() {
        if (!difuminadoPendienteUndo) return
        difuminadoPendienteUndo = false
        pushPixelUndo()
    }

    /** Parche del tamaño del pincel donde se copia lo que el difuminador va a desplazar. */
    private var parcheDifuminado: Bitmap? = null

    private var idCounter = 0

    private val strokePaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    /**
     * Borrador de las capas de arriba: pone los píxeles en transparente con CLEAR, así se
     * quita una parte de una imagen importada y queda al descubierto lo que haya debajo.
     */
    private val eraserPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    /**
     * Borrador de la capa de fondo: pinta el color de la hoja en vez de transparentear.
     *
     * Con CLEAR, borrar sobre la capa de fondo dejaba la zona transparente y se veía el
     * fondo oscuro de la app: el trazo del borrador salía negro sobre el lienzo blanco y
     * parecía que estaba pintando en vez de borrar. En la capa que hace de fondo, lo
     * correcto es volver a poner el color de la hoja.
     */
    private val backgroundEraserPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = CANVAS_BACKGROUND
    }

    /** Pintura de las formas: siempre lleva el color elegido. */
    private val shapePaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val layerCompositePaint = Paint(Paint.ANTI_ALIAS_FLAG)

/** Pintura totalmente opaca, para fundir contenido en un bitmap sin opacidades de por medio. */
private val opaquePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** Recorte circular que usa el clonado y el parche para no copiar más que el pincel. */
    private val patchClip = android.graphics.Path()
    private val copyMatrix = android.graphics.Matrix()

    // ---- Ciclo de vida del lienzo -----------------------------------------

    /**
     * Cambia el tamaño de la hoja **sin deformar lo dibujado**: el contenido se queda donde
     * estaba y lo que aparece se completa con lo que hay debajo. Se guarda el estado previo
     * para que sea reversible con deshacer.
     *
     * La capa de abajo se completa con el papel y las de arriba con lo que hay debajo de cada
     * una. Rellenarlas con el papel era el fallo: una capa de arriba que crece un rectángulo
     * blanco tapaba lo que tuviera debajo, y al ocultar la capa de abajo el rectángulo seguía
     * ahí. Con lo que hay debajo, la capa sigue viéndose igual de grande y de underlie nada.
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
        canvasWidth = width
        canvasHeight = height
        completarCapasDesdeAbajo(CANVAS_BACKGROUND)
        soltarSeleccionDesalineada()
        soltarConfineBuffers()
        compositeBitmap = null
        revision++
    }

    /**
     * Deja todas las capas con el tamaño de la hoja, rellenando lo que falte con lo que hay
     * debajo de cada una.
     *
     * Es lo que hace que una capa nunca sea más pequeña que la hoja. Sin esto, una capa
     * recortada a una zona se quedaba más pequeña y todo lo que se pintara fuera se perdía al
     * salirse del bitmap, y al exportar el rectángulo que le faltaba tapaba lo de debajo con
     * blanco.
     *
     * @return si ha cambiado algo, para no gastar un punto de deshacer cuando ya estaba todo
     *         en su sitio.
     */
    private fun completarCapasDesdeAbajo(fondo: Int): Boolean {
        var cambio = false
        layers.forEachIndexed { indice, layer ->
            val actual = layer.bitmap
            val ancho = maxOf(1, canvasWidth)
            val alto = maxOf(1, canvasHeight)
            if (actual.width == ancho && actual.height == alto) return@forEachIndexed
            val destino = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(destino)
            canvas.drawColor(fondo)
            if (indice > 0) {
                // Lo de debajo se dibuja primero para que la capa siga teniendo lo que tenía
                // detrás, en vez de un agujero con el color del papel. Y pasa por el compositor
                // para que cada capa de abajo llegue con su transformación puesta: si se
                // copiara su bitmap en crudo, al redimensionar la hoja una capa movida se
                // vería corrida respecto a la de encima, y luego ya no podría deshacer.
                layers.take(indice).forEach { debajo ->
                    Compositor.drawLayerBitmap(canvas, debajo, opaquePaint)
                }
            }
            canvas.drawBitmap(actual, 0f, 0f, null)
            layer.bitmap = destino
            cambio = true
        }
        return cambio
    }

    /**
     * Pasa un bitmap al tamaño de la hoja sin deformarlo: lo copia en la esquina superior
     * izquierda, sin escalarlo, y el resto queda con el color de fondo.
     *
     * Antes se estiraba con `drawBitmap(bitmap, src, dst)`, que mapeaba la hoja vieja entera
     * sobre la nueva. Con una hoja de otra proporción eso convertía un círculo en un óvalo y
     * una foto en una mancha, y como el estirado quedaba horneado en el bitmap no había forma
     * de recuperarlo. Ahora la geometría vive en [LayerStack].
     *
     * El bitmap viejo se suelta pero no se recicla: el historial puede tener una capa
     * apuntando a él y reciclarlo deja el lienzo con píxeles muertos.
     */
    private fun fitBitmapToSheet(bitmap: Bitmap, width: Int, height: Int): Bitmap =
        LayerStack.fitToSheet(bitmap, width, height, CANVAS_BACKGROUND)

    /** Lee un bitmap como un arreglo plano de píxeles, para pasarlo a los motores puros. */
    private fun pixelsOf(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels
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
        // El tamaño del visor se lleva siempre el último medido, no solo el primero: se usa
        // como hoja por defecto al cerrar el proyecto, y con el valor del arranque una
        // rotación dejaba la hoja nueva con las proporciones viejas.
        viewportWidth = width
        viewportHeight = height
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
            // La numeración arranca de nuevo: si no, cerrar un proyecto de tres capas y abrir
            // otro dejaba la única capa llamada "Capa 4".
            layerCounter = 0
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawColor(CANVAS_BACKGROUND)
            layers.add(PaintLayer(nextId(), bmp, nextLayerName()))
            canvasWidth = width
            canvasHeight = height
            activeLayerIndex = 0
            // Las capas anteriores ya no existen: cualquier selección que sobreviva estaría
            // hablando de una capa que no está.
            clearSelection()
            soltarConfineBuffers()
            history.clear()
            syncHistoryState()
            compositeBitmap = null
            lastAutosavedSignature = null
            lastAutosavedSlot = null
            // La sesión vieja ya no está en curso: si la app muere ahora, la próxima abre limpia.
            closeSession(context)
            revision++
        }
    }

    private var viewportWidth = 0
    private var viewportHeight = 0

    private fun nextId(): Int = idCounter++
    private var layerCounter = 0
    private fun nextLayerName(): String {
        layerCounter++
        return LayerStack.defaultLayerName(layerCounter)
    }

    private fun activeLayer(): PaintLayer? = layers.getOrNull(activeLayerIndex)

    /** Posición y tamaño del contenido de la capa activa. */
    val activeTransform: LayerTransform
        get() = activeLayer()?.transform ?: LayerTransform()

    /**
     * Pasa un punto de la hoja al contenido de la capa activa.
     *
     * Con la capa sin mover es la identidad y no cambia nada. En cuanto la capa está movida o
     * estirada, sin esta cuenta el pincel pintaría en un sitio y el usuario vería el
     * contenido en otro: la capa parecería moverse sola al pintar.
     */
    fun aContenido(punto: Offset): Offset = activeTransform.aContenido(punto.x, punto.y)

    /** Pasa un punto del contenido de la capa activa a la hoja, para dibujarla encima. */
    fun aHoja(punto: Offset): Offset = activeTransform.aHoja(punto.x, punto.y)

    /**
     * Mueve el contenido de la capa activa [dx]/[dy] píxeles de la hoja.
     *
     * Es lo que da la independencia entre capas: mover una mueve su foto, su texto y sus
     * curvas, y no toca las demás capas. El desplazamiento va en píxeles de la hoja para que un
     * dedo mueva lo mismo que se ve, y no más o menos según la escala de la capa.
     */
    fun moverCapa(dx: Float, dy: Float) {
        val layer = activeLayer() ?: return
        if (dx == 0f && dy == 0f) return
        if (moveUndo.pushOnce()) pushStructuralUndo()
        layer.transform = layer.transform.copy(
            offsetX = layer.transform.offsetX + dx,
            offsetY = layer.transform.offsetY + dy
        )
        soltarConfineBuffers()
        compositeBitmap = null
        revision++
    }

    /**
     * Estira la capa activa alrededor de [foco], que está en coordenadas de la hoja.
     *
     * El foco se queda donde está, como en la pinza de la imagen: si la escala se aplicara
     * en la esquina de la hoja, al ampliar una capa el contenido se iría para el lado
     * contrario y habría que volver a buscarlo.
     */
    fun escalarCapa(foco: Offset, cambio: Float) {
        val layer = activeLayer() ?: return
        if (cambio <= 0f || !cambio.isFinite()) return
        val despues = layer.transform.conZoom(foco, cambio)
        if (despues == layer.transform) return
        if (moveUndo.pushOnce()) pushStructuralUndo()
        layer.transform = despues
        compositeBitmap = null
        revision++
    }

    /** Devuelve el contenido de la capa activa a su sitio y su tamaño, sin tocar las demás. */
    fun restablecerCapa(): Boolean {
        val layer = activeLayer() ?: return false
        if (layer.transform.esIdentity) {
            avisar("La capa ya está en su sitio")
            return false
        }
        pushStructuralUndo()
        layer.transform = LayerTransform()
        soltarSeleccionDesalineada()
        compositeBitmap = null
        revision++
        return true
    }

    /**
     * Cuadro que ocupa el contenido de la capa dentro de la hoja, con su transformación puesta.
     *
     * Lo necesita el recorte del documento: si solo mirara el papel, dejaría fuera el
     * contenido de una capa desplazada, que es el error que hace que "encajar la hoja al
     * dibujo" recorte la imagen de una capa que se ve entera en pantalla.
     */
    fun sheetContentBounds(layer: PaintLayer, fallbackWidth: Int, fallbackHeight: Int): SheetRect =
        layer.transform.cuadro(layer.bitmap.width, layer.bitmap.height)
            .takeIf { !it.isEmpty }
            ?: LayerTransform().cuadro(fallbackWidth, fallbackHeight)
    /**
     * Lienzo de la capa activa, ya recortado a la selección.
     *
     * Poner el recorte aquí y no en cada herramienta es lo que hace que la selección sea
     * una función general: pincel, borrador, spray, formas, texto y "vaciar" quedan
     * Todos dentro de la zona elegida sin tocar una sola línea de esos métodos.
     *
     * Cada llamada devuelve un `Canvas` nuevo, así que el recorte vive solo lo que dura
     * el trazo y no se acumula ni se filtra a la siguiente operación.
     */
    private fun activeCanvas(): Canvas? = activeLayer()?.let {
        ensureLayerCoversSheet(it)
        val canvas = Canvas(it.bitmap)
        if (!SelectionClip.apply(canvas, selection)) return@let canvas
        canvas
    }

    /**
     * Deja la capa lista para editarse por píxeles.
     *
     * Si tiene objetos encima (la imagen puesta, el texto o la curva), los funde en el bitmap
     * y dejan de ser movibles. Hace falta porque clonar y parchear se usan sobre fotos y
     * sobre dibujos, y un objeto no tiene píxeles que retoquear: sin esto las dos
     * herramientas no harían nada visible. El punto de deshacer es estructural, así que
     * deshacer devuelve el objeto con su posición.
     *
     * Los tres se funden con el mismo camino que antes, con un paso más: el texto y la curva
     * se pintan con `Compositor`, que es quien los dibuja en pantalla. Si se pintaran aquí con
     * su propia cuenta, cualquier diferencia entre las dos formas de dibujar se vería justo
     * al clonar: el texto salía corrido o la curva con otro grosor.
     */
    private fun bakePlacedForPixelEdit(
        layer: PaintLayer,
        apuntarHistorial: Boolean = true,
        incluirTransformCapa: Boolean = false
    ) {
        val placed = layer.placed
        val hayTextos = layer.texts.isNotEmpty()
        val hayCurvas = layer.curves.isNotEmpty()
        val transform = layer.transform
        val horneaCapa = incluirTransformCapa && !transform.esIdentity
        if (placed == null && !hayTextos && !hayCurvas && !horneaCapa) return
        if (apuntarHistorial) pushStructuralUndo()
        val destino = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(destino)
        // Se dibuja sin opacidad: la de la capa sigue siendo la de la vista, no algo que
        // haya que cocinar en los píxeles.
        opaquePaint.alpha = 255
        // La transformación de la capa solo se hornea cuando se lo pide. Al recortar la hoja,
        // que es lo que va justo con un rectángulo del papel, sí hace falta: si no, los
        // píxeles se copiarían en el sitio viejo y la capa se vería corrida respecto a como
        // estaba, porque el recorte de la hoja pasa por las coordenadas del papel.
        if (horneaCapa) {
            canvas.save()
            canvas.translate(transform.offsetX, transform.offsetY)
            canvas.scale(transform.scale, transform.scale)
            canvas.drawBitmap(layer.bitmap, 0f, 0f, opaquePaint)
            canvas.restore()
        } else {
            canvas.drawBitmap(layer.bitmap, 0f, 0f, opaquePaint)
        }
        placed?.let { imagen ->
            canvas.save()
            canvas.translate(imagen.offsetX, imagen.offsetY)
            canvas.scale(imagen.scale, imagen.scale)
            canvas.drawBitmap(imagen.bitmap, 0f, 0f, opaquePaint)
            canvas.restore()
        }
        // En el mismo orden en que se ven, para que lo horneado se parezca a lo que había.
        layer.texts.forEach { TextRenderer.draw(canvas, it) }
        layer.curves.forEach { CurveEngine.draw(canvas, it) }
        layer.bitmap = destino
        layer.placed = null
        layer.texts.clear()
        layer.curves.clear()
        // Y la transformación se queda en su sitio, porque ya está cocida en los píxeles. Sin
        // este reset, la capa se dibujaría dos veces corrida: una ya en su sitio y otra
        // desplazada por la transformación que sigue puesta.
        if (horneaCapa) layer.transform = LayerTransform()
    }

    /**
     * Completa una capa que quedó más chica que la hoja para que el pincel tenga dónde
     * pintar en toda la hoja.
     *
     * Recortar a la selección deja la capa con el tamaño del recorte. Como el lienzo se mide
     * contra el bitmap de la capa, una capa así solo cubre su trozo: lo que se pinta fuera se
     * recorta al bitmap y desaparece sin avisar. Ajustarla antes de editar devuelve la regla
     * del documento —todas las capas miden lo mismo—: la capa pasa a ser una hoja entera con
     * lo que ya tenía encima y el resto en blanco. La imagen puesta **no** se funde: se sigue
     * dibujando por encima como objeto movible.
     *
     * Solo la capa de fondo se rellena con el papel: las de arriba se expanden con
     * transparente, o el trozo nuevo aparecería como un rectángulo opaco tapando las capas de
     * abajo. Como la de fondo ya es una hoja blanca completa, el resultado se ve igual.
     *
     * No se recicla el bitmap viejo porque el historial puede estar apuntando a él.
     */
    private fun ensureLayerCoversSheet(layer: PaintLayer) {
        val needs = LayerStack.needsSheetExpansion(
            layer.bitmap.width, layer.bitmap.height, canvasWidth, canvasHeight
        )
        if (!needs) return
        val fondo = if (activeLayerIndex == 0) CANVAS_BACKGROUND else AndroidColor.TRANSPARENT
        LayerStack.ensureSheetSize(layer, canvasWidth, canvasHeight, fondo)
        compositeBitmap = null
        revision++
    }

    /**
     * Avisa cuando la capa ha quedado de un solo color plano.
     *
     * El relleno y la varita eligen una región comparando el color del punto tocado con el de
     * sus vecinos. Si la capa es de un color único no hay nada que comparar, la región sale
     * entera y la herramienta "rellena" o "recorta" los dos lienzos sin discriminar nada, que
     * es justo lo que se le reprocha cuando pasa. El relleno es correcto en ese caso, pero
     * surprising: mejor decirlo que dejar que parezca que la herramienta funciona mal.
     *
     * Se comprueba con una rejilla de muestras y no píxel a píxel porque es una comprobación
     * de diagnóstico, y leer un lienzo entero en el hilo de la interfaz por un aviso sale
     * mucho más caro de lo que aporta.
     */
    private fun avisarSiSeComioElLienzo(bitmap: Bitmap, herramienta: PaintTool) {
        val ancho = bitmap.width
        val alto = bitmap.height
        if (ancho < 2 || alto < 2) return

        val columnas = 24
        val filas = 24
        var referencia = 0
        var primera = true
        for (f in 0 until filas) {
            val y = f * (alto - 1) / (filas - 1)
            for (c in 0 until columnas) {
                val x = c * (ancho - 1) / (columnas - 1)
                val color = bitmap.getPixel(x, y)
                if (primera) {
                    referencia = color
                    primera = false
                } else if (color != referencia) {
                    return
                }
            }
        }
        // El mensaje nombra la herramienta porque el mismo aviso sale de las dos y, puesto
        // al revés, el bote de pintura se anunciaba como si fuera la varita.
        avisar(
            "La capa es de un solo color: ${herramienta.label.lowercase()} no tenía nada que distinguir"
        )
    }

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
        // Al salir de la mano de mover no queda nada que editar con otra herramienta, y dejar
        // el marco puesto encima del lienzo solo estorba. Con la mano puesta se conserva.
        if (tool != PaintTool.MOVE_OBJECT) seleccionar()
        // Cambiar de herramienta es siempre la manera de salir del atasco que dejó un aviso,
        // así que el aviso se va con ella en vez de quedarse diciendo algo que ya no importa.
        limpiarAviso()
    }

    fun selectColor(colorArgb: Int) {
        currentColor = colorArgb
    }

    // El nombre JVM difiere porque `strokeWidth` genera su propio setStrokeWidth(F)V.
    @JvmName("applyStrokeWidth")
    fun setStrokeWidth(width: Float) {
        val acotado = width.coerceIn(2f, 60f)
        strokeWidth = acotado
        // El ancho es el mismo ajuste para las figuras, el aerógrafo y el pincel, así que
        // se guarda una sola vez.
        brushSettings = brushSettings.copy(size = acotado)
    }

    // ---- Ajustes del pincel -------------------------------------------------

    /**
     * Cómo está configurado el pincel: tipo de punta y sus ajustes.
     *
     * El tamaño no vive aquí sino en [strokeWidth], que es el ancho compartido con las
     * figuras y el aerógrafo. Duplicarlo daría dos valores que se desincronizan: se
     * cambiaría el tamaño con el deslizador y el pincel seguiría con el tamaño viejo, que
     * es el fallo más difícil de ver de todos.
     */
    var brushSettings by mutableStateOf(BrushSettings())
        private set

    /** Los ajustes tal como se usan al pintar, con el ancho compartido ya puesto. */
    val pincelActivo: BrushSettings
        get() = brushSettings.copy(size = strokeWidth)

    fun setBrushKind(kind: BrushKind) {
        // Cada tipo entra con unos ajustes que le sirven, porque en un pincel suave la
        // intensidad al 100% con caudal al 100% sale un borón negro y el usuario cree que
        // está roto. Se cambian solo la primera vez que se elige el tipo; si vuelve a elegir
        // el mismo tipo, sus ajustes se quedan como los dejó.
        brushSettings = when (kind) {
            BrushKind.DURO -> brushSettings.copy(kind = kind, dureza = 1f, flow = 1f)
            BrushKind.SUAVE -> brushSettings.copy(kind = kind, dureza = 0.35f, flow = 0.25f)
            BrushKind.DIFUMINADOR -> brushSettings.copy(kind = kind, dureza = 0.5f, flow = 1f)
            BrushKind.MARCADOR -> brushSettings.copy(kind = kind, dureza = 0.8f, flow = 0.6f)
            BrushKind.LAPIZ -> brushSettings.copy(kind = kind, dureza = 1f, flow = 0.8f)
        }
    }

    fun setBrushIntensidad(value: Float) {
        brushSettings = brushSettings.copy(intensidad = value)
    }

    fun setBrushFlow(value: Float) {
        brushSettings = brushSettings.copy(flow = value)
    }

    fun setBrushDureza(value: Float) {
        brushSettings = brushSettings.copy(dureza = value)
    }

    fun setBrushEspaciado(value: Float) {
        brushSettings = brushSettings.copy(espaciado = value)
    }

    /**
     * Cambia qué extiende el difuminador: lo que ya está pintado o el color elegido.
     *
     * Al cambiar de modo se olvida la posición del último toque. Es lo que hace que el
     * trazo siguiente arranque limpio: en arrastrar el primer toque solo marca dónde
     * empezar, y con un origen heredado del modo anterior el primer parche saldría pegado
     * donde terminó el trazo de antes, fuera de donde el dedo acaba de apoyarse.
     */
    fun setSmudgeMode(mode: SmudgeMode) {
        if (brushSettings.difuminado == mode) return
        brushSettings = brushSettings.copy(difuminado = mode)
        difuminadoUltimo = null
    }

    fun toggleShapeFilled() {
        shapeFilled = !shapeFilled
    }

    // ---- Historial (undo/redo) --------------------------------------------

    private fun pushHistory(entry: HistoryEntry) {
        history.push(entry)
        syncHistoryState()
    }

    /** Copia [canUndo] y [canRedo] desde la pila, que es quien sabe si hay algo que deshacer. */
    private fun syncHistoryState() {
        canUndo = history.canUndo
        canRedo = history.canRedo
    }

    /**
     * Copia la capa al historial antes de tocar sus píxeles y devuelve esa copia.
     *
     * Devolverla ahorra hacer una segunda copia cuando además hace falta confinesar a la
     * selección: el parche trabaja por píxeles y necesita el estado previo para deshacer.
     */
    private fun pushPixelUndo(layer: PaintLayer? = activeLayer()): Bitmap? {
        val objetivo = layer ?: return null
        val copia = objetivo.bitmap.editableCopy()
        pushHistory(HistoryEntry.PixelEdit(objetivo.id, copia))
        return copia
    }

    /**
     * Devuelve a su valor anterior todo lo que la última operación tocó fuera de la selección.
     *
     * Lo usan las herramientas que trabajan por píxeles (relleno y varita mágica) porque no
     * pasan por el lienzo recortado. Así también ellas quedan dentro de la selección, que
     * es lo que se espera al ver el marco.
     */
    private fun confineToSelection(bitmap: Bitmap, before: Bitmap) {
        if (selection is Selection.All) return
        val width = bitmap.width
        val height = bitmap.height
        // Si la copia de antes no mide lo mismo, no hay nada que devolver a su sitio: se
        // sale antes de leer, porque `getPixels` con un origen de otra altura escribe
        // desplazado.
        if (before.width != width || before.height != height) return
        val total = width * height
        // Los búferes se reservan una vez y se reutilizan. Esto corre en cada evento de
        // movimiento del dedo, y en una hoja de dos megapíxeles son 8 MB por búfer:
        // reservarlos en cada llamada generaba más de 100 MB por segundo de basura, y el
        // trazo se nota a tirones por la recolección en mitad del gesto.
        val actual = confineBuffer?.takeIf { it.size == total }
            ?: IntArray(total).also { confineBuffer = it }
        val previo = confineOriginBuffer?.takeIf { it.size == total }
            ?: IntArray(total).also { confineOriginBuffer = it }
        bitmap.getPixels(actual, 0, width, 0, 0, width, height)
        before.getPixels(previo, 0, width, 0, 0, width, height)
        val devueltos = SelectionEngine.restoreOutside(actual, previo, selection, width, height)
        if (devueltos == 0) return
        bitmap.setPixels(actual, 0, width, 0, 0, width, height)
    }

    /** Libera los búferes de [confineToSelection] cuando la hoja cambia de tamaño. */
    private fun soltarConfineBuffers() {
        confineBuffer = null
        confineOriginBuffer = null
    }

    private var confineBuffer: IntArray? = null
    private var confineOriginBuffer: IntArray? = null

    private fun pushStructuralUndo() {
        pushHistory(
            HistoryEntry.StructuralEdit(
                layers = snapshotLayers(),
                activeIndex = activeLayerIndex,
                // El tamaño de la hoja va con el punto: si se cambia y se deshace, las capas
                // vuelven a su tamaño pero la hoja se quedaría en el nuevo, y el dibujo
                // aparecería pegado en una esquina. Lo que la UI promete es que se deshace.
                width = canvasWidth,
                height = canvasHeight
            )
        )
    }

    /**
     * Copia el estado de las capas para poder volver a él.
     *
     * Se guardan los valores, no las capas: guardar la lista de [PaintLayer] no sirve de
     * nada, porque son objetos mutables y el historial estaría guardando justamente lo que
     * después se va a modificar, así que deshacer no devolvería nada. Del bitmap solo se
     * conserva la referencia, que no cambia: las ediciones de píxeles van aparte, en
     * [pushPixelUndo].
     */
    private fun snapshotLayers(): List<HistoryEntry.StructuralLayer> = layers.map { snapshotOf(it) }

    /**
     * Copia los valores de una capa, para poder volver a ellos.
     *
     * Los objetos van como datos planos, no como los objetos de la capa: el historial guarda
     * estados pasados, y si guardara el texto o la curva de verdad, deshacer un movimiento
     * dejaría el objeto real en la posición de antes, que es lo contrario de lo que tiene que
     * pasar. La medida del texto no se copia porque se vuelve a medir al restaurar.
     */
    private fun snapshotOf(layer: PaintLayer): HistoryEntry.StructuralLayer {
        val placed = layer.placed
        return HistoryEntry.StructuralLayer(
            id = layer.id,
            bitmap = layer.bitmap,
            name = layer.name,
            visible = layer.visible,
            opacity = layer.opacity,
            placed = placed?.let {
                HistoryEntry.StructuralPlaced(it.bitmap, it.offsetX, it.offsetY, it.scale, it.crop)
            },
            texts = layer.texts.map { structuralTextOf(it) },
            curves = layer.curves.map { structuralCurveOf(it) },
            transform = layer.transform
        )
    }

    /**
     * Copia plana de un texto. Hay dos listas de textos y curvas por capa, así que esto se
     * usa igual al guardar en el historial que al cargar un proyecto, y no puede quedar escrito
     * dos veces con lo mismo.
     */
    private fun structuralTextOf(texto: TextObject) = HistoryEntry.StructuralText(
        text = texto.text,
        offsetX = texto.offsetX,
        offsetY = texto.offsetY,
        baseSize = texto.baseSize,
        colorArgb = texto.colorArgb,
        typeface = texto.typeface,
        bold = texto.bold,
        italic = texto.italic,
        align = texto.align,
        lineSpacingFactor = texto.lineSpacingFactor,
        boxWidth = texto.boxWidth,
        scale = texto.scale,
        crop = texto.crop
    )

    /** Copia plana de una curva, nodos incluidos. Ver [structuralTextOf]. */
    private fun structuralCurveOf(curva: CurveObject) = HistoryEntry.StructuralCurve(
        nodes = curva.nodes.map { n ->
            HistoryEntry.StructuralNode(
                x = n.point.x,
                y = n.point.y,
                outHandleX = n.outHandle.x,
                outHandleY = n.outHandle.y,
                inHandleX = n.inHandle.x,
                inHandleY = n.inHandle.y,
                isCorner = n.isCorner
            )
        },
        colorArgb = curva.colorArgb,
        width = curva.width,
        scale = curva.scale,
        crop = curva.crop
    )

    /** Vuelve a dejar las capas y la hoja como estaban en el punto [snapshot]. */
    private fun restoreLayers(
        snapshot: List<HistoryEntry.StructuralLayer>,
        index: Int,
        width: Int = 0,
        height: Int = 0
    ) {
        layers.clear()
        snapshot.forEach { layers += toPaintLayer(it) }
        // Si la capa que estaba activa ya no existe, se cae a la primera que quede.
        activeLayerIndex = index.coerceIn(0, layers.lastIndex.coerceAtLeast(0))
        if (width > 0 && height > 0) {
            canvasWidth = width
            canvasHeight = height
        }
        // Un deshacer estructural puede devolver un juego de capas completamente distinto, y
        // con otro tamaño. Una selección viva ahí no corresponde con nada de lo que se ve.
        soltarSeleccionDesalineada()
        selectionOrigen = null
    }

    /** Reconstruye una capa a partir de lo que guardó el historial. */
    private fun toPaintLayer(guardada: HistoryEntry.StructuralLayer): PaintLayer {
        val restaurada = PaintLayer(
            id = guardada.id,
            bitmap = guardada.bitmap,
            name = guardada.name,
            visible = guardada.visible,
            opacity = guardada.opacity
        )
        restaurada.transform = guardada.transform
        val placed = guardada.placed
        if (placed != null) {
            restaurada.placed = PlacedImage(placed.bitmap, placed.offsetX, placed.offsetY, placed.scale)
            restaurada.placed?.crop = placed.crop
        }
        guardada.texts.forEach { restaurada.texts.add(textObjectOf(it)) }
        guardada.curves.forEach { restaurada.curves.add(curveObjectOf(it)) }
        return restaurada
    }

    /**
     * Vuelve a construir un texto guardado por el historial o por un proyecto.
     *
     * Cada llamada crea un texto nuevo: si dos estados del historial compartieran el mismo
     * objeto, deshacer uno movería el texto del otro, que es como se rompe el deshacer sin que
     * se note.
     */
    private fun textObjectOf(t: HistoryEntry.StructuralText): TextObject {
        val texto = TextObject(
            text = t.text,
            offsetX = t.offsetX,
            offsetY = t.offsetY,
            baseSize = t.baseSize,
            colorArgb = t.colorArgb,
            typeface = t.typeface,
            bold = t.bold,
            italic = t.italic,
            align = t.align,
            lineSpacingFactor = t.lineSpacingFactor,
            boxWidth = t.boxWidth,
            scale = t.scale
        )
        texto.crop = t.crop
        val medida = TextRenderer.measure(texto)
        texto.setMeasured(medida.width, medida.height)
        return texto
    }

    /** Vuelve a construir una curva guardada. Ver [textObjectOf] sobre el reparto de copias. */
    private fun curveObjectOf(c: HistoryEntry.StructuralCurve): CurveObject {
        val curva = CurveObject(
        nodes = c.nodes.map { n ->
            CurveNode(
                point = Offset(n.x, n.y),
                outHandle = Offset(n.outHandleX, n.outHandleY),
                inHandle = Offset(n.inHandleX, n.inHandleY),
                isCorner = n.isCorner
            )
        }.toMutableList(),
            colorArgb = c.colorArgb,
            width = c.width,
            scale = c.scale
        )
        curva.crop = c.crop
        return curva
    }

    private fun currentStructuralEdit() = HistoryEntry.StructuralEdit(
        layers = snapshotLayers(),
        activeIndex = activeLayerIndex,
        width = canvasWidth,
        height = canvasHeight
    )

    /**
     * Aplica una entrada del historial al dibujo y devuelve la entrada que la deshace.
     *
     * La pila no sabe qué significa cada tipo de entrada, así que eso vive acá. Lo que
     * devuelve `null` es una entrada que ya no se puede aplicar, por ejemplo un trazo sobre
     * una capa que después se borró: en ese caso la pila la descarta y sigue con el siguiente
     * punto, en vez de quedar trabada para siempre.
     */
    private fun applyHistoryEntry(entry: HistoryEntry, direction: HistoryDirection): HistoryEntry? {
        val inversa = when (entry) {
            is HistoryEntry.PixelEdit -> {
                val layer = layers.find { it.id == entry.layerId }
                if (layer == null) {
                    null
                } else {
                    // El historial se queda con una copia de lo que hay ahora, y la capa pasa
                    // a usar el bitmap guardado. Al revés que cuando se empuja un trazo.
                    val copia = HistoryEntry.PixelEdit(entry.layerId, layer.bitmap.editableCopy())
                    layer.bitmap = entry.bitmap
                    activeLayerIndex = layers.indexOf(layer).coerceAtLeast(0)
                    copia
                }
            }
            is HistoryEntry.StructuralEdit -> {
                val actual = currentStructuralEdit()
                restoreLayers(entry.layers, entry.activeIndex, entry.width, entry.height)
                actual
            }
            is HistoryEntry.MergeEdit -> applyMergeEntry(entry, direction)
        }
        compositeBitmap = null
        revision++
        return inversa
    }

    /**
     * Combinar y separar capas no son el mismo paso al revés, así que acá se separa según la
     * dirección: deshacer vuelve a meter la capa de arriba y a devolver el bitmap viejo a la
     * de abajo; rehacer hace lo contrario.
     */
    private fun applyMergeEntry(entry: HistoryEntry.MergeEdit, direction: HistoryDirection): HistoryEntry? {
        val bottom = layers.find { it.id == entry.bottomLayerId } ?: return null
        return when (direction) {
            HistoryDirection.Undo -> {
                bottom.bitmap = entry.bottomBitmapBefore
                layers.add(entry.insertIndex, toPaintLayer(entry.topLayer))
                activeLayerIndex = entry.activeIndexBefore
                entry.copy(forward = true)
            }
            HistoryDirection.Redo -> {
                bottom.bitmap = entry.bottomBitmapAfter
                val idx = layers.indexOfFirst { it.id == entry.topLayer.id }
                if (idx >= 0) layers.removeAt(idx)
                activeLayerIndex = entry.activeIndexAfter
                entry.copy(forward = false)
            }
        }
    }

    fun undo() {
        if (history.undo()) syncHistoryState()
    }

    fun redo() {
        if (history.redo()) syncHistoryState()
    }

    fun clearCanvas() {
        val layer = activeLayer() ?: return
        val canvas = activeCanvas() ?: return
        // Vaciar respeta la selección, como todo lo demás que toca píxeles. Sin esto, con
        // una mancha de selección inteligente en pantalla y "vaciar" en el menú, la
        // sombreación prometía una zona y se borraba la capa entera.
        val antes = if (selection is Selection.All) null
        else layer.bitmap.copy(Bitmap.Config.ARGB_8888, false)
        pushPixelUndo(layer)
        if (activeLayerIndex == 0) {
            canvas.drawColor(CANVAS_BACKGROUND, PorterDuff.Mode.SRC)
        } else {
            canvas.drawColor(AndroidColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }
        if (antes != null) confineToSelection(layer.bitmap, antes)
        compositeBitmap = null
        revision++
    }

    /**
     * Vacía una capa cualquiera sin necesidad de seleccionarla antes. Se usa desde el botón
     * de cada fila del panel de capas, que antes no existía: para borrar el contenido de la
     * capa de abajo había que seleccionarla, abrir el menú de tres puntos y tocar "Limpiar
     * capa actual", y en el peor caso se limpiaba la que no era.
     */
    fun clearLayer(index: Int) {
        val layer = layers.getOrNull(index) ?: return
        if (layer.placed == null && isLayerEmpty(layer.bitmap)) return
        // Si la selección es de otra capa, este botón no la toca: se avisa, porque si no
        // quien la hizo vería su mancha de selección y luego una capa distinta vacía.
        if (!selectionEsDeEstaCapa(layer)) return
        val antes = if (selection is Selection.All) null else {
            layer.bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        pushPixelUndo(layer)
        LayerStack.clear(layer, index == 0, CANVAS_BACKGROUND)
        if (antes != null) confineToSelection(layer.bitmap, antes)
        layer.placed = null
        compositeBitmap = null
        revision++
    }

    /**
     * Quita las franjas vacías de los bordes de la capa sin cambiar su tamaño: lo que
     * queda conserva su sitio en la hoja. La geometría va en [CropEngine].
     */
    fun cropLayerToContent(index: Int) {
        val layer = layers.getOrNull(index) ?: return
        if (layer.placed != null) {
            avisar("Quita primero la imagen puesta para recortar la capa")
            return
        }
        if (isLayerEmpty(layer.bitmap)) {
            avisar("La capa está vacía, no hay nada que recortar")
            return
        }
        // El recorte de bordes escribe sobre la capa en sitio, así que el punto de deshacer
        // tiene que guardarla ANTES. Y como no vale empujarlo a ciegas (un recorte que no
        // recorta dejaría un paso que al pulsarlo no cambia nada), antes se pregunta si de
        // verdad hay franjas que quitar.
        if (!CropEngine.needsTrim(layer.bitmap)) {
            avisar("Esta capa ya ocupa toda la hoja")
            return
        }
        pushPixelUndo(layer)
        CropEngine.trimTransparent(layer.bitmap)
        compositeBitmap = null
        revision++
    }

    /** Recorta la capa que se está editando quitándole las franjas vacías. */
    fun cropActiveLayerToContent() = cropLayerToContent(activeLayerIndex)

    private fun isLayerEmpty(bitmap: Bitmap): Boolean =
        LayerStack.isEmpty(pixelsOf(bitmap), bitmap.width, bitmap.height)

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
        activeLayerIndex = LayerStack.activeIndexAfterRemove(activeLayerIndex, index, layers.size)
        compositeBitmap = null
        revision++
    }

    fun selectLayer(index: Int) {
        if (index in layers.indices) {
            activeLayerIndex = index
            // El objeto seleccionado era de la capa anterior: dejarlo marcado dibujaría su
            // marco encima de una capa donde ya no está.
            seleccionar()
        }
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
        // El nombre se limpia con la misma regla del manifiesto: los dos escriben en el
        // mismo archivo y un `|` en el nombre desalineaba todo lo que venía después.
        if (name.isNotBlank()) layer.name = ProjectManifest.safeName(name)
    }

    fun moveLayerUp(index: Int) {
        if (index !in layers.indices || index == layers.lastIndex) return
        pushStructuralUndo()
        layers.add(index + 1, layers.removeAt(index))
        activeLayerIndex = LayerStack.activeIndexAfterMoveUp(activeLayerIndex, index)
        compositeBitmap = null
        revision++
    }

    fun moveLayerDown(index: Int) {
        if (index !in layers.indices || index == 0) return
        pushStructuralUndo()
        layers.add(index - 1, layers.removeAt(index))
        activeLayerIndex = LayerStack.activeIndexAfterMoveDown(activeLayerIndex, index)
        compositeBitmap = null
        revision++
    }

    /**
     * Combina la capa `index` con la que tiene debajo, respetando su opacidad. Deshacer atómico.
     *
     * La mezcla de píxeles la decide [LayerStack]; la baja de la capa y el punto de deshacer
     * se hacen aquí, porque dependen del historial y de cuál estaba activa.
     */
    fun mergeDown(index: Int) {
        if (index <= 0 || index !in layers.indices) return
        val bottom = LayerStack.mergeDownInto(layers, index) ?: return
        val top = layers[index]
        val before = bottom.bitmap.editableCopy()
        val canvas = Canvas(bottom.bitmap)
        // Se baja el contenido de la capa tal como se ve, imagen puesta incluida: si solo se
        // copiaba lo pintado, al combinar una capa con foto la foto se perdía.
        //
        // Y se dibuja en el espacio de la capa de abajo, que no es el de la hoja si esa capa
        // está movida o estirada. Con la transformación de abajo puesta al revés, lo que se
        // mezcla cae en el sitio correcto de su bitmap en vez de corrido un dedo, y las dos
        // capas siguen teniendo sus objetos como objetos.
        val transformAbajo = bottom.transform
        if (!transformAbajo.esIdentity && transformAbajo.scale > 0f) {
            canvas.save()
            canvas.translate(-transformAbajo.offsetX, -transformAbajo.offsetY)
            canvas.scale(1f / transformAbajo.scale, 1f / transformAbajo.scale)
            Compositor.drawLayerContent(canvas, top, layerCompositePaint)
            canvas.restore()
        } else {
            Compositor.drawLayerContent(canvas, top, layerCompositePaint)
        }
        val after = bottom.bitmap.editableCopy()
        val activeBefore = activeLayerIndex

        layers.removeAt(index)
        activeLayerIndex = index - 1

        pushHistory(
            HistoryEntry.MergeEdit(
                topLayer = snapshotOf(top),
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

    fun onStrokeStart(sheetPoint: Offset) {
        // Empezar a trabajar se lleva por delante cualquier aviso anterior: el usuario ya ha
        // le�do lo que faltaba, y si pinta igual, dejarlo puesto un momento m�s solo le
        // come alto del panel.
        limpiarAviso()
        // El dedo va en coordenadas de la hoja y el pincel pinta en las del contenido de la
        // capa activa, que son distintas en cuanto la capa está movida o estirada. Todas las
        // herramientas de abajo trabajan ya en coordenadas de contenido, y la zona de
        // selección también, porque se dibuja con el mismo dedo.
        val point = aContenido(sheetPoint)
        // Las herramientas de selección se atienden antes de pedir el lienzo: no pintan, y
        // así funcionan también con la capa oculta o sin capas.
        if (ToolPolicy.isSelection(currentTool)) {
            if (currentTool == PaintTool.SELECTION) startSelection(point)
            return
        }
        val canvas = activeCanvas() ?: return
        when (currentTool) {
            PaintTool.BRUSH, PaintTool.ERASER, PaintTool.SPRAY -> {
                // El difuminador en modo arrastrar no pinta en el primer toque: solo fija de
                // dónde se tira. Su deshacer se deja en pendiente y se cumple cuando de
                // verdad escribe algo, así un toque suelto no gasta un paso que no deshace.
                difuminadoPendienteUndo = false
                val arrastra = pincelActivo.kind == BrushKind.DIFUMINADOR &&
                    pincelActivo.difuminado == SmudgeMode.ARRASTRAR
                if (arrastra) {
                    difuminadoPendienteUndo = true
                } else {
                    pushPixelUndo()
                }
                lastPoint = point
                // El difuminador arrastra desde el último toque, así que el trazo nuevo
                // arranca sin ninguno: si no, tiraría de lo que quedó del trazo anterior.
                difuminadoUltimo = null
                // El punto inicial se pinta con la pintura que devuelve la configuración, no
                // con la del pincel: antes usaba siempre `strokePaint`, así que el borrador
                // dejaba un punto del color elegido en cada toque y en un toque suelto (sin
                // arrastrar) no borraba nada, solo pintaba. Parecía un pincel.
                if (currentTool == PaintTool.SPRAY) {
                    sprayAt(canvas, point)
                } else {
                    // Se estampa y no se dibuja un punto: un `drawPoint` sale del tamaño del
                    // pincel pero con el borde duro y sin el degradado, así que un toque suelto
                    // con el pincel suave se veía como un grano en vez de como una mancha.
                    pintarTramo(canvas, point.x, point.y, point.x, point.y)
                }
                compositeBitmap = null
                revision++
            }
            PaintTool.LINE, PaintTool.RECTANGLE, PaintTool.CIRCLE -> {
                shapeStart = point
                shapeCurrent = point
            }
            PaintTool.CURVE -> startCurve(point)
            PaintTool.CLONE -> {
                // Sin fuente elegida, el primer toque la define y no pinta todavía.
                val layer = activeLayer() ?: return
                if (!clone.hasSource) {
                    // Si la capa es una foto suelta, se funde en píxeles para poder clonarla.
                    bakePlacedForPixelEdit(layer)
                    clone.markSourceOnly(point.x, point.y)
                } else {
                    // Si la capa tiene algo puesto encima, se funde aquí: sin esto el sello
                    // copiaría los píxeles de debajo, casi siempre nada, en vez de lo que se ve
                    // a través del marco de mover. Se mira cualquiera de los tres, no solo la
                    // foto: un texto estampado salía como un rectángulo vacío, porque el sello
                    // leía el fondo y el texto se quedaba flotando por encima del resultado.
                    // Con varios textos y curvas ya no se pregunta por uno: se pregunta si hay.
                    if (layer.objects().isNotEmpty()) {
                        bakePlacedForPixelEdit(layer)
                    }
                    // La fuente queda fijada con el desplazamiento que tiene respecto del
                    // dedo: es lo que permite clonar corrido sin que la copia se corra.
                    if (!clone.beginStamp(point.x, point.y)) return
                    val origen = clone.sourceFor(point.x, point.y) ?: return
                    // La copia del estado previo se deja para después de saber que de verdad
                    // se estampa: si la fuente no existe, no queda un paso que no deshace.
                    pushPixelUndo()
                    // El lienzo se vuelve a tomar después de fundir: apunta al bitmap nuevo
                    // y, como en el resto del trazo, lleva el recorte de la selección.
                    val canvasTrasFundir = activeCanvas() ?: return
                    copyAt(canvasTrasFundir, point, Offset(origen.first, origen.second))
                    lastPoint = point
                    compositeBitmap = null
                    revision++
                }
            }
            PaintTool.PATCH -> {
                if (startPatch(point)) {
                    compositeBitmap = null
                    revision++
                }
            }
            PaintTool.MOVE_IMAGE -> startMovingImage(point)
            PaintTool.MOVE_OBJECT -> startMovingObject(point)
            PaintTool.MOVE_LAYER -> {
                // La capa se mueve en píxeles de la hoja, no de su contenido: el dedo tiene
                // que moverla lo mismo que se ve, y no más o menos según la escala que tenga
                // puesta. Con una capa al 200 %, usar su propio espacio la dejaría yendo a
                // media velocidad.
                moveFrom = sheetPoint
                moveUndo.rearm()
            }
            else -> Unit
        }
    }

    fun onStrokeMove(sheetPoint: Offset) {
        val point = aContenido(sheetPoint)
        if (ToolPolicy.isSelection(currentTool)) {
            if (currentTool == PaintTool.SELECTION) updateSelection(point)
            return
        }
        val canvas = activeCanvas() ?: return
        when (currentTool) {
            PaintTool.BRUSH, PaintTool.ERASER -> {
                val from = lastPoint ?: point
                pintarTramo(canvas, from.x, from.y, point.x, point.y)
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
            PaintTool.CURVE -> moveCurve(point)
            PaintTool.CLONE -> {
                // El contenido se copia desde la fuente corrida con el mismo desplazamiento
                // que hizo el dedo, así el clon sale corrido y no se repite el último punto.
                val origen = clone.sourceFor(point.x, point.y) ?: return
                copyAt(canvas, point, Offset(origen.first, origen.second))
                lastPoint = point
                compositeBitmap = null
                revision++
            }
            PaintTool.PATCH -> {
                // El parche escribe píxeles sueltos, así que no pasa por el recorte del
                // lienzo: `patchAt` confinesa aparte con la copia que dejó el historial.
                if (patchAt(point)) {
                    compositeBitmap = null
                    revision++
                }
                lastPoint = point
            }
            PaintTool.MOVE_IMAGE -> moveImage(point)
            PaintTool.MOVE_OBJECT -> moveObject(point)
            PaintTool.MOVE_LAYER -> {
                // El arrastre va en píxeles de la hoja, con los que se guarda [moveFrom], para
                // que la capa siga al dedo tal cual se ve.
                val desde = moveFrom ?: return
                moverCapa(sheetPoint.x - desde.x, sheetPoint.y - desde.y)
            }
            else -> Unit
        }
    }

    fun onStrokeEnd(sheetPoint: Offset) {
        val point = aContenido(sheetPoint)
        if (ToolPolicy.isSelection(currentTool)) {
            if (currentTool == PaintTool.SELECTION) endSelection()
            return
        }
        val canvas = activeCanvas() ?: return
        when (currentTool) {
            PaintTool.BRUSH, PaintTool.ERASER, PaintTool.SPRAY -> {
                lastPoint = null
                // Un trazo que se quedó en un toque dejó el deshacer del difuminador en
                // pendiente sin escribir nada: se anula para que el próximo trazo arranque
                // limpio.
                difuminadoPendienteUndo = false
            }
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
            PaintTool.CURVE -> endCurve()
            PaintTool.CLONE -> {
                lastPoint = null
                clone.endStroke()
            }
            PaintTool.PATCH -> {
                lastPoint = null
                // La copia del historial se queda guardada ahí; aquí solo se suelta
                // la referencia para no tenerla en memoria todo el rato.
                patchBefore = null
            }
            PaintTool.MOVE_IMAGE -> endMovingImage()
            PaintTool.MOVE_OBJECT -> endMovingObject()
            PaintTool.MOVE_LAYER -> {
                moveFrom = null
                moveUndo.rearm()
            }
            else -> Unit
        }
    }

    // ---- Curva ------------------------------------------------------------

    /**
     * Puntos por los que ha pasado el dedo con la herramienta Curva.
     *
     * Se guardan en crudo, sin muestrear, y el muestreo lo hace el motor al soltar: durante el
     * trazo lo que se ve es la línea por la que va el dedo, y decidir el espaciado en ese
     * momento era tirar puntos por un borde que después la curva no iba a seguir igual.
     */
    private val curvePoints = mutableListOf<Offset>()

    /**
     * Puntos del trazo en curso, para la vista previa.
     *
     * Es una copia de [curvePoints] leída desde la pantalla. Se publica con cada movimiento
     * para que el `Canvas` se repinte: si se leyera la lista directamente, la previsualización
     * solo aparecería en el siguiente cambio de estado, y se vería a tirones.
     */
    var curvePointsInProgress by mutableStateOf<List<Offset>>(emptyList())
        private set

    private fun startCurve(point: Offset) {
        curvePoints.clear()
        curvePoints += point
        curvePointsInProgress = curvePoints.toList()
    }

    private fun moveCurve(point: Offset) {
        if (curvePoints.isEmpty()) return
        // El último punto se sustituye en vez de acumular: es el mismo sitio por el que va
        // el dedo, y dejarlo dos veces solo engorda la lista.
        if (curvePoints.last() != point) {
            curvePoints += point
            curvePointsInProgress = curvePoints.toList()
        }
    }

    /**
     * Al soltar, convierte el trazo en una curva y la deja en la capa.
     *
     * El deshacer es estructural y no de píxeles porque la curva no se pinta: es un objeto, y
     * lo que se deshace es su posición y sus nodos.
     *
     * Un trazo de un solo punto no deja curva. Es lo mismo que pasa con un clic suelto en
     * cualquier otra herramienta: no hay línea que guardar y no se gasta un paso de deshacer
     * que no deshace nada.
     */
    private fun endCurve() {
        val layer = activeLayer()
        val puntos = curvePoints.toList()
        curvePoints.clear()
        curvePointsInProgress = emptyList()
        if (layer == null) return
        val curva = CurveEngine.fromStroke(puntos)
        if (!curva.isDrawable) return
        pushStructuralUndo()
        // El grosor sale del mismo sitio que el de las figuras, y por eso la curva responde al
        // deslizador de grosor igual que la línea. Sin configurarla aquí, salía con el valor
        // que hubiera quedado de la última figura dibujada, o con el grosor por defecto.
        configureShapePaint()
        curva.colorArgb = if (currentTool == PaintTool.ERASER) CANVAS_BACKGROUND else currentColor
        curva.width = shapePaint.strokeWidth.takeIf { it > 0f } ?: 8f
        // Se añade a las que ya hay en vez de sustituir a la última. Con una sola curva por
        // capa, cada trazo se comía el anterior sin que se pudiera volver atrás más de un
        // paso, que era como si el trazo se perdiera.
        layer.curves.add(curva)
        compositeBitmap = null
        revision++
    }

    // ---- Clonado, parche y mover imagen -----------------------------------

    /**
     * Estado del sello de clonar. Antes eran tres variables sueltas repartidas por el
     * ViewModel, y lo que mantenía el clon corrido en vez de saltarse era tener que
     * relacionarlas bien en cada movimiento.
     */
    private val clone = CloneStamp()

    /**
     * Distancia vertical de la que el parche toma el contenido, en radios del pincel.
     *
     * Tiene que ser mayor que dos radios: si no, el círculo que se copia pisa la misma
     * mancha que se quiere tapar y no pasa nada. Al ser proporcional al pincel, agrandando
     * el pincel el parche alcanza manchas más grandes.
     */
    private val patchOffsetInRadii = 3f

    private fun brushRadius() = strokeWidth / 2f

    /**
     * Copia el contenido que hay en [source] y lo dibuja en [destino], recortado con un
     * círculo del tamaño del pincel. Es la base que usan el clonado y el parche.
     *
     * El canvas del bitmap ya recorta por los bordes de la capa, así que alcanza con
     * recortar el círculo del pincel.
     */
    private fun copyAt(canvas: Canvas, destino: Offset, source: Offset) {
        val layer = activeLayer() ?: return
        val radius = brushRadius()

        canvas.save()
        // El alfa lo deja la última composición, así que se fuerza opaco para que el
        // clonado y el parche copien tal cual.
        layerCompositePaint.alpha = 255
        layerCompositePaint.isFilterBitmap = false
        patchClip.reset()
        patchClip.addCircle(destino.x, destino.y, radius, android.graphics.Path.Direction.CW)
        canvas.clipPath(patchClip)
        copyMatrix.reset()
        copyMatrix.setTranslate(destino.x - source.x, destino.y - source.y)
        canvas.drawBitmap(layer.bitmap, copyMatrix, layerCompositePaint)
        canvas.restore()
    }

    /**
     * El parche tapa lo que hay abajo con lo que hay un poco más arriba. Sin configurar nada.
     *
     * @return si escribió algún píxel distinto, para que quien llama no invalide la vista ni
     *         cuente un paso de deshacer en lo que no cambió nada.
     */
    private fun patchAt(point: Offset): Boolean {
        val layer = activeLayer() ?: return false
        val bmp = layer.bitmap
        val radius = brushRadius()
        if (radius < 1f) return false

        val ventana = PatchEngine.windowAt(
            point.x.roundToInt(), point.y.roundToInt(), radius.roundToInt(), bmp.width, bmp.height
        ) ?: return false

        val actual = IntArray(ventana.width * ventana.height)
        bmp.getPixels(actual, 0, ventana.width, ventana.left, ventana.top, ventana.width, ventana.height)

        // Mismo cuadrado corrido hacia arriba: de ahí sale el contenido sano. La fila se baja
        // a un sitio que exista dentro de la hoja para no copiar de la nada.
        val donado = IntArray(ventana.width * ventana.height)
        val srcLeft = ventana.left.coerceIn(0, (bmp.width - ventana.width).coerceAtLeast(0))
        val sampleY = PatchEngine.sampleRow(
            point.y.roundToInt(), radius, ventana.height, bmp.height, patchOffsetInRadii
        )
        bmp.getPixels(donado, 0, ventana.width, srcLeft, sampleY, ventana.width, ventana.height)

        val salida = PatchEngine.patchWindow(actual, donado, ventana, point.x, point.y, radius)
        // Si el tramo copia lo mismo que había (típico sobre lo que ya está parcheado), no se
        // escribe ni se gasta un punto de deshacer en el que no deshace nada.
        if (salida.contentEquals(actual)) return false
        // Un solo punto por gesto, no por toque: la primera vez que de verdad se escribe se
        // guarda el estado previo, que sirve también para rellenar lo que caiga fuera de la
        // selección.
        if (patchUndo.pushOnce()) patchBefore = pushPixelUndo(layer)
        bmp.setPixels(salida, 0, ventana.width, ventana.left, ventana.top, ventana.width, ventana.height)
        val previo = patchBefore
        if (previo != null) confineToSelection(bmp, previo)
        return true
    }

    /** Copia del estado previo mientras dura un trazo de parche, para respetar la selección. */
    private var patchBefore: Bitmap? = null

    /** Un solo punto de deshacer por gesto de parche, no uno por toque. */
    private val patchUndo = GestureUndo()

    /**
     * Prepara un parche: deja la imagen puesta en la capa y asegura que la capa cubra la hoja.
     *
     * El parche se usa a toques sueltos, así que esto se llama tanto desde un toque como al
     * empezar a arrastrar. El estado previo y el confine a la selección los lleva [patchAt],
     * que es quien sabe si de verdad se escribió algo.
     */
    private fun startPatch(point: Offset): Boolean {
        val layer = activeLayer() ?: return false
        bakePlacedForPixelEdit(layer)
        ensureLayerCoversSheet(layer)
        patchUndo.rearm()
        lastPoint = point
        return patchAt(point)
    }

    // ---- Mover, estirar y corregir objetos de la capa ---------------------

    private var moveFrom: Offset? = null
    private var moveStartX = 0f
    private var moveStartY = 0f

    /**
     * Un solo punto de deshacer por gesto de mover o pellizcar, no uno por movimiento.
     *
     * Antes era un `Boolean` con la misma forma, pero el "solo una vez" estaba repartido en
     * los dos sitios que mueven la imagen. Aquí está en un sitio, y con un test que
     * comprueba que el segundo movimiento del mismo gesto no vuelve a empujar.
     */
    private val moveUndo = GestureUndo()

    // ---- Mano de mover objetos -------------------------------------------

    /**
     * Qué se está moviendo con la mano: qué objeto, de qué tipo y en qué modo.
     *
     * El objeto se guarda como [LayerObjectRef], que es una vista viva sobre el objeto real de
     * la capa, y no como un `PaintLayer` con su posición: mientras el dedo arrastra, el
     * compositor está repintando y vuelve a pedir la caja del texto, así que el estado de la
     * selección tiene que leer siempre del sitio bueno.
     */
    data class ObjectSelection(
        val ref: LayerObjectRef,
        val mode: ObjectDragMode,
        /** Tirador agarrado, solo en modo RESIZE. */
        val corner: ResizeCorner? = null,
        /** Nodo y tirador de la curva que se está corrigiendo, solo en modo HANDLE. */
        val node: CurveNode? = null,
        /** Si el tirador corregido es el de entrada del nodo. */
        val inHandle: Boolean = true
    )

    /** Qué objeto se está moviendo, o null si está quieto. */
    var movingObject by mutableStateOf<ObjectSelection?>(null)
        private set

    /** Radio de agarre de los tiradores, en píxeles de la hoja. */
    private val cornerRadius = 56f

    /** Radio de agarre de los nodos y tiradores de una curva, en píxeles de la hoja. */
    private val nodeRadius = 40f

    /**
     * Empieza un gesto de la mano sobre lo que hay bajo el dedo.
     *
     * La prioridad de lo que se agarra es la que se ve de arriba abajo: primero los tiradores
     * de la curva, porque son lo más pequeño; luego los tiradores de la caja; y por último el
     * objeto entero, que es la presa más grande. Un tirador que no se puede tomar nunca por no
     * estar lo bastante cerca se pierde para siempre si el objeto se encoge, así que el radio
     * no depende del tamaño de la caja.
     *
     * Tocar en un sitio sin objeto suelta la selección. Es lo mismo que hace el toque suelto
     * en [onTap], y está también en el arranque porque la Selección llega por los gestos de
     * arrastre, no por el de toque: si el dedo se posa y se levanta sin moverse, no hay
     * `onTap`, y sin esto el marco se quedaría pegado a un objeto en el que ya no se está
     * trabajando.
     */
    fun startMovingObject(point: Offset) {
        val layer = activeLayer()
        if (layer == null) {
            movingObject = null
            return
        }
        // Los tiradores y los nodos se buscan en todas las curvas de la capa, empezando por la
        // última, que es la que está más arriba. Con una sola curva no había nada que elegir;
        // ahora hay que elegir la curva correcta y no vale mirar solo una.
        //
        // El orden de qué se agarra es de lo más pequeño a lo más grande: tirador, nodo, tirador
        // de la caja y por último el objeto entero. Los nodos van antes que las esquinas a
        // propósito, porque un nodo de un extremo cae justo encima de una esquina de la caja:
        // con las esquinas primero, los dos nodos de las puntas de una curva no se podrían
        // colocar nunca, que es justo lo que hace falta para enderezarla.
        for (curva in layer.curves.asReversed()) {
            val tirador = curva.handleNear(point, nodeRadius)
            if (tirador != null) {
                if (moveUndo.pushOnce()) pushStructuralUndo()
                movingObject = ObjectSelection(
                    ref = CurveObjectRef(curva),
                    mode = ObjectDragMode.HANDLE,
                    node = tirador.first,
                    inHandle = tirador.second
                )
                return
            }
        }
        for (curva in layer.curves.asReversed()) {
            val nodo = curva.nodeNear(point, nodeRadius)
            if (nodo != null) {
                if (moveUndo.pushOnce()) pushStructuralUndo()
                movingObject = ObjectSelection(
                    ref = CurveObjectRef(curva),
                    mode = ObjectDragMode.NODE,
                    node = nodo
                )
                return
            }
        }

        val objeto = layer.objectAt(point.x, point.y)
        if (objeto == null) {
            seleccionar()
            return
        }
        val caja = objeto.bounds()
        val esquina = ObjectResize.cornerNear(caja, point.x, point.y, cornerRadius)
        if (esquina != null) {
            if (moveUndo.pushOnce()) pushStructuralUndo()
            movingObject = ObjectSelection(
                ref = objeto,
                mode = ObjectDragMode.RESIZE,
                corner = esquina
            )
            return
        }

        if (moveUndo.pushOnce()) pushStructuralUndo()
        moveFrom = point
        movingObject = ObjectSelection(ref = objeto, mode = ObjectDragMode.MOVE)
    }

    /**
     * Sigue el gesto de la mano con el punto donde está el dedo.
     *
     * Cada rama vuelve a leer la caja del objeto antes de trabajar: durante el arrastre el
     * compositor la ha vuelto a medir, y usar la medida del gesto anterior haría que el tirador
     * se fuera de la letra a medida que el objeto crece.
     */
    fun moveObject(point: Offset) {
        val sel = movingObject ?: return
        val capa = sel.ref
        when (sel.mode) {
            ObjectDragMode.MOVE -> {
                val desde = moveFrom ?: return
                val deltaX = point.x - desde.x
                val deltaY = point.y - desde.y
                if (deltaX == 0f && deltaY == 0f) return
                capa.moveBy(deltaX, deltaY)
                // El ancla se corre con el objeto: si se quedara en el punto donde empezó el
                // gesto, cada movimiento sumaría el desplazamiento entero otra vez y el objeto
                // se iría disparando cada vez más lejos del dedo.
                moveFrom = point
                compositeBitmap = null
                revision++
            }
            ObjectDragMode.RESIZE -> {
                val esquina = sel.corner ?: return
                val antes = capa.bounds()
                val despues = ObjectResize.resize(antes, esquina, point.x, point.y)
                val actual = capa.currentScale()
                val deseada = capa.scaleCoherente(actual * ObjectResize.factor(antes, despues))
                if (deseada != actual) {
                    val (x, y) = ObjectResize.placementFor(antes, esquina, deseada)
                    capa.applyScale(deseada)
                    capa.moveTo(x, y)
                }
                compositeBitmap = null
                revision++
            }
            ObjectDragMode.HANDLE -> {
                val nodo = sel.node ?: return
                if (sel.inHandle) nodo.arrastrarEntrada(point) else nodo.arrastrarSalida(point)
                compositeBitmap = null
                revision++
            }
            ObjectDragMode.NODE -> {
                val nodo = sel.node ?: return
                // Se mueve el nodo con sus dos tiradores pegados, en vez de dejar los
                // tiradores atrás: si no, recolocar un nodo estiraría el tramo de curva que
                // entra y sale de él, que es justo el efecto contrario al buscado.
                nodo.moverA(point)
                compositeBitmap = null
                revision++
            }
            ObjectDragMode.IDLE -> Unit
        }
    }

    /**
     * Cierra el gesto de la mano, dejando el objeto donde quedó.
     *
     * La selección **no** se suelta aquí: el objeto se queda marcado y con su marco, sus nodos
     * y el panel de acciones. Antes sí se soltaba, y como el marco y el panel solo se dibujan
     * cuando hay selección, los dos solo existían mientras el dedo estaba apoyado. Eso dejaba
     * dos cosas sin hacer: corregir dos nodos seguidos obligaba a volver a buscar la curva con
     * el dedo cada vez, y el panel con la fuente y el tamaño del texto, que es donde se
     * cambia eso, solo se podía ver arrastrando.
     *
     * La selección se suelta en [startMovingObject] al tocar en vacío, en [onTap] cuando no hay
     * objeto debajo y en [seleccionar], que es lo que hace al cambiar de herramienta o de capa.
     */
    fun endMovingObject() {
        moveFrom = null
        moveUndo.rearm()
        // El gesto se acaba y el modo pasa a reposo, pero la selección sigue: el marco y los
        // tiradores se quedan para poder agarrarlos otra vez sin volver a buscar el objeto.
        movingObject = movingObject?.copy(mode = ObjectDragMode.IDLE)
        compositeBitmap = null
        revision++
    }

    /** Suelta la selección: sin marco, sin panel y sin nada que editar. */
    fun seleccionar() {
        movingObject = null
        moveFrom = null
        // Un recorte a medias se cae con la selección: el objeto se queda como estaba y la
        // zona se vuelve a usar para lo que sea. Dejarlo pendiente convertiría el siguiente
        // marco que se haga, aunque sea para otra cosa, en un recorte de un objeto que ya no
        // está en pantalla.
        recortePendiente = null
    }

    /**
     * Objeto al que se le va a recortar, esperando a que el usuario marque la zona, o null.
     *
     * Va aparte de [selectedObject] porque para recortar hay que cambiar a la herramienta de
     * marco, y al cambiarla se suelta la selección del objeto. Sin guardarlo aquí, el recorte
     * no sabría a quién aplicárselo justo en el gesto que lo termina.
     */
    var recortePendiente by mutableStateOf<LayerObjectRef?>(null)
        private set

    /**
     * Empieza a recortar el objeto seleccionado: pasa al marco y espera a que se marque la zona.
     *
     * Es el recorte de la imagen, del texto o de la curva, que recorta el objeto y no la capa.
     * La diferencia no es un matiz: con una imagen puesta, recortar la capa hornea antes la
     * imagen en los píxeles y el recorte se lleva por delante también lo pintado, y lo que se
     * conserva es un trozo de una foto que ya no se puede volver a colocar ni a enderezar.
     */
    fun iniciarRecorteDeObjeto() {
        val ref = movingObject?.ref
        if (ref == null) {
            avisar("Primero toma el objeto que quieres recortar")
            return
        }
        // Primero la herramienta y después la referencia guardada. Al revés, `selectTool`
        // suelta la selección del objeto y con ella se cae lo pendiente de recortar, y el
        // gesto siguiente recortaría sin saber a qué.
        selectTool(PaintTool.SELECTION)
        recortePendiente = ref
    }

    /** Cancela el recorte a medias y deja el objeto como estaba. */
    fun cancelarRecorteDeObjeto() {
        recortePendiente = null
        clearSelection()
    }

    /**
     * Le deja a [ref] solo la zona [rect], en coordenadas de la hoja.
     *
     * El recorte se acota a la caja del objeto y se guarda en el propio objeto, no en la capa:
     * así el resto de la capa sigue igual, la imagen se puede volver a ampliar sin haber
     * perdido nada, y deshacer devuelve el objeto entero.
     *
     * El punto de deshacer va **después** de acotar la zona: si se apilaran al revés, un
     * recorte que no deja nada gastaría un deshacer, y al pulsarlo no pasaría nada visible.
     */
    private fun aplicarRecorteAObjeto(ref: LayerObjectRef, rect: IntRect): Boolean {
        // La zona se comprueba antes de tocar el objeto. Una zona que no pica nada no es un
        // recorte: si se guardara vacía, el objeto se volvería invisible y ya no habría forma
        // de tomarlo para quitárselo.
        val zona = ref.recorteValido(objectBoundsDe(rect))
        if (zona == null) {
            avisar("La zona no pica nada de ${ref.kind.label}")
            compositeBitmap = null
            revision++
            return false
        }
        pushStructuralUndo()
        ref.setCrop(zona)
        // El objeto vuelve a quedar seleccionado: con el recorte puesto, el panel tiene que
        // ofrecer quitarlo, y para eso hace falta que el objeto siga tomado. Sin esto el
        // recorte quedaba aplicado y sin forma de quitarlo más que deshaciéndolo todo.
        // Y la herramienta pasa a "Mover": el panel de acciones de objeto solo sale con esa,
        // así que dejando la de selección el botón de quitar el recorte no se vería y el
        // usuario acabaría deshaciendo para deshacer el recorte.
        movingObject = ObjectSelection(ref, ObjectDragMode.IDLE)
        selectTool(PaintTool.MOVE_OBJECT)
        compositeBitmap = null
        revision++
        return true
    }

    /**
     * Quita el recorte al objeto seleccionado y lo deja entero otra vez.
     *
     * Es lo que hace posible corregir un recorte mal hecho sin deshacer todo lo demás: la foto
     * nunca se ha tocado, solo se le ha puesto una ventana encima.
     */
    fun quitarRecorteDelObjeto(): Boolean {
        val ref = movingObject?.ref ?: recortePendiente ?: return false
        if (ref.crop() == null) return false
        pushStructuralUndo()
        ref.setCrop(null)
        compositeBitmap = null
        revision++
        return true
    }

    /** Qué objeto hay seleccionado ahora mismo, para que la interfaz dibuje su marco. */
    val selectedObject: LayerObjectRef? get() = movingObject?.ref

    /**
     * El texto seleccionado, o null si lo que se está moviendo no es un texto.
     *
     * Lo lee el panel de acciones para no tener que preguntar por el tipo allí. Como
     * `movingObject` es estado observable, el panel se vuelve a componer cuando cambia el
     * objeto seleccionado, y con él el texto y todos los controles.
     *
     * Sale del mismo `movingObject` que el marco, así que los dos aparecen y desapare juntos:
     * no puede haber un panel de estilo sin objeto al que aplicárselo.
     */
    val selectedText: TextObject?
        get() = (movingObject?.ref as? TextObjectRef)?.text

    /**
     * Borra el objeto seleccionado, y es lo que hace el botón de papelera del marco.
     *
     * Se deja el paso de deshacer puesto: borrar un texto es de las cosas que más cuesta
     * repetir a mano, y sin deshacer habría que volver a escribirlo entero.
     *
     * Con varios textos y curvas por capa, borrar es quitar **el que estaba seleccionado** de su
     * lista, no vaciar la lista. Antes no había más que uno, así que vaciarla y quitarlo eran
     * la misma cosa; ahora vaciarla se llevaría por delante los demás.
     */
    fun deleteSelectedObject() {
        val layer = activeLayer() ?: return
        val ref = movingObject?.ref ?: return
        when (ref.kind) {
            ObjectKind.TEXT -> {
                val texto = (ref as? TextObjectRef)?.text ?: return
                pushStructuralUndo()
                layer.texts.remove(texto)
            }
            ObjectKind.CURVE -> {
                val curva = (ref as? CurveObjectRef)?.curve ?: return
                pushStructuralUndo()
                layer.curves.remove(curva)
            }
            ObjectKind.PLACED -> {
                pushStructuralUndo()
                layer.placed = null
            }
        }
        movingObject = null
        compositeBitmap = null
        revision++
    }

    private fun startMovingImage(point: Offset) {
        val placed = activeLayer()?.placed
        if (placed == null) {
            return
        }
        moveFrom = point
        moveStartX = placed.offsetX
        moveStartY = placed.offsetY
        moveUndo.rearm()
    }

    private fun moveImage(point: Offset) {
        val placed = activeLayer()?.placed ?: return
        val from = moveFrom ?: return
        if (moveUndo.pushOnce()) pushStructuralUndo()
        val nuevo = PlacedImageTransform.drag(
            antes = currentTransform(placed),
            desde = from,
            hasta = point,
            origenX = moveStartX,
            origenY = moveStartY
        )
        placed.offsetX = nuevo.offsetX
        placed.offsetY = nuevo.offsetY
        compositeBitmap = null
        revision++
    }

    private fun endMovingImage() {
        moveFrom = null
        moveUndo.rearm()
    }

    /** Cierra el gesto de pinza sobre la imagen, para que el próximo deshaga el anterior. */
    fun endImageTransform() {
        moveUndo.rearm()
    }

    /**
     * Pinza sobre la imagen puesta: la escala alrededor del punto donde están los dedos y
     * la corre para que ese punto siga bajo el dedo.
     */
    fun transformPlacedImage(sheetFocus: Offset, zoomChange: Float) {
        val placed = activeLayer()?.placed ?: return
        if (zoomChange <= 0f || !zoomChange.isFinite()) return
        // El foco va al espacio de la imagen, que es donde viven sus números. Con la capa
        // estirada, sin esta cuenta la imagen se iría para el lado contrario al ampliar.
        val focus = aContenido(sheetFocus)
        val antes = currentTransform(placed)
        val despues = PlacedImageTransform.zoom(antes, focus, zoomChange)
        if (despues == antes) return
        // Un solo punto de deshacer por gesto, no uno por movimiento de la pinza.
        if (moveUndo.pushOnce()) pushStructuralUndo()
        placed.scale = despues.scale
        placed.offsetX = despues.offsetX
        placed.offsetY = despues.offsetY
        compositeBitmap = null
        revision++
    }

    private fun currentTransform(placed: PlacedImage) =
        PlacedTransform(placed.offsetX, placed.offsetY, placed.scale)

    /**
     * Pinza sobre el objeto seleccionado: lo escala alrededor del punto donde están los dedos,
     * que es lo que se queda quieto.
     *
     * Es la misma cuenta que hace [transformPlacedImage] para la imagen puesta, con la caja
     * del objeto en vez de su desplazamiento. No tiene por qué ser otra: un texto y una foto
     * se estiran con la misma regla, y tener dos copias de la matemática del anclaje al foco
     * es tener una que se queda sin arreglar.
     *
     * Si no hay nada seleccionado la pinza no hace nada, y con eso el lienzo se queda quieto.
     * Es lo que se espera: si la pinza moviera la vista con la mano puesta, no se podría
     * acercar la vista a un texto sin apartar la mano de él.
     */
    fun pinchSelectedObject(sheetFocus: Offset, zoomChange: Float) {
        if (zoomChange <= 0f || !zoomChange.isFinite()) return
        val objeto = selectedObject ?: return
        // Como con la imagen, el foco se lleva al espacio del objeto: si no, con la capa
        // estirada el objeto se iría para el lado contrario al pinzar.
        val focus = aContenido(sheetFocus)
        val antes = objeto.bounds()
        val actual = objeto.currentScale()
        val transform = PlacedImageTransform.zoom(
            antes = PlacedTransform(antes.left, antes.top, actual),
            focus = focus,
            zoomChange = zoomChange
        )
        val escala = objeto.scaleCoherente(transform.scale)
        if (escala == actual) return
        if (moveUndo.pushOnce()) pushStructuralUndo()
        // La posición sale del foco: la distancia del foco a la esquina superior izquierda se
        // multiplica por la escala nueva. Sin esto, el objeto crecería hacia abajo y a la
        // derecha y se iría de debajo de los dedos.
        val factor = escala / actual
        objeto.applyScale(escala)
        objeto.moveTo(focus.x - (focus.x - antes.left) * factor, antes.top)
        compositeBitmap = null
        revision++
    }

    /**
     * Cierra la pinza del objeto, para que el próximo gesto no deshaga el anterior.
     *
     * El gesto se rearma aquí y no en [endMovingObject], porque la pinza no pasa por los pasos
     * de arrastre: son dos caminos distintos al mismo sitio y sin esto una pinza y el arrastre
     * siguiente compartirían un único punto de deshacer, que es justo lo que se pidió evitar.
     */
    fun endObjectPinch() {
        moveUndo.rearm()
    }

    fun onTap(sheetPoint: Offset) {
        val point = aContenido(sheetPoint)
        val x = point.x.toInt()
        val y = point.y.toInt()

        // Las herramientas de selección no necesitan una capa donde pintar, así que van antes
        // de buscarla: también sirven para elegir una zona antes de tener nada dibujado.
        if (currentTool == PaintTool.SMART_SELECTION) {
            smartSelectAt(point)
            return
        }
        if (currentTool == PaintTool.SELECTION) {
            // Un toque suelto con la herramienta de marco quita la selección: es la forma
            // corta de volver a pintar en toda la hoja. Se ve, porque el marco desaparece.
            if (hasSelection) clearSelection()
            return
        }
        val layer = activeLayer() ?: return

        when (currentTool) {
            PaintTool.MOVE_OBJECT -> {
                // Un toque suelto elige el objeto que hay debajo, o suelta la selección si no hay
                // ninguno. Antes quitaba la selección siempre, y con la selección ahora
                // persistente eso pasaba por dos cosas: tocar la curva para verla con su marco
                // y sus nodos lo deseleccionaba, y había que tocarla **arrastrando** para que
                // apareciera algo. Que el marco se vaya se ve; no hace falta además un aviso que
                // lo diga encima del lienzo.
                val objeto = layer.objectAt(point.x, point.y)
                movingObject = objeto?.let { ObjectSelection(ref = it, mode = ObjectDragMode.IDLE) }
                moveFrom = null
                compositeBitmap = null
                revision++
            }
            PaintTool.EYEDROPPER -> {
                // Toma el color de lo que se ve, con la imagen puesta incluida.
                val picked = colorAtPoint(x, y)
                if (picked != null) {
                    currentColor = picked
                    // Vuelve al pincel: el cuenta gotas sirve para elegir color, no para pintar.
                    currentTool = PaintTool.BRUSH
                }
            }
            PaintTool.CLONE -> {
                // Un toque suelto marca la fuente; después se arrastra para copiar.
                clone.markSourceOnly(point.x, point.y)
            }
            PaintTool.BUCKET -> {
                // La foto se funde antes de buscar el color. Sin esto el relleno lee una capa
                // vacía: todos sus píxeles son transparentes, forman una sola región y el
                // relleno se come el lienzo entero en vez de la zona de un color.
                bakePlacedForPixelEdit(layer)
                ensureLayerCoversSheet(layer)
                if (x < 0 || y < 0 || x >= layer.bitmap.width || y >= layer.bitmap.height) return
                val before = layer.bitmap.editableCopy()
                floodFill(layer.bitmap, x, y, currentColor)
                // El relleno se hace entero y luego se devuelve lo que quedó fuera de la
                // selección, que es más simple y más rápido que reescribir el relleno.
                confineToSelection(layer.bitmap, before)
                pushHistory(HistoryEntry.PixelEdit(layer.id, before))
                avisarSiSeComioElLienzo(layer.bitmap, PaintTool.BUCKET)
            }
            PaintTool.MAGIC_WAND -> {
                // Mismo motivo que en el cubo, y aquí el efecto era peor: al no fundir, la
                // capa estaba vacía, la región contigua era la capa entera y la varita
                // seleccionaba y pintaba los dos lienzos de golpe, sin discriminar nada.
                // Fundir deja los píxeles de la foto donde la varita puede compararlos.
                bakePlacedForPixelEdit(layer)
                ensureLayerCoversSheet(layer)
                if (x < 0 || y < 0 || x >= layer.bitmap.width || y >= layer.bitmap.height) return
                // La copia se toma antes de tocar los píxeles: el historial guarda el
                // estado previo, así que hace falta guardarlo aunque el relleno no cambie nada.
                val before = layer.bitmap.editableCopy()
                val touched = MagicWand.fill(
                    bitmap = layer.bitmap,
                    startX = x,
                    startY = y,
                    fillColor = currentColor,
                    tolerance = smartFillTolerance.toInt(),
                    mode = smartFillMode
                )
                if (touched == 0) return
                confineToSelection(layer.bitmap, before)
                pushHistory(HistoryEntry.PixelEdit(layer.id, before))
                avisarSiSeComioElLienzo(layer.bitmap, PaintTool.MAGIC_WAND)
            }
            PaintTool.PATCH -> {
                startPatch(point)
                // El toque no pasa por onStrokeEnd, así que se suelta la copia aquí.
                patchBefore = null
                compositeBitmap = null
                revision++
            }
            PaintTool.TEXT -> {
                textAnchor = Offset(x.toFloat(), y.toFloat())
            }
            else -> return
        }
        compositeBitmap = null
        revision++
    }

    /**
     * Coloca en la capa activa el texto confirmado en el diálogo, como objeto.
     *
     * Antes se pintaba directamente sobre el bitmap y a partir de ahí era pintura: no se
     * podía volver a tomar ni moverlo. Ahora es un objeto de la capa, como la imagen puesta,
     * así que la mano de mover lo elige, lo arrastra y lo estira.
     *
     * Solo hay un texto y una curva por capa. Es lo que ya pasaba con la imagen puesta, y
     * cambiar un objeto por otro al colocar otro nuevo es lo que espera quien dibuja: si se
     * acumulasen, los textos viejos quedarían debajo de los nuevos y sin poder tomar.
     */
    fun commitText(
        anchor: Offset,
        text: String,
        sizePx: Float,
        colorArgb: Int,
        typeface: TextTypeface = TextTypeface.SANS,
        bold: Boolean = false,
        italic: Boolean = false,
        lineSpacingFactor: Float = 1.1f
    ) {
        val layer = activeLayer() ?: return
        if (text.isBlank()) return
        pushStructuralUndo()
        val objeto = TextObject(
            text = text,
            offsetX = anchor.x,
            // La caja del texto empieza por arriba, pero el toque marca su línea base. Con la
            // resta, el texto sale justo donde se tocó en vez de medio cuerpo más abajo.
            offsetY = anchor.y - sizePx * 0.85f,
            baseSize = sizePx,
            colorArgb = colorArgb,
            typeface = typeface,
            bold = bold,
            italic = italic,
            lineSpacingFactor = lineSpacingFactor
        )
        // Se mide antes de guardarlo para que su caja sea la buena y no la estimada de una
        // línea: el marco de mover y los tiradores nascen ya en su sitio.
        val medida = TextRenderer.measure(objeto)
        objeto.setMeasured(medida.width, medida.height)
        // Se añade a los que ya hay, no sustituye al último. Con un solo texto por capa, poner
        // un rótulo y un subtítulo era imposible sin montar dos capas; con la lista, uno detrás
        // del otro y cada uno con su propio marco para moverlo.
        layer.texts.add(objeto)
        compositeBitmap = null
        revision++
    }

    /**
     * Cambia el tamaño con el que se dibuja el texto, sin tocar su escala.
     *
     * Se distingue del estirar por un tirador a propósito: el tirador multiplica el tamaño
     * para que la caja siga guardando el texto como se escribió, mientras que esto lo reescribe
     * de verdad. Con el deslizador se busca un tamaño concreto; con el tirador se estira lo que
     * ya está escrito.
     *
     * Cambia el texto **seleccionado**, no el primero de la capa: con varios textos, cambiar el
     * tamaño del primero porque se tocó el deslizador sobre el segundo era justo el fallo que
     * hacía falta evitar. El panel solo se pinta con un texto seleccionado, así que nunca hay
     * otro al que tocarle.
     */
    fun setTextSize(sizePx: Float) {
        val texto = selectedText ?: return
        if (sizePx <= 0f || !sizePx.isFinite()) return
        pushStructuralUndo()
        texto.baseSize = sizePx
        texto.scale = 1f
        texto.clearMeasured()
        val medida = TextRenderer.measure(texto)
        texto.setMeasured(medida.width, medida.height)
        compositeBitmap = null
        revision++
    }

    /**
     * Cambia el estilo del texto **seleccionado** y lo vuelve a medir.
     *
     * Lo usa la caja de estilo del panel: al cambiar la fuente, el grosor o la negrita, el
     * ancho de la caja cambia, y sin volver a medir el marco de mover y los tiradores se quedan
     * en la posición anterior, con lo cual se agarran en un sitio donde no hay nada.
     *
     * Con varios textos por capa esto va al que está seleccionado y no al primero: el panel
     * pertenece a un texto concreto, y tocar sus controles no puede cambiarle el estilo a otro
     * que no se está viendo.
     */
    fun updateTextStyle(
        typeface: TextTypeface? = null,
        bold: Boolean? = null,
        italic: Boolean? = null,
        align: TextAlign? = null,
        lineSpacingFactor: Float? = null,
        boxWidth: Float? = null,
        text: String? = null
    ) {
        val texto = selectedText ?: return
        val cambiaMedida = typeface != null || bold != null || italic != null ||
            lineSpacingFactor != null || boxWidth != null || text != null
        if (cambiaMedida) pushStructuralUndo()
        typeface?.let { texto.typeface = it }
        bold?.let { texto.bold = it }
        italic?.let { texto.italic = it }
        align?.let { texto.align = it }
        lineSpacingFactor?.let { texto.lineSpacingFactor = it }
        boxWidth?.let { texto.boxWidth = it }
        text?.let { texto.text = it }
        if (cambiaMedida) {
            val medida = TextRenderer.measure(texto)
            texto.setMeasured(medida.width, medida.height)
        }
        compositeBitmap = null
        revision++
    }

    /**
     * Qué figura está activa ahora, en el lenguaje del motor.
     *
     * El mapeo vive aquí porque es lo único que sabe de herramientas; el motor solo entiende
     * "línea, rectángulo o círculo".
     */
    fun currentShapeKind(): ShapeKind? = when (currentTool) {
        PaintTool.LINE -> ShapeKind.LINE
        PaintTool.RECTANGLE -> ShapeKind.RECTANGLE
        PaintTool.CIRCLE -> ShapeKind.CIRCLE
        else -> null
    }

    /**
     * Geometría de la figura que se está arrastrando, o null si no hay ninguna.
     *
     * La vista previa de la pantalla y el trazo final se sirven de este mismo cálculo, así que
     * lo que se ve mientras se arrastra y lo que queda al soltar no pueden separarse.
     */
    fun currentShapeGeometry(): ShapeGeometry? {
        val start = shapeStart ?: return null
        val end = shapeCurrent ?: return null
        val kind = currentShapeKind() ?: return null
        return BrushEngine.geometryOf(kind, start.x, start.y, end.x, end.y)
    }

    private fun drawCommittedShape(canvas: Canvas, start: Offset, end: Offset) {
        val kind = currentShapeKind() ?: return
        BrushEngine.draw(canvas, BrushEngine.geometryOf(kind, start.x, start.y, end.x, end.y), shapePaint)
    }

    /**
     * Pinta un tramo del trazo con el pincel configurado.
     *
     * El difuminador va por su cuenta porque no pinta el color elegido: mueve el que ya hay.
     * Todo lo demás es estampar, que es lo que hace [BrushEngine.pincelSegmento].
     */
    private fun pintarTramo(canvas: Canvas, fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val layer = activeLayer() ?: return
        val ajustes = pincelActivo
        if (ajustes.kind == BrushKind.DIFUMINADOR) {
            difuminar(layer, fromX, fromY, toX, toY, ajustes)
            return
        }
        val borrando = currentTool == PaintTool.ERASER
        // El borrador se queda con el borde duro aunque se elija un pincel suave. Un
        // degradado con el modo CLEAR no quita el color de forma uniforme: lo deja a medias
        // en el borde, y el resultado es una mancha gris que parece un fallo de la app.
        val paraPintar = if (borrando) ajustes.copy(kind = BrushKind.DURO) else ajustes
        BrushEngine.pincelSegmento(
            canvas = canvas,
            fromX = fromX,
            fromY = fromY,
            toX = toX,
            toY = toY,
            settings = paraPintar,
            paint = activeStrokePaint(),
            color = if (borrando) CANVAS_BACKGROUND else currentColor,
            alpha = if (borrando) 1f else paraPintar.alfaPorToque(),
            celosia = if (paraPintar.kind == BrushKind.LAPIZ) 2 else 0
        )
    }

    /**
     * Arrastra el color ya pintado en vez de poner el color elegido.
     *
     * Por cada toque se copia el trozo de capa que había bajo el toque **anterior** y se
     * dibuja desplazado al punto actual. Copiar y pegar un parche por toque, en vez de
     * promediar el color de alrededor, es lo que hace que arrastre la forma y no solo el
     * tono: con el promedio una foto se convierte en una mancha en dos toques, y con el
     * parche el pelo, las sombras y los bordes se desplazan como en un difuminador de verdad.
     *
     * El parche se copia con un `drawBitmap` de región a región, que va por accelerated y no
     * píxel a píxel. Es lo bastante rápido para ir con el dedo, y va dos bitmaps del tamaño
     * del pincel en memoria, que es lo que hace que un pincel grande no crezca sin límite.
     *
     * Con [SmudgeMode.COLOR] cambia solo de dónde sale el parche: en vez de copiarlo de la
     * capa se rellena con el color elegido y una caída radial. El resto —un toque por punto
     * del trazo, la fuerza por toque y el pegado— es el mismo camino, así que los bordes, la
     * velocidad y el tamaño se comportan igual en los dos modos.
     */
    private fun difuminar(
        layer: PaintLayer,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        settings: BrushSettings
    ) {
        val pintaColor = settings.difuminado == SmudgeMode.COLOR
        val anterior = difuminadoUltimo
        difuminadoUltimo = null
        if (anterior == null && !pintaColor) {
            // Primer toque del trazo: no hay nada que arrastrar todavía. Se marca dónde
            // empieza para que el siguiente toque sí pueda tirar de lo que haya debajo.
            // El modo color no espera: no arrastra nada de la capa, así que el primer
            // toque ya pinta y el trazo no arranca con un punto muerto.
            difuminadoUltimo = Offset(toX, toY)
            return
        }
        val origen = anterior ?: Offset(toX, toY)

        val radio = max(settings.size / 2f, 1f)
        val lado = (radio * 2f).toInt()
        if (lado < 2) return
        // El parche se reutiliza entre trazos para no crear un bitmap por toque, pero solo
        // mientras sigue midiendo lo mismo: si el usuario cambia el tamaño del pincel, el
        // parche viejo se quedaba con las medidas del trazo anterior y el difuminado se
        // pegaba desplazado o recortado.
        val parche = parcheDifuminado?.takeIf { it.width == lado && it.height == lado }
            ?: Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
                .also { parcheDifuminado = it }
        val canvas = Canvas(layer.bitmap)
        // El difuminador pinta fuera de `activeCanvas`, con su propio lienzo: sin el recorte
        // de la selección dibujaría también fuera del marco, que es lo único que las otras
        // herramientas no hacen.
        SelectionClip.apply(canvas, selection)
        val puntos = BrushTip.toques(fromX, fromY, toX, toY, settings)
        // En arrastrar la fuerza es cuánto se lleva lo ya pintado; en color es cuánto tapa
        // cada toque, y ahí sí tiene sentido el caudal: el color se acumula donde el trazo
        // pasa despacio, igual que en el pincel suave.
        val fuerza = if (pintaColor) settings.alfaPorToque() else settings.fuerzaDeDifuminado()
        if (fuerza <= 0f) return
        // La primera vez que de verdad se va a escribir un píxel se cumple el deshacer que
        // quedó pendiente del primer toque: por eso este push va aquí y no antes del trazo.
        pushUndoSiPintaDifuminado()

        val pintar = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
            alpha = (255f * fuerza).toInt().coerceIn(0, 255)
        }
        val opaco = Paint().apply { isFilterBitmap = true }

        var i = 0
        var origenX = origen.x
        var origenY = origen.y
        while (i < puntos.size) {
            val x = puntos[i]
            val y = puntos[i + 1]
            // En arrastrar el parche sale de donde estaba el toque anterior; en color se
            // genera en el propio toque, así que el recorte de los bordes tiene que
            // calcularse sobre el punto actual o el círculo saldría cortado en el sitio
            // equivocado al empezar el trazo.
            val baseX = if (pintaColor) x else origenX
            val baseY = if (pintaColor) y else origenY
            val izquierda = (baseX - radio).toInt()
            val arriba = (baseY - radio).toInt()
            // La ventana del parche se sale del lienzo en cuanto el trazo toca un borde, y
            // un `Rect` origen fuera del bitmap lanza. Se recorta a lo que sí cabe y solo se
            // pega esa parte: los bordes son justo donde más se usa un difuminador, así que
            // descartarlos sería perder la mitad del caso.
            val recorteX = 0.coerceAtLeast(-izquierda)
            val recorteY = 0.coerceAtLeast(-arriba)
            val ancho = lado - recorteX - 0.coerceAtLeast(izquierda + lado - layer.bitmap.width)
            val alto = lado - recorteY - 0.coerceAtLeast(arriba + lado - layer.bitmap.height)
            if (ancho <= 0 || alto <= 0) {
                origenX = x
                origenY = y
                i += 2
                continue
            }
            parche.eraseColor(AndroidColor.TRANSPARENT)
            if (pintaColor) {
                pintarRellenoDifuminado(parche, settings)
            } else {
                Canvas(parche).drawBitmap(
                    layer.bitmap,
                    Rect(izquierda + recorteX, arriba + recorteY, izquierda + recorteX + ancho, arriba + recorteY + alto),
                    Rect(recorteX, recorteY, recorteX + ancho, recorteY + alto),
                    opaco
                )
            }
            // El parche se pega por su esquina superior izquierda, así que se resta el radio
            // para que quede centrado en el toque y no medio tamaño desplazado.
            canvas.drawBitmap(
                parche,
                Rect(recorteX, recorteY, recorteX + ancho, recorteY + alto),
                Rect(
                    (x - radio).toInt() + recorteX,
                    (y - radio).toInt() + recorteY,
                    (x - radio).toInt() + recorteX + ancho,
                    (y - radio).toInt() + recorteY + alto
                ),
                pintar
            )
            origenX = x
            origenY = y
            i += 2
        }
        difuminadoUltimo = Offset(toX, toY)
    }

    /**
     * Rellena el parche del difuminador con el color elegido y una caída radial.
     *
     * El parche es cuadrado, así que pintarlo entero de color daría un sello de bordes
     * rectos, que no se parece a ningún pincel. La caída se hace con un degradado radial
     * del mismo diámetro: del centro opaco hasta el borde transparente, con el tramo
     * opaco marcado por la dureza. Es la misma forma que usa el pincel suave, para que
     * los dos se lean igual.
     */
    private fun pintarRellenoDifuminado(parche: Bitmap, settings: BrushSettings) {
        val centro = parche.width / 2f
        if (centro <= 0f) return
        val rgb = currentColor and 0x00FFFFFF
        val opaco = (0xFF shl 24) or rgb
        val nucleo = settings.dureza.coerceIn(0f, 1f)
        val paint = Paint().apply {
            isAntiAlias = true
            shader = RadialGradient(
                centro, centro, centro,
                intArrayOf(opaco, opaco, rgb),
                floatArrayOf(0f, nucleo, 1f),
                Shader.TileMode.CLAMP
            )
        }
        Canvas(parche).drawCircle(centro, centro, centro, paint)
    }

    private fun sprayAt(canvas: Canvas, center: Offset) {
        BrushEngine.spray(
            canvas = canvas,
            centerX = center.x,
            centerY = center.y,
            radius = strokeWidth * 2.2f,
            paint = activeStrokePaint()
        )
    }

    /** Devuelve el paint de trazo configurado según la herramienta activa. */
    private fun activeStrokePaint(): Paint {
        val paint = when {
            currentTool != PaintTool.ERASER -> strokePaint
            // En la capa de fondo, borrar tiene que devolver el papel blanco; transparentear
            // dejaba ver el fondo oscuro de la app y el trazo parecía tinta negra.
            activeLayerIndex == 0 -> backgroundEraserPaint
            else -> eraserPaint
        }
        paint.strokeWidth = strokeWidth
        if (currentTool != PaintTool.ERASER) paint.color = currentColor
        return paint
    }

    /** Deja la pintura de las figuras (línea, rectángulo, círculo) con el grosor y el estilo actuales. */
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
        }
        // La hoja se compone sobre el color de fondo, nunca sobre transparencia: si una
        // capa de arriba queda con huecos, lo que se ve es el papel blanco y no el fondo
        // oscuro de la app, que es lo que hacía parecer que el borrador "pintaba".
        composite.eraseColor(CANVAS_BACKGROUND)
        val canvas = Canvas(composite)
        layers.forEach { layer ->
            if (!layer.visible) return@forEach
            if (filterPreviewBitmap != null && filterPreviewLayerId == layer.id) {
                // La copia de la previsualización ya trae la imagen puesta fundida.
                layerCompositePaint.alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(filterPreviewBitmap!!, 0f, 0f, layerCompositePaint)
            } else {
                Compositor.drawLayerContent(canvas, layer, layerCompositePaint)
            }
        }
        return composite.asImageBitmap()
    }

    private fun flattenedBitmap(): Bitmap =
        Compositor.composite(
            layers = layers,
            width = canvasWidth,
            height = canvasHeight,
            background = CANVAS_BACKGROUND,
            previewLayerId = filterPreviewLayerId,
            previewBitmap = filterPreviewBitmap,
            reusePaint = layerCompositePaint
        )

    // ---- Filtros e importación de imágenes -------------------------------------

    /**
     * Color del píxel en ese punto, tal como se ve en pantalla: se mezclan las capas
     * visibles de abajo hacia arriba y, si hay, la imagen puesta de cada una.
     *
     * Devuelve null si el punto cae fuera de la hoja o si la última capa de arriba es
     * totalmente transparente, porque en ese caso no se ve nada que se pueda copiar.
     */
    private fun colorAtPoint(x: Int, y: Int): Int? {
        if (x < 0 || y < 0 || x >= canvasWidth || y >= canvasHeight) return null
        var result = CANVAS_BACKGROUND
        layers.forEach { layer ->
            if (!layer.visible) return@forEach
            // Cada capa se mide contra su propio bitmap, no contra la hoja: al importar una
            // foto la hoja cambia de tamaño y las capas viejas quedan más chicas, así que
            // getPixel fuera de rango reventaría.
            val bmp = layer.bitmap
            if (x >= bmp.width || y >= bmp.height) return@forEach
            var c = bmp.getPixel(x, y)
            layer.placed?.let { placed ->
                // Se invierte la transformación para saber si la imagen tapa este punto.
                val lx = (x - placed.offsetX) / placed.scale
                val ly = (y - placed.offsetY) / placed.scale
                if (lx >= 0f && ly >= 0f && lx < placed.bitmap.width && ly < placed.bitmap.height) {
                    val px = placed.bitmap.getPixel(lx.toInt(), ly.toInt())
                    c = blendOver(px, c)
                }
            }
            // La opacidad de la capa también cuenta: si está al 50%, lo de arriba se ve
            // mezclado con lo de abajo, no entero.
            c = withOpacity(c, layer.opacity)
            result = blendOver(c, result)
        }
        return result
    }

    /**
     * Devuelve [color] con el alfa escalado por [opacity], como se ve la capa en pantalla.
     * A opacidad 1 no se toca nada.
     */
    private fun withOpacity(color: Int, opacity: Float): Int {
        val factor = opacity.coerceIn(0f, 1f)
        if (factor >= 1f) return color
        val alpha = ((color ushr 24 and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }

    /** Pone [top] encima de [bottom] respetando el alfa. */
    private fun blendOver(top: Int, bottom: Int): Int {        val a = (top ushr 24 and 0xFF)
        if (a == 255) return top
        if (a == 0) return bottom
        val inv = 255 - a
        val rb = (bottom ushr 16 and 0xFF) * inv
        val gb = (bottom ushr 8 and 0xFF) * inv
        val bb = (bottom and 0xFF) * inv
        val r = (top ushr 16 and 0xFF) * a + rb
        val g = (top ushr 8 and 0xFF) * a + gb
        val b = (top and 0xFF) * a + bb
        val outA = a * 255 + (bottom ushr 24 and 0xFF) * inv
        if (outA == 0) return 0
        return (outA shl 24) or
            ((r * 255 / outA).toInt().coerceIn(0, 255) shl 16) or
            ((g * 255 / outA).toInt().coerceIn(0, 255) shl 8) or
            (b * 255 / outA).toInt().coerceIn(0, 255)
    }

    /**
     * Estado de la previsualización de filtro.
     *
     * Los filtros se ven mientras se mueve el control de intensidad: se aplica sobre una
     * copia aparte y se muestra esa copia, sin tocar el bitmap de la capa. Al confirmar se
     * lo mete al historial de verdad; al cancelar se descarta y la capa queda como estaba.
     */
    private var filterPreviewLayerId: Int? = null
    private var filterPreviewBase: Bitmap? = null
    private var filterPreviewActive: ImageFilter? = null
    private var filterPreviewBitmap: Bitmap? = null

    /**
     * Si la copia de previsualización ya trae la imagen puesta fundida dentro, la capa se
     * dibuja con esa copia sola. Ver [flattenLayerForFilter].
     */
    private var filterPreviewFlattened = false

    /** Búfer de píxeles que se reutiliza en cada paso de la previsualización. */
    private var filterPreviewBuffer: IntArray? = null

    /**
     * Copia del contenido sin filtrar, para devolver a su sitio lo que quede fuera de la
     * selección. Se lleva su propio búfer, y no uno compartido con [filterPreviewBuffer],
     * porque los dos tienen que estar vivos a la vez: el primero para filtrar y el segundo
     * para deshacer el desbordamiento, como hace el resto de herramientas por píxeles.
     */
    private var filterPreviewOriginBuffer: IntArray? = null

    var previewingFilter: Boolean by mutableStateOf(false)
        private set

    /** Filtro e intensidad que se están previsualizando, para que la UI los muestre marcados. */
    var previewFilter: ImageFilter? by mutableStateOf(null)
        private set

    var previewAmount: Float by mutableFloatStateOf(0f)
        private set

    /**
     * Copia de lo que se ve de la capa, lista para filtrar. Si la capa tiene una imagen
     * puesta, se dibuja también: el filtro tiene que afectar a lo que se ve, no solo a lo
     * pintado. Al aplicar, esa imagen queda fundida en los píxeles de la capa.
     */
    private fun flattenLayerForFilter(layer: PaintLayer): Bitmap {
        // Sin imagen puesta, el filtro trabaja sobre el bitmap de la capa tal como mide. Si
        // se hiciera sobre la hoja, confirmar el filtro dejaría la capa con el tamaño de la
        // hoja, y una capa que ya estaba recortada crecería justo al filtrarla. Con imagen
        // puesta sí hace falta la hoja entera, porque la foto está colocada en coordenadas
        // de hoja y fuera de ella no se vería.
        val destino = Bitmap.createBitmap(
            if (layer.placed == null) layer.bitmap.width else canvasWidth,
            if (layer.placed == null) layer.bitmap.height else canvasHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(destino)
        if (layer.placed == null) {
            canvas.drawBitmap(layer.bitmap, 0f, 0f, null)
        } else {
            Compositor.drawLayerContent(canvas, layer, layerCompositePaint)
        }
        return destino
    }

    /**
     * Activa o mueve la previsualización. No toca la capa: solo prepara la copia que se ve.
     * Devuelve false si no hay nada que previsualizar.
     */
    fun previewFilter(filter: ImageFilter, amount: Float) {
        val layer = activeLayer() ?: return
        if (filterPreviewBase == null || filterPreviewLayerId != layer.id) {
            // Cambió la capa o no había copia: se vuelve a partir del contenido real.
            discardFilterPreview()
            // createBitmap entrega un bitmap mutable, y esta copia es el lienzo de trabajo
            // del filtro: tiene que aceptar setPixels.
            filterPreviewBase = flattenLayerForFilter(layer)
            filterPreviewFlattened = layer.placed != null
            filterPreviewLayerId = layer.id
        }
        // Siempre se reprocesa desde la base. Aplicar sobre la copia anterior acumularía
        // el filtro y cada movimiento del deslizador lo deformaría un poco más.
        // El destino se crea con createBitmap, que sí entrega un bitmap mutable. Es
        // importante: sobre uno inmutable, setPixels y Canvas(...) tiran IllegalStateException.
        val base = filterPreviewBase ?: return
        val result = filterPreviewBitmap ?: Bitmap.createBitmap(
            base.width, base.height, Bitmap.Config.ARGB_8888
        )
        val w = result.width
        val h = result.height
        // Los búferes se reservan una vez y se reutilizan: en una hoja grande son varios MB
        // y se pedirían en cada movimiento del deslizador. Hacen falta dos: uno con lo que
        // había antes de filtrar y otro con lo ya filtrado, porque la selección se respeta
        // deshaciendo el desbordamiento, igual que el relleno y la varita mágica.
        val pixels = filterPreviewBuffer?.takeIf { it.size == w * h }
            ?: IntArray(w * h).also { filterPreviewBuffer = it }
        val origen = filterPreviewOriginBuffer?.takeIf { it.size == w * h }
            ?: IntArray(w * h).also { filterPreviewOriginBuffer = it }
        base.getPixels(origen, 0, w, 0, 0, w, h)
        result.setPixels(origen, 0, w, 0, 0, w, h)
        ImageFilter.apply(result, filter, amount)
        if (selection !is Selection.All) {
            result.getPixels(pixels, 0, w, 0, 0, w, h)
            SelectionEngine.restoreOutside(pixels, origen, selection, w, h)
            result.setPixels(pixels, 0, w, 0, 0, w, h)
        }
        filterPreviewActive = filter
        filterPreviewBitmap = result
        previewingFilter = true
        previewFilter = filter
        previewAmount = amount
        compositeBitmap = null
        revision++
    }

    /** Confirma lo previsualizado: la capa se queda con el filtro aplicado. */
    fun commitFilterPreview() {
        val layer = activeLayer() ?: return discardFilterPreview()
        val result = filterPreviewBitmap
        val filter = filterPreviewActive
        if (result == null || filter == null || filterPreviewLayerId != layer.id) {
            return discardFilterPreview()
        }
        // Cuando la imagen puesta quedó fundida en la copia, el cambio no es solo de
        // píxeles: también se pierde el objeto movible. Por eso el punto de deshacer es
        // estructural y devuelve la imagen tal como estaba.
        if (filterPreviewFlattened) pushStructuralUndo() else pushPixelUndo()
        // Se reemplaza el bitmap entero en vez de escribir encima: el de la capa puede
        // venir inmutable (sale de una copia al leer el proyecto) y setPixels revienta.
        // Se hace con el tamaño del resultado y no con el de la hoja: la capa manda, y si
        // no la capa podría cambiar de tamaño al confirmar un filtro.
        val combinado = Bitmap.createBitmap(
            result.width, result.height, Bitmap.Config.ARGB_8888
        )
        val w = combinado.width
        val h = combinado.height
        val pixels = IntArray(w * h)
        result.getPixels(pixels, 0, w, 0, 0, w, h)
        combinado.setPixels(pixels, 0, w, 0, 0, w, h)
        layer.bitmap = combinado
        if (filterPreviewFlattened) layer.placed = null
        discardFilterPreview()
        compositeBitmap = null
        revision++
    }

    /** Tira la previsualización y deja la capa como estaba. */
    fun discardFilterPreview() {
        filterPreviewBase = null
        filterPreviewBitmap = null
        filterPreviewActive = null
        filterPreviewLayerId = null
        filterPreviewFlattened = false
        filterPreviewBuffer = null
        filterPreviewOriginBuffer = null
        previewingFilter = false
        previewFilter = null
        previewAmount = 0f
        compositeBitmap = null
        revision++
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
                avisar("No se pudo abrir la imagen")
                return@launch
            }

            pushStructuralUndo()
            if (imported.width != canvasWidth || imported.height != canvasHeight) {
                // La hoja pasa a tomar el tamaño de la imagen, y las capas que ya existían se
                // llevan al tamaño nuevo con lo que tuvieran debajo. Rellenándolas con el papel
                // tapaban lo que hubiera debajo con un rectángulo blanco, y al ocultar la capa
                // de abajo el rectángulo seguía ahí.
                canvasWidth = imported.width
                canvasHeight = imported.height
                completarCapasDesdeAbajo(CANVAS_BACKGROUND)
            }
            soltarSeleccionDesalineada()
            layers.add(activeLayerIndex + 1, PaintLayer(nextId(), imported, "Imagen"))
            activeLayerIndex += 1
            compositeBitmap = null
            revision++
        }
    }

    /**
     * Pone una imagen dentro de una capa, y la hoja pasa a ser la imagen.
     *
     * Es lo que espera quien abre una foto en un editor: que se trabaje **con la foto**, no
     * sobre un papel más o menos del mismo tamaño. Aquí la hoja se hace del tamaño de la
     * imagen y la imagen ocupa el papel entero, sin márgenes ni bandas.
     *
     * La imagen se conserva como objeto de la capa: se puede mover, estirar y recortar sin
     * hornearla. Lo que sí se ajusta es el papel, porque si no la foto queda flotando en un
     * lienzo con un borde blanco alrededor y no hay forma de quitar ese borde sin recortar la
     * capa a mano.
     *
     * Las capas que ya estaban se llevan al tamaño nuevo con lo que tuvieran debajo, y todo
     * cabe en un solo punto de deshacer: es una acción, no varias.
     */
    fun placeImageInLayer(context: Context, layerIndex: Int, uri: android.net.Uri) {
        if (canvasWidth <= 0 || canvasHeight <= 0) return
        if (layerIndex !in layers.indices) return
        // Se guarda el id y no la posición: decodificar una foto grande tarda, y en ese
        // rato el usuario puede borrar o reordenar capas. Con el id la capa se busca de nuevo
        // y, si ya no está, se avisa en vez de dejar el índice activo apuntando al vacío
        // (que dejaba el pincel sin hacer nada y reventaba al agregar otra capa).
        val targetId = layers[layerIndex].id

        viewModelScope.launch {
            val decoded = withContext(Dispatchers.IO) {
                runCatching { decodeForSheet(context, uri) }.getOrNull()
            }
            if (decoded == null) {
                avisar("No se pudo abrir la imagen")
                return@launch
            }

            val actual = layers.indexOfFirst { it.id == targetId }
            if (actual < 0) {
                avisar("La capa ya no está, la imagen no se puso")
                return@launch
            }
            val target = layers[actual]
            // Un solo punto para todo: la hoja nueva, las capas llevadas al tamaño nuevo y la
            // imagen puesta. Con puntos separados, deshacer una vez dejaba la foto puesta en
            // una hoja del tamaño viejo, que es un estado que no existe en la app.
            pushStructuralUndo()
            val destino = ImageHoja.tamanoPara(decoded.width, decoded.height)
            val scale = destino.ancho.toFloat() / decoded.width
            target.placed = PlacedImage(decoded, 0f, 0f, scale)
            canvasWidth = destino.ancho
            canvasHeight = destino.alto
            completarCapasDesdeAbajo(CANVAS_BACKGROUND)
            activeLayerIndex = actual
            soltarSeleccionDesalineada()
            soltarConfineBuffers()
            compositeBitmap = null
            revision++
        }
    }

    /** Quita la imagen puesta en una capa, sin tocar lo que está pintado en ella. */
    fun removePlacedImage(layerIndex: Int) {
        if (layerIndex !in layers.indices) return
        if (layers[layerIndex].placed == null) return
        pushStructuralUndo()
        layers[layerIndex].placed = null
        compositeBitmap = null
        revision++
    }

    /**
     * Lee la imagen para que ella misma sea el tamaño de la hoja, sin estirarla ni dejar
     * bandas. La reducción y la copia mutable las hace [ImageIo].
     */
    private fun decodeForSheet(context: Context, uri: android.net.Uri): Bitmap? =
        ImageIo.decodeForSheet(context, uri, MAX_DOCUMENT_EDGE)

    // ---- Exportar / guardar como imagen -------------------------------------

    fun saveToGallery(context: Context, format: ExportFormat = ExportFormat.PNG) {
        if (layers.isEmpty()) return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching { ImageIo.writeToGallery(context, flattenedBitmap(), format) }.getOrElse { false }
            }
            // Solo se avisa del fracaso. El éxito ya lo dice la galería, que es donde acaba de
            // aparecer la foto, y un aviso encima del lienzo tapando el dibujo recién guardado
            // era la forma de estropearlo en el momento de querer verlo.
            if (!success) avisar("No se pudo guardar la imagen")
        }
    }

    // ---- Guardar / abrir proyecto en progreso (con capas) ---------------------

    /** Copia las capas como están ahora mismo, listas para escribir en disco. */
    private fun currentSnapshot() = layers.map { layer ->
        LayerSnapshot(
            bitmap = layer.bitmap.editableCopy(),
            name = layer.name,
            visible = layer.visible,
            opacity = layer.opacity,
            placed = layer.placed?.let {
                PlacedSnapshot(
                    bitmap = it.bitmap.editableCopy(),
                    offsetX = it.offsetX,
                    offsetY = it.offsetY,
                    scale = it.scale,
                    crop = it.crop
                )
            },
            // Los textos y las curvas se copian con su `toSnapshot`, el mismo que usa el
            // archivo. Escribirlo otra vez aquí era una segunda lista de campos que se
            // desincronizaba de la otra en cuanto se añadía algo a una y no a la otra: el
            // recorte se guardaba en el archivo pero se perdía al guardar el proyecto.
            texts = layer.texts.map { it.toSnapshot() },
            curves = layer.curves.map { it.toSnapshot() },
            transform = layer.transform
        )
    }

    fun saveProject(context: Context) {
        if (layers.isEmpty()) return
        val snapshot = currentSnapshot()
        val width = canvasWidth
        val height = canvasHeight
        val activeIndex = activeLayerIndex
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                ProjectStore.writeTo(StorageLayout.projectDir(context), snapshot, width, height, activeIndex)
            }
            // Guardar bien es lo esperado: solo se avisa cuando algo sale mal.
            if (!success) avisar("No se pudo guardar el proyecto")
        }
    }

    /**
     * Autoguardado: escribe una copia del estado actual y conserva las últimas
     * [StorageLayout.MAX_AUTOSAVES] para poder volver a cualquiera. Se dispara al dejar de
     * estar en primer plano, que es cuando el sistema puede matar el proceso.
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
                ProjectStore.writeAutosave(
                    StorageLayout.autosaveRoot(context), snapshot, width, height, activeIndex
                )
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

    /** La recuperación de sesión corre una sola vez por ViewModel. Ver [restoreSessionIfInterrupted]. */
    private var sessionRestored = false

    private fun markSessionOpen(context: Context, slot: Int?) {
        if (slot == null) return
        viewModelScope.launch(Dispatchers.IO) {
            ProjectStore.markSessionOpen(StorageLayout.autosaveRoot(context), slot)
        }
    }

    /** Llamado cuando el usuario cierra la app a propósito: la próxima vez abre limpio. */
    fun closeSession(context: Context) {
        ProjectStore.closeSession(StorageLayout.autosaveRoot(context))
        lastAutosavedSignature = null
        lastAutosavedSlot = null
    }

    /**
     * Recupera lo último solo si la app se cerró sola. Si el usuario la cerró a propósito
     * no se toca nada: abre en blanco y el historial queda disponible desde el menú.
     */
    fun restoreSessionIfInterrupted(context: Context) {
        // Una sola vez por ViewModel. La Activity se recrea más de lo que uno espera (cambiar
        // el tamaño de la fuente, el idioma, "no mantener actividades") y el ViewModel
        // sobrevive con las capas y el historial intactos. Sin esta guarda, la segunda
        // composición veía el marcador de la sesión anterior, recargaba todo desde los PNG
        // y tiraba abajo los pasos de deshacer del usuario.
        if (sessionRestored) return
        sessionRestored = true
        val slot = runCatching {
            ProjectStore.interruptedSlot(StorageLayout.autosaveRoot(context))
        }.getOrNull() ?: return
        lastAutosavedSlot = slot
        restoreAutosave(context, slot)
    }

    /** Borra un punto concreto del historial de autoguardado. */
    fun deleteAutosave(context: Context, slot: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ProjectStore.deleteAutosave(StorageLayout.autosaveRoot(context), slot)
            }
            // Si el usuario ya no tiene historial, el próximo autoguardado tiene que rehacerlo.
            lastAutosavedSignature = null
        }
    }

    /** Borra todo el historial de autoguardado. */
    fun clearAutosaves(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ProjectStore.clearAutosaves(StorageLayout.autosaveRoot(context)) }
            lastAutosavedSignature = null
        }
    }

    /** Las versiones guardadas por autoguardado, de la más reciente a la más antigua. */
    fun autosaveSlots(context: Context): List<Int> =
        runCatching { ProjectStore.listAutosaves(StorageLayout.autosaveRoot(context)) }.getOrDefault(emptyList())

    /**
     * Carga un punto guardado del historial de autoguardado. Ya no se restaura solo al
     * abrir: el usuario elige cuál recuperar, así abrir la app nunca pisa lo que está
     * dibujando en ese momento.
     */
    fun restoreAutosave(context: Context, slot: Int) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { ProjectStore.readFrom(StorageLayout.autosaveSlot(context, slot)) }.getOrNull()
            } ?: return@launch
            if (loaded.width <= 0 || loaded.height <= 0 || loaded.layers.isEmpty()) return@launch
            applyLoadedProject(loaded.width, loaded.height, loaded.activeIndex, loaded.layers)
        }
    }

    private suspend fun applyLoadedProject(
        w: Int,
        h: Int,
        active: Int,
        snapshots: List<LayerSnapshot>,
        message: String? = null
    ) {
        layers.clear()
        snapshots.forEach { s ->
            val layer = PaintLayer(nextId(), s.bitmap, s.name, s.visible, s.opacity)
            // Y dónde estaba la capa. Sin esta línea el proyecto se abriría con todas las capas
            // en su sitio, y una capa a la que se le dio su propia posición o su propio tamaño
            // se habría perdido de golpe al reabrir.
            layer.transform = s.transform
            s.placed?.let { layer.placed = PlacedImage(it.bitmap, it.offsetX, it.offsetY, it.scale) }
            // Los textos y las curvas se rehacen como objetos y los textos se miden: el
            // proyecto guarda sus datos, no un dibujo, y sin medirlos la caja sería la estimada
            // y los tiradores de la mano de mover se agarrarían en un sitio donde no hay nada.
            s.texts.forEach { t ->
                val texto = TextObject(
                    text = t.text,
                    offsetX = t.offsetX,
                    offsetY = t.offsetY,
                    baseSize = t.baseSize,
                    colorArgb = t.colorArgb,
                    typeface = t.typeface,
                    bold = t.bold,
                    italic = t.italic,
                    align = t.align,
                    lineSpacingFactor = t.lineSpacingFactor,
                    boxWidth = t.boxWidth,
                    scale = t.scale
                )
                val medida = TextRenderer.measure(texto)
                texto.setMeasured(medida.width, medida.height)
                layer.texts.add(texto)
            }
            s.curves.forEach { c ->
                layer.curves.add(
                    CurveObject(
                        nodes = c.nodes.map { n ->
                            CurveNode(
                                point = Offset(n.x, n.y),
                                outHandle = Offset(n.outHandleX, n.outHandleY),
                                inHandle = Offset(n.inHandleX, n.inHandleY),
                                isCorner = n.isCorner
                            )
                        }.toMutableList(),
                        colorArgb = c.colorArgb,
                        width = c.width,
                        scale = c.scale
                    )
                )
            }
            layers.add(layer)
        }
        canvasWidth = w
        canvasHeight = h
        activeLayerIndex = active.coerceIn(0, layers.lastIndex)
        // El proyecto abierto trae sus propias capas y su propia hoja: la selección del
        // proyecto anterior no describe nada de este.
        clearSelection()
        history.clear()
        syncHistoryState()
        compositeBitmap = null
        revision++
        // Solo avisa cuando hay algo que el usuario no esperaría. Los pasos de rutina
        // (abrir, guardar, cerrar) se callan para no llenar la pantalla de avisos.
        if (message != null) avisar(message)
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
        // Una capa con la imagen puesta ya tiene contenido aunque su bitmap esté vacío.
        if (layer.placed != null) return true
        val bitmap = layer.bitmap
        // Se mira una muestra de 24x24 y no todos los píxeles: esto solo decide si hay algo,
        // y recorrer una hoja de 2048 píxeles en cada autoguardado se nota.
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

    fun loadProject(context: Context) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ProjectStore.readFrom(StorageLayout.projectDir(context)) }.getOrNull()
            }

            if (result == null) {
                avisar("No se encontró ningún proyecto guardado")
                return@launch
            }

            applyLoadedProject(result.width, result.height, result.activeIndex, result.layers)
        }
    }

    /** Exporta el proyecto completo (todas las capas) como un .nbpaint (zip) y abre el selector para compartirlo. */
    fun shareProject(context: Context) {
        if (layers.isEmpty() || canvasWidth <= 0) return
        val snapshot = currentSnapshot()
        val width = canvasWidth
        val height = canvasHeight
        val active = activeLayerIndex
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                ProjectStore.exportZip(context, snapshot, width, height, active)
            }

            if (uri == null) {
                avisar("No se pudo preparar el proyecto para compartir")
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