# 71 — Síntesis por voz del sampler (muestra, filtro, envolventes, LFO)

Antes de este cambio el motor reproducía cada muestra tal cual: sin ADSR configurable (3 ms/15 ms fijos), sin filtro,
sin afinación, sin LFO, sin bucle, sin reverse, sin glide ni pitch bend. Referencias consultadas: manual oficial de
FL Studio Mobile (módulo DW Sampler) y manual oficial de DirectWave de escritorio. Se tomaron como referencia, no como
copia: el diseño es propio y más completo (ver "Diferencias").

## Arquitectura (una responsabilidad por archivo)

**Infraestructura de parámetros** (`audio/dsp`, una sola vez para bus y voz)
| Archivo | Responsabilidad |
|---|---|
| `FxCurve`, `FxUnit` | Curvas (LINEAR/LOG/SQUARED) y unidades de visualización |
| `ParamScale` | Rango, defecto, curva, rejilla; conversión valor <-> posición del knob (única implementación) |
| `ParamFormatter` | Valor -> texto de cada unidad (única implementación) |
| `RhythmTable` | Divisiones rítmicas y conversión división + tempo -> tiempo (la comparten `DelaySync` y `LfoSync`) |
| `SamplerParam`, `SamplerToggle` | Contrato común; `FxParam`/`FxToggle` (bus) y `VoiceParam`/`VoiceToggle` (voz) lo implementan |
| `SamplerParams` | Registro de todos los parámetros: lo recorren la persistencia y las pruebas |
| `FxModule`, `FxPreset`, `FxPresets`, `FxPresetCatalog`, `VoicePresetCatalog` | Módulos con reinicio/presets: modelo, operaciones puras y datos por separado |

**Estado de voz** (`audio/voice`, inmutable): `VoiceState` agrupa `SampleState`, `FilterState`, `AdsrState`/
`ModulationEnvelopeState` (x3) y `LfoState` (x2); las opciones son enums (`LoopMode`, `FilterMode`, `LfoShape`,
`LfoDestination`, `LfoRateMode`) sobre `ChoiceOption`. **Controles**: `SampleControls`, `FilterControls`,
`EnvelopeControls` (plantilla única para las 3 envolventes), `LfoControls` (plantilla única para los 2 LFO).

**DSP de voz** (`audio/voice`): `Adsr`, `SvfCoefficients`+`SvfFilter`, `FilterScaling`, `FilterResponse`,
`LfoWaveforms`, `LfoOscillator`, `LfoSync`, `Glide`, `SamplePlayhead`, `VoiceModulator`, `VoiceSettings` (+`FilterSettings`,
`LfoSettings`), `VoiceNote`, `SamplerVoice` (orquesta). `SamplerEngine` sólo asigna voces, mezcla y mide.

**UI** (`ui/components`, `ui/components/voice`): `SamplerPanelContent` (pestañas SAMPLE · FILTER · ENVELOPES · LFO · FX),
una clase por pestaña, `AdsrCurve`/`FilterResponseCurve`/`LfoWavePreview` (la geometría es pura: `AdsrCurveGeometry`,
`LfoPreviewGeometry`), y widgets genéricos `SamplerParamKnob`, `SamplerChoiceChip`, `SamplerToggleSegmented`.

## Comportamiento
- **Estado de fábrica = sonido idéntico al anterior**: filtro puenteado (`FilterSettings.bypass`), ADSR 3 ms/15 ms, sin
  transposición, velocidad lineal. Prueba: `theFactoryVoiceReproducesTheSampleExactly`.
- **Afinación**: Pitch ±24 st, Fine ±100 ct, **Key Trk** (transposición = (nota tocada − raíz) × seguimiento; las notas
  MIDI dentro de una zona extendida ya se transponen), PB Range 1–24 st + pitch bend MIDI real (parser ampliado).
- **Amp ADSR**: ataque lineal (mín. 3 ms), decay/release exponenciales de 60 dB; fin automático con sustain 0.
- **Filtro**: SVF TPT/ZDF — LP12, LP24, HP12, Band, Notch; Cutoff (20 Hz–20,5 kHz), Resonance (Q 0,71–10), Drive,
  Key Trk y Vel Trk bipolares, envolvente (±8 oct) y LFO. La UI dibuja su curva con la misma función que las pruebas.
- **Envolventes de filtro y afinación**: ADSR + cantidad bipolar (afinación ±24 st).
- **2 LFO por voz**: Sine/Triangle/Saw/Square/S&H/Smooth, destinos Pitch/Filter/Level/Pan/Reso, modos Hz/Tempo/One-Shot,
  fase inicial, fundido de entrada, profundidad bipolar (vibrato con curva cuadrática, ±12 st). Se reinicia con cada nota.
