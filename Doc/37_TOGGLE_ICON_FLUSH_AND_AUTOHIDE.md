# 37 — FLECHA PEGADA AL FOOTER + AUTO-OCULTADO CON EL PANEL ABIERTO

**Fecha:** 2026-09-25
**Versión:** `0.6.4-toggle-icon-flush-and-autohide` (`versionCode` 9 -> 10)
**Punto de partida:** `Doc/36` (el ícono ya vivía en el `Box` correcto, sin
reservar altura propia, alineado `BottomCenter` sin offset).
**Origen:** el usuario, con capturas anotadas a mano sobre la build de
`Doc/36`, señaló dos cosas puntuales:

1. La flecha, aunque ya no "engordaba" el footer, seguía viéndose **separada**
   de `DwpStatusBar` (© 2026 by YeiViKas Digital Company) por un hueco.
2. Con el panel del sampler desplegado, la flecha debía **desaparecer**
   (no solo rotar 180°), porque el panel ya trae su propio botón de cierre.

---

## 1. Causa raíz del hueco (no era de layout, era del propio vector)

`Doc/36` ya había resuelto el problema de posicionamiento (fila propia que
sumaba altura). El hueco que quedaba era distinto y vivía **dentro del propio
recurso** `ic_triangle_up.xml`:

- El SVG original define el triángulo con vértices `(4.5,372.5)`,
  `(494.5,372.5)`, `(249.5,127.5)` sobre un `viewBox` de `500x500`.
- Eso dejaba `500 - 372.5 = 127.5` unidades **vacías por debajo** de la base
  del triángulo (y otras 127.5 por encima, para centrarlo dentro del
  cuadrado).
- `Icon(...)` en Compose escala su `painter` con `ContentScale.Fit` y lo
  **centra** dentro de la caja que le da su `Modifier` — así que ese margen
  vacío se conserva sin importar el tamaño (18dp, 32dp, 56dp) que se le diera
  al ícono en cualquiera de las pasadas anteriores (`Doc/33`, `Doc/35`). El
  ícono nunca estuvo "separado por padding de Compose"; el aire estaba
  dibujado dentro del propio triángulo.

## 2. Corrección aplicada

**`ic_triangle_up.xml`**: se recortó el `viewport` al bounding box exacto del
triángulo (`viewportHeight` de `500` a `245`, que es `372.5 - 127.5`) y se
trasladó el `pathData` en consecuencia (`M4.5,245 L494.5,245 L249.5,0 Z`). La
geometría del triángulo no cambia en ningún punto relativo entre sí -- solo
se le quita el lienzo vacío alrededor. El único otro consumidor de este
drawable (el botón de cerrar dentro de `EffectsPanel`, a 16dp dentro de un
`IconButton` ya paddeado) no se ve afectado de forma perceptible por el
recorte.

**`EffectsPanel.kt` (`EffectsPanelToggleButton`)**: al recortar el vector, su
relación de aspecto pasó de `1:1` a `500:245`. Si el `Modifier` del `Icon`
le siguiera dando un tamaño cuadrado (`.size(56.dp)`), `ContentScale.Fit`
volvería a rellenar la caja con el mismo margen vertical que se acaba de
quitar del recurso. Por eso el `Modifier` ahora fija ancho y alto por
separado: `width(56.dp)` y `height(56.dp * TriangleAspectRatio)`, con
`TriangleAspectRatio = 245f / 500f` documentado junto al recurso que lo
origina. Resultado: el vector ocupa el 100% de su caja, sin aire interno, y
al estar esa caja alineada `BottomCenter` sin offset (ya resuelto en
`Doc/36`), la base del triángulo queda exactamente pegada al borde superior
de `DwpStatusBar`.

## 3. Auto-ocultado con el panel abierto

`EffectsPanelToggleButton` ya no gira 180° para indicar "cerrar" -- ahora se
envuelve en su propio `AnimatedVisibility(visible = !expanded, ...)` con
`fadeIn`/`fadeOut` cortos (150ms/120ms, coherentes con las duraciones ya
usadas en `EffectsPanel`). Con el panel cerrado el comportamiento es
idéntico al de antes (mismo tamaño, mismo tinte, mismo `clickable`); al
abrirse, la flecha se desvanece por completo. El cierre sigue disponible
por el botón propio de la cabecera del panel (`IconButton` con el mismo
ícono rotado 180°, sin cambios), que ya existía desde `Doc/31` -- se dejó de
duplicar innecesariamente el control.

Efecto colateral correcto: al depender de `AnimatedVisibility` en vez de una
condición externa, si en el futuro se cierra el panel por cualquier otra vía
(por ejemplo, un futuro gesto de arrastre) la flecha reaparece sola, sin
necesitar tocar este composable de nuevo.

## 4. Limpieza de imports

`EffectsPanel.kt`: se retiraron `animateColorAsState` y `animateFloatAsState`
(ya no hay rotación ni cambio de tinte en el toggle) y `androidx.compose.
runtime.getValue` (sin más delegados `by` en el archivo). Se añadieron
`androidx.compose.foundation.layout.height` y `...layout.width` (tamaño no
cuadrado del ícono).

## 5. Verificación realizada en esta pasada

- XML del vector recortado validado con un parser XML (`xml.dom.minidom`):
  `OK`.
- `grep` confirmando que `ic_triangle_up` solo tiene los dos consumidores ya
  conocidos (`EffectsPanelToggleButton`, botón de cierre en `EffectsPanel`) y
  que ninguno más depende de que el viewport sea cuadrado.
- `grep` confirmando que ningún import retirado de `EffectsPanel.kt` sigue
  usándose en el resto del archivo antes de borrarlo.
- Revisado que `Dp * Float` (`EffectsPanelToggleIconWidth *
  TriangleAspectRatio`) es una operación soportada por
  `androidx.compose.ui.unit.Dp` (no requiere conversión manual).

**No realizado:** compilación real (limitación constante de este entorno).
**Conteo de tests sin cambios (244)** -- este cambio es puramente visual/UI,
sin lógica de dominio involucrada.

## 6. Archivos tocados

`app/src/main/res/drawable/ic_triangle_up.xml` (recorte del viewport),
`ui/components/EffectsPanel.kt` (aspecto no cuadrado + auto-ocultado +
limpieza de imports), `app/build.gradle.kts` (versión). `MainScreen.kt` no
se tocó -- la posición (`Box` + `Alignment.BottomCenter`, sin offset) ya
quedó resuelta en `Doc/36` y sigue siendo correcta.
