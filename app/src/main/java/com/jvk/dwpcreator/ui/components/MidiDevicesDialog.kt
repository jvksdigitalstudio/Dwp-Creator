package com.jvk.dwpcreator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.midi.MidiDeviceSummary
import com.jvk.dwpcreator.ui.theme.NeonCyan
import com.jvk.dwpcreator.ui.theme.NeonGreen
import com.jvk.dwpcreator.ui.theme.SurfacePurpleAlt
import com.jvk.dwpcreator.ui.theme.TextDim

@Composable
fun MidiDevicesDialog(
    devices: List<MidiDeviceSummary>,
    connectedDeviceName: String?,
    onRefresh: () -> Unit,
    onConnect: (MidiDeviceSummary) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dispositivos MIDI")
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = NeonCyan)
                }
            }
        },
        text = {
            Column {
                if (devices.isEmpty()) {
                    Text(
                        "No se detectó ningún teclado MIDI. Conecta uno por USB (con adaptador OTG) " +
                            "o empareja uno por Bluetooth en los Ajustes del sistema, luego pulsa el ícono de actualizar.",
                        color = TextDim,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start
                    )
                } else {
                    devices.forEach { device ->
                        val isConnected = device.name == connectedDeviceName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfacePurpleAlt)
                                .clickable { if (!isConnected) onConnect(device) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(device.name, style = MaterialTheme.typography.bodyMedium)
                                Text(device.typeLabel, style = MaterialTheme.typography.labelSmall, color = TextDim)
                            }
                            if (isConnected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Conectado", tint = NeonGreen)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            if (connectedDeviceName != null) {
                TextButton(onClick = onDisconnect) { Text("Desconectar") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
        dismissButton = {
            if (connectedDeviceName != null) {
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        }
    )
}
