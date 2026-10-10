package com.mc.nbpaint.ui

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.mc.nbpaint.data.ExportFormat
import com.mc.nbpaint.engine.CurveEngine
import com.mc.nbpaint.engine.SelectionEngine
import com.mc.nbpaint.engine.ShapeGeometry
import com.mc.nbpaint.model.CurveObjectRef
import com.mc.nbpaint.model.ObjectBounds
import com.mc.nbpaint.model.Selection
import com.mc.nbpaint.model.TextAlign
import com.mc.nbpaint.model.TextObject
import com.mc.nbpaint.model.TextTypeface
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mc.nbpaint.ui.theme.NbAccent
import com.mc.nbpaint.ui.theme.NbAccentLight
import com.mc.nbpaint.ui.theme.NbBackground
import com.mc.nbpaint.ui.theme.NbError
import com.mc.nbpaint.ui.theme.NbErrorSoft
import com.mc.nbpaint.ui.theme.NbOnSurface
import com.mc.nbpaint.ui.theme.NbOnSurfaceMuted
import com.mc.nbpaint.ui.theme.NbSurface
import com.mc.nbpaint.ui.theme.NbSurfaceLight
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(viewModel: DrawingViewModel = viewModel()) {
    val context = LocalContext.current

    var saveMenuExpanded by remember { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var colorPickerVisible by remember { mutableStateOf(false) }
    var layersSheetVisible by remember { mutableStateOf(false) }
    var filterSheetVisible by remember { mutableStateOf(false) }
    var paperSheetVisible by remember { mutableStateOf(false) }
    var autosaveMenuVisible by remember { mutableStateOf(false) }
    var textDialogAnchor by remember { mutableStateOf<Offset?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.importImage(context, uri)
    }

    // Selector aparte para poner una imagen dentro de una capa concreta, sin que la hoja
    // cambie de tamaño: así se pueden superponer varias imágenes, una por capa.
    var layerForImage by remember { mutableStateOf<Int?>(null) }
    val layerImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val target = layerForImage
        layerForImage = null
        if (uri != null && target != null) viewModel.placeImageInLayer(context, target, uri)
    }

    // La herramienta Texto deja el punto en el motor; la UI reacciona abriendo el diálogo.
    LaunchedEffect(viewModel.textAnchor) {
        val anchor = viewModel.textAnchor
        if (anchor != null) {
            textDialogAnchor = anchor
            viewModel.dismissTextAnchor()
        }
    }

    // Autoguardado: la app puede morir en cualquier momento (el sistema mata procesos en
    // segundo plano), así que se guarda cada vez que deja de estar en primer plano. Al
    // volver se recupera solo únicamente si la app se cerró sola; si la cerraste con la
    // tecla atrás, la próxima abre en blanco y el historial queda a mano en el menú.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.autosave(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // isFinishing solo es true si el usuario cerró la app; si el sistema la destruyó
            // para liberar memoria, el marcador sobrevive y el trabajo se recupera.
            if ((context as? Activity)?.isFinishing == true) viewModel.closeSession(context)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.restoreSessionIfInterrupted(context)
    }

    var panZoomMode by remember { mutableStateOf(false) }
    val view = rememberCanvasTransformState()

    val isZoomed = view.isZoomed

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { AppLogo() },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = viewModel.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Deshacer")
                    }
                    IconButton(onClick = viewModel::redo, enabled = viewModel.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Rehacer")
                    }
                    IconButton(
                        onClick = { if (isZoomed) view.reset() else panZoomMode = !panZoomMode }
                    ) {
                        Icon(
                            imageVector = if (isZoomed) {
                                Icons.Filled.ZoomOutMap
                            } else {
                                Icons.Filled.OpenWith
                            },
                            contentDescription = if (isZoomed) "Restablecer zoom" else "Mover / zoom",
                            tint = if (panZoomMode && !isZoomed) {
                                NbAccent
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    IconButton(onClick = { layersSheetVisible = true }) {
                        Icon(Icons.Filled.Layers, contentDescription = "Capas")
                    }
                    Box {
                        IconButton(onClick = { saveMenuExpanded = true }) {
                            Icon(Icons.Filled.Save, contentDescription = "Guardar imagen")
                        }
                        DropdownMenu(
                            expanded = saveMenuExpanded,
                            onDismissRequest = { saveMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Guardar como PNG") },
                                onClick = {
                                    saveMenuExpanded = false
                                    viewModel.saveToGallery(context, ExportFormat.PNG)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Guardar como JPEG") },
                                onClick = {
                                    saveMenuExpanded = false
                                    viewModel.saveToGallery(context, ExportFormat.JPEG)
                                }
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { overflowMenuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
                        }
                        DropdownMenu(
                            expanded = overflowMenuExpanded,
                            onDismissRequest = { overflowMenuExpanded = false }
                        ) {
                        DropdownMenuItem(
                            text = { Text("Importar imagen") },
                            onClick = {
                                overflowMenuExpanded = false
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Filtros") },
                            onClick = {
                                overflowMenuExpanded = false
                                filterSheetVisible = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Cerrar proyecto") },
                            onClick = {
                                overflowMenuExpanded = false
                                viewModel.nuevoProyecto(context)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Recuperar trabajo guardado") },
                            onClick = {
                                overflowMenuExpanded = false
                                autosaveMenuVisible = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Tamaño de la hoja") },
                            onClick = {
                                overflowMenuExpanded = false
                                paperSheetVisible = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Limpiar capa actual") },
                                onClick = {
                                    overflowMenuExpanded = false
                                    viewModel.clearCanvas()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Guardar proyecto") },
                                onClick = {
                                    overflowMenuExpanded = false
                                    viewModel.saveProject(context)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Abrir proyecto") },
                                onClick = {
                                    overflowMenuExpanded = false
                                    viewModel.loadProject(context)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Compartir proyecto (.nbpaint)") },
                                onClick = {
                                    overflowMenuExpanded = false
                                    viewModel.shareProject(context)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NbSurface)
            )
        }
    ) { padding ->
        // Con el móvil tumbado el lienzo y el dock van uno al lado del otro. Apilados, el
        // dock se queda con casi todo el alto que queda tumbado (360 dp en este teléfono)
        // y el lienzo sobrevive en una franja tan baja que el dibujo se ve aplastado y,
        // al volver a vertical, queda encuadrado sobre una hoja deformada. Con el panel
        // al lado, cada uno se queda con la parte que le toca y la hoja se sigue viendo
        // entera y con su proporción.
        val horizontal = esHorizontal()
        if (horizontal) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(12.dp)
                ) {
                    DrawingCanvas(
                        viewModel = viewModel,
                        view = view,
                        panZoomMode = panZoomMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                BottomDock(
                    viewModel = viewModel,
                    onOpenCustomColor = { colorPickerVisible = true },
                    modifier = Modifier
                        .width(ANCHO_DOCK_HORIZONTAL)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(12.dp)
                ) {
                    DrawingCanvas(
                        viewModel = viewModel,
                        view = view,
                        panZoomMode = panZoomMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                BottomDock(
                    viewModel = viewModel,
                    onOpenCustomColor = { colorPickerVisible = true }
                )
            }
        }
    }

    if (colorPickerVisible) {
        ColorPickerDialog(
            onDismiss = { colorPickerVisible = false },
            initialColor = Color(viewModel.currentColor),
            onColorSelected = { color ->
                viewModel.selectColor(color.toArgb())
                colorPickerVisible = false
            }
        )
    }

    if (layersSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { layersSheetVisible = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = NbSurface
        ) {
            LayersSheet(
                viewModel = viewModel,
                onPickImage = { index ->
                    layerForImage = index
                    layerImagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
        }
    }

    if (filterSheetVisible) {
        Box(Modifier.fillMaxSize()) {
            FilterSheet(
                viewModel = viewModel,
                onDismiss = { filterSheetVisible = false },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (autosaveMenuVisible) {
        // Se relee al abrir y después de cada borrado para que la lista no quede vieja.
        var slots by remember(autosaveMenuVisible) { mutableStateOf(viewModel.autosaveSlots(context)) }
        AutosaveSheet(
            slots = slots,
            onRestore = { slot ->
                viewModel.restoreAutosave(context, slot)
                autosaveMenuVisible = false
            },
            onDelete = { slot ->
                viewModel.deleteAutosave(context, slot)
                slots = viewModel.autosaveSlots(context).filterNot { it == slot }
            },
            onDeleteAll = {
                viewModel.clearAutosaves(context)
                slots = emptyList()
            },
            onDismiss = { autosaveMenuVisible = false }
        )
    }

    if (paperSheetVisible) {
        PaperSizeSheet(
            currentWidth = viewModel.canvasWidth,
            currentHeight = viewModel.canvasHeight,
            onSelect = { w, h ->
                viewModel.resizeDocument(w, h)
                paperSheetVisible = false
            },
            onDismiss = { paperSheetVisible = false }
        )
    }

    textDialogAnchor?.let { anchor ->
        TextInputDialog(
            initialColor = viewModel.currentColor,
            onConfirm = { text, size, color, typeface, bold, italic, lineSpacing ->
                viewModel.commitText(anchor, text, size, color, typeface, bold, italic, lineSpacing)
                textDialogAnchor = null
            },
            onDismiss = { textDialogAnchor = null }
        )
    }
}

/**
 * Isotipo de la app: "NB" dentro de un cuadro con degradado violeta.
 * El nombre completo "NB Paint" sigue siendo el nombre de la app (label del launcher).
 *
 * El cuadro va con tamaño **obligatorio** y el "NB" se dibuja a mano en píxeles, no con un
 * `Text`. Son las dos cosas que hacía falta para que no se deformara nunca:
 *
 *  - Con `Text` en sp, subir el tamaño de letra del sistema lo salía del cuadro, porque el
 *    logo no crece con él como sí crece la barra, y el isotipo salía cortado.
 *  - Con `size` en vez de `requiredSize`, el cuadro se encoge en cuanto la barra no tiene
 *    sitio para el título, que es el único hueco elástico de la fila. Medido a mano contra
 *    la altura del cuadro, sale igual de grande con cualquier tamaño de letra y con la barra
 *    tan llena como esté.
 */
@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            // `requiredSize` y no `size`: el título de la barra es el único hueco de la fila que
            // se encoge cuando no cabe todo, así que con un `size` normal el cuadro se
            // aplastaba en cuanto se acumulaban botones y el "NB" se veía estrecho. Con
            // `requiredSize` el cuadro se sale de su hueco antes que deformarse, que es lo único
            // que no puede pasarle a un isotipo.
            .requiredSize(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(NbAccentLight, NbAccent)
                )
            )
            .border(
                width = 1.dp,
                color = NbAccentLight.copy(alpha = 0.85f),
                shape = RoundedCornerShape(11.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        // "NB" se dibuja a mano en píxeles, y no con un `Text`.
        //
        // El tamaño sale de la altura real del cuadro, así que las letras se reparten bien
        // dentro de él en vez de salirse, y como el cuadro ya no se encoge, la proporción se
        // mantiene siempre: si el "NB" se estirara, sería por esto, y aquí no depende de nada
        // que pueda cambiar.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
                )
                textSize = size.height * 0.46f
            }
            // La línea base se centra con las métricas de la fuente, no con el alto declarado
            // del texto: centrado así, las letras quedan bien centradas en el cuadro.
            val metricas = paint.fontMetrics
            val base = size.height / 2f - (metricas.ascent + metricas.descent) / 2f
            drawContext.canvas.nativeCanvas.drawText("NB", size.width / 2f, base, paint)
        }
    }
}

/**
 * Botón con etiqueta, para elegir una de varias opciones (fuente, estilo, alineación).
 *
 * El diálogo de texto antes no tenía dónde elegir nada más que el tamaño y el color, y con
 * una fuente y dos estilos encima ya no cabían más botones sueltos. Una pildora por opción
 * deja ver de un vistazo cuál está puesta, que es justo lo que se pierde cuando solo hay un
 * desplegable con el nombre de la opción elegida.
 */
@Composable
private fun SelectorPildora(
    texto: String,
    activo: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (activo) NbAccent else NbSurfaceLight)
            .border(
                width = 1.dp,
                color = if (activo) NbAccentLight else NbSurfaceLight,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelMedium,
            color = if (activo) Color.White else NbOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Ancho del dock con el móvil tumbado: lo justo para las herramientas, sin robarle sitio al lienzo. */
private val ANCHO_DOCK_HORIZONTAL = 280.dp

/**
 * Si la pantalla está más ancha que alta.
 *
 * Se decide por la proporción y no por la orientación declarada: durante el giro esa
 * última va diciendo "unknown", y con ella el layout puede cambiar dos veces seguidas en
 * el mismo movimiento y dejar el lienzo con el encuadre de la orientación contraria.
 */
@Composable
private fun esHorizontal(): Boolean {
    val config = LocalConfiguration.current
    return config.screenWidthDp > config.screenHeightDp
}

/**
 * Dock inferior colapsable. Contraído muestra solo el estado actual (color, herramienta,
 * grosor) en una barra delgada; expandido muestra los paneles de herramientas.
 *
 * Color y grosor van detrás de su propio tirador, y plegados de salida. No es solo
 * cuestión de sitio: la paleta (144 px) y el grosor (60 px) ocupaban más de la mitad
 * del panel y dejaban las herramientas sin sitio. En una pantalla de 2312 px (2107
 * útiles con la barra de navegación) al panel le llegan unos 690 px, y con la paleta
 * abierta las fichas de grupo ya comían lo que le quedaba a la fila de botones, que
 * acababa dibujada bajo el borde de la ventana y sin forma de llegar. Plegados caben
 * las fichas y dos filas de botones.
 *
 * El circulo de color de la cabecera abre los ajustes, que es lo que se espera al tocar
 * un color, así que elegir color sigue siendo un toque.
 * Para agregar un panel nuevo (capas, plantillas, etc.) basta con insertarlo en el
 * bloque `content` de [AnimatedVisibility]: el orden y el espacio se ajustan solos.
 */
@Composable
private fun BottomDock(
    viewModel: DrawingViewModel,
    onOpenCustomColor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val horizontal = esHorizontal()

    // Arranca contraído: al abrir se ve el lienzo completo y la barra compacta, sin
    // paneles tapando el dibujo. Se despliega al tocar la barra. Tumbado arranca abierto:
    // en horizontal sobra ancho, y plegado el dock dejaría una columna de lado
    // desperdiciando la mitad de lo que ya sobra, con las herramientas dentro.
    var expanded by rememberSaveable { mutableStateOf(horizontal) }

    // Ajustes de color y grosor plegados de salida, que es lo que deja sitio a las
    // herramientas. El estado se recuerda para poder cambiar varios colores seguidos sin
    // que se pliegue solo, y para que no se pierda al girar el móvil.
    var ajustesAbiertos by rememberSaveable { mutableStateOf(false) }

    // Tope del panel a una parte fija de la pantalla, medido contra el alto total y no contra
    // lo que quede libre. La diferencia importa: el lienzo de arriba lleva `weight(1f)`, así
    // que el alto que le llega al panel depende de lo que el propio panel mida, y si se mide
    // mal se reparte mal. Con el tope puesto en el `ToolPanel` el panel se medía al espacio
    // que ya le había dejado el lienzo (135 px en vez de los ~300 que necesita) y la fila de
    // botones salía dibujada bajo el borde de la ventana, sin llegar.
    //
    // Con el tope aquí, sobre la pantalla y no sobre el hueco, el alto que pide el dock está
    // acotado de antemano y el `weight(1f)` reparte lo que sobra. Nada se dibuja fuera.
    //
    // Tumbado el tope sube bastante: el panel es una columna de lado y el alto es el recurso
    // escaso, así que con el 45% de abajo solo se ve la cabecera y hay que deslizar para
    // llegar a la última herramienta. Como no compite con el lienzo por el alto, puede
    // tomar casi todo.
    val fraccionAlto = if (horizontal) 0.82f else 0.45f
    val altoPanel = (LocalConfiguration.current.screenHeightDp * fraccionAlto).dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NbSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // El círculo abre los ajustes en vez de la barra entera: tocar un color es para
            // cambiarlo, y con la barra plegada no había forma de llegar a la paleta. Por eso
            // despliega las dos cosas: si solo alternara `ajustesAbiertos` con la barra cerrada
            // el estado quedaría a true detrás del pliegue, y el primer toque de color no
            // enseñaría nada.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(viewModel.currentColor))
                    .border(2.dp, NbOnSurfaceMuted, CircleShape)
                    .clickable {
                        // Con la barra plegada el toque es para ver la paleta, no para
                        // alternarla. `ajustesAbiertos` se recuerda entre pliegues, así que
                        // alternarlo a ciegas podía cerrar justo lo que se venía a pedir.
                        if (expanded) {
                            ajustesAbiertos = !ajustesAbiertos
                        } else {
                            expanded = true
                            ajustesAbiertos = true
                        }
                    }
            )
            Spacer(Modifier.width(10.dp))
            // Herramienta y grosor en una sola línea. Antes iban en dos, y esa segunda línea
            // costaba unos 36 px de un panel al que ya le faltaban.
            Text(
                text = "${viewModel.currentTool.label} · ${viewModel.strokeWidth.toInt()} px",
                style = MaterialTheme.typography.labelLarge,
                color = NbOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = if (expanded) "Contraer paneles" else "Expandir paneles",
                tint = NbOnSurfaceMuted
            )
        }

        // Aviso dentro del panel, no flotando sobre el lienzo.
        //
        // Antes era un snackbar emergido desde abajo: se quedaba unos segundos encima del
        // dibujo y, mientras estaba, se comía los toques de esa zona. En una app de pintar eso
        // es peor que no avisar, porque el usuario creería que ha pintado donde no quería. Y
        // la mayoría de los avisos solo confirmaban algo que ya se veía, así que la mayoría
        // se quitaron y aquí solo queda lo que de verdad impide hacer algo: un recorte sin
        // nada que quitar, una selección que es de otra capa, una imagen que no se abre.
        AnimatedVisibility(
            visible = viewModel.aviso != null,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NbErrorSoft)
                    .clickable { viewModel.limpiarAviso() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = NbError,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = viewModel.aviso.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = NbError,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            // El scroll va aquí y no dentro del `ToolPanel`. Con el color y el grosor
            // plegados, que es como arranca, el panel entero son unos 96.dp y no se desliza
            // nada; el scroll solo aparece en el peor caso, con los ajustes abiertos y a la vez
            // las acciones de zona, que sí pasan de la parte de pantalla que se le da.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = altoPanel)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { ajustesAbiertos = !ajustesAbiertos }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Color y grosor",
                        style = MaterialTheme.typography.labelMedium,
                        color = NbOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (ajustesAbiertos) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (ajustesAbiertos) "Plegar ajustes" else "Desplegar ajustes",
                        tint = NbOnSurfaceMuted
                    )
                }

                AnimatedVisibility(
                    visible = ajustesAbiertos,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                ) {
                    Column {
                        ColorPaletteRow(viewModel, onOpenCustomColor = onOpenCustomColor)
                        StrokeWidthSlider(viewModel)
                    }
                }

                if (viewModel.currentTool == PaintTool.MAGIC_WAND) {
                    MagicWandPanel(viewModel)
                }
                if (viewModel.currentTool == PaintTool.MOVE_OBJECT) {
                    ObjectActionsPanel(viewModel)
                }
                if (viewModel.currentTool == PaintTool.MOVE_LAYER) {
                    LayerActionsPanel(viewModel)
                }
                BrushPanel(viewModel)
                SelectionActions(viewModel)
                ToolPanel(viewModel)
            }
        }
    }
}

/**
 * Ajustes que solo aparecen con la varita mágica activa: qué tan amplia es la región y con
 * qué se rellena. Se muestran acá y no en un diálogo para poder ajustarlos y usar la
 * herramienta sin interrupciones.
 */
@Composable
private fun MagicWandPanel(viewModel: DrawingViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NbSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Tolerancia ${viewModel.smartFillTolerance.toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = NbOnSurfaceMuted,
                modifier = Modifier.width(96.dp)
            )
            Slider(
                value = viewModel.smartFillTolerance,
                onValueChange = { viewModel.setSmartFillTolerance(it.toInt()) },
                valueRange = 0f..SelectionEngine.MAX_TOLERANCE.toFloat(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmartFillMode.entries.forEach { mode ->
                val selected = viewModel.smartFillMode == mode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) NbAccent else NbSurfaceLight)
                        .clickable { viewModel.smartFillMode = mode }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) NbOnSurface else NbOnSurfaceMuted
                    )
                }
            }
        }
    }
}

/**
 * Acciones del objeto que hay bajo la mano.
 *
 * Solo aparecen con la mano de mover puesta, y solo si hay algo que agarrar. Estando fuera no
 * hay nada que hacer con un texto o una curva que no sea cogerlos con el dedo, así que el
 * grupo saldría vacío y ocuparía el alto que necesita el panel de herramientas.
 *
 * Lo del texto se edita aquí y no en un diálogo porque la caja cambia al cambiar el estilo, y
 * ver el texto mientras se elige la fuente es justo lo que hace falta para decidir.
 *
 * "Recortar" recorta **el objeto**, no la capa. Con una imagen puesta la diferencia se ve de
 * inmediato: la de la capa hornea la foto en los píxeles y se lleva por delante lo que hubiera
 * pintado, mientras que esta le pone una ventana encima a la foto y la deja colocada. El botón
 * cambia a "Quitar recorte" cuando lo hay, porque quitarlo es lo que se va a hacer casi siempre
 * después de haberlo puesto sin querer.
 */
@Composable
private fun ObjectActionsPanel(viewModel: DrawingViewModel) {
    val texto = viewModel.selectedText
    val hayAlgo = texto != null || viewModel.selectedObject != null
    if (!hayAlgo) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NbSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = texto?.let { "Texto" } ?: "Objeto",
            style = MaterialTheme.typography.labelMedium,
            color = NbOnSurface
        )
        Spacer(Modifier.height(6.dp))

        texto?.let { objeto ->
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextTypeface.entries.forEach { familia ->
                    SelectorPildora(
                        texto = familia.label,
                        activo = objeto.typeface == familia,
                        modifier = Modifier.clickable {
                            viewModel.updateTextStyle(typeface = familia)
                        }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectorPildora(
                    texto = "Negrita",
                    activo = objeto.bold,
                    modifier = Modifier.clickable { viewModel.updateTextStyle(bold = !objeto.bold) }
                )
                SelectorPildora(
                    texto = "Cursiva",
                    activo = objeto.italic,
                    modifier = Modifier.clickable { viewModel.updateTextStyle(italic = !objeto.italic) }
                )
                TextAlign.entries.forEach { alineacion ->
                    SelectorPildora(
                        texto = alineacion.label,
                        activo = objeto.align == alineacion,
                        modifier = Modifier.clickable { viewModel.updateTextStyle(align = alineacion) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Interlineado ${"%.1f".format(objeto.lineSpacingFactor)}",
                style = MaterialTheme.typography.labelSmall,
                color = NbOnSurfaceMuted
            )
            Slider(
                value = objeto.lineSpacingFactor,
                onValueChange = { viewModel.updateTextStyle(lineSpacingFactor = it) },
                valueRange = 0.8f..2.2f
            )
            Text(
                text = "Tamaño ${objeto.sizePx.toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = NbOnSurfaceMuted
            )
            Slider(
                value = objeto.sizePx,
                onValueChange = { viewModel.setTextSize(it) },
                valueRange = 8f..320f
            )
        }

        val recortado = viewModel.selectedObject?.crop() != null
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Arrastra para mover, tira de los tiradores para estirar",
                style = MaterialTheme.typography.labelSmall,
                color = NbOnSurfaceMuted,
                modifier = Modifier.weight(1f)
            )
            if (recortado) {
                OutlinedButton(onClick = { viewModel.quitarRecorteDelObjeto() }) {
                    Icon(Icons.Default.ContentCut, contentDescription = "Quitar recorte")
                    Spacer(Modifier.width(4.dp))
                    Text("Quitar recorte")
                }
            } else {
                OutlinedButton(onClick = { viewModel.iniciarRecorteDeObjeto() }) {
                    Icon(Icons.Default.ContentCut, contentDescription = "Recortar objeto")
                    Spacer(Modifier.width(4.dp))
                    Text("Recortar")
                }
            }
            OutlinedButton(onClick = { viewModel.deleteSelectedObject() }) {
                Icon(Icons.Default.Delete, contentDescription = "Borrar objeto")
                Spacer(Modifier.width(4.dp))
                Text("Borrar")
            }
        }
    }
}

/**
 * Acciones de la mano de la capa: moverla, estirarla y devolverla a su sitio.
 *
 * El botón de restablecer es lo que hace falta después de mover o estirar una capa: la
 * transformación no se hornea en los píxeles, así que sin esto la única manera de volver
 * atrás sería deshacer, y deshacer también se lleva por delante lo demás que se hizo entre
 * medias.
 *
 * El número que se ve es la escala de la capa, y no un tanto por ciento de la imagen: es lo
 * que el usuario acaba tocando con la pinza, y con un 200 % se entiende que la capa se ha
 * doblado de tamaño.
 */
@Composable
private fun LayerActionsPanel(viewModel: DrawingViewModel) {
    val transform = viewModel.activeTransform
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NbSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Capa",
            style = MaterialTheme.typography.labelMedium,
            color = NbOnSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Arrastra para mover la capa, pinza para estirarla. Solo esta capa; las demás no se mueven",
            style = MaterialTheme.typography.labelSmall,
            color = NbOnSurfaceMuted
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (transform.esIdentity) "En su sitio" else "${(transform.scale * 100).toInt()} %",
                style = MaterialTheme.typography.labelSmall,
                color = NbOnSurfaceMuted,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { viewModel.restablecerCapa() },
                enabled = !transform.esIdentity
            ) {
                Icon(Icons.Default.Restore, contentDescription = "Restablecer capa")
                Spacer(Modifier.width(4.dp))
                Text("Restablecer")
            }
        }
    }
}

@Composable
private fun DrawingCanvas(
    viewModel: DrawingViewModel,
    view: CanvasTransformState,
    panZoomMode: Boolean,
    modifier: Modifier = Modifier
) {
    fun toCanvasCoords(pointer: Offset): Offset = (pointer - view.pan) / view.scale

    val documentWidth = viewModel.canvasWidth
    val documentHeight = viewModel.canvasHeight
    // Se lee la selección acá y no adentro del dibujado, para poder cachear la sombra con
    // `remember`: si se leyera solo en el lienzo, la imagen se rearma en cada repintado.
    val seleccion = viewModel.selection
    val sombraSeleccion = rememberSelectionShade(seleccion)

    // El tamaño del contenido es el del DOCUMENTO, y no el del nodo del lienzo, que ahora
    // mide el visor. De ahí sale el encuadre: si se usara el del nodo, importar una imagen
    // más grande que la pantalla dejaría de encajar apenas un instante, porque el nodo ya
    // está medido con el tamaño viejo.
    LaunchedEffect(documentWidth, documentHeight) {
        view.onContentSize(IntSize(documentWidth, documentHeight))
    }

    // Cada capa tiene su vista. Al cambiar de capa se guarda la que se estaba mirando y se
    // carga la de la nueva, que es lo que evita que Pinchar en una capa traiga de vuelta el
    // papel de la anterior. Va con el id de la capa y no con su posición: si se moviera una
    // capa en la pila, su vista tiene que seguir siendo suya.
    val idCapaVista = viewModel.layers.getOrNull(viewModel.activeLayerIndex)?.id
    LaunchedEffect(idCapaVista) {
        view.setActiveLayer(idCapaVista)
    }

    // Y las vistas de las capas que ya no existen se sueltan, para que borrar una capa no
    // deje su zoom guardado para siempre.
    val idsVivos = viewModel.layers.map { it.id }.toSet()
    LaunchedEffect(idsVivos) {
        view.retainLayers(idsVivos)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NbBackground)
            .border(1.dp, NbSurfaceLight, RoundedCornerShape(16.dp))
            // El visor toma el tamaño que dejan los paneles; el documento no depende de él.
            .onSizeChanged {
                view.onViewportSize(it)
                viewModel.initDocument(it.width, it.height)
            }
            // Las keys son solo el modo de pan. No puede haber más: si la escala o el
            // desplazamiento fueran keys, cada cambio de encuadre cancelaría el reconocedor
            // y lo dejaría esperando un dedo que ya está apoyado, así que el zoom con dos
            // dedos moría en el primer evento. El estado se lee adentro, en cada llamada.
            .pointerInput(panZoomMode) {
                var lastCanvasPoint = Offset.Zero
                detectCanvasGestures(
                    singleFingerPans = panZoomMode,
                    // Mientras hay un dedo apoyado el visor no se reencuadra solo: si el dock
                    // crece a mitad de arrastre (el grupo "Zona" al nacer la selección) el
                    // lienzo saltaría bajo el dedo y la selección caería donde no se dibujó.
                    onGestureStart = { view.beginGesture() },
                    onGestureEnd = { view.endGesture() },
                    onTap = { viewModel.onTap(toCanvasCoords(it)) },
                    onDoubleTap = { view.toggleDoubleTap(it) },
                    onDrawStart = {
                        lastCanvasPoint = toCanvasCoords(it)
                        viewModel.onStrokeStart(lastCanvasPoint)
                    },
                    onDrawMove = {
                        lastCanvasPoint = toCanvasCoords(it)
                        viewModel.onStrokeMove(lastCanvasPoint)
                    },
                    onDrawEnd = { viewModel.onStrokeEnd(lastCanvasPoint) },
                    onTransform = { view.applyTransform(it) },
                    onZoomEnd = {
                        view.startInertia(it)
                        when (viewModel.currentTool) {
                            PaintTool.MOVE_IMAGE -> viewModel.endImageTransform()
                            PaintTool.MOVE_OBJECT -> viewModel.endObjectPinch()
                            PaintTool.MOVE_LAYER -> viewModel.endImageTransform()
                            else -> Unit
                        }
                    },
                    onImageTransform = { gesture ->
                        // Se pregunta la herramienta acá y no al armar el reconocedor: si el
                        // detector vive más que la selección, cambiar de herramienta lo
                        // dejaba transformando la vista en vez de la imagen.
                        when (viewModel.currentTool) {
                            PaintTool.MOVE_IMAGE ->
                                viewModel.transformPlacedImage(toCanvasCoords(gesture.centroid), gesture.zoom)
                            // Con la mano de mover, la pinza estira el objeto seleccionado en vez
                            // de la vista. Sin esto, hacer pinza con la mano puesta movía el
                            // lienzo y escalaba el texto a la vez, sin poder hacer ninguna bien.
                            PaintTool.MOVE_OBJECT ->
                                viewModel.pinchSelectedObject(toCanvasCoords(gesture.centroid), gesture.zoom)
                            // Y con la mano de la capa, la pinza estira la capa activa, que es
                            // lo que da su propio tamaño a cada capa sin tocar las demás.
                            PaintTool.MOVE_LAYER ->
                                viewModel.escalarCapa(toCanvasCoords(gesture.centroid), gesture.zoom)
                            else -> view.applyTransform(gesture)
                        }
                    }
                )
            }
    ) {
        // El nodo del lienzo mide el VISOR, y el documento se dibuja adentro con una
        // transformación explícita.
        //
        // La versión anterior pedía `requiredSize(documentWidth, documentHeight)`, para que
        // el nodo midiera el documento y `graphicsLayer` lo encajara. No funciona: un nodo
        // más grande que su padre se lo coloca desplazado, y con el documento de 2048 px
        // importado dentro de un visor de 1008 el nodo quedaba en x = -520, así que se veía
        // justo la mitad derecha de la imagen importada. Medido con `onGloballyPositioned`.
        //
        // Haciéndolo al revés no hay nada que dependa de cómo se coloque un hijo sobredimensionado:
        // el nodo es del tamaño del visor y el escalado y el desplazamiento se aplican
        // explícitamente al dibujar. Además el `toDp` y el round-trip de vuelta a px
        // desaparecen, que no servían para nada.
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Se lee `revision` para que el lienzo se repinte en cada cambio del motor.
            @Suppress("UNUSED_EXPRESSION")
            viewModel.revision

            withTransform({
                translate(left = view.pan.x, top = view.pan.y)
                scale(scaleX = view.scale, scaleY = view.scale, pivot = Offset.Zero)
            }) {
                viewModel.currentImageBitmap()?.let { image ->
                    drawImage(
                        image = image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(image.width, image.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(documentWidth, documentHeight)
                    )
                }

                // Todo lo que se está dibujando en la capa activa —la figura, la zona y la
                // curva provisional— va dentro de la transformación de esa capa, con la misma
                // cuenta que usa el compositor. Sin esto, con la capa movida o estirada, la
                // previsualización se dibujaba en un sitio y el trazo salía en otro, y el
                // marco de la zona tampoco coincidía con la parte de la capa que recortaba.
                val transformCapa = viewModel.activeTransform
                withTransform({
                    if (!transformCapa.esIdentity) {
                        translate(left = transformCapa.offsetX, top = transformCapa.offsetY)
                        scale(scaleX = transformCapa.scale, scaleY = transformCapa.scale, pivot = Offset.Zero)
                    }
                }) {
                    drawShapePreview(viewModel)
                    // La marca de la selección va aparte de la previsualización de figuras porque
                    // sigue ahí cuando no se está arrastrando nada: es el aviso permanente de que
                    // se está trabajando dentro de una zona.
                    drawSelectionOverlay(viewModel, sombraSeleccion)
                    // La curva se está dibujando: el trazo provisional va con el resto de las
                    // previsualizaciones, antes de que exista como objeto.
                    drawCurvePreview(viewModel)
                }
            }
        }

        // El marco del objeto se dibuja fuera de la transformación del lienzo, y por eso sus
        // tiradores miden lo mismo con cualquier zoom. Adentro, con el zoom al 800 %, un
        // tirador de 12 px se volvía un cuadrado enorme y tapaba el texto que se quería ver
        // para corregirlo.
        if (viewModel.movingObject != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                @Suppress("UNUSED_EXPRESSION")
                viewModel.revision
                drawObjectFrame(viewModel, view)
            }
        }

        if (view.isZoomed) {
            ZoomBadge(
                percent = view.zoomPercent,
                onReset = view::reset,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            )
        }
    }
}

