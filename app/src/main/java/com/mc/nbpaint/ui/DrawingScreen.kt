package com.mc.nbpaint.ui

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush as BrushIcon
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mc.nbpaint.ui.theme.NbAccent
import com.mc.nbpaint.ui.theme.NbAccentLight
import com.mc.nbpaint.ui.theme.NbBackground
import com.mc.nbpaint.ui.theme.NbOnSurface
import com.mc.nbpaint.ui.theme.NbOnSurfaceMuted
import com.mc.nbpaint.ui.theme.NbSurface
import com.mc.nbpaint.ui.theme.NbSurfaceLight
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(viewModel: DrawingViewModel = viewModel()) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var saveMenuExpanded by remember { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var colorPickerVisible by remember { mutableStateOf(false) }
    var layersSheetVisible by remember { mutableStateOf(false) }

    var panZoomMode by remember { mutableStateOf(false) }
    val scale = remember { mutableFloatStateOf(1f) }
    val pan = remember { mutableStateOf(Offset.Zero) }

    fun resetZoom() {
        scale.floatValue = 1f
        pan.value = Offset.Zero
    }

    val isZoomed = scale.floatValue != 1f || pan.value != Offset.Zero

    LaunchedEffect(viewModel.messages) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    IconButton(onClick = { panZoomMode = !panZoomMode }) {
                        Icon(
                            Icons.Filled.OpenWith,
                            contentDescription = "Mover / zoom",
                            tint = if (panZoomMode) NbAccent else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (isZoomed) {
                        IconButton(onClick = ::resetZoom) {
                            Icon(Icons.Filled.ZoomOutMap, contentDescription = "Restablecer zoom")
                        }
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
                    panZoomMode = panZoomMode,
                    scale = scale.floatValue,
                    pan = pan.value,
                    onScaleChange = { newScale ->
                        scale.floatValue = newScale.coerceIn(MIN_SCALE, MAX_SCALE)
                    },
                    onPanChange = { newPan -> pan.value = newPan },
                    modifier = Modifier.fillMaxSize()
                )
            }
            BottomDock(
                viewModel = viewModel,
                onOpenCustomColor = { colorPickerVisible = true }
            )
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
            LayersSheet(viewModel)
        }
    }
}

/**
 * Isotipo de la app: "NB" dentro de un cuadro con degradado violeta.
 * El nombre completo "NB Paint" sigue siendo el nombre de la app (label del launcher).
 */
@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(38.dp)
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
        Text(
            text = "NB",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Dock inferior colapsable. Contraído muestra solo el estado actual (color, herramienta,
 * grosor) en una barra delgada; expandido muestra los paneles de herramientas.
 *
 * Para agregar un panel nuevo (capas, plantillas, etc.) basta con insertarlo en el
 * bloque `content` de [AnimatedVisibility]: el orden y el espacio se ajustan solos.
 */
@Composable
private fun BottomDock(
    viewModel: DrawingViewModel,
    onOpenCustomColor: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = Modifier
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
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(viewModel.currentColor))
                    .border(2.dp, NbOnSurfaceMuted, CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = viewModel.currentTool.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = NbOnSurface
                )
                Text(
                    text = "${viewModel.strokeWidth.toInt()} px",
                    style = MaterialTheme.typography.labelSmall,
                    color = NbOnSurfaceMuted
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = if (expanded) "Contraer paneles" else "Expandir paneles",
                tint = NbOnSurfaceMuted
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            Column {
                ColorPaletteRow(viewModel, onOpenCustomColor = onOpenCustomColor)
                StrokeWidthSlider(viewModel)
                ToolBar(viewModel)
            }
        }
    }
}

