package com.jvk.dwpcreator.dwp

import com.jvk.dwpcreator.codec.wav.DecodedWav

/**
 * Parcheo del formato de audio (`0x01f7`) de una muestra concreta de un
 * [DwpDocument] tras sustituir su audio.
 *
 * Todas las operaciones son **atómicas**: la validación (índice, tamaño y
 * unicidad del bloque `0x01f7`) se hace antes de reescribir ningún bloque, así
 * que una entrada inválida produce un error y nunca un documento parcheado a
 * medias.
 */
object DwpAudioPatcher {

    /**
     * The only payload size ever confirmed for [DwpBlock.TAG_AUDIO_FORMAT]
     * (0x01f7), verified against the real fixture's 48 samples. Any other
     * size means this .dwp does not match the format this engine was built
     * against, and must not be edited blindly.
     */
    private const val AUDIO_FORMAT_PAYLOAD_SIZE = 40

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
        return patchSampleContainer(doc, sampleContainerIndex) { inner ->
            if (inner.tag == DwpBlock.TAG_AUDIO_FORMAT) {
                val newPayload = inner.payload.copyOf()
                DwpTokenizer.writeLE32(newPayload, 0, newFrameCount)
                DwpBlock(inner.tag, inner.reserved, newPayload)
            } else {
                inner
            }
        }
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
        wav: DecodedWav
    ): DwpDocument {
        requireValidSampleIndex(doc, sampleContainerIndex)
        return patchSampleContainer(doc, sampleContainerIndex) { inner ->
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
    }

    /**
     * Reescribe el contenedor de muestra número [sampleContainerIndex] aplicando
     * [patchInner] a cada bloque interno. Valida el contenedor (`0x01f7` único y
     * de tamaño confirmado) **antes** de tocar nada, de modo que un fallo aborta
     * toda la operación sin devolver un resultado parcial.
     */
    private fun patchSampleContainer(
        doc: DwpDocument,
        sampleContainerIndex: Int,
        patchInner: (DwpBlock) -> DwpBlock
    ): DwpDocument {
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
            DwpBlock(top.tag, top.reserved, DwpTokenizer.serialize(nested.map(patchInner)))
        }
        return DwpDocument(doc.preamble, newBlocks)
    }

    /**
     * Locates the single [DwpBlock.TAG_AUDIO_FORMAT] block inside [nested]
     * (the already-tokenized contents of one sample container) and verifies
     * it's safe to patch: exactly one instance, with the one confirmed
     * payload size. Throws [DwpFormatException] -- never silently returns
     * null, never patches an ambiguous or malformed target -- for:
     *   - zero occurrences (nothing to patch);
     *   - more than one occurrence (ambiguous: which one is "the" real one?);
     *   - a payload size other than [AUDIO_FORMAT_PAYLOAD_SIZE].
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
     * Validates [sampleContainerIndex] against [doc]'s real sample count
     * before any function that patches-by-index proceeds. An out-of-range
     * index must never fall through as a silent no-op edit (the document
     * returned unchanged with no indication that nothing happened) nor throw a
     * generic, unhelpful [IndexOutOfBoundsException] deeper in the stack.
     */
    private fun requireValidSampleIndex(doc: DwpDocument, sampleContainerIndex: Int) {
        val count = DwpSampleCatalog.sampleCount(doc)
        require(sampleContainerIndex in 0 until count) {
            "sampleContainerIndex=$sampleContainerIndex fuera de rango: el documento tiene $count sample(s) (0..${count - 1})."
        }
    }
}