/**
 * Marco del objeto que se está moviendo, con sus tiradores y, si es una curva, sus nodos.
 *
 * Va fuera de la transformación del lienzo a propósito, y por eso trabaja en dos espacios:
 * la caja del objeto viene en coordenadas de la hoja y se lleva a la pantalla con el mismo
 * `pan` y la misma `escala` que usa el lienzo, mientras que los tiradores se dibujan con
 * [TIRADOR_PX] fijos. Así el marco se lee igual con el zoom al 100 % que al 800 %, que es
 * cuando de verdad hace falta verlo para colocar el dedo.
 */
private const val TIRADOR_PX = 11f

/** Diámetro del tirador, en píxeles de pantalla. */
private const val TIRADOR_DIAMETRO = TIRADOR_PX * 2f

/** Radio de agarre del tirador en la pantalla, generoso para el dedo. */
private const val TIRADOR_AGRARRE = 44f

/** Diámetro del nodo de una curva, en píxeles de pantalla. */
private const val NODO_DIAMETRO = 9f

private fun DrawScope.drawObjectFrame(viewModel: DrawingViewModel, view: CanvasTransformState) {
    val sel = viewModel.movingObject ?: return
    val escala = view.scale
    // La caja del objeto viene en el espacio de la capa, así que pasa por su transformación
    // antes de llevarla a la pantalla. Con la capa movida o estirada, sin esto el marco se
    // quedaba en el sitio viejo y los tiradores no caían donde estaba el objeto: era
    // imposible agarrarlo.
    val transform = viewModel.activeTransform
    val esquinaA = transform.aHoja(sel.ref.bounds().left, sel.ref.bounds().top)
    val esquinaB = transform.aHoja(sel.ref.bounds().right, sel.ref.bounds().bottom)
    val x = view.pan.x + esquinaA.x * escala
    val y = view.pan.y + esquinaA.y * escala
    val ancho = (esquinaB.x - esquinaA.x) * escala
    val alto = (esquinaB.y - esquinaA.y) * escala

    val marco = Color(0xFF8B7CF6)
    // El marco se dibuja aunque la caja se haya salido de la pantalla: así se ve que el
    // objeto está fuera y de qué lado, en vez de desaparecer sin más.
    drawRect(
        color = marco,
        topLeft = Offset(x, y),
        size = Size(ancho, alto),
        style = Stroke(width = 2f)
    )

    // Los tiradores de las cuatro esquinas, salvo mientras se está corrigiendo la curva: ahí lo
    // que importa son los nodos y estorbarían justo donde se está tocando.
    if (sel.mode != ObjectDragMode.HANDLE && sel.mode != ObjectDragMode.NODE) {
        ResizeCorner.entries.forEach { esquina ->
            val centro = esquina.deEsquina(x, y, ancho, alto)
            drawCircle(color = Color.White, radius = TIRADOR_DIAMETRO / 2f, center = centro)
            drawCircle(color = marco, radius = TIRADOR_DIAMETRO / 2f, center = centro, style = Stroke(width = 2f))
        }
    }

    // Nodos y tiradores de la curva: son la manera de doblar la línea, y sin ellos no se
    // puede corregir nada de lo que se dibujó. Se dibujan con la curva simplemente seleccionada,
    // no solo mientras se arrastra: si solo aparecieran durante el gesto, no se sabría dónde
    // se puede tocar para corregirla, que es justo para lo que hace falta verlos.
    val curva = (sel.ref as? CurveObjectRef)?.curve ?: return
    // Un atajo para llevar un punto del espacio de la capa a la pantalla, que es lo que hacen
    // los nodos y sus tiradores uno a uno.
    fun enPantalla(x: Float, y: Float): Offset {
        val enHoja = transform.aHoja(x, y)
        return Offset(view.pan.x + enHoja.x * escala, view.pan.y + enHoja.y * escala)
    }
    curva.nodes.forEach { nodo ->
        // Los nodos van por la misma cuenta que la caja: están en el espacio de la capa.
        val p = enPantalla(nodo.point.x, nodo.point.y)
        val agarrado = sel.mode == ObjectDragMode.NODE && sel.node === nodo
        // Lo que se está moviendo se dibuja relleno y un poco más grande: si no, con el dedo
        // encima y el zoom alto no hay manera de saber cuál de los nodos se está corrigiendo.
        val radio = if (agarrado) NODO_DIAMETRO * 0.85f else NODO_DIAMETRO / 2f
        if (nodo.hasHandles) {
            val salida = enPantalla(nodo.outHandle.x, nodo.outHandle.y)
            val entrada = enPantalla(nodo.inHandle.x, nodo.inHandle.y)
            // La guía va del tirador al nodo, porque sin ella no se sabe a cuál de los dos
            // tiradores se está agarrando.
            drawLine(color = Color(0xFF8B7CF6).copy(alpha = 0.6f), start = p, end = salida, strokeWidth = 1.5f)
            drawLine(color = Color(0xFF8B7CF6).copy(alpha = 0.6f), start = p, end = entrada, strokeWidth = 1.5f)
            // El orden es el de la lista, y el primero es el de salida. Va escrito así y no con
            // `i == 0` porque el índice se lee al revés de lo que significa: si se cambiara el
            // orden de la lista, el resaltado se quedaría en el tirador equivocado.
            listOf(salida to false, entrada to true).forEach { (tirador, esEntrada) ->
                val agarrandoTirador =
                    sel.mode == ObjectDragMode.HANDLE && sel.node === nodo && sel.inHandle == esEntrada
                if (agarrandoTirador) {
                    drawCircle(color = Color.White, radius = 6f, center = tirador)
                }
                drawCircle(color = marco, radius = 4.5f, center = tirador)
            }
        }
        if (agarrado) {
            drawCircle(color = Color.White, radius = radio, center = p)
        } else {
            // Relleno también sin agarrar: un nodo solo con el borde se pierde sobre un trazo
            // claro justo en el punto que hace falta verlo.
            drawCircle(color = Color.White.copy(alpha = 0.75f), radius = radio * 0.7f, center = p)
        }
        drawCircle(color = marco, radius = radio, center = p, style = Stroke(width = 2f))
    }
}