@Composable
fun ToolBar(viewModel: DrawingViewModel) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NbSurface)
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolButton(
            icon = Icons.Default.BrushIcon,
            contentDescription = PaintTool.BRUSH.label,
            isSelected = viewModel.currentTool == PaintTool.BRUSH,
            onClick = { viewModel.selectTool(PaintTool.BRUSH) }
        )
        ToolButton(
            icon = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = PaintTool.ERASER.label,
            isSelected = viewModel.currentTool == PaintTool.ERASER,
            onClick = { viewModel.selectTool(PaintTool.ERASER) }
        )
        ToolButton(
            icon = Icons.Default.BlurOn,
            contentDescription = PaintTool.SPRAY.label,
            isSelected = viewModel.currentTool == PaintTool.SPRAY,
            onClick = { viewModel.selectTool(PaintTool.SPRAY) }
        )
        ToolButton(
            icon = Icons.Default.FormatColorFill,
            contentDescription = PaintTool.BUCKET.label,
            isSelected = viewModel.currentTool == PaintTool.BUCKET,
            onClick = { viewModel.selectTool(PaintTool.BUCKET) }
        )
        ToolButton(
            icon = Icons.Default.Timeline,
            contentDescription = PaintTool.LINE.label,
            isSelected = viewModel.currentTool == PaintTool.LINE,
            onClick = { viewModel.selectTool(PaintTool.LINE) }
        )
        ToolButton(
            icon = Icons.Default.CropSquare,
            contentDescription = PaintTool.RECTANGLE.label,
            isSelected = viewModel.currentTool == PaintTool.RECTANGLE,
            onClick = { viewModel.selectTool(PaintTool.RECTANGLE) }
        )
        ToolButton(
            icon = Icons.Default.Circle,
            contentDescription = PaintTool.CIRCLE.label,
            isSelected = viewModel.currentTool == PaintTool.CIRCLE,
            onClick = { viewModel.selectTool(PaintTool.CIRCLE) }
        )

        if (viewModel.currentTool == PaintTool.RECTANGLE || viewModel.currentTool == PaintTool.CIRCLE) {
            ToolButton(
                icon = Icons.Default.Edit,
                contentDescription = "Rellenar figura",
                isSelected = viewModel.shapeFilled,
                onClick = viewModel::toggleShapeFilled
            )
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) NbAccent else NbSurfaceLight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) NbOnSurface else NbOnSurfaceMuted,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun DrawingCanvas(
    viewModel: DrawingViewModel,
    panZoomMode: Boolean,
    scale: Float,
    pan: Offset,
    onScaleChange: (Float) -> Unit,
    onPanChange: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    fun toCanvasCoords(pointer: Offset): Offset = (pointer - pan) / scale

    var lastCanvasPoint by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NbBackground)
            .border(1.dp, NbSurfaceLight, RoundedCornerShape(16.dp))
            .pointerInput(panZoomMode, scale, pan) {
                if (panZoomMode) {
                    detectTransformGestures { _, gesturePan, zoom, _ ->
                        onScaleChange(scale * zoom)
                        onPanChange(pan + gesturePan)
                    }
                } else {
                    detectDragGestures(
                        onDragStart = {
                            lastCanvasPoint = toCanvasCoords(it)
                            viewModel.onStrokeStart(lastCanvasPoint)
                        },
                        onDragEnd = { viewModel.onStrokeEnd(lastCanvasPoint) },
                        onDragCancel = { viewModel.onStrokeEnd(lastCanvasPoint) },
                        onDrag = { change, _ ->
                            change.consume()
                            lastCanvasPoint = toCanvasCoords(change.position)
                            viewModel.onStrokeMove(lastCanvasPoint)
                        }
                    )
                }
            }
            .pointerInput(panZoomMode, scale, pan) {
                if (!panZoomMode) {
                    detectTapGestures { viewModel.onTap(toCanvasCoords(it)) }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = pan.x,
                    translationY = pan.y,
                    transformOrigin = TransformOrigin(0f, 0f)
                )
                .onSizeChanged { viewModel.ensureCanvas(it.width, it.height) }
        ) {
            // Se lee `revision` para que el lienzo se repinte en cada cambio del motor.
            @Suppress("UNUSED_EXPRESSION")
            viewModel.revision

            viewModel.currentImageBitmap()?.let { image ->
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                )
            }

            drawShapePreview(viewModel)
        }
    }
}

/** Vista previa punteada de la figura que se está arrastrando. */
private fun DrawScope.drawShapePreview(viewModel: DrawingViewModel) {
    val start = viewModel.shapeStart ?: return
    val end = viewModel.shapeCurrent ?: return
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

    when (viewModel.currentTool) {
        PaintTool.LINE -> drawLine(
            color = color,
            start = start,
            end = end,
            cap = StrokeCap.Round,
            strokeWidth = viewModel.strokeWidth
        )
        PaintTool.RECTANGLE -> {
            val topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y))
            val rectSize = Size(abs(start.x - end.x), abs(start.y - end.y))
            drawRect(color = color, topLeft = topLeft, size = rectSize, style = style)
        }
        PaintTool.CIRCLE -> {
            val center = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
            val radius = hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble()).toFloat() / 2f
            drawCircle(color = color, radius = radius, center = center, style = style)
        }
        else -> Unit
    }
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
fun LayersSheet(viewModel: DrawingViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Capas", style = MaterialTheme.typography.titleLarge, color = NbOnSurface)
            TextButton(onClick = viewModel::addLayer) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Nueva capa")
            }
        }
        Spacer(Modifier.height(8.dp))

        // La capa superior se muestra primero, como en los editores de imagen.
        val ordered = viewModel.layers.indices.reversed()
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(320.dp)
        ) {
            items(ordered.toList(), key = { viewModel.layers[it].id }) { index ->
                LayerItem(
                    viewModel = viewModel,
                    index = index,
                    isSelected = viewModel.activeLayerIndex == index,
                    onSelect = { viewModel.selectLayer(index) }
                )
            }
        }
    }
}

@Composable
private fun LayerItem(
    viewModel: DrawingViewModel,
    index: Int,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val layer = viewModel.layers[index]
    var renaming by remember { mutableStateOf(false) }

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
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.toggleLayerVisibility(index) }) {
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
                        viewModel.renameLayer(index, newName)
                        renaming = false
                    }
                )
            } else {
                Text(
                    text = layer.name,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { renaming = true },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) NbAccent else MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(onClick = { viewModel.moveLayerUp(index) }, enabled = index < viewModel.layers.lastIndex) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = "Subir capa",
                    tint = if (index < viewModel.layers.lastIndex) NbOnSurfaceMuted else NbSurfaceLight,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = { viewModel.moveLayerDown(index) }, enabled = index > 0) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Bajar capa",
                    tint = if (index > 0) NbOnSurfaceMuted else NbSurfaceLight,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = { viewModel.mergeDown(index) },
                enabled = index > 0
            ) {
                Icon(
                    Icons.Default.ArrowDownward,
                    contentDescription = "Combinar con la de abajo",
                    tint = if (index > 0) NbAccent else NbSurfaceLight,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = { viewModel.removeLayer(index) },
                enabled = viewModel.layers.size > 1
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Eliminar capa",
                    tint = if (viewModel.layers.size > 1) Color.Red.copy(alpha = 0.7f) else NbSurfaceLight,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Opacidad",
                style = MaterialTheme.typography.labelSmall,
                color = NbOnSurfaceMuted
            )
            Slider(
                value = layer.opacity,
                onValueChange = { viewModel.setLayerOpacity(index, it) },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = NbAccent,
                    activeTrackColor = NbAccent,
                    inactiveTrackColor = NbSurface
                )
            )
            Text(
                text = "${(layer.opacity * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = NbOnSurfaceMuted,
                modifier = Modifier.width(36.dp)
            )
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
