package com.mc.nbpaint.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Healing


import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.mc.nbpaint.engine.BrushKind
import com.mc.nbpaint.engine.SmudgeMode
import com.mc.nbpaint.ui.theme.NbAccent
import com.mc.nbpaint.ui.theme.NbOnSurface
import com.mc.nbpaint.ui.theme.NbOnSurfaceMuted
import com.mc.nbpaint.ui.theme.NbSurfaceLight

/** Cuántos botones entran en una fila del panel. Cinco, para que ningún grupo ocupe dos. */
private const val BOTONES_POR_FILA = 5

/**
 * Un grupo de herramientas del panel.
 *
 * Antes todas iban en una fila horizontal con scroll. Con quince ya había que deslizar para
 * llegar a las últimas, y cada herramienta nueva empujaba un poco más. Peor: al haber scroll
 * horizontal no se ve de un vistazo qué hay, así que la propia herramienta desaparece del
 * panel en cuanto se dibuja y hay que buscarla otra vez.
 *
 * Agruparlas por tipo resuelve las dos cosas: se ve todo el catálogo sin deslizar, y el
 * título dice dónde está cada cosa.
 *
 * Los grupos salen casi todos plegados porque la fila de la pantalla no da para los cuatro
 * abiertos. Con los cuatro a la vista el último quedaba por debajo del borde y sin scroll no
 * se llegaba: se rectal el panel, no el contenido. Ahora se ve siempre el catálogo entero
 * (una cabecera por grupo) y solo se despliega el grupo que se está usando.
 */
private data class ToolGroup(
    val title: String,
    val tools: List<PaintTool>
)

/**
 * Catálogo de herramientas por tipo.
 *
 * El reparto va por manera de trabajar, no por icono: pintar, dibujar figuras, lo que copia o
 * rellena por su cuenta, y elegir zona. Los dos últimos son modos más que herramientas, y por
 * eso van al final y la selección arranca plegada.
 */
private val gruposDeHerramientas = listOf(
    ToolGroup(
        "Pintura",
        listOf(
            PaintTool.BRUSH,
            PaintTool.ERASER,
            PaintTool.SPRAY,
            PaintTool.BUCKET,
            PaintTool.EYEDROPPER
        )
    ),
    ToolGroup(
        "Formas",
        listOf(
            PaintTool.LINE,
            PaintTool.RECTANGLE,
            PaintTool.CIRCLE,
            PaintTool.CURVE
        )
    ),
    ToolGroup(
        "Objetos",
        listOf(
            PaintTool.TEXT,
            PaintTool.MOVE_OBJECT
        )
    ),
    ToolGroup(
        "Avanzado",
        listOf(
            PaintTool.MAGIC_WAND,
            PaintTool.CLONE,
            PaintTool.PATCH,
            PaintTool.MOVE_IMAGE,
            PaintTool.MOVE_LAYER
        )
    ),
    ToolGroup(
        "Selección",
        listOf(
            PaintTool.SELECTION,
            PaintTool.SMART_SELECTION
        )
    )
)

/**
 * Icono de la goma de borrar.
 *
 * Material solo trae una tecla de retroceso, que no se parece a una goma. Este vector está
 * dibujado a mano (una goma inclinada con el borde de la banda) en tonos grises de la paleta,
 * para que no desentone con el resto de los iconos. Al tener varias caras no se tiñe con el
 * color único de los demás: se dibuja sin tinte, así que sus grises salen tal cual.
 *
 * Se dibuja más grande que los iconos de Material (30dp en vez de 24) y la geometría llena la
 * caja casi entera, porque la goma es inclinada y a tamaño normal parecía un puntito.
 */