/** La esquina [this] del marco que va de ([left], [top]) con ese tamaño, en pantalla. */
private fun ResizeCorner.deEsquina(left: Float, top: Float, width: Float, height: Float): Offset = when (this) {
    ResizeCorner.TOP_LEFT -> Offset(left, top)
    ResizeCorner.TOP_RIGHT -> Offset(left + width, top)
    ResizeCorner.BOTTOM_LEFT -> Offset(left, top + height)
    ResizeCorner.BOTTOM_RIGHT -> Offset(left + width, top + height)
}

/** Indicador del zoom actual; al tocarlo vuelve al 100%. */
@Composable
private fun ZoomBadge(percent: Int, onReset: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NbSurface.copy(alpha = 0.85f))
            .clickable(onClick = onReset)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelSmall,
            color = NbOnSurface
        )
    }
}

/**
 * Vista previa punteada de la figura que se está arrastrando.
 *
 * La geometría no se calcula acá: se pide al motor, el mismo que usa el trazo final. Por eso
 * el punteado y la figura que queda al soltar no pueden describir cosas distintas.
 */
private fun DrawScope.drawShapePreview(viewModel: DrawingViewModel) {
    val geometry = viewModel.currentShapeGeometry() ?: return
    val filled = viewModel.shapeFilled

    val style = if (filled) {
        Fill
    } else {
        Stroke(
            width = viewModel.strokeWidth,
            cap = StrokeCap.Round,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(10f, 8f), 0f
            )
        )
    }
    val color = Color(viewModel.currentColor)

    when (geometry) {
        is ShapeGeometry.Segment -> drawLine(
            color = color,
            start = Offset(geometry.x1, geometry.y1),
            end = Offset(geometry.x2, geometry.y2),
            cap = StrokeCap.Round,
            strokeWidth = viewModel.strokeWidth
        )
        is ShapeGeometry.Box -> drawRect(
            color = color,
            topLeft = Offset(geometry.left, geometry.top),
            size = Size(geometry.right - geometry.left, geometry.bottom - geometry.top),
            style = style
        )
        is ShapeGeometry.Disc -> drawCircle(
            color = color,
            radius = geometry.radius,
            center = Offset(geometry.centerX, geometry.centerY),
            style = style
        )
    }
}

