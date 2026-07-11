package com.jvk.dwpcreator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.ui.theme.BgDark
import com.jvk.dwpcreator.ui.theme.NeonPurple
import com.jvk.dwpcreator.ui.theme.TextDim

@Composable
fun DwpStatusBar(
    sampleCount: Int,
    octaveCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BgDark)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("$sampleCount samples", style = MaterialTheme.typography.labelSmall, color = TextDim)
        Text(
            "© 2026 by JVK's Music Company",
            style = MaterialTheme.typography.labelSmall,
            color = NeonPurple.copy(alpha = 0.7f)
        )
        Text("$octaveCount octaves", style = MaterialTheme.typography.labelSmall, color = TextDim)
    }
}
