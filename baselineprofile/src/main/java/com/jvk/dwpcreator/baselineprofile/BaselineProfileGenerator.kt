package com.jvk.dwpcreator.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Genera el Baseline Profile real de DwpCreator -- ver Doc/47 para el
 * contexto completo (qué es, por qué está aislado de `assembleRelease`, y
 * qué falta para cubrir más que el arranque).
 *
 * **Alcance de este primer perfil: el arranque en frío hasta la pantalla
 * vacía ("Cargar" / [com.jvk.dwpcreator.ui.components.LoadEmptyState]).**
 * Deliberadamente NO simula tocar "LOAD" y elegir un `.zip` real: eso abre
 * el selector de archivos del sistema (Storage Access Framework), que un
 * test de Macrobenchmark no puede resolver de forma fiable sin un fixture
 * de prueba dedicado (un instrumento de ejemplo empaquetado + un modo
 * "test-only" en la propia app para saltarse el selector) -- ese fixture
 * NO se añadió aquí para no tocar código de producción con un atajo
 * exclusivo de test sin que el usuario lo haya pedido explícitamente.
 *
 * Aun así, el arranque en frío (inicialización de Activity, Compose,
 * ViewModel, el `Scaffold`/`DwpTopBar` iniciales) es una porción real y
 * significativa del "se siente pesado" que reportó el usuario -- cubre las
 * clases del framework y de la propia app que se cargan SIEMPRE, en cada
 * apertura, independientemente de qué instrumento se cargue después.
 */
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "com.jvk.dwpcreator",
        // Arranques repetidos -- el generador promedia/combina varias
        // corridas para no quedarse con un perfil sesgado por una corrida
        // atípica (p. ej. una carga de clases retrasada por el propio
        // sistema en la primera ejecución del emulador).
        maxIterations = 5
    ) {
        // Doc/57: mitigación real y documentada para
        // `IllegalStateException: Unable to confirm activity launch
        // completion` en `amStartAndWait` (el error real que sí se pudo
        // reproducir, a diferencia de los cuelgues sin causa de la imagen
        // ATD que se abandonó). Con las animaciones del sistema en su
        // valor por defecto, un emulador recién arrancado puede tardar en
        // reportar el fin de la animación de apertura de la Activity vía
        // `dumpsys gfxinfo`, y Macrobenchmark no logra confirmarlo dentro
        // de su ventana de espera. Ponerlas en 0 (como hacen los propios
        // tests instrumentados de Android en CI) elimina esa animación.
        device.executeShellCommand("settings put global window_animation_scale 0")
        device.executeShellCommand("settings put global transition_animation_scale 0")
        device.executeShellCommand("settings put global animator_duration_scale 0")

        pressHome()
        startActivityAndWait()

        // Espera real a que la pantalla vacía termine de componerse (el
        // botón "LOAD" de `DwpTopBar`/`LoadEmptyState`) antes de cerrar la
        // iteración -- sin esto, el perfil podría cortarse a mitad de la
        // composición inicial y no capturar clases que sí importan en cada
        // arranque real.
        device.wait(Until.hasObject(By.textContains("LOAD")), 5_000)
    }
}
