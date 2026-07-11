package com.jvk.dwpcreator.domain.io

import com.jvk.dwpcreator.domain.dwp.DwpDocument
import com.jvk.dwpcreator.domain.dwp.DwpEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
 */
class ZipProjectIoTest {

    private fun realDwpBytes(): ByteArray {
        val stream = javaClass.classLoader!!.getResourceAsStream("Instrument.dwp")
            ?: error("Test fixture Instrument.dwp not found on test classpath")
        return stream.readBytes()
    }

    private fun placeholderAudio(sampleName: String) = "WAV-$sampleName".toByteArray()

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
}
