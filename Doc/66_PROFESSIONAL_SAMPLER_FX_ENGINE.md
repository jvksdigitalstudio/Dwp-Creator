# 66 — Motor de mezcla profesional + bus de efectos del sampler

**Estado:** compila en CI real; 283/285 pruebas pasaron en la primera ejecución y las 2 restantes (un único defecto del limitador) están corregidas — pendiente de re-ejecución en CI y de prueba en dispositivo (ver §9).
**Reemplaza:** el motor "un `AudioTrack` por nota" (Doc/59–65) y el panel provisional de Doc/31/38.

## 1. Problema de raíz
Reverb, delay y chorus actúan sobre la **suma** de todo lo que suena. Con una pista de
hardware independiente por voz (`AudioVoicePool` + un hilo por voz) no existe un punto común donde
insertarlos: el panel SAMPLER sólo podía ser una maqueta. No se resolvía con un parche;
hacía falta cambiar la arquitectura de salida.

## 2. Arquitectura
```
 UI (FxKnob, módulos)  ──►  DwpCreatorViewModel.fxState (StateFlow, persistido)
                                     │ SamplePlayer.setFxState
 SamplePlayer.play/stopNote ──► SamplerEngine ◄── AudioOutputDriver (hilo de audio, AudioTrack float)
   (tokens VoiceToken)         voces → MasterFxChain → limitador → salida estéreo float
```
| Capa | Archivo | Android | Probado en JVM |
|---|---|---|---|
| DSP | `audio/dsp/*` | no | sí |
| Mezcla de voces | `audio/SamplerEngine.kt` | no | sí |
| Salida de hardware | `audio/AudioOutputDriver.kt` | sí | — |
| Fachada | `audio/SamplePlayer.kt` | sí | — |
| Persistencia | `audio/SamplerFxPreferences.kt` | sí | — |

## 3. Cadena de señal (bus maestro)
`voces → DRIVE A → CHORUS → DELAY → REVERB → DRIVE B → VOLUME → limitador`
Los efectos son *sends* (la señal seca no se toca; se le suma el efecto × Mix). Todos arrancan
**apagados**: con el estado por defecto el sonido es idéntico al anterior.

| Módulo | Parámetros | Implementación |
|---|---|---|
| MASTER | Poly/Mono, Volume (−48…+6 dB) | Mono libera la nota anterior con fundido de 6 ms |
| FX DRIVE A | Amount | `tanh` simétrico, bypass exacto a 0 %, compensación de nivel |
| FX DRIVE B | Amount | `tanh` asimétrico (armónicos pares) + anti-DC, post-efectos |
| FX DELAY | Time, Feedback, Low cut, High cut, Mix, Normal/Bounce | realimentación **filtrada**; ping-pong; tiempo con deslizamiento |
| FX REVERB | Room, Damp, Diffusion, Decay (RT60), Mix | FDN de 8 líneas, Hadamard ortonormal, RT60 exacto por línea |
| FX CHORUS | Delay, Depth, Rate, Feedback, Mix | 2 líneas moduladas, LFO en cuadratura L/R |

Valores por defecto de Reverb y Chorus = los que muestra DirectWave (capturas de referencia).

## 4. Decisiones de ingeniería
- **Un solo stream float estéreo** (`ENCODING_PCM_FLOAT`, `LOW_LATENCY`) a la frecuencia y ráfaga **nativas**
  del dispositivo; búfer adaptativo según *underruns*; reconstrucción ante `ERROR_DEAD_OBJECT`.
- **Hilo de audio sin locks ni asignaciones** en el camino de render: órdenes por `ConcurrentLinkedQueue`,
  estado de efectos por referencia `@Volatile` a un objeto inmutable.
- **Liberación por token**, no por mensaje: soltar la tecla antes de que la muestra termine de decodificarse
  no deja notas pegadas. Nota mínima de 15 ms (sin "tic" mudo en toques ultrarrápidos).
