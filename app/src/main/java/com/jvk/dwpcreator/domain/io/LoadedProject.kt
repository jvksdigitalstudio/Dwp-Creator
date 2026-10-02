package com.jvk.dwpcreator.domain.io

import com.jvk.dwpcreator.domain.dwp.DwpDocument

/**
 * A fully loaded, in-memory project: the parsed [document] plus every
 * sample's raw audio bytes, matched **by position** (not by name) to
 * `DwpEngine.listSamples(document)`.
 *
 * Matching by index instead of by name is deliberate: after
 * [com.jvk.dwpcreator.domain.dwp.DwpEngine.renameInstrument] the sample
 * names change, but the audio bytes obviously haven't moved — using the
 * stable file order avoids ever having to re-match audio to name after an
 * edit.
 */
data class LoadedProject(
    val document: DwpDocument,
    val dwpZipEntryName: String,
    val audioByIndex: List<ByteArray>,
    val originalZipEntryNames: List<String>
)
