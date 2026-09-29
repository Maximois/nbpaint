package com.mc.nbpaint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mc.nbpaint.ui.DrawingScreen
import com.mc.nbpaint.ui.theme.NBPaintTheme

/**
 * NB-paint — app de dibujo estilo Paint para Android.
 * Desarrollado para MC PRODUCCIONES.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NbPaintApp()
        }
    }
}

@Composable
private fun NbPaintApp() {
    NBPaintTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            DrawingScreen()
        }
    }
}
