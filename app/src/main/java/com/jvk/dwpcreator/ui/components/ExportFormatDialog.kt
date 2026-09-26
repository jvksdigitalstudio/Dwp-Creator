package com.jvk.dwpcreator.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * FASE 2 (integración real de Monolithic DWP): elegir el formato de
 * exportación. Antes de esta pasada solo existía el ZIP clásico
 * (`.dwp` + un `.wav` por muestra); ahora existe además un `.dwp`
 * Monolítico autocontenido (audio embebido, sin `.wav` externos).
 *
 * Deliberadamente sin jerga interna (tags, offsets, FLAC frames, CRC --
 * Sección 22 del prompt maestro de la fase de especificación: "el UI NO
 * debe conocer" esos detalles): la diferencia se explica en términos que
 * el usuario final puede evaluar (cuántos archivos produce, si hace falta
 * arrastrar `.wav` sueltos), no en términos del formato binario.
 */
@Composable
fun ExportFormatDialog(
    onExportZip: () -> Unit,
    onExportMonolithic: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar instrumento") },
        text = {
            Column {
                ExportOptionRow(
                    title = "ZIP (recomendado)",
                    description = "Un .dwp más un .wav por cada muestra, en una carpeta -- el formato que " +
                        "esta app siempre ha generado, pensado para FL Studio Mobile.",
                    onClick = onExportZip
                )
                Spacer(Modifier.height(12.dp))
                ExportOptionRow(
                    title = ".dwp autocontenido (experimental)",
                    description = "Un único archivo .dwp con el audio de cada muestra embebido dentro -- " +
                        "sin .wav sueltos que perder. Su compatibilidad con DirectWave/FL Studio real " +
                        "todavía no está confirmada: pruébalo antes de depender de él.",
                    onClick = onExportMonolithic
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun ExportOptionRow(title: String, description: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
