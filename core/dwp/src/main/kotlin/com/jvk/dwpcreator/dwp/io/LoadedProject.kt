package com.jvk.dwpcreator.dwp.io

import com.jvk.dwpcreator.dwp.DwpDocument

/**
 * A fully loaded, in-memory project: the parsed [document] plus every
 * sample's raw audio bytes, matched **by position** (not by name) to
 * `DwpSampleCatalog.listSamples(document)`.
 *
 * Matching by index instead of by name is deliberate: after
 * [com.jvk.dwpcreator.dwp.DwpRenamer.renameInstrument] the sample
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
