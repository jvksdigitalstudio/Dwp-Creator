package com.jvk.dwpcreator.dwp.io

import com.jvk.dwpcreator.codec.wav.WavDecoder
import com.jvk.dwpcreator.codec.wav.WavFormatException
import com.jvk.dwpcreator.dwp.DwpAudioPatcher
import com.jvk.dwpcreator.dwp.DwpSampleCatalog
import java.io.ByteArrayOutputStream

/**
 * Rebuilds a `.zip` ready for FL Studio Mobile from a [LoadedProject]: one
 * `.dwp` + one `.wav` per sample, all sharing [newFolderName] as their
 * single top-level folder (dwp and sample folder name always match, as
 * DirectWave/FL Mobile expect).
 *
 * Always uses the **current** sample names from `project.document` (so a
 * prior [com.jvk.dwpcreator.dwp.DwpRenamer.renameInstrument] is
 * reflected), matched by position to `project.audioByIndex`.
 *
 * Before this fix, export() only ever wrote `project.document.toBytes()`
 * straight through: text (names/paths) got updated by a prior
 * [DwpRenamer.renameInstrument] call, but each sample's audio-format blob
 * (`0x01f7` — frame count, channels, sample rate, bits/sample) stayed
 * whatever the *original template* had. As soon as the actual audio bytes
 * being written differed from the template's original audio in duration or
 * format, DirectWave/FL Mobile would read a frame count that didn't match
 * the file on disk and the import would come out corrupt. This is now
 * fixed: every sample's `0x01f7` is recomputed from the real bytes about to
 * be written, via [DwpAudioPatcher.replaceSampleAudio].
 */
object ZipProjectExporter {

    /**
     * Validates that [name] is safe to use as a single path segment inside
     * the export zip. [context] identifies the offending name in the
     * exception message (e.g. "nombre de carpeta" or "nombre de la muestra
     * \"foo\"").
     *
     * This matters because a sample's name ultimately comes from the
     * `0x01f5` payload of a *parsed* `.dwp` (see [DwpSampleCatalog.listSamples]) --
     * for a malformed or maliciously crafted file, that payload could
     * contain anything that happens to decode as printable text, including
     * `/`, `\`, or `..` segments. Building a [ZipEntry] path by simple string
     * concatenation with an unvalidated name is exactly the shape of a
     * "zip slip" path-traversal bug (Sección 14 del prompt maestro).
     *
     * The policy is deliberately simple and safe: a name must be non-blank,
     * must not contain `/` or `\`, and must not be exactly `.` or `..`. It
     * does not need to allow subdirectories -- every valid export always
     * places the `.dwp` and every `.wav` directly inside `newFolderName`.
     */
    private fun requireSafeZipSegment(name: String, context: String) {
        require(name.isNotBlank()) { "$context está vacío; no es un nombre de archivo/carpeta válido." }
        require(!name.contains('/') && !name.contains('\\')) {
            "$context (\"$name\") contiene un separador de ruta ('/' o '\\\\'); no está permitido en un solo segmento de ruta del zip exportado."
        }
        require(name != "." && name != "..") {
            "$context (\"$name\") es un nombre de segmento de ruta reservado ('.'/'..'); se rechaza para evitar escribir fuera de la carpeta del proyecto."
        }
    }

    /**
     * Valida [project] y resincroniza los metadatos de audio de cada muestra
     * SIN escribir nada todavía; ver [PreparedZipExport].
     *
     * @param onProgress invocado una vez por cada muestra ya resincronizada
     * (1-based `current`, más el nombre de esa muestra), antes de empezar a
     * escribir el zip. Callback opcional -- por defecto no hace nada, para
     * no forzar a cada llamador existente a proveerlo.
     */
    fun prepare(
        project: LoadedProject,
        newFolderName: String,
        onProgress: (current: Int, total: Int, sampleName: String) -> Unit = { _, _, _ -> }
    ): PreparedZipExport {
        requireSafeZipSegment(newFolderName, "El nombre de carpeta/proyecto")

        val samples = DwpSampleCatalog.listSamples(project.document)
        require(samples.size == project.audioByIndex.size) {
            "Descuadre interno: ${samples.size} muestras en el .dwp vs ${project.audioByIndex.size} audios cargados en memoria."
        }
        for (sample in samples) {
            requireSafeZipSegment(sample.name, "El nombre de la muestra \"${sample.name}\"")
        }
        val duplicateNames = samples.map { it.name }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        require(duplicateNames.isEmpty()) {
            "Hay muestras con el mismo nombre (${duplicateNames.joinToString(", ")}); " +
                "escribirlas produciría entradas de zip duplicadas que se sobrescribirían en silencio. Renómbralas primero."
        }

        // Re-sync each sample's audio-format metadata with the actual bytes
        // that will be written for it. Name/path are passed through
        // unchanged here (renaming is already handled by
        // DwpRenamer.renameInstrument before export is called) — only the
        // binary format fields are being corrected.
        var doc = project.document
        for ((i, sample) in samples.withIndex()) {
            val wav = try {
                WavDecoder.decode(project.audioByIndex[i])
            } catch (e: WavFormatException) {
                throw IllegalStateException(
                    "La muestra \"${sample.name}\" no se pudo leer como WAV válido: ${e.message}", e
                )
            }
            doc = DwpAudioPatcher.replaceSampleAudio(
                doc = doc,
                sampleContainerIndex = i,
                newName = sample.name,
                newPath = sample.dwpPath,
                wav = wav
            )
            onProgress(i + 1, samples.size, sample.name)
        }

        return PreparedZipExport(
            folderName = newFolderName,
            sampleNames = samples.map { it.name },
            audioByIndex = project.audioByIndex,
            dwpBytes = doc.toBytes()
        )
    }

    /**
     * Exporta [project] como un zip completo en memoria. Equivale a
     * [prepare] + [PreparedZipExport.writeTo] sobre un `ByteArrayOutputStream`;
     * se conserva por compatibilidad y para tests. La app usa [prepare] +
     * [PreparedZipExport.writeTo] directamente contra el archivo de destino,
     * para no duplicar el instrumento entero en memoria.
     */
    fun export(
        project: LoadedProject,
        newFolderName: String,
        onProgress: (current: Int, total: Int, sampleName: String) -> Unit = { _, _, _ -> }
    ): ByteArray {
        val out = ByteArrayOutputStream()
        prepare(project, newFolderName, onProgress).writeTo(out)
        return out.toByteArray()
    }
}
