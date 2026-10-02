# 43 — Columna de nombre calculada (no adivinada) + separador vertical nombre/mezclador

## Reporte del usuario

Tres capturas anotadas a mano sobre la lista de muestras:

1. Captura del estado roto: nombres cortados con "…" (`INSTRUMENT_C3...`),
   y el mezclador (Mute/Solo/Pan/Volumen) empezando en una posición X
   distinta en cada fila -- la lista se ve "desordenada" en vez de en
   columnas rectas.
2. Captura de referencia con la disposición correcta: número, nombre
   completo, mezclador, tecla de piano -- pero **alineado**, sin cortes.
3. Aclaración explícita del usuario tras la primera corrección: *"la
   ubicación estaba bien, solo ordena/alinea ese mini mezclador y que no
   choque con el texto del name de los samples para que no se vean
   cortados, calcula"* -- confirma que la posición relativa (nombre y
   mezclador juntos, hueco después, tecla al final) no se toca; el problema
   real es el cálculo del ancho de la columna de nombre.
4. Cuarta captura, ya con el nombre corregido: pide una línea blanca que
   separe visualmente el texto del nombre de la franja de mezclador.

## Diagnóstico real (no un síntoma, la causa)

`SampleRow.kt` usaba `Modifier.widthIn(max = 150.dp)` en el nombre -- un
número fijo **adivinado**, con dos problemas reales, no cosméticos:

1. **Corte de texto real**: `INSTRUMENT_C3_127` (la etiqueta de inicio de
   octava, en mayúsculas y `FontWeight.ExtraBold` a 16sp monospace) mide más
   de 150dp de ancho real -- por diseño, ese `widthIn(max=150dp)` la corta
   con "…" siempre, no es un caso límite raro.
2. **Desalineación entre filas**: al ser un *máximo* (`widthIn`), no un
   ancho fijo, cada fila con un nombre más corto ("D3", "E3"...) encogía su
   columna al tamaño real de ESE texto. Como el mezclador va pegado
   inmediatamente después del nombre, su posición X terminaba siendo
   distinta en cada fila -- de ahí el aspecto "desordenado" de la captura 1,
   no un bug de posicionamiento sino de **ancho no uniforme**.

## Corrección real aplicada

### `ui/components/SampleRow.kt`
- Se elimina `NameMaxWidth` (150dp adivinado) y `Modifier.widthIn(max = ...)`.
- Nuevo parámetro `nameColumnWidth: Dp`, con `Modifier.width(nameColumnWidth)`
  -- un ancho FIJO, idéntico en todas las filas, calculado fuera de esta
  función (ver `MainScreen` más abajo). `TextOverflow.Ellipsis` se conserva
  únicamente como resguardo ante un nombre personalizado patológicamente
  largo tras un renombrado manual -- ya no como mecanismo principal.
- Nuevas constantes públicas `NameColumnMinWidth` (110dp) / `NameColumnMaxWidth`
  (220dp) que acotan ese cálculo (fuente única de verdad, igual que
  `PianoKeyWidth`/`SampleRowEndPadding`).
- **Separador vertical nuevo** entre el nombre y la franja de mezclador: un
  `Box` de 1dp de ancho, color `NameMixerColumnDivider` (blanco ~30%),
  pedido explícito de la última captura anotada.
  - **Detalle real de layout, no un accesorio decorativo cualquiera**: esta
    fila vive dentro de un `LazyColumn`, que mide cada item con altura
    MÁXIMA NO ACOTADA. Un `Modifier.fillMaxHeight()` en ese contexto se
    resuelve silenciosamente al alto MÍNIMO (0dp) -- la línea compilaría y
    se vería bien en un preview aislado, pero sería invisible en la app
    real. Se ancla en cambio a `PianoKeyHeight` (la misma fuente única de
    verdad del alto real de la fila) menos un margen vertical, con
    `Modifier.height(...)` explícito.
- Orden final de la fila, sin cambios respecto a lo que el usuario confirmó
  que ya estaba bien: `[número][nombre, ancho fijo][línea divisoria][mezclador][Spacer(weight=1f)][tecla]`.

### `ui/screens/MainScreen.kt`
- Nueva función privada `rememberNameColumnWidth(samples)`: mide con
  `rememberTextMeasurer()` -- la tipografía REAL de cada tipo de fila
  (negrita/mayúscula en el Do de cada octava vs. normal en el resto,
  exactamente como las renderiza `SampleRow`) -- el ancho en píxeles de
  **cada** nombre del instrumento cargado, toma el más ancho de todos, y lo
  acota entre `NameColumnMinWidth`/`NameColumnMaxWidth`.
- Ese resultado (`nameColumnWidth`) se calcula UNA sola vez por instrumento
  cargado (`remember(samples, ...)`, no en cada recomposición) y se pasa
  igual a todas las filas del `LazyColumn`.
- Por qué esto es correcto y no un parche: ya no hay ningún número
  "adivinado" en el código -- el ancho de columna se deriva siempre de los
  nombres reales del instrumento que el usuario cargó, así que ningún
  nombre real se corta, y todas las filas comparten exactamente el mismo
  ancho de columna -- el mezclador y la tecla de piano quedan alineados en
  columnas rectas, fila tras fila, sin importar cuánto mida cada nombre
  individual.

### `ui/theme/Color.kt`
- Nuevo color `NameMixerColumnDivider` (blanco ~30%), documentado como
  distinto de `SampleRowDivider` (el separador horizontal entre filas, más
  discreto por repetirse en 48+ filas) porque esta línea es corta y
  necesita notarse de un vistazo como límite de columna.

## Pendiente de verificación en dispositivo real

Sin dispositivo/emulador Android en este entorno: falta confirmar en
hardware real que el ancho calculado para el nombre más largo del
instrumento típico (`INSTRUMENT_C3_127`/`Instrument_C#3_127`) deja espacio
cómodo para el mezclador + la tecla de piano en los teléfonos más angostos
del mercado. Si un instrumento con nombres excepcionalmente largos
(renombrado manual) empuja la columna hasta el tope de `NameColumnMaxWidth`
y el mezclador queda visualmente apretado, la vía ya prevista (ver
`Doc/41`) es mover la franja de mezclador a una segunda línea por muestra.
