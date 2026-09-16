# Dwp Creator

App Android 100% nativa (Kotlin + Jetpack Compose) para renombrar y reempaquetar
instrumentos DirectWave (.dwp) creados en FL Studio Desktop, listos para FL Studio Mobile.

**Estado real del proyecto** (ver `Doc/`, empezar por `Doc/18_CANONICAL_STATE.md`
para el estado agregado más actual, y `Doc/21_CORE_STABILIZATION_PASS3.md` para
la última pasada de correcciones): la app ya tiene un motor DWP funcional
(parseo/edición/reexportación binaria con round-trip exacto verificado contra
un `.dwp` real), un decoder de audio WAV (8/16/24/32-bit PCM, float32,
WAVE_FORMAT_EXTENSIBLE), reproducción nativa de samples (`AudioTrack`) y
entrada MIDI externa (USB/Bluetooth, con parser de máquina de estados
tolerante a mensajes fragmentados entre buffers) para previsualizar el
instrumento tecla por tecla.

**Estabilización del Core: provisional según evidencia, no confirmada.** Los
problemas de robustez identificados en tres auditorías sucesivas fueron
corregidos con evidencia de código y 125 tests escritos, pero **ninguno de
esos tests se ha ejecutado realmente** en un entorno con Android SDK/Gradle —
solo verificados por lectura y trazado manual. El estado oficial es
`CORE NOT YET VERIFIED` hasta que se confirme una ejecución real (ver
`Doc/21_CORE_STABILIZATION_PASS3.md` §6).

Lo que **todavía no existe** es la generación de DWP "monolítico" con audio
embebido (FLAC, `0x0206`) — DwpCreator **no es, todavía, un generador de DWP
Monolithic**; ese es el objetivo final del proyecto y está deliberadamente
pendiente de una fase de investigación separada (ver
`Doc/08_MONOLITHIC_DWP_ANALYSIS.md`).

Toda la documentación técnica interna vive en `Doc/`: auditorías, registro de
riesgos, y el historial completo de qué se corrigió y cuándo. Es la fuente de
verdad sobre el estado del código, no este README.

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
4. Descarga `DwpCreator-debug-apk`, es un .zip que contiene `app-debug.apk`.
5. Instálalo en tu celular/tablet (activa "orígenes desconocidos" si te lo pide).

El workflow ejecuta la suite de tests unitarios (`gradle testDebugUnitTest`)
antes de compilar el APK, e imprime en el log un resumen explícito (tests
detectados/ejecutados/exitosos/fallidos/omitidos) leído directamente de los
reportes JUnit reales — si ves ✅ verde, esos números pasaron de verdad en ese
build. El conteo agregado vigente (125 tests en 5 archivos, no el conteo
histórico de Fase 1) está en `Doc/18_CANONICAL_STATE.md` §E.

