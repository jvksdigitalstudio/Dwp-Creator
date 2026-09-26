# 29 — TECLAS DE PIANO SIN COLOR POR OCTAVA + AUDITORÍA DE ESTABILIDAD DE LA APP

**Fecha:** 2026-09-24
**Versión:** `0.4.0-piano-keys-stability` (`versionCode` 2)
**Punto de partida:** proyecto tal como llegó en `DwpCreator.zip` (estado de `Doc/28` + mejoras de UI de exportación posteriores).
**Alcance pedido:** (1) quitar el marco de color por octava de cada tecla del piano para que se vea como un piano normal; (2) antes de entregar, auditar el proyecto y corregir los errores/bugs encontrados.

> **Limitación de verificación (leer primero).** Este entorno no tiene compilador Kotlin, Gradle, Android SDK ni red (igual que en `Doc/25`–`Doc/28`). Todo lo de este documento está verificado por **lectura de código real, razonamiento sobre la semántica documentada de Compose/AudioTrack/coroutines y comprobación estática de balance de llaves/paréntesis** en cada archivo tocado. **No se ha compilado ni ejecutado ningún test de esta pasada.** Los 22 tests nuevos están escritos, no ejecutados. El siguiente paso real es la corrida de CI (`gradle testDebugUnitTest` + `assembleDebug`) y la prueba en dispositivo (§7).

---

## 1. Cambio pedido: teclas de piano sin marco de color

**Antes.** `PianoKeyBadge` recibía `accentColor` (el color de la octava) y lo usaba para el borde (`accentColor.copy(alpha = 0.55f)`) y para el fondo de la tecla cuando sonaba. Resultado en pantalla: cada octava con un marco de color distinto (verde, magenta, lima, celeste…).

**Ahora.**
- `PianoKeyBadge` ya **no tiene parámetro `accentColor`**. Tecla blanca = blanca, tecla negra = negra.
- Borde neutro y casi imperceptible del propio tono de la tecla (`WhiteKeyBorder` `#BDB2D0`, `BlackKeyBorder` `#3A3A4A`), solo para separar teclas contiguas.
- Al sonar, **un único color de "pulsada" compartido por todas las octavas** (`WhiteKeyPressedBg` `#D6B9FF`, `BlackKeyPressedBg` `#6A2BC2`, texto blanco en la negra).
- El color por octava se **conserva donde sí ayuda a escanear la lista**: número y nombre de cada fila (`SampleRow`). No se tocó.
- Constantes nuevas en `ui/theme/Color.kt`; el tamaño de la tecla (`PianoKeyWidth`/`PianoKeyHeight`) no cambió, así que el overlay de gestos multi-touch sigue calculando la misma franja.

Archivos: `ui/theme/Color.kt`, `ui/components/PianoKeyBadge.kt`, `ui/components/SampleRow.kt`.

---

## 2. Hallazgos de la auditoría y correcciones

Cada hallazgo indica cómo se sabe que es real. Severidad: **A** = rompe o corrompe datos / crash; **M** = comportamiento incorrecto visible o audible; **B** = robustez/UX.

### H-01 (M) — La primera fila salía con el color de otra octava y el contador decía "5 octaves" (visible en tu captura)
- **Evidencia:** en la captura, `Instrument_C3_127` va en verde y `C#3`…`B3` en magenta, y la barra inferior dice "5 octaves" para un instrumento C3–B6 (4 octavas). `MainScreen` calculaba la octava con `sample.lowKey / 12`, pero la zona de la muestra más grave está extendida hasta la nota MIDI 0 (`DwpEngine.listSamples`, `Doc/05`): `0 / 12 = 0`. Esa fila caía en "octava 0" (color verde de `OctaveColors[0]`) y añadía una octava fantasma al conteo.
- **Corrección:** nuevo `ui/state/SampleOctave.kt`. La octava sale de **la nota** (`C#3` → 3); si el nombre no la trae, de `rootKey`, y solo como último recurso de `lowKey`. `MainScreen` lo usa para el color de fila y para el contador.
- **Tests:** `SampleOctaveTest` (7), incluido el fixture real de 48 muestras → exactamente 4 octavas y misma octava para `samples[0]` y `samples[1]`.

