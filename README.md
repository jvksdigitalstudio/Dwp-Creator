# Dwp Creator

App Android 100% nativa (Kotlin + Jetpack Compose) para renombrar y reempaquetar
instrumentos DirectWave (.dwp) creados en FL Studio Desktop, listos para FL Studio Mobile.

**Estado real del proyecto** (ver `Doc/`, empezar por `Doc/18_CANONICAL_STATE.md`
para el estado agregado más actual): la app ya tiene un motor DWP funcional
(parseo/edición/reexportación binaria con round-trip exacto verificado contra
un `.dwp` real), un decoder de audio WAV (8/16/24/32-bit PCM, float32,
WAVE_FORMAT_EXTENSIBLE), reproducción nativa de samples (`AudioTrack`) y
entrada MIDI externa (USB/Bluetooth, con parser de máquina de estados
tolerante a mensajes fragmentados entre buffers) para previsualizar el
instrumento tecla por tecla. Al exportar, la app ahora deja elegir entre el
`.zip` clásico (un `.dwp` + un `.wav` por muestra, para FL Studio Mobile) o
un `.dwp` autocontenido experimental con el audio de cada muestra embebido
como FLAC dentro del propio archivo — ver `Doc/27_PHASE2_APP_INTEGRATION_REPORT.md`.

**Sampler profesional (Doc/66).** La reproducción ya no es "un `AudioTrack` por
nota": un motor de mezcla único (`SamplerEngine` + `AudioOutputDriver`, stream float de baja
latencia) alimenta un bus maestro con MASTER, FX DRIVE A/B, FX DELAY, FX REVERB y FX CHORUS,
controlable desde el panel SAMPLER (knobs al estilo DirectWave). Compila en CI real (283/285 pruebas en la
primera corrida; el defecto restante del limitador está corregido). Pendiente: nueva corrida de CI y prueba en dispositivo —
ver `Doc/66_PROFESSIONAL_SAMPLER_FX_ENGINE.md` §9.

**Core verificado con ejecución real de tests, no solo por lectura de código.**
Los problemas de robustez identificados en las auditorías sucesivas fueron
corregidos con evidencia de código, y los 125 tests se ejecutaron realmente
en GitHub Actions: `BUILD SUCCESSFUL`, **125/125 pasando, 0 fallidos**, APK
compilado. El estado oficial es `CORE STABLE — VERIFIED BY REAL CI EXECUTION`
(ver `Doc/22_CORE_STABILIZATION_FINAL_VERIFICATION.md` §18-19 para la
evidencia completa, incluyendo el log real de esa corrida).

Lo que **todavía no está verificado con ejecución real** es la generación de
DWP "monolítico" con audio embebido (FLAC, `0x0206`) — ahora ya accesible
desde la propia app (diálogo de exportación, ver arriba), no solo como
código de dominio: el subsistema existe como código (módulos `:core:codec`
y `:core:dwp`, paquetes `codec.flac` y `dwp.monolithic`) y tiene tests unitarios escritos (incluida
cobertura del flujo de proyecto completo, varias muestras a la vez, en
`Doc/27`), pero **no se ha ejecutado `gradle testDebugUnitTest` contra la
versión actual todavía** — se implementó y audita en un entorno sin Android
SDK/Gradle/red, así que su primera ejecución real ocurre en GitHub Actions,
igual que exige el resto del proyecto. Una corrida anterior (run #26, ver
`Doc/25` §20) sí llegó a ejecutarse y encontró 4 fallos reales, ya
corregidos junto con un segundo bug real encontrado en FASE 1 (límite de
tamaño de bloque FLAC, ver `Doc/26` §7) — pero ninguna corrida posterior a
esas correcciones ni a la integración de FASE 2 ha confirmado el estado
verde todavía. Hasta que esa corrida sea verde, tratar esta capa como
"escrita, auditada e integrada en la UI real, no verificada por ejecución
real" (ver `Doc/26`/`Doc/27` para el estado exacto, punto por punto).
Tampoco hay, todavía, ningún `.dwp` monolítico real de referencia contra el
que confirmar la estructura exacta de `0x0206`, ni una prueba en FL Studio:
ver `Doc/26` §17/§21 y `Doc/27` §5-7. Por eso el diálogo de exportación
etiqueta esta opción como "experimental" en la propia UI.

Toda la documentación técnica interna vive en `Doc/`: auditorías, registro de
riesgos, y el historial completo de qué se corrigió y cuándo. Es la fuente de
verdad sobre el estado del código, no este README.

## Arquitectura (multimódulo)

