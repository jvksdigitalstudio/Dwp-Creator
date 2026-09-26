package com.jvk.dwpcreator.domain.io

import com.jvk.dwpcreator.domain.dwp.DwpDocument
import com.jvk.dwpcreator.domain.dwp.DwpEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Cobertura de las vías "sin duplicar el instrumento en memoria" de la capa
 * de I/O: `ZipProjectLoader.load(InputStream)` y
 * `ZipProjectExporter.prepare(...)` + `PreparedZipExport.writeTo(...)`, que
 * son las que usa la app real (la vía `ByteArray` se conserva para tests y
 * como envoltorio). Lo que se comprueba es que ambas vías son EQUIVALENTES:
 * el streaming no puede cambiar el contenido de lo cargado ni de lo exportado.
 */
class ZipProjectStreamingTest {

    private fun realDwpBytes(): ByteArray {
        val stream = javaClass.classLoader!!.getResourceAsStream("Instrument.dwp")
            ?: error("Test fixture Instrument.dwp not found on test classpath")
        return stream.readBytes()
    }

    /** Mismo WAV mínimo válido que usan ZipProjectIoTest/ZipProjectExporterProgressTest. */
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

    private fun assertSameProject(expected: LoadedProject, actual: LoadedProject) {
        assertEquals(expected.dwpZipEntryName, actual.dwpZipEntryName)
        assertArrayEquals(expected.document.toBytes(), actual.document.toBytes())
        assertEquals(expected.audioByIndex.size, actual.audioByIndex.size)
        for (i in expected.audioByIndex.indices) {
            assertArrayEquals("audio de la muestra #$i", expected.audioByIndex[i], actual.audioByIndex[i])
        }
    }

    @Test
    fun `load from an InputStream gives the same project as load from a ByteArray`() {
        val zipBytes = ZipProjectExporter.export(realLoadedProject(), "Instrument")

        val fromBytes = ZipProjectLoader.load(zipBytes)
        val fromStream = ZipProjectLoader.load(ByteArrayInputStream(zipBytes))

        assertEquals(48, fromStream.audioByIndex.size)
        assertSameProject(fromBytes, fromStream)
    }

    @Test
    fun `prepare then writeTo exports exactly what export() exports`() {
        val project = realLoadedProject()

        val viaExport = ZipProjectLoader.load(ZipProjectExporter.export(project, "Instrument"))

        val streamed = ByteArrayOutputStream()
        ZipProjectExporter.prepare(project, "Instrument").writeTo(streamed)
        val viaStreaming = ZipProjectLoader.load(streamed.toByteArray())

        assertSameProject(viaExport, viaStreaming)
    }

    @Test
    fun `a streamed export survives a full reload with every sample matched to its audio`() {
        val project = realLoadedProject()
        val streamed = ByteArrayOutputStream()
        ZipProjectExporter.prepare(project, "Didas").writeTo(streamed)

        val reloaded = ZipProjectLoader.load(ByteArrayInputStream(streamed.toByteArray()))
        assertEquals("Didas/Didas.dwp", reloaded.dwpZipEntryName)
        assertEquals(48, reloaded.audioByIndex.size)
        val samples = DwpEngine.listSamples(reloaded.document)
        for (i in samples.indices) {
            assertArrayEquals(project.audioByIndex[i], reloaded.audioByIndex[i])
        }
    }

    @Test
    fun `writeTo closes the destination stream`() {
        var closed = false
        val sink = object : OutputStream() {
            override fun write(b: Int) {}
            override fun write(b: ByteArray, off: Int, len: Int) {}
            override fun close() { closed = true }
        }

        ZipProjectExporter.prepare(realLoadedProject(), "Instrument").writeTo(sink)

        assertTrue("writeTo debe cerrar el flujo de destino (el llamador usa un solo `use`)", closed)
    }

    @Test
    fun `prepare validates before anything could be written`() {
        val project = realLoadedProject()
        var threw = false
        try {
            ZipProjectExporter.prepare(project, "../evil")
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("una carpeta con path traversal debe rechazarse en prepare(), antes de escribir nada", threw)
    }

    @Test
    fun `prepare reports real progress once per sample, in order`() {
        val seen = mutableListOf<Int>()
        ZipProjectExporter.prepare(realLoadedProject(), "Instrument") { current, total, _ ->
            assertEquals(48, total)
            seen += current
        }
        assertEquals((1..48).toList(), seen)
    }
}