### H-02 (A) — Renombrar con acentos, ñ o emojis destruía los nombres de todas las muestras
- **Evidencia (código):** `DwpBlock.isTextPayload()` solo acepta bytes `0x20..0x7E`. El nombre nuevo se escribe en ISO-8859-1 (`renameRecursive`). Con `"Acústico"`, el byte `0xFA` hace que el bloque ya no decodifique como texto → `listSamples` devuelve `sample_0…sample_47` y nota `?`, `detectInstrumentBaseName` devuelve `null`, y al exportar `ZipProjectExporter` reescribe nombre y ruta de cada muestra con ese `sample_N`. El diálogo aceptaba el nombre sin decir nada. Para un usuario hispanohablante es un caso muy probable.
- **Corrección:** nuevo `domain/dwp/InstrumentNameValidator.kt` (solo ASCII imprimible; rechaza `/ \ : * ? " < > |`, vacío, espacios en extremos y punto final). `RenameAllDialog` valida en vivo (campo en error + botón desactivado); `DwpCreatorViewModel.renameAll` vuelve a validar y muestra un diálogo de error en vez de ignorar en silencio.
- **Tests:** `InstrumentNameValidatorTest` (9). El último **reproduce el daño contra el fixture real** (`"Acústico"` → `sample_0` / base `null`), para que no sea una afirmación sin evidencia.

### H-03 (A) — `renameAll` podía cerrar la app
- **Evidencia (código):** `DwpEngine.renameInstrument` → `tokenizeStrict` puede lanzar `DwpFormatException` si el contenedor de alguna muestra no cuadra exacto (el parseo inicial solo valida el nivel superior). La llamada estaba en `viewModelScope.launch` **sin `try/catch`** → excepción no capturada → crash.
- **Corrección:** `try/catch` con diálogo de error que devuelve a la pantalla cargada. Además, si el instrumento no tiene nombre base detectable, ahora se informa (antes: `return` silencioso).

### H-04 (M) — El overlay de teclas se reiniciaba en pleno toque y re-disparaba notas
- **Evidencia (código):** `PianoKeyGestureOverlay` usaba `pointerInput(samples, listState, onNoteOn, onNoteOff)`. Las lambdas de `MainActivity` capturan `viewModel` (tipo inestable para Compose) y, con el compilador de Compose de este proyecto (1.5.11, sin *strong skipping*), no se memorizan: se recrean en cada recomposición, y cada `noteOn`/`noteOff` cambia `playingIndices` → recompone. El bloque de gestos se cancelaba y volvía a empezar con el dedo aún apoyado, perdiendo `activeKeyForPointer`; en el siguiente evento (cualquier micro-movimiento) la misma tecla se trataba como "nueva" y **volvía a sonar**. La ventana de riesgo se abre en cada cambio de `playingIndices`: al encender la tecla y a los 250 ms, cuando la luz se apaga sola. (Deducido del código y de la semántica de Compose; no reproducido en dispositivo.)
- **Corrección:** `pointerInput(listState)` + `rememberUpdatedState` para `samples`/`onNoteOn`/`onNoteOff`. Además: si el dedo sale por arriba/abajo de las filas visibles se suelta la tecla, y un `finally` apaga las teclas si el overlay sale de la composición con dedos apoyados.

### H-05 (M) — Una nota tocada y soltada rápido podía no sonar; y el final de cada muestra se cortaba
- **Evidencia (código), parte 1:** `noteOn` metía `samplePlayer.play(...)` **dentro** del mismo job que apagaba la luz, y `noteOff` (al levantar el dedo) cancela ese job. Si la cancelación llega antes de que `Dispatchers.Default` empiece a ejecutar el bloque, `withContext` no lo ejecuta nunca. **Corrección:** el audio va en su propio job; la luz en otro.
- **Evidencia (código), parte 2:** `SamplePlayer` llamaba a `releaseTrack` (`stop()` + `release()`) inmediatamente después de escribir el último trozo. En `MODE_STREAM`, `write` vuelve cuando el trozo **entra** al buffer, no cuando **suena**: se perdía la cola de cada muestra (hasta ~2 buffers). **Corrección:** el hilo escritor espera a que `playbackHeadPosition` alcance el último frame (con tope de tiempo y salida si el track se detiene) antes de liberar.

### H-06 (M) — Eventos MIDI mutaban estado desde un hilo que no es el principal
- **Evidencia (código):** `MidiReceiver.onSend` corre en un hilo del servicio MIDI; `handleMidiNoteOn` → `noteOn` tocaba `highlightJobs` (un `HashMap` no thread-safe) y `_playingIndices` desde ahí.
- **Corrección:** `handleMidiNoteOn` salta a `viewModelScope` (hilo principal) antes de tocar nada.

