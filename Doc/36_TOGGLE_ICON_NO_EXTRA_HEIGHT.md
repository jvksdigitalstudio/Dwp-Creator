# 36 — CORRECCIÓN DE FONDO: EL ÍCONO NO RESERVA ALTURA, FLOTA SOBRE EL FOOTER

**Fecha:** 2026-09-25
**Versión:** `0.6.3-toggle-icon-no-extra-height` (`versionCode` 8 -> 9)
**Punto de partida:** `Doc/35` (ícono a 56dp, en su propia fila entre la lista y `DwpStatusBar`).
**Origen:** el usuario mostró de nuevo la captura *original* de su primer pedido (antes de cualquier implementación) y explicó, por fin con la comparación exacta: el footer de esa captura es **uno solo**; lo único que pedía era la flecha encima, sin volverlo "más grueso" ni duplicarlo.

---

## 1. El error real (distinto de los dos anteriores)

`Doc/33` y `Doc/34` descartaron correctamente que hubiera un `Row`/`IconButton`/`Surface` extra -- eso ya no existía. Pero había un problema distinto, más sutil, que ninguna de esas dos pasadas atacó: `EffectsPanelToggleButton` vivía en **su propia fila** dentro del `Column` exterior, entre el `Box` de contenido (teclado + lista) y `DwpStatusBar`:

```kotlin
Box(modifier = Modifier.weight(1f)) { /* teclado + lista */ }
EffectsPanelToggleButton(modifier = Modifier.fillMaxWidth().wrapContentWidth(...).padding(vertical = 4.dp))
DwpStatusBar(...)
```

Aunque esa fila no tenía ningún `background` propio, **sí ocupaba altura real** dentro del `Column` (56dp del ícono + 8dp de padding vertical). Como todo el `Column` comparte un único color de fondo (`BgDark`), el resultado visual no era "dos barras separadas" sino **una sola zona inferior más alta que antes** -- exactamente lo que el usuario describió: "dando la impresión de haber agrandado el mismo header de abajo". El footer no se duplicó ni se le puso un fondo distinto; se le sumó altura de forma invisible pero perceptible en el conjunto.

## 2. Corrección

`EffectsPanelToggleButton` ya no tiene fila propia. Se movió **dentro del mismo `Box(modifier = Modifier.weight(1f))`** que ya contenía la `LazyColumn`, `PianoKeyGestureOverlay` y `EffectsPanel` -- es decir, se trata como un cuarto elemento superpuesto de esa zona, no como un elemento nuevo del `Column` exterior. Con `modifier = Modifier.align(Alignment.BottomCenter)` y **sin ningún padding ni offset**, el ícono queda pegado exactamente al borde inferior de esa zona -- que es, pixel por pixel, donde termina el contenido y empieza `DwpStatusBar`. Ninguna de las dos zonas (contenido, footer) cambia de tamaño: el ícono simplemente flota encima del límite entre ambas, tal como mostraba la captura original del pedido.

Se mantiene, sin cambios: el tamaño del ícono (56dp, `Doc/35`), que solo sea un `Icon` con `Modifier.clickable` (`Doc/33`), y que vaya último en el orden de composición de ese `Box` para quedar visible por encima del panel incluso con este desplegado (`Doc/31`).

## 3. Efecto colateral correcto (no un descuido)

Al vivir ahora dentro del `Box` que solo se compone cuando `exportProgress == null`, el ícono deja de mostrarse automáticamente durante una exportación -- el mismo comportamiento que ya tenía antes por una condición explícita (`Doc/33`), ahora se obtiene de forma estructural sin necesitar ese comentario/condición aparte.

## 4. Imports retirados

`fillMaxWidth`, `wrapContentWidth` (de `androidx.compose.foundation.layout`) y `androidx.compose.ui.unit.dp` en `MainScreen.kt` quedaron sin uso tras quitar la fila dedicada -- se retiraron para no dejar imports muertos.

## 5. Verificación realizada en esta pasada

- Balance de paréntesis/llaves/corchetes de `MainScreen.kt`: `(` 115/`)` 115, `{` 47/`}` 47, `[` 7/`]` 7 -- correcto.
- Confirmado con `grep` que ningún import retirado sigue usándose en el archivo antes de borrarlo.
- Revisado que `Modifier.align(Alignment.BottomCenter)` se usa dentro de un lambda de `BoxScope` (el mismo patrón ya usado por `PianoKeyGestureOverlay` y `EffectsPanel` en ese mismo `Box`) -- no un uso inválido de `align` fuera de contexto.
- Re-validación XML de los 5 archivos `.xml` del proyecto: los 5 siguen dando `OK`.
- `versionCode`/`versionName` subidos de nuevo para que este cambio sea verificable en el dispositivo tras una instalación limpia.

**No realizado:** compilación real (limitación constante de este entorno). **Conteo de tests sin cambios (244).**

## 6. Archivos tocados

`ui/screens/MainScreen.kt` (reubicación del ícono + limpieza de imports), `app/build.gradle.kts` (versión). `EffectsPanel.kt` no se tocó -- el ícono en sí sigue siendo exactamente el de `Doc/35`.
