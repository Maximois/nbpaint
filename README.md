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
- **Imágenes dentro de las capas**: se puede poner una foto del galería en una capa concreta sin cambiar el tamaño de la hoja, y queda como un objeto que se mueve con un dedo y se escala con pinza (anclado al punto del pellizco, entre 0.05x y 20x). Se puede tener una imagen por capa y superponerlas.
- **Filtros de imagen** (brillo, contraste, saturación, temperatura, nitidez, desenfoque, blanco y negro, sepia, invertir) con **vista previa en vivo** y un deslizador de intensidad: se ve el efecto antes de aplicarlo, y se puede cancelar sin tocar el dibujo.
- **Cuentagotas**: toma el color del píxel que se toque, de cualquier capa y respetando su opacidad.
- **Clonado (sello)**: se marca la fuente con un toque y después se pinta copiando esa zona, como el pincel de clonar de otros editores.
- **Parche**: copia un punto fijo más arriba y tapa lo que hay abajo, con bordes difuminados, para quitar arañazos, errores o manchas sin saber de qué color era.
- **Varita mágica** con dos modos: rellenar con el color elegido o con el promedio del fondo alrededor de la región.
- **Texto**: se toca el lienzo, se escribe y se agrega con el color y tamaño elegidos.
- **Autoguardado con historial**: la app guarda sola cada cambio y, si se cierra sin querer, al volver ofrece **"Recuperar trabajo guardado"** con las últimas versiones (también se puede abrir una anterior o borrar el historial).
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
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/mc/nbpaint/
│       │   │   ├── MainActivity.kt
│       │   │   ├── model/                  → Tipos de datos, sin Android
│       │   │   │   ├── Layers.kt           → PaintLayer, PlacedImage, snapshots
│       │   │   │   ├── HistoryEntry.kt     → Entradas del historial
│       │   │   │   └── Selection.kt        → IntRect y Selection
│       │   │   ├── engine/                 → Motores, la mayoría puros y testeables
│       │   │   │   ├── SelectionEngine.kt  → Marco, inteligente, tolerancias
│       │   │   │   ├── SelectionClip.kt    → Recorte de Canvas según la selección
│       │   │   │   ├── CropEngine.kt       → Recortes
│       │   │   │   ├── LayerStack.kt       → Operaciones de capa
│       │   │   │   ├── HistoryController.kt → Pilas de deshacer/rehacer
│       │   │   │   ├── BrushEngine.kt      → Geometría de figuras y spray
│       │   │   │   ├── Compositor.kt       → Composición de capas
│       │   │   │   └── PatchEngine.kt      → Ventanas y mezcla del parche
│       │   │   ├── data/                   → Persistencia (depende de Android)
│       │   │   │   ├── ProjectManifest.kt   → Formato .nbpaint, puro
│       │   │   │   ├── ProjectStore.kt     → Guardado, autoguardados, ZIP
│       │   │   │   ├── ImageIo.kt          → Decodificar, importar, exportar
│       │   │   │   └── StorageLayout.kt    → Carpetas y constantes
│       │   │   └── ui/
│       │   │       ├── DrawingScreen.kt      → Interfaz (Compose)
│       │   │       ├── DrawingViewModel.kt   → Fachada: coordina todo lo de arriba
│       │   │       ├── CanvasGestures.kt     → Gestos y pellizco
│       │   │       ├── ImageFilters.kt       → Filtros de píxeles
│       │   │       ├── PlacedImageTransform.kt → Zoom y arrastre de imágenes
│       │   │       ├── FloodFill.kt          → Relleno contiguo
│       │   │       ├── MagicWand.kt          → Relleno por tolerancia
│       │   │       ├── SelectionShade.kt     → Sombra de la selección (bitmap en cache)
│       │   │       ├── Tool.kt               → Enum de herramientas y paleta
│       │   │       └── theme/                → Colores y tema oscuro
│       │   └── res/                          → Ícono, strings, tema
│       └── test/java/com/mc/nbpaint/         → Tests unitarios (JVM)
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Cómo compilarlo

