# 52 — Auditoría completa del código real y correcciones

## Alcance
Revisión estática de todo el proyecto: configuración de build (raíz, `:app`,
`:baselineprofile`), workflows de CI, `MainActivity`/`Application`, ViewModel,
capa de audio (`SamplePlayer`, `AudioVoicePool`, `DecodedSampleCache`), MIDI,
E/S de archivos (zip), y la capa de UI (`MainScreen`, mezclador, diálogos,
panel de efectos). La capa `domain/` (DWP, FLAC, WAV) tiene 19 archivos de
prueba que CI ejecuta en cada build; se revisó su E/S y límites, no se reescribió.

**Límite de esta auditoría:** este entorno no tiene SDK de Android ni red, así
que **nada de lo de abajo se pudo compilar ni ejecutar aquí**. Se verificó
sintaxis YAML de los workflows y balance de llaves/paréntesis de los archivos
Kotlin editados. La verificación real es la próxima corrida de `Build APK` y
una prueba en el dispositivo.

## Errores propios corregidos
1. **Stack traces ilegibles en release.** Desde el Doc/45 el release usa R8
   con ofuscación; `CrashDiagnosticScreen` (que muestra el stack trace en
   pantalla para diagnosticar por captura) habría mostrado `a.b.c()`.
   `proguard-rules.pro`: `-dontobfuscate` (se conserva shrink y optimización,
   que es lo que da rendimiento; solo se deja de renombrar).
2. **Doc/47 era inexacto.** Decía que `build.yml` no dependía de
   `:baselineprofile`, pero `gradle assembleRelease` a nivel raíz construye
   todos los módulos. `build.yml` ahora usa `:app:testDebugUnitTest` y
   `:app:assembleRelease`.
3. **README desactualizado:** describía el artefacto `DwpCreator-debug-apk`.
   Corregido a `DwpCreator-release-apk` con la aclaración de la firma de debug.
4. **Dependencia sin uso:** `espresso-core` en `:baselineprofile` (no se importa
   en ningún sitio) arrastraba `hamcrest-library`, una de las descargas
   rechazadas con 429 en el primer run. Eliminada.

## Errores del código de la app corregidos
5. **Nota "pegada" en muestras frías (`SamplePlayer.play`).** La voz se
   registraba DESPUÉS de decodificar la muestra; soltar la tecla durante esa
   decodificación hacía que `stopNote` no encontrara nada y la muestra sonara
   entera. Ahora se registra primero y `startVoice` devuelve si la voz quedó
   entregada al hilo escritor; si no, se retira el registro. Verificado que
   `stopNote` solo levanta un `AtomicBoolean` (no bloquea el hilo principal).
6. **Conexión MIDI "fantasma" (`MidiInputManager`).** `openDevice` es asíncrono:
   desconectar o cambiar de dispositivo con una apertura pendiente terminaba
   conectando igual o dejaba un dispositivo abierto sin cerrar. Se añadió una
   generación de conexión; los callbacks tardíos cierran el dispositivo y salen.
7. **Desenchufar el teclado no se notaba** (la app seguía en "conectado").
   Se registra un `MidiManager.DeviceCallback` (solo mientras hay conexión) que
   desconecta cuando el dispositivo activo desaparece.
8. **Notas MIDI retenidas al perder la conexión.** El ViewModel ahora suelta
   las teclas físicas apretadas en cualquier `onConnectionChanged(false)`, no
   solo en `disconnectMidi()`. `onCleared` cierra también una conexión aún
   abriéndose (antes solo si `_midiConnected` ya era true).
9. **Carrera en `MidiMessageParser`:** `reset()` (hilo principal) y `parse()`
   (hilo del servicio MIDI) comparten estado; ambos `@Synchronized`.
10. **Callbacks capturados una sola vez en Pan/Volumen:** `pointerInput(Unit)`
    nunca se reinicia y llamaba al primer `onPanChange`/`onVolumeChange`
    recibido. Ahora `rememberUpdatedState` (mismo patrón que el overlay del piano).

## Mejoras de calidad
11. `collectAsState()` -> `collectAsStateWithLifecycle()`: no se recolecta ni se
    recompone con la app en segundo plano.
12. Iconos `VolumeUp`/`VolumeOff` -> `Icons.AutoMirrored.Filled.*` (eran los
    avisos de deprecación del log de CI).
13. `build.yml`: `concurrency` (cancela compilaciones superadas) y
    `timeout-minutes`; `baseline-profile.yml`: `timeout-minutes: 45`.
14. Versión: `versionCode` 12, `versionName` `0.7.0-release-r8-k2`.

## Revisado y sin cambios (correcto)
- Cierre de streams: `ZipProjectExporter.writeTo` y `ZipProjectLoader.load`
  usan `use`. El exportador valida nombres contra zip-slip y duplicados.
- `SamplePlayer`: trozos de 10 ms, fade-out de 12 ms, pan de potencia
  constante, `stopNote` no bloqueante, pool de voces con descarte de voces rotas.
- `DecodedSampleCache`: LRU acotada en bytes, decodificación fuera del lock.
- ViewModel: guarda `isBusy`, manejo de `OutOfMemoryError`, restauración del
  estado previo si falla una carga.
- `MainScreen`: multitouch por puntero, `finally` que apaga teclas al salir de
  la composición.
- Sin `!!`, sin `GlobalScope`, sin `runBlocking`, sin TODO pendientes.

## Observaciones (no cambiadas a propósito)
- Los rieles de Pan/Volumen solo reaccionan al arrastre, no a un toque suelto
  (`detectHorizontalDragGestures` exige superar el umbral de movimiento). Es un
  cambio de comportamiento; se deja a decisión del usuario.
- `SnackbarHost` en `MainScreen` no se usa (los errores van en diálogo).
- `CrashDiagnosticScreen` sigue marcada como andamiaje temporal de desarrollo.
- El test `BaselineProfileGenerator` sigue sin producir un perfil verificable
  (Doc/50-51); el workflow manual imprime ahora el XML del test para diagnosticarlo.

## Pendiente de verificación real
1. Push a `main` y confirmar que `Build APK` compila y pasa los tests.
2. En el dispositivo: tocar y soltar rápido una tecla recién cargada, conectar y
   desenchufar un teclado USB, arrastrar Pan/Volumen, y provocar un error para
   ver que la pantalla de crash muestra nombres reales.

## Adición (misma sesión): toque directo en Pan/Volumen
Antes, `MixerPanControl`/`MixerVolumeControl` solo reaccionaban a un arrastre
(`detectHorizontalDragGestures` exige superar el umbral de movimiento del
sistema para empezar; un toque corto sin mover el dedo no hacía nada). Se
reemplazó por `detectTapAndHorizontalDrag`, una función compartida que
reacciona ya en el primer toque (`awaitFirstDown`) y sigue el dedo mientras
se mantenga presionado. Mismo comportamiento de arrastre de antes, más la
capacidad de tocar cualquier punto del riel para saltar directo ahí.
