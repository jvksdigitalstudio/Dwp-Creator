package com.jvk.dwpcreator.domain.dwp

import com.jvk.dwpcreator.domain.audio.WavDecoder

/**
 * High-level operations on a parsed [DwpDocument]: renaming, patching a
 * sample's cached frame count after an audio swap, and summarizing samples
 * for the UI.
 *
 * <p>Renaming is deliberately narrow: it only ever rewrites the payload of
 * a tag in [RENAMABLE_TAGS] (instrument name/path, sample name/path) whose
 * semantics are actually confirmed. It never touches a block just because
 * it happens to decode as printable text — an earlier version did that, and
 * it was flagged as an unverified structural risk (a raw/binary field could
 * coincidentally be all-printable-bytes and get corrupted by a substring
 * match it had nothing to do with). Unknown/opaque blocks are copied through
 * unmodified regardless of what they contain.
 */
object DwpEngine {

    private val SAMPLE_NAME_REGEX = Regex("""^(.*)_([A-G]#?-?\d+)_(\d+)$""")

    /**
     * The only tags [renameInstrument] is allowed to rewrite. Deliberately a
     * closed allow-list, not "any block that looks like text": each of these
     * four tags has confirmed, demonstrated semantics (see [DwpBlock]'s KDoc
     * and Doc/05_DWP_IMPLEMENTATION_AUDIT.md) as an instrument/sample name or
     * path. No other tag is eligible for text substitution, no matter what
     * its payload decodes as.
     */
    private val RENAMABLE_TAGS = setOf(
        DwpBlock.TAG_INSTRUMENT_NAME,
        DwpBlock.TAG_PROJECT_PATH,
        DwpBlock.TAG_SAMPLE_NAME,
        DwpBlock.TAG_SAMPLE_PATH
    )

    /**
     * Replaces every textual occurrence of [oldName] with [newName], but
     * *only* inside [RENAMABLE_TAGS] (the instrument name, the dwp's own
     * saved path, and every sample's name + path, recursing into each sample
     * container). Each rewritten block gets its own length recomputed; every
     * other block -- including any block that happens to decode as
     * printable text but isn't one of the four known name/path tags -- is
     * copied through byte-for-byte untouched.
     */
    fun renameInstrument(doc: DwpDocument, oldName: String, newName: String): DwpDocument {
        if (oldName.isEmpty() || oldName == newName) return doc
        return DwpDocument(doc.preamble, doc.blocks.map { renameRecursive(it, oldName, newName) })
    }

    private fun renameRecursive(block: DwpBlock, oldName: String, newName: String): DwpBlock {
        if (block.tag == DwpBlock.TAG_SAMPLE_CONTAINER) {
            val nested = DwpTokenizer.tokenizeStrict(block.payload, context = "contenedor de sample")
            val renamedNested = nested.map { renameRecursive(it, oldName, newName) }
            return DwpBlock(block.tag, block.reserved, DwpTokenizer.serialize(renamedNested))
        }
        if (block.tag !in RENAMABLE_TAGS) return block
        val text = block.textOrNull() ?: return block
        if (!text.contains(oldName)) return block
        return DwpBlock(block.tag, block.reserved, text.replace(oldName, newName).toByteArray(Charsets.ISO_8859_1))
    }

    /**
     * The only payload size ever confirmed for [DwpBlock.TAG_AUDIO_FORMAT]
     * (0x01f7), verified against the real fixture's 48 samples. Any other
     * size means this .dwp does not match the format this engine was built
     * against, and must not be edited blindly.
     */
    private const val AUDIO_FORMAT_PAYLOAD_SIZE = 40

    /**
     * Locates the single [DwpBlock.TAG_AUDIO_FORMAT] block inside [nested]
     * (the already-tokenized contents of one sample container) and verifies
     * it's safe to patch: exactly one instance, with the one confirmed
     * payload size. Throws [DwpFormatException] -- never silently returns
     * null, never patches an ambiguous or malformed target -- for:
     *   - zero occurrences (nothing to patch);
     *   - more than one occurrence (ambiguous: which one is "the" real one?);
     *   - a payload size other than [AUDIO_FORMAT_PAYLOAD_SIZE].
     *
     * Called *before* building any patched block list in
     * [patchFrameCount]/[replaceSampleAudio], so a validation failure here
     * aborts the whole operation before a single byte is touched --
     * "invalid input -> error -> no document modified at all", never a
     * partially-patched result (Sección 5 del prompt maestro).
     */
    private fun requireSingleAudioFormatBlock(nested: List<DwpBlock>, context: String): DwpBlock {
        val matches = nested.filter { it.tag == DwpBlock.TAG_AUDIO_FORMAT }
        if (matches.isEmpty()) {
            throw DwpFormatException(
                "$context no contiene ningún bloque 0x01f7 (formato de audio); no hay nada seguro que parchear."
            )
        }
        if (matches.size > 1) {
            throw DwpFormatException(
                "$context contiene ${matches.size} bloques 0x01f7 (formato de audio); es ambiguo cuál de ellos " +
                    "representa el formato real, se rechaza la operación en vez de adivinar."
            )
        }
        val block = matches.single()
        if (block.payload.size != AUDIO_FORMAT_PAYLOAD_SIZE) {
            throw DwpFormatException(
                "$context: el bloque 0x01f7 tiene ${block.payload.size} bytes, se esperaban exactamente " +
                    "$AUDIO_FORMAT_PAYLOAD_SIZE; no coincide con el formato verificado, no se toca a ciegas."
            )
        }
        return block
    }