1. Abrí la carpeta `NBPaint` con **Android Studio** (Koala o más reciente). El wrapper de Gradle ya está incluido.
2. Dejá que Android Studio sincronice y presioná **Run**, o compilá desde la terminal con `gradlew.bat assembleDebug` (Windows) / `./gradlew assembleDebug` (macOS, Linux). El APK queda en `app/build/outputs/apk/debug/`.
3. Conectá un dispositivo o emulador con **Android 8.0 (API 26) o superior**.

No requiere ninguna librería externa fuera de las estándar de Jetpack Compose / AndroidX.

## Tests

Hay tests unitarios de JVM (se corren con `gradlew.bat test`), 157 en total:

- `SelectionEngineTest` (30): marco, inteligente, tolerancia, unión, inversión y restauración de píxeles fuera de la selección.
- `LayerStackTest` (22): a qué capa queda el panel tras borrar o mover capas, nombres, detección de capa vacía y cuándo una capa queda corta para cubrir la hoja.
- `BrushTipTest` (20): dónde va cada toque del pincel, la curva del degradado y que los ajustes se acoten.
- `PatchEngineTest` (12): tamaño de la ventana del parche, mezcla, promedios y bordes.
- `HistoryControllerTest` (12): orden del deshacer, tope de ambas pilas, invalidación del redo, entradas que ya no se pueden aplicar y dirección de cada paso.
- `CanvasTransformStateTest` (13): encuadre del lienzo (zoom, desplazamiento y límites al cambiar el tamaño del visor), y que el visor no se reencuadre con un dedo apoyado.
- `ProjectManifestTest` (8): nombres con `|` y saltos de línea, proyectos antiguos, cabeceras corruptas, capas inválidas.
- `CloneStampTest` (8): marcar la fuente, fijar el desplazamiento del clon durante el trazo y soltar el sello.
- `PlacedImageTransformTest` (7): zoom anclado al punto, límites de escala y arrastre de imágenes.
- `ToolPolicyTest` (7): qué gestos atiende cada herramienta, y que ninguna se quede sin hacer nada.
- `BrushEngineTest` (6): geometría de figuras y de spray.
- `SelectionMaskTest` (6): límites y conteo de una región, y que se puedan pedir varias veces.
- `GestureUndoTest` (5): un solo punto de deshacer por gesto de mover o pinzar.

La regla que siguen los motores: lo que se puede dejar sin Android se deja sin Android, para poder probarlo en la JVM. Por eso `SelectionEngine` y `PatchEngine` trabajan con `IntArray` y aritmética de bits en vez de llamar a `android.graphics.Color` (que en un test JVM lanza "not mocked"). Hay dos casos más que*rinden lo mismo:

- `HistoryController` es genérico sobre el tipo de entrada, porque no sabe qué significa deshacer una: sus tests usan entradas de texto en vez de `HistoryEntry` y no dependen de `Bitmap`.
- `LayerStack` mezcla funciones con `Bitmap` con reglas puras. Lo que se puede testear está en la parte de arriba, separada: la aritmética de "a qué capa queda activa el panel" es justo lo que se corrompe en silencio cuando algo se corre de lugar, y sin tests un error ahí no se ve hasta que se pierde la capa que se estaba editando.
- `CloneStamp` y `GestureUndo` son estado en vez de dibujo, y también se dejan fuera de Android. El clonado repartía su geometría en tres variables sueltas del `ViewModel`, y lo que lo mantenía corrido en vez de saltarse era tener que relacionarlas bien en cada movimiento; ahora esa relación es una clase con nombre. `GestureUndo` hace lo mismo con el "un solo punto de deshacer por gesto", que estaba repartido entre los dos sitios que mueven la imagen.
- `ToolPolicy` es una decisión de clasificación, no de dibujo: no sabe pintar nada, solo qué gesto atiende cada herramienta.

Lo que depende de `Bitmap` o `Context` se verifica a mano en el dispositivo.

### Por qué no hay un handler por herramienta

Con quince herramientas, lo obvio era un `ToolController` con un handler por herramienta, cada uno como su propia clase. No se hizo, y el motivo es concreto: un handler no dibuja por su cuenta, dibuja sobre el `Canvas` de una capa concreta, con la pintura configurada por la herramienta y pasando por el historial. Eso son unos 35 miembros del `ViewModel` (lienzo, pinturas, OncePerGesture, las ocho variables de placed image, el historial...) que habría que pasarle a cada handler por un contexto compartido. El resultado serían quince archivos que no se pueden probar sin montar ese contexto entero, y el trabajo real se habría movido de sitio sin haberse hecho más testeable.

