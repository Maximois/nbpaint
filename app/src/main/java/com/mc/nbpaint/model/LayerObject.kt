package com.mc.nbpaint.model

/**
 * Un objeto de una capa, de los tres tipos que hay: la imagen puesta, el texto y la curva.
 *
 * Antes cada uno tenía su propia forma de moverse: la imagen puesta tenía su escala y su
 * transformación, y el texto se rasterizaba al colocarlo y a partir de ahí no se podía ni
 * tomar ni mover. Con esta vista común, la mano de mover elige el objeto que toca y trabaja
 * con él sin preguntar de qué tipo es, y añadir un tipo nuevo no obliga a tocar ni la mano ni
 * el compositor.
 *
 * Es una vista, no una copia: el objeto de cada tipo es el de verdad, así que mover o
 * estirar a través de aquí los cambia en el sitio y se ve en pantalla. Además una capa puede
 * tener varios textos y varias curvas, y aquí no se distingue: son el mismo tipo de cosa.
 */
sealed interface LayerObjectRef {

    /** Qué clase de objeto es, para el compositor y para los mensajes de la interfaz. */
    val kind: ObjectKind

    /** Caja que ocupa el objeto ahora mismo, en coordenadas de la hoja. */
    fun bounds(): ObjectBounds

    /** Mueve el objeto. [dx] y [dy] van en píxeles de la hoja. */
    fun moveBy(dx: Float, dy: Float)

    /** Escala que tiene el objeto ahora mismo, ya acotada a un rango usable. */
    fun currentScale(): Float

    /** Cambia la escala, acotada al rango de su tipo. */
    fun applyScale(value: Float)

    /** Lleva la esquina superior izquierda del objeto a ([x], [y]). */
    fun moveTo(x: Float, y: Float)

    /** Escala acotada a un rango usable, para que ni desaparezca ni se infle sin fin. */
    fun scaleCoherente(value: Float): Float

    /**
     * Caja que ocupa el objeto **sin** recortar.
     *
     * Va aparte de [bounds] porque los dos ya no son lo mismo: con el recorte puesto, [bounds]
     * es la parte que se ve, que es por donde hay que poder agarrarlo, y esta es la caja entera,
     * que es la que acota hasta dónde se puede recortar. Con una sola caja, recortar a una
     * esquina dejaría el recorte tan pequeño que la segunda vez ya no se podía ampliar.
     */
    fun fullBounds(): ObjectBounds

    /** Zona recortada del objeto, en coordenadas de la hoja, o null si se ve entero. */
    fun crop(): ObjectBounds?

    /** Pone el recorte en el objeto, o lo quita con null. */
    fun setCrop(value: ObjectBounds?)

    /**
     * La zona de [value] tal como quedaría puesta, o null si no vale.
     *
     * Va aparte de [setCrop] para que quien recorta pueda preguntar **antes** de tocar nada.
     * Si el recorte se guardara siempre, una zona arrastrada medio fuera del objeto se
     * guardaría más grande que el objeto y no se notaría: la imagen se vería entera y el
     * usuario pensaría que el botón no hace nada. Con una zona que no pica nada, peor: el
     * objeto se volvería invisible y ya no habría forma de tomarlo para quitarle el recorte.
     *
     * Por eso devuelve null en vez de guardar un recorte vacío, y por eso no toca el objeto.
     */
    fun recorteValido(value: ObjectBounds?): ObjectBounds? {
        if (value == null) return null
        val acotada = value.interseccion(fullBounds()) ?: return null
        return if (acotada.isEmpty) null else acotada
    }

    /** ¿El punto (en coordenadas de la hoja) cae dentro de la caja del objeto? */
    fun contains(x: Float, y: Float): Boolean = bounds().contains(x, y)

