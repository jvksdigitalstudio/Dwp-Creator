# 42 — Mezclador al lado izquierdo (junto al nombre) + rediseño premium

## Reporte del usuario

Captura anotada: la franja de Mute/Solo/Pan/Volumen quedó pegada a la tecla
de piano (lado derecho). Pedido explícito: moverla al lado **izquierdo**,
junto al **nombre** de cada muestra, lejos de la tecla. Además: mejorar el
slider de volumen y los iconos de las opciones a un nivel "premium
profesional" -- nada básico ni experimental.

## Corrección #1 — reubicación

`SampleRow`: orden original `[número][nombre, weight(1f)][mezclador][tecla]`
→ primer intento `[número][mezclador][nombre, weight(1f)][tecla]` (mezclador
pegado al número, delante del nombre) → segundo intento
`[número][nombre, weight(1f)][mezclador][tecla]` (mezclador después del
nombre, pero el nombre seguía con `weight(1f)`).

**El bug real, encontrado tras la tercera captura anotada del usuario**: con
`weight(1f)` en el nombre, el nombre reclama TODO el espacio sobrante de la
fila -- sin importar en qué orden estuvieran declarados los composables
alrededor. El resultado visual era el mezclador empujado hasta quedar pegado
contra la tecla de piano, exactamente lo contrario de lo pedido, aunque en
el código ya estuviera "después del nombre". El orden del código nunca fue
el problema real; el problema era que un `weight` en el elemento equivocado
reserva ese espacio pase lo que pase con el resto de la fila.

**Corrección final**: el nombre pasa a `Modifier.widthIn(max = 150.dp)` (se
encoge a su propio contenido si es corto, trunca con "…" solo si no entra en
ese tope) en vez de `weight`. El mezclador queda pegado inmediatamente
después, sin hueco. El `Spacer(Modifier.weight(1f))` que antes tenía el
nombre se mueve a DESPUÉS del mezclador y ANTES de la tecla de piano -- es
ese `Spacer`, no el nombre, el que absorbe el espacio sobrante de la fila y
empuja la tecla a su columna fija del borde derecho. Orden final:
`[número][nombre, ancho acotado][mezclador][Spacer(weight=1f)][tecla]`.

## Corrección #2 — rediseño premium

Redise~ño completo de `SampleMixerControls.kt`, reutilizando **la misma
receta visual que ya usan las teclas del piano** (`PianoKeyBadge`) en vez de
inventar un estilo aparte:

- **Degradado vertical + veta de brillo superior** en los botones Mute/Solo
  y en los rieles de Pan/Volumen -- la misma técnica de "superficie física
  con luz cayendo desde arriba" que las teclas, no un relleno plano.
- **Mute/Solo imitan un botón físico de consola**: elevados (con sombra) en
  reposo, hundidos (sin sombra, superficie más oscura) al activarse -- se
  "sienten" pulsados, no solo coloreados. El color de "activo" se deriva del
  tono base con `lerp` (igual que la tecla activa por octava), no es un
  color fijo aparte.
- **Iconos reales en vez de letras sueltas**: `VolumeOff`/`VolumeUp` para
  Mute (cambia según el estado), `Headset` para Solo -- el icono estándar de
  "escuchar en solitario" en cualquier DAW. El tinte del icono se calcula
  por la luminancia real del color activo (mismo mecanismo que el texto de
  la tecla activa), no un color fijo por caso.
- **Slider de volumen tipo fader de consola real**: el relleno ya no es un
  color sólido, es un degradado (más oscuro a más brillante) que se "carga"
  hacia el extremo derecho a medida que sube el volumen. Marcas discretas
  "−"/"+" en los extremos del riel (Pan: "L"/"R").
- **"Thumb" (perilla) con relieve real**: degradado radial claro -> color
  (no un círculo plano) con sombra propia, y **crece y brilla mientras se
  arrastra** (retroalimentación táctil real, vía `graphicsLayer` +
  `animateFloatAsState`, no solo un cambio de color).

### Por qué no es un parche
No se tocaron colores sueltos por encima del diseño anterior: se reescribió
la superficie compartida de los tres controles (`MixerTrackSurface`) y el
"thumb" compartido (`MixerThumb`) desde cero, reutilizando exactamente el
mismo mecanismo de degradado + brillo + `lerp` por luminancia que
`PianoKeyBadge`, para que el mezclador se vea como una pieza más del mismo
instrumento, no como un panel ajeno pegado encima.

## Pendiente de verificación en dispositivo real
Sin dispositivo/emulador Android en este entorno: falta confirmar en
hardware real la legibilidad de las marcas "L"/"R"/"−"/"+" a este tamaño, y
que el ancho reservado para el nombre siga siendo cómodo en los teléfonos
más angostos del mercado con el mezclador ahora también compitiendo por
espacio desde el lado izquierdo.
