# 53 — Baseline Profile: el test nunca se ejecutaba (ejecutor de pruebas equivocado)

## Evidencia real (run #6, paso "Locate generated profile files")
- XML del test: `<testsuite tests="0" failures="0" errors="0" skipped="0" ...>`
- `test-results.log`: `Test results for InstrumentationTestRunner=` ... `OK (0 tests)`
- `additional_output`: carpetas creadas y vacías (ningún archivo de perfil).

## Causa
`:baselineprofile` no declaraba `testInstrumentationRunner`. AGP usó el ejecutor
antiguo `android.test.InstrumentationTestRunner`, que solo ejecuta pruebas
JUnit3 y descarta las `@Test` de JUnit4. El emulador arrancó, la app se
instaló y la tarea terminó en verde, pero sin ejecutar `BaselineProfileGenerator`.
Es un error de configuración propio; el código del test no era el problema.

## Corrección
`baselineprofile/build.gradle.kts`: `testInstrumentationRunner =
"androidx.test.runner.AndroidJUnitRunner"` y dependencia explícita
`androidx.test:runner:1.6.1` (misma familia 1.6.1 que `androidx.test:core`
que ya aparece resuelto en los logs de CI).

## Cómo verificar (nuevo run de "Generate Baseline Profile")
1. En el paso "Locate generated profile files" el XML debe decir `tests="1"`
   y el log `Test results for AndroidJUnitRunner`.
2. `additional_output/.../pixel6Api34` debe contener un archivo `*baseline-prof*.txt`.
3. Si aparece, se fija la ruta definitiva de subida y de copia al repo.
4. Si sigue en `tests="0"` o el test falla, el mensaje del XML/log indica la
   causa siguiente (p. ej. la app cerrándose al arrancar en el emulador).

## No afecta
El APK de `Build APK` no cambia: usa `:app:assembleRelease` (Doc/52).