    /**
     * La parte visible del objeto: el recorte si lo hay, y la caja entera si no.
     *
     * Se llama a [fullBounds] y no a [bounds] a propósito, para que un recorte ya puesto no se
     * recorte a sí mismo otra vez al recalcular su propia caja.
     */
    fun visibleBounds(): ObjectBounds {
        val entera = fullBounds()
        val zona = crop() ?: return entera
        return zona.interseccion(entera) ?: ObjectBounds(entera.left, entera.top, entera.left, entera.top)
    }
}

/** Los tipos de objeto que puede tener una capa. */
enum class ObjectKind {
    PLACED,
    TEXT,
    CURVE;

    /** Cómo se llama el objeto en la interfaz, para los mensajes y los botones. */
    val label: String
        get() = when (this) {
            PLACED -> "la imagen"
            TEXT -> "el texto"
            CURVE -> "la curva"
        }
}

/** La imagen puesta, vista como objeto. */
class PlacedObjectRef(val placed: PlacedImage) : LayerObjectRef {
    override val kind = ObjectKind.PLACED

    override fun fullBounds() = ObjectBounds(
        left = placed.offsetX,
        top = placed.offsetY,
        right = placed.offsetX + placed.drawWidth,
        bottom = placed.offsetY + placed.drawHeight
    )

    override fun bounds() = visibleBounds()

    override fun moveBy(dx: Float, dy: Float) {
        placed.offsetX += dx
        placed.offsetY += dy
        placed.crop = placed.crop?.trasladada(dx, dy)
    }

    override fun moveTo(x: Float, y: Float) {
        val dx = x - placed.offsetX
        val dy = y - placed.offsetY
        placed.offsetX = x
        placed.offsetY = y
        placed.crop = placed.crop?.trasladada(dx, dy)
    }

    override fun currentScale(): Float = placed.scale

    override fun applyScale(value: Float) {
        val nueva = scaleCoherente(value)
        val factor = if (placed.scale > 0f) nueva / placed.scale else 1f
        // La esquina superior izquierda se queda clavada al estirar, así que es el punto fijo
        // del recorte. Con otro ancla la zona recortada se despegaría de la foto.
        placed.crop = placed.crop?.escaladaSobre(placed.offsetX, placed.offsetY, factor)
        placed.scale = nueva
    }

    override fun crop(): ObjectBounds? = placed.crop

    override fun setCrop(value: ObjectBounds?) {
        placed.crop = value
    }

    override fun scaleCoherente(value: Float): Float = value.coerceIn(MIN_SCALE, MAX_SCALE)

    companion object {
        /** Escala mínima: con menos la imagen desaparece y no se puede volver a tomar. */
        const val MIN_SCALE = 0.05f

        /** Escala máxima: evita que se infle hasta comerse la memoria. */
        const val MAX_SCALE = 20f
    }
}

/** Un texto, visto como objeto. La caja sale de la medida que el motor de texto dejó en él. */
class TextObjectRef(val text: TextObject) : LayerObjectRef {
    override val kind = ObjectKind.TEXT

    override fun fullBounds() = ObjectBounds(
        left = text.offsetX,
        top = text.offsetY,
        right = text.offsetX + text.boxWidthNow,
        bottom = text.offsetY + text.boxHeightNow
    )

    override fun bounds() = visibleBounds()

    override fun moveBy(dx: Float, dy: Float) {
        text.moveBy(dx, dy)
        text.crop = text.crop?.trasladada(dx, dy)
    }

    override fun moveTo(x: Float, y: Float) {
        moveBy(x - text.offsetX, y - text.offsetY)
    }

    override fun currentScale(): Float = text.scale

    override fun applyScale(value: Float) {
        val nueva = scaleCoherente(value)
        val factor = if (text.scale > 0f) nueva / text.scale else 1f
        // Igual que en la imagen: al estirar por una esquina, la esquina opuesta es la que se
        // queda, y el recorte tiene que estirarse con el mismo factor desde ahí.
        text.crop = text.crop?.escaladaSobre(text.offsetX, text.offsetY, factor)
        text.scale = nueva
        // La caja medida es la del tamaño anterior, así que se descarta: mientras siga
        // puesta, los tiradores de la mano se quedarían en la posición previa y se agarrarían
        // en un sitio donde ya no hay letra. El compositor la vuelve a medir al repintar.
        text.clearMeasured()
    }

