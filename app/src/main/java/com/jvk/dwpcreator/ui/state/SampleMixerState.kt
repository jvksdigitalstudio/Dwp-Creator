package com.jvk.dwpcreator.ui.state

/**
 * Ajustes de mezcla en vivo de una muestra individual -- Mute, Solo, Pan y
 * Volumen -- pedidos explícitamente como una franja de mini-controles junto
 * a cada fila de la lista (referencia visual: un canal de mesa de mezcla).
 *
 * **Alcance deliberado: solo afecta la previsualización dentro de esta app,
 * NO se escribe en el `.dwp`/zip exportado.** El formato DirectWave no tiene
 * ningún campo estándar para "mute/solo/pan/volumen por muestra" que
 * `DwpEngine`/`MonolithicDwpBuilder` puedan preservar sin inventar una
 * extensión no estándar del formato -- y el pedido original ("cada muestra
 * que tenga estas opciones") describe un panel de control tipo mezclador
 * para escuchar el instrumento mientras se revisa, no una propiedad
 * documental del instrumento en sí. Si más adelante se pide persistir esto
 * en el export, es un cambio deliberadamente aparte (tocaría el formato
 * `.dwp` real, con todo el riesgo de corrupción binaria que eso implica --
 * ver `Doc/23`/`Doc/24`), no una extensión trivial de esta clase.
 *
 * Vive en [com.jvk.dwpcreator.viewmodel.DwpCreatorViewModel] como un
 * `StateFlow<Map<Int, SampleMixerState>>` **solo con las entradas que se
 * apartan de la neutra** ([isDefault]) -- la inmensa mayoría de un
 * instrumento de 48+ muestras nunca toca estos controles, así que guardar
 * una entrada por cada una desperdiciaría memoria sin ningún beneficio.
 * [com.jvk.dwpcreator.viewmodel.DwpCreatorViewModel.mixerStateFor] devuelve
 * [DEFAULT] para cualquier índice sin entrada propia.
 */
data class SampleMixerState(
    /** Si es `true`, esta muestra no suena al tocarla -- salvo que alguna otra muestra esté en [solo] (ver semántica de [solo]). */
    val muted: Boolean = false,
    /**
     * Si es `true`, únicamente las muestras en `solo` suenan al tocarlas --
     * el resto del instrumento queda silenciado mientras exista al menos una
     * en solo, sin importar su propio [muted] individual. Semántica estándar
     * de "Solo" en cualquier mesa de mezcla: varias muestras pueden estar en
     * solo a la vez (no es exclusivo/radio-button), y se oyen todas ellas
     * juntas.
     */
    val solo: Boolean = false,
    /** Paneo estéreo: -1f = extremo izquierdo, 0f = centro, 1f = extremo derecho. */
    val pan: Float = 0f,
    /** Volumen de esta muestra: 0f = silencio, 1f = ganancia unitaria (el nivel "de fábrica", sin atenuar ni amplificar). */
    val volume: Float = 1f
) {
    /** `true` para el estado "de fábrica" exacto -- usado para no guardar entradas redundantes en el mapa del ViewModel. */
    fun isDefault(): Boolean = this == DEFAULT

    companion object {
        val DEFAULT = SampleMixerState()
    }
}
