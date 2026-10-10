# 22 — Core Stabilization — Final Verification

Este documento registra la fase de **cierre y verificación del Core**, ejecutada
sobre el código real del proyecto tal como estaba tras
`Doc/21_CORE_STABILIZATION_PASS3.md`. No se implementó ninguna funcionalidad
nueva (Monolithic DWP, FLAC, nuevos formatos) — ver Sección 14.

## 1. Estado del código

Inspección directa (no basada en documentación previa) de: 31 archivos Kotlin
en `app/src/main`, 5 en `app/src/test`, `app/build.gradle.kts`,
`settings.gradle.kts`, `.github/workflows/build.yml`, los 21 documentos
existentes en `Doc/`, y `README.md`. Namespace/`applicationId`
(`com.jvk.dwpcreator`) verificado consistente contra la ruta real de carpeta
de cada uno de los 36 archivos `.kt` — sin excepciones.

## 2. Tests detectados

**125 métodos `@Test`**, verificado con `grep -c "@Test"` archivo por archivo,
no con una cifra heredada de documentación previa:

| Archivo | Tests |
|---|---|
| `DwpEngineTest.kt` | 48 |
| `WavDecoderTest.kt` | 28 |
| `MidiMessageParserTest.kt` | 29 |
| `ZipProjectIoTest.kt` | 14 |
| `PcmConverterTest.kt` | 6 |
| **Total** | **125** |

Coincide exactamente con `Doc/18_CANONICAL_STATE.md` §E.

## 3. Tests ejecutados

**Ninguno, en este entorno de trabajo.** Este contenedor no tiene acceso de
red habilitado (confirmado: `services.gradle.org` no está en la lista
blanca de salida) ni Android SDK instalado. `gradle testDebugUnitTest`
requiere descargar el propio Gradle, plugins de Android/Kotlin, dependencias
de Maven/Google, y componentes del SDK (`platforms;android-34`,
`build-tools;34.0.0`) — nada de eso es alcanzable desde aquí.

Esta es la misma limitación ya declarada honestamente en `Doc/10_BUILD_AUDIT.md`,
`Doc/20_CORE_STABILIZATION_PASS2.md` y `Doc/21_CORE_STABILIZATION_PASS3.md`.
No cambió en esta fase. La ejecución real solo puede ocurrir en GitHub
Actions (que sí tiene red y SDK), motivo por el cual esta fase invirtió
esfuerzo en mejorar el workflow para que el resultado real de esa ejecución
quede explícito en el log (ver Sección 6).

## 4. Tests exitosos

**No determinable en este entorno** — ver Sección 3. No inventado.

## 5. Tests fallidos

**No determinable en este entorno** — ver Sección 3. No inventado.

## 6. Resultado Gradle (`testDebugUnitTest`)

**No ejecutado aquí.** Lo que sí se hizo: `.github/workflows/build.yml` fue
modificado para agregar el step **"Summarize test results (from real JUnit
XML reports)"**, que corre siempre (`if: always()`, incluso si los tests
fallan) inmediatamente después de `gradle testDebugUnitTest`. Este step:

- Parsea los archivos XML reales de JUnit en
  `app/build/test-results/testDebugUnitTest/*.xml` (no HTML, no un resumen de
  Gradle) con `xml.etree.ElementTree` de Python 3 (ya presente en los runners
  `ubuntu-latest`).
- Suma los atributos reales `tests`/`failures`/`errors`/`skipped` de cada
  `<testsuite>`.
- Imprime explícitamente: `Tests detected`, `Tests executed`, `Tests passed`,
  `Tests failed`, `Tests skipped`, y `BUILD SUCCESSFUL (tests)` /
  `BUILD FAILED (tests)`.
- Si no se encuentra ningún XML (p. ej. el build falló antes de generarlos),
  lo reporta como error explícito en vez de mostrar ceros engañosos.

Este script fue probado localmente contra reportes JUnit XML sintéticos
(125/1 fallo/1 omitido) para confirmar que la aritmética es correcta antes de
confiarlo a CI — ver evidencia de esa prueba en el historial de esta
conversación. **No se hardcodeó "125" en ningún lado**: el número sale de
contar los `<testsuite>` reales que Gradle genere en la ejecución real.

**Próximo paso pendiente para ti:** hacer push y revisar el log de Actions;
ese log (real) es la única fuente válida para llenar las Secciones 3-6 con
números definitivos.

## 7. Resultado `assembleDebug`

