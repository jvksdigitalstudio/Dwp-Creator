# 69 — Benchmark de arranque: sin perfil vs con Baseline Profile

**Estado: medido (Startup Benchmark #2, 10-oct, commit d4118de, verde). Resultado: NO se demuestra mejora del arranque con el Baseline Profile en este emulador.** Detalle en "Resultados".
Pendiente que cerraba Doc/68 ("no se midió cuánto mejora el arranque").

## Qué mide
`StartupBenchmark` (módulo `:baselineprofile`) arranca en frío la app **10 veces por modo** en tres estados de
compilación de ART, sobre la misma variante `benchmarkRelease` (release + R8 + `profileable` + `baseline-prof.txt` real):

| Modo | `CompilationMode` | Qué representa |
|---|---|---|
| Sin perfil | `None()` | primer arranque tras instalar, sin perfil (sólo intérprete/JIT) |
| Con Baseline Profile | `Partial(BaselineProfileMode.Require)` | arranque con el perfil aplicado |
| Compilado completo | `Full()` | techo de referencia (todo AOT) |

`Require` hace fallar el test si el perfil no está instalado: así no se puede obtener, por error, una comparación
"con perfil" que en realidad no lo lleva. Métricas: `timeToInitialDisplayMs` y, como `MainActivity` llama a
`reportFullyDrawn()`, `timeToFullDisplayMs`.

## Cómo ejecutarlo (después de que Build APK pase en verde)
GitHub -> Actions -> **Startup Benchmark** -> Run workflow (Branch: main). Manual (`workflow_dispatch`), aislado:
no forma parte de Build APK. Dura unos 10-20 min. El resultado aparece en el **resumen de la corrida** (tabla con
min / mediana / max y % frente a "sin perfil") y los JSON crudos en el artefacto `startup-benchmark`.

## Cómo leer el resultado (límites)
- Se mide en un **emulador x86_64 de CI**, no en un teléfono ARM: las cifras absolutas **no** son las de un dispositivo.
  Vale la **diferencia relativa** entre modos, medida en las mismas condiciones. Por eso el workflow pasa
  `androidx.benchmark.suppressErrors=EMULATOR,LOW-BATTERY` (Macrobenchmark se niega a correr en emulador sin él).
- El perfil sólo cubre el **arranque hasta la pantalla vacía**; la mejora medida es la del arranque, no la de tocar notas.
- 10 iteraciones dan una mediana razonable pero con ruido de emulador compartido; si "con perfil" sale dentro del ruido
  de "sin perfil", la conclusión honesta es "mejora no demostrable aquí", no "mejora".

## Corrida #1 — qué pasó y qué se corrigió
**Funcionó:** la tarea `:baselineprofile:connectedBenchmarkReleaseAndroidTest` existe; el emulador API 30 arrancó; Gradle compiló
la variante `benchmarkRelease` (R8 + `mergeBenchmarkReleaseArtProfile`, es decir, con el perfil) y ejecutó "Starting 3 tests".
El argumento `suppressErrors=EMULATOR,LOW-BATTERY` fue efectivo (el fallo llegó después de la comprobación de emulador).
Además, `startupWithBaselineProfile` usa `BaselineProfileMode.Require`, que se verifica **antes** de las iteraciones: no se quejó,
así que el perfil muy probablemente se instaló bien.

**Falló** (los 3 modos, idéntico): `IllegalStateException: No RT frame slice associated with UI thread frame slice ends after
reportFullyDrawn` en `StartupTimingQuery.findEndRenderTimeForUiFrame`.

**Causa (en la app, no en el workflow):** `MainActivity` llamaba a `reportFullyDrawn()` en un `LaunchedEffect(Unit)`, que corre *entre*
frames. Macrobenchmark toma el siguiente frame de UI que termina después del aviso y exige que tenga su par de dibujo en el hilo de
render; en una pantalla estática ese frame no dibujaba nada. No depende del modo de compilación (por eso fallaron los tres igual).

**Corrección:** `reportFullyDrawnOnFirstDraw()` registra un `OnDrawListener` de un solo uso y llama a `reportFullyDrawn()` desde dentro de
`onDraw()` del primer frame dibujado (se retira en el mensaje siguiente: Android prohíbe quitarlo dentro de `onDraw`). Se conserva el
aviso (Doc/60-61: lo necesita la generación del perfil y Android Vitals). Efecto colateral aceptado: `timeToFullDisplayMs` pasa a ser
prácticamente igual a `timeToInitialDisplayMs` (la app no tiene carga asíncrona posterior que medir); el valor útil del benchmark es TTID.

**Ruido del log que NO es la causa:** `adb: device offline` (sondeo de arranque del emulador), `Unable to connect to adb daemon on port
5037`, `Failed to start Emulator console`, `stop: Not implemented`, avisos de Vulkan/libX11. Y el aviso de que `suppressErrors` por línea
de comandos no es compatible con la caché de configuración: es un aviso; si en algún momento dejara de aplicarse, el síntoma sería un
fallo explícito "EMULATOR" al inicio, y la solución sería moverlo a `testInstrumentationRunnerArguments` en el DSL de Gradle.

## Resultados (corrida #2, emulador x86_64 API 30 de CI, 10 arranques en frío por modo)
Tiempo hasta el primer frame (TTID), ms:

| Modo | min | mediana | max | vs sin perfil |
|---|---:|---:|---:|---:|
| Sin perfil (`CompilationMode.None`) | 791 | 852 | 973 | - |
| Con Baseline Profile (`Partial/Require`) | 806 | 824 | 880 | -3,3 % |
| Compilado completo (techo) | 779 | 850 | 901 | -0,2 % |

`timeToFullDisplayMs` salió idéntico a TTID (esperado tras mover `reportFullyDrawn` al primer frame dibujado).

**Interpretación honesta:**
- Los tres rangos (min-max) se solapan casi por completo y la dispersión de un mismo modo (~90-180 ms) es mucho mayor que la
  diferencia de medianas (28 ms). **-3,3 % está dentro del ruido**: no se puede afirmar mejora. Tampoco se puede afirmar lo contrario.
- La pista más fuerte es el techo: con **toda** la app compilada AOT la mediana sólo bajó 0,2 %. Si compilarlo todo no cambia nada, el
  arranque en este entorno **no está limitado por la compilación** (intérprete/JIT), que es lo único que un Baseline Profile puede mejorar.
  El tiempo (~850 ms) lo dominan otras cosas: creación del proceso, inicialización de Compose/tema y el emulador (gráficos por software).
- Límite de la medición: un emulador x86_64 de CI (CPU y almacenamiento rápidos, otra arquitectura) no es un teléfono/tablet ARM. En
  hardware real modesto la compilación suele pesar más, pero **no hay dato propio que lo respalde** y no debe asumirse.

**Decisión:** se conserva el perfil (coste nulo: textual, ~1 MB en el repo, binario pequeño en el APK; sin efecto negativo medible) pero
**no se vende como mejora de rendimiento**. Para saber el efecto real habría que repetir este mismo benchmark con la tablet conectada por
adb desde un PC (`:baselineprofile:connectedBenchmarkReleaseAndroidTest` con el dispositivo, mismos argumentos); no es posible desde CI.
Más iteraciones en el emulador no aportarían: el techo (0,2 %) indica que el efecto máximo posible aquí es despreciable.

## Verificación realizada y pendiente
- Hecho: el script de resumen del workflow se **ejecutó** contra JSON sintético con el formato real de Macrobenchmark
  (tabla correcta; caso sin datos avisa sin fallar). Los números de esa prueba eran inventados: sólo validan el script.
- Hecho: la infraestructura del benchmark se verificó con la corrida #1 (ver arriba).
- Hecho: corrida #2 verde con `reportFullyDrawn` corregido (la causa del fallo de la #1 era de la app y quedó confirmada).
- Pendiente (opcional, fuera de CI): medir en hardware real si se quiere saber el efecto en la tablet.