> **Refactorización modular (Doc/72, Doc/73): escrita y verificada estáticamente, todavía NO compilada ni
> ejecutada.** La primera corrida de CI es la verificación real. Hasta entonces, tratarla como "pendiente de CI".

El proyecto ya no es un único módulo: son siete, con dependencias solo hacia abajo (sin ciclos), una
responsabilidad por archivo y la presentación desacoplada del motor mediante contratos.

| Módulo | Tipo | Contenido |
|---|---|---|
| `:app` | Android app | UI Compose, ViewModel (fachada), capa de sesión (`session.*`), raíz de composición (`di`) |
| `:platform:sampler-android` | Android lib | Salida de audio (`AudioTrack`), `SamplePlayer`, persistencia de ajustes |
| `:core:sampler-engine` | Kotlin JVM | Efectos DSP, voces (ADSR/SVF/LFO), mezcla, caché de muestras |
| `:core:sampler-api` | Kotlin JVM | Parámetros, estados, presets y contratos (`SamplerPlayback`, `FxStateStore`) |
| `:platform:midi` | Android lib | Entrada MIDI de Android y decodificación de mensajes |
| `:core:dwp` | Kotlin JVM | Formato DWP, renombrado/parcheo, exportador monolítico, E/S de zip |
| `:core:codec` | Kotlin JVM | Decodificación WAV y códec FLAC |

**Estructura de carpetas** (detalle y responsabilidades en el `README.md` de cada una):

```
DwpCreator/
├── app/                        Aplicación Android: UI Compose, ViewModel, sesión, DI
├── core/                       Lógica de negocio en Kotlin puro (JVM, sin Android)
│   ├── codec/                    WAV y FLAC
│   ├── dwp/                      Formato DWP, DWP monolítico, proyectos zip
│   ├── sampler-api/              Parámetros, estados, presets y contratos
│   └── sampler-engine/           Motor de audio: DSP, voces, mezcla
├── platform/                   Integraciones con Android
│   ├── sampler-android/          Salida de audio, SamplePlayer, persistencia
│   └── midi/                     Entrada MIDI
├── baselineprofile/            Generación del Baseline Profile (rendimiento)
├── testdata/                   Archivos reales para las pruebas (Instrument.dwp)
├── tools/                      Verificaciones del repositorio (arquitectura)
├── config/                     Configuración de compilación compartida
├── Doc/                        Documentación técnica (índice en Doc/README.md)
├── .github/workflows/          CI
└── build.gradle.kts · settings.gradle.kts · gradle.properties · README.md · .gitignore
```

La carpeta refleja la capa: **`core/`** no puede depender de Android; **`platform/`** solo adapta APIs de
Android a contratos de `core/`; **`app/`** compone todo. Cada carpeta tiene su `README.md` con su
responsabilidad, API principal y qué NO debe contener.

```bash
python3 tools/check_architecture.py          # reglas de arquitectura (segundos, sin compilar)
gradle :core:codec:test :core:dwp:test :core:sampler-api:test :core:sampler-engine:test        # Kotlin puro, sin emulador
gradle :platform:midi:testDebugUnitTest :platform:sampler-android:testDebugUnitTest :app:testDebugUnitTest
gradle :app:assembleRelease                  # APK
```

Reglas (las hace cumplir CI): un módulo solo importa de los que declara; sin ciclos; **un tipo por archivo**;
un paquete pertenece a un solo módulo. El fixture real `Instrument.dwp` vive en `testdata/`; la configuración de Compose compartida, en `config/`. Detalle completo,
mapa de paquetes antiguo→nuevo y guía de dónde añadir cada cosa: `Doc/72_MODULAR_ARCHITECTURE_REFACTOR.md`.

## Subir el proyecto desde Termux (tablet)

```bash
pkg install git -y
cd ruta/donde/tengas/el/proyecto/DwpCreator

git init
git add .
git commit -m "Actualización del proyecto"

git remote add origin https://github.com/TU_USUARIO/TU_REPO.git
git branch -M main
git push -u origin main
```

## Descargar el APK

1. Ve a tu repo en GitHub → pestaña **Actions**.
2. Entra al workflow "Build APK" que se ejecuta automáticamente al hacer push.
3. Cuando termine (ícono verde ✅), abre el run y baja hasta **Artifacts**.
4. Descarga `DwpCreator-release-apk`, es un .zip que contiene `app-release.apk`.
5. Instálalo en tu celular/tablet (activa "orígenes desconocidos" si te lo pide).

