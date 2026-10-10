package com.mc.nbpaint.ui

/**
 * Qué hace cada herramienta, en un solo sitio.
 *
 * El gesto llega siempre por los mismos cuatro pasos (`onStrokeStart`, `onStrokeMove`,
 * `onStrokeEnd` y `onTap`), y cada uno tenía su propio `when` con la lista de herramientas
 * que no hacen nada en ese paso. Con quince herramientas eso eran cuatro listas que había
 * que mantener a mano, y el fallo no se veía: una herramienta nueva que se olvidara en una
 * de las cuatro listas simplemente no respondía a ese gesto, sin error ni aviso, y el
 * síntoma era "el pincel no pinta" o "no puedo rellenar con el dedo".
 *
 * Aquí se declara una vez y los cuatro pasos lo preguntan. Es una decisión de
 * clasificación, no de dibujo: el dibujo sigue donde estaba, que es donde toca a Android.
 *
 * No depende de nada de Android, así que se prueba en la JVM como el resto de los motores.
 */
object ToolPolicy {

    /**
     * Herramientas que eligen una zona en vez de pintar.
     *
     * Se atienden antes de buscar una capa donde pintar, porque no pintan: así también
     * sirven para elegir una zona con la capa oculta o sin capas, que es justo cuando hace
     * falta poder recortar algo que no se está viendo.
     */
    fun isSelection(tool: PaintTool): Boolean =
        tool == PaintTool.SELECTION || tool == PaintTool.SMART_SELECTION

    /**
     * ¿La herramienta necesita una capa para funcionar?
     *
     * Las de selección no, y por eso se saltan ese paso. El resto sí: pintar, mover la
     * imagen puesta o pegar un sello son cosas que van sobre una capa concreta.
     */
    fun needsLayer(tool: PaintTool): Boolean = !isSelection(tool)

    /**
     * Herramientas que hacen algo siguiendo el dedo, y por eso se dibujan al mover.
     */
    fun followsDrag(tool: PaintTool): Boolean = when (tool) {
        PaintTool.BRUSH, PaintTool.ERASER, PaintTool.SPRAY,
        PaintTool.LINE, PaintTool.RECTANGLE, PaintTool.CIRCLE, PaintTool.CURVE,
        PaintTool.CLONE, PaintTool.PATCH, PaintTool.MOVE_IMAGE, PaintTool.MOVE_OBJECT,
        PaintTool.MOVE_LAYER -> true

        PaintTool.BUCKET, PaintTool.MAGIC_WAND, PaintTool.TEXT, PaintTool.EYEDROPPER,
        PaintTool.SELECTION, PaintTool.SMART_SELECTION -> false
    }

    /**
     * Las figuras se dibujan provisionalmente mientras se arrastra y se confirman al
     * soltar. El resto de herramientas que siguen el dedo van directo al lienzo.
     */
    fun isShape(tool: PaintTool): Boolean =
        tool == PaintTool.LINE || tool == PaintTool.RECTANGLE || tool == PaintTool.CIRCLE

    /**
     * Herramientas que reaccionan a un toque suelto, sin arrastrar.
     *
     * El relleno, la varita, el texto y el cuentagotas solo funcionan con un toque. El
     * clonado y el parche también, pero no solo eso: el toque les sirve para marcar la
     * fuente del sello o para aplicar un parche puntual, y seguir funcionando al arrastrar.
     */
    fun actsOnTap(tool: PaintTool): Boolean = when (tool) {
        PaintTool.BUCKET, PaintTool.MAGIC_WAND, PaintTool.EYEDROPPER, PaintTool.TEXT,
        PaintTool.CLONE, PaintTool.PATCH, PaintTool.MOVE_OBJECT,
        PaintTool.SELECTION, PaintTool.SMART_SELECTION -> true

        PaintTool.BRUSH, PaintTool.ERASER, PaintTool.SPRAY,
        PaintTool.LINE, PaintTool.RECTANGLE, PaintTool.CIRCLE, PaintTool.CURVE,
        PaintTool.MOVE_IMAGE, PaintTool.MOVE_LAYER -> false
    }
}