private val EraserIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Eraser",
        defaultWidth = 30.dp,
        defaultHeight = 30.dp,
        viewportWidth = 100f,
        viewportHeight = 100f
    ).apply {
        val contorno = SolidColor(ContornoGoma)

        // Caras claras de la goma, con tono distinto según la luz que les toque.
        path(fill = SolidColor(Color(0xFFECECF4))) {
            moveTo(8.5f, 51.8f); lineTo(31.4f, 38.3f); lineTo(53f, 50.4f); lineTo(30f, 63.9f); close()
        }
        path(fill = SolidColor(Color(0xFFECECF4))) {
            moveTo(53f, 26.1f); lineTo(76f, 12.6f); lineTo(97.6f, 26.1f); lineTo(74.6f, 38.3f); close()
        }
        path(fill = SolidColor(Color(0xFFC9C9DC))) {
            moveTo(8.5f, 51.8f); lineTo(30f, 63.9f); lineTo(30f, 77.4f); lineTo(8.5f, 65.3f); close()
        }

        // Banda central: la azul del dibujo original, en dos grises para conservar el sombreado.
        path(fill = SolidColor(Color(0xFFA0A0B8))) {
            moveTo(31.4f, 38.3f); lineTo(53f, 26.1f); lineTo(74.6f, 38.3f); lineTo(53f, 50.4f); close()
        }
        path(fill = SolidColor(Color(0xFF7E7E9A))) {
            moveTo(53f, 50.4f); lineTo(74.6f, 38.3f); lineTo(74.6f, 51.8f); lineTo(53f, 63.9f); close()
        }

        // Contorno exterior de la goma.
        path(
            fill = null,
            stroke = contorno,
            strokeLineWidth = 4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(8.5f, 51.8f); lineTo(31.4f, 38.3f); lineTo(53f, 26.1f); lineTo(76f, 12.6f)
            lineTo(97.6f, 26.1f); lineTo(97.6f, 39.6f); lineTo(74.6f, 51.8f); lineTo(53f, 63.9f)
            lineTo(30f, 77.4f); lineTo(8.5f, 65.3f); close()
        }
        // Aristas internas que separan las caras y la banda.
        path(fill = null, stroke = contorno, strokeLineWidth = 4f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(30f, 63.9f); lineTo(53f, 50.4f); lineTo(74.6f, 38.3f)
        }
        path(fill = null, stroke = contorno, strokeLineWidth = 4f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(31.4f, 38.3f); lineTo(30f, 63.9f)
            moveTo(53f, 26.1f); lineTo(53f, 63.9f)
            moveTo(74.6f, 38.3f); lineTo(74.6f, 51.8f)
        }
        path(fill = null, stroke = contorno, strokeLineWidth = 4f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(8.5f, 51.8f); lineTo(30f, 63.9f)
            moveTo(30f, 63.9f); lineTo(30f, 77.4f)
        }
    }.build()
}

/** Trazo de la goma: un gris oscuro que se distingue sobre las caras claras y la banda. */
private val ContornoGoma = Color(0xFF1E1E2E)

/**
 * Mano abierta, el gesto de "mover esto" en los editores conocidos.
 *
 * Material trae `OpenWith` (cuatro flechas), que más bien dice "ordenar" o "alinear". Como la
 * herramienta elige un objeto y lo arrastra, la mano communicates it better. Es un contorno a
 * una línea, para que se lea a 24 dp junto al resto de iconos.
 */
private val HandIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Hand",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            fill = null
        ) {
            // Palma y dedos: la mano apunta hacia arriba, con los cuatro dedos estirados y
            // el pulgar salir al lado. La palma se cierra por abajo con un arco.
            moveTo(9f, 11.5f)
            lineTo(9f, 5.6f)
            moveTo(12f, 11f)
            lineTo(12f, 4.4f)
            moveTo(15f, 11.2f)
            lineTo(15f, 5.6f)
            moveTo(18f, 12.2f)
            lineTo(18f, 8.4f)
            // Pulgar.
            moveTo(9f, 11.5f)
            lineTo(7.4f, 9.8f)
            curveTo(6.6f, 8.9f, 5.2f, 9.2f, 5f, 10.2f)
            curveTo(4.8f, 11.2f, 5.4f, 12.4f, 6.2f, 13.4f)
            curveTo(7.4f, 15.2f, 9f, 16.6f, 11.2f, 17.4f)
            curveTo(13f, 18.1f, 15f, 18.1f, 16.4f, 17.4f)
            curveTo(17.6f, 16.8f, 18.4f, 15.4f, 18.8f, 13.8f)
            lineTo(19.2f, 12.4f)
        }
    }.build()
}

