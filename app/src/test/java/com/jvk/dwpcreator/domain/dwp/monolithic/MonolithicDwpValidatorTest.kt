package com.jvk.dwpcreator.domain.dwp.monolithic

import com.jvk.dwpcreator.domain.audio.WavDecoder
import com.jvk.dwpcreator.domain.dwp.DwpBlock
import com.jvk.dwpcreator.domain.dwp.DwpDocument
import com.jvk.dwpcreator.domain.dwp.DwpTokenizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MonolithicDwpValidatorTest {

    private fun le32(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()
    )
    private fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

    private fun preamble90(): ByteArray {
        val p = ByteArray(90)
        System.arraycopy("DwPr".toByteArray(Charsets.US_ASCII), 0, p, 0, 4)
        System.arraycopy(le32(0x26), 0, p, 4, 4)
        return p
    }

    private fun audioFormatPayload(frameCount: Int, channels: Int, bytesPerSample: Int, sampleRate: Int): ByteArray {
        val p = ByteArray(40)
        DwpTokenizer.writeLE32(p, 0, frameCount)
        DwpTokenizer.writeLE32(p, 8, channels)
        DwpTokenizer.writeLE32(p, 12, bytesPerSample)
        System.arraycopy(le32(sampleRate.toFloat().toRawBits()), 0, p, 16, 4)
        DwpTokenizer.writeLE32(p, 36, bytesPerSample * 8)
        return p
    }

    private fun sampleContainer(name: String, frameCount: Int): DwpBlock {
        val nested = listOf(
            DwpBlock(DwpBlock.TAG_KEY_RANGE, 0, ByteArray(25)),
            DwpBlock(DwpBlock.TAG_SAMPLE_NAME, 0, name.toByteArray(Charsets.ISO_8859_1)),
            DwpBlock(DwpBlock.TAG_SAMPLE_PATH, 0, "C:\\s\\$name.wav".toByteArray(Charsets.ISO_8859_1)),
            DwpBlock(DwpBlock.TAG_AUDIO_FORMAT, 0, audioFormatPayload(frameCount, 1, 2, 44100)),
            DwpBlock(DwpBlock.TAG_SAMPLE_END, 0, ByteArray(0))
        )
        return DwpBlock(DwpBlock.TAG_SAMPLE_CONTAINER, 0, DwpTokenizer.serialize(nested))
    }

    private fun doc(): DwpDocument = DwpDocument(
        preamble90(),
        listOf(
            DwpBlock(DwpBlock.TAG_INSTRUMENT_NAME, 0, "T".toByteArray()),
            sampleContainer("S0", 4),
            sampleContainer("S1", 4),
            DwpBlock(DwpBlock.TAG_TOP_LEVEL_END, 0, ByteArray(0))
        )
    )

    private fun wav(samples: List<Int>) = WavDecoder.DecodedWav(
        44100, 1, WavDecoder.SampleFormat.PCM_16, samples.flatMap { le16(it).toList() }.toByteArray()
    )

    @Test
    fun `validate reports ok for a correctly built document`() {
        val original = doc()
        val w = wav(listOf(1, 2, 3, 4))
        val result = MonolithicDwpBuilder.build(original, 0, w, validate = false)
        val validation = MonolithicDwpValidator.validate(original, result.document, 0, w)
        assertTrue(validation.issues.joinToString(), validation.ok)
    }

    @Test
    fun `validate detects a corrupted embedded FLAC payload`() {
        val original = doc()
        val w = wav(listOf(1, 2, 3, 4, 5, 6, 7, 8))
        val built = MonolithicDwpBuilder.build(original, 0, w, validate = false).document

        val targetIndex = 1 // sample container #0 está en el índice top-level 1
        val nested = DwpTokenizer.tokenizeStrict(built.blocks[targetIndex].payload)
        val corruptedNested = nested.map { block ->
            if (block.tag == MonolithicDwpAudioBuilder.TAG_MONOLITHIC_AUDIO) {
                val corrupted = block.payload.copyOf()
                corrupted[corrupted.size / 2] = (corrupted[corrupted.size / 2].toInt() xor 0xFF).toByte()
                block.copy(payload = corrupted)
            } else block
        }
        val corruptedContainer = built.blocks[targetIndex].copy(payload = DwpTokenizer.serialize(corruptedNested))
        val corruptedDoc = built.copy(blocks = built.blocks.toMutableList().also { it[targetIndex] = corruptedContainer })

        val validation = MonolithicDwpValidator.validate(original, corruptedDoc, 0, w)
        assertFalse(validation.ok)
        assertTrue(validation.issues.isNotEmpty())
    }

    @Test
    fun `validate detects an unrelated top-level block that was modified`() {
        val original = doc()
        val w = wav(listOf(1, 2, 3, 4))
        val built = MonolithicDwpBuilder.build(original, 0, w, validate = false).document

        val tampered = built.copy(
            blocks = built.blocks.toMutableList().also {
                it[0] = it[0].copy(payload = "TAMPERED".toByteArray())
            }
        )

        val validation = MonolithicDwpValidator.validate(original, tampered, 0, w)
        assertFalse(validation.ok)
        assertTrue(validation.issues.any { it.contains("#0") })
    }

    @Test
    fun `validate detects inconsistent 0x01f7 metadata`() {
        val original = doc()
        val w = wav(listOf(1, 2, 3, 4))
        val built = MonolithicDwpBuilder.build(original, 0, w, validate = false).document

        val nested = DwpTokenizer.tokenizeStrict(built.blocks[1].payload)
        val tamperedNested = nested.map { block ->
            if (block.tag == DwpBlock.TAG_AUDIO_FORMAT) {
                val p = block.payload.copyOf()
                DwpTokenizer.writeLE32(p, 0, 999999) // frameCount incoherente
                block.copy(payload = p)
            } else block
        }
        val tamperedContainer = built.blocks[1].copy(payload = DwpTokenizer.serialize(tamperedNested))
        val tamperedDoc = built.copy(blocks = built.blocks.toMutableList().also { it[1] = tamperedContainer })

        val validation = MonolithicDwpValidator.validate(original, tamperedDoc, 0, w)
        assertFalse(validation.ok)
        assertTrue(validation.issues.any { it.contains("frameCount") })
    }

    @Test
    fun `validateOrThrow throws MonolithicDwpValidationException when validation fails`() {
        val original = doc()
        val w = wav(listOf(1, 2, 3, 4))
        val tampered = original.copy(
            blocks = original.blocks.toMutableList().also { it[0] = it[0].copy(payload = "X".toByteArray()) }
        )
        assertThrows(MonolithicDwpValidationException::class.java) {
            MonolithicDwpValidator.validateOrThrow(original, tampered, 0, w)
        }
    }
}
