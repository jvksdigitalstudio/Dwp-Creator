package com.jvk.dwpcreator.ui.components.sampler.widgets

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/** Entrada de un [FxMenu]: texto de un solo color (cian = elegida, atenuado = resto). */
@Composable
fun FxMenuItem(text: String, color: Color, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text = text, color = color, fontSize = 13.sp) }, onClick = onClick)
}
