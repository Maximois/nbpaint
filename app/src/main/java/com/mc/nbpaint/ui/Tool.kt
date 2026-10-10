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
    BUCKET("Bote de pintura"),
    MAGIC_WAND("Varita mágica"),
    TEXT("Texto"),

    /** Cuentagotas: toma el color del píxel que se toque del lienzo. */
    EYEDROPPER("Cuentagotas"),

    /** Mueve y escala la imagen puesta en la capa activa. */
    MOVE_IMAGE("Mover imagen"),

    /**
     * Mano de mover objetos: elige lo que hay bajo el dedo (una imagen puesta o un texto) y
     * lo arrastra o lo estira por los tiradores de su caja, sin soltar la herramienta.
     *
     * Es distinta de [MOVE_IMAGE] en que no se limita a la imagen puesta: elige el objeto
     * que toca y lo mueve sin importar de qué tipo sea.
     */
    MOVE_OBJECT("Mover objeto"),

    /** Sello de clonar: se marca la fuente con un toque y después se pinta copiándola. */
    CLONE("Clonado"),

    /** Parche: copia un punto fijo más arriba y tapa lo que hay abajo, sin configurar nada. */
    PATCH("Parche"),

    /**
     * Curvas: trazo suave con puntos de control, como las curvas de un editor de dibujo.
     *
     * Nace de un trazo del dedo y se corrige después con los tiradores de los puntos, para
     * poder doblar la línea sin volver a dibujarla. Es un objeto de la capa, igual que el
     * texto y la imagen puesta, así que se puede volver a tomar con [MOVE_OBJECT].
     */
    CURVE("Curva"),

    /**
     * Mano de la capa: mueve y escala el contenido de la capa activa, sin tocar las demás.
     *
     * Es lo que da independencia entre capas. Antes el contenido de todas las capas estaba
     * pegado al papel, con la misma escala y el mismo sitio, así que no había manera de
     * agrandar una capa sin mover el resto ni de ajustar una sinaguantar que las demás
     * cambiaran de lugar.
     */
    MOVE_LAYER("Mover capa"),

    /**
     * Selección por marco: se arrastra y define una zona rectangular. En cuanto hay zona,
     * todo lo que se dibuja o se recorta se queda dentro de ella.
     */
    SELECTION("Selección"),

    /**
     * Selección inteligente: toca un color y elige sola la región parecida, con la
     * tolerancia elegida. Se puede mantener pulsado para sumar otra zona.
     */
    SMART_SELECTION("Selección inteligente")
}

/** Cómo se completa una región seleccionada con la varita mágica. */
enum class SmartFillMode(val label: String) {
    /** Rellena la región con el color de la herramienta. */
    COLOR("Con color"),

    /** Rellena la región con el promedio de los píxeles que la rodean: despeja el fondo. */
    SAMPLE("Con el fondo")
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
