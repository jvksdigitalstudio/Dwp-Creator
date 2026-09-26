package com.jvk.dwpcreator.domain.dwp

/**
 * UI-friendly summary of one sample container inside a [DwpDocument],
 * extracted by [DwpEngine.listSamples]. Purely derived data — never the
 * source of truth (that's always the underlying [DwpBlock]s).
 */
data class SampleInfo(
    val index: Int,
    val name: String,
    val note: String,
    val velocity: Int,
    val lowKey: Int,
    val rootKey: Int,
    val highKey: Int,
    val frameCount: Int,
    val dwpPath: String
) {
    val isBlackKey: Boolean get() = note.contains("#")
    val isOctaveStart: Boolean get() = note.startsWith("C") && !note.startsWith("C#")
}
