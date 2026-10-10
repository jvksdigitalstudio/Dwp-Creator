package com.jvk.dwpcreator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.ui.theme.BgDark
import com.jvk.dwpcreator.ui.theme.NeonPurple
import com.jvk.dwpcreator.ui.theme.SurfacePurpleAlt

/**
 * Barra superior de acciones.
 *
 * [loadEnabled] y [projectActionsEnabled] existen para que la UI nunca deje
 * lanzar una operación mientras otra está en curso ni una que no tiene nada
 * sobre lo que operar: durante una importación/exportación se desactivan
 * LOAD/RENOMBRAR/EXPORT (antes seguían pulsables y podían lanzar una segunda
 * operación en paralelo sobre el mismo estado), y RENOMBRAR/EXPORT tampoco
 * se ofrecen mientras no hay ningún instrumento cargado (antes eran un
 * no-op silencioso). MIDI no depende del proyecto, así que nunca se
 * desactiva.
 */
@Composable
fun DwpTopBar(
    onLoad: () -> Unit,
    onRenameAll: () -> Unit,
    onMidi: () -> Unit,
    onExport: () -> Unit,
    midiConnected: Boolean = false,
    loadEnabled: Boolean = true,
    projectActionsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BgDark)
            // La app dibuja de borde a borde (enableEdgeToEdge en MainActivity) y
            // Scaffold da por hecho que un `topBar` gestiona por sí mismo la
            // inserción de la barra de estado -- Material TopAppBar lo hace,
            // esta barra propia no lo hacía: en un móvil con barra de estado o
            // notch, los botones quedaban debajo de la hora/iconos. El fondo se
            // pinta ANTES del padding para que el color siga cubriendo esa zona.
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TopBarButton("LOAD", Icons.Default.Upload, onLoad, enabled = loadEnabled)
        TopBarButton(
            "RENOMBRAR",
            Icons.Default.DriveFileRenameOutline,
            onRenameAll,
            enabled = projectActionsEnabled
        )
        TopBarButton(
            label = if (midiConnected) "MIDI ●" else "MIDI",
            icon = Icons.Default.Piano,
            onClick = onMidi
        )
        TopBarButton("EXPORT", Icons.Default.Download, onExport, enabled = projectActionsEnabled)
    }
}

@Composable
private fun RowScope.TopBarButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SurfacePurpleAlt,
            contentColor = NeonPurple,
            disabledContainerColor = SurfacePurpleAlt.copy(alpha = 0.45f),
            disabledContentColor = NeonPurple.copy(alpha = 0.40f)
        ),
        contentPadding = PaddingValues(horizontal = 6.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.height(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