- **Anti-clic por muestra:** ataque 3 ms, liberación 15 ms, robo de voz 6 ms, fundido de 2 ms al final natural.
- **Polifonía:** 32 voces vivas + 8 extra para las que se apagan; la más antigua se libera, nunca se corta en seco.
- **Remuestreo** del archivo a la frecuencia del dispositivo con interpolación cúbica (Catmull-Rom).
- **Parámetros suavizados** (sin ruido "zipper"); **primera configuración sin deslizamiento**.
- **Bypass con cola controlada:** módulo apagado → Mix a 0 en ~30 ms, luego se vacía y deja de consumir CPU.
- **Denormales** purgados en todos los bucles de realimentación (rendimiento en ARM).
- **Suspensión por inactividad:** tras 15 s de silencio total el stream se pausa y el hilo duerme; la
  siguiente nota lo despierta (patrón "publicar bandera → comprobar cola": sin ventana de nota perdida).
- **Limitador de picos** (techo 0,97) siempre al final: la suma de voces + drive + colas nunca recorta en el HAL.
- **UI:** `FxKnob` (arrastre relativo, doble toque = valor de fábrica, `setProgress` para TalkBack);
  el panel recoge `StateFlow` por sí mismo → mover un knob recompone **sólo el panel**.
- **Fuente única de verdad:** `FxParam`/`FxToggle` alimentan DSP (rangos), UI (mapeo/texto) y persistencia.

## 5. Alcance — qué NO se implementó y por qué
Los módulos de DirectWave **Glide, LFO 1/2, Filter 1/2, Env 1/2, Mod Matrix, Ringmod/Decimator/Quantizer/Phaser**
no se incluyen: el instrumento reproduce cada muestra a su altura original y no existe aún un motor de
filtros/envolventes/modulación por voz que los haga funcionar. Mostrar knobs que no hacen nada sería una maqueta.
Son la **Fase 2** natural (filtro + envolvente por voz → matriz de modulación).

## 6. Verificación realizada
- **Simulación numérica** (Python) de la reverb FDN con las mismas constantes: RT60 = 1 s → caída medida
  **64,6 dB en 1 s** (esperado ~60), cola estable (sin divergencia), nivel húmedo a Mix 100 % = −3,6 dB vs seco.
- Escaneo estático de 99 archivos Kotlin: llaves/paréntesis balanceados, símbolos resueltos, sin imports sin usar.
- Contraste manual con la API real del proyecto (`DecodedSampleCache`, colores del tema, Kotlin 2.0.21).

## 7. Auditoría — hallazgos corregidos
| # | Hallazgo | Corrección |
|---|---|---|
| 1 | Carrera en la suspensión: nota encolada justo antes de pausar quedaba sin despertar | Patrón bandera→cola + `Semaphore` + `hasPendingCommands()` |
| 2 | `track.bufferSizeInFrames = x` no compila (setter devuelve `int`) | Llamada explícita `setBufferSizeInFrames` |
| 3 | `FlowRow(itemVerticalAlignment)` no existe en foundation 1.6 | Retirado |
| 4 | Efectos "deslizaban" desde sus defaults en la primera configuración | *Snap* en el primer `setParams` |
| 5 | Modo Mono leía estado desfasado un bloque | Lee `fxState` vigente |
| 6 | Decaimiento del vúmetro dependía del tamaño de trozo | Semivida fija en tiempo real |
| 7 | Asignación `Pair` en el hilo de audio (contradecía el contrato) | Cálculo en línea |
| 8 | Todo `fxState` subía por `MainActivity→MainScreen`: recomposición total por fotograma de arrastre | `SamplerFxController` con `StateFlow`; el panel lo recoge |
| 9 | Duplicados por voz (`attackStep`, `minHoldFrames`), `isFiniteAndBounded` sin uso, import `abs` sin uso, nombre `damped` confuso, comentario obsoleto | Eliminados/renombrados |
| 10 | `AudioVoicePool` y la lógica por hilo/voz, obsoletos | Eliminados |
| 11 | **Hallado por CI real:** `PeakLimiter` rebasaba el techo (0,97035 con la señal de prueba; 0,97180 con 32 voces): el paso de liberación subía la ganancia por encima de la máxima permitida para la muestra | `gain = min(relajada, needed)` — invariante `|salida| <= ceiling`; prueba de regresión con señal que decae despacio |

