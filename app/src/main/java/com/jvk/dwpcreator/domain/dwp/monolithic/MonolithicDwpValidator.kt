package com.jvk.dwpcreator.domain.dwp.monolithic

import com.jvk.dwpcreator.domain.audio.WavDecoder
import com.jvk.dwpcreator.domain.dwp.DwpBlock
import com.jvk.dwpcreator.domain.dwp.DwpDocument
import com.jvk.dwpcreator.domain.dwp.DwpFormatException
import com.jvk.dwpcreator.domain.dwp.DwpTokenizer
import com.jvk.dwpcreator.domain.flac.FlacDecoder
import com.jvk.dwpcreator.domain.flac.FlacDecodingException
import com.jvk.dwpcreator.domain.flac.FlacEncoder

/**
 * Validación estructural y de audio de un `.dwp` Monolithic recién
 * construido, antes de escribirlo a disco -- Sección 16/30 del prompt
 * maestro ("no basta con 'el archivo se creó'"). Cubre exactamente los
 * puntos mínimos de Doc/23_MONOLITHIC_DWP_FLAC_IMPLEMENTATION_SPEC.md §30/§36
 * que son verificables **sin** un fixture monolítico real de referencia
 * (round-trip binario, validez FLAC, coherencia de frameCount, no
 * corrupción de bloques ajenos). Lo que §36 exige y esta clase **no** puede
 * verificar -- compatibilidad real con FL Studio -- queda fuera de alcance
 * por diseño y debe validarse externamente (Doc/23 §31).
 */
object MonolithicDwpValidator {

    data class ValidationResult(val ok: Boolean, val issues: List<String>)

    /**
     * Valida [builtDoc] (el resultado de [MonolithicDwpBuilder.build])
     * contra [originalDoc] (el documento de entrada, antes de la
     * transformación) y el [wav] real cuyo audio se embebió en
     * `sampleContainerIndex`.
     */
    fun validate(
        originalDoc: DwpDocument,
        builtDoc: DwpDocument,
        sampleContainerIndex: Int,
        wav: WavDecoder.DecodedWav
    ): ValidationResult {
        val issues = mutableListOf<String>()

        // 1) Round-trip estructural físico completo + EOF exacto: DwpDocument.parse
        //    ya lanza DwpFormatException si el stream no cuadra exacto con el fin
        //    de archivo en cualquier nivel (top o anidado, vía tokenizeStrict).
        //    Comparación a nivel de bytes (no via equals()/data class): DwpDocument
        //    no sobrescribe equals(), así que dos preambles con el mismo contenido
        //    pero distinta instancia de ByteArray compararían por identidad, no
        //    por contenido -- el mismo motivo por el que DwpBlock sí lo hace.
        val builtBytes = DwpBinaryAssembler.assemble(builtDoc)
        val reparsed = try {
            DwpDocument.parse(builtBytes)
        } catch (e: DwpFormatException) {
            issues.add("Round-trip estructural falló: ${e.message}")
            null
        }
        if (reparsed != null && !reparsed.toBytes().contentEquals(builtBytes)) {
            issues.add("El documento reparseado no es byte-idéntico al documento construido (round-trip no exacto).")
        }

        // 2) No corrupción de bloques ajenos al sample objetivo: todo bloque
        //    top-level que no sea el contenedor de sample modificado debe
        //    permanecer exactamente igual al original.
        val originalContainers = originalDoc.blocks.withIndex().filter { it.value.tag == DwpBlock.TAG_SAMPLE_CONTAINER }
        if (sampleContainerIndex !in originalContainers.indices) {
            issues.add("sampleContainerIndex=$sampleContainerIndex fuera de rango (hay ${originalContainers.size} sample(s) en el original).")
            return ValidationResult(false, issues)
        }
        val targetTopLevelIndex = originalContainers[sampleContainerIndex].index

        if (originalDoc.blocks.size != builtDoc.blocks.size) {
            issues.add(
                "El número de bloques top-level cambió (${originalDoc.blocks.size} -> ${builtDoc.blocks.size}); " +
                    "se esperaba que solo el contenido del sample objetivo cambiara, no la cantidad de bloques."
            )
        } else {
            for (i in originalDoc.blocks.indices) {
                if (i == targetTopLevelIndex) continue
                if (originalDoc.blocks[i] != builtDoc.blocks[i]) {
                    issues.add("El bloque top-level #$i cambió sin haber sido el objetivo de esta operación (tag=0x${originalDoc.blocks[i].tag.toString(16)}).")
                }
            }
        }
        if (!originalDoc.preamble.contentEquals(builtDoc.preamble)) {
            issues.add("El preámbulo cambió; no debería (esta operación solo toca un sample container).")
        }

        // 3) Validación de audio: localizar 0x0206 dentro del sample objetivo,
        //    decodificarlo, y comparar contra el PCM normalizado esperado.
        if (targetTopLevelIndex < builtDoc.blocks.size) {
            val targetContainer = builtDoc.blocks[targetTopLevelIndex]
            val nested = try {
                DwpTokenizer.tokenizeStrict(targetContainer.payload, context = "contenedor de sample objetivo (validación)")
            } catch (e: DwpFormatException) {
                issues.add("No se pudo tokenizar el contenedor de sample objetivo tras la construcción: ${e.message}")
                null
            }
            if (nested != null) {
                val embedded = nested.filter { it.tag == MonolithicDwpAudioBuilder.TAG_MONOLITHIC_AUDIO }
                if (embedded.size != 1) {
                    issues.add("Se esperaba exactamente 1 bloque 0x0206 en el sample objetivo, se encontraron ${embedded.size}.")
                } else {
                    validateAudio(embedded.single().payload, wav, issues)
                }

                val audioFormat = nested.filter { it.tag == DwpBlock.TAG_AUDIO_FORMAT }
                if (audioFormat.size == 1 && audioFormat.single().payload.size == 40) {
                    validateAudioFormatBlock(audioFormat.single().payload, wav, issues)
                }
            }
        }

        return ValidationResult(issues.isEmpty(), issues)
    }

