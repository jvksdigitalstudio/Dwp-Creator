# 45 — Lag persistente al scrollear tras el Doc/44: auditoría de recomposición y Strong Skipping Mode

## Contexto

El Doc/44 ya identificó y corrigió una causa real de coste de dibujado
(sombras dinámicas con `ambientColor`/`spotColor` propios en los 4 controles
de mezcla por fila, sustituidas por `softGlow` dibujado a mano). El usuario
reporta que el lag/retardo/bajo FPS al scrollear la lista de 48 muestras
sigue sintiéndose. Se auditó de nuevo el código real (no se asumió que el
fix anterior fuera insuficiente sin comprobarlo) para encontrar la siguiente
causa concreta.

## Verificación de lo ya corregido

`SampleMixerControls.kt` (líneas 204-220): `softGlow` sigue en su sitio,
aplicado en `MixerToggleButton` y `MixerThumb`. Confirmado: esa causa sigue
resuelta, no fue revertida. El lag remanente tiene otro origen.

## Segunda causa real encontrada: recomposición no "saltable" (skip)

Auditoría de `MainScreen.kt` + `SampleRow.kt` + `SampleMixerControls.kt`:

- `LazyColumn` recibe `mixerStates: Map<Int, SampleMixerState>` y closures
  (`onToggleMute`, `onToggleSolo`, `onPanChange`, `onVolumeChange`, etc.)
  capturados directamente por la lambda de `items(...)`.
- El compilador de Compose (Kotlin 1.9.23 / Compose Compiler 1.5.11, ver
  `app/build.gradle.kts`) **no puede demostrar que `Map` sea inmutable** --
  la interfaz `kotlin.collections.Map` admite implementaciones mutables, así
  que Compose la marca como tipo *inestable*. Un parámetro inestable
  desactiva el "skip" (comparación de parámetros para decidir si repetir o
  no el trabajo) de **toda la función que lo recibe**, no solo de la parte
  que en verdad lee ese mapa.
- Cada `SampleRow` monta, además, hasta 10 objetos de animación
  (`animate*AsState`) entre `PianoKeyBadge` y `SampleMixerStrip` (2 en la
  tecla + 3 por botón Mute/Solo x2 + 1 por thumb de Pan/Volumen x2). Sin
  "skip" efectivo, cada uno de ellos vuelve a evaluarse en cada
  recomposición del `LazyColumn`, no solo cuando el estado que de verdad
  gobierna esa fila cambió.
- Esto no depende de que el usuario esté tocando el mezclador: durante un
  scroll rápido, cada fila que entra en pantalla se compone por primera vez
  (esperado, no es un bug), pero **las filas que ya estaban visibles vuelven
  a recomponerse de más** cada vez que `LazyColumn` reevalúa su contenido,
  en vez de saltarse (skip) porque sus parámetros reales no cambiaron. Ese
  trabajo repetido, sumado al coste real por fila (gradientes, bordes,
  degradados, halos) del Doc/44, es la segunda causa medible del lag.

## Corrección real aplicada

`app/build.gradle.kts`: **Strong Skipping Mode** del compilador de Compose,
activado vía el flag experimental oficial del propio plugin
(`-P plugin:androidx.compose.compiler.plugins.kotlin:experimentalStrongSkipping=true`),
aplicado a todas las tareas `KotlinCompile` del módulo.

Con este modo activo, Compose deja de invalidar una función completa solo
porque uno de sus parámetros sea de un tipo que no puede demostrar
inmutable (como `Map`/lambdas): en su lugar, compara por **identidad
estable inferida** y por valor donde puede, y solo repite el trabajo de la
parte que realmente cambió. No cambia ningún resultado visual ni de
comportamiento -- es puramente una reducción de trabajo de recomposición
redundante.

### Por qué no es un parche
No se "bajó la calidad" de las filas (gradientes, halos, bordes) para
disimular el coste, ni se quitaron controles: se identificó el mecanismo
real de Compose (parámetros no-`@Stable` desactivando el skip de la función
completa) y se corrigió con la herramienta que el propio equipo de Compose
documenta para ese problema exacto -- no una reescritura arriesgada a
Kotlin 2.0/K2 sin poder compilar y probar en este entorno.

