# 60 — Causa real encontrada: falta `reportFullyDrawn()`, no el renderizado del emulador

## Evidencia decisiva (run con reactivecircus, Doc/58)
El log confirma que esta vez el emulador SÍ arrancó bien
(`Emulator booted.`), con GPU real vía software (`swiftshader_indirect`,
Doc/58) y con las animaciones desactivadas de verdad
(`Disabling animations` + los tres `settings put global ..._scale 0.0`
ejecutados por la propia Action). Y aun así: el MISMO error exacto,
`IllegalStateException: Unable to confirm activity launch completion`.

Esto descarta por completo la hipótesis de origen (Doc/54): no es un
problema de renderizado gráfico del emulador de CI. Si lo fuera, controlar
la GPU real lo habría resuelto.

## Causa real
Apps 100% Jetpack Compose no siempre disparan la señal automática de
"Activity completamente dibujada" que usa `ActivityManager` (esa heurística
está pensada para jerarquías de `View` clásicas). Sin esa señal,
herramientas que dependen de ella para confirmar el arranque --como
`amStartAndWait` de Macrobenchmark-- pueden fallar exactamente con este
error. La recomendación oficial de Android para apps Compose es llamar a
`Activity.reportFullyDrawn()` explícitamente en cuanto la primera
composición real se asienta.

## Corrección
`MainActivity.kt`: dentro de `setContent { }`, un `LaunchedEffect(Unit) {
reportFullyDrawn() }` junto al contenido real (`DwpCreatorApp()` o
`CrashDiagnosticScreen`). No es un ajuste de CI -- es una llamada real de
la app, útil incluso fuera de las pruebas: Android Vitals en Play Console
usa esta misma señal para medir el tiempo de arranque en frío real de la
app en dispositivos de usuarios.

## Alcance de la sesión de hoy sobre Baseline Profile
1. GMD, imagen ATD -> colgada tres veces sin causa (Docs 55/56).
2. GMD, imagen normal + animaciones en 0 -> mismo error (Doc/57).
3. reactivecircus + GPU real (swiftshader) + animaciones en 0 -> MISMO
   error -- la evidencia que descartó "renderizado" como causa (este doc).
4. `reportFullyDrawn()` en la propia app -- primer intento que ataca la
   causa real identificada, no la infraestructura de CI.

## No afecta
`Build APK` (el release real) no depende de `reportFullyDrawn()` para
compilar -- es una llamada normal de Android, disponible desde
`ComponentActivity` sin ninguna dependencia nueva. Mejora la app en
producción, no solo el CI.

## Siguiente paso
Disparar "Generate Baseline Profile" de nuevo (con el workflow del Doc/58,
reactivecircus). Si el error desaparece, confirma la causa. Si persiste,
sigue habiendo una pista real y distinta que perseguir (no se vuelve a
tocar la imagen del emulador).