El APK es de **release** (R8 con shrink + optimización, ver `Doc/45`): es el que
hay que usar para juzgar el rendimiento real, porque una build de debug es
notablemente más lenta en Jetpack Compose. Va firmado con la clave de debug de
AGP solo para poder instalarlo directo desde el artefacto de CI -- **no sirve
para publicar en Play Store** (eso requiere un keystore de producción propio).
La ofuscación está desactivada a propósito (`-dontobfuscate`, ver `Doc/52`) para
que la pantalla de crash siga mostrando trazas legibles.

El workflow primero verifica las reglas de arquitectura (`tools/check_architecture.py`),
luego ejecuta la suite de tests unitarios de **todos los módulos** (Doc/72 §11)
antes de compilar el APK, e imprime en el log un resumen explícito, desglosado
por módulo (tests detectados/ejecutados/exitosos/fallidos/omitidos) leído
directamente de los reportes JUnit reales — si ves ✅ verde, esos números pasaron de verdad en ese
build.

**Conteo vigente tras la refactorización modular (Doc/72): 521 métodos `@Test` en 54 clases de prueba**
(los 480 anteriores se conservan íntegros y se añaden 41 de la capa de sesión). Es el total de tests
*presentes*, no un resultado de ejecución: la refactorización aún no se ha compilado en CI. El párrafo
siguiente es el conteo histórico de una fase anterior.

**Conteo actual de tests presentes en el repositorio: 244 métodos `@Test`
en 19 archivos** (215 hasta `Doc/28` + 29 nuevos de `Doc/29` -- incluida la adenda de motor de audio de baja latencia: `InstrumentNameValidatorTest`, `SampleOctaveTest`, `ZipProjectStreamingTest`, `DecodedSampleCacheTest`, todavía sin ejecutar; el desglose que sigue describe los 215 anteriores) — 130 del Core (`DwpEngineTest`, `WavDecoderTest`,
`MidiMessageParserTest`, `ZipProjectIoTest`, `PcmConverterTest`; 5 de los
130 son nuevos de FASE 2.1, cobertura de NaN/Infinity/-0.0 en
`PcmConverterTest.kt` — ver `Doc/28_PHASE2_1_FLOAT32_DWP_AUDIT.md`), **ya
ejecutados y verificados por CI real** (ver `Doc/22_CORE_STABILIZATION_FINAL_VERIFICATION.md`;
los 5 más recientes, igual que el resto de esta lista, todavía no),
más 85 del subsistema Monolithic DWP + FLAC (archivos de los módulos `:core:codec` —FLAC— y
`:core:dwp` —monolítico y E/S de zip—, antes `domain/flac/`, `domain/dwp/monolithic/` y `domain/io/`; 4 de los 85 son nuevos -- cobertura
real del callback de progreso de exportación en `ZipProjectExporter` y
`MonolithicDwpProjectBuilder`, contra el fixture real de 48 muestras),
**escritos pero todavía sin una ejecución
real conjunta posterior a las últimas correcciones en Gradle/CI** — ver el
párrafo anterior y `Doc/26_PHASE1_FLAC_MONOLITHIC_INTEGRATION_REPORT.md`
§14/`Doc/27_PHASE2_APP_INTEGRATION_REPORT.md` §4. No se debe leer "244"
como "244 pasando": es el total de tests *presentes* en el código hoy, no
un resultado de ejecución. El estado agregado y trazable de ambos números
está en `Doc/18_CANONICAL_STATE.md` §E y en `Doc/26`/`Doc/27`/`Doc/28`.

**Sobre "32-bit float":** una auditoría forense (`Doc/28`) determinó que la
caracterización del fixture real (`Instrument.dwp`) como "float32", repetida
en varios documentos anteriores (`Doc/05`, `Doc/07`, `Doc/23`), depende de
un `.wav` real examinado en una fase muy anterior del proyecto y ya no
disponible en este repositorio — el propio `.dwp` no lleva ningún campo que
distinga PCM entero de 32 bits de IEEE float de 32 bits. Reclasificado como
🟡 evidencia parcial, no 🟢 confirmada; el comportamiento del código
(rechazar ambos formatos explícitamente en el pipeline Monolithic) no
cambió.

**Módulos FX premium (Doc/70).** Drive con anti-aliasing ADAA + Tone; Chorus con Hermite + Width; Delay con Sync, modo cinta y Wow; Reverb con Pre-delay, Modulation y Width; presets de fábrica por módulo con detección de "Custom" y reset.

**Síntesis por voz (Doc/71).** Pestañas SAMPLE · FILTER · ENVELOPES · LFO · FX: afinación y pitch bend, filtro SVF de 5 modos con curva en vivo, ADSR de amplitud/filtro/afinación, 2 LFO, bucle (chunk `smpl`), reverse, legato y glide.
