package com.mc.nbpaint.engine

/**
 * Pila de deshacer/rehacer.
 *
 * Antes de esto las dos pilas vivían dentro del `DrawingViewModel`, que acababa con 31
 * llamadas repartidas por el archivo y tenía que acordarse de empujar el historial a mano
 * en cada operación nueva. Ese era el punto donde era fácil olvidar el undo, y el síntoma
 * era un botón de deshacer que no deshacía.
 *
 * Aquí solo viven las dos pilas y las reglas que las gobiernan:
 *
 * - cuánto se guarda antes de tirar lo más viejo,
 * - que una operación nueva invalide el redo,
 * - qué se responde a [canUndo] y [canRedo].
 *
 * Lo que NO vive aquí es qué significa deshacer una entrada: eso depende de las capas y de
 * los bitmaps, así que lo decide quien la usa a través de [apply]. Por eso la pila es
 * genérica sobre el tipo de entrada: no conoce ni un `Bitmap` y se puede probar en la JVM
 * con entradas inventadas, sin depender de Android.
 *
 * [apply] recibe la dirección porque deshacer y rehacer no siempre son el mismo paso: al
 * combinar capas, deshacer vuelve a separar la capa de arriba y a poner el bitmap viejo en
 * la de abajo, y rehacer hace lo contrario. Son dos operaciones distintas que la pila solo
 * no puede adivinar.
 *
 * El flujo es siempre el mismo: se hace la operación, se empuja el estado anterior con
 * [push], y para volver se llama a [undo] o [redo], que devuelven la operación inversa ya
 * calculada.
 *
 * @param E el tipo de entrada del historial. En la app es `HistoryEntry`.
 * @param apply aplica una entrada al dibujo y devuelve la entrada que la deshace, ya
 *   preparada para la pila contraria. Devolver `null` significa que la entrada no se pudo
 *   aplicar porque lo que guardaba ya no existe (por ejemplo, se editó una capa que después
 *   se borró); en ese caso el punto se descarta en vez de dejar la pila trabada para siempre.
 */
class HistoryController<E>(
    /** Cuántos pasos se guardan como máximo. */
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val apply: (E, HistoryDirection) -> E?
) {

    private val undoStack = ArrayDeque<E>()
    private val redoStack = ArrayDeque<E>()

    /** Si se puede deshacer. Lo consulta la UI para pintar el botón. */
    val canUndo: Boolean get() = undoStack.isNotEmpty()

    /** Si se puede rehacer. */
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Guarda el estado anterior a una operación.
     *
     * Se llama DESPUÉS de hacer el cambio, pasando cómo estaba antes: así el código de cada
     * operación es "cambiar esto, y guarda cómo estaba", en ese orden, que es como se lee
     * de natural.
     */
    fun push(entry: E) {
        undoStack.addLast(entry)
        capUndo()
        // El redo deja de tener sentido en cuanto se hace algo nuevo.
        redoStack.clear()
    }

    /**
     * Deshace el último paso.
     *
     * @return true si había algo que deshacer, para que quien llama sepa si hay que repintar.
     */
    fun undo(): Boolean {
        val entry = undoStack.removeLastOrNull() ?: return false
        val inversa = apply(entry, HistoryDirection.Undo)
        if (inversa != null) {
            redoStack.addLast(inversa)
            capRedo()
        }
        return true
    }

    /**
     * Rehace el paso deshecho.
     *
     * @return true si había algo que rehacer, para que quien llama sepa si hay que repintar.
     */
    fun redo(): Boolean {
        val entry = redoStack.removeLastOrNull() ?: return false
        val inversa = apply(entry, HistoryDirection.Redo)
        if (inversa != null) {
            undoStack.addLast(inversa)
            capUndo()
        }
        return true
    }

    /** Tira el historial. Se usa al abrir otro proyecto o al empezar uno nuevo. */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    private fun capUndo() {
        while (undoStack.size > maxEntries) undoStack.removeFirst()
    }

    private fun capRedo() {
        while (redoStack.size > maxEntries) redoStack.removeFirst()
    }

    companion object {
        /** Cuántos pasos se guardan, igual que el tope que usaba el ViewModel. */
        const val DEFAULT_MAX_ENTRIES = 20
    }
}

/** En qué sentido se está aplicando una entrada del historial. */
enum class HistoryDirection {
    /** Volver al estado anterior. */
    Undo,

    /** Volver al estado que se había deshecho. */
    Redo
}
