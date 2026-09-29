package com.mc.nbpaint.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NbColorScheme = darkColorScheme(
    primary = NbAccent,
    secondary = NbAccentLight,
    background = NbBackground,
    surface = NbSurface,
    onPrimary = NbOnSurface,
    onBackground = NbOnSurface,
    onSurface = NbOnSurface
)

/**
 * NB-paint se usa siempre en oscuro: el contraste alto sobre el lienzo es lo que
 * hace legible el color real con el que se está pintando.
 */
@Composable
fun NBPaintTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NbColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
