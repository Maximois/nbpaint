package com.mc.nbpaint.data

import com.mc.nbpaint.model.LayerTransform
import com.mc.nbpaint.model.ObjectBounds

/**
 * Cómo se escribe y se lee el archivo que describe un proyecto.
 *
 * El formato es de texto plano a propósito: si un proyecto se daña, se puede abrir con
 * un editor y arreglar. Son líneas separadas por saltos de línea, y los campos de cada capa
 * van separados por `|`.
 *
 *
 * ```
 * ancho,alto,capaActiva
 * archivo.png|Nombre|visible|opacidad|imagenPuesta|posX|posY|escala
 * ```
 *
 * Las últimas cuatro se añadieron después: los proyectos anteriores tienen solo cuatro campos
 * y se siguen leyendo igual. Por eso [decode] salta las líneas raras en vez de fallar.
 *
 * Todo esto es texto y números, sin Android, así que se puede probar en la JVM. Es
 * justamente lo que se necesita: este archivo es el que decide si al abrir el proyecto se
 * recuperan las capas con las fotos puestas o se abre en blanco.
 */
object ProjectManifest {

    const val MANIFEST_NAME = "manifest.txt"
    private const val FIELD = '|'
    private const val LAYER_FIELDS_MIN = 4
    private const val LAYER_FIELDS_WITH_PLACED = 8

    /**
     * La ficha de una capa: dónde están sus archivos y cómo se muestra.
     *
     * Lo que no cabe en números, como el texto escrito o la lista de nodos de una curva, va
     * en un archivo aparte cuyo nombre se guarda aquí. La alternativa, meterlo todo en esta
     * línea separando con `|`, se descarta por el texto: un texto con salto de línea partía
     * la línea del manifiesto en dos y la capa se perdía entera al abrir el proyecto.
     */
    data class Layer(
        val fileName: String,
        val name: String,
        val visible: Boolean,
        val opacity: Float,
        val placedFileName: String? = null,
        val placedX: Float? = null,
        val placedY: Float? = null,
        val placedScale: Float? = null,
        /** Archivo con el texto de la capa, o null si no tiene texto. */
        val textFileName: String? = null,
        /** Archivo con los nodos de la curva de la capa, o null si no tiene curva. */
        val curveFileName: String? = null,
        /**
         * Zona recortada de la imagen puesta, o null si se guarda entera.
         *
         * Son cuatro números más al final de la línea, y solo se escriben si hay recorte: un
         * proyecto guardado antes de que existiera se sigue abriendo con su foto.
         */
        val placedCrop: ObjectBounds? = null,
        /**
         * Posición y tamaño del contenido de la capa.
         *
         * Son tres números al final de la línea, y solo se escriben si la capa está movida o
         * estirada: un proyecto guardado antes de esto se abre con sus capas en su sitio, que
         * es como estaban todas entonces.
         */
        val transform: LayerTransform = LayerTransform()
    ) {
        /** ¿Trae imagen puesta con sus tres números completos? */
        val hasPlaced: Boolean
            get() = !placedFileName.isNullOrEmpty() && placedX != null && placedY != null && placedScale != null
    }

    /** El proyecto entero, en texto. */
    data class Project(
        val width: Int,
        val height: Int,
        val activeIndex: Int,
        val layers: List<Layer>
    )

    /**
     * Deja un nombre que no rompe el archivo.
     *
     * Los campos van separados por `|`, así que un `|` en el nombre desalinearía todo lo que
     * viene después: la capa volvía invisible y se perdía la foto que tenía puesta. El salto
     * de línea también rompería el formato, porque separa las capas.
     */
    fun safeName(name: String): String =
        name.replace('|', ',').replace('\n', ' ').replace('\r', ' ').trim().take(80)

    /** Escribe el proyecto como texto. */
    fun encode(project: Project): String = buildString {
        appendLine("${project.width},${project.height},${project.activeIndex}")
        project.layers.forEach { layer ->
            // Los cuatro huecos de la imagen puesta se escriben siempre, vacíos si no la hay.
            // Dejarlos como un hueco solo en vez de cuatro movía todo lo que viene detrás: el
            // nombre del archivo de texto caía donde se esperaba la escala de la foto y el
            // texto desaparecía al abrir el proyecto.
            val placed = if (layer.hasPlaced) {
                "${layer.placedFileName}$FIELD${layer.placedX}$FIELD${layer.placedY}$FIELD${layer.placedScale}"
            } else {
                "$FIELD$FIELD$FIELD"
            }
            val text = layer.textFileName?.takeIf { it.isNotEmpty() }.orEmpty()
            val curve = layer.curveFileName?.takeIf { it.isNotEmpty() }.orEmpty()
            val crop = layer.placedCrop
            // El recorte son cuatro números al final de la línea, con su barra delante. La
            // barra va siempre que haya recorte y los números nunca sin ella: si se escribieran
            // los números pegados al nombre de la curva, el final de la línea se leería como un
            // nombre de archivo largo y el recorte se perdía al abrir el proyecto.
            val cropTexto = crop?.let {
                "$FIELD${it.left}$FIELD${it.top}$FIELD${it.right}$FIELD${it.bottom}"
            }.orEmpty()
            // Lo mismo con la transformación de la capa: se escribe solo si la capa está
            // movida o estirada. Con la identidad no se escribe nada: la capa se abre en su
            // sitio, que es como se guardaban todas las capas antes de que esto existiera.
            val transform = layer.transform
            val transformTexto = if (transform.esIdentity) {
                ""
            } else {
                "$FIELD${transform.offsetX}$FIELD${transform.offsetY}$FIELD${transform.scale}"
            }
            appendLine(
                "${layer.fileName}$FIELD${safeName(layer.name)}$FIELD${layer.visible}" +
                    "$FIELD${layer.opacity}$FIELD$placed$FIELD$text$FIELD$curve$cropTexto" +
                    transformTexto
            )
        }
    }

