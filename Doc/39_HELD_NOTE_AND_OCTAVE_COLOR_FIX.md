# 39 — Nota sostenida real (audio + luz) y coloreado de tecla activa por octava

## Contexto / reporte del usuario

Captura anotada a mano (flecha verde) sobre la fila `F#3`: al presionar una
tecla **sin soltar el dedo**, la tecla se "apagaba" (dejaba de sonar / dejaba
de resaltarse) de inmediato, como si el sistema soltara la tecla por su
cuenta mientras el dedo seguía físicamente apoyado sobre la pantalla. Un
piano o teclado profesional no se comporta así: debe sonar **mientras** se
mantiene pulsada, y silenciarse **solo** cuando el dedo se levanta o se
desliza a otra tecla.

Segunda corrección pedida en la misma sesión: al presionar una o más teclas
(incluyendo acordes y arrastre/glissando entre teclas), cada tecla activa
debe pintarse con el **color de su propia octava** (la misma paleta que ya
se usaba para el número/nombre de la fila y la banda lateral del Do), no con
un tono "pulsada" genérico compartido por todo el teclado.

## Causa raíz #1 — luz y audio con vida propia, desconectados del gesto real

`DwpCreatorViewModel.noteOn(index)` encendía la luz (`_playingIndices`) y
lanzaba el audio, pero programaba **un temporizador fijo de 250ms**
(`KEY_HIGHLIGHT_MS`) que apagaba la luz sí o sí, sin consultar si el dedo
seguía apretado:

```kotlin
// ANTES
highlightJobs[index] = viewModelScope.launch {
    delay(KEY_HIGHLIGHT_MS)
    _playingIndices.value = _playingIndices.value - index
}
```

El overlay de gestos (`PianoKeyGestureOverlay` en `MainScreen.kt`) sí
distinguía correctamente `pressed`/`released` por dedo y llamaba a
`onNoteOff` en el momento correcto — el bug no estaba ahí, estaba en que el
ViewModel ignoraba esa señal y apagaba la tecla por su cuenta con un timer
ciego.

Además, incluso arreglando solo la luz, el **audio** seguía siendo un
one-shot puro: `SamplePlayer.play()` escribía la muestra completa en un
único `AudioTrack.write()` bloqueante y la dejaba sonar hasta el final
pasara lo que pasara con el dedo — no existía ningún mecanismo para
cortarla antes.

## Causa raíz #2 — la tecla activa no usaba el color de su octava

`PianoKeyBadge` sí recibía `octaveAccentColor` por parámetro, pero solo lo
usaba para la banda lateral del Do (`isOctaveStart`). El estado `active`
(tecla sonando) pintaba siempre con un único par de colores "pulsada" fijo
(`WhiteKeyPressedGradient*` / `BlackKeyPressedGradient*`), igual para
cualquier octava.

## Corrección — resumen técnico

### 1. `SamplePlayer` (`app/src/main/java/.../audio/SamplePlayer.kt`)

- `play()` ahora escribe el PCM en **trozos de ~10ms** (`WRITE_CHUNK_MS`) en
  vez de en un único `write()` bloqueante de todo el archivo. Esto es lo que
  permite reaccionar a media reproducción: un único `write()` gigante no
  deja ningún punto de control hasta que termina.
- Cada llamada a `play()` registra un `VoiceHandle` (bandera
  `AtomicBoolean releaseRequested`) apilado por índice de muestra
  (`voiceHandlesByIndex`, protegido por `voiceHandlesLock`). Entre cada
  trozo escrito se consulta esa bandera.
- **`stopNote(index)`** (nuevo método público): desapila el `VoiceHandle`
  más reciente para ese índice y marca `releaseRequested = true`. Si la
  muestra ya había terminado sola, no hay ningún handle que cortar y no
  hace nada.
- Cuando el escritor detecta `releaseRequested == true` a media
  reproducción, **no corta la voz en seco** (eso sonaría como un "clic" por
  la discontinuidad brusca de la forma de onda): escribe una cola corta
  (`RELEASE_FADE_MS` = 12ms) con un `fade-out` lineal calculado sobre una
  copia temporal del PCM restante (nunca se muta el `ByteArray` cacheado en
  `DecodedSampleCache`, que se reutiliza en pulsaciones futuras de la misma
  muestra) y libera la voz de inmediato después (`pause()` + `flush()`),
  sin esperar el resto de la muestra original.
- Reproducción natural hasta el final (sin `stopNote` de por medio, p. ej.
  una muestra corta más breve que la pulsación) sigue funcionando
  exactamente igual que antes.