    override fun crop(): ObjectBounds? = text.crop

    override fun setCrop(value: ObjectBounds?) {
        text.crop = value
    }

    override fun scaleCoherente(value: Float): Float = text.scaleCoherente(value)
}

/** Una curva, vista como objeto. La caja la calcula ella misma, contando los tiradores. */
class CurveObjectRef(val curve: CurveObject) : LayerObjectRef {
    override val kind = ObjectKind.CURVE

    override fun fullBounds(): ObjectBounds {
        val b = curve.bounds()
        return ObjectBounds(b.left, b.top, b.right, b.bottom)
    }

    override fun bounds() = visibleBounds()

    override fun moveBy(dx: Float, dy: Float) {
        curve.moveBy(dx, dy)
        curve.crop = curve.crop?.trasladada(dx, dy)
    }

    override fun moveTo(x: Float, y: Float) {
        val actual = fullBounds()
        moveBy(x - actual.left, y - actual.top)
    }

    override fun currentScale(): Float = curve.scale

    override fun applyScale(value: Float) {
        val nueva = scaleCoherente(value)
        // La escala se aplica como un factor sobre la geometría que ya hay, no como un valor
        // absoluto. Es la diferencia entre estirar la curva y engordarla: los nodos se separan
        // de verdad, y por eso la caja crece con ellos.
        val factor = if (curve.scale > 0f) nueva / curve.scale else 1f
        // El punto fijo de `scaleGeometry` es el nodo más a la izquierda y arriba, no la caja:
        // la caja lleva el margen del grosor del trazo, y usar su esquina movería el recorte
        // justo medio grosor de trazo, que es lo justo para que se viera descuadrado.
        val caja = fullBounds()
        val margen = curve.drawWidth / 2f
        val anclaX = caja.left + margen
        val anclaY = caja.top + margen
        curve.scaleGeometry(factor)
        curve.scale = nueva
        curve.crop = curve.crop?.escaladaSobre(anclaX, anclaY, factor)
    }

    override fun crop(): ObjectBounds? = curve.crop

    override fun setCrop(value: ObjectBounds?) {
        curve.crop = value
    }

    override fun scaleCoherente(value: Float): Float = curve.scaleCoherente(value)
}

/**
 * Los objetos de una capa, en el orden en que se dibujan: la foto, luego los textos en el
 * orden en que se pusieron, y luego las curvas en el suyo.
 *
 * Se recorre del último al primero cuando se busca qué hay bajo el dedo, que es lo que hace
 * [PaintLayer.objectAt]: lo de arriba es lo que se ve, así que es lo primero que se debe
 * tomar. Un objeto encima de otro no se elige por turnos, sino por el que se ve.
 */
fun PaintLayer.objects(): List<LayerObjectRef> = objectsOf(placed, texts, curves)

/**
 * Los mismos objetos que [PaintLayer.objects], pero sin necesitar la capa entera.
 *
 * Vive aparte porque la capa lleva un `Bitmap` dentro, y eso no se puede construir en un test
 * de JVM sin Android. La regla de qué se dibuja encima de qué y de cuál se coge primero no
 * tiene nada que ver con los píxeles, así que se puede probar sola.
 */
fun objectsOf(
    placed: PlacedImage?,
    texts: List<TextObject>,
    curves: List<CurveObject>
): List<LayerObjectRef> = buildList {
    placed?.let { add(PlacedObjectRef(it)) }
    texts.forEach { add(TextObjectRef(it)) }
    curves.forEach { add(CurveObjectRef(it)) }
}

/** El objeto de la capa que hay bajo el punto (x, y), o null si no hay ninguno. */
fun PaintLayer.objectAt(x: Float, y: Float): LayerObjectRef? =
    objects().lastOrNull { it.contains(x, y) }