## Causa raíz confirmada: el pipeline real solo construía debug

Preguntado directamente, el usuario confirmó que no usa Android Studio --
el flujo real de este proyecto es 100% `.github/workflows/build.yml`
(GitHub Actions genera el APK, el usuario lo instala y prueba en tablet).
Auditoría de ese workflow: el job `build` solo tenía un paso
`gradle assembleDebug` y subía `app-debug.apk` como único artefacto (líneas
85-93, versión anterior). **El APK que el usuario prueba en la tablet real
nunca había pasado por R8** -- ni minify ni shrink de recursos -- y además
Compose añade comprobaciones/hooks de tooling propios del modo debug que no
existen en una build real. Esto es, con diferencia, el factor más
determinante del "lag pesado" reportado -- más que cualquier optimización a
nivel de composable individual (Doc/44, Strong Skipping Mode arriba), que
siguen siendo correctas y necesarias pero no bastan por sí solas contra el
coste estructural de una build de debug.

### Corrección real aplicada (no firma de producción, documentado como tal)

- `app/build.gradle.kts`: `release` ahora tiene `isMinifyEnabled = true`,
  `isShrinkResources = true`, y las reglas por defecto de AGP
  (`proguard-android-optimize.txt`) + `app/proguard-rules.pro` propio.
- `app/proguard-rules.pro`: auditado contra el código real del proyecto
  antes de escribir una sola regla -- no hay reflexión (`Class.forName`,
  `::class.java` reflexivo), ni `kotlinx.serialization`/Gson, ni
  `Parcelable` en `app/src/main/java`. El único API "especial" es
  `android.media.midi.*`, consumido de forma normal. Por eso el archivo es
  corto y explica por qué, en vez de copiar reglas de plantilla "por si
  acaso".
- `release.signingConfig = signingConfigs.getByName("debug")` -- firma con
  la config de debug que AGP genera automáticamente (sin ningún keystore ni
  secreto que gestionar), deliberado y documentado en el propio
  `build.gradle.kts`: el objetivo es que el APK de release, ya optimizado,
  se siga pudiendo instalar directo desde el artefacto de CI, igual que el
  debug de siempre. **Esto no es una firma válida para publicar en Play
  Store** -- eso es un paso aparte, con su propio keystore de producción
  gestionado como secreto del repositorio, fuera del alcance de este
  cambio.
- `.github/workflows/build.yml`: el paso de build ahora es
  `gradle assembleRelease`, y el artefacto subido pasa de
  `DwpCreator-debug-apk` (`app-debug.apk`) a `DwpCreator-release-apk`
  (`app-release.apk`). Los tests unitarios siguen corriendo sobre la
  variante debug (`testDebugUnitTest`) -- eso es correcto, es solo
  ejecución de tests, no afecta al APK que se instala.

## Pendiente de verificación real (no se puede hacer desde aquí)

Este entorno no tiene SDK de Android ni red para descargar dependencias
-- no se pudo compilar ni perfilar ningún cambio de este documento. Antes
de dar esto por cerrado, en el próximo push a `main`:

1. Confirmar que `assembleRelease` compila limpio en GitHub Actions con
   R8 activado (un fallo típico si algo se rompe es un `ClassNotFoundException`
   o `NoSuchMethodError` en tiempo de ejecución, no en tiempo de compilación
   -- por eso el paso siguiente es instalar y probar el APK real, no solo
   ver el workflow en verde).
2. Instalar `app-release.apk` (nuevo artefacto) en la tablet y repetir
   exactamente la misma prueba de scroll reportada al inicio de este hilo.
3. Si el lag mejora pero no desaparece del todo, perfilar con
   `adb shell dumpsys gfxinfo com.jvk.dwpcreator` para aislar cuánto queda
   de coste real de composición (Doc/44 + Strong Skipping) frente a
   cualquier otro cuello de botella aún no identificado.