    /** Igual que [validate], pero lanza [MonolithicDwpValidationException] si hay cualquier problema. */
    fun validateOrThrow(
        originalDoc: DwpDocument,
        builtDoc: DwpDocument,
        sampleContainerIndex: Int,
        wav: WavDecoder.DecodedWav
    ) {
        val result = validate(originalDoc, builtDoc, sampleContainerIndex, wav)
        if (!result.ok) {
            throw MonolithicDwpValidationException(
                "Validación de DWP Monolithic falló con ${result.issues.size} problema(s):\n" +
                    result.issues.joinToString("\n") { "- $it" }
            )
        }
    }

    private fun validateAudio(flacPayload: ByteArray, wav: WavDecoder.DecodedWav, issues: MutableList<String>) {
        val decoded = try {
            FlacDecoder.decode(flacPayload)
        } catch (e: FlacDecodingException) {
            issues.add("El FLAC embebido en 0x0206 no se pudo decodificar: ${e.message}")
            return
        }
        val expectedPcm = try {
            PcmNormalizer.normalize(wav)
        } catch (e: UnsupportedMonolithicFormatException) {
            issues.add("No se pudo normalizar el WAV de referencia para validar el audio: ${e.message}")
            return
        }
        if (decoded.pcm != expectedPcm) {
            issues.add(
                "El PCM decodificado del FLAC embebido no coincide bit-exacto con el PCM normalizado de origen " +
                    "(frameCount decodificado=${decoded.pcm.frameCount}, esperado=${expectedPcm.frameCount})."
            )
        }
        if (decoded.streamInfo.totalSamples != wav.frameCount.toLong()) {
            issues.add(
                "STREAMINFO.total_samples (${decoded.streamInfo.totalSamples}) no coincide con el frameCount " +
                    "lógico del WAV de origen (${wav.frameCount})."
            )
        }
        // MD5 de STREAMINFO: FlacEncoder lo calcula correctamente sobre el PCM de
        // origen (com.jvk.dwpcreator.domain.flac.FlacEncoder.computeMd5), pero
        // hasta este Pass (Doc/25) nada lo verificaba tras decodificar -- una
        // corrupción localizada exactamente en esos 16 bytes de STREAMINFO
        // pasaba inadvertida, ya que no están protegidos por el CRC-16 de los
        // frames de audio (que solo cubre el frame header/footer) ni por
        // ningún otro chequeo. Recalcular el MD5 del PCM ya decodificado y
        // compararlo contra el declarado cierra ese hueco.
        val recomputedMd5 = FlacEncoder.computeMd5(decoded.pcm)
        if (!recomputedMd5.contentEquals(decoded.streamInfo.md5)) {
            issues.add(
                "El MD5 recalculado del PCM decodificado no coincide con el MD5 declarado en STREAMINFO " +
                    "del FLAC embebido; el bloque 0x0206 está corrupto o fue alterado tras la codificación."
            )
        }
    }

    private fun validateAudioFormatBlock(payload: ByteArray, wav: WavDecoder.DecodedWav, issues: MutableList<String>) {
        val frameCount = DwpTokenizer.readLE32(payload, 0)
        val channelCount = DwpTokenizer.readLE32(payload, 8)
        val bytesPerSample = DwpTokenizer.readLE32(payload, 12)
        val sampleRateBits = DwpTokenizer.readLE32(payload, 16)
        val bits = DwpTokenizer.readLE32(payload, 36)

        if (frameCount != wav.frameCount) issues.add("0x01f7 frameCount=$frameCount no coincide con wav.frameCount=${wav.frameCount}")
        if (channelCount != wav.channelCount) issues.add("0x01f7 channelCount=$channelCount no coincide con wav.channelCount=${wav.channelCount}")
        if (bytesPerSample != wav.bytesPerSample) issues.add("0x01f7 bytesPerSample=$bytesPerSample no coincide con wav.bytesPerSample=${wav.bytesPerSample}")
        if (Float.fromBits(sampleRateBits) != wav.sampleRateHz.toFloat()) {
            issues.add("0x01f7 sampleRate=${Float.fromBits(sampleRateBits)} no coincide con wav.sampleRateHz=${wav.sampleRateHz}")
        }
        if (bits != wav.bytesPerSample * 8) issues.add("0x01f7 bitsPerSample=$bits no coincide con wav.bytesPerSample*8=${wav.bytesPerSample * 8}")
    }
}
