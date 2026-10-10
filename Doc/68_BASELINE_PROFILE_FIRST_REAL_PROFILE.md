# 68 — Primer Baseline Profile real instalado

## Resultado del workflow
"Generate Baseline Profile" **#15** (9-oct-2026, emulador API 30): **verde** y con perfil real
(`app/src/release/generated/baselineProfiles/baseline-prof.txt`, 1.001.243 bytes, 10.411 reglas; 385 son
clases propias, el resto androidx/compose/kotlin/java/android). Primera corrida con perfil real tras la
investigación cerrada en Doc/62 (#7–#14 fallaron; la causa de aquellos fallos nunca se diagnosticó).

## Corrección de un error de diagnóstico previo
Se afirmó que `app/src/main/baseline-prof.txt` tenía "39 líneas escritas a mano". **Era falso**: solo se leyó la
cabecera; el archivo contenía únicamente comentarios (Doc/46: "deliberadamente vacío"). El proyecto **no tenía
ningún perfil real** hasta este documento.

## Lo que reveló el perfil (auditoría del arranque en frío)
- `AudioOutputDriver.start`, `runLoop`, `buildTrack` y `SamplerEngine.render`: **0 entradas** -> el arranque ya
  no abre audio (arreglo de Doc/67 confirmado con evidencia).
- Pero `SamplePlayer.<init>`, `SamplerEngine.<init>` y los constructores de `MasterFxChain`, `FdnReverbEffect`,
  `StereoDelayEffect` y `ChorusEffect` **sí se ejecutaban al arrancar**: se construía el motor (búferes de
  delay/reverb ~1 MB + ~30 clases de DSP) sin que hubiera nada que reproducir.

## Cambios
1. `SamplePlayer`: el motor y el driver se crean **bajo demanda** (`backend()` con doble comprobación) en `prewarm`
   o en el primer `play`. `setFxState` guarda el estado y lo aplica al crear el motor (bajo el mismo lock: sin
   actualizaciones perdidas). `outputPeaks`/`audioStats` devuelven ceros si el motor aún no existe (el monitor del
   panel no lo fuerza). Arranque sin ningún trabajo de audio.
2. `app/src/main/baseline-prof.txt` = perfil generado, **sin** las 65 reglas del motor de audio (solo estaban por el
   arranque eager ya eliminado). Se conservan `SamplePlayer`, `SamplerFxPreferences` y `SamplerFxState`/`FxParam`/
   `FxToggle` (el ViewModel los usa al arrancar).
3. Workflows: `runs-on: ubuntu-latest` -> `ubuntu-24.04` (GitHub migra `ubuntu-latest` a Ubuntu 26 el 19-oct-2026;
   fijarlo evita que los dos workflows cambien de imagen sin aviso).

## Alcance y límites (honestidad)
- Cubre **sólo el arranque en frío hasta la pantalla vacía**. No cubre lista de muestras, panel SAMPLER ni el camino de
  render de audio (el generador no puede cargar un instrumento: selector de archivos del sistema).
- Medición posterior (Doc/69): en el emulador de CI la mejora de arranque **no es demostrable** (-3,3 % de mediana, dentro del ruido; el techo
  de compilar todo es -0,2 %). El perfil se conserva por su coste nulo, sin presentarlo como mejora medida.
- Esta versión no se compiló aquí; CI debe confirmar que `assembleRelease` acepta el perfil de ~1 MB (formato
  generado por la propia herramienta, así que no se espera problema).
