# 57 — Baseline Profile: se abandona la imagen ATD, vuelta a "aosp" + animaciones en 0

## Historial real de los 4 intentos con ATD
- Run #7 (imagen `aosp`, API 34, sin ATD): termina en **3m 9s** con un error
  real y diagnosticable (`Unable to confirm activity launch completion`,
  Doc/54).
- Run #8 (`aosp-atd`, API 30): **colgado 45m** hasta el límite (Doc/55).
- Run #9 (`aosp-atd` + instalación explícita de la imagen, límite 20m):
  **colgado 20m** -- la instalación de la imagen sí funcionó (50s, trabajo
  real), pero el cuelgue seguía más adelante (Doc/56).
- Run #10 (`aosp-atd` + `require64Bit = true`, límite 15m): **colgado 15m**
  otra vez, sin ningún cambio de comportamiento (este documento).

Tres cuelgues seguidos sin un solo mensaje de error concreto, cada uno
después de un cambio distinto y razonado. En este entorno de CI, la imagen
ATD para Gradle Managed Devices simplemente no funciona de forma fiable --
no se pudo determinar la causa exacta porque nunca produjo un error, solo
silencio hasta el timeout.

## Decisión
Se abandona el camino ATD. `baselineprofile/build.gradle.kts` vuelve a la
imagen `aosp` en API 34 (la configuración del run #7, la única que alguna
vez completó). Un fallo rápido (3 min) con una causa real es objetivamente
mejor que un cuelgue sin ninguna pista, así se tarde más en resolver la
causa real.

## Ataque directo al error real
En vez de seguir cambiando la imagen del emulador, se ataca el error que
run #7 sí reportó (`amStartAndWait` / `Unable to confirm activity launch
completion`) con su mitigación documentada real:
`BaselineProfileGenerator.kt` desactiva las animaciones del sistema
(`window_animation_scale`, `transition_animation_scale`,
`animator_duration_scale` en 0, vía `device.executeShellCommand`) antes de
`startActivityAndWait()`. Sin esas animaciones, la app termina de
componerse sin la transición de apertura que a veces le impide a
Macrobenchmark confirmar el fin del arranque leyendo `dumpsys gfxinfo`.

## Otros cambios
- `baseline-profile.yml`: se retira el paso que instalaba la imagen ATD
  (ya no aplica) y `timeout-minutes` baja a 10 (generoso para una corrida
  que, cuando funciona, tarda 3-4 minutos).

## No afecta
El APK de `Build APK` sigue sin depender de este módulo.

## Siguiente paso
Disparar el workflow. Si termina rápido (con o sin éxito), es la primera
señal real en 3 intentos. Si falla, ahora sí debería dar un mensaje
concreto -- no otro cuelgue -- y se corrige sobre ese mensaje real.
