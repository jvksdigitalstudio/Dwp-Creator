package com.jvk.dwpcreator.domain.io

import com.jvk.dwpcreator.domain.dwp.DwpDocument
import com.jvk.dwpcreator.domain.dwp.DwpEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Uses the real, audited Instrument.dwp (already a test resource from the
 * DWP engine tests) paired with tiny placeholder "audio" so the zip stays
 * lightweight in the repo. The placeholder content is unique per sample
 * name, which is what actually proves the load/export pairing logic is
 * correct — not the real audio bytes themselves.
 *
 * IMPORTANT: the placeholder must be a genuinely valid (if tiny) WAV file.
 * [ZipProjectExporter.export] now decodes every sample's real audio via
 * [com.jvk.dwpcreator.domain.audio.WavDecoder] to recompute its `0x01f7`
 * format metadata (frame count, channels, sample rate, bits/sample) instead
 * of blindly copying the template's original values — that's the fix for
 * exports that loaded corrupt in FL Mobile. A non-WAV placeholder (e.g. raw
 * ASCII text) would fail to decode and is no longer a valid stand-in.
 */
class ZipProjectIoTest {

    private fun realDwpBytes(): ByteArray {
        val stream = javaClass.classLoader!!.getResourceAsStream("Instrument.dwp")
            ?: error("Test fixture Instrument.dwp not found on test classpath")
        return stream.readBytes()
    }

