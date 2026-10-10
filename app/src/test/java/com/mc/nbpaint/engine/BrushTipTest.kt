package com.mc.nbpaint.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * El motor del pincel, probado sin emulador.
 *
 * Aquí no se dibuja nada: se comprueba la cuenta de dónde va cada toque y con qué opacidad,
 * que es donde estaban los fallos. Un toque mal colocado no lanza ninguna excepción, solo
 * deja el trazo punteado o con un bulto al final, y eso no se ve hasta que lo pintas con el
 * dedo en un móvil.
 */
class BrushTipTest {

    private val duro = BrushSettings(kind = BrushKind.DURO, size = 20f, espaciado = 0.15f)

    @Test
    fun `un segmento más corto que el paso da un solo toque`() {
        val puntos = BrushTip.toques(10f, 10f, 12f, 10f, duro)
        assertEquals(2, puntos.size)
        assertEquals(11f, puntos[0], 0.01f)
        assertEquals(10f, puntos[1], 0.01f)
    }

    @Test
    fun `un segmento largo da varios toques`() {
        val puntos = BrushTip.toques(0f, 0f, 100f, 0f, duro)
        assertTrue("un trazo de 100 px debe tener más de un toque", puntos.size > 4)
    }

    @Test
    fun `el primer y el último toque caen en los extremos`() {
        val puntos = BrushTip.toques(5f, 7f, 95f, 7f, duro)
        assertEquals(5f, puntos[0], 0.01f)
        assertEquals(7f, puntos[1], 0.01f)
        val ultimo = puntos.size - 2
        assertEquals(95f, puntos[ultimo], 0.01f)
        assertEquals(7f, puntos[ultimo + 1], 0.01f)
    }

    @Test
    fun `los toques van repartidos a paso constante sobre la recta`() {
        val puntos = BrushTip.toques(0f, 0f, 100f, 0f, duro)
        // Todos los puntos tienen que caer sobre la recta, que es lo que evita el trazo
        // ondulado cuando el paso no cuadra con la longitud del segmento.
        for (i in 0 until puntos.size step 2) {
            assertEquals(0f, puntos[i + 1], 0.01f)
        }
    }

    @Test
    fun `un espaciado mas fino da mas toques`() {
        val fino = duro.copy(espaciado = 0.05f)
        val grueso = duro.copy(espaciado = 0.6f)
        val nFino = BrushTip.toques(0f, 0f, 100f, 0f, fino).size
        val nGrueso = BrushTip.toques(0f, 0f, 100f, 0f, grueso).size
        assertTrue("el espaciado fino debe dar más toques: $nFino vs $nGrueso", nFino > nGrueso)
    }

    @Test
    fun `un espaciado cero no divide el trazo en infinitos puntos`() {
        // Sin este tope, paso 0 daría un array de tamaño infinito y reventaría la app al
        // pintar. El mínimo es lo que convierte "se me olvidó el mínimo" en un trazo normal.
        val puntos = BrushTip.toques(0f, 0f, 100f, 0f, duro.copy(espaciado = 0f))
        assertTrue(puntos.size in 2..4000)
    }

    @Test
    fun `el ultimo toque llega al punto exacto y no se queda a medio paso`() {
        val puntos = BrushTip.toques(0f, 0f, 33f, 0f, duro)
        val ultimo = puntos.size - 2
        assertEquals(33f, puntos[ultimo], 0.001f)
    }

    @Test
    fun `la caida es uno en el centro y cero en el borde`() {
        val radio = 10f
        assertEquals(1f, BrushTip.caida(0f, radio, 0f), 0.001f)
        assertEquals(0f, BrushTip.caida(radio, radio, 0f), 0.001f)
        assertEquals(0f, BrushTip.caida(radio * 2f, radio, 0f), 0.001f)
    }

    @Test
    fun `con el nucleo opaco la caida es uno hasta el nucleo`() {
        val radio = 10f
        val nucleo = 6f
        assertEquals(1f, BrushTip.caida(5f, radio, nucleo), 0.001f)
        assertEquals(1f, BrushTip.caida(6f, radio, nucleo), 0.001f)
        assertTrue("justo fuera del núcleo tiene que empezar a caer", BrushTip.caida(7f, radio, nucleo) < 1f)
    }

