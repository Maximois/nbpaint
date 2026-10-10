package com.mc.nbpaint.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import com.mc.nbpaint.model.LayerSnapshot
import com.mc.nbpaint.model.PlacedSnapshot
import com.mc.nbpaint.model.editableCopy
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Un proyecto tal como sale del disco: las medidas, la capa activa y las capas con sus
 * imágenes ya cargadas en memoria.
 */
data class LoadedProject(
    val width: Int,
    val height: Int,
    val activeIndex: Int,
    val layers: List<LayerSnapshot>
)

/**
 * Guardar y abrir proyectos: la carpeta, el archivo de texto y el historial de autoguardados.
 *
 * El formato y los nombres de campo están en [ProjectManifest], que es texto puro y se
 * prueba aparte. Acá solo se mueven archivos, y cada paso se puede fallar sin dejar la
 * carpeta a medias: se escribe en una carpeta temporal y se cambia por la buena al final, así
 * un autoguardado que se interrumpe no pisa el último proyecto bueno.
 */
object ProjectStore {

    /**
     * Escribe el proyecto en una carpeta propia, replacing lo que hubiera.
     *
     * Se arma en una carpeta con otro nombre y al final se borra la vieja y se renombra:
     * si el proceso se muere escribiendo, el proyecto que ya estaba sigue intacto en vez de
     * quedar con la mitad de las capas.
     */
    fun writeTo(
        dir: File,
        layers: List<LayerSnapshot>,
        width: Int,
        height: Int,
        activeIndex: Int
    ): Boolean = runCatching {
        val borrador = File(dir.parentFile, "${dir.name}.tmp")
        if (borrador.exists()) borrador.deleteRecursively()
        borrador.mkdirs()

        val manifest = ProjectManifest.Project(
            width = width,
            height = height,
            activeIndex = activeIndex,
            layers = layers.mapIndexed { index, layer -> manifestLayer(index, layer) }
        )

        // Las imágenes van primero: si alguna falla, todavía no se escribió el manifiesto y la
        // carpeta a medias se descarta entera. Los textos y las curvas van en la misma pasada
        // por la misma razón: si fallan, tampoco se ha escrito el manifiesto.
        layers.forEachIndexed { index, layer ->
            writePng(File(borrador, "layer_$index.png"), layer.bitmap)
            layer.placed?.let { writePng(File(borrador, "layer_${index}_placed.png"), it.bitmap) }
            // Un archivo por capa con todos sus textos, y otro con todas sus curvas. Con un
            // archivo por objeto habría que renumerarlos al borrar uno, y el manifiesto guardaría
            // una lista de nombres en vez de dos campos.
            if (layer.texts.isNotEmpty()) {
                File(borrador, "layer_${index}_text.txt")
                    .writeText(ObjectRecords.encodeTexts(layer.texts))
            }
            if (layer.curves.isNotEmpty()) {
                File(borrador, "layer_${index}_curve.txt")
                    .writeText(ObjectRecords.encodeCurves(layer.curves))
            }
        }
        File(borrador, ProjectManifest.MANIFEST_NAME).writeText(ProjectManifest.encode(manifest))

        dir.deleteRecursively()
        borrador.renameTo(dir) || borrador.copyRecursively(dir, overwrite = true)
    }.getOrElse { false }

    private fun manifestLayer(index: Int, layer: LayerSnapshot) = ProjectManifest.Layer(
        fileName = "layer_$index.png",
        name = layer.name,
        visible = layer.visible,
        opacity = layer.opacity,
        placedFileName = if (layer.placed != null) "layer_${index}_placed.png" else null,
        placedX = layer.placed?.offsetX,
        placedY = layer.placed?.offsetY,
        placedScale = layer.placed?.scale,
        textFileName = if (layer.texts.isNotEmpty()) "layer_${index}_text.txt" else null,
        curveFileName = if (layer.curves.isNotEmpty()) "layer_${index}_curve.txt" else null,
        placedCrop = layer.placed?.crop,
        // Y dónde y con qué tamaño estaba la capa. Sin esto la capa se abriría en su sitio
        // aunque estuviera movida o estirada, y el usuario perdería el trabajo de haberle
        // dado su propia posición sin que nada lo dijera.
        transform = layer.transform
    )

