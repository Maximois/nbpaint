package com.mc.nbpaint.model

import android.graphics.Bitmap

/**
 * Historial unificado: ediciones de píxeles, cambios de estructura de capas y combinar.
 *
 * Vive en su propio archivo porque lo usan tanto el motor de capas como el de pintura:
 * cualquier operación que se pueda deshacer mete aquí una entrada.
 */
sealed interface HistoryEntry {
    /** Una capa con los valores que tenía, para poder volver a ella desde el historial. */
    data class StructuralLayer(
        val id: Int,
        val bitmap: Bitmap,
        val name: String,
        val visible: Boolean,
        val opacity: Float,
        val placed: StructuralPlaced? = null,
        val texts: List<StructuralText> = emptyList(),
        val curves: List<StructuralCurve> = emptyList(),
        /**
         * Posición y tamaño del contenido de la capa en ese momento.
         *
         * Sin esto, mover o estirar una capa y deshacer lo devolvía al sitio y al tamaño
         * equivocados: el historial guardaba dónde estaban los píxeles, pero no cómo estaba
         * puesta la capa, que es lo que decide dónde se ven.
         */
        val transform: LayerTransform = LayerTransform()
    )

    /** Una imagen puesta en una capa, tal como estaba en el momento del historial. */
    data class StructuralPlaced(
        val bitmap: Bitmap,
        val offsetX: Float,
        val offsetY: Float,
        val scale: Float,
        val crop: ObjectBounds? = null
    )

    /**
     * Un texto en una capa, tal como estaba en el momento del historial.
     *
     * Es una copia y no el objeto de verdad: el historial guarda estados pasados, y si
     * compartiera el texto con la capa, deshacer un movimiento dejaría el objeto de siempre con
     * la posición de antes, que es justo lo contrario de lo que tiene que pasar. Con varios
     * textos por capa pasa lo mismo: las copias del historial son todas independientes entre sí
     * y de los textos vivos, o deshacer un texto distinto al que se movió tocaría al que no
     * era.
     */
    data class StructuralText(
        val text: String,
        val offsetX: Float,
        val offsetY: Float,
        val baseSize: Float,
        val colorArgb: Int,
        val typeface: TextTypeface,
        val bold: Boolean,
        val italic: Boolean,
        val align: TextAlign,
        val lineSpacingFactor: Float,
        val boxWidth: Float,
        val scale: Float,
        val crop: ObjectBounds? = null
    )

    /** Una curva en una capa, tal como estaba en el momento del historial. */
    data class StructuralCurve(
        val nodes: List<StructuralNode>,
        val colorArgb: Int,
        val width: Float,
        val scale: Float,
        val crop: ObjectBounds? = null
    )

    /** Un nodo de curva del historial, con la misma razón que en [StructuralText]. */
    data class StructuralNode(
        val x: Float,
        val y: Float,
        val outHandleX: Float,
        val outHandleY: Float,
        val inHandleX: Float,
        val inHandleY: Float,
        val isCorner: Boolean
    )

    data class PixelEdit(val layerId: Int, val bitmap: Bitmap) : HistoryEntry

    data class StructuralEdit(
        val layers: List<StructuralLayer>,
        val activeIndex: Int,
        val width: Int = 0,
        val height: Int = 0
    ) : HistoryEntry

    data class MergeEdit(
        val topLayer: StructuralLayer,
        val insertIndex: Int,
        val bottomLayerId: Int,
        val bottomBitmapBefore: Bitmap,
        val bottomBitmapAfter: Bitmap,
        val activeIndexBefore: Int,
        val activeIndexAfter: Int,
        val forward: Boolean
    ) : HistoryEntry
}