Lo que sí era un problema real: el reparto de herramientas estaba **duplicado en cuatro sitios** (`onStrokeStart`, `onStrokeMove`, `onStrokeEnd`, `onTap`), cada uno con su lista de herramientas que no hacen nada en ese paso. Añadir una obligaba a tocar cuatro listas, y olvidarse en una no daba ningún error: la herramienta simplemente no respondía a ese gesto. Desde fuera parecía que el pincel no pintaba. Eso se resolvió con `ToolPolicy`, que declara la clasificación una vez, y con un test (`ToolPolicyTest`) que falla si alguna herramienta se queda sin clasificar en ningún gesto.

El criterio general: extraer la **decisión y la aritmética** que se puede probar, y dejar en el `ViewModel` el pegamento de Android que no se puede. Un archivo por herramienta se justifica cuando la herramienta trae lógica propia; para el pincel, el relleno o el cuentagotas no la hay, y partir el archivo solo haría más difícil seguir el recorrido de un toque.

### Cómo se dibuja el pincel

El trazo era un `drawLine` con el borde duro, y no había forma de cambiarlo: un `drawLine` no sabe de degradados. La primera solución aparente, pintar la misma línea varias veces con opacidad cada vez menor, cuesta un canvas entero por trazo y aun así deja un escalonado en el borde, porque cada pasada tiene su propio borde antialiaseado.

Lo que hay ahora es **estampar**: se recorre el segmento y se pone un toque cada `espaciado` de diámetro. Cada toque lleva su propio `shader` radial, así que el borde sale limpio y el difuminado es real. Hay cinco puntas (`BrushKind`): duro, suave, difuminador, marcador y lápiz, y cuatro ajustes: intensidad, caudal, dureza y espaciado. El difuminador añade además un modo (`SmudgeMode`), porque con una sola punta no caben los dos usos que se le piden.

Dos detalles que no son detalles:

- El `shader` se construye **una vez por trazo**, no uno por toque. Construir un `RadialGradient` es de lo más caro que hace `Paint`, y hacerlo por toque con el dedo moviéndose es la diferencia entre pintar fluido y ir a trompicones.
- El difuminador no promedia el color de alrededor: copia el trozo de capa que había bajo el toque anterior y lo pega en el actual. Promediar convierte una foto en una mancha en dos toques; copiar el parche desplaza la forma, que es lo que hace un difuminador de verdad.
- Ese truco de arrastrar tiene un límite: solo puede extender un color que ya esté en la capa. Para teñir una zona con un color concreto hay un segundo modo, "Pintar color", que recorre el mismo camino —un toque por punto, la fuerza por toque, el pegado— pero rellena el parche con el color elegido y una caída radial en vez de copiarlo de la capa. Así el color entra suave y se acumula donde el trazo va despacio, sin el sello de borde recto que daría pintar el cuadrado del parche entero.

El reparto entre `BrushTip` y `BrushEngine` es deliberado: `BrushTip` es Kotlin puro y decide **dónde** va cada toque y con qué opacidad, que es justo lo que se puede probar en la JVM (`BrushTipTest`, 20 tests). `BrushEngine` solo sabe estampar, que necesita `android.graphics` y se comprueba en el dispositivo.

Dos limitaciones que conviene no olvidar: el borrador se queda con el borde duro aunque se elija un pincel suave, porque un degradado con el modo `CLEAR` deja el color a medias en el borde y se ve como una mancha gris; y el aerógrafo sigue siendo una herramienta aparte en vez de un tipo de pincel, porque necesita seguir pintando con el dedo quieto y eso ya es un reloj, no un pincel.

### Por qué la varita mágica se comía el lienzo entero

El relleno y la varita eligen la zona comparando el color del punto tocado con el de sus vecinos. Con una foto importada, la foto **no estaba en la capa**: era un bitmap aparte que se componía encima al dibujar (`layer.placed`). Así que la varita leía una capa vacía, todos sus píxeles eran transparentes, formaban una sola región contigua, y la región era el lienzo entero. De ahí la queja de que pintaba todo indiscriminadamente: no estaba comparando mal los colores, es que no había nada que comparar.