### H-07 (M) — Se podían lanzar operaciones en paralelo
- **Evidencia (código):** durante una importación/exportación los botones LOAD/RENOMBRAR/EXPORT seguían activos; dos `exportTo` simultáneos se pisaban `_uiState`, y una carga durante una exportación reemplazaba `project`.
- **Corrección:** `isBusy()` en el ViewModel (garantía real) y `DwpTopBar` con `loadEnabled`/`projectActionsEnabled` (RENOMBRAR/EXPORT también se desactivan si no hay instrumento cargado: antes eran un no-op silencioso).

### H-08 (M) — Una carga fallida dejaba la app en "vacío" con el proyecto anterior invisible en memoria
- **Evidencia (código):** `loadFromZip` ponía `Importing` y, al fallar, `Error(previous = Empty)` por defecto, mientras `project` seguía siendo el proyecto anterior. **Corrección:** el error conserva como `previous` el estado previo real.

### H-09 (M) — La barra superior quedaba debajo de la barra de estado
- **Evidencia (código):** `MainActivity` usa `enableEdgeToEdge()`. `Scaffold` calcula `innerPadding.top` como la altura del `topBar` y **da por hecho que el propio `topBar` gestiona la inserción de la barra de estado** (lo hace `TopAppBar` de Material; `DwpTopBar` es un `Row` propio y no lo hacía). En un móvil con barra de estado/notch, los botones quedaban bajo la hora/iconos. (En tu captura de tablet no se nota porque no hay barra de estado visible.)
- **Corrección:** `windowInsetsPadding(WindowInsets.safeDrawing.only(Top + Horizontal))` aplicado **después** del `background`, para que el color siga cubriendo esa zona.
- **Relacionado:** `enableEdgeToEdge()` por defecto elige el color de los iconos de las barras del sistema según el tema del **teléfono**; con el móvil en modo claro salían oscuros sobre el fondo morado oscuro. Ahora `SystemBarStyle.dark(TRANSPARENT)` (la app tiene un único tema oscuro fijo).

### H-10 (A) — Riesgo real de `OutOfMemoryError` al cargar/exportar un instrumento del tamaño del de referencia (~144 MB)
- **Evidencia (código):**
  - Carga: `readBytes()` del zip completo **y** luego todo su contenido descomprimido → el pico era zip + contenido.
  - Exportación: el zip se construía en un `ByteArrayOutputStream` (crece por duplicación, hasta ~2× el tamaño final) y luego `toByteArray()` copiaba todo otra vez → **más de 400 MB de pico** sobre los ~150 MB de audio ya en memoria.
  - Manifest sin `largeHeap` y ningún `catch` de `OutOfMemoryError` (no es `Exception`) → cierre de la app.
- **Corrección:**
  - `ZipProjectLoader.load(InputStream)`: se descomprime directamente desde el archivo. `load(ByteArray)` se mantiene como envoltorio, misma API y mismos límites anti zip-bomb.
  - `ZipProjectExporter.prepare(...)` (valida y resincroniza; **todo lo que puede fallar por contenido, antes de crear el archivo**) + `PreparedZipExport.writeTo(OutputStream)` (vuelca muestra a muestra con búfer de 64 KB). `export(...) : ByteArray` se mantiene con su firma exacta.
  - `android:largeHeap="true"` (la app mantiene todo el audio en memoria por diseño).
  - `catch (OutOfMemoryError)` con mensaje claro en carga y ambas exportaciones. La exportación muestra ahora la etapa "Escribiendo archivo" mientras vuelca (antes la barra se quedaba en 100 % durante la compresión).
- **Tests:** `ZipProjectStreamingTest` (6): `load(InputStream)` ≡ `load(ByteArray)`, `prepare + writeTo` ≡ `export`, ciclo completo con las 48 muestras, `writeTo` cierra el destino, `prepare` valida antes de escribir, progreso real 1..48.

### H-11 (B) — Texto del estado vacío prometía `.dwp` suelto
- Era `RISK-03` de `Doc/12` (abierto). `ZipProjectLoader` solo acepta `.zip`. Texto corregido: "Formato soportado: .zip (un .dwp + sus .wav)".

### H-12 (B) — Zips de algunos proveedores salían atenuados en el selector
- El selector solo pedía `application/zip` y `application/octet-stream`; muchos proveedores etiquetan los `.zip` como `application/x-zip-compressed`. Añadido.

---

## 3. Qué NO se tocó (y por qué)
Sin cambios en `DwpEngine`, `DwpBlock`, `DwpDocument`, `DwpTokenizer`, `DwpVersionProfile`, `WavDecoder`, `PcmConverter`, `MidiMessageParser`, `domain/flac/*` ni `domain/dwp/monolithic/*`. Los tests existentes siguen siendo válidos: las firmas públicas que usan (`export`, `load(ByteArray)` con y sin límites explícitos) se conservaron. Sí se tocó la capa de I/O (`ZipProjectLoader`, `ZipProjectExporter`), añadiendo API sin retirar ninguna.

