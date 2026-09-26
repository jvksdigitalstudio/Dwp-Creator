package com.jvk.dwpcreator.domain.dwp.monolithic

import com.jvk.dwpcreator.domain.audio.WavDecoder
import com.jvk.dwpcreator.domain.dwp.DwpDocument
import com.jvk.dwpcreator.domain.dwp.DwpEngine
import com.jvk.dwpcreator.domain.flac.FlacEncoder
import com.jvk.dwpcreator.domain.io.LoadedProject

/**
 * FASE 2 (integración real en el flujo de aplicación). Punto de entrada de
 * más alto nivel del subsistema Monolithic DWP: convierte un
 * [LoadedProject] completo (el mismo tipo que ya usa el flujo de
 * exportación ZIP real, [com.jvk.dwpcreator.domain.io.ZipProjectExporter])
 * en un [DwpDocument] donde **todas** las muestras tienen su audio embebido
 * como FLAC (`0x0206`), listo para servir bytes de un único `.dwp`
 * autocontenido.
 *
 * Esta clase no reimplementa nada de [MonolithicDwpBuilder]/
 * [MonolithicDwpAudioBuilder]/[MonolithicDwpStructureBuilder]/
 * [MonolithicDwpValidator] -- únicamente los orquesta una vez por muestra,
 * en el mismo estilo de "fold" ya auditado y en producción que usa
 * [com.jvk.dwpcreator.domain.io.ZipProjectExporter.export] con
 * [DwpEngine.replaceSampleAudio]: se parte del documento actual, se decodifica
 * el WAV real de cada muestra (los mismos bytes en memoria que
 * `ZipProjectExporter` usaría para escribir un `.wav`), y se pasa el
 * documento resultante como entrada al siguiente sample -- así cada llamada
 * a [MonolithicDwpBuilder.build] solo modifica el contenedor de sample que
 * le corresponde y preserva el resto byte a byte, exactamente como exige
 * Sección 10/19 de la especificación (`Doc/23`).
 *
 * A diferencia de `ZipProjectExporter`, esta clase **no conoce `.zip` en
 * absoluto** (Sección 6 del prompt de la fase de especificación: "ZIP y
 * Monolithic DWP son conceptos diferentes") -- el resultado es un único
 * `.dwp` autocontenido, sin archivos `.wav` externos, porque el audio ya
 * vive embebido dentro de cada contenedor de sample.
 *
 * Nota de validación por lote: [MonolithicDwpBuilder.build] valida (por
 * defecto) cada conversión individual comparando el documento justo antes
 * y justo después de esa muestra concreta -- no reprocesa el documento
 * completo en cada paso -- así que encadenar N muestras cuesta O(N), no
 * O(N²), y cualquier fallo se reporta ya localizado a la muestra exacta que
 * lo causó (índice y nombre), no como un fallo genérico de "el documento
 * final no valida".
 */
object MonolithicDwpProjectBuilder {

    /**
     * Construye el [DwpDocument] Monolithic completo de [project]. Lanza
     * [IllegalStateException] si algún `.wav` en memoria no es un WAV válido
     * (mismo criterio que usa hoy `ZipProjectExporter.export`), o cualquier
     * subclase de [MonolithicDwpException] que ya lancen
     * [MonolithicDwpBuilder]/[MonolithicDwpAudioBuilder]/[MonolithicDwpValidator]
     * -- por ejemplo [UnsupportedMonolithicFormatException] si alguna
     * muestra es PCM_32/FLOAT_32 (formato que [PcmNormalizer] rechaza
     * explícitamente, ver su KDoc), sin que esta clase intente adivinar ni
     * forzar una conversión no evidenciada.
     */
    /**
     * @param onProgress invocado una vez por cada muestra ya embebida como
     * FLAC (1-based `current`, más el nombre de esa muestra). Opcional --
     * por defecto no hace nada.
     */
    fun build(
        project: LoadedProject,
        flacBlockSize: Int = FlacEncoder.DEFAULT_BLOCK_SIZE,
        onProgress: (current: Int, total: Int, sampleName: String) -> Unit = { _, _, _ -> }
    ): DwpDocument {
        val samples = DwpEngine.listSamples(project.document)
        require(samples.size == project.audioByIndex.size) {
            "Descuadre interno: ${samples.size} muestras en el .dwp vs ${project.audioByIndex.size} audios cargados en memoria."
        }

        var doc = project.document
        for (i in samples.indices) {
            val wav = try {
                WavDecoder.decode(project.audioByIndex[i])
            } catch (e: WavDecoder.WavFormatException) {
                throw IllegalStateException(
                    "La muestra \"${samples[i].name}\" no se pudo leer como WAV válido: ${e.message}", e
                )
            }
            doc = MonolithicDwpBuilder.build(
                doc = doc,
                sampleContainerIndex = i,
                wav = wav,
                flacBlockSize = flacBlockSize
            ).document
            onProgress(i + 1, samples.size, samples[i].name)
        }
        return doc
    }

    /**
     * Bytes finales listos para escribir a disco como un único `.dwp`
     * Monolithic autocontenido -- sin `.wav` externos, sin `.zip`.
     */
    fun buildBytes(
        project: LoadedProject,
        flacBlockSize: Int = FlacEncoder.DEFAULT_BLOCK_SIZE,
        onProgress: (current: Int, total: Int, sampleName: String) -> Unit = { _, _, _ -> }
    ): ByteArray =
        DwpBinaryAssembler.assemble(build(project, flacBlockSize, onProgress))
}
