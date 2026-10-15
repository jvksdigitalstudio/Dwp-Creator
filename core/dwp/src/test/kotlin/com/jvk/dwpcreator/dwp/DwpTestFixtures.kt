package com.jvk.dwpcreator.dwp

/**
 * Fixtures compartidos por las pruebas del núcleo DWP.
 *
 * Las pruebas se ejecutan contra el `Instrument.dwp` REAL exportado desde FL
 * Studio Desktop (instrumento cromático de 48 muestras, C3–D6, velocity 127,
 * 47 307 bytes): el mismo archivo de la auditoría binaria original. Si pasan,
 * el núcleo está verificado contra la verdad de campo, no contra suposiciones.
 */
internal object DwpTestFixtures {

    fun loadRealFixture(): ByteArray {
        val stream = javaClass.classLoader!!.getResourceAsStream("Instrument.dwp")
            ?: error("Test fixture Instrument.dwp not found on test classpath")
        return stream.readBytes()
    }

    // --- RISK-05 (audit Doc/12_KNOWN_ISSUES.md): reserializing an edited
    // sample container must never silently drop bytes it didn't understand.
    // These tests build a deliberately-corrupted nested payload (extra
    // trailing bytes that don't form a valid block header) and confirm the
    // engine now refuses to touch it instead of quietly reserializing a
    // truncated copy. Before this fix, renameRecursive/patchFrameCount/
    // replaceSampleAudio used the lenient DwpTokenizer.tokenize() on nested
    // payloads with no check that it consumed the whole thing.

    fun buildDocWithCorruptedSampleContainer(): DwpDocument {
        val realDoc = DwpDocument.parse(loadRealFixture())
        val firstSample = realDoc.blocks.first { it.tag == DwpBlock.TAG_SAMPLE_CONTAINER }
        // Append 5 trailing bytes that cannot possibly be interpreted as a
        // further valid block (fewer than the 12-byte minimum header size).
        val corruptedPayload = firstSample.payload + byteArrayOf(1, 2, 3, 4, 5)
        val corruptedContainer = DwpBlock(firstSample.tag, firstSample.reserved, corruptedPayload)
        val newBlocks = realDoc.blocks.map { if (it === firstSample) corruptedContainer else it }
        return DwpDocument(realDoc.preamble, newBlocks)
    }

    // --- Sección 3/4 del prompt maestro: patchFrameCount/replaceSampleAudio
    // deben exigir EXACTAMENTE un 0x01f7 de 40 bytes antes de escribir.

    fun sampleContainerWithAudioFormatBlocks(vararg audioFormatPayloads: ByteArray): DwpDocument {
        val original = DwpDocument.parse(loadRealFixture())
        val firstSample = original.blocks.first { it.tag == DwpBlock.TAG_SAMPLE_CONTAINER }
        val nested = DwpTokenizer.tokenizeStrict(firstSample.payload, context = "test")
        val withoutAudioFormat = nested.filter { it.tag != DwpBlock.TAG_AUDIO_FORMAT }
        val newAudioFormatBlocks = audioFormatPayloads.map { DwpBlock(DwpBlock.TAG_AUDIO_FORMAT, 0, it) }
        val newNested = withoutAudioFormat + newAudioFormatBlocks
        val newContainer = DwpBlock(firstSample.tag, firstSample.reserved, DwpTokenizer.serialize(newNested))
        val newBlocks = original.blocks.map { if (it === firstSample) newContainer else it }
        return DwpDocument(original.preamble, newBlocks)
    }

    // --- Sección 10 del prompt maestro Pass 3: listSamples() es lectura
    // tolerante; las operaciones mutantes siguen siendo estrictas.

    fun docWithMalformedAudioFormatPayload(malformedPayload: ByteArray): DwpDocument {
        val original = DwpDocument.parse(loadRealFixture())
        val firstSample = original.blocks.first { it.tag == DwpBlock.TAG_SAMPLE_CONTAINER }
        val nested = DwpTokenizer.tokenizeStrict(firstSample.payload, context = "test")
        val newNested = nested.map {
            if (it.tag == DwpBlock.TAG_AUDIO_FORMAT) DwpBlock(it.tag, it.reserved, malformedPayload) else it
        }
        val newContainer = DwpBlock(firstSample.tag, firstSample.reserved, DwpTokenizer.serialize(newNested))
        val newBlocks = original.blocks.map { if (it === firstSample) newContainer else it }
        return DwpDocument(original.preamble, newBlocks)
    }

    // explícitamente por qué se detuvo, no solo "se detuvo". Estos tests
    // construyen streams sintéticos mínimos (sin depender del fixture real)
    // para cada StopReason.

    fun block(tag: Int, payload: ByteArray, reserved: Int = 0): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        DwpTokenizer.writeLE32(out, tag)
        DwpTokenizer.writeLE32(out, payload.size)
        DwpTokenizer.writeLE32(out, reserved)
        out.write(payload)
        return out.toByteArray()
    }
}