## 4. Hallazgos que quedan abiertos (documentados, no corregidos)

| ID | Qué | Por qué no se tocó | Recomendación |
|---|---|---|---|
| A-01 | **Exportar `.dwp` autocontenido falla con audio `FLOAT_32` / `PCM_32`** (`PcmNormalizer` los rechaza a propósito). Si tus WAV vienen de FL Studio en float32, esa opción mostrará un error explícito. | Decisión de diseño documentada en `Doc/23` §15 / `Doc/28`: no hay evidencia del formato DirectWave real para esas profundidades. | Probar el ZIP clásico (recomendado en el diálogo). Para habilitar monolítico con float32 hace falta un `.dwp` monolítico real de referencia. |
| A-02 | `DwpEngine.renameInstrument` reemplaza el texto viejo **en cualquier parte** del nombre, incluida la parte de la nota. Con un nombre base muy corto (`"A"`, `"C"`, `"1"`), `A_A3_127` → `B_B3_127`. | Es Core (no se toca sin causa demostrable; el caso real, "Instrument", no lo dispara). | Reemplazo anclado al prefijo `<base>_<nota>_<vel>`. |
| A-03 | No se detecta el desenchufado de un teclado MIDI (`midiConnected` sigue `true`). | Requiere `MidiManager.DeviceCallback`; cambio de comportamiento no pedido. | Registrarlo en `MidiInputManager`. |
| A-04 | Cada nota decodifica el WAV y lo convierte a 16-bit en el momento (latencia audible en muestras de ~3 MB). | Optimización, no bug; necesita política de caché/memoria. | Caché LRU de PCM16 por índice. |
| A-05 | El ZIP comprime los WAV con Deflate (poco rentable en audio y costoso en CPU móvil). | No afecta corrección. | Evaluar `STORED` tras probar la compatibilidad en FL Studio Mobile. |
| A-06 | `DwpCreatorApplication` (pantalla de diagnóstico de crash) sigue siendo andamiaje "temporal". | Útil mientras se prueba en dispositivo. | Retirar antes de una versión pública. |

## 5. Archivos modificados / nuevos

**Modificados:** `AndroidManifest.xml`, `app/build.gradle.kts` (versión), `MainActivity.kt`, `audio/SamplePlayer.kt`, `viewmodel/DwpCreatorViewModel.kt`, `domain/io/ZipProjectLoader.kt`, `domain/io/ZipProjectExporter.kt`, `ui/screens/MainScreen.kt`, `ui/components/{PianoKeyBadge,SampleRow,DwpTopBar,RenameAllDialog,LoadEmptyState}.kt`, `ui/theme/Color.kt`.
**Nuevos:** `domain/dwp/InstrumentNameValidator.kt`, `ui/state/SampleOctave.kt`, y los tests `InstrumentNameValidatorTest.kt` (9), `ui/state/SampleOctaveTest.kt` (7), `domain/io/ZipProjectStreamingTest.kt` (6).

**Conteo de tests presentes:** 215 → **237** (`@Test` en 18 archivos de test; el «16 archivos» de documentos anteriores no coincidía con el árbol real, que tenía 15 antes de esta pasada). Son tests *presentes*, no *pasando*: ninguno de esta pasada se ha ejecutado (ver la advertencia inicial).

## 6. Verificación realizada en esta pasada
- Lectura completa del código de producción de app, UI, ViewModel, audio, MIDI, I/O, motor DWP, WAV, PCM y monolítico; lectura de `Doc/12` y `Doc/18` para no reabrir lo ya cerrado.
- Balance de llaves/paréntesis/corchetes (ignorando strings y comentarios) en los 17 archivos Kotlin tocados o creados: correcto.
- Comprobación cruzada de firmas: llamadas existentes en tests (`export(project, name) { … }`, `load(bytes, maxEntries = …)`) siguen resolviendo contra las sobrecargas.
- **No realizado:** compilación, ejecución de tests, prueba en dispositivo.

