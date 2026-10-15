package com.jvk.dwpcreator.ui.components.sampler.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvk.dwpcreator.ui.theme.MixerControlBorder
import com.jvk.dwpcreator.ui.theme.NeonGreen
import com.jvk.dwpcreator.ui.theme.NeonPurple
import com.jvk.dwpcreator.ui.theme.SurfacePurple
import com.jvk.dwpcreator.ui.theme.TextDim

/**
 * Tarjeta de un módulo del sampler (MASTER, FX DRIVE A, FX DELAY...): título,
 * interruptor de encendido opcional y el contenido del módulo.
 *
 * [powered] = `null` -> el módulo no se puede apagar (MASTER, sin LED).
 * [headerExtras] se dibuja a la derecha del LED (selector de presets).
 * [preview] es una visualización opcional (curva de envolvente, respuesta del
 * filtro...) que se dibuja entre la cabecera y los controles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FxModuleCard(
    title: String,
    powered: Boolean?,
    onPoweredChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    headerExtras: @Composable () -> Unit = {},
    preview: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfacePurple)
            .border(1.dp, MixerControlBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = if (powered == false) TextDim else NeonPurple,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (powered != null) {
                // Zona táctil de 32 dp alrededor de un LED de 10 dp.
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(role = Role.Switch) { onPoweredChange(!powered) }
                        .semantics { contentDescription = "$title encendido" },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (powered) NeonGreen else MixerControlBorder)
                    )
                }
            }
            headerExtras()
        }
        preview()
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            content()
        }
    }
}
