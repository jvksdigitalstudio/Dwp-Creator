# 41 — Mini-mezclador por muestra: Mute, Solo, Pan y Volumen

## Reporte del usuario

Dos referencias: una captura de la app señalando una fila de muestra, y una
captura anotada a mano de un canal de mezcla con: un botón de
activar/desactivar (mute), un botón "Solo", un control de "Pan" y un slider
de volumen ("vol +/-"). Pedido explícito: **cada muestra** de la lista debe
llevar estos cuatro controles a su lado (no el nombre del preset de la
referencia, ese se descarta) -- "al lado de cada muestra", en la misma fila.

## Alcance decidido (documentado para que quede explícito, no implícito)

Estos controles afectan **solo la previsualización en vivo dentro de esta
app**. No se escriben en el `.dwp`/zip exportado: el formato DirectWave no
tiene un campo estándar para "mute/solo/pan/volumen por muestra" que
`DwpEngine`/`MonolithicDwpBuilder` puedan preservar sin inventar una
extensión no estándar del formato, y el pedido original describe un panel de
control tipo mezclador para escuchar el instrumento mientras se revisa, no
una propiedad documental del instrumento en sí. Ver KDoc de
`SampleMixerState` para el razonamiento completo. Si más adelante se pide
persistir esto en el export, es un cambio deliberadamente aparte.

Solo se aplica al **audio real de previsualización** (tocar la tecla del
piano, arrastre/glissando, acorde, MIDI físico) -- no al ícono del sampler
(`EffectsPanel`), que sigue siendo un placeholder sin funcionalidad propia
pedido así explícitamente en una sesión anterior.

## Semántica de cada control

- **Mute (`M`)**: si está activo, esa muestra no suena al tocarla -- salvo
  que alguna otra esté en Solo (ver abajo). No detiene una nota que ya
  estuviera sonando en ese instante; silencia la *siguiente* vez que se
  toque, igual que en cualquier mesa de mezcla real.
- **Solo (`S`)**: en cuanto **cualquier** muestra del instrumento está en
  Solo, el resto se silencia entero, sin importar su propio Mute individual.
  No es exclusivo/radio-button: varias muestras pueden estar en Solo a la
  vez, y se oyen todas juntas.
- **Pan**: -1f (extremo izquierdo) .. 0f (centro) .. 1f (extremo derecho).
  Riel horizontal compacto con un "thumb" arrastrable; arrastrar salta
  directamente a la posición X tocada (no es un delta relativo).
- **Volumen**: 0f (silencio) .. 1f (ganancia unitaria, "de fábrica"). Mismo
  tipo de riel, con relleno progresivo estilo fader.

La luz de la tecla (`_playingIndices`) **siempre** se enciende al tocar,
esté o no muteada -- es la confirmación visual de que el gesto se registró;
solo el audio real se omite si corresponde.

## Cambios por archivo

### `ui/state/SampleMixerState.kt` (nuevo)
`data class SampleMixerState(muted, solo, pan, volume)` con `DEFAULT` neutro
e `isDefault()`.

### `viewmodel/DwpCreatorViewModel.kt`
- `_mixerStates: MutableStateFlow<Map<Int, SampleMixerState>>` -- solo
  guarda entradas que se apartan del neutro (la inmensa mayoría de un
  instrumento de 48+ muestras nunca toca estos controles).
- `mixerStateFor(index)`, `toggleMute(index)`, `toggleSolo(index)`,
  `setPan(index, pan)`, `setVolume(index, volume)`.
- `isAudible(index)`: resuelve la semántica real de Solo/Mute descrita
  arriba, consultada desde `noteOn` antes de gastar una voz del pool.
- `noteOn` pasa `mixer.volume`/`mixer.pan` a `samplePlayer.play(...)`.
- **Reseteado al cargar un instrumento nuevo** (`loadFromZip`), igual que
  `_playingIndices`/`highlightJobs`/la caché de audio (`Doc/39`): un ajuste
  de mezcla puesto en la posición 3 del instrumento anterior no tiene ningún
  sentido aplicado a la muestra que ahora ocupa esa misma posición.