## 7. Prueba en dispositivo recomendada (checklist)
1. **Teclas:** todas las teclas blancas/negras sin marco de color; al tocar una, se ilumina con un único violeta igual en todas las octavas.
2. **Fila `C3`:** mismo color que `C#3`…`B3`; barra inferior "**4 octaves**".
3. **Toque largo/deslizar** sobre las teclas: no debe re-disparar la misma nota al mantener el dedo; el glissando sigue funcionando.
4. **Tocar y soltar muy rápido** repetidamente: debe sonar cada vez; el final de la muestra ya no se corta.
5. **Renombrar** con `Acústico`: el campo se marca en rojo y el botón queda desactivado. Con `Didas 2`: funciona y se puede exportar y recargar.
6. **Exportar ZIP** con el instrumento de 48 muestras: barra → "Escribiendo archivo" → archivo generado; recargarlo debe dar 48 muestras.
7. **Móvil con barra de estado:** botones de arriba por debajo de la hora/batería, e iconos de sistema legibles aunque el móvil esté en modo claro.
8. Durante una exportación, LOAD/RENOMBRAR/EXPORT están atenuados.

---

## 8. Adenda — Motor de audio de baja latencia ("teclado profesional")

**Fecha:** 2026-09-24 (misma jornada, tras feedback directo del usuario probando el APK: "lag/delay al presionar una tecla o acordes").

### 8.1 Causa raíz (por qué había retardo)

Antes de esta adenda, **cada pulsación** -- incluida cada nota de un acorde -- repetía, desde cero, tres operaciones costosas que son idénticas cada vez que se toca la misma tecla:

1. `WavDecoder.decode()`: reparsear todos los chunks RIFF y copiar el chunk `data` completo (varios MB por muestra).
2. `PcmConverter.*ToInt16()`: recorrer sample a sample para convertir a 16-bit (peor caso: float32 estéreo, exactamente lo que suelen ser las muestras de DirectWave).
3. `AudioTrack.Builder().build()`: reservar un buffer nativo y abrir una ruta real en el HAL de audio -- una operación de construcción no trivial, repetida y destruida (`stop()`+`release()`) en cada nota.

Además, ninguna voz pedía el modo de baja latencia de la plataforma: todo el audio iba por la ruta normal del mezclador de Android (típicamente 40-100ms+ de latencia de por sí), no por la ruta rápida que sí existe para apps de audio/instrumentos.

### 8.2 Corrección

- **`domain/audio/DecodedSampleCache.kt`** (nuevo, Kotlin/JVM puro, testeable): caché LRU acotada en bytes (48 MB por defecto) de PCM de 16 bits ya decodificado y convertido, por índice de muestra. La primera vez que se toca una tecla se decodifica igual que antes; la segunda vez (y siguientes) el resultado ya está listo.
- **`audio/AudioVoicePool.kt`** (nuevo): pool de voces `AudioTrack` reutilizables entre notas, agrupadas por `(sampleRateHz, channelCount)`. Reciclar una voz (`pause()`+`flush()`) en vez de destruirla y reconstruirla elimina el coste de `Builder().build()` de la mayoría de las pulsaciones. Intenta `PERFORMANCE_MODE_LOW_LATENCY` (API 26, el propio `minSdk` de la app) con reintento automático en modo normal si el dispositivo no lo concede -- **no confirmado con hardware real en este entorno de trabajo** si el modo rápido se concede efectivamente para el sample rate del instrumento del usuario.
- **`audio/SamplePlayer.kt`** (reescrito): usa ambas piezas; el hilo que escribe cada nota sale de un `ExecutorService` de tamaño fijo reutilizado entre notas, no de un `Thread` nuevo por pulsación.
- **`DwpCreatorViewModel.loadFromZip`**: tras publicar el proyecto cargado, lanza un precalentamiento de "mejor esfuerzo" (`SamplePlayer.prewarm`) en segundo plano, sin bloquear la UI, para que las primeras teclas que el usuario probablemente toque ya estén decodificadas antes de la primera pulsación real.

### 8.3 Qué mejora esto y qué no

- **Mejora garantizada, verificable por lectura de código:** la segunda vez (y siguientes) que se toca la misma tecla, no hay ni reparseo de WAV, ni conversión PCM, ni construcción de `AudioTrack` en el camino -- solo `write()` sobre una voz ya lista. Un acorde repetido varias veces se siente instantáneo a partir de la segunda vez.
- **Mejora probable pero no confirmada en hardware real:** el modo de baja latencia reduce la latencia de la RUTA de audio en sí (no solo el trabajo de la app) en dispositivos que lo soportan para el sample rate de las muestras. Sin dispositivo Android disponible en este entorno de trabajo, esto queda como 🟡 esperado por documentación de la plataforma, no verificado.
- **Primera pulsación de una tecla nunca precalentada:** sigue pagando el coste de decodificación completo (igual que toda la app antes de este cambio), pero ya no reconstruye el `AudioTrack` si el pool tiene una voz libre de ese formato.

### 8.4 Tests nuevos

