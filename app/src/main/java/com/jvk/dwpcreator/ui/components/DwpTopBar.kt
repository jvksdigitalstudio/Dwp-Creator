package com.jvk.dwpcreator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

@Composable
fun DwpTopBar(
    onLoad: () -> Unit,
    onRenameAll: () -> Unit,
    onMidi: () -> Unit,
    onExport: () -> Unit,
    midiConnected: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BgDark)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "DWP CREATOR",
            style = MaterialTheme.typography.titleLarge,
            color = NeonPurple
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TopBarButton("LOAD", Icons.Default.Upload, onLoad)
            TopBarButton("RENOMBRAR", Icons.Default.DriveFileRenameOutline, onRenameAll)
            TopBarButton(
                label = if (midiConnected) "MIDI ●" else "MIDI",
                icon = Icons.Default.Piano,
                onClick = onMidi
            )
            TopBarButton("EXPORT", Icons.Default.Download, onExport)
        }
    }
}

@Composable
private fun RowScope.TopBarButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SurfacePurpleAlt,
            contentColor = NeonPurple
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