/**
 * Trazo de la curva mientras se está dibujando.
 *
 * Se ve la línea por la que pasa el dedo, tal cual, y no los nodos que saldrán de ella: esos
 * se eligen al soltar, con el muestreo que hace el motor. Anticipar los nodos mientras se
 * dibuja mostraba una rejilla de puntos que luego no coincidía con la curva final.
 */
private fun DrawScope.drawCurvePreview(viewModel: DrawingViewModel) {
    val puntos = viewModel.curvePointsInProgress
    if (puntos.size < 2) return
    val path = Path().apply {
        moveTo(puntos.first().x, puntos.first().y)
        puntos.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(
        path = path,
        color = Color(viewModel.currentColor),
        style = Stroke(width = viewModel.strokeWidth, cap = StrokeCap.Round)
    )
}

/**
 * Marca de la zona de trabajo.
 *
 * Se dibuja encima del lienzo pero no toca los píxeles: es solo para ver dónde se va a
 * trabajar. El marco punteado se ve mientras se arrastra y queda fijo al soltarlo, y la
 * región inteligente se sombrea con la imagen que arma [rememberSelectionShade], que es la
 * única forma honesta de mostrar una mancha irregular sin convertir el sombreado en el
 * problema.
 */
/**
 * Dibuja la zona elegida dejando hueco lo que se va a descartar.
 *
 * Se atenúa el alrededor en vez de teñir la zona. Teñir lo elegido se leía al revés de lo que
 * el usuario espera: el recorte deja exactamente lo marcado, así que la zona tiene que verse
 * como el resultado, no como lo que se quita. Con el alrededor atenuado la zona se entiende
 * sola, sin ninguna etiqueta.
 *
 * El marco es un rectángulo, pero la mancha inteligente no lo es, y por eso el caso de la
 * máscara no se dibuja con un rectángulo: se atenúa píxel a píxel una vez y el resultado se
 * pinta con un solo `drawImage`. Ver [rememberSelectionShade] para el porqué de no hacerlo con
 * un `drawRect` por píxel.
 */
private fun DrawScope.drawSelectionOverlay(
    viewModel: DrawingViewModel,
    sombra: ImageBitmap?
) {
    val seleccion = viewModel.selection
    if (seleccion is Selection.All) return

    // Tinta con la que se apaga lo de fuera. Va más fuerte que la anterior: esta ya no tiñe la
    // zona, así que es el único indicio de dónde acaba la región, y tiene que leerse bien sin
    // tapar lo que hay alrededor (que es justo lo que se está decidiendo si entra o no).
    val aparte = Color(0x8C000000)

    if (seleccion is Selection.Mask) {
        if (sombra != null) {
            drawImage(
                image = sombra,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(sombra.width, sombra.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(seleccion.width, seleccion.height)
            )
        }
        val limites = viewModel.selectionBounds() ?: return
        drawRect(
            color = NbAccent,
            topLeft = Offset(limites.left.toFloat(), limites.top.toFloat()),
            size = Size(limites.width.toFloat(), limites.height.toFloat()),
            style = Stroke(width = 2f)
        )
        return
    }

    val rect = (seleccion as Selection.Rect).rect
    // Un solo relleno con la regla "par": la hoja entera más el rectángulo del hueco. Es el
    // mismo resultado que dibujar el alrededor con cuatro rectángulos, pero en una operación
    // en vez de cuatro, y sin tener que acertar con los bordes para que no se solapen.
    val camino = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(0f, 0f, viewModel.canvasWidth.toFloat(), viewModel.canvasHeight.toFloat()))
        addRect(
            Rect(
                left = rect.left.toFloat(),
                top = rect.top.toFloat(),
                right = rect.right.toFloat(),
                bottom = rect.bottom.toFloat()
            )
        )
    }
    drawPath(camino, aparte)
    // Borde alrededor del hueco: marca dónde acaba la zona aunque el contenido sea claro o
    // esté vacío. Antes era un punteado de 1 px, que a resolución de móvil se perdía.
    drawRect(
        color = NbAccent,
        topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
        size = Size(rect.width.toFloat(), rect.height.toFloat()),
        style = Stroke(width = 2f)
    )
}

