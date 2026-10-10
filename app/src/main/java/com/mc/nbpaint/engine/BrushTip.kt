package com.mc.nbpaint.engine

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Cómo se dibuja un pincel: el tipo de punta y sus ajustes.
 *
 * Antes el trazo era siempre el mismo: una línea de ancho fijo con el borde duro. Con eso
 * "intensidad" no existe como concepto, porque un trazo o se ve entero o no se ve, y no hay
 * forma de escribir ni de sombrear con el mismo pincel. Aquí la punta es un dato más, y cada
 * ajuste hace una sola cosa.
 *
 * Todo esto es Kotlin puro a propósito: no dibuja nada, solo decide. Dibujar necesita
 * `android.graphics`, y eso no se puede probar en la JVM. Lo que decide un pincel (dónde
 * van los toques y con qué opacidad) sí, y es justo donde están los fallos que no se ven:
 * un espaciado mal calculado deja huecos entre toques o repinta el mismo punto mil veces.
 */
enum class BrushKind(val label: String) {

    /** Redondo de borde duro, el trazo de siempre. Sin degradado: va más rápido y se ve igual. */
    DURO("Duro"),

    /**
     * Redondo con el borde desvanecido hacia fuera. La dureza decide cuánto se difumina:
     * a 1 es un borde limpio y a 0 una mancha. Es el pincel de sombrear y de difuminar.
     */
    SUAVE("Suave"),

    /**
     * Arrastra el color que ya está en la capa en vez de pintar encima. No pone el color
     * elegido: recoge lo que hay bajo el dedo y lo lleva unos píxeles más allá, que es lo
     * que hace un difuminador de verdad. Por eso ignora la opacidad del trazo y usa su
     * propia: lo que se regula es cuánto se desplaza lo ya pintado.
     */
    DIFUMINADOR("Difuminador"),

    /** Trazo ancho y de caudal constante, como un rotulador: se ve igual de fuerte deprisa y despacio. */
    MARCADOR("Marcador"),

    /** Puntos irregulares dentro de cada toque, para que el trazo tenga grano en vez de ser liso. */
    LAPIZ("Lápiz")
}

/**
 * Qué se lleva el difuminador en cada toque.
 *
 * [ARRASTRAR] es el difuminador de verdad: copia el trozo de capa que hay bajo el dedo y
 * lo suelta unos píxel más allá, así que arrastra la forma y no solo el tono.
 *
 * [COLOR] cambia de dónde sale lo que se extiende: en vez de recoger lo que ya está
 * pintado, pinta el color elegido con la misma caída suave. Sirve para teñir una zona con
 * un color concreto sin sacar lo que hay de debajo, que con [ARRASTRAR] no se puede: o se
 * arrastra el color que ya estaba, o se pinta encima de golpe con el pincel duro.
 */
enum class SmudgeMode(val label: String) {
    ARRASTRAR("Arrastrar"),
    COLOR("Pintar color")
}

/**
 * Los ajustes de un pincel, ya normalizados.
 *
 * Todos los valores van de 0 a 1 menos el tamaño, que va en píxeles, porque así el motor no
 * tiene que decidir si un 60 significa 60 píxeles o 60 por ciento. Normalizarlo en un solo
 * sitio también evita el fallo clásico de tener un 100 en un sitio y un 1.0 en otro.
 *
 * @param size diámetro del toque, en píxeles de la hoja.
 * @param intensidad cuánto tapa el trazo en total. A 0 no se ve nada, y es el ajuste que
 *        la gente busca cuando dice que el pincel "pinta muy fuerte".
 * @param flow cuánto tapa **cada toque**. Es lo que hace que un trazo rápido salga claro y
 *        uno lento salga oscuro: donde se solapan los toques, el color se acumula.
 * @param dureza para los pinceles suaves, de 0 (mancha) a 1 (borde limpio).
 * @param espaciado separación entre toques, como parte del diámetro. Un 0.15 pone quince
 *        toques por cada diámetro, que es lo bastante seguido para que no se noten los
 *        bordes. A 1 solo hay un toque por diámetro y el trazo sale a puntos.
 * @param difuminado qué extiende el difuminador: lo que ya está pintado o el color elegido.
 */
data class BrushSettings(
    val kind: BrushKind = BrushKind.DURO,
    val size: Float = 16f,
    val intensidad: Float = 1f,
    val flow: Float = 1f,
    val dureza: Float = 0.5f,
    val espaciado: Float = 0.15f,
    val difuminado: SmudgeMode = SmudgeMode.ARRASTRAR
) {
    /** El settings de un pincel duro no tiene sentido con dureza ni con espaciado fino. */
    val usaDegradado: Boolean
        get() = kind != BrushKind.DURO && kind != BrushKind.LAPIZ &&
            (kind != BrushKind.DIFUMINADOR || difuminado == SmudgeMode.COLOR)

    /** Radio del núcleo opaco, en píxeles. El degradado empieza aquí. */
    val radioNucleo: Float
        get() = size / 2f * dureza.coherente()

    /**
     * Opacidad de un toque suelto: intensidad por caudal.
     *
     * Se multiplican y no se elige una u otra porque son dos cosas distintas que la gente
     * confunde: la intensidad baja el trazo entero, y el caudal deja que el trazo se acumule
     * donde pasa más rápido. Un pincel al 100% de intensidad y 20% de caudal es un pincel
     * claro que se oscurece en las curvas, que es justo el pincel de sombrear.
     */
    fun alfaPorToque(): Float = (intensidad * flow).coherente()

    /**
     * Cuánto desplaza un difuminador lo que ya está pintado, por toque.
     *
     * Se toma de la intensidad porque es lo que el usuario está tocando cuando nota que el
     * difuminador "no hace nada" o "borra todo": con intensidad baja apenas mueve el color y
     * con intensidad alta se lo lleva entero.
     */
    fun fuerzaDeDifuminado(): Float = intensidad.coherente()

    private fun Float.coherente(): Float = this.coerceIn(0f, 1f)
}

