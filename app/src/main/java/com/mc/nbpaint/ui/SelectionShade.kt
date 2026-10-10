package com.mc.nbpaint.ui

import android.graphics.Bitmap as AndroidBitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mc.nbpaint.model.Selection

/**
 * La sombra que atenúa lo que queda fuera de la región elegida, armada una sola vez.
 *
 * Antes esto sombreaba píxel a píxel con un `drawRect` de 1x1 por cada seleccionado, porque
 * la alternativa obvia (un rectángulo) no sirve: una mancha inteligente no es un rectángulo, y
 * la forma que realmente tiene es la única que hay que mostrar.
 *
 * El problema no era el sombreado sino cómo se dibujaba. Un `drawRect` por píxel son casi un
 * millón de operaciones en una región grande, y Compose las guarda todas en la lista de dibujo
 * para volver a reproducirlas en cada frame: 1,5 GB y "no responde". Aquí se recorre la máscara
 * una vez para componer un bitmap y después el sombreado es un solo `drawImage` por frame.
 *
 * Va cacheado por selección porque el costo de construirlo es lineal en los píxeles del
 * documento, y solo hay que pagarlo cuando la selección cambia, no mientras se dibuja.
 */

/**
 * Tinta con la que se atenúa lo descartado. Va con poca opacidad a propósito: tiene que
 * quitarle protagonismo a la zona, no taparla, porque el usuario sigue teniendo que ver lo
 * que hay alrededor para saber qué está dejando fuera.
 */
private const val SHADE_ARGB = 0x99000000.toInt()

/**
 * Devuelve la imagen de la sombra, o null si la selección no es una máscara (un rectángulo se
 * dibuja solo con su marco y no necesita relleno).
 *
 * La clave del `remember` es la propia máscara, que compara por contenido: si se vuelve a
 * elegir la misma región, no se rearma nada.
 */
@Composable
fun rememberSelectionShade(seleccion: Selection): ImageBitmap? =
    remember(seleccion) {
        if (seleccion is Selection.Mask) shadeOf(seleccion) else null
    }

private fun shadeOf(mascara: Selection.Mask): ImageBitmap {
    val bitmap = AndroidBitmap.createBitmap(mascara.width, mascara.height, AndroidBitmap.Config.ARGB_8888)
    // Un solo `setPixels` en vez de un bucle de Lienzo: la máscara ya está en un IntArray de
    // índices, y pasarlo entero a Android evita un mapeo por píxel.
    //
    // Se sombrea lo que NO está elegido. Antes iba al revés y el resultado se leía como que la
    // zona marcada era lo que se iba a quitar, cuando el recorte deja exactamente esa zona.
    // Atenuar el alrededor hace que la región se lea sola como el resultado, que es lo que el
    // usuario está esperando ver antes de confirmar.
    val pixeles = IntArray(mascara.mask.size)
    for (i in mascara.mask.indices) {
        pixeles[i] = if (mascara.mask[i]) 0 else SHADE_ARGB
    }
    bitmap.setPixels(pixeles, 0, mascara.width, 0, 0, mascara.width, mascara.height)
    return bitmap.asImageBitmap()
}
