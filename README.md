# NB-paint

App de dibujo estilo Microsoft Paint para Android, hecha en **Kotlin + Jetpack Compose**, según el plan de proyecto de MC PRODUCCIONES.

## Qué incluye esta versión

- **Lienzo táctil** con renderizado inmediato sobre un `Bitmap` ARGB_8888 (sin lag: el trazo se dibuja directo sobre el bitmap ya confirmado, separado de la vista previa de figuras).
- **Pincel libre** con grosor y color configurables, puntas redondeadas.
- **Borrador** (pinta con el color de fondo).
- **Figuras geométricas**: línea, rectángulo y círculo, con vista previa punteada en tiempo real mientras arrastrás el dedo, y trazo final fijado al soltar.
- **Aerógrafo (spray)**: partículas aleatorias dispersas dentro de un radio configurable.
- **Bote de pintura (flood fill)**: relleno por escaneo de líneas (scanline), rápido y sin riesgo de desbordar la pila en lienzos grandes.
- **Paleta de 12 colores** predefinidos + **selector de color HSV** (área de saturación/valor + slider de matiz) con vista previa en vivo y código hexadecimal.
- **Interfaz oscura** de alto contraste: el color que ves en la pantalla es exactamente el que se aplica al lienzo.
- **Deshacer / Rehacer** unificado: cubre trazos, formas, relleno y borrado, **y también** agregar, quitar, reordenar y combinar capas (hasta 20 pasos).
- **Capas**: crear, eliminar, reordenar, ocultar/mostrar, renombrar, ajustar opacidad y combinar (merge down) desde el panel de capas (ícono de capas en la barra superior).
- **Zoom y desplazamiento (pan)** anclado al punto exacto del pellizco: botón "mover/zoom" en la barra superior activa el modo pellizco (hasta 6x); al desactivarlo, volvés a dibujar normalmente aunque estés con zoom aplicado. Botón para restablecer el zoom cuando está activo.
- **Formas rellenas**: al elegir rectángulo o círculo aparece un botón de "relleno" para alternar entre contorno y forma sólida.
- **Guardar y abrir proyecto en progreso**: desde el menú de tres puntos, guarda todas las capas (con su orden, visibilidad y opacidad) en el almacenamiento interno de la app y las recupera después, no solo la imagen final.
- **Compartir proyecto**: exporta todas las capas como un archivo `.nbpaint` (un .zip con las capas en PNG + un manifiesto) y abre el selector para enviarlo por cualquier app (Drive, WhatsApp, correo, etc.).
- **Guardar en la galería** como **PNG o JPEG** a elección (usa `MediaStore`, compatible con Android 8 a 15+, sin permisos especiales en Android 10+).
- **Limpiar lienzo**.
- Interfaz oscura, barra de herramientas inferior con scroll horizontal, pensada para uso con una o dos manos.

## Requisitos

- **Android 8.0 (API 26) o superior** (`minSdk = 26`).
- `targetSdk` / `compileSdk` 35, JDK 17, Kotlin con Jetpack Compose (BOM 2024.06.00).
- Sin librerías de terceros: solo Jetpack Compose y AndroidX.

## Estructura del proyecto

```
NBPaint/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/mc/nbpaint/
│       │   ├── MainActivity.kt
│       │   └── ui/
│       │       ├── DrawingScreen.kt      → Toda la interfaz (Compose)
│       │       ├── DrawingViewModel.kt   → Motor de dibujo, undo/redo, guardado
│       │       ├── FloodFill.kt          → Algoritmo de bote de pintura
│       │       ├── Tool.kt               → Enum de herramientas y paleta
│       │       └── theme/                → Colores y tema oscuro
│       └── res/                          → Ícono, strings, tema
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Cómo compilarlo

1. Abrí la carpeta `NBPaint` con **Android Studio** (Koala o más reciente). El wrapper de Gradle ya está incluido.
2. Dejá que Android Studio sincronice y presioná **Run**, o compilá desde la terminal con `gradlew.bat assembleDebug` (Windows) / `./gradlew assembleDebug` (macOS, Linux). El APK queda en `app/build/outputs/apk/debug/`.
3. Conectá un dispositivo o emulador con **Android 8.0 (API 26) o superior**.

No requiere ninguna librería externa fuera de las estándar de Jetpack Compose / AndroidX.

## Próximos pasos posibles

- Importar un proyecto `.nbpaint` compartido por otra persona (hoy "Compartir proyecto" exporta, pero "Abrir proyecto" solo recupera el último guardado en este mismo dispositivo).
- Compresión/undo más eficiente en memoria para lienzos muy grandes con muchas capas.
- Selector de pincel con textura/opacidad variable (no solo grosor y color).
- Tests automatizados: hoy solo hay los tests de plantilla de Android Studio.

Decime si querés que avance con alguna de estas, o si preferís que revise/ajuste algo de lo ya hecho.