La solución es la misma que ya usaban el clonado y el recorte: `bakePlacedForPixelEdit` funde la foto en los píxeles de la capa antes de buscar el color. Y como un lienzo en blanco sigue siendo una sola región, ahora además se avisa cuando la capa ha quedado de un solo color plano, para que el relleno sea correcto pero no parezca un fallo.
### Cómo se dibuja la selección

Un detalle que costó encontrar. La selección inteligente sombrea la zona elegida, y para mostrarla hay que hacerlo píxel a píxel: la alternativa obvia, pintar un rectángulo, miente, porque una mancha inteligente no es un rectángulo y la forma que tiene es justo lo que hay que enseñar.

El problema nunca fue sombrearla, sino **cómo**. Un `drawRect` de 1x1 por píxel seleccionado son casi un millón de operaciones en una región grande, y Compose las graba todas en la lista de dibujado para volver a reproducirlas en cada frame. Con una selección de 844 000 píxeles la app subía a 1,5 GB, se colgaba y quedaba "no responde". Los ANR parecían de entrada (`Input dispatching timed out`), pero el proceso estaba en estado `R` con el hilo principal al 100%: la selección en sí tarda milisegundos, lo que se colgaba era dibujarla.

La solución es armar la sombra **una sola vez por selección** como imagen (`SelectionShade.rememberSelectionShade`) y dibujarla con un único `drawImage`. Así el sombreado es una operación por frame en vez de una por píxel, y ni siquiera por frame: recorrer la máscara se paga solo cuando la selección cambia. Como el `remember` está indexado por la propia máscara, que compara por contenido, volver a elegir la misma región no rearma nada.

Medido en el dispositivo con una selección de 844 000 píxeles: la memoria pasó de 1,5 GB a ~136 MB, el hilo principal quedó en reposo y la mancha sigue viéndose igual, con 77 000 píxeles sombreados por muestreo contra 1 000 de lienzo sin sombrear. Antes de eso también se cachearon en `Selection.Mask` los límites y el conteo de la región, que se recorrían enteros en cada frame.

### Por qué las imágenes importadas se veían a medias

Otro detalle que costó encontrar, y que además es el que más se nota de una app de pintura. Importar una foto grande y ver solo la mitad no es un error de decodificación: es de **layout**.

La idea intuitiva es hacer que el nodo del lienzo mida el documento, para tener un sistema de coordenadas donde "el punto 0,0 de la foto" sea el origen del dibujo. El problema es que un hijo más grande que su padre se lo coloca desplazado para poder seguir cabiendo: con una foto de 3000x2000, `ImageIo` la deja en 2048x1365 (el lado mayor se limita a 2048 conservando la proporción, así que 3000 px no se recortan a la mitad, se achican), y dentro de un visor de 1008 px de ancho ese nodo quedaba en `x = -520`, o sea exactamente `(1008 - 2048) / 2`. El centro de la foto caía en el borde izquierdo de la pantalla, y lo único que entraba en pantalla era su mitad derecha.

La solución fue invertirlo: el nodo del lienzo mide **el visor** y el documento se dibuja adentro con una transformación explícita (`withTransform` con la escala y el desplazamiento del encuadre). Así nada depende de cómo se coloque un hijo sobredimensionado, y de paso se van el `toDp` y la vuelta a píxeles por el camino, que no hacían falta.

Dos detalles que hacen falta para que no vuelva a pasar:

- El tamaño de contenido que alimenta el encuadre se avisa con el tamaño del **documento**, no con el del nodo. Si se usara el del nodo, al importar una imagen más grande que la pantalla el encuadre no se recalcularía hasta el siguiente cambio de tamaño del visor.
- El `toCanvasCoords` (de pantalla a documento) y el dibujado (de documento a pantalla) tienen que ser la operación inversa exacta. Un error de signo ahí se nota enseguida: el pincel pinta en otro sitio.

Verificado en el dispositivo importando una horizontal de 3000x2000 y una vertical de 1600x3000: en los dos casos entran los cuatro cuadrantes, la imagen queda centrada y el lienzo vacío respeta el mismo criterio. Un trazo pedido de (250,700) a (450,900) aparece en 247..450 x 692..904, o sea donde toca.

