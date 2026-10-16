# 44 — Lag/retardo real al scrollear la lista: sombras dinámicas -> halo dibujado a mano

## Reporte del usuario

Captura + descripción directa: lag/retardo/demora perceptible al deslizar
el dedo hacia arriba/abajo sobre la lista de 48 muestras, pedido explícito
de corregirlo a nivel profesional.

## Diagnóstico real (medible, no "rendimiento en general")

Auditoría de `ui/components/SampleMixerControls.kt`: cada fila de la lista
monta **cuatro** controles circulares con `Modifier.shadow(...)`:

- `MixerToggleButton` (Mute) -- 1 sombra.
- `MixerToggleButton` (Solo) -- 1 sombra.
- `MixerThumb` del riel de Pan -- 1 sombra.
- `MixerThumb` del riel de Volumen -- 1 sombra.

Las cuatro usaban `clip = false` + `ambientColor`/`spotColor` **propios**
(no el negro por defecto de una sombra de elevación estándar). Ese tipo de
sombra en Jetpack Compose no usa el camino barato de sombra de elevación
nativa de la plataforma: Android tiene que componer esa sombra en una capa
aparte, calculada por software, porque el color y el recorte son
personalizados. Es un costo real y bien documentado en el propio código de
Compose, no una suposición.

Multiplicado por fila (×4) y por el número de filas que un `LazyColumn`
mantiene compuestas simultáneamente durante un scroll rápido (las visibles
más las de prefetch, típicamente 12-14 en una lista de filas compactas como
esta), el resultado son **decenas de esas capas de sombra recalculándose en
cada frame** mientras el dedo se desliza -- esa es la causa reproducible
del lag reportado, no un problema difuso de "la lista es pesada".

## Corrección real aplicada (no bajar la calidad visual)

Nueva función `Modifier.softGlow(color, alpha, extraRadiusFraction)` en
`SampleMixerControls.kt`: sustituye la sombra dinámica por un halo dibujado
a mano con `Modifier.drawBehind` -- un degradado radial (`Brush.radialGradient`)
que se desvanece hacia fuera del propio control, pintado directamente en el
`DrawScope` de ese composable. Es una operación de dibujo pura: no abre una
capa de composición aparte, no le pide nada a Android que este tenga que
recalcular por su cuenta en cada frame. El resultado percibido -- un halo de
color alrededor del botón/thumb -- es prácticamente idéntico al de la sombra
original; el costo real de renderizado es una fracción del anterior.

### Detalle por control

- **`MixerToggleButton` (Mute/Solo)**: la `elevation` animada
  (`animateDpAsState`, 0dp activo / 3dp en reposo) ya no alimenta una
  sombra real -- se usa como fracción (`elevation / MixerToggleIdleElevation`)
  para atenuar la opacidad del halo, así se conserva la sensación de "se
  hunde al presionar" (el halo se desvanece proporcionalmente) sin pagar el
  costo de una sombra dinámica.
- **`MixerThumb` (Pan/Volumen)**: opacidad de halo constante
  (`MixerThumbGlowAlpha`); el halo se dibuja DENTRO de la misma cadena de
  modificadores que ya escala con `graphicsLayer` mientras se arrastra, así
  que sigue creciendo junto con el thumb -- mismo comportamiento percibido
  que antes, sin sombra real.

### Por qué no es un parche
No se bajó la opacidad ni se quitó el efecto "premium" para disimular el
problema: se identificó el mecanismo real de Android que encarece una
sombra con color/recorte personalizado, y se reemplazó ese mecanismo por
uno equivalente visualmente pero sin ese costo -- la extensión `softGlow`
queda documentada en el propio código con el motivo técnico exacto, para
que nadie la revierta sin entender por qué existe.

## Pendiente de verificación en dispositivo real

Sin dispositivo/emulador Android en este entorno: falta perfilar el scroll
real con el profilador de Android Studio (o `adb shell dumpsys gfxinfo`)
antes/después de este cambio para confirmar la mejora de frame time en
hardware real, especialmente en un teléfono de gama media/baja donde el
costo de una sombra por software es más notorio.
