package com.jvk.dwpcreator.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.ui.theme.BgDark
import com.jvk.dwpcreator.ui.theme.NeonCyan
import com.jvk.dwpcreator.ui.theme.NeonGreen
import com.jvk.dwpcreator.ui.theme.NeonPurple
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

@Composable
private fun EqualizerBars() {
    val colors = listOf(NeonPurple, NeonCyan, NeonGreen, NeonCyan, NeonPurple)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.forEachIndexed { i, color ->
            val transition = rememberInfiniteTransition(label = "eq$i")
            val height by transition.animateFloat(
                initialValue = 8f,
                targetValue = 36f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 420 + i * 60, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "eqHeight$i"
            )
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}
