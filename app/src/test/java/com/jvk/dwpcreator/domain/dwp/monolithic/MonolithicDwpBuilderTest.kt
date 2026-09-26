package com.jvk.dwpcreator.domain.dwp.monolithic

import com.jvk.dwpcreator.domain.audio.WavDecoder
import com.jvk.dwpcreator.domain.dwp.DwpBlock
import com.jvk.dwpcreator.domain.dwp.DwpDocument
import com.jvk.dwpcreator.domain.dwp.DwpTokenizer
import com.jvk.dwpcreator.domain.flac.FlacDecoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de integración de extremo a extremo del subsistema Monolithic DWP,
 * usando un documento sintético con la misma forma que el fixture real
 * (`Instrument.dwp`: preámbulo de 90 bytes de la versión 0x26, sample
 * containers con 0x01f4/0x01f5/0x01f6/0x01f7/0x0004), construido a mano en
 * vez de depender del fixture real -- el fixture es 32-bit float, que
 * `PcmNormalizer` rechaza explícitamente en esta fase (ver
 * `PcmNormalizerTest`), así que no puede usarse para ejercitar este flujo
 * end-to-end. Esto no reduce cobertura: cubre exactamente la misma forma
 * estructural con datos 16-bit PCM sintéticos.
 */
class MonolithicDwpBuilderTest {

    private fun le32(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()
    )
    private fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    private fun floatLe32(v: Float) = le32(v.toRawBits())

    private fun preamble90(): ByteArray {
        val p = ByteArray(90)
        val magic = "DwPr".toByteArray(Charsets.US_ASCII)
        System.arraycopy(magic, 0, p, 0, 4)
        val version = le32(0x26)
        System.arraycopy(version, 0, p, 4, 4)
        return p
    }

    private fun audioFormatPayload(frameCount: Int, channels: Int, bytesPerSample: Int, sampleRate: Int): ByteArray {
        val p = ByteArray(40)
        DwpTokenizer.writeLE32(p, 0, frameCount)
        DwpTokenizer.writeLE32(p, 8, channels)
        DwpTokenizer.writeLE32(p, 12, bytesPerSample)
        val srBytes = floatLe32(sampleRate.toFloat())
        System.arraycopy(srBytes, 0, p, 16, 4)
        DwpTokenizer.writeLE32(p, 36, bytesPerSample * 8)
        return p
    }

    private fun sampleContainer(name: String, frameCount: Int, channels: Int, bytesPerSample: Int, sampleRate: Int): DwpBlock {
        val nested = listOf(
            DwpBlock(DwpBlock.TAG_KEY_RANGE, 0, ByteArray(25)),
            DwpBlock(DwpBlock.TAG_SAMPLE_NAME, 0, name.toByteArray(Charsets.ISO_8859_1)),
            DwpBlock(DwpBlock.TAG_SAMPLE_PATH, 0, "C:\\samples\\$name.wav".toByteArray(Charsets.ISO_8859_1)),
            DwpBlock(DwpBlock.TAG_AUDIO_FORMAT, 0, audioFormatPayload(frameCount, channels, bytesPerSample, sampleRate)),
            DwpBlock(DwpBlock.TAG_SAMPLE_END, 0, ByteArray(0))
        )
        return DwpBlock(DwpBlock.TAG_SAMPLE_CONTAINER, 0, DwpTokenizer.serialize(nested))
    }

    private fun buildSyntheticDoc(): DwpDocument {
        val blocks = listOf(
            DwpBlock(DwpBlock.TAG_INSTRUMENT_NAME, 0, "TestInstrument".toByteArray(Charsets.ISO_8859_1)),
            sampleContainer("Sample0", frameCount = 4, channels = 1, bytesPerSample = 2, sampleRate = 44100),
            sampleContainer("Sample1", frameCount = 6, channels = 2, bytesPerSample = 2, sampleRate = 48000),
            DwpBlock(DwpBlock.TAG_TOP_LEVEL_END, 0, ByteArray(0))
        )
        return DwpDocument(preamble90(), blocks)
    }