**No ejecutado aquí**, misma limitación de entorno. Sin cambios al step de
`assembleDebug` en el workflow — sigue corriendo solo si el step de tests no
falla (comportamiento heredado, sin modificar).

## 8. Warnings encontrados

A) `MidiInputManager.kt` — el getter deprecado real es
`MidiManager.getDevices()` (propiedad Kotlin `manager.devices`), deprecada
por Android en API 30 a favor de `getDevicesForTransport(int)`. Usada dos
veces: en `listDevices()` y en `connectTo()`.

B) `Theme.kt` — parámetro `darkTheme: Boolean = isSystemInDarkTheme()`
declarado pero nunca leído dentro de la función. Causa raíz confirmada:
la app define un único `ColorScheme` fijo (`DwpColorScheme`, oscuro/neón) sin
variante clara — es andamiaje sobrante de la plantilla estándar de Compose,
no una funcionalidad a medio implementar. Confirmado también que ningún
caller (`MainActivity.kt`) pasa ese parámetro.

## 9. Warnings corregidos

**Ambos, con causa raíz corregida, no silenciada:**

- **A)** Se añadió `MidiManager.allDevices()`, un helper privado con gate de
  versión: usa `getDevicesForTransport(TRANSPORT_MIDI_BYTE_STREAM)` desde API
  30 en adelante, y cae al `devices` deprecado solo por debajo de API 30
  (el `minSdk` del proyecto es 26, y no existe otra API disponible ahí — no
  hay a qué migrar en ese rango). El `@Suppress("DEPRECATION")` se aplica
  **únicamente** a esa rama de fallback, con comentario KDoc explicando por
  qué es necesario y por qué no es una supresión general.
- **B)** Se eliminó el parámetro `darkTheme` (y el import ahora no usado de
  `isSystemInDarkTheme`) de `DwpCreatorTheme(...)`, con un comentario KDoc
  dejando constancia de que se retiró en vez de conectarse, y por qué (no
  hay un segundo esquema de color al que alternar).

## 10. Warnings restantes y justificación

Ninguno de los dos warnings originales debería seguir apareciendo. No se
investigaron ni corrigieron otros warnings no mencionados explícitamente en
el prompt (por ejemplo, cualquier otro `NO-SOURCE`/deprecación de terceros
que Gradle pudiera reportar) — eso solo puede confirmarse con una ejecución
real (Sección 3).

## 11. Estado de documentación

Se auditaron los 21 documentos de `Doc/` más `README.md` contra el código
real, buscando específicamente cifras de tests obsoletas y afirmaciones que
contradijeran el código actual (no solo cifras desactualizadas):

- **`Doc/09_TEST_AUDIT.md`**: describía el estado de Fase 1 (32 tests,
  `BUG-02` presentado como brecha de cobertura abierta) sin haberse
  actualizado en Fase 2/Pass 2/Pass 3. Corregido: se le añadió una nota de
  vigencia explícita (mismo patrón que ya usaba `Doc/01_PROJECT_STRUCTURE.md`)
  señalando el conteo real actual (125/5) y que `BUG-02` fue cerrado en Fase 2
  (`Doc/13_RISK_REGISTER.md` R-09). **No se reescribió** la tabla histórica
  de 32 tests de Fase 1 — sigue siendo un registro histórico válido, solo ya
  no es la fuente vigente.
- **`Doc/03_CODE_INVENTORY.md`**: quedó un paso atrás (snapshot de Pass 2:
  97 tests, `WavDecoder` 21, `MidiMessageParser` 18) respecto al código
  actual tras Pass 3. Corregido a 125/28/29 respectivamente. Además —el
  hallazgo más importante de esta auditoría de documentación— **describía
  como bug activo algo que ya no existe en el código**: afirmaba que
  `MainActivity.kt` no conectaba `onSampleRename`/`onSampleDelete` y que
  `SampleRow.kt` ofrecía opciones "Renombrar"/"Eliminar" rotas. Verificado
  contra el código real: esos callbacks y esas opciones de menú **ya no
  existen** — fueron retirados al mitigar `BUG-01` en Fase 1 (consistente con
  `Doc/12_KNOWN_ISSUES.md`, que si reflejaba correctamente ese cierre).
  Corregidas las 3 filas correspondientes de **BUG/PLACEHOLDER** a **KEEP**,
  con nota explícita de la corrección para no borrar el rastro del error.
- **`README.md`**: apuntaba a `Doc/09_TEST_AUDIT.md` (desactualizado) como
  fuente del resultado de tests del workflow. Corregido para apuntar a
  `Doc/18_CANONICAL_STATE.md` §E (vigente) y para describir el nuevo resumen
  explícito que imprime el workflow.