@Composable
fun ColorPaletteRow(viewModel: DrawingViewModel, onOpenCustomColor: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NbSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenCustomColor) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Color personalizado",
                tint = NbAccent
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 12.dp)
        ) {
            items(PresetPalette) { argb ->
                val color = Color(argb.toInt())
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = 2.dp,
                            color = if (viewModel.currentColor == argb.toInt()) NbAccent else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { viewModel.selectColor(argb.toInt()) }
                )
            }
        }
    }
}

private const val FilterSheetColumns = 3
private val FilterSheetGap = 8.dp
private val FilterChipHeight = 40.dp

private fun filterGridHeight(): Dp {
    val rows = (ImageFilter.entries.size + FilterSheetColumns - 1) / FilterSheetColumns
    return FilterChipHeight * rows + FilterSheetGap * (rows - 1)
}

/**
 * Filtros con vista previa en vivo.
 *
 * Al elegir un filtro o al mover el control de intensidad, el lienzo ya muestra cómo va a
 * quedar. Nada se toca en la capa hasta que se confirma con "Aplicar": la previsualización
 * trabaja sobre una copia aparte. Con "Cancelar" se descarta y el dibujo queda como estaba,
 * así no hace falta adivinar ni deshacer a ciegas.
 *
 * Se muestra como una franja fija sobre el lienzo, sin scrim ni hoja arrastrable: con la
 * hoja de Material 3, arrastrar el control de intensidad cerraba el panel y se perdía la
 * previsualización, y ese gesto es justamente el que importa acá.
 */
