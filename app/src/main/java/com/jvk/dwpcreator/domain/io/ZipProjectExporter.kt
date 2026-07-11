package com.jvk.dwpcreator.domain.io

import com.jvk.dwpcreator.domain.dwp.DwpEngine
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Rebuilds a `.zip` ready for FL Studio Mobile from a [LoadedProject]: one
 * `.dwp` + one `.wav` per sample, all sharing [newFolderName] as their
 * single top-level folder (dwp and sample folder name always match, as
 * DirectWave/FL Mobile expect).
 *
 * Always uses the **current** sample names from `project.document` (so a
 * prior [com.jvk.dwpcreator.domain.dwp.DwpEngine.renameInstrument] is
 * reflected), matched by position to `project.audioByIndex`.
 */
object ZipProjectExporter {

    fun export(project: LoadedProject, newFolderName: String): ByteArray {
        val samples = DwpEngine.listSamples(project.document)
        require(samples.size == project.audioByIndex.size) {
            "Descuadre interno: ${samples.size} muestras en el .dwp vs ${project.audioByIndex.size} audios cargados en memoria."
        }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("$newFolderName/$newFolderName.dwp"))
            zos.write(project.document.toBytes())
            zos.closeEntry()

            for ((i, sample) in samples.withIndex()) {
                zos.putNextEntry(ZipEntry("$newFolderName/${sample.name}.wav"))
                zos.write(project.audioByIndex[i])
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