- **`Doc/18_CANONICAL_STATE.md`, `Doc/12_KNOWN_ISSUES.md`,
  `Doc/13_RISK_REGISTER.md`, `Doc/21_CORE_STABILIZATION_PASS3.md`**:
  verificados contra el código real y encontrados **ya correctos** — no
  requirieron cambios. En particular, el desglose de tests de
  `18_CANONICAL_STATE.md` §E coincide exactamente con el conteo fresco de
  esta pasada, y R-02/R-08 siguen correctamente documentados como abiertos.
- El resto de documentos históricos (`00`–`08`, `10`, `14`–`17`, `19`, `20`)
  no contenían afirmaciones que contradijeran el código actual más allá de
  ser, correctamente, snapshots de fases anteriores ya superadas — no se
  tocaron.

## 12. Estado del fixture `Instrument.dwp`

**Byte-idéntico, confirmado por SHA-256 antes y después de todos los
cambios de esta fase:**

```
d7699c3cafdd4680c3b9e854c6fc961b910652cae8b28822ee5354c230941925
```

No se abrió, modificó ni tocó ese archivo en ningún paso de esta fase (los
cambios fueron exclusivamente en `MidiInputManager.kt`, `Theme.kt`,
`.github/workflows/build.yml`, y tres documentos de `Doc/`/`README.md`).

## 13. Riesgos abiertos

Sin cambios respecto a `Doc/13_RISK_REGISTER.md`:

- **RISK-02 / R-02**: offsets de `0x01f7` verificados solo contra muestras
  32-bit float, sin evidencia para 16-bit PCM. Requiere un segundo `.dwp` de
  referencia real — no resoluble solo con código ni con esta fase.
- **R-08**: sesgo de fixture único (`Instrument.dwp` no varía internamente en
  los 10 campos de `0x01f7`). Bloqueante para investigación futura de
  formato, no para el Core en sí.

Ambos permanecen deliberadamente **ABIERTOS** — no hay evidencia técnica
nueva en esta fase que permita cerrarlos.

## 14. Funcionalidades deliberadamente NO implementadas

Confirmado por búsqueda exhaustiva en `app/src/main` (0 coincidencias) y por
no haber tocado ningún archivo relacionado en esta fase:

- Monolithic DWP — **NOT IMPLEMENTED**
- Audio embebido — **NOT IMPLEMENTED**
- FLAC (encoder/decoder) — **NOT IMPLEMENTED**
- `0x0205` / `0x0206` — **sin código, sin evidencia**
- Generación de un `.dwp` completo desde cero — **NOT IMPLEMENTED**
- Nuevas estructuras DWP de cualquier tipo — **NOT IMPLEMENTED**

## 15. Conclusión técnica

El Core **no cambió de comportamiento observable** en esta fase más que en
los dos puntos de warnings corregidos (ambos con fix real, no supresión
ciega, y ambos limitados a una función auxiliar de `MidiInputManager.kt` y a
la firma pública de `DwpCreatorTheme`, ninguno toca el motor DWP/WAV/MIDI ni
la lógica de negocio). El trabajo principal de esta fase fue: (a) hacer el
resultado de tests explícito y verificable en CI en vez de implícito en la
salida cruda de Gradle, y (b) corregir documentación que se había quedado
desactualizada respecto al código real, incluyendo un caso concreto donde un
documento describía un bug ya corregido como si siguiera activo.

**No se declara "CORE STABLE"**, por la misma razón ya establecida en fases
anteriores y que sigue aplicando sin cambios: **ningún test se ha ejecutado
realmente** en un entorno con Android SDK/Gradle — este entorno de trabajo no
tiene red ni SDK, y afirmar estabilidad basándose solo en lectura de código y
conteo estático de tests sería precisamente el tipo de afirmación no
verificada que este proceso prohíbe explícitamente.

## 16. Addendum — corrección de un error introducido por esta misma fase

El primer intento del fix de §9-A (warning de `MidiManager.getDevices()`
deprecado) **no compilaba**. Se detectó con el log real del run #23 en
GitHub Actions (`build.yml`, step "Run unit tests"), compartido por el
usuario:

```
e: .../MidiInputManager.kt:63:61 Type mismatch: inferred type is
(Mutable)Set<MidiDeviceInfo!> but Array<MidiDeviceInfo> was expected
```