- **Bucle**: Off/Forward/Sustain/Bounce/One-Shot con interpolación sin salto en la costura; Reverse; Start.
- **Mono/Legato/Glide**: legato continúa la voz y desliza la afinación; glide lineal en semitonos, "Always" o "Legato only".
- Modulaciones a ritmo de control (cada 16 muestras) con interpolación lineal de velocidad y ganancias; el contador es de
  la voz, así que el resultado no depende del tamaño de bloque (prueba incluida). Sin asignaciones en el hilo de audio.

## Diferencias respecto a las referencias (mejoras deliberadas)
FL Mobile: 1 filtro LP, 1 LFO de 3 formas y 4 destinos. Aquí: 5 modos de filtro con drive, 2 LFO con 6 formas, 5 destinos,
fase y fundido; envolventes con curva en vivo; modos de bucle de DirectWave; seguimiento bipolar; presets (filtro, LFO) con
detección de "Custom" y reinicio por módulo.

## Limitaciones conocidas (honestas)
1. **Los puntos de bucle de los `.dwp` no se decodifican** (campos 0x01f8–0x0204 sin fixture real, R-08). Hoy el bucle sale del
   chunk `smpl` del WAV; sin él, los modos de bucle reproducen la muestra una vez. No se inventó el formato del DWP.
2. Legato: al soltar la tecla nueva la voz se libera aunque la anterior siga pulsada (no hay pila de notas con retorno).
3. Sin mod wheel/CC, sin LFO en free-run global, sin editor de puntos de bucle.
4. La resonancia no lleva compensación de ganancia (como un filtro analógico): el limitador del bus protege la salida.
5. Los presets de filtro/LFO son criterio de oído; ajustar tras probar.

## Verificación
- Pruebas JVM nuevas: `AdsrTest`, `SvfFilterTest` (el filtro digital coincide con la curva analítica ≤1 dB),
  `FilterScalingAndResponseTest`, `LfoTest`, `GlideTest`, `SamplePlayheadTest`, `VoiceModulatorTest`, `VoiceSettingsTest`,
  `SamplerEngineVoiceTest` (afinación, filtro, envolventes, bucle, one-shot, reverse, legato, glide, pitch bend, LFO,
  independencia de bloque, extremos), `ParamScaleTest`, `ParamFormatterTest`, `SamplerParamsTest` (cada parámetro cambia
  sólo su campo), `FxPresetsTest` (ampliado), `WavLoopPointsTest`, `CurveGeometryTest`, pruebas de pitch bend en el parser MIDI.
- **Hecho**: la respuesta del SVF se validó en Python contra la curva analítica (≤0,8 dB hasta 4 kHz).
- **Pendiente** (el entorno de edición no tiene compilador Kotlin ni red): compilar, correr `testDebugUnitTest` en el CI y
  escuchar en la tablet. Coste de CPU por voz con filtro LP24 + 2 LFO sin medir en ARM.

## Auditoría posterior (correcciones aplicadas)
Revisión completa del código nuevo y de los parches de cableado. Hallazgos y solución:
1. **Bug (voz)**: al activar el filtro a mitad de nota con un bloque de control en curso, los coeficientes sin inicializar
   producían hasta 16 muestras de silencio/chasquido. `SamplerVoice.engageFilter` calcula coeficientes y limpia el estado antes
   de la primera muestra filtrada. Prueba de regresión: `enablingTheFilterMidControlBlockDoesNotCreateAGap`.
2. **Bug (motor)**: tras `resetAllVoices` la última tecla seguía recordada y la primera nota del instrumento siguiente podía
   deslizarse desde una nota del anterior. `lastKey` se reinicia con el reset.
3. **Duplicado**: `DelaySync` y `LfoSync` repetían tabla, nombres, índices y conversión por BPM -> `RhythmTable` única.
4. **Duplicado de mecanismo**: la división del delay usaba una unidad propia (`DIVISION`); ahora es un parámetro `CHOICE` como
   el resto (se eliminó la unidad y su rama del formateador).
5. **Duplicado y fragilidad**: los presets de drive A/B eran dos listas idénticas y un destructurado dependiente del orden de
   `params`; ahora una sola lista de especificaciones genera ambas.
6. **Duplicado (UI)**: el menú desplegable y sus entradas estaban copiados en el selector de preset y en el de opción ->
   `FxMenu` / `FxMenuItem`.
7. **Rendimiento**: `SamplePlayhead.read` recalculaba el estado de bucle 4 veces por muestra y canal; se calcula una vez por lectura.
8. **Defecto previo del repositorio**: el comentario de `refreshMidiDevices` estaba pegado a `setFxParam`; recolocado y
   documentadas las funciones de parámetros.
9. Limpieza: imports sin uso en 4 archivos.
Verificaciones mecánicas sobre los 100+ archivos tocados: balance de llaves/paréntesis, todos los `import` internos resuelven
a una declaración, y ningún identificador del proyecto se usa sin importar (los 3 avisos restantes son previos, de pruebas ajenas).
