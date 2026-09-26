package com.jvk.dwpcreator.domain.dwp.monolithic

import com.jvk.dwpcreator.domain.dwp.DwpBlock
import com.jvk.dwpcreator.domain.dwp.DwpDocument
import com.jvk.dwpcreator.domain.dwp.DwpEngine
import com.jvk.dwpcreator.domain.dwp.DwpTokenizer
import com.jvk.dwpcreator.domain.flac.FlacDecoder
import com.jvk.dwpcreator.domain.io.LoadedProject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * FASE 2 (integración real en el flujo de aplicación): tests de
 * [MonolithicDwpProjectBuilder], el orquestador que convierte un
 * [LoadedProject] **completo** (todas las muestras) a Monolithic, no una
 * muestra suelta -- eso ya lo cubre [MonolithicDwpBuilderTest].
 *
 * Reutiliza el mismo estilo de fixture sintético que [MonolithicDwpBuilderTest]
 * (documento con la forma real de `Instrument.dwp`, construido a mano) y el
 * mismo helper `buildWav` que [com.jvk.dwpcreator.domain.audio.WavDecoderTest]
 * usa para producir bytes de `.wav` reales -- porque, a diferencia de
 * [MonolithicDwpBuilder.build] (que recibe un [com.jvk.dwpcreator.domain.audio.WavDecoder.DecodedWav]
 * ya decodificado), [MonolithicDwpProjectBuilder] recibe [LoadedProject],
 * cuyo `audioByIndex` son bytes de archivo `.wav` crudos -- el mismo tipo de
 * dato que trae `ZipProjectLoader.load()` en producción.
 */
class MonolithicDwpProjectBuilderTest {

    private fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    private fun le32(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()
    )
    private fun floatLe32(v: Float) = le32(v.toRawBits())