**Causa:** se asumió que `MidiManager.getDevicesForTransport(Int)` devuelve
`Array<MidiDeviceInfo>` como el `getDevices()` deprecado que reemplaza. El
compilador real (evidencia del log, no memoria/documentación) confirma que
devuelve `Set<MidiDeviceInfo>` — tipos distintos, de ahí el `Type mismatch`
en la rama `if`.

**Corrección:** `allDevices()` ahora devuelve `List<MidiDeviceInfo>`,
normalizando ambas ramas con `.toList()` (`Set<T>.toList()` y
`Array<T>.toList()` son ambos extensiones estándar de Kotlin). Todos los
consumidores (`listDevices()`, `connectTo()`) ya operaban sobre el resultado
con `.filter`/`.map`/`.firstOrNull`, que funcionan igual sobre `List` — sin
más cambios necesarios.

Este error nunca llegó a afectar tests ni fixture: el build falló en
`compileDebugKotlin`, antes de que `compileDebugUnitTestKotlin` pudiera
siquiera empezar. Por eso el step completo tomó solo 24s (contra ~1m29s en
corridas donde sí se llega a compilar y ejecutar los 125 tests) — la
"Summarize test results" añadida en esta misma fase detectó correctamente
que no había reportes JUnit que resumir y falló explícito
(`BUILD FAILED (no test reports produced)`) en vez de mostrar ceros
engañosos, cumpliendo su propósito incluso ante este error.

Pendiente: nueva corrida real de CI para confirmar que este segundo intento
sí compila y que los 125 tests se ejecutan.

## 17. Estado oficial

🟡 **CORE NOT YET VERIFIED** — sin cambios respecto a la conclusión de §15.
Se mantiene hasta confirmar, con el log real de GitHub Actions, que el build
compila y que los 125 tests se ejecutan y pasan.

## 18. Confirmación con evidencia real de CI (run #24, GitHub Actions)

Tras el fix de §16, el usuario compartió los logs reales de la corrida
siguiente. Reemplaza las Secciones 3-7 (antes "no determinable en este
entorno") con datos reales:

- **Run**: "Migración de identidad: Dwp Creator #24" -- **succeeded** en 2m 21s.
- **`gradle testDebugUnitTest`**: `BUILD SUCCESSFUL in 38s`, `23 actionable
  tasks: 23 executed`. Log completo revisado línea por línea (líneas 14-46,
  sin cortes) -- **cero líneas `w:`**, es decir, cero warnings del
  compilador de Kotlin. Los 2 warnings que sí muestra el panel
  "Annotations" de esta corrida (`Node.js 20 deprecated` en el action
  runner, `setup-java v4 deprecated`) son de infraestructura de GitHub
  Actions, no del código -- no relacionados con `MidiDeviceInfo`/`darkTheme`.
- **Step "Summarize test results" (parseando los XML JUnit reales)**:
  ```
  Test report files parsed: 5
  Tests detected: 125
  Tests executed: 125
  Tests passed: 125
  Tests failed: 0
  Tests skipped: 0
  BUILD SUCCESSFUL (tests)
  ```
- **`gradle assembleDebug`**: step "Build debug APK" completado en 14s,
  artefacto subido en "Upload APK artifact" (3s).

### Secciones actualizadas con datos reales

- **§3 Tests ejecutados**: 125 (real, confirmado por CI)
- **§4 Tests exitosos**: 125 (real, confirmado por CI)
- **§5 Tests fallidos**: 0 (real, confirmado por CI)
- **§6 Resultado Gradle**: `BUILD SUCCESSFUL` (real, confirmado por CI)
- **§7 Resultado `assembleDebug`**: exitoso, APK generado y subido (real,
  confirmado por CI)
- **§10 Warnings restantes**: ninguno de los 2 warnings originales del
  compilador Kotlin. (Sí quedan 2 warnings de infraestructura de CI --
  versión de Node.js/`setup-java` de las GitHub Actions usadas -- fuera del
  alcance de esta fase, que era sobre el Core, no sobre el pipeline de CI en
  sí; quedan anotados aquí para no ocultarlos, no para ignorarlos
  permanentemente)

## 19. Estado oficial (revisado)

🟢 **CORE VERIFIED** -- con evidencia real de CI, no por inspección estática:
125/125 tests pasando, `assembleDebug` exitoso, 0 warnings del compilador
Kotlin, fixture `Instrument.dwp` byte-idéntico durante toda la fase. RISK-02
y R-08 permanecen deliberadamente abiertos (no son parte de lo que esta
fase podía cerrar). Monolithic DWP y FLAC confirmados NO implementados, como
exigía el alcance de esta fase.