@Composable
fun FilterSheet(
    viewModel: DrawingViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val layer = viewModel.layers.getOrNull(viewModel.activeLayerIndex)
    var selected by remember { mutableStateOf(ImageFilter.BRIGHTNESS) }
    var amount by remember { mutableFloatStateOf(0.5f) }

    // Si la franja se cierra por lo que sea (atrás, toque afuera), la copia se tira sola.
    DisposableEffect(Unit) {
        onDispose { viewModel.discardFilterPreview() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NbSurface)
            .padding(horizontal = 20.dp)
            .padding(vertical = 20.dp)
    ) {
                Text(
                    text = "Probando ${selected.label.lowercase()} en ${layer?.name ?: "la capa"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = NbOnSurface
                )
                Spacer(Modifier.height(12.dp))

                if (selected.hasAmount) {
                    Slider(
                        value = amount,
                        onValueChange = {
                            amount = it
                            viewModel.previewFilter(selected, it)
                        },
                        valueRange = 0f..1f
                    )
                    Spacer(Modifier.height(8.dp))
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(FilterSheetColumns),
                    modifier = Modifier.height(filterGridHeight()),
                    horizontalArrangement = Arrangement.spacedBy(FilterSheetGap),
                    verticalArrangement = Arrangement.spacedBy(FilterSheetGap)
                ) {
                    items(ImageFilter.entries.size) { index ->
                        val filter = ImageFilter.entries[index]
                        val isSelected = filter == selected
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) NbAccent else NbSurfaceLight)
                                .clickable {
                                    selected = filter
                                    amount = 0.5f
                                    viewModel.previewFilter(filter, if (filter.hasAmount) 0.5f else 0f)
                                }
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) NbOnSurface else NbOnSurfaceMuted
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.discardFilterPreview()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            viewModel.commitFilterPreview()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Aplicar")
                    }
                }
    }
}

/**
 * Historial de autoguardados. Cada salida de la app deja un punto guardado, y acá se elige
 * cuál recuperar. No se carga nada al abrir la app: así nunca se pisa lo que se está
 * dibujando en ese momento.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutosaveSheet(
    slots: List<Int>,
    onRestore: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onDeleteAll: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NbSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Trabajos guardados",
                        style = MaterialTheme.typography.titleMedium,
                        color = NbOnSurface
                    )
                    Text(
                        text = "Se guarda solo al salir de la app, y solo si dibujaste algo.",
                        style = MaterialTheme.typography.labelMedium,
                        color = NbOnSurfaceMuted
                    )
                }
                if (slots.isNotEmpty()) {
                    TextButton(onClick = onDeleteAll) {
                        Text("Borrar todo", color = NbOnSurfaceMuted)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            if (slots.isEmpty()) {
                Text(
                    text = "Todavía no hay nada guardado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NbOnSurfaceMuted
                )
            } else {
                slots.forEachIndexed { index, slot ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NbSurfaceLight)
                            .clickable { onRestore(slot) }
                            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (index == 0) "Más reciente" else "Versión $slot",
                                style = MaterialTheme.typography.bodyLarge,
                                color = NbOnSurface
                            )
                            Text(
                                text = if (index == 0) "Autoguardado" else "Autoguardado anterior",
                                style = MaterialTheme.typography.labelMedium,
                                color = NbOnSurfaceMuted
                            )
                        }
                        Text(
                            text = "Restaurar",
                            style = MaterialTheme.typography.labelLarge,
                            color = NbAccent
                        )
                        IconButton(onClick = { onDelete(slot) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Borrar versión $slot",
                                tint = NbOnSurfaceMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Presets de hoja. La proporción se conserva al elegir; el tamaño es en píxeles. */
