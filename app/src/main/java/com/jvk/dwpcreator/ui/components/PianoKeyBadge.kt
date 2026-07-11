package com.jvk.dwpcreator.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.ui.theme.BlackKeyBg
import com.jvk.dwpcreator.ui.theme.BlackKeyText
import com.jvk.dwpcreator.ui.theme.WhiteKeyBg
import com.jvk.dwpcreator.ui.theme.WhiteKeyText

/**
 * A single small piano-key tile: white or black depending on the note,
 * with a colored accent border matching the sample's octave color.
 * [active] lights it up (used for MIDI/touch playback feedback later).
 */
@Composable
fun PianoKeyBadge(
    note: String,
    accentColor: Color,
    active: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isBlack = note.contains("#")
    val baseBg = if (isBlack) BlackKeyBg else WhiteKeyBg
    val bg by animateColorAsState(
        targetValue = if (active) accentColor else baseBg,
        label = "pianoKeyBg"
    )
    val textColor = if (active) {
        if (isBlack) Color.White else Color.Black
    } else {
        if (isBlack) BlackKeyText else WhiteKeyText
    }

    Box(
        modifier = modifier
            .width(58.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .border(1.dp, accentColor.copy(alpha = if (active) 1f else 0.55f), RoundedCornerShape(7.dp))
            .padding(bottom = 4.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = note,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}