### Por qué una capa más chica que la hoja dejaba de pintar

Recortar a la selección deja la capa con el tamaño del recorte, y a partir de ahí la capa puede ser **más chica que la hoja**. Como cada herramienta mide el lienzo contra el bitmap de la capa, lo que se pintaba fuera de ese trozo se recortaba al bitmap y desaparecía sin avisar: el pincel parecía no pintar.

La regla del documento es que todas las capas midan lo mismo, así que antes de editar píxeles la capa se completa a tamaño de hoja con `LayerStack.ensureSheetSize` (fondo del papel, `needsSheetExpansion` decide si hace falta). Lo que ya tenía la capa queda en su sitio y el resto queda blanco; una **imagen puesta** encima no se funde, se sigue dibujando como objeto movible. El ajuste es automático (cualquier pincel, borrador, cubo, varita o parche lo dispara) y no empuja un punto de deshacer propio: el cambio es idempotente y el historial del trazo ya guarda el estado resultante.

## Ideas de funciones avanzadas

Lo que ya está resuelto y qué quedaría como siguiente paso. Están ordenadas de menor a mayor esfuerzo, porque no todas valen lo mismo.

### Baratas y de alto valor

- **Filtros como pila de ajustes, no como horneado.** Hoy `Aplicar` quema el filtro en los píxeles (y aplana la imagen puesta en la capa, que deja de poder moverse). Si cada capa guardara una lista de ajustes `(filtro, intensidad)` que se aplican al dibujar, se podría cambiar la intensidad de un filtro ya aplicado sin volver a procesar nada, deshacerlo sería sacar un elemento de la lista y las imágenes seguirían movibles. El costo es renderizar los ajustes en cada fotograma, así que conviene limitar cuántos se acumulan.
- **Previsualización de filtros sin tirones.** El preview se calcula sobre la hoja completa en el hilo principal. Con la vista previa reducida que ya existe en `ImageFilter.preview` (por ejemplo, un cuarto del tamaño) y un pequeño retardo antes de aplicar, el deslizador dejaría de congelar la pantalla en lienzos grandes.
- **Girar la imagen puesta.** Hoy solo se puede escalar (`PlacedImage` tiene `offsetX/offsetY/scale`). Agregar un ángulo y generalizar `PlacedImageTransform` con una matriz sería el paso natural, y los tests existentes ya servirían de base.
- **Abrir un `.nbpaint` desde el selector de archivos.** Exportar ya funciona; el que falta es el camino inverso para un proyecto recibido de otra persona. Hoy el ítem "Abrir proyecto" del menú solo recupera el último guardado en este mismo dispositivo (`ProjectStore.readFrom` sobre el directorio interno), no lee un `.nbpaint` que te llegue por Drive o WhatsApp.

### Grandes, pero las que más cambian el editor

- **Máscaras de capa.** Es la mayor adición estructural: una máscara en escala de grises por capa, multiplicada como alfa al componer. Habilita borrar o pintar solo en una zona, y difuminar una capa con el pincel sin tocar sus píxeles. El lugar natural para meterlo es el dibujado de cada capa dentro de la composición, que ya las recorre en orden.
- **Selección con tiradores.** Un rectángulo de selección con ocho tiradores que restrinja el pincel, el borrado, los filtros y el parche, y que permita recortar, escalar de forma no uniforme y rotar lo seleccionado. Hoy las imágenes solo se escalan de forma uniforme, y la selección obligaría a generalizar esa matemática.
- **Texto que se pueda volver a editar.** El texto se rasteriza al agregarlo. Guardando el texto, la caja y el estilo en la capa se podría corregir una errata sin rehacerlo, y el texto dejaría de perder nitidez al escalarlo.
- **Deshacer que sobreviva al cierre de la app.** El historial vive en memoria y se pierde si el sistema mata el proceso; el autoguardado salva el dibujo, pero no los pasos de deshacer. Guardar el historial como deltas (o reutilizando las versiones del autoguardado) permitiría volver a un punto anterior del trabajo, no solo abrir la última versión.
- **Renderizado en mosaicos o por GPU.** La composición se rehace completa cada vez que cambia algo, así que el costo crece con el área por la cantidad de capas. Dibujar las capas directamente en Compose, o componer por partes, eliminaría esa copia intermedia.
- **Selector de pincel con textura, presión y estabilizador.** El pincel hoy solo tiene grosor y color. La presión ya está disponible en los eventos táctiles, y un estabilizador (suavizar los puntos antes de pintar) es lo que más cambia la sensación de un pincel digital.

