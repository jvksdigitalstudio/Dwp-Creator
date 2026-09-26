package com.jvk.dwpcreator.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jvk.dwpcreator.audio.SamplePlayer
import com.jvk.dwpcreator.domain.dwp.DwpEngine
import com.jvk.dwpcreator.domain.dwp.InstrumentNameValidator
import com.jvk.dwpcreator.domain.dwp.monolithic.MonolithicDwpProjectBuilder
import com.jvk.dwpcreator.domain.io.LoadedProject
import com.jvk.dwpcreator.domain.io.ZipProjectExporter
import com.jvk.dwpcreator.domain.io.ZipProjectLoader
import com.jvk.dwpcreator.midi.MidiDeviceSummary
import com.jvk.dwpcreator.midi.MidiInputManager
import com.jvk.dwpcreator.midi.MidiNoteEvent
import com.jvk.dwpcreator.ui.state.DwpUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream

/**
 * The single owner of app state, connecting the pure-Kotlin, already-tested
 * domain layer (DwpEngine, ZipProjectLoader, ZipProjectExporter, WavDecoder)
 * and the native audio layer (SamplePlayer) to the UI.
 *
 * All heavy work (unzipping, tokenizing, re-zipping, audio decoding) runs
 * off the main thread via [Dispatchers.Default]/[Dispatchers.IO] inside
 * [viewModelScope], so the UI never freezes.
 */
class DwpCreatorViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        /** Ritmo mínimo/máximo por paso de progreso visible -- ver [paceAndPaintProgress]. */
        const val MIN_PROGRESS_STEP_MS = 4L
        const val MAX_PROGRESS_STEP_MS = 40L

        /** Cuánto tiempo se queda encendida una tecla tras un noteOn si nadie la apaga antes. */
        const val KEY_HIGHLIGHT_MS = 250L

        /** Búfer de lectura del zip de entrada (ver [loadFromZip]). */
        const val ZIP_READ_BUFFER_BYTES = 64 * 1024

        const val OUT_OF_MEMORY_MESSAGE =
            "El dispositivo se quedó sin memoria procesando este instrumento. Cierra otras apps e " +
                "inténtalo de nuevo, o usa un instrumento con menos audio."
    }

    private val _uiState = MutableStateFlow<DwpUiState>(DwpUiState.Empty)
    val uiState: StateFlow<DwpUiState> = _uiState.asStateFlow()

    private val _playingIndices = MutableStateFlow<Set<Int>>(emptySet())
    /**
     * Índices de todas las teclas con su luz encendida ahora mismo -- un
     * `Set`, no un `Int?` único, porque un teclado real permite acordes
     * (varios dedos a la vez) y arrastrar el dedo entre teclas sin soltar
     * (glissando): en ambos casos hay más de una tecla "sonando/resaltada"
     * simultáneamente. Ver [noteOn]/[noteOff].
     */
    val playingIndices: StateFlow<Set<Int>> = _playingIndices.asStateFlow()

    /**
     * Un `Job` de auto-apagado por índice, usado **únicamente** por
     * [previewSample] (toque simple sin gesto de "soltar" real, ver su
     * KDoc) para que dos teclas resaltadas a la vez no se pisen sus
     * temporizadores. [noteOn]/[noteOff] -- el camino real de "tecla
     * mantenida" (overlay táctil y MIDI) -- no programan ni dependen de
     * ningún temporizador aquí: la luz se apaga exclusivamente cuando llega
     * [noteOff]. Solo se toca desde el hilo principal (los eventos MIDI
     * saltan a él en [handleMidiNoteOn]/[handleMidiNoteOff]).
     */
    private val highlightJobs = mutableMapOf<Int, Job>()

    /**
     * Nota MIDI física (0-127) -> índice de muestra disparado por ella,
     * mientras esa tecla física sigue apretada. Necesario para que
     * [handleMidiNoteOff] sepa qué muestra apagar: el mensaje MIDI Note Off
     * solo trae el número de nota, no el índice de muestra, y volver a
     * buscarlo por rango lowKey..highKey en ese momento asumiría que el
     * mapeo de muestras no cambió entre el Note On y el Note Off (frágil si
     * el usuario recarga/edita el instrumento con la tecla física aún
     * apretada). Guardar el índice real usado en el Note On es exacto en
     * cualquier caso. Solo se toca desde el hilo principal (mismo motivo
     * que [highlightJobs]).
     */
    private val activeMidiNoteIndices = mutableMapOf<Int, Int>()

    /** The real in-memory project. Null until something is successfully loaded. */
    private var project: LoadedProject? = null

    private val samplePlayer = SamplePlayer()

    private val _midiConnected = MutableStateFlow(false)
    val midiConnected: StateFlow<Boolean> = _midiConnected.asStateFlow()

    private val _midiConnectedDeviceName = MutableStateFlow<String?>(null)
    val midiConnectedDeviceName: StateFlow<String?> = _midiConnectedDeviceName.asStateFlow()

    private val _midiDevices = MutableStateFlow<List<MidiDeviceSummary>>(emptyList())
    val midiDevices: StateFlow<List<MidiDeviceSummary>> = _midiDevices.asStateFlow()

    private val midiInputManager: MidiInputManager by lazy {
        MidiInputManager(getApplication()).apply {
            onConnectionChanged = { connected, name ->
                _midiConnected.value = connected
                _midiConnectedDeviceName.value = name
            }
            onNoteEvent = { event -> if (event.isNoteOn) handleMidiNoteOn(event) else handleMidiNoteOff(event) }
        }
    }

    /**
     * `true` mientras hay una importación o una exportación en curso. Toda
     * operación que cambia el proyecto o el estado (cargar, renombrar,
     * exportar) se niega a arrancar entonces: la UI ya desactiva sus botones,
     * pero esta es la garantía real -- dos exportaciones/cargas simultáneas
     * se pisarían `_uiState` y `project` entre sí.
     */
    private fun isBusy(): Boolean = when (val current = _uiState.value) {
        is DwpUiState.Importing -> true
        is DwpUiState.Loaded -> current.exportProgress != null
        else -> false
    }

    fun loadFromZip(uri: Uri) {
        if (isBusy()) return
        // Si la carga falla, la pantalla debe volver a lo que había (el proyecto
        // anterior sigue en memoria en `project`), no quedarse en "Empty" con un
        // proyecto cargado invisible detrás.
        val stateBeforeLoad = _uiState.value.let { if (it is DwpUiState.Error) it.previous else it }

        viewModelScope.launch {
            _uiState.value = DwpUiState.Importing(message = "Leyendo y emparejando muestras…")
            try {
                // El zip se descomprime directamente desde el flujo del archivo, sin
                // leerlo antes entero a un ByteArray (ver ZipProjectLoader.load(InputStream)).
                val loaded = withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado.")
                    ZipProjectLoader.load(BufferedInputStream(stream, ZIP_READ_BUFFER_BYTES))
                }

                project = loaded
                publishLoaded(loaded)
                // Precalentamiento de "mejor esfuerzo" (Doc/29 §... teclado profesional):
                // decodifica y cachea por adelantado el audio de las primeras muestras
                // mientras el usuario mira la lista recién cargada, para que tocar una
                // tecla no tenga que decodificarla la primera vez. No bloquea la UI ni
                // retrasa publishLoaded -- si el usuario toca antes de que termine, esa
                // muestra concreta simplemente se decodifica en el momento, como siempre.
                launch(Dispatchers.Default) { samplePlayer.prewarm(loaded.audioByIndex) }
            } catch (e: ZipProjectLoader.ZipLoadException) {
                _uiState.value = DwpUiState.Error(e.message ?: "Error al leer el zip.", stateBeforeLoad)
            } catch (e: OutOfMemoryError) {
                _uiState.value = DwpUiState.Error(OUT_OF_MEMORY_MESSAGE, stateBeforeLoad)
            } catch (e: Exception) {
                _uiState.value = DwpUiState.Error(
                    "No se pudo cargar el archivo: ${e.message ?: e::class.simpleName}",
                    stateBeforeLoad
                )
            }
        }
    }

    fun renameAll(newName: String) {
        if (isBusy()) return
        val current = project ?: return
        val loadedState = _uiState.value as? DwpUiState.Loaded ?: return
        val trimmed = newName.trim()

        // Defensa en profundidad: RenameAllDialog ya valida esto en vivo, pero
        // este es el único punto por el que un nombre llega al .dwp, y un nombre
        // no ASCII corrompe los nombres de TODAS las muestras (ver
        // InstrumentNameValidator). Se informa, no se ignora en silencio.
        InstrumentNameValidator.validate(trimmed)?.let { reason ->
            _uiState.value = DwpUiState.Error("Nombre no válido: $reason", loadedState)
            return
        }

        val oldName = DwpEngine.detectInstrumentBaseName(current.document)
        if (oldName == null) {
            _uiState.value = DwpUiState.Error(
                "No se pudo determinar el nombre actual del instrumento: sus muestras no siguen el " +
                    "patrón «Nombre_Nota_Velocidad», así que no hay un nombre base que renombrar.",
                loadedState
            )
            return
        }
        if (trimmed == oldName) return

        viewModelScope.launch {
            try {
                val renamedDoc = withContext(Dispatchers.Default) {
                    DwpEngine.renameInstrument(current.document, oldName, trimmed)
                }
                val renamedProject = current.copy(document = renamedDoc)
                project = renamedProject
                publishLoaded(renamedProject)
            } catch (e: Exception) {
                // p. ej. DwpFormatException si el contenedor de alguna muestra no
                // cuadra exacto: antes esta excepción no se capturaba en ningún
                // sitio y cerraba la app.
                _uiState.value = DwpUiState.Error(
                    "No se pudo renombrar el instrumento: ${e.message ?: e::class.simpleName}",
                    loadedState
                )
            }
        }
    }

    fun exportTo(uri: Uri) {
        if (isBusy()) return
        val current = project ?: return
        val loadedState = _uiState.value as? DwpUiState.Loaded ?: return
        val total = current.audioByIndex.size

        viewModelScope.launch {
            _uiState.value = loadedState.copy(
                exportProgress = DwpUiState.ExportProgress(0, total, "Preparando", "")
            )
            val ticks = Channel<DwpUiState.ExportProgress>(Channel.UNLIMITED)
            val paintJob = launch { paceAndPaintProgress(total, loadedState, ticks) }
            try {
                // Fase 1: validar y resincronizar (todo lo que puede fallar por el
                // contenido del proyecto), SIN tocar aún el archivo de destino.
                val prepared = withContext(Dispatchers.Default) {
                    ZipProjectExporter.prepare(current, loadedState.instrumentName) { doneCount, totalCount, sampleName ->
                        ticks.trySend(DwpUiState.ExportProgress(doneCount, totalCount, "Empaquetando", sampleName))
                    }
                }
                ticks.close()
                paintJob.join() // espera a que la barra termine de pintar el 100% real antes de dar por cerrado el overlay

                // Fase 2: volcar el zip directamente al archivo, muestra a muestra --
                // sin construirlo antes entero en un ByteArray (ver PreparedZipExport.writeTo).
                _uiState.value = loadedState.copy(
                    exportProgress = DwpUiState.ExportProgress(total, total, "Escribiendo archivo", "")
                )
                withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("No se pudo escribir el archivo de salida.")
                    prepared.writeTo(stream)
                }
                _uiState.value = loadedState.copy(exportProgress = null)
            } catch (e: OutOfMemoryError) {
                ticks.close()
                paintJob.cancel()
                _uiState.value = DwpUiState.Error(
                    message = "No se pudo exportar: $OUT_OF_MEMORY_MESSAGE",
                    previous = loadedState.copy(exportProgress = null)
                )
            } catch (e: Exception) {
                ticks.close()
                paintJob.cancel()
                _uiState.value = DwpUiState.Error(
                    message = "No se pudo exportar: ${e.message ?: e::class.simpleName}",
                    previous = loadedState.copy(exportProgress = null)
                )
            }
        }
    }

    /**
     * Exporta el proyecto actual como un único `.dwp` Monolithic
     * autocontenido: el audio de cada muestra queda embebido como FLAC
     * dentro del propio `.dwp` (vía [MonolithicDwpProjectBuilder]), sin
     * archivos `.wav` externos.
     *
     * Mismo patrón exacto que [exportTo] (mismo estado, mismo manejo de
     * errores, mismo `exportProgress`, mismo pintado con ritmo vía
     * [paceAndPaintProgress]) -- deliberado: para el usuario ambas
     * exportaciones son "la misma acción con un formato distinto", no dos
     * flujos separados.
     *
     * Compatibilidad real con DirectWave/FL Studio para este formato
     * todavía **no está confirmada** (ver `Doc/23`/`Doc/26`); esta función
     * no lo oculta ni lo decide por el usuario -- ese aviso vive en la UI
     * que ofrece elegir entre este formato y el ZIP clásico
     * ([com.jvk.dwpcreator.ui.components.ExportFormatDialog]).
     */
    fun exportMonolithicTo(uri: Uri) {
        if (isBusy()) return
        val current = project ?: return
        val loadedState = _uiState.value as? DwpUiState.Loaded ?: return
        val total = current.audioByIndex.size

        viewModelScope.launch {
            _uiState.value = loadedState.copy(
                exportProgress = DwpUiState.ExportProgress(0, total, "Preparando", "")
            )
            val ticks = Channel<DwpUiState.ExportProgress>(Channel.UNLIMITED)
            val paintJob = launch { paceAndPaintProgress(total, loadedState, ticks) }
            try {
                val dwpBytes = withContext(Dispatchers.Default) {
                    MonolithicDwpProjectBuilder.buildBytes(current) { doneCount, totalCount, sampleName ->
                        ticks.trySend(DwpUiState.ExportProgress(doneCount, totalCount, "Codificando FLAC", sampleName))
                    }
                }
                ticks.close()
                paintJob.join()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(dwpBytes) }
                        ?: throw IllegalStateException("No se pudo escribir el archivo de salida.")
                }
                _uiState.value = loadedState.copy(exportProgress = null)
            } catch (e: OutOfMemoryError) {
                ticks.close()
                paintJob.cancel()
                _uiState.value = DwpUiState.Error(
                    message = "No se pudo exportar el .dwp monolítico: $OUT_OF_MEMORY_MESSAGE",
                    previous = loadedState.copy(exportProgress = null)
                )
            } catch (e: Exception) {
                ticks.close()
                paintJob.cancel()
                _uiState.value = DwpUiState.Error(
                    message = "No se pudo exportar el .dwp monolítico: ${e.message ?: e::class.simpleName}",
                    previous = loadedState.copy(exportProgress = null)
                )
            }
        }
    }

    /**
     * El trabajo real de exportar (empaquetar ZIP / codificar FLAC) es tan
     * rápido para un proyecto normal que puede terminar por completo en
     * menos de un frame de pantalla (~16ms) -- Compose solo pinta el
     * último valor de estado que exista cuando le toca dibujar, así que sin
     * este paso intermedio la barra "salta" directa al 100% sin que el ojo
     * llegue a ver ningún paso intermedio (hallazgo real, reportado sobre
     * el build compilado).
     *
     * Esto NO ralentiza el trabajo real ni inventa ningún valor: [ticks]
     * recibe cada progreso *ya ocurrido de verdad* tan rápido como el
     * productor (el bucle de exportación, en otro hilo) lo genere; esta
     * función solo controla cuánto tiempo mínimo se deja cada valor real en
     * pantalla antes de pasar al siguiente, para que sea perceptible. El
     * techo por paso (`MIN_PROGRESS_STEP_MS`/`MAX_PROGRESS_STEP_MS`) se
     * calcula dinámicamente a partir de `total` para que la duración total
     * de la animación se mantenga en un rango razonable (~0.9s) sin
     * importar si el proyecto tiene 3 muestras o 500 -- un proyecto grande
     * no debe convertirse en un espectáculo de luces de varios segundos
     * solo por esto.
     */
    private suspend fun paceAndPaintProgress(
        total: Int,
        loadedState: DwpUiState.Loaded,
        ticks: Channel<DwpUiState.ExportProgress>
    ) {
        val targetTotalAnimationMs = 900L
        val stepDelayMs = (targetTotalAnimationMs / total.coerceAtLeast(1))
            .coerceIn(MIN_PROGRESS_STEP_MS, MAX_PROGRESS_STEP_MS)
        for (tick in ticks) {
            _uiState.value = loadedState.copy(exportProgress = tick)
            delay(stepDelayMs)
        }
    }

    /**
     * Dispara la muestra [index] a [velocity] (convención MIDI, 1..127) y
     * enciende su tecla **de forma sostenida**: la luz (y, en `SamplePlayer`,
     * el audio -- ver su KDoc) permanecen encendidos hasta que llegue un
     * [noteOff] explícito para este mismo índice, sin ningún temporizador
     * de auto-apagado. Es el comportamiento real de una tecla de
     * piano/teclado: mientras el dedo (o la tecla física MIDI) sigue
     * apretado, suena; solo se calla al soltar.
     *
     * Seguro de llamar para varios índices a la vez (acorde) o
     * repetidamente para el mismo índice (p. ej. dos dedos sobre la misma
     * tecla) -- [SamplePlayer] apila una voz por cada llamada y
     * [noteOff] apaga la más reciente.
     */
    fun noteOn(index: Int, velocity: Int = 127) {
        val current = project ?: return
        val audio = current.audioByIndex.getOrNull(index) ?: return

        // El AUDIO se lanza en su propio job para no bloquear el hilo
        // principal mientras `SamplePlayer` decodifica/cachea la muestra la
        // primera vez -- ver DecodedSampleCache. `noteOff`, si llega antes
        // de que este `launch` alcance a ejecutar `samplePlayer.play`,
        // igual apaga correctamente la voz en cuanto esta arranca: `play`
        // registra su `VoiceHandle` de forma sincrónica al principio de su
        // propio cuerpo, y `stopNote` simplemente no encuentra nada que
        // detener si `play` todavía no llegó a correr -- pero como la luz
        // (`_playingIndices`) ya se apagó igual (ver `noteOff`), no queda
        // ningún rastro visible de una nota que "se quedó pegada".
        viewModelScope.launch(Dispatchers.Default) { samplePlayer.play(index, audio, velocity) }

        // Si esta tecla venía de un `previewSample` (toque simple) cuyo
        // temporizador de auto-apagado todavía no corrió, se cancela: a
        // partir de aquí el apagado es exclusivamente responsabilidad de
        // `noteOff`, un temporizador viejo apagando la luz por su cuenta
        // sería exactamente el bug que se corrige aquí.
        highlightJobs.remove(index)?.cancel()
        _playingIndices.value = _playingIndices.value + index
    }

    /**
     * Apaga la tecla [index] de inmediato -- para cuando el dedo se levanta
     * o se desliza a otra tecla (arrastre/glissando, ver el overlay de
     * gestos de `MainScreen`), o cuando se suelta la tecla física
     * correspondiente en un teclado MIDI. Apaga tanto la luz
     * (`_playingIndices`) como el audio real: [SamplePlayer.stopNote] corta
     * la voz activa de esa muestra con un `fade-out` corto anti-clic en vez
     * de dejarla sonar hasta el final -- si no hay ninguna voz activa para
     * este índice (la muestra ya había terminado sola de reproducirse), no
     * hace nada.
     */
    fun noteOff(index: Int) {
        highlightJobs.remove(index)?.cancel()
        _playingIndices.value = _playingIndices.value - index
        samplePlayer.stopNote(index)
    }

    /**
     * Reproduce la muestra [index] como un toque simple y breve -- usado
     * por el clic en el nombre/número de una fila (`SampleRow.onPreview`),
     * fuera de la columna de teclas de piano, donde no existe ningún gesto
     * de "soltar" que dispare [noteOff] (un `combinedClickable.onClick` es
     * un evento instantáneo, no un contacto sostenido). Por eso, y solo
     * para este camino, la tecla se enciende con [noteOn] y se apaga sola
     * transcurrido [KEY_HIGHLIGHT_MS] -- un destello de confirmación visual,
     * no una nota mantenida.
     *
     * El overlay de gestos del teclado (toque/arrastre real sobre las
     * teclas) y el MIDI físico ([handleMidiNoteOn]/[handleMidiNoteOff]) NO
     * pasan por aquí: usan [noteOn]/[noteOff] directamente, porque ahí sí
     * hay un evento real de "soltar" que debe mandar sobre cualquier
     * temporizador.
     */
    fun previewSample(index: Int, velocity: Int = 127) {
        noteOn(index, velocity)
        highlightJobs[index] = viewModelScope.launch {
            delay(KEY_HIGHLIGHT_MS)
            noteOff(index)
        }
    }

    /** Rescans for currently visible MIDI devices (call when opening the device panel). */
    fun refreshMidiDevices() {
        _midiDevices.value = midiInputManager.listDevices()
    }

    fun connectToMidiDevice(deviceId: Int) {
        midiInputManager.connectTo(deviceId)
    }

    fun disconnectMidi() {
        midiInputManager.disconnect()
        // Si el dispositivo se desconecta con alguna tecla física todavía
        // apretada, nunca va a llegar su Note Off correspondiente: sin
        // esto, esa nota se quedaría con la luz encendida (y, en teoría, la
        // voz sonando) para siempre.
        activeMidiNoteIndices.values.toList().forEach(::noteOff)
        activeMidiNoteIndices.clear()
    }

    /**
     * Matches an incoming MIDI Note On to the sample whose key range covers
     * it -- following the .dwp's own lowKey..highKey zone exactly as
     * verified in the binary audit (not a nearest-neighbor guess), so
     * playback here matches what DirectWave/FL Studio Mobile would
     * actually trigger. Enciende la nota de forma **sostenida** (ver
     * [noteOn]): un teclado MIDI físico manda su propio Note Off exacto
     * cuando la tecla se suelta ([handleMidiNoteOff]), así que aquí no hace
     * falta ningún temporizador de auto-apagado -- a diferencia de
     * [previewSample], sí existe un evento real de "soltar" que esperar.
     */
    private fun handleMidiNoteOn(event: MidiNoteEvent) {
        // `MidiReceiver.onSend` corre en un hilo propio del servicio MIDI, NO en el
        // principal: aquí se sale a él antes de tocar `highlightJobs`/`activeMidiNoteIndices`
        // (mapas no thread-safe) o el estado de la UI. `viewModelScope` ya despacha en el
        // hilo principal.
        viewModelScope.launch {
            val samples = (_uiState.value as? DwpUiState.Loaded)?.samples ?: return@launch
            val match = samples.firstOrNull { event.note in it.lowKey..it.highKey } ?: return@launch
            activeMidiNoteIndices[event.note] = match.index
            noteOn(match.index, event.velocity)
        }
    }

    /**
     * Apaga la nota disparada por [handleMidiNoteOn] para esta misma nota
     * MIDI física, si sigue activa. Usa el índice de muestra guardado en el
     * momento del Note On ([activeMidiNoteIndices]) en vez de volver a
     * resolver el rango lowKey..highKey ahora, para ser exacto incluso si
     * el instrumento cargado cambió entre el Note On y el Note Off.
     */
    private fun handleMidiNoteOff(event: MidiNoteEvent) {
        viewModelScope.launch {
            val index = activeMidiNoteIndices.remove(event.note) ?: return@launch
            noteOff(index)
        }
    }

    fun dismissError() {
        val current = _uiState.value
        if (current is DwpUiState.Error) {
            _uiState.value = current.previous
        }
    }

    private fun publishLoaded(loaded: LoadedProject) {
        val name = DwpEngine.detectInstrumentBaseName(loaded.document) ?: "Instrument"
        val samples = DwpEngine.listSamples(loaded.document)
        _uiState.value = DwpUiState.Loaded(instrumentName = name, samples = samples)
    }

    override fun onCleared() {
        super.onCleared()
        samplePlayer.releaseAll()
        if (_midiConnected.value) {
            midiInputManager.disconnect()
        }
    }
}