`domain/audio/DecodedSampleCacheTest.kt` (7 tests): acierto de caché idéntico a una decodificación independiente, conversión float32→16-bit verificada contra `PcmConverter` directamente, formatos no soportados devuelven `null` sin lanzar, expulsión LRU (se descarta la entrada usada hace más tiempo, nunca la recién pedida), `isFull`/`clear`. `AudioVoicePool` y el `SamplePlayer` reescrito siguen sin test unitario por la misma razón que siempre (`android.media.AudioTrack` no instanciable en JVM puro) -- se listan en el checklist de prueba en dispositivo (§8.5).

**Conteo de tests presentes: 237 → 244.**

### 8.5 Checklist de prueba en dispositivo (añadir al de §7)

9. Tocar la misma tecla dos veces seguidas: la segunda debe sentirse claramente más rápida que la primera (caché caliente).
10. Tocar un acorde de 3-4 notas repetidamente: debe sentirse instantáneo a partir de la segunda vez, sin "chasquido" de arranque en cada repetición.
11. Nada más cargar el instrumento, tocar una tecla de las primeras de la lista sin esperar: debería sonar ya con la mejora de la caché si el precalentamiento en segundo plano llegó a tiempo (no garantizado, es "mejor esfuerzo").
12. Sesión larga (varios minutos tocando notas y acordes intercalados): sin fugas de voces ni caída de sonido progresiva (verifica que el pool de voces se recicla correctamente y no se agota).

---

## 9. Corrección de compilación real (CI run #33, `compileDebugKotlin` FAILED)

**Fecha:** 2026-09-24 (mismo día, tras recibir capturas del CI real ejecutándose por primera vez sobre el motor de audio de baja latencia de §8).

**Esto es exactamente el tipo de fallo que este entorno de trabajo no puede detectar por sí solo** (sin compilador Kotlin/Gradle -- ver la advertencia del §0/§6): un símbolo que no existe. `audio/SamplePlayer.kt` (§8) usaba `AudioTrack.ENCODING_PCM_16BIT` para volver a calcular el tamaño de buffer antes de recomendar el drenaje de una voz -- pero esa constante vive en `android.media.AudioFormat`, no en `AudioTrack`. Kotlin lo reportó de la única forma en que puede reportarlo: `compileDebugKotlin FAILED` / `Compilation error`, sin detalle adicional visible en el log resumido de GitHub Actions.

**Corrección:** se importa `android.media.AudioFormat` en `SamplePlayer.kt` y el recálculo del tamaño de buffer para el drenaje usa `AudioFormat.CHANNEL_OUT_MONO`/`STEREO` y `AudioFormat.ENCODING_PCM_16BIT` a partir del formato ya conocido de la muestra (`cached.sampleRateHz`/`cached.channelCount`), en vez de leerlo de propiedades de `track`. Mismo resultado numérico, sin el símbolo inexistente.

Se releyeron con atención, símbolo por símbolo de la API de Android, los tres archivos nuevos/reescritos de §8 (`SamplePlayer.kt`, `AudioVoicePool.kt`, `DecodedSampleCache.kt`) buscando el mismo tipo de error -- no se encontró ningún otro. Sin cambios de comportamiento respecto a lo descrito en §8; es exclusivamente la corrección del único error de compilación real detectado hasta ahora en todo este ciclo de trabajo.

---

## 10. Auditoría integral del proyecto completo (post-fix de compilación)

**Fecha:** 2026-09-24 (mismo día). Alcance pedido explícitamente: revisar **todo** el proyecto -- errores, bugs, código muerto/basura, cosas mal implementadas -- y anticipar fallos futuros, no solo reaccionar a los ya reportados.

### 10.1 Endurecimiento real anticipado: voces "zombi" en el pool de audio

Al revisar en frío el motor de §8/§9 buscando el *siguiente* bug antes de que ocurra (no solo el que ya causó el fallo de CI), encontré una laguna real: `AudioVoicePool.acquire()` descarta una voz inactiva si `track.state != STATE_INITIALIZED`, pero **ese estado solo refleja si el `AudioTrack` se construyó bien**, nunca si sigue funcionando. Una voz cuyo `play()` o `write()` empieza a fallar en tiempo real (pérdida de foco de audio, un fallo puntual del HAL, lo que sea) seguiría reportando `STATE_INITIALIZED` para siempre -- y antes de este cambio, `SamplePlayer` la reciclaba igualmente tras cada intento fallido. En una sesión de uso larga (el punto 12 del checklist de prueba, §8.5), eso habría producido una "voz zombi": un hueco de los `MAX_CONCURRENT_VOICES` ocupado permanentemente por una voz que nunca vuelve a sonar, sin ningún error visible.