## Ideas para edición de fotos

Lo anterior es el editor de dibujo en general. Esto es lo que falta para que la app se sienta como un editor de fotos: lo que hace falta es que nada de lo que ya se hizo destruya el original.

### Lo que más urge: que nada sea irreversible

El patrón de fondo de todos estos puntos es el mismo. Hoy `Aplicar` quema el filtro en los píxeles y `Recortar` reduce la capa de verdad, así que una foto retoqueada se queda sin vuelta atrás.

- **Filtros como pila de ajustes, no como horneado.** Cada capa guarda una lista `(filtro, intensidad)` que se aplica al componer. Así se cambia la intensidad de un filtro ya aplicado sin reprocesar, deshacer un filtro es sacar un elemento de la lista, y las imágenes puestas siguen movibles. Es la idea que más rápido se nota y ya está detallada más arriba.
- **Recorte y enderece no destructivos.** Con una máscara de recorte, la foto original queda intacta y solo cambia el encuadre. Es de las mejoras que más se agradecen: hoy un recorte bien hecho es un recorte que no se puede deshacer.
- **Deshacer que sobreviva al cierre.** El autoguardado salva el dibujo, pero no los pasos de deshacer. Guardando el historial como deltas, o reutilizando las versiones del autoguardado, se podría volver a un punto anterior del trabajo y no solo abrir la última versión. Cuando el sistema mata la app a mitad de una sesión larga, es la diferencia entre perder un paso y perder el rato.

### Ajustes que hoy faltan y se notan enseguida

Los nueve filtros actuales son todos globales y lineales, que es justo lo que menos rinde con una foto real.

- **Curvas y niveles.** En vez de un único deslizador de brillo, una curva por canal (RGB y una por canal). Es el ajuste que más impacto tiene y el que más echa de menos quien viene de otro editor.
- **Tono y saturación con protección de piel.** Hoy la saturación sube todo por igual, y eso arruina los tonos de piel. Separar luminancia de croma y limitar la saturación en la zona de tonos de piel arregla el caso más común.
- **Altas luces y sombras por separado.** Enfocar una foto de exterior deja las luces quemadas o las sombras en negro. Dos controles separados resuelven más fotos que la suma de los filtros actuales.
- **Vibrancia además de saturación.** Satura lo que está poco saturado y respeta lo que ya está. De los ajustes baratos, es de los que más se echa de menos.

### Herramientas

- **Selección con tiradores.** Ocho tiradores que restrinjan pincel, borrado, filtros y parche, y que permitan recortar, escalar de forma no uniforme y rotar lo seleccionado. Hoy las imágenes solo se escalan de forma uniforme, así que obligaría a generalizar esa matemática. Es lo que convierte la selección en algo de edición de foto y no solo de pintura.
- **Girar la imagen puesta.** Solo se puede escalar. Agregar ángulo y generalizar `PlacedImageTransform` con una matriz es el paso natural, y los tests de `PlacedImageTransform` ya servirían de base.
- **Máscaras de capa.** Es la mayor adición estructural: una máscara en escala de grises por capa, multiplicada como alfa al componer. Es lo que permitiría difuminar una capa con el pincel sin tocar sus píxeles, que es como se retoca una foto sin destruirla.
- **Pincel History y degradados para integrar.** Si el grano o el ruido de una zona no casa con la de al lado, la costura se nota; un pincel que toma de lo pintado hace un momento atrás, o un degradado para pasar de un lado al otro, son las dos herramientas que más usa quien retoca de verdad.

### Lo primero que haría

**Curvas + historial persistente.** Los dos atacan el mismo problema real: cuando una foto no sale bien, el flujo actual obliga a deshacer a lo bruto o a perder el trabajo. Los dos son acotados, no tocan la estructura de composición a diferencia de las máscaras, y se pueden probar en la JVM porque son lógica pura.

Decime cuál te interesa y arranco por esa.
