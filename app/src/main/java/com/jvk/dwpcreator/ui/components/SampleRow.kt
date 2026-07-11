package com.jvk.dwpcreator.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.domain.dwp.SampleInfo
import com.jvk.dwpcreator.ui.theme.SurfacePurple
import com.jvk.dwpcreator.ui.theme.SurfacePurpleAlt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SampleRow(
    sample: SampleInfo,
    accentColor: Color,
    isPlaying: Boolean = false,
    onPreview: () -> Unit = {},
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (sample.isOctaveStart) SurfacePurpleAlt else SurfacePurple)
            .combinedClickable(
                onClick = onPreview,
                onLongClick = { menuOpen = true }
            )
            .padding(horizontal = 16.dp, vertical = if (sample.isOctaveStart) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = (sample.index + 1).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            modifier = Modifier.width(34.dp)
        )

        Text(
            text = sample.name.uppercase().takeIf { sample.isOctaveStart } ?: sample.name,
            style = if (sample.isOctaveStart) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (sample.isOctaveStart) FontWeight.ExtraBold else FontWeight.Normal,
            color = accentColor,
            modifier = Modifier.weight(1f)
        )

        PianoKeyBadge(note = sample.note, accentColor = accentColor, active = isPlaying)

        SampleContextMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onPreview = { menuOpen = false; onPreview() },
            onRename = { menuOpen = false; onRename() },
            onDelete = { menuOpen = false; onDelete() }
        )
    }
}

@Composable
private fun RowScope.SampleContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPreview: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Preview") },
            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
            onClick = onPreview
        )
        DropdownMenuItem(
            text = { Text("Renombrar") },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
            onClick = onRename
        )
        DropdownMenuItem(
            text = { Text("Eliminar") },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
            onClick = onDelete
        )
    }
}