    private fun writePng(file: File, bitmap: Bitmap) {
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    }

    /**
     * Lee el proyecto de una carpeta, o null si no hay nada guardado o no se entiende.
     *
     * Las capas se copian a bitmaps mutables al leerlas: `BitmapFactory` devuelve inmutables
     * y una capa inmutable no se puede modificar, así que el proyecto se abriría pero no se
     * podría pintar encima.
     */
    fun readFrom(dir: File): LoadedProject? {
        val manifestFile = File(dir, ProjectManifest.MANIFEST_NAME)
        if (!manifestFile.exists()) return null
        val manifest = runCatching { ProjectManifest.decode(manifestFile.readText()) }.getOrNull() ?: return null

        val layers = manifest.layers.mapNotNull { entry ->
            val bitmap = decodePng(File(dir, entry.fileName)) ?: return@mapNotNull null
            val placed = if (entry.hasPlaced) {
                decodePng(File(dir, entry.placedFileName!!))?.let { bmp ->
                    PlacedSnapshot(bmp, entry.placedX!!, entry.placedY!!, entry.placedScale!!, entry.placedCrop)
                }
            } else {
                null
            }
            // Un objeto que no se puede leer se deja fuera, en vez de tirar el proyecto entero:
            // un texto con un archivo corrupto no puede impedir abrir las demás capas con sus
            // imágenes, que es lo que se recupera de verdad. Y dentro del archivo, un registro
            // roto se salta sin perder los que sí se entienden, que es lo mismo: si el segundo
            // texto de tres no se puede leer, los otros dos tienen que salir igual.
            val texts = entry.textFileName
                ?.let { readObject(File(dir, it), ObjectRecords::decodeTexts) }
                .orEmpty()
            val curves = entry.curveFileName
                ?.let { readObject(File(dir, it), ObjectRecords::decodeCurves) }
                .orEmpty()
            LayerSnapshot(
                bitmap, entry.name, entry.visible, entry.opacity, placed, texts, curves,
                transform = entry.transform
            )
        }
        if (layers.isEmpty()) return null
        return LoadedProject(manifest.width, manifest.height, manifest.activeIndex, layers)
    }

    private fun decodePng(file: File): Bitmap? =
        if (!file.exists()) null
        else runCatching { BitmapFactory.decodeFile(file.absolutePath)?.editableCopy() }.getOrNull()

    /** Lee un archivo de objeto (texto o curva) y lo convierte, o null si no se puede. */
    private fun <T> readObject(file: File, decode: (String) -> T?): T? =
        if (!file.exists()) null
        else runCatching { decode(file.readText()) }.getOrNull()

    /**
     * Escribe un autoguardado nuevo y devuelve en qué número quedó, descartando los viejos
     * que ya no caben.
     */
    fun writeAutosave(
        root: File,
        layers: List<LayerSnapshot>,
        width: Int,
        height: Int,
        activeIndex: Int,
        maxAutosaves: Int = StorageLayout.MAX_AUTOSAVES
    ): Int {
        if (!root.exists()) root.mkdirs()
        val usadas = usedSlots(root)
        // Se numera a partir de las carpetas que ya hay, así el más reciente queda al final
        // y se puede mostrar como historial.
        var siguiente = 1
        while (siguiente in usadas) siguiente++
        writeTo(File(root, siguiente.toString()), layers, width, height, activeIndex)
        usadas.add(siguiente)
        usadas.sorted().take((usadas.size - maxAutosaves).coerceAtLeast(0)).forEach { vieja ->
            File(root, vieja.toString()).deleteRecursively()
            usadas.remove(vieja)
        }
        return siguiente
    }

