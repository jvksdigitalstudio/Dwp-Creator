package com.jvk.dwpcreator.domain.dwp.monolithic

import com.jvk.dwpcreator.domain.audio.WavDecoder
import com.jvk.dwpcreator.domain.dwp.DwpBlock
import com.jvk.dwpcreator.domain.dwp.DwpDocument
import com.jvk.dwpcreator.domain.flac.FlacEncoder

/**
 * Punto de entrada público del subsistema Monolithic DWP. Orquesta,
 * **sin implementar ninguna lógica propia de codificación ni de
 * validación** (Sección 7 del prompt maestro: "DwpBuilder NO debe
 * implementar manualmente toda la compresión FLAC"), el flujo completo:
 *
 * ```
 * DwpDocument (plantilla, ya parseada por el Core)
 *      + WAV real de reemplazo
 *      ↓
 * MonolithicDwpAudioBuilder   (PCM -> FLAC -> DwpBlock 0x0206)
 *      ↓
 * MonolithicDwpStructureBuilder (inserta/reemplaza dentro del 0x0003 objetivo)
 *      ↓
 * DwpBinaryAssembler           (DwpDocument -> bytes, cuando se piden)
 *      ↓
 * MonolithicDwpValidator       (opcional, recomendado antes de escribir a disco)
 * ```
 *
 * No convierte a `ZipProjectExporter` en el centro de esta arquitectura
 * (Sección 6 del prompt maestro: "ZIP y Monolithic DWP son conceptos
 * diferentes") -- este builder no sabe nada de `.zip`, solo de
 * [DwpDocument]. La integración con el flujo de exportación ZIP existente,
 * si se decide, es una decisión de UI/producto fuera del alcance de esta
 * clase.
 */
object MonolithicDwpBuilder {

    data class BuildResult(
        val document: DwpDocument,
        val embeddedAudio: MonolithicDwpAudioBuilder.EmbeddedAudio
    )

    /**
     * Construye un nuevo [DwpDocument] con el sample en [sampleContainerIndex]
     * de [doc] convertido a Monolithic: su audio se codifica desde [wav] a
     * FLAC y se embebe como `0x0206`, reemplazando cualquier audio embebido
     * previo de ese sample; todo lo demás del documento se preserva
     * byte a byte (Sección 10 del prompt maestro, "preservación binaria").
     *
     * Si [validate] es `true` (por defecto), el resultado se valida con
     * [MonolithicDwpValidator] antes de devolverse -- lanza
     * [MonolithicDwpValidationException] si la validación falla, en vez de
     * devolver un documento potencialmente inconsistente.
     */
    fun build(
        doc: DwpDocument,
        sampleContainerIndex: Int,
        wav: WavDecoder.DecodedWav,
        flacBlockSize: Int = FlacEncoder.DEFAULT_BLOCK_SIZE,
        validate: Boolean = true
    ): BuildResult {
        requireValidSampleIndex(doc, sampleContainerIndex)

        val embeddedAudio = MonolithicDwpAudioBuilder.build(wav, flacBlockSize)

        var seen = -1
        val newBlocks = doc.blocks.map { top ->
            if (top.tag != DwpBlock.TAG_SAMPLE_CONTAINER) return@map top
            seen++
            if (seen != sampleContainerIndex) return@map top
            MonolithicDwpStructureBuilder.applyToSampleContainer(top, wav, embeddedAudio.block)
        }
        val builtDoc = DwpDocument(doc.preamble, newBlocks)

        if (validate) {
            MonolithicDwpValidator.validateOrThrow(doc, builtDoc, sampleContainerIndex, wav)
        }

        return BuildResult(builtDoc, embeddedAudio)
    }

    /** Bytes finales listos para escribir a disco, vía [DwpBinaryAssembler]. */
    fun buildBytes(
        doc: DwpDocument,
        sampleContainerIndex: Int,
        wav: WavDecoder.DecodedWav,
        flacBlockSize: Int = FlacEncoder.DEFAULT_BLOCK_SIZE,
        validate: Boolean = true
    ): ByteArray = DwpBinaryAssembler.assemble(build(doc, sampleContainerIndex, wav, flacBlockSize, validate).document)

    private fun requireValidSampleIndex(doc: DwpDocument, sampleContainerIndex: Int) {
        val count = doc.blocks.count { it.tag == DwpBlock.TAG_SAMPLE_CONTAINER }
        require(sampleContainerIndex in 0 until count) {
            "sampleContainerIndex=$sampleContainerIndex fuera de rango: el documento tiene $count sample(s) (0..${count - 1})."
        }
    }
}
