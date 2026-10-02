package com.jvk.dwpcreator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.ui.theme.BgDark
import com.jvk.dwpcreator.ui.theme.TextDim

@Composable
fun ImportingOverlay(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EqualizerBars()
        Spacer(Modifier.height(20.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextDim)
    }
}
