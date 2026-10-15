# 51 — Baseline Profile, run #4: verde, pero sin evidencia de perfil generado por el test

## Datos reales del paso "Locate generated profile files"
- `app/build/intermediates/merged_art_profile/nonMinifiedRelease/.../baseline-prof.txt`
  = 172005 bytes. Es el perfil mezclado por AGP (perfiles empaquetados por
  las librerías + `app/src/main/baseline-prof.txt`, que pesa 2372 bytes por los
  comentarios). No se pudo atribuir al test de Macrobenchmark.
- `app/src/*/generated`: vacío. El plugin no guardó un perfil generado en src.
- `baselineprofile/build/outputs`: solo `androidTest-results/...` (logs adb,
  XML de resultados) y el APK `nonMinifiedRelease`. Ningún archivo de perfil.

## Corrección de lecturas anteriores
El artefacto de 24.9 MB del run #4 no es "el perfil": es el glob amplio de
diagnóstico. El archivo de 172 KB tampoco debe copiarse al repo como si fuera
el perfil del test, porque su origen no está probado.

## Qué no se sabe
Si el test `BaselineProfileGenerator.generate()` llegó a ejecutarse, si fue
omitido (0 tests) o si corrió y no dejó salida. No hay forma de saberlo con los
datos actuales.

## Cambio
El paso de diagnóstico ahora imprime el XML de resultados del test, el final de
`test-results.log`, la ruta `additional_output` (donde Macrobenchmark deja el
perfil) y la lista de carpetas de salida.

## Siguiente paso
Re-ejecutar el workflow y revisar esa salida: número de tests ejecutados,
fallos u omisiones, y si existe `additional_output`. Con eso se corrige la causa
real (configuración del runner, filtro de tests o el propio test).
