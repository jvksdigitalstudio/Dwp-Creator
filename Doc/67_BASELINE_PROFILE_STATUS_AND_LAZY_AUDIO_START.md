# 67 — Estado del Baseline Profile y arranque diferido del audio

## Estado del Baseline Profile (workflow `baseline-profile.yml`, `workflow_dispatch`)
- Doc/62 lo cerró **sin resolver**: 6 intentos con distintos entornos produjeron el mismo error
  (`IllegalStateException: Unable to confirm activity launch completion` en `amStartAndWait`).
- Historial en GitHub Actions al 9-oct: #6 verde (28-sep); #7–#14 rojo o con advertencia (hasta 30-sep).
  **Esos fallos son anteriores al motor de audio nuevo (Doc/66)**: no se les puede atribuir.
- La causa sigue **sin diagnosticar** (hace falta el log del paso que falla; no hay SDK/emulador/logcat en el
  entorno de desarrollo). Este documento NO afirma que vaya a pasar.

## Regresión propia detectada y corregida
La auditoría de Doc/62 daba por hecho que *"SamplePlayer no crea ningún AudioTrack hasta que se reproduce una nota"*.
Con el motor nuevo dejó de ser cierto: `SamplePlayer.init { driver.start() }` abría hilo de audio + `AudioTrack`
en **cada arranque**, incluso para quien sólo renombra/exporta, y justo en el camino de arranque en frío que el
Baseline Profile perfila.

**Corrección:** `driver.start()` ya no está en `init`. El stream se abre en `prewarm()` (se llama al cargar un
instrumento: `DwpCreatorViewModel`, tras la carga) y, como red de seguridad idempotente, en el primer `play()`.
Resultado: arranque sin ningún trabajo de audio; la primera nota sigue sin pagar la apertura porque el stream
ya se abrió al cargar el instrumento. Se restaura el invariante de Doc/62.

## Alcance del perfil (sin cambios)
El generador sólo cubre el arranque en frío hasta la pantalla vacía (sin fixture de instrumento: el selector de
archivos no es automatizable). Los composables del panel SAMPLER no forman parte del perfil.

## Cómo ejecutarlo
Subir esta versión a `main` → Actions → *Generate Baseline Profile* → *Run workflow* (Branch: main).
Si falla: abrir el paso rojo y enviar su log (como con las pruebas); el error exacto decide el siguiente paso.
