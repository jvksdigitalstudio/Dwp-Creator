# 50 — Baseline Profile, run #3: generación OK, ruta del artefacto incorrecta

## Log real
Run #3: todos los pasos en verde, incluido "Generate Baseline Profile" (2m 23s),
excepto "Upload Baseline Profile":
`Error: No files were found with the provided path: app/src/**/generated/baselineProfiles/**`.
El `if-no-files-found: error` del Doc/49 hizo su trabajo: falló a la vista en
lugar de subir el placeholder en silencio.

## Qué se sabe y qué no
- Se sabe: la tarea `:app:generateReleaseBaselineProfile` terminó sin error y
  la ruta asumida en el Doc/49 no contiene archivos.
- No se sabe: dónde dejó el plugin el resultado (o si el perfil salió vacío).
  La ruta del Doc/49 era una suposición y no se puede verificar sin ejecutar.

## Cambio
`baseline-profile.yml`: nuevo paso "Locate generated profile files" (`find`
sobre todo el repo por `*baseline-prof*`, `*.prof` y carpetas `baselineProfiles`,
con tamaños en bytes) y subida con `if: always()` de tres globs
(`app/src/**/generated/**`, `**/baseline-prof*.txt`,
`baselineprofile/build/outputs/**`) con `if-no-files-found: warn`.

## Siguiente paso
Volver a ejecutar el workflow, abrir el paso "Locate generated profile files"
y enviar esa salida. Con la ruta real se fija el upload definitivo. Si la
lista muestra archivos de 0 bytes o ninguno, el problema está en el test de
`BaselineProfileGenerator` y se revisa ahí.
