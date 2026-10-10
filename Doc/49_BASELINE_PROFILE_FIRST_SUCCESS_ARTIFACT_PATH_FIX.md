# 49 — Baseline Profile: primer run exitoso y corrección de la ruta del artefacto

## Resultado real
Run #2 de "Generate Baseline Profile": **Success** (4m 29s), tarea
`:app:generateReleaseBaselineProfile` completada. Confirma que los reintentos
de `gradle.properties` (Doc/48) resolvieron el 429 y que el módulo
`:baselineprofile`, el emulador gestionado y el test de arranque funcionan.

## Error propio detectado
El paso "Upload Baseline Profile" apuntaba a `app/src/main/baseline-prof.txt`,
que es el placeholder vacío de Doc/46. El plugin `androidx.baselineprofile`
guarda el perfil generado en `app/src/<variante>/generated/baselineProfiles/`.
El artefacto del run #2 (1 archivo) es, con alta probabilidad, el placeholder
y no el perfil real. No se pudo verificar la ruta exacta sin ejecutar el run,
por eso el path nuevo usa un glob (`app/src/**/generated/baselineProfiles/**`)
y `if-no-files-found: error`: si la ruta no coincide, el paso falla de forma
visible en vez de subir un archivo equivocado en silencio.

## Siguiente paso
1. Subir este cambio y volver a disparar el workflow.
2. Descargar el artefacto `baseline-prof` y confirmar que trae un
   `baseline-prof.txt` con líneas reales (`HSPL...`, `Lcom/jvk/...`).
3. Copiarlo al repo en la misma ruta `app/src/release/generated/baselineProfiles/`
   y hacer commit; el `assembleRelease` normal lo empaqueta.
4. Instalar el APK y comparar el primer arranque y el primer scroll.