/**
 * Curva con dos tiradores, que es lo que hace la herramienta.
 *
 * El trazo es el de la curva final y los tiradores, los que salen de ella al corregirla, para
 * que el icono enseñe las dos cosas de un vistazo.
 */
private val CurveIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Curve",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(3.5f, 17.5f)
            curveTo(8.5f, 5.5f, 15.5f, 18.5f, 20.5f, 6.5f)
        }
        path(fill = SolidColor(Color.White)) {
            moveTo(3.5f, 15.9f); lineTo(5.1f, 19.1f); lineTo(1.9f, 19.1f); close()
        }
        path(fill = SolidColor(Color.White)) {
            moveTo(20.5f, 4.9f); lineTo(22.1f, 8.1f); lineTo(18.9f, 8.1f); close()
        }
    }.build()
}

/** El icono de cada herramienta, en un solo sitio para poder recorrer el catálogo con un bucle. */
private fun iconOf(tool: PaintTool): ImageVector = when (tool) {
    PaintTool.BRUSH -> Icons.Default.Brush
    PaintTool.ERASER -> EraserIcon
    PaintTool.SPRAY -> Icons.Default.BlurOn
    PaintTool.BUCKET -> Icons.Default.FormatColorFill
    PaintTool.EYEDROPPER -> Icons.Default.Colorize
    PaintTool.LINE -> Icons.Default.Timeline
    PaintTool.RECTANGLE -> Icons.Default.CropSquare
    PaintTool.CIRCLE -> Icons.Default.Circle
    PaintTool.TEXT -> Icons.Default.TextFields
    PaintTool.MAGIC_WAND -> Icons.Default.AutoFixHigh
    PaintTool.CLONE -> Icons.Default.ContentCopy
    PaintTool.PATCH -> Icons.Default.Healing
    PaintTool.MOVE_IMAGE -> Icons.Default.OpenWith
    PaintTool.MOVE_OBJECT -> HandIcon
    PaintTool.MOVE_LAYER -> Icons.Default.Layers
    PaintTool.CURVE -> CurveIcon
    PaintTool.SELECTION -> Icons.Default.CropSquare
    PaintTool.SMART_SELECTION -> Icons.Default.AutoFixHigh
}

/**
 * Panel de herramientas, agrupado y plegable.
 *
 * Los títulos van en fila, no apilados. Apilados ocupaban unos 270 px de cabecera y con el
 * resto del panel el grupo "Selección" quedaba por debajo del borde de una pantalla de
 * 2312 px, sin forma de llegar. En fila son una sola tira de unos 50 px, y además el panel
 * deja de depender de cuántas herramientas tenga cada grupo: se agrupan en horizontal, no en
 * vertical, así que añadir una ya no hace que el panel crezca hacia abajo.
 *
 * El grupo abierto es siempre el de la herramienta activa, más el que el usuario haya
 * abierto a mano. Si se pudiera dejar cerrado el grupo de la herramienta en uso, al elegir
 * una el botón se escondería justo cuando se está usando.
 */
