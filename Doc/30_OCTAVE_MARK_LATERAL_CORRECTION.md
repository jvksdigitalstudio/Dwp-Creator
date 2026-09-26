# 30 — CORRECCIÓN: MARCA DE OCTAVA AL BORDE LATERAL IZQUIERDO + ETIQUETAS CENTRADAS

**Fecha:** 2026-09-25
**Versión:** `0.4.1-octave-mark-lateral`
**Punto de partida:** estado de `Doc/29` §11 (etiqueta "premium", posición abajo-izquierda, marca de octava como pie horizontal).
**Origen del pedido:** feedback directo del usuario, con capturas anotadas a mano sobre la app real, tras probar el resultado de `Doc/29` §11 en dispositivo.

> **Limitación de verificación (leer primero).** Igual que en `Doc/25`–`Doc/29`: este entorno no tiene compilador Kotlin, Gradle ni Android SDK. Verificado por lectura de código real, comprobación de balance de llaves/paréntesis y revisión símbolo por símbolo de la API de Compose usada. **No compilado ni ejecutado en esta pasada.**

---

## 1. El error de `Doc/29` §11 (por qué había que corregirlo)

`Doc/29` §11 interpretó "marca de octava en el pie de la tecla, como en un teclado físico" de forma literal: una franja horizontal pegada al **borde inferior** de la tecla Do, y el texto de la nota en `Alignment.BottomStart` (abajo-izquierda) para todas las teclas.

El usuario señaló, con capturas anotadas, que esa lectura es incorrecta para *este* diseño de pantalla: aquí no hay un teclado horizontal fotografiado de frente (donde "abajo" sí es el borde físico que toca el atril) -- hay una **lista vertical de filas**, una fila por muestra, con el nombre de la nota a la derecha de cada fila (`SampleRow`). En ese layout, recorrer el teclado de grave a agudo equivale a recorrer la lista de arriba a abajo, y la tecla "entra" en la fila por su **lado izquierdo** (el lado más cercano al nombre de la muestra). Por tanto:

1. La marca que indica "aquí empieza una octava nueva" debe ser una **banda vertical en el borde lateral izquierdo** de la tecla (de arriba abajo), no una franja horizontal abajo.
2. El nombre de nota de **todas** las teclas -- no solo el Do -- debe leerse **centrado verticalmente** y pegado a ese mismo borde izquierdo, no repartido entre "abajo" (teclas normales) y "abajo-izquierda" (ninguna, en la versión anterior todas estaban ya en `BottomStart`, pero verticalmente ancladas al fondo, no al centro).

Esta pasada corrige exactamente eso, sin tocar nada más de la pantalla.

## 2. Cambios

**Antes (`Doc/29` §11):**
- Marca de octava: `Box` con `fillMaxWidth()` + `height(OctaveFootHeight = 3.dp)`, alineado a `Alignment.BottomCenter`.
- Texto de nota: alineado a `Alignment.BottomStart`, con `padding(start = 6.dp, bottom = ...)`.

**Ahora:**
- Marca de octava (`OctaveStripeWidth = 3.dp`, mismo grosor visual que antes, ahora como ancho en vez de alto): `Box` con `fillMaxHeight()` + `width(OctaveStripeWidth)`, alineado a `Alignment.CenterStart` -- pegado al borde lateral izquierdo, de arriba abajo de la tecla.
- Texto de nota (todas las teclas, no solo el Do): alineado a `Alignment.CenterStart`, con `padding(start = OctaveStripeWidth + LabelStartGap)` cuando la tecla lleva banda de octava (para no quedar tapado por ella) o `padding(start = LabelStartGap)` en el resto -- centrado verticalmente en la tecla y pegado al mismo lado izquierdo que la banda.
- Se mantiene sin cambios todo lo demás de `Doc/29` §11: degradado vertical de la tecla, veta de brillo superior, esquinas redondeadas solo abajo, tamaño/negrita mayor del Do de cada octava, y el color de acento de octava compartido con `SampleRow` (mismo `OctaveColors` calculado por `SampleOctave`, `Doc/29` §2/H-01). El color del texto de cada tecla tampoco cambió -- solo su posición, tal como pidió el usuario ya en `Doc/29` §11 ("el color de las etiquetas que no cambien, solo su orden") y se reafirma ahora para la posición lateral.

## 3. Archivo tocado

Solo `ui/components/PianoKeyBadge.kt` (reescrito). `SampleRow.kt` no cambia: ya pasaba `isOctaveStart`/`octaveAccentColor` al badge, y toda la lógica de posicionamiento vive dentro de `PianoKeyBadge`, no en el llamador. `PianoKeyWidth`/`PianoKeyHeight` no cambiaron -- el overlay de gestos multi-touch de `MainScreen` sigue calculando la misma franja.

## 4. Verificación realizada en esta pasada

- Lectura completa del archivo reescrito y comparación campo a campo contra la versión anterior para confirmar que solo cambiaron los dos `Alignment`/tamaños descritos arriba (ningún otro comportamiento del badge -- degradado, borde, veta de brillo, tamaño de fuente del Do -- se tocó).
- Balance de paréntesis/llaves/corchetes del archivo: `(` 49 `)` 49, `{` 6 `}` 6, `[` 8 `]` 8 -- correcto.
- Verificación de API: `Alignment.CenterStart`, `Modifier.fillMaxHeight()`, `Modifier.width()` son API estándar de Compose Foundation/UI ya usadas en otras partes del proyecto (`SampleRow.kt` usa `Modifier.width`, `MainScreen`/otros usan `fillMaxHeight` sobre `Column`/`Box`); ningún símbolo nuevo sin precedente en el árbol.
- Revisado que `showOctaveStripe` (nueva variable local, antes el chequeo `isOctaveStart && octaveAccentColor.isSpecified` se repetía inline dos veces) se usa consistentemente en el padding del texto y en la condición de renderizado de la banda -- un único punto de verdad para "esta tecla lleva marca de octava".

**No realizado:** compilación, ejecución de tests, prueba en dispositivo. El cambio es puramente visual (posición/orientación de dos elementos dentro de un único `@Composable` sin estado ni lógica de negocio), por lo que no añade ni modifica tests -- **conteo de tests sin cambios (244)**, igual que el cambio equivalente de `Doc/29` §11.

## 5. Prueba en dispositivo recomendada (añadir a la de `Doc/29` §7/§8.5/§11.4)

16. La banda de color de la tecla Do es ahora una franja **vertical** pegada al borde **izquierdo** de la tecla (de arriba abajo), no una franja horizontal abajo.
17. El nombre de nota de **todas** las teclas (Do y el resto) se lee centrado verticalmente dentro de la tecla y pegado al lado izquierdo -- ya no "flotando" cerca del borde inferior.
18. En la tecla Do, el texto no queda tapado por la banda de color (debe quedar visiblemente a la derecha de la banda, con el mismo margen que en las demás teclas respecto al borde izquierdo).
19. El Do de cada octava sigue viéndose más grande/negrita que el resto, y con el mismo color de acento que el número/nombre de esa fila en la lista.
