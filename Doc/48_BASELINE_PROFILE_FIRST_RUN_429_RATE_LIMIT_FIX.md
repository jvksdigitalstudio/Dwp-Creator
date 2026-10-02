# 48 — Primer intento real del workflow de Baseline Profile: 429 de Maven Central, no una versión mal adivinada

## Qué pasó (log real, no supuesto)

Usuario disparó "Generate Baseline Profile" manualmente (Doc/47). Falló en
`:baselineprofile:checkTestedAppObfuscationNonMinifiedRelease` /
`:baselineprofile:nonMinifiedReleaseCompileClasspath` -- **no** por una
coordenada de dependencia que no existe, sino por
`Received status code 429 from server: Too Many Requests` al descargar de
`repo.maven.apache.org`, repetido para varias dependencias transitivas
distintas: `org.hamcrest:hamcrest-library:1.3`,
`org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.1`,
`org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.4`,
`com.squareup.wire:wire-runtime:4.9.7`.

El Doc/47 ya avisaba que las versiones exactas de este módulo nuevo no se
pudieron verificar contra un resolutor real sin red en este entorno -- pero
el log confirma que ese no fue el problema esta vez: Maven Central le negó
temporalmente la descarga al runner de GitHub Actions (429 = límite de
peticiones, no 404 "no existe"), algo esperable la primera vez que un
módulo nuevo resuelve TODO su árbol de dependencias de golpe sin nada en
caché todavía.

## Corrección real aplicada

`gradle.properties` (raíz): dos propiedades de sistema que son la
mitigación oficial de Gradle documentada para este código de error exacto
-- `systemProp.org.gradle.internal.repository.max.tentatives=10` y
`systemProp.org.gradle.internal.repository.initial.backoff=500`. Con esto,
un 429 puntual ya no tumba el build a la primera: Gradle reintenta la
descarga hasta 10 veces con espera progresiva entre intentos.

### Por qué no es un parche
No se cambió ninguna versión de dependencia a ciegas para "hacer
desaparecer" el error -- el log deja claro que las coordenadas en sí no son
el problema (el servidor respondió 429, no "not found"). Cambiar versiones
sin ese diagnóstico habría sido adivinar sobre un síntoma equivocado.

## Siguiente paso real

Disparar el workflow de nuevo (Actions -> "Generate Baseline Profile" ->
"Run workflow", o el botón "Re-run jobs" de la corrida fallida). Con el
reintento automático ya en `gradle.properties`, un 429 puntual durante la
descarga ya no debería tumbar el build. Si vuelve a fallar, pegar el log
completo otra vez -- si esta vez el error es distinto (por ejemplo, una
coordenada que sí "no existe"), recién ahí corresponde ajustar una versión
concreta, con el error real delante, no a ciegas.
