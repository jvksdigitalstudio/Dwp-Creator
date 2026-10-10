# 70 — Módulos de efectos: DSP de calidad comercial y presets

Continuación de Doc/66 (motor FX). Alcance: FX DRIVE A/B, FX CHORUS, FX DELAY y FX REVERB.
MASTER no cambia. Todos los efectos siguen apagados por defecto y la cadena por defecto sigue siendo
transparente (0 dB, nada insertado; cubierto por `defaultChainIsTransparentAtZeroDb`).

## Qué estaba mal / limitado antes (auditoría del código real)
| Módulo | Limitación |
|---|---|
| Líneas de retardo | Lectura **lineal**: pasa-bajos variable, el brillo "respira" al modular. |
| Drive | `tanh` muestra a muestra: **aliasing** audible a drive alto; sin control de tono. |
| Chorus | Sin control de anchura estéreo (un chorus muy abierto puede cancelarse en mono). |
| Delay | Sin sincronía con tempo, sin carácter de cinta. |
| Reverb | Sin pre-delay, sin modulación (colas largas con timbre metálico), sin anchura. |
| UI | Sin presets ni reinicio de módulo. |

## Cambios de DSP
- **`DelayLine.readCubic`**: interpolación cúbica de Hermite (Catmull-Rom, 4 puntos). Exacta en retardos enteros.
  La lectura lineal (`read`) se conserva sólo donde el retardo es fijo.
- **`Dsp.sin2Pi`**: seno parabólico refinado (error máx. ≈ 1,1e-3) para los LFO por muestra.
  **`Dsp.logCosh`**: estable para |x| grande.
- **Drive (`DriveStage`)**: anti-aliasing **ADAA de primer orden** (Parker et al., DAFx 2016) con la antiderivada
  exacta `ln cosh` de `tanh`; salida acotada en [-1, 1]; medio retardo de muestra. Nuevo **Tone**
  (pasa-bajos de un polo 500 Hz–20 kHz sobre la rama saturada; 20 kHz = desactivado, camino idéntico al anterior).
- **Chorus**: lectura Hermite + **Width** 0–150 % (escala la componente lado en mid/side).
- **Delay**: lectura Hermite; **Sync** (BPM 40–240 + división 1/16T…1/1 con tresillo y puntillo; los tiempos que
  exceden 1,99 s se pliegan por octavas, no se recortan); **Tape** (saturación `tanh(1,5x)/1,5` en el bucle de
  realimentación + **Wow**/flutter por modulación del tiempo, ±1,2 ms y ±0,12 ms a 100 %, fase distinta por canal).
  Wow sólo actúa con Tape activo.
- **Reverb**: **Pre-delay** 0–250 ms (pre = 0 es paso directo exacto), **Modulation** (LFO lento propio por cada una
  de las 8 líneas de la FDN, ±12 muestras máx., lectura Hermite), **Width** 0–150 %.
  Estabilidad: la FDN conserva ganancia < 1 (Hadamard ortonormal + `g_i` por línea + damping ≤ 1).

## Parámetros nuevos (persistencia automática: claves = `name` del enum; valores ausentes -> fábrica)
`DRIVE_A_TONE`, `DRIVE_B_TONE`, `CHORUS_WIDTH`, `DELAY_BPM`, `DELAY_DIVISION` (escalonado, 10 posiciones),
`DELAY_WOW`, `REVERB_PREDELAY`, `REVERB_MODULATION`, `REVERB_WIDTH`; interruptores `DELAY_SYNC`, `DELAY_TAPE`.
`FxParam` gana `steps` (0 = continuo). Una preferencia antigua se carga sin migración.
El sampler **no recibe tempo de un anfitrión**: el BPM del delay es un parámetro propio.

## Presets (`FxPresets`, funciones puras)
- 5 (drive), 5 (chorus), 5 (delay), 6 (reverb) presets de fábrica. Cada preset describe el módulo completo.
- `apply` carga el preset y **enciende** el módulo. `reset` restaura los ajustes de fábrica **sin cambiar el encendido**.
- `activeName` devuelve el preset coincidente o **Custom** (comparación sobre la posición normalizada del knob,
  tolerancia 0,002; el encendido no cuenta). La UI no guarda selección propia: el nombre sale siempre del estado.
- UI: cada tarjeta muestra un selector con el nombre activo + menú de presets + "Reset module"
  (`FxPresetSelector`). Con SYNC el knob TIME se sustituye por DIVISION + BPM.

## Verificación
- Pruebas JVM nuevas/ampliadas: `DspBuildingBlocksTest` (Hermite exacta en enteros y ~10x más precisa que la lineal,
  `sin2Pi`, `logCosh`, ADAA vs `tanh`, Tone), `EffectsBehaviourTest` (pre-delay = desplazamiento exacto de 960
  muestras a 20 ms, modulación finita, widths, cinta), `DelaySyncTest`, `FxPresetsTest` (cada preset aplicado,
  detectado y renderizado por la cadena real sin NaN y bajo el techo del limitador), `SamplerFxStateTest`.
- **Hecho:** las fórmulas numéricas críticas (`sin2Pi`, Hermite, ADAA) se reprodujeron en Python y cumplen las
  tolerancias de los tests (sin2Pi 1,1e-3; Hermite/lineal ≈ 0,10; ADAA vs `tanh` 5e-3).
- **Pendiente (no se pudo en el entorno de edición: sin compilador Kotlin ni red):** compilar y correr
  `./gradlew testDebugUnitTest` / el CI, y **escuchar** los presets en la tablet. Los valores de presets y la
  intensidad de Wow/Modulation son criterio de oído y se espera ajustarlos tras la prueba real.
- Coste de CPU de ADAA (≈ 2 funciones trascendentes por canal y muestra con Drive activo) sin medir en ARM:
  revisar el monitor DSP del panel con Drive A+B al máximo.
