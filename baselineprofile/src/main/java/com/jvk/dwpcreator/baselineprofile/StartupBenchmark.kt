package com.jvk.dwpcreator.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

/**
 * **Benchmark de arranque en frío: sin perfil vs con Baseline Profile** (Doc/69).
 *
 * Mide la MISMA app (variante `benchmarkRelease`: release con R8, `profileable`, con el
 * `baseline-prof.txt` real empaquetado) arrancándola en frío [ITERATIONS] veces en tres
 * estados de compilación de ART:
 *
 *  1. [startupWithoutProfile] -- `CompilationMode.None()`: sin compilación AOT, sólo
 *     interpretación/JIT. Es lo que vive un usuario en el **primer arranque tras instalar**
 *     sin perfil.
 *  2. [startupWithBaselineProfile] -- `Partial(BaselineProfileMode.Require)`: ART compila lo
 *     que dice el perfil. Es lo que vive el usuario **con** el perfil. `Require` hace que el
 *     test **falle** si el perfil no está instalado, en vez de medir en silencio "sin perfil"
 *     y dar una comparación falsa.
 *  3. [startupFullyCompiled] -- `Full()`: todo compilado AOT. Techo teórico de referencia: lo
 *     que daría el arranque si ART lo hubiera compilado todo (lo que ocurre, por ejemplo,
 *     tras días de uso con la compilación en segundo plano del sistema).
 *
 * Métricas: `timeToInitialDisplayMs` (hasta el primer frame) y, porque `MainActivity` llama a
 * `reportFullyDrawn()` tras la primera composición, `timeToFullDisplayMs`.
 *
 * **Lectura honesta de los resultados.** Se ejecuta en un emulador x86_64 de CI (no en un
 * teléfono ARM real): las cifras absolutas no son las de un dispositivo; lo que vale es la
 * **diferencia relativa** entre los tres modos medida en las mismas condiciones. Por eso el
 * workflow pasa `suppressErrors=EMULATOR` (Macrobenchmark se niega a correr en emulador si no).
 */
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithBaselineProfile() =
        startup(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    @Test
    fun startupFullyCompiled() = startup(CompilationMode.Full())

    private fun startup(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = "com.jvk.dwpcreator",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = {
            // Mismo motivo que en BaselineProfileGenerator (Doc/57): sin animaciones de sistema,
            // el emulador confirma el fin del lanzamiento de la Activity de forma fiable.
            device.executeShellCommand("settings put global window_animation_scale 0")
            device.executeShellCommand("settings put global transition_animation_scale 0")
            device.executeShellCommand("settings put global animator_duration_scale 0")
            pressHome()
        }
    ) {
        startActivityAndWait()
    }

    private companion object {
        /** 10 arranques por modo: suficiente para una mediana estable sin alargar el job de CI. */
        const val ITERATIONS = 10
    }
}
