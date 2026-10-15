package com.jvk.dwpcreator.ui.components.sampler.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvk.dwpcreator.ui.theme.ActiveAccentTextDark
import com.jvk.dwpcreator.ui.theme.MixerControlBorder
import com.jvk.dwpcreator.ui.theme.MixerTrackGradientBottom
import com.jvk.dwpcreator.ui.theme.NeonCyan
import com.jvk.dwpcreator.ui.theme.TextDim

/** Selector de 2+ opciones excluyentes (POLY/MONO, NORMAL/BOUNCE). */
@Composable
fun FxSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, MixerControlBorder, RoundedCornerShape(6.dp))
    ) {
        options.forEachIndexed { index, text ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .background(if (selected) NeonCyan else MixerTrackGradientBottom)
                    .clickable(role = Role.RadioButton) { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = if (selected) ActiveAccentTextDark else TextDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
