# 58 — Baseline Profile: se abandona Gradle Managed Devices, se usa reactivecircus/android-emulator-runner

## Por qué (historial completo hasta aquí)
Seis intentos seguidos con Gradle Managed Devices (GMD), documentados en
Docs 54-57:
1. Imagen `aosp` API 34 -> corre en 3 min, falla con un error real:
   `IllegalStateException: Unable to confirm activity launch completion`.
2. Imagen `aosp-atd` API 30 -> **colgada 45 min**, sin error.
3. Igual + instalación explícita de la imagen -> **colgada 20 min**.
4. Igual + `require64Bit = true` -> **colgada 15 min**.
5. Vuelta a `aosp` API 34 + animaciones del sistema en 0 (mitigación
   documentada real para el error del punto 1) -> **mismo error exacto**,
   3 min.

Con GMD agotadas las mitigaciones documentadas que se pudieron aplicar
(imagen ATD, animaciones en 0), y sin forma de pasarle al emulador la
opción que de verdad resuelve ese error en emuladores sin GPU física
(`-gpu swiftshader_indirect`) -- GMD no expone esa opción en su DSL --, se
cambia de mecanismo por completo.

## Cambio de estrategia
`reactivecircus/android-emulator-runner` es la Action que usa la mayoría
de proyectos Android reales en GitHub Actions para pruebas instrumentadas,
precisamente porque sí permite pasar `emulator-options` directas
(`-gpu swiftshader_indirect`, `-no-window`, `-no-snapshot`, etc.). Arranca
su propio emulador y lo conecta por `adb` -- desde el punto de vista de
Gradle, es un "dispositivo conectado", no uno que Gradle gestiona por sí
mismo.

## Archivos cambiados
- `.github/workflows/baseline-profile.yml`: el paso "Generate Baseline
  Profile" ahora es la Action `reactivecircus/android-emulator-runner@v2`
  (API 30, `aosp` default, x86_64, perfil `pixel_6`), con
  `emulator-options: -no-window -gpu swiftshader_indirect -no-snapshot
  -noaudio -no-boot-anim -camera-back none` y `disable-animations: true`.
  Dentro de esa misma sesión (con el emulador vivo) corre
  `gradle :app:generateReleaseBaselineProfile --stacktrace`.
- `app/build.gradle.kts`: `baselineProfile { useConnectedDevices = true }`
  -- ahora sí explícito (antes `false`).
- `baselineprofile/build.gradle.kts`: `baselineProfile { useConnectedDevices
  = true }` (antes `false`, con `managedDevices += "pixel6Api34"`, que se
  deja como bloque de referencia sin uso, no se borra).

## Honestidad sobre el riesgo de este intento
- `reactivecircus/android-emulator-runner@v2` es una Action de terceros
  ampliamente usada y estable (versión mayor fija, bajo riesgo de
  resolución comparado con las coordenadas Maven que sí dieron problemas
  antes). No se pudo verificar contra un runner real desde este entorno.
- Es un cambio de mecanismo real, no otro parámetro más sobre el mismo
  camino que ya falló tres veces sin dar señales.

## No afecta
El APK de `Build APK` sigue sin depender de este módulo ni de este
workflow para nada.

## Siguiente paso
Disparar el workflow. Con este mecanismo, si algo falla, debería dar un
mensaje de la propia Action (instalación del emulador, arranque, etc.) o el
mismo/otro error de Gradle -- en cualquier caso, información real para
seguir, no otro cuelgue sin causa.
