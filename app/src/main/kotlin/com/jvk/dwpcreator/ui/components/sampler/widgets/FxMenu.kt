package com.jvk.dwpcreator.ui.components.sampler.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jvk.dwpcreator.ui.theme.SurfacePurpleAlt

/** Menú desplegable con el aspecto del panel: base de los selectores de preset y de opción. */
@Composable
fun FxMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(SurfacePurpleAlt),
        content = content
    )
}
