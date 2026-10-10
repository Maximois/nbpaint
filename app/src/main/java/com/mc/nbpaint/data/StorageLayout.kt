package com.mc.nbpaint.data

import android.content.Context
import java.io.File

/**
 * Dónde vive cada cosa en el disco.
 *
 * Los nombres de carpeta y de archivo estaban repartidos entre el ViewModel y un archivo de
 * constantes que ya no existe. Centralizarlos aquí evita el error clásico de guardar con un
 * nombre y leer con otro: el proyecto se guarda bien y no aparece.
 */
object StorageLayout {

    const val PROJECT_DIR_NAME = "nbpaint_project"
    const val AUTOSAVE_DIR_NAME = "nbpaint_autosaves"
    const val MANIFEST_NAME = ProjectManifest.MANIFEST_NAME

    /**
     * Archivo dentro de la carpeta de autoguardados que dice "había trabajo sin terminar".
     * Solo se borra cuando el usuario cierra la app o el proyecto a propósito.
     */
    const val SESSION_MARKER = ".sesion_abierta"

    /** Cuántos puntos de autoguardado se conservan. */
    const val MAX_AUTOSAVES = 5

    /** Tope del lado mayor de la hoja, para que una foto enorme no quede imposible de dibujar. */
    const val MAX_DOCUMENT_EDGE = 2048

    fun projectDir(context: Context): File = File(context.filesDir, PROJECT_DIR_NAME)

    fun autosaveRoot(context: Context): File = File(context.filesDir, AUTOSAVE_DIR_NAME)

    fun autosaveSlot(context: Context, slot: Int): File = File(autosaveRoot(context), slot.toString())
}