    private fun monoWav16(samples: List<Int>, sampleRate: Int = 44100): WavDecoder.DecodedWav {
        val bytes = samples.flatMap { le16(it).toList() }.toByteArray()
        return WavDecoder.DecodedWav(sampleRate, 1, WavDecoder.SampleFormat.PCM_16, bytes)
    }

    private fun nestedOf(doc: DwpDocument, sampleIndex: Int): List<DwpBlock> {
        val containers = doc.blocks.filter { it.tag == DwpBlock.TAG_SAMPLE_CONTAINER }
        return DwpTokenizer.tokenizeStrict(containers[sampleIndex].payload, context = "test")
    }

    @Test
    fun `synthetic document round trips through DwpDocument parse before any Monolithic operation`() {
        val doc = buildSyntheticDoc()
        val bytes = doc.toBytes()
        val reparsed = DwpDocument.parse(bytes)
        assertArrayEquals(bytes, reparsed.toBytes())
    }

    @Test
    fun `build embeds FLAC audio in the target sample and preserves everything else byte for byte`() {
        val doc = buildSyntheticDoc()
        val wav = monoWav16(listOf(1000, -1000, 500, -500, 250, -250, 0, 111))

        val result = MonolithicDwpBuilder.build(doc, sampleContainerIndex = 0, wav = wav)

        // El otro sample (#1) y el resto de bloques top-level deben quedar intactos.
        assertEquals(doc.blocks[0], result.document.blocks[0]) // TAG_INSTRUMENT_NAME
        assertEquals(doc.blocks[2], result.document.blocks[2]) // Sample1, no tocado
        assertEquals(doc.blocks[3], result.document.blocks[3]) // TAG_TOP_LEVEL_END
        assertArrayEquals(doc.preamble, result.document.preamble)

        // El sample objetivo debe contener ahora exactamente un 0x0206 y conservar 0x01f4/0x01f5/0x01f6/0x0004.
        val nested = nestedOf(result.document, 0)
        val originalNested = nestedOf(doc, 0)
        assertEquals(originalNested.first { it.tag == DwpBlock.TAG_KEY_RANGE }, nested.first { it.tag == DwpBlock.TAG_KEY_RANGE })
        assertEquals(originalNested.first { it.tag == DwpBlock.TAG_SAMPLE_NAME }, nested.first { it.tag == DwpBlock.TAG_SAMPLE_NAME })
        assertEquals(originalNested.first { it.tag == DwpBlock.TAG_SAMPLE_PATH }, nested.first { it.tag == DwpBlock.TAG_SAMPLE_PATH })
        assertEquals(1, nested.count { it.tag == DwpBlock.TAG_SAMPLE_END })
        assertEquals(1, nested.count { it.tag == MonolithicDwpAudioBuilder.TAG_MONOLITHIC_AUDIO })

        // El 0x0206 embebido debe decodificar exactamente al PCM de origen.
        val flacPayload = nested.first { it.tag == MonolithicDwpAudioBuilder.TAG_MONOLITHIC_AUDIO }.payload
        val decoded = FlacDecoder.decode(flacPayload)
        val expectedPcm = PcmNormalizer.normalize(wav)
        assertEquals(expectedPcm, decoded.pcm)

        // 0x01f7 debe reflejar los nuevos parámetros de audio.
        val audioFormat = nested.first { it.tag == DwpBlock.TAG_AUDIO_FORMAT }.payload
        assertEquals(wav.frameCount, DwpTokenizer.readLE32(audioFormat, 0))
        assertEquals(wav.channelCount, DwpTokenizer.readLE32(audioFormat, 8))
        assertEquals(wav.bytesPerSample, DwpTokenizer.readLE32(audioFormat, 12))
        assertEquals(wav.bytesPerSample * 8, DwpTokenizer.readLE32(audioFormat, 36))

        // El documento resultante debe seguir siendo un .dwp estructuralmente válido y round-trippeable.
        val bytes = DwpBinaryAssembler.assemble(result.document)
        val reparsed = DwpDocument.parse(bytes)
        assertArrayEquals(bytes, reparsed.toBytes())
    }

