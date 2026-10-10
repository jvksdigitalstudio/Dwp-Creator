# 32 — CORRECCIÓN DE CI: XML INVÁLIDO EN `ic_triangle_up.xml` (BUILD FAILED)

**Fecha:** 2026-09-25
**Versión:** `0.4.3-ci-fix-vector-comment`
**Punto de partida:** estado de `Doc/31` (panel del sampler placeholder), primer CI real ejecutado sobre ese cambio.
**Origen:** capturas del CI real (GitHub Actions) proporcionadas por el usuario, con el job `Run unit tests` fallando en `:app:mergeDebugResources` / `:app:parseDebugLocalResources`.

Este documento es exactamente el tipo de corrección que este entorno de trabajo, sin compilador ni CI propio, no puede detectar por revisión de código Kotlin (el archivo roto no es `.kt`) -- solo lo confirma un parser XML real, que es lo que se usó para esta corrección y para verificar que no queda ningún otro caso igual en el árbol.

---

## 1. Causa raíz

El nuevo `res/drawable/ic_triangle_up.xml` (`Doc/31`) llevaba un comentario XML (`<!-- ... -->`) redactado con el mismo estilo que uso en los comentarios Kotlin de todo este proyecto, donde `--` se usa como raya larga tipográfica (p. ej. `"... el color no cambio -- solo su posicion"`). Eso es válido en Kotlin (`//` y `/* */` no tienen esa restricción), pero **la especificación XML 1.0 prohíbe la secuencia `--` dentro del cuerpo de un comentario**, no solo como sus delimitadores de apertura/cierre. El archivo tenía exactamente un `--` de ese tipo en la línea 12, columna 34 -- la posición exacta que reportan las cuatro fases distintas del pipeline de Android que llegaron a tocar ese archivo antes de fallar (todas coinciden en `[12,34]` / `line 12; columnNumber 34`, ver capturas):

1. `aapt2`/`ResourceCompiler.compileXml` (compilación de recursos de la app) -- `ResourceCompilationException`.
2. `ResourceDirectoryParser` (parseo de recursos ya empaquetados, `packageDebugResources`) -- `ResourceDirectoryParseException`.
3. Ambas fases fallan con la misma causa raíz de fondo: `javax.xml.stream.XMLStreamException` / `org.xml.sax.SAXParseException`: `"The string '--' is not permitted within comments."`.

Efecto en cascada visible en el log: `:app:mergeDebugResources FAILED` y `:app:parseDebugLocalResources FAILED` -> `BUILD FAILED` -> el job siguiente (`Summarize test results`) no encuentra reportes JUnit (`No JUnit XML reports found`, porque los tests nunca llegaron a ejecutarse) -> falla también, en cascada, sin ser un problema independiente.

## 2. Corrección

Reescrito el comentario de `ic_triangle_up.xml` sin ninguna secuencia `--` en su cuerpo -- se reemplazó la raya larga por una redacción normal con comas/punto y coma, y la flecha `->` de la descripción del triángulo (`4.5,372.5 -> 494.5,372.5 -> ...`) por la palabra `to`, ya que `->` también empieza por un guion doble parcial en un contexto donde no vale la pena arriesgarse. Se dejó además una nota explícita, dentro del propio comentario, de por qué se evita ese patrón -- **redactada con cuidado especial de no reincidir en el mismo error al explicarlo** (el primer borrador de esta nota reintrodujo un `--` al describir la propia regla, y se corrigió antes de dar el archivo por bueno).

Ningún otro archivo del proyecto tenía el mismo problema: es el único `.xml` nuevo de `Doc/31`. El comentario preexistente de `AndroidManifest.xml` (`Doc/29` §H-10, sobre `largeHeap`) fue revisado y **no** contiene `--` en su cuerpo -- las únicas apariciones de `--` en ese archivo son los propios delimitadores `<!--`/`-->`.

## 3. Verificación realizada en esta pasada (con evidencia, no solo lectura)

A diferencia de las pasadas anteriores (`Doc/25`-`Doc/31`), donde la falta de compilador obligaba a una revisión manual símbolo por símbolo, **este error sí se puede verificar de forma determinista sin Gradle ni Android SDK**: la validez sintáctica de un XML es verificable con cualquier parser XML estándar, y así se hizo:

```
find app -name "*.xml" -> parseado con xml.etree.ElementTree (Python), uno por uno
```

Resultado, los 5 archivos `.xml` de todo `app/src/main`:

| Archivo | Resultado |
|---|---|
| `res/values/strings.xml` | OK |
| `res/values/themes.xml` | OK |
| `res/drawable/ic_triangle_up.xml` | OK (antes: `FAIL`, columna 12:34, reproducido localmente con el mismo mensaje que el CI) |
| `res/drawable/ic_launcher.xml` | OK |
| `AndroidManifest.xml` | OK |

Se reprodujo primero el fallo exacto del CI en este entorno (el mismo parser Python arrojaba el mismo error de "--" en comentario antes de la corrección, en la misma línea/columna que reportaba `aapt2`), y luego se confirmó que, tras el cambio, el archivo pasa sin excepción -- no es una inspección visual, es la misma clase de comprobación (parseo XML real) que ejecuta `aapt2` internamente.

**No realizado:** no hay forma de ejecutar `aapt2`/Gradle en este entorno (sigue aplicando la limitación de `Doc/25` §0), así que la confirmación definitiva de que `:app:mergeDebugResources` pasa sigue siendo el próximo run de CI. Lo que sí queda descartado con evidencia, no solo con lectura, es que el XML en sí sea sintácticamente inválido -- que era la causa raíz exacta reportada en las cuatro trazas de las capturas.

## 4. Por qué esto no afecta a nada más de `Doc/31`

El resto del panel del sampler (`EffectsPanel.kt`, la integración en `MainScreen.kt`, el propio contenido del `<path>` del vector) no cambia: la geometría del triángulo (`M4.5,372.5 L494.5,372.5 L249.5,127.5 Z`), su `viewportWidth`/`viewportHeight`, y el uso del ícono desde `EffectsPanel.kt` (`painterResource(id = R.drawable.ic_triangle_up)`) quedan exactamente iguales a como se entregaron en `Doc/31`. El único contenido que cambió es texto dentro de un comentario, que no forma parte del recurso compilado.

## 5. Archivos tocados

Solo `res/drawable/ic_triangle_up.xml` (comentario reescrito). Ningún archivo de código Kotlin, ningún test, ninguna otra parte de `Doc/31` se tocó.

**Conteo de tests: sin cambios (244).** Este era, literalmente, el primer intento de compilar el trabajo de `Doc/31` -- por eso la corrección llega antes de cualquier resultado de test real (los tests de la propia versión anterior también fallaron en cascada por el mismo motivo, no por una regresión de lógica).
