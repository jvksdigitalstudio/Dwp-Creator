package com.jvk.dwpcreator.ui.components.sample

import androidx.compose.ui.unit.dp

/**
 * Padding horizontal de cada fila -- fuente única de verdad, reutilizada
 * por [MainScreen][com.jvk.dwpcreator.ui.screens.MainScreen] para calcular
 * el ancho exacto de la columna de teclas (banda derecha) al implementar
 * el overlay de gestos multi-touch. No duplicar este número.
 */
val SampleRowEndPadding = 16.dp

/**
 * Ancho de la columna del nombre -- **fijo e idéntico en todas las filas**,
 * nunca calculado fila a fila a partir del propio texto.
 *
 * Historial del bug real (capturas anotadas del usuario, layout "roto"):
 * primero se usó `Modifier.weight(1f)` en el nombre, que reclamaba TODO el
 * espacio sobrante de la fila y empujaba el mezclador contra la tecla de
 * piano. La corrección siguiente cambió a `Modifier.widthIn(max = 150.dp)`
 * -- un tope fijo "adivinado" que:
 *  1. Cortaba con "…" cualquier nombre real más largo que 150dp (p. ej.
 *     `INSTRUMENT_C3_127` en mayúsculas/negrita, la etiqueta de inicio de
 *     octava, que a 16sp monospace en negrita ya no entra en ese ancho).
 *  2. Al ser un *máximo* y no un ancho fijo, cada fila con un nombre más
 *     corto encogía su columna a su propio contenido -- por eso el
 *     mezclador (pegado inmediatamente después del nombre) empezaba en una
 *     posición X distinta en cada fila, y la lista se veía "desalineada"
 *     verticalmente en vez de en columnas rectas.
 *
 * La corrección real (no un parche sobre el número 150) es medir con
 * [androidx.compose.ui.text.TextMeasurer] el ancho que de verdad necesita el
 * nombre más largo del instrumento cargado -- en las DOS tipografías reales
 * que puede usar esta fila (ver `isOctaveStart` en [SampleRow]) -- y pasar
 * ese resultado como [Dp] fijo a **todas** las filas via [nameColumnWidth].
 * Así ningún nombre real se corta y la columna queda perfectamente alineada
 * en vertical, fila tras fila, sin volver a adivinar un número.
 *
 * [NameColumnMinWidth]/[NameColumnMaxWidth] acotan ese cálculo: el mínimo
 * evita una columna ridículamente angosta si todos los nombres son cortos,
 * y el máximo es la última red de seguridad para un nombre personalizado
 * patológicamente largo (tras un renombrado manual) -- ahí, y solo ahí,
 * sigue aplicando `TextOverflow.Ellipsis` como resguardo, nunca como
 * mecanismo principal.
 */
val NameColumnMinWidth = 110.dp

val NameColumnMaxWidth = 220.dp
