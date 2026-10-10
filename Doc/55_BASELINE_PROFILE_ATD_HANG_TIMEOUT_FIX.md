# 55 — Baseline Profile: el run #8 se colgó 45 minutos, no falló limpio

## Evidencia real
Run #8: `Status: Cancelled`, `The job has exceeded the maximum execution time
of 45m0s`. El log de la tarea "Generate Baseline Profile" muestra las tareas
normales de compilación de Gradle avanzando con normalidad (hasta
`:baselineprofile:mergeNonMinifiedReleaseNativeLibs` y siguientes) -- no hay
ningún error, ninguna excepción, solo silencio hasta el corte por timeout.

## Diferencia con el run anterior (Doc/54)
El run #7 (imagen `aosp`, API 34) SÍ terminó rápido (3m 9s) con un error
concreto. El único cambio entre el #7 y el #8 fue pasar a la imagen `aosp-atd`
(API 30). La sospecha real, no confirmada porque este entorno no tiene forma
de reproducirlo: Gradle Managed Devices descarga la imagen de sistema por su
cuenta, dentro de la propia tarea de Gradle -- si esa descarga necesita
aceptar una licencia que el paso "Ensure required Android SDK packages" no
cubría para ESE paquete nuevo, se queda esperando una respuesta que nunca
llega, en silencio, dentro del log de Gradle.

## Corrección
- `baseline-profile.yml`: se instala explícitamente
  `system-images;android-30;aosp_atd;x86_64` en el paso "Ensure required
  Android SDK packages" (con licencia pre-aceptada ahí, en un paso con su
  propio log visible), en vez de dejar que Gradle la pida por su cuenta sin
  que se vea nada.
- Se agrega un listado (`sdkmanager --list | grep atd`) justo antes, por si
  el identificador exacto de la imagen no es el correcto -- así la salida
  del próximo run trae la lista real de imágenes ATD disponibles en vez de
  tener que adivinar otra vez.
- `timeout-minutes` baja de 45 a 20: de sobra para un arranque de emulador +
  test normal (las corridas que sí funcionaron tardaron 3-4 minutos en
  total). Si algo se cuelga de nuevo, se corta y se ve en 20 minutos, no en 45.

## No afecta
El APK de `Build APK` sigue sin depender de este módulo.

## Siguiente paso
Disparar el workflow de nuevo. Si el paso "Ensure required Android SDK
packages" falla ahí mismo (en vez de colgarse), el error va a decir
exactamente qué identificador de imagen es el correcto -- mandar esa captura.
