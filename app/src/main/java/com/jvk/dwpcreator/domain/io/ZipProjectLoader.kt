package com.jvk.dwpcreator.domain.io

import com.jvk.dwpcreator.domain.dwp.DwpDocument
import com.jvk.dwpcreator.domain.dwp.DwpEngine
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Loads a `.zip` (as exported by FL Studio Desktop: one `.dwp` + one `.wav`
 * per sample) into a [LoadedProject].
 *
 * Pure JVM logic — no Android dependency, so it's fully unit-testable. The
 * Android layer only needs to turn a `content://` [android.net.Uri] into a
 * `ByteArray` (via `ContentResolver.openInputStream`) and hand it to
 * [load].
 */
object ZipProjectLoader {

    class ZipLoadException(message: String) : Exception(message)

    fun load(zipBytes: ByteArray): LoadedProject {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val dwpEntryName = entries.keys.firstOrNull { it.endsWith(".dwp", ignoreCase = true) }
            ?: throw ZipLoadException("El zip no contiene ningún archivo .dwp.")

        val document = try {
            DwpDocument.parse(entries.getValue(dwpEntryName))
        } catch (e: Exception) {
            throw ZipLoadException("El archivo .dwp no es válido: ${e.message}")
        }

        // Index every .wav by its base file name (no path, no extension),
        // case-insensitive -- matches how DirectWave/FL Mobile resolve samples
        // relative to the .dwp regardless of the exact folder they sit in.
        val wavByBaseName = entries.entries
            .filter { it.key.endsWith(".wav", ignoreCase = true) }
            .associate { (path, bytes) ->
                val baseName = path.substringAfterLast('/').substringBeforeLast('.')
                baseName.lowercase() to (path to bytes)
            }

        val samples = DwpEngine.listSamples(document)
        val audioByIndex = ArrayList<ByteArray>(samples.size)
        val originalNames = ArrayList<String>(samples.size)
        val missing = mutableListOf<String>()

        for (sample in samples) {
            val match = wavByBaseName[sample.name.lowercase()]
            if (match == null) {
                missing += sample.name
            } else {
                audioByIndex += match.second
                originalNames += match.first
            }
        }

        if (missing.isNotEmpty()) {
            throw ZipLoadException(
                "El .dwp hace referencia a ${missing.size} muestra(s) sin su .wav correspondiente en el zip: " +
                    missing.take(5).joinToString(", ") + if (missing.size > 5) ", …" else ""
            )
        }

        return LoadedProject(document, dwpEntryName, audioByIndex, originalNames)
    }
}