    /** Mismo helper que WavDecoderTest.buildWav -- WAV mínimo, real, spec-correcto. */
    private fun buildWav(channels: Int, sampleRate: Int, bitsPerSample: Int, pcmData: ByteArray): ByteArray {
        val blockAlign = channels * (bitsPerSample / 8)
        val byteRate = sampleRate * blockAlign
        val fmtChunk = ByteArrayOutputStream().apply {
            write("fmt ".toByteArray(Charsets.US_ASCII))
            write(le32(16))
            write(le16(1)) // PCM
            write(le16(channels))
            write(le32(sampleRate))
            write(le32(byteRate))
            write(le16(blockAlign))
            write(le16(bitsPerSample))
        }.toByteArray()
        val dataChunk = ByteArrayOutputStream().apply {
            write("data".toByteArray(Charsets.US_ASCII))
            write(le32(pcmData.size))
            write(pcmData)
        }.toByteArray()
        val riffBody = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write(fmtChunk)
            write(dataChunk)
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            write(le32(riffBody.size))
            write(riffBody)
        }.toByteArray()
    }

    private fun monoWav16Bytes(samples: List<Int>, sampleRate: Int = 44100): ByteArray =
        buildWav(channels = 1, sampleRate = sampleRate, bitsPerSample = 16, pcmData = samples.flatMap { le16(it).toList() }.toByteArray())

    private fun stereoWav16Bytes(left: List<Int>, right: List<Int>, sampleRate: Int = 48000): ByteArray {
        require(left.size == right.size)
        val interleaved = left.indices.flatMap { i -> listOf(le16(left[i]).toList(), le16(right[i]).toList()).flatten() }
        return buildWav(channels = 2, sampleRate = sampleRate, bitsPerSample = 16, pcmData = interleaved.toByteArray())
    }

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
        System.arraycopy(floatLe32(sampleRate.toFloat()), 0, p, 16, 4)
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

    /** Documento con 3 muestras -- deliberadamente más de 1, para probar que el "fold" real recorre todas. */
    private fun buildThreeSampleDoc(): DwpDocument {
        val blocks = listOf(
            DwpBlock(DwpBlock.TAG_INSTRUMENT_NAME, 0, "TestInstrument".toByteArray(Charsets.ISO_8859_1)),
            sampleContainer("Kick", frameCount = 4, channels = 1, bytesPerSample = 2, sampleRate = 44100),
            sampleContainer("Snare", frameCount = 6, channels = 2, bytesPerSample = 2, sampleRate = 48000),
            sampleContainer("HiHat", frameCount = 5, channels = 1, bytesPerSample = 2, sampleRate = 44100),
            DwpBlock(DwpBlock.TAG_TOP_LEVEL_END, 0, ByteArray(0))
        )
        return DwpDocument(preamble90(), blocks)
    }

    private fun nestedOf(doc: DwpDocument, sampleIndex: Int): List<DwpBlock> {
        val containers = doc.blocks.filter { it.tag == DwpBlock.TAG_SAMPLE_CONTAINER }
        return DwpTokenizer.tokenizeStrict(containers[sampleIndex].payload, context = "test")
    }

    @Test
    fun `build embeds FLAC audio in every sample of the project, not just the first`() {
        val doc = buildThreeSampleDoc()
        val kick = monoWav16Bytes(listOf(1000, -1000, 500, -500))
        val snare = stereoWav16Bytes(listOf(100, 200, 300), listOf(-100, -200, -300))
        val hihat = monoWav16Bytes(listOf(1, 2, 3, 4, 5))
        val project = LoadedProject(
            document = doc,
            dwpZipEntryName = "TestInstrument.dwp",
            audioByIndex = listOf(kick, snare, hihat),
            originalZipEntryNames = listOf("Kick.wav", "Snare.wav", "HiHat.wav")
        )

        val result = MonolithicDwpProjectBuilder.build(project)

        // Las 3 muestras deben tener exactamente un 0x0206 cada una.
        for (i in 0..2) {
            val nested = nestedOf(result, i)
            assertEquals("sample $i debe tener exactamente 1 bloque 0x0206", 1, nested.count { it.tag == MonolithicDwpAudioBuilder.TAG_MONOLITHIC_AUDIO })
        }

        // Cada una debe decodificar exactamente al PCM de origen correspondiente (no mezcladas entre sí).
        val kickFlac = nestedOf(result, 0).first { it.tag == MonolithicDwpAudioBuilder.TAG_MONOLITHIC_AUDIO }.payload
        val kickDecoded = FlacDecoder.decode(kickFlac)
        assertEquals(4, kickDecoded.pcm.channels[0].size)
        assertArrayEquals(intArrayOf(1000, -1000, 500, -500), kickDecoded.pcm.channels[0])

        val snareFlac = nestedOf(result, 1).first { it.tag == MonolithicDwpAudioBuilder.TAG_MONOLITHIC_AUDIO }.payload
        val snareDecoded = FlacDecoder.decode(snareFlac)
        assertEquals(2, snareDecoded.pcm.channels.size)
        assertArrayEquals(intArrayOf(100, 200, 300), snareDecoded.pcm.channels[0])
        assertArrayEquals(intArrayOf(-100, -200, -300), snareDecoded.pcm.channels[1])

        val hihatFlac = nestedOf(result, 2).first { it.tag == MonolithicDwpAudioBuilder.TAG_MONOLITHIC_AUDIO }.payload
        val hihatDecoded = FlacDecoder.decode(hihatFlac)
        assertArrayEquals(intArrayOf(1, 2, 3, 4, 5), hihatDecoded.pcm.channels[0])

        // Nombres/paths/key-range preservados en las 3 (nada de eso lo toca este flujo).
        assertEquals(3, DwpEngine.listSamples(result).size)
        assertEquals(listOf("Kick", "Snare", "HiHat"), DwpEngine.listSamples(result).map { it.name })

        // El documento sigue siendo un .dwp estructuralmente válido y round-trippeable.
        val bytes = DwpBinaryAssembler.assemble(result)
        val reparsed = DwpDocument.parse(bytes)
        assertArrayEquals(bytes, reparsed.toBytes())
    }

    @Test
    fun `buildBytes produces the same bytes as assembling build's document`() {
        val doc = buildThreeSampleDoc()
        val project = LoadedProject(
            document = doc,
            dwpZipEntryName = "x.dwp",
            audioByIndex = listOf(
                monoWav16Bytes(listOf(1, 2)),
                stereoWav16Bytes(listOf(1), listOf(2)),
                monoWav16Bytes(listOf(9, 8, 7))
            ),
            originalZipEntryNames = listOf("a.wav", "b.wav", "c.wav")
        )
        val viaBuildBytes = MonolithicDwpProjectBuilder.buildBytes(project)
        val viaBuild = DwpBinaryAssembler.assemble(MonolithicDwpProjectBuilder.build(project))
        assertArrayEquals(viaBuild, viaBuildBytes)
    }

    @Test
    fun `build throws IllegalStateException with the offending sample name when a wav is malformed`() {
        val doc = buildThreeSampleDoc()
        val project = LoadedProject(
            document = doc,
            dwpZipEntryName = "x.dwp",
            audioByIndex = listOf(
                monoWav16Bytes(listOf(1, 2)),
                byteArrayOf(1, 2, 3, 4), // "Snare": no es un WAV válido (falta RIFF)
                monoWav16Bytes(listOf(3, 4, 5))
            ),
            originalZipEntryNames = listOf("a.wav", "b.wav", "c.wav")
        )
        val ex = assertThrows(IllegalStateException::class.java) {
            MonolithicDwpProjectBuilder.build(project)
        }
        assertTrue("el mensaje debe identificar la muestra concreta (\"Snare\"), no ser genérico: ${ex.message}", ex.message!!.contains("Snare"))
    }

    @Test
    fun `build throws for a project where sample count does not match audioByIndex size`() {
        val doc = buildThreeSampleDoc()
        val project = LoadedProject(
            document = doc,
            dwpZipEntryName = "x.dwp",
            audioByIndex = listOf(monoWav16Bytes(listOf(1, 2))), // solo 1, el doc tiene 3 samples
            originalZipEntryNames = listOf("a.wav")
        )
        assertThrows(IllegalArgumentException::class.java) {
            MonolithicDwpProjectBuilder.build(project)
        }
    }

    @Test
    fun `build propagates UnsupportedMonolithicFormatException for a float32 sample, identifying it did not silently skip it`() {
        // float32 WAV real (audioFormatTag=3), mismo caso que PcmNormalizer rechaza explícitamente.
        val pcm = ByteArray(16)
        val floatWav = ByteArrayOutputStream().apply {
            write("fmt ".toByteArray(Charsets.US_ASCII)); write(le32(16))
            write(le16(3)); write(le16(1)); write(le32(44100)); write(le32(44100 * 4))
            write(le16(4)); write(le16(32))
        }.toByteArray()
        val dataChunk = ByteArrayOutputStream().apply {
            write("data".toByteArray(Charsets.US_ASCII)); write(le32(pcm.size)); write(pcm)
        }.toByteArray()
        val riffBody = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII)); write(floatWav); write(dataChunk)
        }.toByteArray()
        val floatWavBytes = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII)); write(le32(riffBody.size)); write(riffBody)
        }.toByteArray()

        val doc = buildThreeSampleDoc()
        val project = LoadedProject(
            document = doc,
            dwpZipEntryName = "x.dwp",
            audioByIndex = listOf(monoWav16Bytes(listOf(1, 2)), floatWavBytes, monoWav16Bytes(listOf(3))),
            originalZipEntryNames = listOf("a.wav", "b.wav", "c.wav")
        )
        assertThrows(UnsupportedMonolithicFormatException::class.java) {
            MonolithicDwpProjectBuilder.build(project)
        }
    }

    @Test
    fun `onProgress fires once per sample, in order, 1-based, with the correct sample names`() {
        val doc = buildThreeSampleDoc()
        val project = LoadedProject(
            document = doc,
            dwpZipEntryName = "x.dwp",
            audioByIndex = listOf(
                monoWav16Bytes(listOf(1, 2, 3, 4)),
                stereoWav16Bytes(listOf(1, 2), listOf(3, 4)),
                monoWav16Bytes(listOf(5, 6, 7))
            ),
            originalZipEntryNames = listOf("a.wav", "b.wav", "c.wav")
        )
        val calls = mutableListOf<Triple<Int, Int, String>>()

        MonolithicDwpProjectBuilder.build(project) { current, total, sampleName ->
            calls.add(Triple(current, total, sampleName))
        }

        assertEquals(3, calls.size)
        assertEquals(listOf(1, 2, 3), calls.map { it.first })
        assertTrue(calls.all { it.second == 3 })
        assertEquals(listOf("Kick", "Snare", "HiHat"), calls.map { it.third })
    }

    @Test
    fun `build and buildBytes without an onProgress argument still work (default no-op)`() {
        val doc = buildThreeSampleDoc()
        val project = LoadedProject(
            document = doc,
            dwpZipEntryName = "x.dwp",
            audioByIndex = listOf(
                monoWav16Bytes(listOf(1, 2)),
                stereoWav16Bytes(listOf(1), listOf(2)),
                monoWav16Bytes(listOf(9, 8, 7))
            ),
            originalZipEntryNames = listOf("a.wav", "b.wav", "c.wav")
        )
        MonolithicDwpProjectBuilder.build(project) // no debe hacer falta el callback
        val bytes = MonolithicDwpProjectBuilder.buildBytes(project)
        assertTrue(bytes.isNotEmpty())
    }
}
