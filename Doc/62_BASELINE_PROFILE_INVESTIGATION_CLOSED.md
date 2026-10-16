# 62 — Cierre de la investigación del Baseline Profile

## Resumen de los 6 intentos (Docs 54-61)
1. Imagen ATD (API 30) -> colgado sin error, 3 veces seguidas con variantes
   (instalación explícita, `require64Bit`).
2. Imagen `aosp` normal + animaciones del sistema en 0.
3. `reactivecircus/android-emulator-runner` con GPU real (`swiftshader_indirect`).
4. `reportFullyDrawn()` agregado a `MainActivity`.
5. `<profileable android:shell="true" />` agregado al manifiesto.

Las variantes 2, 3, 4 y 5 (entornos y código completamente distintos)
produjeron el **mismo error exacto, letra por letra**:
`IllegalStateException: Unable to confirm activity launch completion`
en `androidx.benchmark.macro.MacrobenchmarkScope.amStartAndWait`.

## Auditoría final del código de la app (esta sesión)
Se revisó el código real buscando una causa de crash al arranque en frío
que explicara el patrón (en vez de seguir ajustando infraestructura):
- `MidiInputManager`: casteo seguro (`as? MidiManager`) y
  `listDevices()` con `?: return emptyList()` -- no puede lanzar por
  ausencia de servicio MIDI.
- `SamplePlayer`/`AudioVoicePool`: no crean ningún `AudioTrack` hasta que
  se reproduce una nota real -- el test nunca toca una nota, así que no
  hay forma de que esto sea la causa.
- `AndroidManifest.xml`: sin permisos peligrosos que requieran
  autorización en tiempo de ejecución.
- Recurso del ícono de lanzador: válido.
- `DwpCreatorApplication`: el manejador de excepciones no capturadas
  registra el crash y delega al manejador por defecto -- no reinicia el
  proceso en bucle.

No se encontró ningún punto de fallo real en el código de la app.

## Conclusión
La invariancia total del error a través de seis cambios independientes de
entorno y de código, combinada con una auditoría limpia del código de la
app, indica que la causa está fuera del alcance diagnosticable desde este
entorno de trabajo (sin SDK, sin red, sin acceso a logcat en el momento
exacto del fallo) -- probablemente una incompatibilidad de la versión de
`androidx.benchmark:benchmark-macro-junit4` (`1.3.3`, nunca verificada
contra notas de versión reales) con esta combinación concreta de AGP/Compose
en este runner de GitHub Actions.

## Decisión
Se cierra esta investigación. El módulo `:baselineprofile` y su workflow
(`baseline-profile.yml`) quedan en el repositorio, aislados y sin efecto
sobre `Build APK` ni sobre el APK que el usuario instala -- disponibles
para retomar en el futuro si se decide investigar con herramientas de
depuración reales (un dispositivo físico, o acceso a logcat en vivo), pero
sin más intentos a ciegas por ahora.

## Estado real del proyecto al cierre de esta sesión
- `Build APK`: compila y pasa tests (confirmado por el usuario).
- Rendimiento: resuelto (Docs 45-46 -- release con R8 + Kotlin 2/K2 +
  Strong Skipping).
- Auditoría de código completa (Doc/52): errores reales corregidos en
  audio, MIDI y mezclador; sin pendientes conocidos.
- Baseline Profile: sin resolver, sin impacto en el producto real.
