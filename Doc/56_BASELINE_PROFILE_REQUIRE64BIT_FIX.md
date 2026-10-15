# 56 — Baseline Profile: cuelgue dentro de Gradle -> require64Bit

## Evidencia real (run #9)
- "Ensure required Android SDK packages": **50s** (antes 4s) -- confirma que
  la instalación explícita de la imagen ATD del Doc/55 sí hizo trabajo real y
  no fue la causa del cuelgue anterior.
- "Generate Baseline Profile": 19m 3s hasta el corte por timeout (20 min,
  Doc/55). El log visible solo llega a tareas normales de compilación de
  `:app` (líneas 1-38) -- el cuelgue ocurre más adelante, probablemente al
  intentar arrancar el propio emulador gestionado por Gradle.

## Causa probable
Las imágenes ATD (`aosp-atd`) son de 64 bits. El `ManagedVirtualDevice` no
tenía `require64Bit = true`; sin esa bandera, Gradle puede intentar resolver
una variante de la imagen que no existe para esa combinación exacta de
API/arquitectura y quedarse esperando en vez de fallar con un error claro.

## Corrección
`baselineprofile/build.gradle.kts`: `require64Bit = true` en la definición
del dispositivo. `timeout-minutes` baja de 20 a 15 (ya se sabe que un run
sano tarda 3-4 minutos en total).

## Honestidad sobre el estado de esto
Van cuatro cambios seguidos sobre el mismo módulo sin poder verificar ninguno
antes de que el usuario lo compile -- cada vuelta cuesta 15-45 minutos de su
tiempo real. Si este cambio tampoco resuelve el cuelgue, la recomendación
pasa a ser volver a la imagen `aosp` (API 34) sin ATD -- esa SÍ llega a
ejecutar el test en ~3 minutos con un error concreto y diagnosticable
(Doc/54), en vez de colgarse sin dar información. Un fallo rápido y claro es
mejor que un cuelgue silencioso, aunque signifique no resolver el error de
`amStartAndWait` en este entorno de CI concreto.

## No afecta
El APK de `Build APK` sigue sin depender de este módulo.
