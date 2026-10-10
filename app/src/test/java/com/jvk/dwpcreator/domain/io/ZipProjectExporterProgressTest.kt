package com.jvk.dwpcreator.domain.io

import com.jvk.dwpcreator.domain.dwp.DwpDocument
import com.jvk.dwpcreator.domain.dwp.DwpEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Cobertura del parámetro `onProgress` añadido a [ZipProjectExporter.export]
 * (progreso real de exportación para la UI, ver `ExportProgressOverlay`) --
 * usando el fixture real de 48 muestras, no uno sintético de 2-3, porque lo
 * que esto prueba es precisamente que el conteo y el orden se sostienen a
 * lo largo de un proyecto del tamaño real de este mismo repositorio.
 */
class ZipProjectExporterProgressTest {

    private fun realDwpBytes(): ByteArray {
        val stream = javaClass.classLoader!!.getResourceAsStream("Instrument.dwp")
            ?: error("Test fixture Instrument.dwp not found on test classpath")
        return stream.readBytes()
    }

    /** Mismo WAV mínimo válido que usa ZipProjectIoTest -- ver esa clase para el porqué. */
    private fun placeholderAudio(sampleName: String): ByteArray {
        val nameBytes = sampleName.toByteArray(Charsets.US_ASCII)
        val frameCount = maxOf(1, nameBytes.size / 2)
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
        le16(1); le16(1); le32(sampleRate); le32(sampleRate * 2); le16(2); le16(16)
        out.write("data".toByteArray()); le32(dataSize)
        out.write(nameBytes, 0, dataSize)
        return out.toByteArray()
    }

    private fun realLoadedProject(): LoadedProject {
        val document = DwpDocument.parse(realDwpBytes())
        val samples = DwpEngine.listSamples(document)
        return LoadedProject(
            document = document,
            dwpZipEntryName = "Instrument/Instrument.dwp",
            audioByIndex = samples.map { placeholderAudio(it.name) },
            originalZipEntryNames = samples.map { "${it.name}.wav" }
        )
    }

    @Test
    fun `onProgress fires once per sample, in order, 1-based, over the real 48-sample fixture`() {
        val project = realLoadedProject()
        val samples = DwpEngine.listSamples(project.document)
        val calls = mutableListOf<Triple<Int, Int, String>>()

        ZipProjectExporter.export(project, "Instrument") { current, total, sampleName ->
            calls.add(Triple(current, total, sampleName))
        }

        assertEquals(48, calls.size)
        assertTrue("cada llamada debe reportar total=48", calls.all { it.second == 48 })
        assertEquals((1..48).toList(), calls.map { it.first })
        assertEquals(samples.map { it.name }, calls.map { it.third })
    }

    @Test
    fun `export without an onProgress argument still works (default no-op)`() {
        // No debe hacer falta pasar el callback -- firma retrocompatible.
        val bytes = ZipProjectExporter.export(realLoadedProject(), "Instrument")
        assertTrue(bytes.isNotEmpty())
    }
}
