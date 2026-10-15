package com.jvk.dwpcreator.ui.components.mixer

/** Posición X (px, dentro del propio riel) -> fracción 0f..1f, saturada a los extremos del riel. */
internal fun xToFraction(xPx: Float, widthPx: Float): Float =
    if (widthPx <= 0f) 0f else (xPx / widthPx).coerceIn(0f, 1f)

/** Posición X (px, dentro del propio riel) -> pan -1f..1f, saturada a los extremos del riel. */
internal fun xToPan(xPx: Float, widthPx: Float): Float = (xToFraction(xPx, widthPx) * 2f) - 1f
