package com.jvk.dwpcreator.domain.dwp

/**
 * High-level operations on a parsed [DwpDocument]: renaming, patching a
 * sample's cached frame count after an audio swap, and summarizing samples
 * for the UI.
 *
 * Everything here is built strictly on top of the generic, audit-verified
 * [DwpTokenizer] — no operation assumes anything about the file beyond the
 * tag shapes documented on [DwpBlock]. In particular, renaming NEVER touches
 * raw/binary payloads (key ranges, audio format, envelopes, the 99-slot
 * parameter table); it only rewrites payloads that decode as printable text
 * and literally contain the old name.
 */
object DwpEngine {

    private val SAMPLE_NAME_REGEX = Regex("""^(.*)_([A-G]#?-?\d+)_(\d+)$""")

    /**
     * Replaces every textual occurrence of [oldName] with [newName]: the
     * instrument name, the dwp's own saved path, and every sample's name +
     * path (recursing into each sample container). Each rewritten block gets
     * its own length recomputed; every other block is copied through
     * byte-for-byte untouched — including the 99-slot parameter table and
     * each sample's key range / audio format / envelope blocks.
     */
    fun renameInstrument(doc: DwpDocument, oldName: String, newName: String): DwpDocument {
        if (oldName.isEmpty() || oldName == newName) return doc
        return DwpDocument(doc.preamble, doc.blocks.map { renameRecursive(it, oldName, newName) })
    }

    private fun renameRecursive(block: DwpBlock, oldName: String, newName: String): DwpBlock {
        if (block.tag == DwpBlock.TAG_SAMPLE_CONTAINER) {
            val nested = DwpTokenizer.tokenize(block.payload)
            val renamedNested = nested.blocks.map { renameRecursive(it, oldName, newName) }
            return DwpBlock(block.tag, block.reserved, DwpTokenizer.serialize(renamedNested))
        }
        val text = block.textOrNull() ?: return block
        if (!text.contains(oldName)) return block
        return DwpBlock(block.tag, block.reserved, text.replace(oldName, newName).toByteArray(Charsets.ISO_8859_1))
    }

    /**
     * Updates the cached frame count for one sample (by its 0-based position
     * among sample containers) so it matches replacement audio of a
     * different duration. Verified against a real .wav: the first 4 bytes of
     * [DwpBlock.TAG_AUDIO_FORMAT] are exactly the PCM frame count
     * (data-chunk-size / bytes-per-frame).
     */
    fun patchFrameCount(doc: DwpDocument, sampleContainerIndex: Int, newFrameCount: Int): DwpDocument {
        var seen = -1
        val newBlocks = doc.blocks.map { top ->
            if (top.tag != DwpBlock.TAG_SAMPLE_CONTAINER) return@map top
            seen++
            if (seen != sampleContainerIndex) return@map top

            val nested = DwpTokenizer.tokenize(top.payload)
            val patchedNested = nested.blocks.map { inner ->
                if (inner.tag == DwpBlock.TAG_AUDIO_FORMAT) {
                    val newPayload = inner.payload.copyOf()
                    DwpTokenizer.writeLE32(newPayload, 0, newFrameCount)
                    DwpBlock(inner.tag, inner.reserved, newPayload)
                } else {
                    inner
                }
            }
            DwpBlock(top.tag, top.reserved, DwpTokenizer.serialize(patchedNested))
        }
        return DwpDocument(doc.preamble, newBlocks)
    }

    /** Extracts a human-readable summary of every sample container, for the UI list. */
    fun listSamples(doc: DwpDocument): List<SampleInfo> {
        val result = mutableListOf<SampleInfo>()
        var index = 0
        for (top in doc.blocks) {
            if (top.tag != DwpBlock.TAG_SAMPLE_CONTAINER) continue
            val nested = DwpTokenizer.tokenize(top.payload).blocks
            val byTag = nested.groupBy { it.tag }

            val name = byTag[DwpBlock.TAG_SAMPLE_NAME]?.firstOrNull()?.textOrNull() ?: "sample_$index"
            val path = byTag[DwpBlock.TAG_SAMPLE_PATH]?.firstOrNull()?.textOrNull() ?: ""
            val keyRange = byTag[DwpBlock.TAG_KEY_RANGE]?.firstOrNull()?.payload
            val audioFormat = byTag[DwpBlock.TAG_AUDIO_FORMAT]?.firstOrNull()?.payload

            val lowKey = keyRange?.getOrNull(0)?.let { it.toInt() and 0xFF } ?: -1
            val rootKey = keyRange?.getOrNull(1)?.let { it.toInt() and 0xFF } ?: -1
            val highKey = keyRange?.getOrNull(2)?.let { it.toInt() and 0xFF } ?: -1
            val frameCount = audioFormat?.let { DwpTokenizer.readLE32(it, 0) } ?: -1

            val m = SAMPLE_NAME_REGEX.find(name)
            result.add(
                SampleInfo(
                    index = index,
                    name = name,
                    note = m?.groupValues?.get(2) ?: "?",
                    velocity = m?.groupValues?.get(3)?.toIntOrNull() ?: -1,
                    lowKey = lowKey,
                    rootKey = rootKey,
                    highKey = highKey,
                    frameCount = frameCount,
                    dwpPath = path
                )
            )
            index++
        }
        return result
    }

    /** Detects the common "<name>_<note>_<velocity>" prefix shared by every sample. */
    fun detectInstrumentBaseName(doc: DwpDocument): String? {
        val matches = listSamples(doc).mapNotNull { SAMPLE_NAME_REGEX.find(it.name) }
        if (matches.isEmpty()) return null
        return matches.groupingBy { it.groupValues[1] }.eachCount().maxByOrNull { it.value }?.key
    }
}
