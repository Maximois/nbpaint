package com.mc.nbpaint.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mc.nbpaint.ui.theme.NbAccent
import com.mc.nbpaint.ui.theme.NbOnSurfaceMuted
import com.mc.nbpaint.ui.theme.NbSurface
import com.mc.nbpaint.ui.theme.NbSurfaceLight
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(viewModel: DrawingViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var saveMenuExpanded by remember { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var colorPickerVisible by remember { mutableStateOf(false) }
    var layersSheetVisible by remember { mutableStateOf(false) }

    var panZoomMode by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val scaleState = remember { mutableFloatStateOf(1f) }
    val offsetState = remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(viewModel.messages) {
        viewModel.messages.collect { msg ->
            scope.launch { snackbarHostState.showSnackbar(msg) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("NB-paint") },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = viewModel.canUndo) {
                        Icon(Icons.Filled.Undo, contentDescription = "Deshacer")
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = viewModel.canRedo) {
                        Icon(Icons.Filled.Redo, contentDescription = "Rehacer")
                    }
                    IconButton(onClick = { panZoomMode = !panZoomMode }) {
                        Icon(
                            Icons.Filled.OpenWith,
                            contentDescription = "Mover / zoom",
                            tint = if (panZoomMode) NbAccent else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (scale != 1f || offset != Offset.Zero) {
                        IconButton(onClick = { scale = 1f; offset = Offset.Zero }) {
                            Icon(Icons.Filled.ZoomOutMap, contentDescription = "Restablecer zoom")
                        }
                    }
                    IconButton(onClick = { layersSheetVisible = true }) {
                        Icon(Icons.Filled.Layers, contentDescription = "Capas")
                    }
                    IconButton(onClick = { viewModel.clearCanvas() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Limpiar capa actual")
                    }
                    Box {
                        IconButton(onClick = { saveMenuExpanded = true }) {
                            Icon(Icons.Filled.Save, contentDescription = "Guardar imagen")
                        }
                        DropdownMenu(expanded = saveMenuExpanded, onDismissRequest = { saveMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Guardar como PNG") },
                                onClick = { saveMenuExpanded = false; viewModel.saveToGallery(context, ExportFormat.PNG) }
                            )
                            DropdownMenuItem(
                                text = { Text("Guardar como JPEG") },
                                onClick = { saveMenuExpanded = false; viewModel.saveToGallery(context, ExportFormat.JPEG) }
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { overflowMenuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
                        }
                        DropdownMenu(expanded = overflowMenuExpanded, onDismissRequest = { overflowMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Guardar proyecto") },
                                onClick = { overflowMenuExpanded = false; viewModel.saveProject(context) }
                            )
                            DropdownMenuItem(
                                text = { Text("Abrir proyecto") },
                                onClick = { overflowMenuExpanded = false; viewModel.loadProject(context) }
                            )
                            DropdownMenuItem(
                                text = { Text("Compartir proyecto (.nbpaint)") },
                                onClick = { overflowMenuExpanded = false; viewModel.shareProject(context) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NbSurface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DrawingCanvas(
                viewModel = viewModel,
                panZoomMode = panZoomMode,
                scaleState = scaleState,
                offsetState = offsetState,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(12.dp)
            )
            ColorPaletteRow(viewModel, onOpenCustomColor = { colorPickerVisible = true })
            StrokeWidthSlider(viewModel)
            ToolBar(viewModel)
        }
    }

    if (colorPickerVisible) {
        ColorPickerDialog(
            onDismiss = { colorPickerVisible = false },
            initialColor = viewModel.brushColor,
            onColorSelected = { color ->
                viewModel.updateBrushColor(color)
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

@Composable
fun ToolBar(viewModel: DrawingViewModel) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NbSurface)
            .horizontalScroll(scrollState)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolButton(
            icon = Icons.Default.Brush,
            contentDescription = "Pincel",
            isSelected = viewModel.currentTool == Tool.Brush,
            onClick = { viewModel.updateTool(Tool.Brush) }
        )
        ToolButton(
            icon = Icons.Default.FormatPaint,
            contentDescription = "Pincel Alisado",
            isSelected = viewModel.currentTool == Tool.SmoothBrush,
            onClick = { viewModel.updateTool(Tool.SmoothBrush) }
        )
        ToolButton(
            icon = Icons.Default.FormatColorFill,
            contentDescription = "Relleno",
            isSelected = viewModel.currentTool == Tool.Bucket,
            onClick = { viewModel.updateTool(Tool.Bucket) }
        )
        ToolButton(
            icon = Icons.Default.Backspace,
            contentDescription = "Borrador",
            isSelected = viewModel.currentTool == Tool.Eraser,
            onClick = { viewModel.updateTool(Tool.Eraser) }
        )
        ToolButton(
            icon = Icons.Default.Timeline,
            contentDescription = "Línea",
            isSelected = viewModel.currentTool == Tool.Line,
            onClick = { viewModel.updateTool(Tool.Line) }
        )
        ToolButton(
            icon = Icons.Default.CropSquare,
            contentDescription = "Rectángulo",
            isSelected = viewModel.currentTool == Tool.Rectangle,
            onClick = { viewModel.updateTool(Tool.Rectangle) }
        )
        ToolButton(
            icon = Icons.Default.Circle,
            contentDescription = "Círculo",
            isSelected = viewModel.currentTool == Tool.Circle,
            onClick = { viewModel.updateTool(Tool.Circle) }
        )
        ToolButton(
            icon = Icons.Default.BlurOn,
            contentDescription = "Difuminar",
            isSelected = viewModel.currentTool == Tool.Blur,
            onClick = { viewModel.updateTool(Tool.Blur) }
        )
    }
}

@Composable
fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) NbAccent.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) NbAccent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) NbAccent else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun DrawingCanvas(
    viewModel: DrawingViewModel,
    panZoomMode: Boolean,
    scaleState: androidx.compose.runtime.MutableFloatState,
    offsetState: androidx.compose.runtime.MutableState<Offset>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, NbSurfaceLight, RoundedCornerShape(16.dp))
            .pointerInput(panZoomMode) {
                if (panZoomMode) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scaleState.floatValue *= zoom
                        offsetState.value += pan
                    }
                } else {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val adjustedOffset = (offset - offsetState.value) / scaleState.floatValue
                            viewModel.startDrawing(adjustedOffset)
                        },
                        onDrag = { change, _ ->
                            val adjustedOffset = (change.position - offsetState.value) / scaleState.floatValue
                            viewModel.draw(adjustedOffset)
                        },
                        onDragEnd = { viewModel.endDrawing() }
                    )
                }
            }
            .pointerInput(panZoomMode) {
                if (!panZoomMode) {
                    detectTapGestures { offset ->
                        val adjustedOffset = (offset - offsetState.value) / scaleState.floatValue
                        viewModel.handleTap(adjustedOffset)
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scaleState.floatValue,
                    scaleY = scaleState.floatValue,
                    translationX = offsetState.value.x,
                    translationY = offsetState.value.y,
                    transformOrigin = TransformOrigin(0f, 0f)
                )
        ) {
            viewModel.layers.forEach { layer ->
                if (layer.isVisible) {
                    layer.paths.forEach { pathData ->
                        drawPathData(pathData)
                    }
                }
            }
            // Vista previa del trazo actual
            viewModel.currentPathData?.let { drawPathData(it) }
        }
    }
}

private fun DrawScope.drawPathData(pathData: PathData) {
    val style = if (pathData.isFilled) Fill else Stroke(
        width = pathData.strokeWidth,
        cap = StrokeCap.Round,
        pathEffect = if (pathData.tool == Tool.Blur) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
    )
    
    when (pathData.tool) {
        Tool.Rectangle -> {
            val size = Size(
                abs(pathData.endPoint.x - pathData.startPoint.x),
                abs(pathData.endPoint.y - pathData.startPoint.y)
            )
            val topLeft = Offset(
                minOf(pathData.startPoint.x, pathData.endPoint.x),
                minOf(pathData.startPoint.y, pathData.endPoint.y)
            )
            drawRect(color = pathData.color, topLeft = topLeft, size = size, style = style)
        }
        Tool.Circle -> {
            val radius = (pathData.endPoint - pathData.startPoint).getDistance()
            drawCircle(color = pathData.color, radius = radius, center = pathData.startPoint, style = style)
        }
        else -> {
            drawPath(path = pathData.path, color = pathData.color, style = style)
        }
    }
}

@Composable
fun ColorPaletteRow(viewModel: DrawingViewModel, onOpenCustomColor: () -> Unit) {
    val colors = listOf(
        Color.Black, Color.DarkGray, Color.Gray, Color.LightGray, Color.White,
        Color.Red, Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
        Color.Blue, Color(0xFF03A9F4), Color(0xFF00BCD4), Color(0xFF009688),
        Color.Green, Color(0xFF8BC34A), Color(0xFFCDDC39), Color.Yellow,
        Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722), Color(0xFF795548)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenCustomColor) {
            Icon(Icons.Default.Add, contentDescription = "Color personalizado", tint = NbAccent)
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 16.dp)
        ) {
            items(colors) { color ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = 2.dp,
                            color = if (viewModel.brushColor == color) NbAccent else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { viewModel.updateBrushColor(color) }
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
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tamaño del pincel", style = MaterialTheme.typography.labelMedium, color = NbOnSurfaceMuted)
            Text("${viewModel.strokeWidth.toInt()} px", style = MaterialTheme.typography.labelLarge, color = NbAccent)
        }
        Slider(
            value = viewModel.strokeWidth,
            onValueChange = { viewModel.updateStrokeWidth(it) },
            valueRange = 1f..100f,
            colors = androidx.compose.material3.SliderDefaults.colors(
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
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Capas", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { viewModel.addLayer() }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Nueva Capa")
            }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(300.dp)
        ) {
            items(viewModel.layers.reversed()) { layer ->
                LayerItem(
                    layer = layer,
                    isSelected = viewModel.activeLayerId == layer.id,
                    onSelect = { viewModel.selectLayer(layer.id) },
                    onToggleVisibility = { viewModel.toggleLayerVisibility(layer.id) },
                    onDelete = { viewModel.deleteLayer(layer.id) },
                    onMoveUp = { viewModel.moveLayerUp(layer.id) },
                    onMoveDown = { viewModel.moveLayerDown(layer.id) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun LayerItem(
    layer: Layer,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleVisibility: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) NbAccent.copy(alpha = 0.1f) else NbSurfaceLight)
            .border(
                width = 1.dp,
                color = if (isSelected) NbAccent else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onSelect() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleVisibility) {
            Icon(
                if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = "Visibilidad",
                tint = if (layer.isVisible) NbAccent else NbOnSurfaceMuted
            )
        }
        Text(
            text = layer.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) NbAccent else MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = onMoveUp) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Subir", size(20.dp))
        }
        IconButton(onClick = onMoveDown) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bajar", size(20.dp))
        }
        IconButton(onClick = onDelete, enabled = !layer.isLocked) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red.copy(alpha = 0.7f))
        }
    }
}

fun Modifier.size(size: androidx.compose.ui.unit.Dp): Modifier = this.then(Modifier.size(size))

@Composable
fun ColorPickerDialog(
    onDismiss: () -> Unit,
    initialColor: Color,
    onColorSelected: (Color) -> Unit
) {
    // Implementación simplificada del selector de color
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color personalizado") },
        text = {
            Text("Aquí iría un selector de color más complejo (HSB/RGB).")
        },
        confirmButton = {
            Button(onClick = { onColorSelected(Color.Blue) }) {
                Text("Seleccionar Azul")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