    /**
     * A minimal but genuinely valid 16-bit PCM mono WAV: real RIFF/WAVE/fmt
     * /data chunks, tiny (a handful of frames), with the sample name's own
     * bytes used as the PCM payload so the result stays unique per sample
     * name and deterministic — same input always produces the same bytes,
     * which is all the identity/position assertions below actually need.
     */
    private fun placeholderAudio(sampleName: String): ByteArray {
        val nameBytes = sampleName.toByteArray(Charsets.US_ASCII)
        val frameCount = maxOf(1, nameBytes.size / 2) // 16-bit mono = 2 bytes/frame
        val dataSize = frameCount * 2
        val sampleRate = 8000
        val out = ByteArrayOutputStream()
        fun le32(v: Int) {
            out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
            out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF)
        }
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v ushr 8) and 0xFF) }

        out.write("RIFF".toByteArray()); le32(36 + dataSize); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); le32(16)
        le16(1)                       // PCM
        le16(1)                       // mono
        le32(sampleRate)
        le32(sampleRate * 2)          // byte rate
        le16(2)                       // block align
        le16(16)                      // bits per sample
        out.write("data".toByteArray()); le32(dataSize)
        out.write(nameBytes, 0, dataSize)
        return out.toByteArray()
    }

    private fun buildTestZip(folder: String = "Instrument"): ByteArray {
        val document = DwpDocument.parse(realDwpBytes())
        val samples = DwpEngine.listSamples(document)

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("$folder/$folder.dwp"))
            zos.write(document.toBytes())
            zos.closeEntry()
            for (sample in samples) {
                zos.putNextEntry(ZipEntry("$folder/${sample.name}.wav"))
                zos.write(placeholderAudio(sample.name))
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `loads all 48 samples matched to their correct audio by name`() {
        val project = ZipProjectLoader.load(buildTestZip())
        assertEquals(48, project.audioByIndex.size)

        val samples = DwpEngine.listSamples(project.document)
        for (i in samples.indices) {
            assertArrayEquals(placeholderAudio(samples[i].name), project.audioByIndex[i])
        }
    }

    @Test(expected = ZipProjectLoader.ZipLoadException::class)
    fun `throws a clear error when the zip has no dwp file`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("nope.txt"))
            zos.write("hello".toByteArray())
            zos.closeEntry()
        }
        ZipProjectLoader.load(out.toByteArray())
    }

    @Test(expected = ZipProjectLoader.ZipLoadException::class)
    fun `throws a clear error when sample audio is missing from the zip`() {
        val document = DwpDocument.parse(realDwpBytes())
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("Instrument/Instrument.dwp"))
            zos.write(document.toBytes())
            zos.closeEntry()
            // Deliberately omit every .wav.
        }
        ZipProjectLoader.load(out.toByteArray())
    }

    @Test
    fun `export reflects a rename and survives a full load-rename-export-reload cycle`() {
        val project = ZipProjectLoader.load(buildTestZip())
        val renamedDoc = DwpEngine.renameInstrument(project.document, "Instrument", "Didas")
        val renamedProject = project.copy(document = renamedDoc)

        val exportedZipBytes = ZipProjectExporter.export(renamedProject, "Didas")
        val reloaded = ZipProjectLoader.load(exportedZipBytes)

        assertEquals("Didas/Didas.dwp", reloaded.dwpZipEntryName)

        val samples = DwpEngine.listSamples(reloaded.document)
        assertEquals("Didas_C3_127", samples[0].name)
        assertEquals("Didas_B6_127", samples[47].name)

        // Audio content is untouched by a rename -- it still carries the
        // ORIGINAL name in its placeholder payload, proving the export
        // matched bytes by position, not by (now-changed) name.
        assertArrayEquals(placeholderAudio("Instrument_C3_127"), reloaded.audioByIndex[0])
    }

    // --- Sección 13 del prompt maestro: ZipProjectLoader debe rechazar
    // proyectos ambiguos en vez de resolverlos silenciosamente escogiendo
    // "el primero que encuentre" o sobrescribiendo dentro de un mapa.

    @Test(expected = ZipProjectLoader.ZipLoadException::class)
    fun `rejects a zip containing more than one dwp file instead of silently picking the first`() {
        val document = DwpDocument.parse(realDwpBytes())
        val samples = DwpEngine.listSamples(document)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("Instrument/Instrument.dwp"))
            zos.write(document.toBytes())
            zos.closeEntry()
            // A second .dwp, anywhere in the zip -- the project is now
            // ambiguous about which one is "the" instrument.
            zos.putNextEntry(ZipEntry("Instrument/Backup/Instrument2.dwp"))
            zos.write(document.toBytes())
            zos.closeEntry()
            for (sample in samples) {
                zos.putNextEntry(ZipEntry("Instrument/${sample.name}.wav"))
                zos.write(placeholderAudio(sample.name))
                zos.closeEntry()
            }
        }
        ZipProjectLoader.load(out.toByteArray())
    }

    @Test(expected = ZipProjectLoader.ZipLoadException::class)
    fun `rejects a zip where two wav files in different folders share the same base name`() {
        // Sección 13: "Nunca permitir que FolderA/Sample.wav, FolderB/Sample.wav
        // se sobrescriban silenciosamente dentro de un mapa." This must be a
        // clear, rejected error, not a coin-flip pick of one of the two.
        val document = DwpDocument.parse(realDwpBytes())
        val samples = DwpEngine.listSamples(document)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("Instrument/Instrument.dwp"))
            zos.write(document.toBytes())
            zos.closeEntry()
            for (sample in samples) {
                zos.putNextEntry(ZipEntry("Instrument/${sample.name}.wav"))
                zos.write(placeholderAudio(sample.name))
                zos.closeEntry()
            }
            // Ambiguous duplicate: same base name as samples[0], different folder.
            zos.putNextEntry(ZipEntry("Instrument/Extra/${samples[0].name}.wav"))
            zos.write(placeholderAudio("decoy"))
            zos.closeEntry()
        }
        ZipProjectLoader.load(out.toByteArray())
    }

    // --- Sección 14 del prompt maestro: ZipProjectExporter debe validar
    // nombres antes de construir cualquier ruta de zip.

    @Test
    fun `export rejects a folder name that would cause path traversal`() {
        val project = ZipProjectLoader.load(buildTestZip())
        try {
            ZipProjectExporter.export(project, "../evil")
            throw AssertionError("Expected IllegalArgumentException for a path-traversal folder name")
        } catch (e: IllegalArgumentException) {
            // Expected -- '/' in the folder name is rejected outright.
        }
    }

    @Test
    fun `export rejects a sample name containing a path separator`() {
        // Simulates a malformed/malicious 0x01f5 payload that decodes as
        // printable text containing '/': DwpEngine.listSamples has no way
        // to know this isn't a legitimate name, so the exporter itself must
        // be the one refusing to turn it into an unsafe zip path.
        val project = ZipProjectLoader.load(buildTestZip())
        val maliciousDoc = DwpEngine.renameInstrument(project.document, "Instrument", "../evil")
        val maliciousProject = project.copy(document = maliciousDoc)

        try {
            ZipProjectExporter.export(maliciousProject, "SafeFolder")
            throw AssertionError("Expected IllegalArgumentException for a sample name containing '/'")
        } catch (e: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun `export rejects two samples that ended up with the same name`() {
        val project = ZipProjectLoader.load(buildTestZip())
        val samples = DwpEngine.listSamples(project.document)
        // Force sample #1 to have the exact same name as sample #0 --
        // writing this to disk would silently overwrite one .wav entry
        // with the other inside the zip.
        val collidedDoc = DwpEngine.replaceSampleAudio(
            doc = project.document,
            sampleContainerIndex = 1,
            newName = samples[0].name,
            newPath = samples[1].dwpPath,
            wav = com.jvk.dwpcreator.domain.audio.WavDecoder.decode(project.audioByIndex[1])
        )
        val collidedProject = project.copy(document = collidedDoc)

        try {
            ZipProjectExporter.export(collidedProject, "Instrument")
            throw AssertionError("Expected IllegalArgumentException for duplicate sample names")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains(samples[0].name))
        }
    }

    // --- Sección 20-23 del prompt maestro: límites defensivos de recursos,
    // verificados con límites diminutos inyectados vía la sobrecarga
    // `internal` de `load()` -- sin necesidad de asignar cientos de MB
    // reales solo para ejercitar la comprobación.

    @Test(expected = ZipProjectLoader.ZipLoadException::class)
    fun `rejects a zip with more entries than MAX_ENTRIES allows`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            repeat(3) { i ->
                zos.putNextEntry(ZipEntry("file$i.txt"))
                zos.write("hello".toByteArray())
                zos.closeEntry()
            }
        }
        ZipProjectLoader.load(out.toByteArray(), maxEntries = 2, maxSingleEntryBytes = 1_000, maxTotalUncompressedBytes = 10_000)
    }

    @Test
    fun `MAX_ENTRIES counts directory entries too, not just files -- Seccion 12`() {
        // A zip crafted with many empty directory entries and zero real
        // files must still be rejected by the entry-count limit -- before
        // this fix, entryCount only incremented for non-directory entries,
        // so a flood of directories sailed straight past MAX_ENTRIES and
        // the zip would instead fail later for a completely different,
        // unrelated reason ("no .dwp found"). Asserting the exact message
        // here is what actually proves this fix, not just any
        // ZipLoadException occurring for whatever reason.
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            repeat(5) { i ->
                zos.putNextEntry(ZipEntry("dir$i/"))
                zos.closeEntry()
            }
        }
        val ex = assertThrows(ZipProjectLoader.ZipLoadException::class.java) {
            ZipProjectLoader.load(out.toByteArray(), maxEntries = 3, maxSingleEntryBytes = 1_000, maxTotalUncompressedBytes = 10_000)
        }
        assertTrue(
            "Expected rejection for exceeding MAX_ENTRIES (directories counted), got: ${ex.message}",
            ex.message!!.contains("entradas")
        )
    }

    @Test
    fun `accepts a zip within MAX_ENTRIES (boundary check, no false positive)`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            repeat(2) { i ->
                zos.putNextEntry(ZipEntry("file$i.txt"))
                zos.write("hello".toByteArray())
                zos.closeEntry()
            }
        }
        // 2 entries with a limit of exactly 2 must NOT be rejected on entry
        // count alone (it will still fail later for lacking a .dwp, which is
        // a separate, expected error -- this test only cares that it's not
        // rejected for the entry-count limit).
        try {
            ZipProjectLoader.load(out.toByteArray(), maxEntries = 2, maxSingleEntryBytes = 1_000, maxTotalUncompressedBytes = 10_000)
        } catch (e: ZipProjectLoader.ZipLoadException) {
            assertTrue("Expected rejection for missing .dwp, not for entry count", e.message!!.contains(".dwp"))
        }
    }

    @Test
    fun `rejects a single entry larger than MAX_SINGLE_ENTRY_BYTES`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("big.bin"))
            zos.write(ByteArray(1_000)) // 1000 bytes, over our injected 100-byte limit
            zos.closeEntry()
        }
        val ex = assertThrows(ZipProjectLoader.ZipLoadException::class.java) {
            ZipProjectLoader.load(out.toByteArray(), maxEntries = 10, maxSingleEntryBytes = 100, maxTotalUncompressedBytes = 10_000)
        }
        assertTrue(ex.message!!.contains("big.bin"))
    }

    @Test
    fun `rejects a zip whose total uncompressed size exceeds MAX_TOTAL_UNCOMPRESSED_BYTES even if no single entry is too large`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            repeat(3) { i ->
                zos.putNextEntry(ZipEntry("part$i.bin"))
                zos.write(ByteArray(100)) // 100 bytes each, none individually over the single-entry limit
                zos.closeEntry()
            }
        }
        // 3 x 100 = 300 bytes total, over an injected 250-byte total limit,
        // even though each individual entry (100 bytes) is well under the
        // injected 1000-byte single-entry limit.
        val ex = assertThrows(ZipProjectLoader.ZipLoadException::class.java) {
            ZipProjectLoader.load(out.toByteArray(), maxEntries = 10, maxSingleEntryBytes = 1_000, maxTotalUncompressedBytes = 250)
        }
        assertTrue(ex.message!!.contains("250"))
    }
}