    /** Los puntos de autoguardado que hay de verdad, con su manifiesto completo. */
    fun listAutosaves(root: File): List<Int> {
        if (!root.exists()) return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory && File(it, ProjectManifest.MANIFEST_NAME).exists() }
            ?.mapNotNull { it.name.toIntOrNull() }
            ?.sortedDescending()
            .orEmpty()
    }

    private fun usedSlots(root: File): MutableSet<Int> =
        root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { it.name.toIntOrNull() }
            ?.toMutableSet()
            ?: mutableSetOf()

    fun deleteAutosave(root: File, slot: Int) {
        File(root, slot.toString()).deleteRecursively()
    }

    fun clearAutosaves(root: File) {
        root.deleteRecursively()
    }

    // ---- Marcador de sesión ---------------------------------------------------

    /**
     * Dice "había trabajo sin terminar" y en qué autoguardado está.
     *
     * Android destruye la pantalla (y con ella el ViewModel) cuando apaga la pantalla o aprieta
     * la memoria, aunque el proceso siga vivo, y también mata el proceso entero en segundo
     * plano. En los dos casos lo que había en el lienzo se pierde y el usuario vuelve a un
     * Hoja en blanco. Para eso está esto.
     */
    fun markSessionOpen(root: File, slot: Int) {
        if (!root.exists()) root.mkdirs()
        File(root, StorageLayout.SESSION_MARKER).writeText(slot.toString())
    }

    /** El autoguardado que quedó a medias, o null si la app se cerró a propósito. */
    fun interruptedSlot(root: File): Int? {
        val marker = File(root, StorageLayout.SESSION_MARKER)
        if (!marker.exists()) return null
        val slot = marker.readText().trim().toIntOrNull()
        if (slot == null) {
            marker.delete()
            return null
        }
        // El marcador puede quedar de una versión anterior que ya no existe: se limpia para no
        // intentar recuperar una y otra vez lo mismo.
        if (!File(root, slot.toString()).let { File(it, ProjectManifest.MANIFEST_NAME).exists() }) {
            marker.delete()
            return null
        }
        return slot
    }

    /** Llamado cuando el usuario cierra la app a propósito: la próxima abre limpio. */
    fun closeSession(root: File) {
        File(root, StorageLayout.SESSION_MARKER).delete()
    }

    // ---- Compartir -----------------------------------------------------------

    /**
     * Empaqueta el proyecto completo en un `.nbpaint` (un zip) y devuelve la `Uri` para
     * mandarlo a otra app.
     */
    fun exportZip(
        context: Context,
        layers: List<LayerSnapshot>,
        width: Int,
        height: Int,
        activeIndex: Int
    ) = runCatching {
        val exportDir = File(context.cacheDir, "project_exports").apply { mkdirs() }
        val zipFile = File(exportDir, "NB-paint_${ImageIo.timestamp()}.nbpaint")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            val manifest = ProjectManifest.Project(
                width = width,
                height = height,
                activeIndex = activeIndex,
                layers = layers.mapIndexed { index, layer -> manifestLayer(index, layer) }
            )
            layers.forEachIndexed { index, layer ->
                putPng(zip, "layer_$index.png", layer.bitmap)
                layer.placed?.let { putPng(zip, "layer_${index}_placed.png", it.bitmap) }
                if (layer.texts.isNotEmpty()) {
                    putText(zip, "layer_${index}_text.txt", ObjectRecords.encodeTexts(layer.texts))
                }
                if (layer.curves.isNotEmpty()) {
                    putText(zip, "layer_${index}_curve.txt", ObjectRecords.encodeCurves(layer.curves))
                }
            }
            zip.putNextEntry(ZipEntry(ProjectManifest.MANIFEST_NAME))
            zip.write(ProjectManifest.encode(manifest).toByteArray())
            zip.closeEntry()
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    }.getOrNull()

    private fun putPng(zip: ZipOutputStream, name: String, bitmap: Bitmap) {
        zip.putNextEntry(ZipEntry(name))
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)
        zip.closeEntry()
    }

    private fun putText(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray())
        zip.closeEntry()
    }
}
