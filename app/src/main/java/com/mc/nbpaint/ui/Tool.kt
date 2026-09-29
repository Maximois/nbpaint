package com.mc.nbpaint.ui

/**
 * Todas las herramientas disponibles en NB-paint.
 */
enum class PaintTool(val label: String) {
    BRUSH("Pincel"),
    ERASER("Borrador"),
    LINE("Línea"),
    RECTANGLE("Rectángulo"),
    CIRCLE("Círculo"),
    SPRAY("Aerógrafo"),
    BUCKET("Bote de pintura")
}

/** Paleta de colores predefinida mostrada en la barra inferior. */
val PresetPalette: List<Long> = listOf(
    0xFF000000, // negro
    0xFFFFFFFF, // blanco
    0xFFE74C3C, // rojo
    0xFFE67E22, // naranja
    0xFFF1C40F, // amarillo
    0xFF2ECC71, // verde
    0xFF1ABC9C, // turquesa
    0xFF3498DB, // azul
    0xFF9B59B6, // violeta
    0xFFE84393, // rosa
    0xFF795548, // marrón
    0xFF7F8C8D  // gris
)