### `audio/SamplePlayer.kt` -- Pan y Volumen reales, no solo de UI
`play()` ahora acepta `volume: Float = 1f, pan: Float = 0f`. Cambios de
fondo, no cosméticos:

- **Toda voz se reproduce ahora por una pista de salida SIEMPRE estéreo**
  (`STEREO_CHANNEL_COUNT = 2`), sin importar si la muestra de origen es mono
  o estéreo. Una pista de salida mono no tiene manera física de posicionar
  el sonido entre izquierda y derecha -- por eso una muestra mono se "sube"
  a estéreo duplicando su señal en ambos canales, cada uno con su propia
  ganancia.
- **Ley de paneo de potencia constante** (`resolvePanGains`, equal-power pan
  law), no un paneo lineal simple -- mantiene el volumen percibido constante
  en todo el recorrido del control, estándar de cualquier DAW.
- Las ganancias se aplican **directamente sobre el PCM**, muestra a muestra
  (`writeStereoChunk`), no con `AudioTrack.setStereoVolume` (deprecada desde
  API 21, y de todas formas inútil sobre una pista que antes podía ser
  mono).
- El `fade-out` de liberación (`Doc/39`) ahora combina su envolvente 1→0 con
  las mismas ganancias de pan/volumen (`writeReleaseFadeTail`), en vez de
  ser un mecanismo aparte.
- **Simplificación deliberada y documentada**: para una muestra ya estéreo,
  esto actúa como *balance* (escala cada canal de origen) en vez de un
  paneo "puro" que mezclara los canales entre sí -- suficiente para un
  mezclador de previsualización de muestras de instrumento (casi siempre
  mono en un `.dwp` real).
- Pan/Volumen se fijan una sola vez al iniciar la nota, con el ajuste
  vigente en ese instante -- no hay automatización en vivo a media nota
  sostenida.

### `ui/components/SampleMixerControls.kt` (nuevo)
`SampleMixerStrip` (contenedor) + `MixerToggleButton` (Mute/Solo, botón
circular con caption "M"/"S", colores de alerta estándar de mezclador:
rojo para Mute, ámbar para Solo -- deliberadamente distintos de los colores
de octava para que un vistazo nunca los confunda) + `MixerPanControl` /
`MixerVolumeControl` (rieles compactos con thumb arrastrable, construidos a
mano con `pointerInput`/`detectHorizontalDragGestures`, en el mismo lenguaje
visual "premium" del resto de la app -- no widgets `Slider` de Material3
genéricos).

### `ui/components/SampleRow.kt`
Inserta `SampleMixerStrip` entre el nombre y la tecla de piano, en la MISMA
línea (pedido explícito: "al lado de cada muestra"). Contrapartida real y
documentada: el nombre dispone de menos ancho y se trunca con `maxLines = 1`
+ `TextOverflow.Ellipsis` en teléfonos angostos -- la misma tensión que
tiene cualquier DAW mobile entre "nombre de pista visible" y "controles de
canal visibles", resuelta a favor de los controles siempre visibles.

### `ui/theme/Color.kt`
Colores nuevos del mezclador (`MixerControlTrackBg`, `MixerControlBorder`,
`MixerControlThumb`, `MixerControlFill`, `MixerCaptionText`,
`MixerMuteActive(Text)`, `MixerSoloActive(Text)`).

### `ui/screens/MainScreen.kt` / `MainActivity.kt`
Nuevos parámetros (`mixerStates`, `onToggleMute`, `onToggleSolo`,
`onPanChange`, `onVolumeChange`) propagados desde el `ViewModel` hasta cada
`SampleRow`.

## Pendiente de verificación en dispositivo real

Sin dispositivo/emulador Android en este entorno (misma limitación de
siempre), falta confirmar en hardware real:

- Que el "upmix" mono->estéreo no introduzca ningún artefacto audible.
- Percepción real del paneo de potencia constante en auriculares vs.
  altavoz del teléfono.
- Que el ancho de la franja de mezclador (148dp + 16dp de padding) deje
  suficiente espacio legible para el nombre en los teléfonos más angostos
  del mercado -- si no, la alternativa documentada es mover la franja a una
  segunda línea por muestra en vez de la misma línea.
