# 40 — Eliminación del menú "Preview" y separador entre filas

## Reporte del usuario

Dos capturas anotadas a mano:

1. Al presionar/mantener presionada una fila de muestra aparecía un menú
   flotante con una única opción, "Preview" — pedido explícito: eliminarlo
   por completo, que no aparezca nada.
2. Pedido de un separador fino (blanco, no grueso) entre cada muestra, de
   extremo a extremo (todo el ancho de la pantalla), como en la referencia.

## Corrección #1 — menú "Preview" eliminado

`SampleRow` usaba `combinedClickable(onClick = onPreview, onLongClick = {
menuOpen = true })`, donde `onLongClick` desplegaba un `DropdownMenu` con un
único ítem ("Preview") que, al pulsarlo, llamaba exactamente al mismo
callback que ya dispara el toque simple (`onClick = onPreview`) — es decir,
el menú entero era **puramente redundante**: nunca ofrecía nada que el toque
simple no hiciera ya, y además duplicaba la reproducción real de audio, que
de todas formas ya vive en la columna de teclas de piano (toque/arrastre/
acorde, con nota sostenida real -- ver `Doc/39`).

Se eliminó `combinedClickable`, `onLongClick`, el estado `menuOpen` y la
función `SampleContextMenu` (con su `DropdownMenu`/`DropdownMenuItem`)
completos. `SampleRow` ahora usa `Modifier.clickable(onClick = onPreview)`
simple: un toque reproduce la muestra (igual que antes), mantener presionado
ya no dispara ningún menú ni diálogo -- no queda ningún manejador de
long-press en la fila.

Efecto secundario limpio: al quitar `combinedClickable` ya no hace falta el
`@OptIn(ExperimentalFoundationApi::class)` de la función, ni los imports de
`DropdownMenu`, `DropdownMenuItem`, `Icon`, `Icons.Default.PlayArrow` ni
`RowScope` (solo se usaba como receptor del menú eliminado) -- todos fuera.

## Corrección #2 — separador fino entre cada muestra, de extremo a extremo

Nuevo color de tema `SampleRowDivider` (`ui/theme/Color.kt`): blanco al
~16% de opacidad (`0x29FFFFFF`), no blanco sólido -- una línea 100% blanca
sobre el fondo morado oscuro, repetida en 48+ filas seguidas, resultaría
demasiado dura a la vista; a esta opacidad se percibe nítidamente como una
línea blanca fina sin competir con el texto ni con el degradado de las
teclas.

En `SampleRow`, la línea se dibuja con `Modifier.drawWithContent` **antes**
de que la cadena de modificadores aplique el padding horizontal de
contenido (`SampleRowEndPadding`): en ese punto de la cadena, el `size`
disponible en el `DrawScope` sigue siendo el ancho **completo** de la fila
(borde a borde de la pantalla) -- ese orden es lo que logra el "de extremo a
extremo" pedido, sin que el padding interno del número/nombre/tecla la
recorte por los costados. Grosor: 1dp (`DividerThickness`) -- fino,
explícitamente no grueso.

## Por qué no es un parche

No se ocultó el menú con una bandera ni se le puso alpha 0 -- se borró el
código entero que lo construía (estado, función, imports), porque no existe
ningún caso de uso real que lo necesite. El separador no es un `Divider`
superpuesto como elemento adicional en el árbol de la `LazyColumn`: es un
único `drawLine` sobre el propio `Row` de cada muestra, en el punto exacto
de la cadena de modificadores donde el ancho disponible es el total de la
pantalla -- consistente con cómo ya se dibuja el resto de esta fila
(fondo, degradado de tecla, etc.), no una capa aparte.