**Corrección:**
- `AudioVoicePool` ahora distingue **reciclar** (`recycle`, voz que terminó de escribir con normalidad) de **descartar** (`discard`, voz que demostró estar rota: se libera de verdad y su hueco vuelve a estar disponible para construir una voz nueva).
- `SamplePlayer.play()` cubre `setVolume()`+`play()` bajo el mismo `try/catch`: si cualquiera de los dos falla, la voz se descarta en vez de reciclarse, **y la excepción nunca sale de esa función** -- antes, un fallo ahí se habría colado como excepción no capturada en la corrutina de `noteOn`, y sin un manejador instalado, eso cierra la app entera por una sola nota fallida.
- En el hilo escritor, una voz solo se considera "sana" si de verdad llegó a escribir al menos un byte (`offset > 0`) antes de terminar; si no, se descarta.

### 10.2 Revisión sistemática del resto del proyecto

Se releyeron por completo, en busca de código muerto, imports no usados, fugas de recursos y errores de tipos/símbolos: `DwpCreatorViewModel.kt`, `MainScreen.kt` (incluido el overlay de gestos completo), `MainActivity.kt`, `DwpTopBar.kt`, `RenameAllDialog.kt`, `SampleRow.kt`, `Color.kt`, `MidiInputManager.kt`, `ZipProjectLoader.kt`/`ZipProjectExporter.kt`, `LoadedProject.kt`, `SampleInfo.kt`, y las firmas reales de `WavDecoder`/`PcmConverter`/`DwpEngine` contra cada uso nuevo de esta sesión (`DecodedSampleCache`, los tests nuevos). Con un análisis automatizado (miembros `private` declarados y nunca referenciados, imports sin uso detectable) sobre todo `app/src/main/java`: **ningún miembro privado muerto, ningún import realmente sin usar** (los pocos que el detector marcó -- `getValue`/`setValue` en archivos con `by remember { mutableStateOf(...) }` -- son necesarios para el operador `by`, falsos positivos del propio detector).

**Hallazgo verificado y descartado como riesgo:** `SampleRow.kt` tiene su propio `isOctaveStart` (fondo/negrita de la primera nota de cada octava), independiente del `SampleOctave` de §2/H-01 -- se comprobó que **ya estaba bien** desde el principio (`note.startsWith("C")`, basado en la nota, nunca en `lowKey`), así que el bug de "octava 0 fantasma" corregido en H-01 no tenía una segunda instancia oculta en otro sitio.

**Núcleo (`domain/dwp`, `domain/flac`, `domain/dwp/monolithic`, ~3600 líneas):** sin cambios en esta pasada -- ya cuenta con 130+ tests propios acumulados a lo largo de auditorías previas (`Doc/12`, `Doc/18`) y ninguna de las clases de ese núcleo resultó tocada por el trabajo de esta sesión (motor de audio + UI de teclas). Todas las clases de `domain/dwp/monolithic/*` se confirmaron efectivamente usadas (sin builders huérfanos).

### 10.3 Qué NO se puede garantizar todavía

