package com.mc.nbpaint.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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

@Composable
fun NBPaintTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NbColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