    /**
     * Lee el proyecto de un texto.
     *
     * Devuelve null si la cabecera no se entiende, y se salta las capas que no se pueden
     * leer. Es a propósito: antes un solo número raro en la cabecera revienta la lectura
     * entera y el usuario perdía el proyecto sin explicación. Ahora se pierde lo que esté
     * roto y se conserva el resto.
     */
    fun decode(text: String): Project? {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val header = lines[0].split(",").map { it.trim() }
        if (header.size < 3) return null
        val width = header[0].toIntOrNull() ?: return null
        val height = header[1].toIntOrNull() ?: return null
        val active = header[2].toIntOrNull() ?: return null
        if (width <= 0 || height <= 0) return null

        val layers = lines.drop(1).mapNotNull { readLayer(it) }
        return Project(width, height, active, layers)
    }

    private fun readLayer(line: String): Layer? {
        val parts = line.split(FIELD)
        if (parts.size < LAYER_FIELDS_MIN) return null
        val fileName = parts[0].trim()
        if (fileName.isEmpty()) return null
        val opacity = parts[3].trim().toFloatOrNull() ?: 1f
        if (parts.size < LAYER_FIELDS_WITH_PLACED) {
            // Proyecto viejo: cuatro campos y nada de imagen puesta.
            return Layer(fileName, parts[1], parts[2].trim() == "true", opacity)
        }
        val placedName = parts[4].trim()
        val placedX = parts[5].trim().toFloatOrNull()
        val placedY = parts[6].trim().toFloatOrNull()
        val placedScale = parts[7].trim().toFloatOrNull()
        val completo = placedName.isNotEmpty() && placedX != null && placedY != null && placedScale != null
        // Los dos campos últimos llegaron después que todo lo demás, así que se leen solo si
        // están: un proyecto guardado con la versión anterior se sigue abriendo con su foto.
        val textName = parts.getOrNull(8)?.trim()?.takeIf { it.isNotEmpty() }
        val curveName = parts.getOrNull(9)?.trim()?.takeIf { it.isNotEmpty() }
        // El recorte son cuatro números a partir del campo 10: el 9 es el nombre del archivo de
        // curvas y el 10 es el primer número, no una barra suelta. Leerlo un campo más allá
        // hacía que el recorte no volviera nunca y saliera null.
        val crop = leerCrop(parts, 10)
        // Y la transformación de la capa, tres números más detrás del recorte. Solo se leen si
        // están: un proyecto guardado antes de esto se sigue abriendo con sus capas en su
        // sitio, sin transformación y sin avisos.
        val transform = leerTransform(parts, if (crop != null) 14 else 10)
        return Layer(
            fileName = fileName,
            name = parts[1],
            visible = parts[2].trim() == "true",
            opacity = opacity,
            placedFileName = placedName.takeIf { completo },
            placedX = placedX.takeIf { completo },
            placedY = placedY.takeIf { completo },
            placedScale = placedScale.takeIf { completo },
            textFileName = textName,
            curveFileName = curveName,
            placedCrop = crop,
            transform = transform
        )
    }

    /**
     * Lee la zona recortada de una línea de campos, a partir de [desde].
     *
     * Si no están los cuatro números, o si alguno está vacío o es raro, se devuelve null y la
     * imagen se abre entera. Perder el recorte es un Cosmetics; perder la foto entera porque
     * el recorte traía un número raro sería un desastre.
     */
    private fun leerCrop(parts: List<String>, desde: Int): ObjectBounds? {
        if (parts.size < desde + 4) return null
        val numeros = (desde until desde + 4).map { parts[it].trim().toFloatOrNull() ?: return null }
        if (numeros.any { !it.isFinite() }) return null
        val zona = ObjectBounds(numeros[0], numeros[1], numeros[2], numeros[3])
        return if (zona.isEmpty) null else zona
    }

    /**
     * Lee la transformación de una capa: tres números, desplazamiento en X, en Y y escala.
     *
     * Se lee desde el campo que toca según si la línea trae recorte antes, porque las dos cosas
     * van seguidas y el recorte se puede no haber escrito. Un número que no se entiende o una
     * escala nula dejan la capa en su sitio, que es como se guardaron todas hasta que esto
     * existió: es preferible abrir la capa donde se espera a abrirla deformada sin avisar.
     */
    private fun leerTransform(parts: List<String>, desde: Int): LayerTransform {
        if (parts.size < desde + 3) return LayerTransform()
        val numeros = (desde until desde + 3).map { parts[it].trim().toFloatOrNull() ?: return LayerTransform() }
        if (numeros.any { !it.isFinite() } || numeros[2] <= 0f) return LayerTransform()
        return LayerTransform(offsetX = numeros[0], offsetY = numeros[1], scale = numeros[2]).acotada()
    }
}
