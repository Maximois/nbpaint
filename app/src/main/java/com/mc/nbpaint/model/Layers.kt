package com.mc.nbpaint.model

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Datos del documento: capas, imágenes puestas y sus copias para disco e historial.
 *
 * Aquí no hay lógica de dibujo, solo el estado. Las operaciones viven en
 * `com.mc.nbpaint.engine`, y la pantalla en `com.mc.nbpaint.ui`.
 */

/**
 * Una imagen puesta dentro de una capa, como un objeto de Photoshop: se mueve y se escala
 * con el dedo sin perderse lo que ya está pintado en esa capa.
 */
class PlacedImage(
    val bitmap: Bitmap,
    var offsetX: Float,
    var offsetY: Float,
    var scale: Float = 1f
) {
    val drawWidth get() = bitmap.width * scale
    val drawHeight get() = bitmap.height * scale

    /**
     * Zona de la imagen que se sigue viendo, en coordenadas de la hoja, o null si se ve entera.
     *
     * El recorte no toca el bitmap: es una ventana que el compositor respeta al dibujar. Se
     * guarda así, en vez de recortar la imagen de verdad, por dos razones: se puede deshacer y
     * volver a abrir la zona más grande sin haber perdido nada, y ampliar la imagen después no
     * enseña un borde ya mordido donde antes se veía la foto entera.
     *
     * Va en coordenadas de la hoja y no en las de la imagen porque es lo único que sirve para
     * los tres tipos de objeto a la vez, y porque la mano de mover trabaja en esas
     * coordenadas. Quien la mantiene al día es [PlacedObjectRef], que es quien mueve y
     * escala el objeto.
     */
    var crop: ObjectBounds? = null

    /** ¿El punto (en coordenadas de la hoja) cae dentro de la imagen puesta? */
    fun contains(x: Float, y: Float): Boolean =
        x >= offsetX && x <= offsetX + drawWidth && y >= offsetY && y <= offsetY + drawHeight
}

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

    /** Imagen puesta en esta capa, si la hay. Se dibuja encima de lo pintado. */
    var placed by mutableStateOf<PlacedImage?>(null)

    /**
     * Textos de esta capa, en el orden en que se fueron poniendo: se dibujan encima de lo
     * pintado y en ese orden, de abajo arriba.
     *
     * Es una lista y no un solo texto porque con uno solo cada texto nuevo borraba el anterior
     * sin dejar rastro, y para poner un rótulo y un subtítulo había que ponerse a hacerlo en
     * dos capas, que no es lo que se espera de una capa de texto.
     *
     * Es una lista observable porque el compositor la recorre para dibujar: al añadir o quitar
     * un texto, la lista cambia y con ella lo que se ve, sin que nadie tenga que acordarse de
     * pedir un repintado.
     */
    val texts: SnapshotStateList<TextObject> = mutableStateListOf()

    /** Curvas de esta capa, en el mismo orden y por el mismo motivo que [texts]. */
    val curves: SnapshotStateList<CurveObject> = mutableStateListOf()

    /**
     * Posición y tamaño del contenido de esta capa dentro de la hoja.
     *
     * Es lo que hace que las capas tengan independencia: cada una se puede mover y estirar
     * sin tocar las demás, en vez de tener todas la misma escala y el mismo sitio pegadas al
     * papel. Se aplica a los tres tipos de contenido de la capa —lo pintado, la imagen puesta
     * y los textos y curvas— para que la capa se mueva como una sola cosa.
     */
    var transform by mutableStateOf(LayerTransform())
}

/**
 * Copia de un bitmap que se puede volver a modificar.
 *
 * Siempre mutable, a propósito. Un bitmap inmutable no se deja escribir: `setPixels`
 * revienta con IllegalStateException y dibujar sobre un `Canvas` abierto con él se ignora
 * en silencio, así que el pincel, el clonado o un filtro dejarían de hacer nada. Como el
 * historial y el autoguardado devuelven estas copias a las capas, si alguna saliera
 * inmutable la capa quedaría ineditable después de deshacer.
 */
fun Bitmap.editableCopy(): Bitmap = copy(Bitmap.Config.ARGB_8888, true)

data class LayerSnapshot(
    val bitmap: Bitmap,
    val name: String,
    val visible: Boolean,
    val opacity: Float,
    val placed: PlacedSnapshot? = null,
    val texts: List<TextSnapshot> = emptyList(),
    val curves: List<CurveSnapshot> = emptyList(),
    /**
     * Posición y tamaño del contenido de la capa, tal como queda en disco.
     *
     * Por defecto es la identidad, que es como estaban las capas antes de que existiera esto:
     * un proyecto viejo se abre con sus capas en su sitio y sin tener que hacer nada.
     */
    val transform: LayerTransform = LayerTransform()
)

/** Una imagen puesta en una capa, tal como queda en disco. */
data class PlacedSnapshot(
    val bitmap: Bitmap,
    val offsetX: Float,
    val offsetY: Float,
    val scale: Float,
    val crop: ObjectBounds? = null
)