/**
 * Dónde poner los toques de un trazo y con qué opacidad.
 *
 * Separado de `BrushEngine` a propósito: aquí no hay `Canvas` ni `Paint`, solo la cuenta de
 * dónde va cada toque, y eso se prueba en la JVM sin emulador.
 */
object BrushTip {

    /** Separación mínima entre toques, como parte del diámetro, para que un trazo no sea un punteado. */
    const val ESPACIADO_MINIMO = 0.02f

    /**
     * Puntos donde se estampa el pincel al recorrer el segmento de ([fromX], [fromY]) a
     * ([toX], [toY]), en pares x,y dentro de un `FloatArray`.
     *
     * Se devuelve un array llano y no una lista de `Offset` a propósito: esto se llama en
     * cada movimiento del dedo, y en un trazo largo son miles de llamadas. Un array de
     * primitivos no asigna un objeto por punto, que es lo que hace que un pincel suave no
     * se note más lento que uno duro.
     *
     * El primer y el último punto siempre están: si el dedo se para justo al empezar, tiene
     * que verse algo, y un trazo sin ningun toque en el origen se ve como que no pinta.
     */
    fun toques(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        settings: BrushSettings
    ): FloatArray {
        val radio = max(settings.size / 2f, 0.5f)
        val paso = max(radio * 2f * settings.espaciado.coerceAtLeast(ESPACIADO_MINIMO), 0.5f)

        val distancia = hypot((toX - fromX).toDouble(), (toY - fromY).toDouble()).toFloat()

        // Un toque suelto, o un segmento más corto que el paso: uno en el medio. Ponerlo en el
        // medio y no en el origen evita que dos toques seguidos muy juntos se apilen y dejen el
        // extremo del trazo más oscuro que el resto.
        if (distancia < paso) return floatArrayOf((fromX + toX) / 2f, (fromY + toY) / 2f)

        val cuantos = (distancia / paso).toInt() + 1
        val salida = FloatArray(cuantos * 2)
        val ux = (toX - fromX) / distancia
        val uy = (toY - fromY) / distancia
        for (i in 0 until cuantos) {
            val d = min(i * paso, distancia)
            salida[i * 2] = fromX + ux * d
            salida[i * 2 + 1] = fromY + uy * d
        }
        // El último punto exacto, para que el trazo llegue justo al dedo en vez de quedarse
        // a medio paso: con un espaciado grande la diferencia se nota como un rabito.
        salida[(cuantos - 1) * 2] = toX
        salida[(cuantos - 1) * 2 + 1] = toY
        return salida
    }

    /**
     * Opacidad de un píxel a distancia [distancia] del centro del toque, con radio [radio].
     *
     * De 1 en el centro a 0 en el borde, con el núcleo opaco de radio [nucleo] en medio.
     * La caída va al cuadrado y no en línea recta porque en recta el degradado se ve como un
     * aro: el ojo nota el cambio de pendiente en el punto donde la curva se dobla.
     */
    fun caida(distancia: Float, radio: Float, nucleo: Float): Float {
        if (radio <= 0f) return 0f
        val t = distancia / radio
        if (t >= 1f) return 0f
        if (t <= 0f) return 1f
        if (nucleo >= radio) return 1f
        if (distancia <= nucleo) return 1f
        val relativo = ((distancia - nucleo) / (radio - nucleo)).coerceIn(0f, 1f)
        val resto = 1f - relativo
        return resto * resto
    }

    /**
     * Puntos del degradado radial de un toque suave: de dónde a dónde y con qué opacidad.
     *
     * Se devuelven en tres arrays planos para poder pasarlos tal cual a `RadialGradient` sin
     * crear objetos intermedios. Los tres son del mismo tamaño, y `tope == 1f` es el final.
     */
    fun paradasGradiente(nucleo: Float, radio: Float): GradientStops {
        val posiciones = floatArrayOf(0f, 0f, 0.5f, 1f)
        if (nucleo <= 0f) {
            // Sin núcleo opaco (dureza 0): todo el degradado cae desde el centro.
            return GradientStops(posiciones.copyOf(), floatArrayOf(1f, 1f, 0.25f, 0f))
        }
        if (nucleo >= radio) return GradientStops(floatArrayOf(0f, 1f), floatArrayOf(1f, 1f))
        // (1-t)^2 con nucleo: en el núcleo opacidad 1, y de ahí al borde cayendo al cuadrado.
        val medio = nucleo + (radio - nucleo) * 0.5f
        return GradientStops(
            floatArrayOf(0f, nucleo / radio, medio / radio, 1f),
            floatArrayOf(1f, 1f, 0.25f, 0f)
        )
    }

    /** Las paradas de un degradado radial, en la forma que los quiere `RadialGradient`. */
    class GradientStops(val posiciones: FloatArray, val opacidades: FloatArray)
}