    /**
     * Updates the cached frame count for one sample (by its 0-based position
     * among sample containers) so it matches replacement audio of a
     * different duration. Verified against a real .wav: the first 4 bytes of
     * [DwpBlock.TAG_AUDIO_FORMAT] are exactly the PCM frame count
     * (data-chunk-size / bytes-per-frame).
     *
     * Requires exactly one 40-byte 0x01f7 block inside the target sample
     * container -- see [requireSingleAudioFormatBlock] -- and is atomic:
     * that validation runs before any block is rewritten, so a rejected
     * input never returns a partially-patched document.
     */
    fun patchFrameCount(doc: DwpDocument, sampleContainerIndex: Int, newFrameCount: Int): DwpDocument {
        requireValidSampleIndex(doc, sampleContainerIndex)
        require(newFrameCount >= 0) { "newFrameCount=$newFrameCount no puede ser negativo." }
        val containers = doc.blocks.filter { it.tag == DwpBlock.TAG_SAMPLE_CONTAINER }
        val target = containers[sampleContainerIndex]
        val context = "contenedor de sample #$sampleContainerIndex"
        val nested = DwpTokenizer.tokenizeStrict(target.payload, context = context)
        requireSingleAudioFormatBlock(nested, context) // validate BEFORE mutating anything

        var seen = -1
        val newBlocks = doc.blocks.map { top ->
            if (top.tag != DwpBlock.TAG_SAMPLE_CONTAINER) return@map top
            seen++
            if (seen != sampleContainerIndex) return@map top

            val patchedNested = nested.map { inner ->
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

    /**
     * Replaces one sample's audio metadata (and optionally its name/path) so
     * a real replacement WAV plays correctly — not just cosmetically renamed.
     * This supersedes [patchFrameCount] for real sample swaps: that function
     * only ever touched frame count, which is why exports built on it loaded
     * corrupt/garbled in FL Mobile whenever the replacement audio differed
     * from the template's original audio in *any* other way.
     *
     * Fields patched inside the 40-byte [DwpBlock.TAG_AUDIO_FORMAT] blob
     * (offsets verified against a real FL Studio Desktop export where every
     * sample was 44100Hz/stereo/32-bit float):
     *   - offset 0  (4 bytes): frame count
     *   - offset 8  (4 bytes): channel count
     *   - offset 12 (4 bytes): bytes per sample (2 = 16-bit int, 4 = 32-bit)
     *   - offset 16 (4 bytes): sample rate, stored as an IEEE-754 float32
     *   - offset 36 (4 bytes): bits per sample
     *
     * KNOWN UNVERIFIED RISK: the reference file used to reverse-engineer this
     * blob only contained 32-bit float samples. We do not have a confirmed
     * reference for 16-bit PCM, so there is a chance some other byte in this
     * blob (not among the 5 above) also encodes int-vs-float and simply
     * happened to be 0 in every sample of the reference file. Until that's
     * confirmed against a real 16-bit-sourced export tested in FL Mobile,
     * the safest first test is replacement audio that is ALREADY
     * 44100Hz/stereo/32-bit float, so frame count is the only field that
     * actually changes from the known-good baseline.
     */
    fun replaceSampleAudio(
        doc: DwpDocument,
        sampleContainerIndex: Int,
        newName: String,
        newPath: String,
        wav: WavDecoder.DecodedWav
    ): DwpDocument {
        requireValidSampleIndex(doc, sampleContainerIndex)
        val containers = doc.blocks.filter { it.tag == DwpBlock.TAG_SAMPLE_CONTAINER }
        val target = containers[sampleContainerIndex]
        val context = "contenedor de sample #$sampleContainerIndex"
        val nested = DwpTokenizer.tokenizeStrict(target.payload, context = context)
        requireSingleAudioFormatBlock(nested, context) // validate BEFORE mutating anything

        var seen = -1
        val newBlocks = doc.blocks.map { top ->
            if (top.tag != DwpBlock.TAG_SAMPLE_CONTAINER) return@map top
            seen++
            if (seen != sampleContainerIndex) return@map top

            val patchedNested = nested.map { inner ->
                when (inner.tag) {
                    DwpBlock.TAG_SAMPLE_NAME ->
                        DwpBlock(inner.tag, inner.reserved, newName.toByteArray(Charsets.ISO_8859_1))

                    DwpBlock.TAG_SAMPLE_PATH ->
                        DwpBlock(inner.tag, inner.reserved, newPath.toByteArray(Charsets.ISO_8859_1))

                    DwpBlock.TAG_AUDIO_FORMAT -> {
                        val p = inner.payload.copyOf()
                        DwpTokenizer.writeLE32(p, 0, wav.frameCount)
                        DwpTokenizer.writeLE32(p, 8, wav.channelCount)
                        DwpTokenizer.writeLE32(p, 12, wav.bytesPerSample)
                        DwpTokenizer.writeLE32(p, 16, wav.sampleRateHz.toFloat().toRawBits())
                        DwpTokenizer.writeLE32(p, 36, wav.bytesPerSample * 8)
                        DwpBlock(inner.tag, inner.reserved, p)
                    }

                    else -> inner
                }
            }
            DwpBlock(top.tag, top.reserved, DwpTokenizer.serialize(patchedNested))
        }
        return DwpDocument(doc.preamble, newBlocks)
    }

    /**
     * Extracts a human-readable summary of every sample container, for the
     * UI list. Deliberately uses the lenient [DwpTokenizer.tokenize] (not
     * [DwpTokenizer.tokenizeStrict]): this function only *reads* the nested
     * stream and never re-serializes it, so an unexpectedly-shaped container
     * can't silently lose bytes here the way it could in [renameRecursive]/
     * [patchFrameCount]/[replaceSampleAudio] (which DO reserialize and use
     * the strict variant). Worst case here is an incomplete/best-effort
     * [SampleInfo] for that one sample, not data loss.
     */
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

            // Physical order of 0x01f4 is [rootKey, lowKey, highKey] -- verified
            // against all 48 samples of the real fixture (see Doc/05, error E-01):
            // offset 0 matches each sample's own pitch in 48/48 cases with zero
            // exceptions; offset 1 matches in 47/48, deviating only for the
            // lowest sample (extended down to MIDI note 0, a boundary
            // extension -- never the root). Previously these two were swapped
            // here, which broke MIDI-triggered preview for the lowest sample
            // (see DwpCreatorViewModel.previewForNote).
            val rootKey = keyRange?.getOrNull(0)?.let { it.toInt() and 0xFF } ?: -1
            val lowKey = keyRange?.getOrNull(1)?.let { it.toInt() and 0xFF } ?: -1
            val highKey = keyRange?.getOrNull(2)?.let { it.toInt() and 0xFF } ?: -1
            // Sección 10 del prompt maestro Pass 3: listSamples() is a
            // read-only diagnostic operation and must never throw just
            // because a malformed .dwp has a 0x01f7 payload shorter than 4
            // bytes -- readLE32 would otherwise throw
            // ArrayIndexOutOfBoundsException reading its offset-0 frame
            // count. Reading tolerantly here does NOT relax the strict
            // exactly-40-bytes requirement enforced by
            // requireSingleAudioFormatBlock() for the MUTATING operations
            // (patchFrameCount/replaceSampleAudio) -- "lectura tolerante,
            // mutación estricta" is deliberate and preserved.
            val frameCount = if (audioFormat != null && audioFormat.size >= 4) {
                DwpTokenizer.readLE32(audioFormat, 0)
            } else {
                -1
            }

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

    /** Number of top-level `0x0003` sample containers in [doc]. */
    fun sampleCount(doc: DwpDocument): Int = doc.blocks.count { it.tag == DwpBlock.TAG_SAMPLE_CONTAINER }

    /**
     * Validates [sampleContainerIndex] against [doc]'s real sample count
     * before any function that patches-by-index proceeds. Per the audit
     * (Doc/12_KNOWN_ISSUES.md): an out-of-range index must never be allowed
     * to silently fall through as a no-op edit (the `map`+`seen` pattern
     * used by [patchFrameCount]/[replaceSampleAudio] would otherwise just
     * never match anything and return the document unchanged, with no
     * indication to the caller that nothing happened) or throw a generic,
     * unhelpful [IndexOutOfBoundsException] deeper in the call stack.
     */
    private fun requireValidSampleIndex(doc: DwpDocument, sampleContainerIndex: Int) {
        val count = sampleCount(doc)
        require(sampleContainerIndex in 0 until count) {
            "sampleContainerIndex=$sampleContainerIndex fuera de rango: el documento tiene $count sample(s) (0..${count - 1})."
        }
    }
}
