package com.jvk.dwpcreator.ui.components.mixer

import androidx.compose.ui.unit.dp

/** Diámetro del "thumb" arrastrable de Pan/Volumen en reposo -- crece ligeramente mientras se arrastra, ver [DraggingThumbScale]. */
internal val MixerThumbDiameter = 13.dp

/** Cuánto crece el "thumb" (factor de escala) mientras el dedo lo tiene agarrado -- retroalimentación táctil, no solo visual. */
internal const val DraggingThumbScale = 1.35f