    @Test
    fun `build inserts the embedded audio block immediately before the sample terminator`() {
        val doc = buildSyntheticDoc()
        val wav = monoWav16(listOf(1, 2, 3, 4))
        val result = MonolithicDwpBuilder.build(doc, 0, wav)
        val nested = nestedOf(result.document, 0)
        val terminatorIndex = nested.indexOfFirst { it.tag == DwpBlock.TAG_SAMPLE_END }
        assertEquals(MonolithicDwpAudioBuilder.TAG_MONOLITHIC_AUDIO, nested[terminatorIndex - 1].tag)
    }

    @Test
    fun `calling build twice replaces the previous embedded audio instead of duplicating it`() {
        val doc = buildSyntheticDoc()
        val wavA = monoWav16(listOf(10, 20, 30, 40))
        val wavB = monoWav16(listOf(-1, -2, -3, -4, -5))

        val firstResult = MonolithicDwpBuilder.build(doc, 0, wavA)
        val secondResult = MonolithicDwpBuilder.build(firstResult.document, 0, wavB)

        val nested = nestedOf(secondResult.document, 0)
        assertEquals(1, nested.count { it.tag == MonolithicDwpAudioBuilder.TAG_MONOLITHIC_AUDIO })

        val flacPayload = nested.first { it.tag == MonolithicDwpAudioBuilder.TAG_MONOLITHIC_AUDIO }.payload
        val decoded = FlacDecoder.decode(flacPayload)
        assertEquals(PcmNormalizer.normalize(wavB), decoded.pcm)
    }

    @Test
    fun `build throws for an out-of-range sampleContainerIndex`() {
        val doc = buildSyntheticDoc()
        val wav = monoWav16(listOf(1, 2))
        assertThrows(IllegalArgumentException::class.java) {
            MonolithicDwpBuilder.build(doc, sampleContainerIndex = 5, wav = wav)
        }
    }

    @Test
    fun `build throws UnsupportedMonolithicFormatException for float32 wav`() {
        val doc = buildSyntheticDoc()
        val wav = WavDecoder.DecodedWav(44100, 1, WavDecoder.SampleFormat.FLOAT_32, ByteArray(16))
        assertThrows(UnsupportedMonolithicFormatException::class.java) {
            MonolithicDwpBuilder.build(doc, 0, wav)
        }
    }

    @Test
    fun `applyToSampleContainer throws when the sample has no unique terminator`() {
        val brokenNested = listOf(
            DwpBlock(DwpBlock.TAG_KEY_RANGE, 0, ByteArray(25)),
            DwpBlock(DwpBlock.TAG_SAMPLE_NAME, 0, "x".toByteArray())
            // sin 0x0004
        )
        val container = DwpBlock(DwpBlock.TAG_SAMPLE_CONTAINER, 0, DwpTokenizer.serialize(brokenNested))
        val wav = monoWav16(listOf(1, 2))
        val embedded = MonolithicDwpAudioBuilder.build(wav)
        assertThrows(MonolithicZoneStructureException::class.java) {
            MonolithicDwpStructureBuilder.applyToSampleContainer(container, wav, embedded.block)
        }
    }

    @Test
    fun `build with validate=false skips MonolithicDwpValidator but still produces a parseable document`() {
        val doc = buildSyntheticDoc()
        val wav = monoWav16(listOf(1, 2, 3))
        val result = MonolithicDwpBuilder.build(doc, 0, wav, validate = false)
        assertTrue(MonolithicDwpValidator.validate(doc, result.document, 0, wav).ok)
    }

    @Test
    fun `buildBytes produces bytes identical to assembling build's document`() {
        val doc = buildSyntheticDoc()
        val wav = monoWav16(listOf(7, 8, 9))
        val viaBuildBytes = MonolithicDwpBuilder.buildBytes(doc, 0, wav)
        val viaBuild = DwpBinaryAssembler.assemble(MonolithicDwpBuilder.build(doc, 0, wav).document)
        assertArrayEquals(viaBuild, viaBuildBytes)
    }
}