@Composable
fun ToolPanel(viewModel: DrawingViewModel) {
    val grupoDeLaActiva = gruposDeHerramientas.indexOfFirst { grupo ->
        grupo.tools.any { it == viewModel.currentTool }
    }
    // `null` significa "solo el de la herramienta activa". Es el estado de arranque, y lo
    // guarda `rememberSaveable` para que al girar el móvil no salte de grupo.
    var abiertoManual by rememberSaveable { mutableStateOf(-1) }
    val abierto = when {
        abiertoManual >= 0 -> abiertoManual
        grupoDeLaActiva >= 0 -> grupoDeLaActiva
        else -> 0
    }

    // El panel no lleva ni tope de alto ni scroll propio. Se mide a lo que mide su contenido
    // (unas 72.dp con un grupo abierto) y quien controla el alto y el scroll es el dock, que
    // es quien sabe cuánto le queda de pantalla. Un tope aquí se medía contra el hueco que
    // dejaba el lienzo, y como el dock reparte el espacio con `weight(1f)` ese hueco
    // dependía del propio tope: el panel se quedaba corto y la fila de botones se dibujaba
    // bajo el borde de la ventana, sin forma de llegar.
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            gruposDeHerramientas.forEachIndexed { indice, grupo ->
                GroupTab(
                    title = grupo.title,
                    isOpen = indice == abierto,
                    isActiveGroup = indice == grupoDeLaActiva,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        // Tocar el grupo ya abierto lo vuelve a plegar y deja solo el de la
                        // herramienta activa, que es el que siempre se ve.
                        abiertoManual = if (indice == abierto) -1 else indice
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = abierto in gruposDeHerramientas.indices,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            val grupo = gruposDeHerramientas[abierto]
            Column {
                // Cinco por fila y no cuatro, para que ningún grupo ocupe dos filas: Pintura
                // tiene cinco herramientas y con cuatro era la que desbordaba el panel hacia
                // abajo. Con cinco, cada grupo cabe en una fila.
                grupo.tools.chunked(BOTONES_POR_FILA).forEach { fila ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        fila.forEach { tool ->
                            ToolButton(
                                icon = iconOf(tool),
                                contentDescription = tool.label,
                                isSelected = viewModel.currentTool == tool,
                                onClick = { viewModel.selectTool(tool) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Rellena el hueco de la última fila para que los botones no queden
                        // estirados si la fila va incompleta.
                        repeat(BOTONES_POR_FILA - fila.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                // El relleno de la figura solo tiene sentido con una figura, así que va en
                // su grupo y no suelto por el panel.
                if (viewModel.currentTool == PaintTool.RECTANGLE || viewModel.currentTool == PaintTool.CIRCLE) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        ToolButton(
                            icon = Icons.Default.Edit,
                            contentDescription = "Rellenar figura",
                            isSelected = viewModel.shapeFilled,
                            onClick = viewModel::toggleShapeFilled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** Cabecera de un grupo: título, flecha y un toque para plegarlo o abrirlo. */
@Composable
private fun GroupTab(
    title: String,
    isOpen: Boolean,
    isActiveGroup: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // El grupo abierto va con el color de acento; el que tiene la herramienta activa pero
    // está plegado se distingue con un borde, que es lo único que queda para saber de qué
    // grupo es la herramienta con la que se está pintando.
    val fondo = if (isOpen) NbAccent else NbSurfaceLight
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(fondo)
            .then(
                if (!isOpen && isActiveGroup) {
                    Modifier.border(1.dp, NbAccent, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            // 34.dp en vez de los 48.dp que Material impone por accesibilidad: son 48.dp en un
            // panel al que no le cabían, y con 34.dp la fila de herramientas deja de salirse
            // por debajo de la pantalla. Es el punto más fácil de subir si en una pantalla
            // más alta se quisiera el mínimo cómodo al tacto.
            .height(34.dp)
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = if (isOpen) NbOnSurface else NbOnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Botón de una herramienta del panel.
 *
 * Sin texto debajo, solo el icono. No es renunciar a la etiqueta: el nombre de la herramienta
 * activa ya está en la cabecera del panel, en grande, y repetirlo bajo cada icono costaba
 * unos 70 px por fila. Con cuatro filas de iconos el panel ya no entraba en una pantalla de
 * 2312 px y el último grupo quedaba por debajo del borde, inalcanzable. El nombre largo de
 * algunas ("Aerógrafo", "Varita mágica") además se recortaba a "Bote de p", que es peor que
 * no poner nada: el `contentDescription` sigue diciendo qué es cada botón.
 */
@Composable
private fun ToolButton(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) NbAccent else NbSurfaceLight)
            .clickable(onClick = onClick)
            // Alto fijo para que todas las filas midan lo mismo y el panel no quede torcido.
            .height(34.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            // La goma lleva sus caras en grises: se dibuja sin tinte para que se vean, el
            // resto de los iconos se tiñen con el color del botón como siempre.
            tint = if (icon == EraserIcon) Color.Unspecified else if (isSelected) NbOnSurface else NbOnSurfaceMuted
        )
    }
}

/**
 * Acciones de la zona elegida, como un grupo más del panel.
 *
 * Aquí estaba el origen de la confusión: había dos botones que se llamaban igual "Recortar" y
 * en sitios distintos, uno quitaba lo de fuera de la zona y otro quitaba los márgenes vacíos de
 * la capa. Mismo nombre, dos cosas opuestas, y el que hacía lo esperado dependía de cuál de los
 * dos paneles estuviera abierto.
 *
 * Ahora cada acción vive donde se cumple su condición, y solo aquí van las que necesitan que
 * haya zona. Lo que es de la capa (ajustar al dibujo, vaciar) se queda en la barra de la capa,
 * que aparece siempre, porque esas dos funcionan sin zona ninguna.
 *
 * El nombre de la que recorta dice qué se conserva, que es lo único que de verdad le importa
 * al usuario antes de apretar: "dejar solo la zona" y no "recortar", que no dice si lo que se
 * va es lo de fuera o lo de dentro.
 *
 * Las dos del recorte se separan porque recortan cosas distintas: la hoja entera o solo la capa
 * de arriba. Van en su propia fila y no juntas para que se lea cuál es cuál: "recortar hoja"
 * encoge el papel y lo de alrededor se pierde para siempre, y "dejar solo la zona" deja el
 * papel como estaba.
 */
@Composable
fun SelectionActions(viewModel: DrawingViewModel) {
    if (!viewModel.hasSelection) return
    val limites = viewModel.selectionBounds()

    ActionGroup(title = "Zona") {
        // El recorte de objeto está a medio hacer y necesita un gesto, así que el aviso de
        // unos segundos no sirve: aquí la instrucción se queda hasta que se marque la zona o se
        // cancele. Va dentro del panel y no flotando, como los demás avisos, para no tapar justo
        // la zona que hay que marcar.
        viewModel.recortePendiente?.let { ref ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Arrastra sobre ${ref.kind.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NbAccent,
                    modifier = Modifier.weight(1f)
                )
                ActionButton("Cancelar", Modifier.width(96.dp)) {
                    viewModel.cancelarRecorteDeObjeto()
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        limites?.let {
            Text(
                text = "${it.width} x ${it.height} px",
                style = MaterialTheme.typography.labelSmall,
                color = NbOnSurfaceMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            ActionButton("Todo", Modifier.weight(1f)) { viewModel.selectAll() }
            ActionButton("Invertir", Modifier.weight(1f)) { viewModel.invertSelection() }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            ActionButton("Recortar hoja", Modifier.weight(1f)) {
                viewModel.cropDocumentToSelection()
            }
            ActionButton("Quitar zona", Modifier.weight(1f)) { viewModel.clearSelection() }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            ActionButton("Dejar solo la zona", Modifier.weight(2f), destacado = true) {
                viewModel.cropActiveLayerToSelection()
            }
        }
    }
}

/**
 * Configuración del pincel, plegada y solo con el pincel activo.
 *
 * Va en su propio grupo y no suelto en el panel porque son cinco deslizadores: abiertos
 * se comen el alto del dock y vuelven a dejar las herramientas fuera de pantalla.
 * Plegados, el tipo se lee en la cabecera y se entra solo cuando se va a cambiar algo.
 *
 * Solo sale con el pincel. El borrador usa el mismo motor pero con el borde duro, así que
 * aquí no tendría nada que configurar y sería un panel que ocupa sitio para no hacer nada.
 */
@Composable
fun BrushPanel(viewModel: DrawingViewModel) {
    if (viewModel.currentTool != PaintTool.BRUSH) return
    var abierto by rememberSaveable { mutableStateOf(false) }
    val ajustes = viewModel.brushSettings

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { abierto = !abierto }
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (abierto) "Pincel" else "Pincel · ${ajustes.kind.label}",
                style = MaterialTheme.typography.labelMedium,
                color = NbOnSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (abierto) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (abierto) "Plegar ajustes del pincel" else "Desplegar ajustes del pincel",
                tint = NbOnSurfaceMuted
            )
        }

        AnimatedVisibility(
            visible = abierto,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            Column {
                // Los cinco tipos en dos filas, porque en una sola no caben con el ancho
                // justo que hay y el quinto se salía del panel.
                BrushKind.entries.chunked(3).forEach { fila ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                    ) {
                        fila.forEach { kind ->
                            BrushKindChip(
                                kind = kind,
                                seleccionado = ajustes.kind == kind,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.setBrushKind(kind) }
                            )
                        }
                        repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                // El modo solo aparece en el difuminador. En los demás pinceles el ajuste
                // existiría, se tocaría sin ver nada y el usuario pensaría que está roto.
                if (ajustes.kind == BrushKind.DIFUMINADOR) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                    ) {
                        SmudgeMode.entries.forEach { modo ->
                            SmudgeModeChip(
                                modo = modo,
                                seleccionado = ajustes.difuminado == modo,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.setSmudgeMode(modo) }
                            )
                        }
                    }
                }

                BrushSlider("Intensidad", ajustes.intensidad, "${(ajustes.intensidad * 100).toInt()} %") {
                    viewModel.setBrushIntensidad(it)
                }
                BrushSlider("Caudal", ajustes.flow, "${(ajustes.flow * 100).toInt()} %") {
                    viewModel.setBrushFlow(it)
                }
                // La dureza solo tiene sentido donde hay degradado. En el lápiz y en el
                // trazo duro no hay borde que suavizar, así que el deslizador aparecería
                // sin efecto: el peor tipo de control, el que se mueve y no pasa nada.
                if (ajustes.usaDegradado) {
                    BrushSlider("Dureza", ajustes.dureza, "${(ajustes.dureza * 100).toInt()} %") {
                        viewModel.setBrushDureza(it)
                    }
                }
                BrushSlider("Espaciado", ajustes.espaciado, "${(ajustes.espaciado * 100).toInt()} %") {
                    viewModel.setBrushEspaciado(it)
                }
            }
        }
    }
}

/** Ficha de un tipo de pincel. */
@Composable
private fun BrushKindChip(
    kind: BrushKind,
    seleccionado: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (seleccionado) NbAccent else NbSurfaceLight)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = kind.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (seleccionado) NbOnSurface else NbOnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Ficha del modo del difuminador: arrastrar lo pintado o pintar el color elegido.
 *
 * Va aparte de [BrushKindChip] porque no son lo mismo: una elige la herramienta y la otra
 * cómo se comporta esa herramienta, y mezclar los dos niveles en la misma fila haría
 * pensar que "Pintar color" es un pincel más.
 */
@Composable
private fun SmudgeModeChip(
    modo: SmudgeMode,
    seleccionado: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (seleccionado) NbAccent else NbSurfaceLight)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = modo.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (seleccionado) NbOnSurface else NbOnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Un deslizador de los ajustes del pincel, con su valor a la derecha.
 *
 * El valor se pone en texto y no en la posición porque "Caudal 25 %" y "Caudal 0.25" no
 * dicen lo mismo: con caudal bajo el trazo sale claro y solo si el número se lee como
 * porcentaje se entiende que es a propósito y no un fallo.
 */
@Composable
private fun BrushSlider(
    titulo: String,
    valor: Float,
    texto: String,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.labelSmall,
                color = NbOnSurfaceMuted
            )
            Text(
                text = texto,
                style = MaterialTheme.typography.labelSmall,
                color = NbAccent
            )
        }
        Slider(
            value = valor,
            onValueChange = onChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = NbAccent,
                activeTrackColor = NbAccent,
                inactiveTrackColor = NbSurfaceLight
            )
        )
    }
}

/** Bloque de acciones con su título. No se pliega: son pocas y solo aparece cuando aplican. */
@Composable
private fun ActionGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = NbOnSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        content()
    }
}

/** Botón de texto del panel de acciones. */
@Composable
private fun ActionButton(
    text: String,
    modifier: Modifier = Modifier,
    destacado: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (destacado) NbAccent else NbOnSurfaceMuted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (destacado) NbSurfaceLight else NbSurfaceLight.copy(alpha = 0.5f))
            .border(1.dp, if (destacado) color else NbSurfaceLight, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