private data class PaperSize(val label: String, val width: Int, val height: Int)

private val PaperSizes = listOf(
    PaperSize("Cuadrado", 2048, 2048),
    PaperSize("Retrato 3:4", 1536, 2048),
    PaperSize("Horizontal 4:3", 2048, 1536),
    PaperSize("Historia 9:16", 1152, 2048),
    PaperSize("Panorámica 16:9", 2048, 1152),
    PaperSize("Post 1:1", 1440, 1440)
)

/**
 * Selector de tamaño de hoja. Cada opción muestra su proporción con un rectángulo de
 * verdad para que se vea la forma sin tener que leer el nombre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperSizeSheet(
    currentWidth: Int,
    currentHeight: Int,
    onSelect: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NbSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Tamaño de la hoja",
                style = MaterialTheme.typography.titleMedium,
                color = NbOnSurface
            )
            Text(
                text = "Ahora: $currentWidth x $currentHeight px",
                style = MaterialTheme.typography.labelMedium,
                color = NbOnSurfaceMuted
            )
            Spacer(Modifier.height(16.dp))

            PaperSizes.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { size ->
                        val isCurrent = size.width == currentWidth && size.height == currentHeight
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) NbAccent.copy(alpha = 0.18f) else NbSurfaceLight)
                                .clickable { onSelect(size.width, size.height) }
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PaperOutline(size.width, size.height)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = size.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCurrent) NbOnSurface else NbOnSurfaceMuted
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Al cambiar la tamaño, el dibujo se estira. Podés deshacerlo.",
                style = MaterialTheme.typography.bodySmall,
                color = NbOnSurfaceMuted
            )
        }
    }
}

/** Dibuja la proporción real de la hoja, contenida en un cuadro de 44x44 dp. */
@Composable
private fun PaperOutline(width: Int, height: Int, box: Dp = 44.dp) {
    val ratio = width.toFloat() / height.toFloat()
    Box(
        modifier = Modifier.size(box),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w: Float
            val h: Float
            if (ratio >= 1f) {
                w = size.width
                h = size.width / ratio
            } else {
                h = size.height
                w = size.height * ratio
            }
            val left = (size.width - w) / 2f
            val top = (size.height - h) / 2f
            drawRect(
                color = NbAccent,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(w, h),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
        }
    }
}

@Composable
fun StrokeWidthSlider(viewModel: DrawingViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NbSurface)
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tamaño del pincel",
                style = MaterialTheme.typography.labelMedium,
                color = NbOnSurfaceMuted
            )
            Text(
                text = "${viewModel.strokeWidth.toInt()} px",
                style = MaterialTheme.typography.labelLarge,
                color = NbAccent
            )
        }
        Slider(
            value = viewModel.strokeWidth,
            onValueChange = viewModel::setStrokeWidth,
            valueRange = 2f..60f,
            colors = SliderDefaults.colors(
                thumbColor = NbAccent,
                activeTrackColor = NbAccent,
                inactiveTrackColor = NbSurfaceLight
            )
        )
    }
}

@Composable
fun LayersSheet(viewModel: DrawingViewModel, onPickImage: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // En pantallas bajas el panel se recortaba por abajo y el control de opacidad
            // quedaba fuera de la vista: al arrastrar a ciegas se tocaban filas de capas y
            // la opacidad seemedTo be global. Con scroll, todo siempre se alcanza.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Capas", style = MaterialTheme.typography.titleMedium, color = NbOnSurface)
            TextButton(onClick = viewModel::addLayer) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Nueva capa")
            }
        }
        Spacer(Modifier.height(8.dp))

        // La capa superior se muestra primero, como en los editores de imagen.
        //
        // La lista se arma con los ids y no con las posiciones: al combinar o borrar una
        // capa la lista pierde un elemento, y si la clave de cada fila se buscara por
        // posición Compose pediría un índice que ya no existe y la app cerraría.
        val orderedIds = viewModel.layers.indices.reversed().map { viewModel.layers[it].id }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            // Antes 224.dp: con la barra de opacidad dentro de cada fila, cada capa mide
            // unas 72.dp y en un móvil de 1610 px solo se veía una y el panel parecía
            // roto. Con 200.dp entran dos capas enteras, con su opacidad, sin recorte.
            modifier = Modifier.height(200.dp)
        ) {
            items(orderedIds, key = { it }) { layerId ->
                LayerItem(
                    viewModel = viewModel,
                    layerId = layerId,
                    isSelected = viewModel.activeLayerIndex == viewModel.indexOfLayer(layerId),
                    onPickImage = onPickImage
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        ActiveLayerBar(viewModel)
    }
}

/**
 * Acciones de la capa que se está editando.
 *
 * La opacidad no va aquí: cada capa tiene su propia barra dentro de su fila, y así se
 * ajusta una sin tocar las demás y se puede ver la de debajo mientras se retoca la de
 * encima. Aquí solo quedan las acciones que vacían o aprietan algo.
 *
 * "Ajustar al dibujo" antes se llamaba "Recortar", igual que el recorte a la zona, y las dos
 * aparecían en paneles distintos. Se renombró para que el nombre diga qué se conserva: esto
 * quita los márgenes vacíos alrededor del dibujo, y la otra deja la zona elegida.
 *
 * "Ajustar hoja" hace lo mismo pero con el papel: encoge la hoja hasta donde llega el dibujo
 * de cualquier capa. Va aquí porque es la acción hermana de "Ajustar al dibujo" y el usuario
 * la busca justo al lado, aunque lo que cambie sea la hoja y no la capa.
 */
@Composable
private fun ActiveLayerBar(viewModel: DrawingViewModel) {
    val index = viewModel.activeLayerIndex
    val layer = viewModel.layers.getOrNull(index) ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NbSurfaceLight)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .height(44.dp)
    ) {
        Text(
            text = "Editando: ${layer.name}",
            style = MaterialTheme.typography.labelMedium,
            color = NbAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)
        )
        TextButton(onClick = { viewModel.cropActiveLayerToContent() }) {
            Text("Ajustar al dibujo", style = MaterialTheme.typography.labelSmall, color = NbOnSurfaceMuted)
        }
        TextButton(onClick = { viewModel.cropDocumentToContent() }) {
            Text("Ajustar hoja", style = MaterialTheme.typography.labelSmall, color = NbOnSurfaceMuted)
        }
        TextButton(onClick = { viewModel.clearLayer(index) }) {
            Text("Vaciar", style = MaterialTheme.typography.labelSmall, color = NbOnSurfaceMuted)
        }
    }
}