## 8. Pruebas nuevas
`SamplerFxStateTest`, `DspBuildingBlocksTest`, `EffectsBehaviourTest`, `SamplerEngineTest`, `FxLevelMeterTest`.
Cubren: coherencia de descriptores, líneas de retardo, all-pass, filtros, drive (bypass exacto), limitador,
tiempos de eco, ping-pong, estabilidad de realimentación, RT60 de la reverb, transparencia a 0 dB,
ley de paneo, velocidad, ataque/liberación sin saltos, liberación temprana, robo de voz, remuestreo 44,1→48 kHz,
independencia del tamaño de bloque, cola de reverb tras terminar la nota.

## 9. Estado de verificación
**Verificado por CI real (GitHub Actions, JDK 17):** el código Kotlin completo **compila**; 285 pruebas detectadas y
ejecutadas, **283 pasaron**. Fallaron 2 (`SamplerEngineTest.polyphonyLimit…`, `DspBuildingBlocksTest.limiterNeverExceeds…`),
ambas por el mismo defecto real de `PeakLimiter` (§7 #11). Reproducido numéricamente (Python) y corregido; la salida
corregida queda en exactamente 0,970000 en los tres escenarios simulados (señal de la prueba, señal decreciente, 32 voces).
**Pendiente:** nueva corrida de CI con el arreglo y prueba en dispositivo (latencia, *underruns*, sonido de reverb/delay,
auriculares, volver del segundo plano tras >15 s).

## 10. Benchmark del motor (dos niveles)
**a) En CI — `SamplerEngineBenchmarkTest`.** Mide el motor real (mismo código de producción) por bloque de
256 frames @ 48 kHz (5,33 ms de tiempo real) en 4 escenarios: reposo; 32 voces mono sin FX (remuestreo cúbico);
**32 voces estéreo + todos los FX** (peor caso real: drive A/B, chorus, delay fb 0,9, reverb RT60 20 s); y cola
decayendo 30 s (vigila regresiones de denormales). Informa promedio, p99, peor bloque, carga % y factor de tiempo real.
El informe se imprime en el log, se publica en el *resumen* de la corrida de GitHub Actions y se sube como artefacto
(`unit-test-report`). Aserciones deliberadamente holgadas (RTF >= 3 en el peor caso; cola con coste acotado):
sólo saltan ante un empeoramiento de orden de magnitud. **Es una medición en el JVM del runner, no en un teléfono**:
sirve para comparar escenarios y detectar regresiones, no como cifra absoluta de un dispositivo.

**b) En el dispositivo — monitor del panel SAMPLER.** `AudioOutputDriver` mide en su hilo de audio (no estima):
carga DSP (% del tiempo del bloque, media móvil), bloque más lento de la última ventana, tamaño actual del búfer
(latencia que añade la app) y *underruns* acumulados. Se muestra en la cabecera del panel
(`DSP 7% · 10.7 ms · 0 xruns`) y pasa a ámbar (carga >= 70 % o cualquier underrun) y rojo (>= 90 %). Los underruns se
registran además en logcat (`AudioOutputDriver`). Así, "latencia y crujidos" se ven en la propia pantalla.

**Pendiente:** las cifras. No existen todavía: aparecerán en la primera corrida de CI (a) y al probar en tu
dispositivo (b). Defecto latente corregido al instrumentar: tras reconstruir el `AudioTrack` (cambio de ruta), el
contador de ráfagas y de underruns conservaba los valores del stream anterior; ahora se reinician con el stream nuevo.
