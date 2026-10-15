package com.jvk.dwpcreator.ui.components.sampler

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvk.dwpcreator.ui.theme.NeonCyan
import com.jvk.dwpcreator.ui.theme.TextDim

/** Barra de pestañas del panel del sampler; se desplaza en horizontal si no caben todas. */
@Composable
fun SamplerPanelTabBar(
    selected: SamplerPanelTab,
    onSelect: (SamplerPanelTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.horizontalScroll(rememberScrollState())) {
        for (tab in SamplerPanelTab.entries) {
            val isSelected = tab == selected
            Column(
                modifier = Modifier
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tab.title,
                    color = if (isSelected) NeonCyan else TextDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .height(2.dp)
                        .width(24.dp)
                        .background(if (isSelected) NeonCyan else Color.Transparent)
                )
            }
        }
    }
}