@Composable
private fun LayerItem(
    viewModel: DrawingViewModel,
    layerId: Int,
    isSelected: Boolean,
    onPickImage: (Int) -> Unit
) {
    // Todo se busca por id y se resuelve la posición recién al actuar. Si la fila quedó
    // vieja porque otra capa se combinó o se borró, la posición ya no sirve.
    val index = viewModel.indexOfLayer(layerId)
    if (index < 0) return
    val layer = viewModel.layers[index]
    var renaming by remember(layerId) { mutableStateOf(false) }

    // Si la capa sigue existiendo, le aplica la acción; si ya no, no hace nada.
    fun onLayer(action: (Int) -> Unit) {
        val current = viewModel.indexOfLayer(layerId)
        if (current >= 0) action(current)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) NbAccent.copy(alpha = 0.12f) else NbSurfaceLight)
            .border(
                width = 1.dp,
                color = if (isSelected) NbAccent else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onLayer { viewModel.selectLayer(it) } }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(38.dp)
        ) {
            IconButton(
                onClick = { onLayer { viewModel.toggleLayerVisibility(it) } },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Visibilidad",
                    tint = if (layer.visible) NbAccent else NbOnSurfaceMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (renaming) {
                RenameLayerDialog(
                    initialName = layer.name,
                    onDismiss = { renaming = false },
                    onConfirm = { newName ->
                        onLayer { viewModel.renameLayer(it, newName) }
                        renaming = false
                    }
                )
            } else {
                // El nombre solo muestra el texto. Antes llevaba un clickable que abría el
                // diálogo de renombrar: al tocar para elegir capa se abría ese diálogo, que
                // tapa el panel y atenúa el lienzo, y parecía que la otra capa desaparecía.
                // Ahora tocar la fila elige la capa, y renombrar es un botón aparte.
                Text(
                    text = layer.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) NbAccent else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            IconButton(onClick = { renaming = true }, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Renombrar capa",
                    tint = if (isSelected) NbAccent else NbOnSurfaceMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = { onLayer { viewModel.moveLayerUp(it) } },
                enabled = index < viewModel.layers.lastIndex,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = "Subir capa",
                    tint = if (index < viewModel.layers.lastIndex) NbOnSurfaceMuted else NbSurfaceLight,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = { onLayer { viewModel.moveLayerDown(it) } },
                enabled = index > 0,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Bajar capa",
                    tint = if (index > 0) NbOnSurfaceMuted else NbSurfaceLight,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = { onLayer { viewModel.mergeDown(it) } },
                enabled = index > 0,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Default.ArrowDownward,
                    contentDescription = "Combinar con la de abajo",
                    tint = if (index > 0) NbAccent else NbSurfaceLight,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = { onLayer { viewModel.removeLayer(it) } },
                enabled = viewModel.layers.size > 1,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Eliminar capa",
                    tint = if (viewModel.layers.size > 1) Color.Red.copy(alpha = 0.7f) else NbSurfaceLight,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Opacidad de ESTA capa, con su propia barra dentro de su fila.
        //
        // Cada capa guarda la suya, así que subir o bajar la de una no toca a las demás:
        // es lo que deja ver la capa de debajo mientras se retoca la de encima. La barra
        // está en su propia línea y no en la fila de los botones para que un toque cerca
        // de los iconos no la mueva por accidente.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(34.dp)
        ) {
            Text(
                text = "Opacidad",
                style = MaterialTheme.typography.labelSmall,
                color = NbOnSurfaceMuted,
                modifier = Modifier.padding(start = 4.dp)
            )
            Slider(
                value = layer.opacity,
                onValueChange = { value -> onLayer { viewModel.setLayerOpacity(it, value) } },
                valueRange = 0f..1f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                colors = SliderDefaults.colors(
                    thumbColor = NbAccent,
                    activeTrackColor = NbAccent,
                    inactiveTrackColor = NbSurface
                )
            )
            Text(
                text = "${(layer.opacity * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (layer.opacity < 1f) NbAccent else NbOnSurfaceMuted,
                modifier = Modifier.width(38.dp)
            )
        }

        // Imagen puesta en esta capa. Con varias capas, cada una con la suya, quedan
        // superpuestas y se mueven una por una con "Mover imagen".
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
                TextButton(onClick = { onLayer(onPickImage) }) {
                    Icon(
                        if (layer.placed == null) Icons.Default.AddPhotoAlternate else Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = NbOnSurfaceMuted
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (layer.placed == null) "Poner imagen" else "Cambiar imagen",
                        style = MaterialTheme.typography.labelSmall,
                        color = NbOnSurfaceMuted
                    )
                }
                if (layer.placed != null) {
                    TextButton(onClick = { onLayer { viewModel.removePlacedImage(it) } }) {
                        Text(
                            text = "Quitar imagen",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Red.copy(alpha = 0.7f)
                        )
                    }
                }
        }
    }
}

@Composable
private fun RenameLayerDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar capa") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun ColorPickerDialog(
    onDismiss: () -> Unit,
    initialColor: Color,
    onColorSelected: (Color) -> Unit
) {
    val hsv = remember(initialColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColor.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }

    val preview = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color personalizado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.White, preview)
                                )
                            )
                            .border(1.dp, NbSurfaceLight, RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = String.format("#%06X", 0xFFFFFF and preview.toArgb()),
                        style = MaterialTheme.typography.titleMedium,
                        color = NbOnSurface
                    )
                }

                SaturationValueField(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onSaturationChange = { saturation = it },
                    onValueChange = { value = it }
                )

                HueSlider(hue = hue, onHueChange = { hue = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(preview) }) { Text("Usar color") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun SaturationValueField(
    hue: Float,
    saturation: Float,
    value: Float,
    onSaturationChange: (Float) -> Unit,
    onValueChange: (Float) -> Unit
) {
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    val pure = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))

    fun update(position: Offset) {
        if (fieldSize.width == 0 || fieldSize.height == 0) return
        onSaturationChange((position.x / fieldSize.width).coerceIn(0f, 1f))
        onValueChange(1f - (position.y / fieldSize.height).coerceIn(0f, 1f))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(Color.White, pure)))
            .onSizeChanged { fieldSize = it }
            .pointerInput(fieldSize) {
                detectTapGestures { update(it) }
            }
            .pointerInput(fieldSize) {
                detectDragGestures(onDrag = { change, _ -> update(change.position) })
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black)
                    )
                )
        )
        val indicatorX = (saturation * fieldSize.width).roundToInt()
        val indicatorY = ((1f - value) * fieldSize.height).roundToInt()
        Box(
            modifier = Modifier
                .offset { IntOffset(indicatorX - 10, indicatorY - 10) }
                .size(20.dp)
                .border(2.dp, Color.White, CircleShape)
                .border(1.dp, Color.Black.copy(alpha = 0.4f), CircleShape)
        )
    }
}

@Composable
private fun HueSlider(hue: Float, onHueChange: (Float) -> Unit) {
    var sliderWidth by remember { mutableStateOf(IntSize.Zero) }
    val rainbow = listOf(
        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue,
        Color.Magenta, Color.Red
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(CircleShape)
            .background(Brush.horizontalGradient(rainbow))
            .onSizeChanged { sliderWidth = it }
            .pointerInput(sliderWidth) {
                detectTapGestures { onHueChange(horizontalFraction(it.x, sliderWidth.width) * 360f) }
            }
            .pointerInput(sliderWidth) {
                detectDragGestures { change, _ ->
                    onHueChange(horizontalFraction(change.position.x, sliderWidth.width) * 360f)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset((hue / 360f * sliderWidth.width).roundToInt() - 8, 4) }
                .size(20.dp)
                .border(2.dp, Color.White, CircleShape)
                .border(1.dp, Color.Black.copy(alpha = 0.4f), CircleShape)
        )
    }
}

private fun horizontalFraction(x: Float, width: Int): Float =
    if (width == 0) 0f else (x / width).coerceIn(0f, 1f)

/**
 * Diálogo de texto. Se abre al tocar el lienzo con la herramienta Texto; el punto del toque
 * queda como ancla y el texto se dibuja con la línea base ahí.
 *
 * El tamaño se elige en píxeles de la hoja, y no en sp como estaba: el diálogo no sabe la
 * escala del lienzo, y en sp un mismo número significaba un tamaño distinto según el
 * teléfono. Con píxeles, el texto que escribes a 48 sale a 48 en cualquier pantalla.
 *
 * Se ofrecen cuatro familias, dos estilos y el interlineado porque son las cuatro cosas que
 * se echa de menos al escribir un texto y se pueden cambiar sin salir a buscar nada. El
 * tamaño de la caja y la alineación no están aquí a propósito: se estiran después con los
 * tiradores de la mano de mover, que es más directo que dialing un número.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputDialog(
    initialColor: Int,
    onConfirm: (text: String, sizePx: Float, colorArgb: Int, typeface: TextTypeface, bold: Boolean, italic: Boolean, lineSpacing: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var size by remember { mutableFloatStateOf(48f) }
    var color by remember { mutableIntStateOf(initialColor) }
    var typeface by remember { mutableStateOf(TextTypeface.SANS) }
    var bold by remember { mutableStateOf(false) }
    var italic by remember { mutableStateOf(false) }
    var lineSpacing by remember { mutableFloatStateOf(1.1f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar texto") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Texto") },
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Tamaño ${size.toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NbOnSurfaceMuted
                )
                Slider(
                    value = size,
                    onValueChange = { size = it },
                    valueRange = 12f..200f
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Fuente",
                    style = MaterialTheme.typography.labelSmall,
                    color = NbOnSurfaceMuted
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextTypeface.entries.forEach { familia ->
                        SelectorPildora(
                            texto = familia.label,
                            activo = typeface == familia,
                            modifier = Modifier.clickable { typeface = familia }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectorPildora(
                        texto = "Negrita",
                        activo = bold,
                        modifier = Modifier.clickable { bold = !bold }
                    )
                    SelectorPildora(
                        texto = "Cursiva",
                        activo = italic,
                        modifier = Modifier.clickable { italic = !italic }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Interlineado ${"%.1f".format(lineSpacing)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NbOnSurfaceMuted
                )
                Slider(
                    value = lineSpacing,
                    onValueChange = { lineSpacing = it },
                    valueRange = 0.8f..2.2f
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(0xFF000000, 0xFFFFFFFF, 0xFFE74C3C, 0xFFF1C40F, 0xFF2ECC71, 0xFF3498DB, 0xFF9B59B6)
                        .forEach { argb ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        if (color == argb.toInt()) 3.dp else 1.dp,
                                        if (color == argb.toInt()) NbAccentLight else NbSurfaceLight,
                                        CircleShape
                                    )
                                    .clickable { color = argb.toInt() }
                            )
                        }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text, size, color, typeface, bold, italic, lineSpacing) },
                enabled = text.isNotBlank()
            ) { Text("Agregar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
