# 54 — Baseline Profile: "Unable to confirm activity launch completion" -> imagen ATD

## Evidencia real (run #7)
- `Starting 1 tests on pixel6Api34` -- confirma que el fix del Doc/53
  (`AndroidJUnitRunner`) funcionó: el test por fin se ejecuta.
- Falla dentro del test:
  `java.lang.IllegalStateException: Unable to confirm activity launch
  completion [] Please report a bug with the output of adb shell dumpsys
  gfxinfo ... framestats`, en
  `androidx.benchmark.macro.MacrobenchmarkScope.amStartAndWait`.

## Causa
No es un bug de esta app. Es una limitación conocida y documentada de
Macrobenchmark: confirma que una Activity terminó de arrancar leyendo
estadísticas de frames (`dumpsys gfxinfo`), algo que los emuladores estándar
de los runners de GitHub Actions (sin aceleración gráfica real) no siempre
reportan de forma fiable.

## Corrección
`baselineprofile/build.gradle.kts`: el dispositivo gestionado pasa de imagen
`aosp` (API 34) a **`aosp-atd`** (Automated Test Device) en **API 30** --
las imágenes ATD son las que el propio equipo de Android construyó
específicamente para pruebas instrumentadas en CI, y es la configuración que
usan en sus ejemplos oficiales de Baseline Profile. ATD no está disponible en
todos los niveles de API (no en 34); se usa 30 porque el contenido del
perfil generado (entradas de bytecode) no depende de qué versión de Android
tenía el dispositivo que lo generó.

`baseline-profile.yml`: el paso de diagnóstico ahora también busca e imprime
cualquier logcat y lista los reportes HTML del test, por si esto no resuelve
el problema del todo y hace falta ver la causa exacta del siguiente fallo.

## No afecta
El APK de `Build APK` sigue sin depender de este módulo (`:app:assembleRelease`,
Doc/52).

## Siguiente paso
Subir el cambio, disparar "Generate Baseline Profile" y revisar el resultado:
- Verde con `tests="1"` y un archivo de perfil real en `additional_output` -> se
  copia ese archivo al repo (Doc/49 explica dónde).
- Sigue fallando -> el log ahora trae logcat y HTML del reporte; se manda esa
  captura y se corrige con la causa real, no a ciegas.