Sigue siendo cierto lo dicho en §0/§6/§9: este entorno de trabajo no tiene compilador Kotlin, Gradle ni Android SDK. Todo lo anterior es una revisión manual, símbolo por símbolo, contra las firmas reales del propio proyecto -- el mismo método que falló en detectar `AudioTrack.ENCODING_PCM_16BIT` en la pasada anterior. Por eso, además de corregirlo, esta vez se verificó explícitamente cada símbolo de Android nuevo usado (`AudioTrack.*`, `AudioFormat.*`, `AudioAttributes.*`) contra la documentación de la API antes de darlo por bueno. Aun así, **el resultado real del próximo CI (build #34+) sigue siendo la única confirmación definitiva.**

**Conteo de tests: sin cambios (244)** -- los ajustes de §9/§10 son correcciones de robustez en tiempo de ejecución dentro de `SamplePlayer`/`AudioVoicePool` (ambos sin test unitario posible en JVM puro, ver §8.4), no lógica nueva testeable de forma aislada.

---

## 11. Rediseño "premium" de la etiqueta de cada tecla

**Fecha:** 2026-09-25. Pedido explícito del usuario, con capturas de referencia (una tecla de un teclado real, y el propio strip de la app con flechas dibujadas a mano): las etiquetas deben verse más profesionales, reposicionarse (mismo color de texto, sin cambiarlo -- solo orden/posición), y el Do de cada octava debe destacar más (más grande, más grueso) y llevar una marca de color en el pie de la tecla, como en un teclado físico real.

### 11.1 Cambios

- **Posición de la etiqueta:** de centrada-abajo a **abajo-a-la-izquierda** de la tecla (antes `Alignment.BottomCenter`, ahora `Alignment.BottomStart` con margen), siguiendo la referencia fotográfica de un teclado real y la flecha dibujada por el usuario sobre la columna de teclas de la app.
- **El Do de cada octava (`SampleInfo.isOctaveStart`) se destaca:** texto más grande (13sp vs. el tamaño normal de la lista) y en negrita (`FontWeight.ExtraBold` vs. `Medium` en el resto), para que el ojo encuentre el inicio de cada octava de un vistazo en la columna de teclas -- no solo en la lista de nombres, donde ya pasaba.
- **Marca de octava en el pie de la tecla:** una franja de color a todo lo ancho, pegada al borde inferior, **solo en la tecla Do** -- exactamente como en la fotografía de referencia del usuario (la marca turquesa bajo "C3"). Reutiliza el mismo color de octava que ya pinta el número/nombre de esa fila en la lista (`OctaveColors`, calculado por `SampleOctave`), así que la tecla y su fila comparten un único código de color coherente en toda la pantalla -- no se inventó una paleta nueva.
- **Acabado "premium":** cada tecla pasa de un color plano a un degradado vertical (más clara arriba, más oscura abajo) más una veta de brillo cerca del borde superior, y solo se redondea por abajo (el borde superior queda recto) -- el lenguaje visual de la cara frontal de una tecla física vista de frente/en ángulo, en vez de una etiqueta plana. El color base de cada tecla (blanco/negro) y el color de su texto **no cambiaron** -- solo se les dio volumen con el degradado, tal como pidió el usuario ("el color de las etiquetas que no cambien, solo que su orden").

### 11.2 Lo que se descartó conscientemente, y por qué

El pedido menciona un "ángulo lateral" tipo teclado fotografiado en 3D. La app no dibuja un teclado horizontal completo: cada muestra es una fila independiente de una lista vertical, con una sola tecla a la derecha -- esa es la arquitectura de toda la pantalla (`MainScreen`/`SampleRow`), de la que depende también el overlay de gestos multi-touch (acordes y glissando, `Doc/29` §1-2). Reconstruir eso como un teclado horizontal real, en perspectiva 3D, sería un rediseño estructural completo de la pantalla -- muy por encima de "mejorar el aspecto de la tecla" y con riesgo real de romper el overlay de gestos ya estabilizado. En su lugar se entregó la interpretación profesional más cercana dentro de la arquitectura actual: degradado + brillo + forma recta-arriba/redondeada-abajo, que da sensación de volumen y de "cara frontal de una tecla física" sin rehacer la pantalla entera. Si lo que se busca es literalmente un teclado horizontal fotorrealista en 3D, es un pedido aparte y mucho mayor que conviene tratar como su propio proyecto de diseño, no como un ajuste dentro de este.

### 11.3 Archivos tocados

`ui/theme/Color.kt` (colores de degradado nuevos, sin tocar los existentes), `ui/components/PianoKeyBadge.kt` (reescrito), `ui/components/SampleRow.kt` (pasa `isOctaveStart`/`octaveAccentColor` al badge). `PianoKeyWidth`/`PianoKeyHeight` **no cambiaron** -- el overlay de gestos de `MainScreen` sigue calculando la misma franja.

**Verificación en este entorno:** revisión símbolo por símbolo de cada API de Compose nueva usada (`Brush.verticalGradient`/`horizontalGradient`, `Color.isSpecified`, `RoundedCornerShape` con radios independientes por esquina, `TextStyle.copy`) contra la documentación real, y verificación de balance de llaves/paréntesis en los tres archivos tocados y en todo el árbol de código fuente (dos falsos positivos detectados y descartados: nombres de test entre backticks con apóstrofes, en archivos no tocados esta sesión). Sin compilador disponible, la confirmación definitiva sigue siendo el próximo CI.

**Conteo de tests: sin cambios (244)** -- cambio puramente visual, sin lógica nueva testeable en JVM.

### 11.4 Prueba en dispositivo recomendada

13. Las etiquetas de nota se leen abajo-a-la-izquierda de cada tecla, no centradas.
14. Cada tecla Do se ve claramente más grande/gruesa que el resto, con una franja de color en su borde inferior -- del mismo color que el número/nombre de esa fila en la lista.
15. Las teclas tienen un ligero degradado de color (más claras arriba) en vez de un color plano.
