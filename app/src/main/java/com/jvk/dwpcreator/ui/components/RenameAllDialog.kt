package com.jvk.dwpcreator.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jvk.dwpcreator.domain.dwp.InstrumentNameValidator

/**
 * Diálogo de "RENOMBRAR". Valida el nombre EN VIVO con
 * [InstrumentNameValidator] (mismas reglas que vuelve a comprobar el
 * ViewModel antes de tocar el proyecto): un nombre con acentos, ñ o emojis
 * hacía que el `.dwp` perdiera los nombres de todas sus muestras, y antes el
 * diálogo lo aceptaba sin decir nada.
 */
@Composable
fun RenameAllDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentName) }
    val candidate = text.trim()
    val validationError = if (candidate.isEmpty()) null else InstrumentNameValidator.validate(candidate)
    val canConfirm = candidate.isNotEmpty() && validationError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar instrumento") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Nuevo nombre") },
                isError = validationError != null,
                supportingText = if (validationError != null) { { Text(validationError) } } else null
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (canConfirm) onConfirm(candidate) },
                enabled = canConfirm
            ) {
                Text("Renombrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
