package com.jvk.dwpcreator.domain.io

import com.jvk.dwpcreator.domain.dwp.DwpDocument
import com.jvk.dwpcreator.domain.dwp.DwpEngine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
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

    /**
     * Defensive resource caps (Sección 20/21 del prompt maestro), enforced
     * *during* decompression, not after the fact -- a zip entry's declared
     * size in its header is not trustworthy on its own (a "zip bomb" can
     * claim a small size but decompress to far more). Values are chosen to
     * comfortably fit the app's real use case (a single DirectWave
     * instrument -- the reference fixture is 48 samples, ~144MB total
     * uncompressed) while still bounding worst-case memory use for a
     * malicious or corrupt zip:
     */
    // A single instrument could legitimately have many samples across a
    // wide key range; 2000 individual zip entries is already far beyond
    // any realistic DirectWave export.
    private const val MAX_ENTRIES = 2000

    // A single sample .wav at 24-bit/192kHz stereo for over 10 minutes is
    // still under this; anything larger is far outside what a "sample"
    // (as opposed to a full song render) should ever be.
    private const val MAX_SINGLE_ENTRY_BYTES = 200L * 1024 * 1024 // 200 MB

    // The real 48-sample reference fixture is ~144MB uncompressed; this
    // gives roughly 7x headroom for larger legitimate instruments before
    // rejecting outright.
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 1024L * 1024 * 1024 // 1 GB

    fun load(zipBytes: ByteArray): LoadedProject =
        load(zipBytes, MAX_ENTRIES, MAX_SINGLE_ENTRY_BYTES, MAX_TOTAL_UNCOMPRESSED_BYTES)

    /**
     * Same as [load], but with the resource limits as explicit parameters
     * instead of the hardcoded production constants. `internal` rather than
     * `private` purely so the test suite can verify the rejection behavior
     * itself (too many entries / single entry too large / total too large)
     * using tiny limits, without needing to actually allocate hundreds of
     * megabytes just to exercise the check. Production code always goes
     * through the public no-arg [load] above, which uses the real
     * documented constants.
     */
    internal fun load(
        zipBytes: ByteArray,
        maxEntries: Int,
        maxSingleEntryBytes: Long,
        maxTotalUncompressedBytes: Long
    ): LoadedProject {
        val entries = LinkedHashMap<String, ByteArray>()
        val duplicateEntryNames = mutableListOf<String>()
        var totalUncompressedBytes = 0L
        var entryCount = 0
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                // Sección 12 del prompt maestro Pass 3: MAX_ENTRIES must
                // count every entry the zip format makes us process --
                // including directories -- not just the ones whose bytes we
                // go on to store. A zip crafted with a huge number of empty
                // directory entries and few/no real files would otherwise
                // sail straight past this limit, defeating its purpose as a
                // structural safety cap.
                entryCount++
                if (entryCount > maxEntries) {
                    throw ZipLoadException(
                        "El zip contiene más de $maxEntries entradas (incluyendo directorios); se rechaza como " +
                            "medida de seguridad (límite MAX_ENTRIES, ver ZipProjectLoader)."
                    )
                }
                if (!entry.isDirectory) {
                    // A zip with two entries sharing the exact same path is
                    // malformed/ambiguous -- never silently keep "whichever
                    // came last" the way a plain map assignment would.
                    if (entries.containsKey(entry.name)) duplicateEntryNames += entry.name

                    val bytes = readBytesLimited(zis, maxSingleEntryBytes, "La entrada '${entry.name}'")
                    totalUncompressedBytes += bytes.size
                    if (totalUncompressedBytes > maxTotalUncompressedBytes) {
                        throw ZipLoadException(
                            "El total descomprimido del zip supera $maxTotalUncompressedBytes bytes; se rechaza " +
                                "como medida de seguridad (límite MAX_TOTAL_UNCOMPRESSED_BYTES, ver ZipProjectLoader)."
                        )
                    }
                    entries[entry.name] = bytes
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        if (duplicateEntryNames.isNotEmpty()) {
            throw ZipLoadException(
                "El zip contiene entradas duplicadas con el mismo nombre exacto: " +
                    duplicateEntryNames.distinct().joinToString(", ") + ". Zip ambiguo/corrupto, se rechaza."
            )
        }

        val dwpEntryNames = entries.keys.filter { it.endsWith(".dwp", ignoreCase = true) }
        if (dwpEntryNames.isEmpty()) {
            throw ZipLoadException("El zip no contiene ningún archivo .dwp.")
        }
        if (dwpEntryNames.size > 1) {
            throw ZipLoadException(
                "El zip contiene ${dwpEntryNames.size} archivos .dwp (${dwpEntryNames.joinToString(", ")}); " +
                    "se esperaba exactamente uno. Proyecto ambiguo, se rechaza."
            )
        }
        val dwpEntryName = dwpEntryNames.single()

        val document = try {
            DwpDocument.parse(entries.getValue(dwpEntryName))
        } catch (e: Exception) {
            throw ZipLoadException("El archivo .dwp no es válido: ${e.message}")
        }

        // Index every .wav by its base file name (no path, no extension),
        // case-insensitive -- matches how DirectWave/FL Mobile resolve samples
        // relative to the .dwp regardless of the exact folder they sit in.
        // Normalizes both '/' and '\' as path separators: most zips use '/'
        // per the ZIP spec, but some Windows-authored tools still emit '\'.
        val wavGroupsByBaseName = LinkedHashMap<String, MutableList<String>>()
        for (path in entries.keys) {
            if (!path.endsWith(".wav", ignoreCase = true)) continue
            val baseName = path.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
            wavGroupsByBaseName.getOrPut(baseName.lowercase()) { mutableListOf() }.add(path)
        }
        val ambiguousBaseNames = wavGroupsByBaseName.filterValues { it.size > 1 }
        if (ambiguousBaseNames.isNotEmpty()) {
            val detail = ambiguousBaseNames.entries.joinToString("; ") { (base, paths) -> "\"$base\": ${paths.joinToString(", ")}" }
            throw ZipLoadException(
                "El zip contiene más de un .wav con el mismo nombre base en carpetas distintas, lo cual es ambiguo " +
                    "y nunca se resuelve silenciosamente: $detail. Proyecto rechazado -- renombra o elimina los duplicados."
            )
        }
        val wavByBaseName: Map<String, Pair<String, ByteArray>> = wavGroupsByBaseName.mapValues { (_, paths) ->
            val path = paths.single()
            path to entries.getValue(path)
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

    /**
     * Reads all remaining bytes of [input] (a single zip entry's
     * decompression stream), throwing [ZipLoadException] the moment more
     * than [maxBytes] have actually been decompressed -- checked *during*
     * reading, not against the entry's (untrustworthy) declared size
     * afterward. This is what actually defends against a "zip bomb" entry
     * that declares a small size but decompresses to far more.
     */
    private fun readBytesLimited(input: InputStream, maxBytes: Long, context: String): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            total += n
            if (total > maxBytes) {
                throw ZipLoadException("$context supera el límite de $maxBytes bytes permitido por entrada; se rechaza como medida de seguridad.")
            }
            buffer.write(chunk, 0, n)
        }
        return buffer.toByteArray()
    }
}
