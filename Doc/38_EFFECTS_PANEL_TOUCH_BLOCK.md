# 38 — EL PANEL DEL SAMPLER YA NO TRASPASA EL TOQUE AL TECLADO/LISTA

**Fecha:** 2026-09-25
**Versión:** `0.6.5-effects-panel-touch-block` (`versionCode` 10 -> 11)
**Punto de partida:** `Doc/37` (flecha pegada al footer + auto-ocultado).
**Origen:** el usuario, con capturas anotadas a mano, reportó que al tocar
**cualquier zona** del panel abierto (fondo, texto placeholder, espacio
vacío de la cabecera) sonaba una nota del piano o se reproducía una muestra
-- como si el panel fuera transparente al tacto, a pesar de dibujarse
visualmente encima y bloquear la vista.

---

## 1. Causa raíz

En `MainScreen.kt`, `EffectsPanel` se compone dentro del mismo `Box` que la
`LazyColumn` de muestras y `PianoKeyGestureOverlay`, alineado
`BottomCenter`, superpuesto sobre ambos. Visualmente eso alcanza para que se
vea "encima" -- pero Compose **no decide a quién entregar un toque por orden
de dibujo (z-order) exclusivamente**: cuando dos hermanos de un `Box` se
superponen en un punto, Compose prueba primero al que está más arriba en el
orden de composición y, si ese hermano **no tiene ningún nodo de entrada
táctil** cubriendo ese punto exacto, sigue probando al siguiente hermano de
atrás hasta encontrar uno que sí lo tenga.

El `EffectsPanel`, tal como estaba, no tenía ningún `clickable` ni
`pointerInput` propio -- solo `Column` + `background` + `Text` (contenido
placeholder intencional, `Doc/31`). Un `background` por sí solo no registra
ningún manejador de toque en Compose (es puramente de dibujo). Resultado: al
tocar dentro del rectángulo del panel, Compose no encontraba ahí ningún nodo
táctil y seguía probando al siguiente hermano detrás -- que sí lo tiene:
`PianoKeyGestureOverlay` (dispara notas MIDI vía `AudioVoicePool`) del lado
derecho, o la fila tocada de la `LazyColumn` (`SampleRow`, con su propio
`onPreview`) del lado izquierdo. De ahí el síntoma exacto que describió el
usuario: tocar "en cualquier lado" de la ventana abierta sonaba el piano.

## 2. Corrección

Se añadió un `Modifier.clickable(indication = null, onClick = {})` al
`Column` raíz de `EffectsPanel`, con su propio `MutableInteractionSource`
(para no compartir estado de "presionado" con ningún otro control) y sin
`onClick` real (no hace nada -- su única función es *existir* como nodo
táctil). Con esto, Compose ya encuentra un manejador de toque cubriendo el
100% del rectángulo del panel y **deja de probar a los hermanos de detrás**
mientras el panel está visible: ni `PianoKeyGestureOverlay` ni la
`LazyColumn` reciben ya ningún evento originado dentro de esos límites.

El botón de cerrar de la cabecera (`IconButton` con su propio `clickable`,
`Doc/31`) sigue funcionando exactamente igual: en Compose, un `clickable`
anidado dentro de otro consume el gesto en su propio paso antes de que
llegue al `clickable` del ancestro (así es como ya funciona cualquier botón
dentro de una tarjeta/fila clicable en este framework) -- no hace falta
ninguna condición especial para que ambos convivan.

## 3. Alcance de la corrección

Este bloqueo cubre **toda la superficie del panel**, no solo el tap: al
resolverse a nivel de hit-testing (qué subárbol recibe el evento desde el
primer `down`), también evita que un gesto de arrastre (glissando) iniciado
dentro del panel se filtre hacia `PianoKeyGestureOverlay`, que usa detección
de gestos de bajo nivel (`pointerInput` + `awaitPointerEventScope`, no
`clickable`) para permitir acordes y deslizar el dedo entre teclas.

Es además una corrección **estructural, no una lista de casos**: cualquier
control real que se añada más adelante al panel (sliders de reverb/delay,
`Doc/31`) seguirá recibiendo su propio toque con normalidad por ser un
`clickable`/`pointerInput` más interno, y cualquier zona del panel que
quede sin control específico seguirá bloqueada por este `clickable` de
respaldo -- no depende de enumerar manualmente cada área vacía.

## 4. Verificación realizada en esta pasada

- Revisado `PianoKeyGestureOverlay` (`MainScreen.kt`): confirmado que usa
  `pointerInput` de bajo nivel sobre su propio `Modifier`, hermano de
  `EffectsPanel` dentro del mismo `Box` -- consistente con el diagnóstico
  de hit-testing por hermanos superpuestos.
- Revisado `SampleRow.kt`: confirmado que su fila usa un `clickable` propio
  (`onPreview`) independiente, mismo mecanismo de "hermano detrás" afectado.
- Balance de imports: `MutableInteractionSource` y `remember` añadidos y
  usados; ningún import quedó sin uso tras el cambio.
- No fue necesario tocar `MainScreen.kt`: el arreglo vive enteramente dentro
  de `EffectsPanel.kt`, en el propio composable responsable del panel.

**No realizado:** compilación real (limitación constante de este entorno).
**Conteo de tests sin cambios (244)** -- corrección de interacción táctil en
Compose, sin lógica de dominio involucrada.

## 5. Archivos tocados

`ui/components/EffectsPanel.kt` (`clickable` de bloqueo + KDoc), `app/build.
gradle.kts` (versión). `MainScreen.kt` no se tocó.
