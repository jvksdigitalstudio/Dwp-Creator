# 47 — Módulo `:baselineprofile`: generación real del Baseline Profile

> **Corrección (Doc/52):** la sección "Por qué queda aislado del pipeline de release" de este documento era inexacta. `gradle assembleRelease` a nivel raíz sí construía `:baselineprofile`. Desde el Doc/52 `build.yml` usa `:app:assembleRelease` y el aislamiento es real.

## Pedido del usuario

Tras confirmar que Kotlin 2.0/K2 (Doc/46) compiló limpio, pidió
explícitamente continuar con "todo lo necesario, nivel top profesional"
para la máxima fluidez -- el ítem que había quedado pendiente y documentado
como tal en el propio Doc/46: el contenido real del Baseline Profile.

## Qué se añadió

- **`:baselineprofile`** (módulo nuevo, `com.android.test` +
  `androidx.baselineprofile`): un test instrumentado de Macrobenchmark
  (`BaselineProfileGenerator.kt`) que instrumenta el APK de **release**
  real de `:app` (mismo build que se distribuye, no debug) en un emulador
  gestionado por Gradle (Gradle Managed Devices, Pixel 6 / API 34 -- sin
  depender de una Action de terceros aparte), y vuelca qué clases/métodos
  se ejecutan en el arranque en frío.
- **`app/build.gradle.kts`**: plugin `androidx.baselineprofile` aplicado,
  dependencia `baselineProfile(project(":baselineprofile"))`, y
  `baselineProfile { automaticGenerationDuringBuild = false }` --
  explícitamente NO ligado a `assembleRelease`.
- **`.github/workflows/baseline-profile.yml`**: workflow nuevo, **separado**
  de `build.yml`, disparado solo a mano (`workflow_dispatch`, nunca en cada
  push a `main`). Corre el emulador, genera el perfil, lo sube como
  artefacto descargable -- exactamente el mismo patrón que ya usas para
  bajar el APK.

## Por qué queda aislado del pipeline de release (decisión deliberada)

`build.yml` (el que genera el APK que instalas y ya confirmaste que anda
bien) **no cambia en absoluto** con este commit -- sigue sin depender de
`:baselineprofile` para nada. Motivo: montar un emulador Android en CI es
la parte más propensa a fallar de todo este proyecto (tiempos de arranque,
disponibilidad de KVM en el runner, versiones exactas de
`androidx.baselineprofile`/`androidx.benchmark` que no se pudieron verificar
contra un compilador real en este entorno sin acceso a red). Si algo de eso
falla, el único afectado es este workflow nuevo -- tu APK de release sigue
saliendo normal desde `build.yml`, sin ningún cambio de comportamiento.

## Alcance real de este primer perfil (honesto, no inflado)

`BaselineProfileGenerator.generate()` cubre el **arranque en frío** de la
app hasta la pantalla vacía (botón "LOAD"). Deliberadamente NO simula tocar
"LOAD" y elegir un `.zip` real -- eso abre el selector de archivos del
sistema (Storage Access Framework), que un test de Macrobenchmark no puede
resolver de forma fiable sin un fixture de prueba dedicado (un instrumento
de ejemplo empaquetado + un atajo "test-only" en la propia app para
saltarse el selector). Ese fixture no se añadió aquí porque tocaría código
de producción con un atajo exclusivo de test que el usuario no pidió
explícitamente -- se deja como una extensión futura aparte, explícita, si
se decide que vale la pena.

Aun con ese alcance, el arranque en frío es una porción real: cubre la
inicialización de la Activity, la composición inicial de Compose y del
`ViewModel` -- clases que se cargan en **cada** apertura de la app,
independientemente de qué instrumento se cargue después. No cubre
específicamente el scroll de la lista de 48 muestras en sí (para eso, la
mejora real ya aplicada es la build de release + R8 del Doc/45, más Strong
Skipping del Doc/45/46).

## Cómo usarlo (paso del usuario, no automático)

1. En GitHub → pestaña **Actions** → workflow **"Generate Baseline
   Profile"** → botón **"Run workflow"** (disparo manual).
2. Al terminar, descargar el artefacto `baseline-prof` del run.
3. Reemplazar el contenido de `app/src/main/baseline-prof.txt` en el repo
   con el archivo descargado, hacer commit.
4. El **siguiente** push normal a `main` (el `build.yml` de siempre) ya
   empaqueta ese perfil real en el próximo APK de release -- sin que
   `build.yml` haya tenido que tocar un emulador en ningún momento.

## Pendiente de verificación real (no se puede hacer desde aquí)

Sin red ni SDK de Android en este entorno, no se pudo:
- Confirmar que las coordenadas exactas (`androidx.baselineprofile:1.3.3`,
  `androidx.benchmark:benchmark-macro-junit4:1.3.3`,
  `androidx.test.uiautomator:uiautomator:2.3.0`, etc.) son las versiones
  vigentes correctas -- son las más recientes que se pudo verificar de
  memoria contra el conocimiento de este proyecto, no contra un resolutor
  de Gradle real.
- Confirmar que el runner `ubuntu-latest` de este repositorio tiene KVM
  disponible (se agregó el paso "Enable KVM", el mecanismo estándar
  documentado por Google/GitHub, pero no se pudo probar aquí).

**Siguiente paso real**: disparar el workflow manualmente una vez. Si
falla, pegar aquí el log completo del job -- con Gradle es normal que un
primer intento de un módulo nuevo así necesite 1-2 ajustes de versión, y
con el log real delante se corrige exacto, no a ciegas.