    @Test
    fun `la caida baja de forma monotonica`() {
        // Si la curva tuviera un bache, el degradado se vería como un aro más claro en
        // medio del trazo. Es el fallo típico de interpolar a mano los puntos del shader.
        var anterior = 1.1f
        for (d in 0..20) {
            val v = BrushTip.caida(d.toFloat(), 20f, 4f)
            assertTrue("la caída sube en $d: $anterior -> $v", v <= anterior)
            anterior = v
        }
    }

    @Test
    fun `un radio de cero no revienta`() {
        assertEquals(0f, BrushTip.caida(1f, 0f, 0f), 0.001f)
    }

    @Test
    fun `las paradas del gradiente van de cero a uno`() {
        val paradas = BrushTip.paradasGradiente(4f, 10f)
        assertEquals(0f, paradas.posiciones.first(), 0.001f)
        assertEquals(1f, paradas.posiciones.last(), 0.001f)
        assertEquals(paradas.posiciones.size, paradas.opacidades.size)
    }

    @Test
    fun `las paradas del nucleo caen antes de uno`() {
        // Si la primera parada no está en 0, `RadialGradient` lanza: las posiciones tienen
        // que empezar en cero y acabar en uno o no hay degradado.
        val paradas = BrushTip.paradasGradiente(0f, 10f)
        assertEquals(0f, paradas.posiciones.first(), 0.001f)
        assertEquals(1f, paradas.posiciones.last(), 0.001f)
    }

    @Test
    fun `un nucleo del tamaño del radio da un borde duro`() {
        val paradas = BrushTip.paradasGradiente(10f, 10f)
        assertEquals(2, paradas.posiciones.size)
        assertEquals(1f, paradas.opacidades[0], 0.001f)
        assertEquals(1f, paradas.opacidades[1], 0.001f)
    }

    @Test
    fun `el alfa del toque es intensidad por caudal`() {
        val ajustes = BrushSettings(intensidad = 0.5f, flow = 0.5f)
        assertEquals(0.25f, ajustes.alfaPorToque(), 0.001f)
    }

    @Test
    fun `los ajustes se acotan a uno y a cero`() {
        val ajustes = BrushSettings(intensidad = 5f, flow = -3f)
        assertEquals(0f, ajustes.alfaPorToque(), 0.001f)
    }

    @Test
    fun `el trazo completo no se sale del segmento`() {
        // Regresión del difuminador: los puntos tienen que ir del origen al destino, nunca
        // más allá, o el trazo deja una cola detrás del dedo.
        val puntos = BrushTip.toques(100f, 200f, 180f, 260f, duro)
        for (i in 0 until puntos.size step 2) {
            val d = hypot(
                (puntos[i] - 100f).toDouble(),
                (puntos[i + 1] - 200f).toDouble()
            )
            val total = hypot(80.0, 60.0)
            assertTrue("un punto se sale del segmento: $d de $total", d <= total + 0.01)
        }
    }

    @Test
    fun `la dureza solo aparece donde hay degradado`() {
        // El criterio es "el control hace algo", no "el pincel tiene bordes". El
        // difuminador que arrastra copia un parche y no pinta un circulo, asi que un
        // deslizador de dureza ahi seria un control que se mueve sin pasar nada.
        assertTrue(BrushSettings(kind = BrushKind.SUAVE).usaDegradado)
        assertTrue(BrushSettings(kind = BrushKind.MARCADOR).usaDegradado)
        assertFalse(BrushSettings(kind = BrushKind.DURO).usaDegradado)
        assertFalse(BrushSettings(kind = BrushKind.LAPIZ).usaDegradado)
        assertFalse(BrushSettings(kind = BrushKind.DIFUMINADOR).usaDegradado)
    }

    @Test
    fun `el difuminador de color si usa dureza`() {
        // Ese es justo el caso contrario: al pintar el color elegido el parche se rellena
        // con un degradado radial, asi que la dureza marca donde empieza a desvanecerse y
        // el control vuelve a servir para algo.
        val color = BrushSettings(kind = BrushKind.DIFUMINADOR, difuminado = SmudgeMode.COLOR)
        val arrastre = BrushSettings(kind = BrushKind.DIFUMINADOR, difuminado = SmudgeMode.ARRASTRAR)
        assertTrue(color.usaDegradado)
        assertFalse(arrastre.usaDegradado)
    }

    @Test
    fun `el difuminador arranca arrastrando`() {
        // El modo de siempre no debe cambiar para quien no toque nada nuevo.
        assertEquals(SmudgeMode.ARRASTRAR, BrushSettings().difuminado)
    }
}