### 2. `DwpCreatorViewModel` (`app/src/main/java/.../viewmodel/DwpCreatorViewModel.kt`)

- **`noteOn(index)`**: enciende la luz y lanza el audio, **sin** programar
  ningún auto-apagado. A partir de ahora solo se apaga vía `noteOff`.
- **`noteOff(index)`**: apaga la luz de inmediato y llama a
  `samplePlayer.stopNote(index)` para cortar el audio real (con el
  fade-out corto descrito arriba).
- **`previewSample(index)`**: pasa a ser el único lugar que conserva el
  comportamiento de "destello" de 250ms (`KEY_HIGHLIGHT_MS`) — se usa
  exclusivamente para el toque simple en el nombre/número de una fila
  (`SampleRow.onPreview`, un `combinedClickable.onClick` instantáneo sin
  ningún gesto de "soltar" que observar). Internamente llama a
  `noteOn` + un `delay` + `noteOff`.
- El overlay de gestos del teclado y el MIDI físico **no** pasan por
  `previewSample`: usan `noteOn`/`noteOff` directamente, porque ahí sí hay
  un evento real de "soltar" que debe mandar sobre cualquier temporizador.
- **MIDI físico**: antes, `MidiInputManager.onNoteEvent` **descartaba por
  completo** los eventos Note Off (`if (event.isNoteOn) handleMidiNoteOn(event)`,
  sin rama `else`) — un teclado MIDI real tenía exactamente el mismo
  síntoma que el táctil. Se corrigió en el mismo paso, por ser la misma
  causa raíz: se añadió `handleMidiNoteOff`, y un mapa
  `activeMidiNoteIndices: Map<nota MIDI, índice de muestra>` para que el
  Note Off sepa qué muestra apagar sin tener que volver a resolver el rango
  `lowKey..highKey` en ese momento. `disconnectMidi()` también libera
  cualquier nota que hubiera quedado sonando si el dispositivo se
  desconecta con una tecla física todavía apretada.

### 3. `PianoKeyBadge` (`app/src/main/java/.../ui/components/PianoKeyBadge.kt`) + `Color.kt`

- Mientras `active == true` y se provee `octaveAccentColor`, el barniz
  vertical de la tecla (antes un tono "pulsada" fijo) se deriva ahora del
  color real de la octava: `lerp(accent, White, 0.30f)` arriba y
  `lerp(accent, Black, 0.30f)` abajo, conservando el mismo lenguaje visual
  de "superficie física en degradado" que ya tenía el resto del teclado.
- El color del texto de la nota, mientras está activa, se decide por la
  **luminancia relativa real** del acento (`Color.luminance()`, umbral
  0.5) en vez de un valor fijo por caso — así sigue siendo legible aunque
  la paleta `OctaveColors` cambie más adelante. Colores nuevos:
  `ActiveAccentTextDark` / `ActiveAccentTextLight` en `Color.kt`.
- Si no se provee acento (`Color.Unspecified`), se conserva el
  comportamiento anterior (tono "pulsada" genérico) como fallback seguro.
- Esto aplica a **toda** tecla activa, no solo al Do: en un acorde o un
  arrastre (glissando), cada tecla que suena se pinta con el color de su
  propia octava mientras sigue activa, y vuelve a su acabado neutro al
  soltarla — que es exactamente el efecto pedido.

## Por qué no es un parche

No se añadió ninguna bandera adicional para "simular" el sostenido, ni se
recortó el temporizador a un número más corto. Se rediseñó el mecanismo de
escritura de audio para que sea real y verificablemente cortable
(`WRITE_CHUNK_MS` + `VoiceHandle`), y se hizo que la luz de la UI dependa
exclusivamente de la señal real de "soltar" (`noteOff`) en vez de un
temporizador desconectado del gesto. El color de tecla activa pasó a
derivarse matemáticamente del acento real de la octava (`lerp` +
`luminance`) en vez de codificar un color fijo adicional por caso.

## Pendiente de verificación en dispositivo real

Este entorno de trabajo no tiene un dispositivo/emulador Android disponible
(ver limitación ya documentada en `AudioVoicePool`). Falta confirmar en
hardware real:

- Que el fade-out de 12ms sea imperceptible como "clic" en distintos
  dispositivos/rutas de audio (baja latencia vs. ruta normal).
- Que el troceo en ventanas de ~10ms no introduzca artefactos audibles en
  muestras con sample rates atípicos.
- Contraste de texto de `ActiveAccentTextDark`/`ActiveAccentTextLight`
  sobre cada color real de `OctaveColors`, a simple vista en pantalla.
