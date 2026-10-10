package com.jvk.dwpcreator.domain.dwp.monolithic

import com.jvk.dwpcreator.domain.audio.WavDecoder
import com.jvk.dwpcreator.domain.dwp.DwpBlock
import com.jvk.dwpcreator.domain.dwp.DwpTokenizer

/**
 * Construye la lista de bloques hijos resultante de un contenedor de sample
 * (`0x0003`) con el audio embebido insertado o reemplazado, preservando
 * todo lo demás **exactamente** -- Sección 10/28 del prompt maestro
 * ("preservación binaria"; "no reinterpretar, no limpiar, no rellenar
 * arbitrariamente, no eliminar" ningún bloque cuya semántica no esté
 * confirmada).
 *
 * Decisiones de esta clase, cada una explícitamente clasificada
 * (Doc/23_MONOLITHIC_DWP_FLAC_IMPLEMENTATION_SPEC.md es la referencia de
 * evidencia para cada una):
 * - El bloque `0x0206` nuevo se inserta **inmediatamente antes** del
 *   terminador `0x0004` (Doc/23 §27: 🟡 hipótesis de diseño, no evidencia
 *   confirmada -- el modelo físico no tiene offsets absolutos ni punteros
 *   que reparar, así que la posición no debería ser estructuralmente
 *   significativa, pero eso en sí es una inferencia sobre el modelo
 *   genérico, no una confirmación específica para `0x0206`).
 * - `0x01f7` (formato de audio), si está presente, se **conserva y se
 *   actualiza** con los nuevos frameCount/canales/bytesPerSample/
 *   sampleRate/bits -- Doc/23 §13: 🟡 hipótesis de diseño de menor riesgo
 *   (coexistencia), no un hecho confirmado. Si está ausente, no se inventa.
 * - Cualquier bloque `0x0206`/`0x0205` preexistente se retira antes de
 *   insertar el nuevo (semántica de "reemplazo"); más de una instancia
 *   preexistente se trata como estructura ambigua y se rechaza, mismo
 *   criterio que ya usa `DwpEngine` para `0x01f7` duplicados.
 */
object MonolithicDwpStructureBuilder {

    /** Único tamaño de payload confirmado para 0x01f7 (Doc/05, ya usado por DwpEngine). */
    private const val AUDIO_FORMAT_PAYLOAD_SIZE = 40

    /**
     * Aplica el reemplazo/inserción de audio embebido sobre [nested] (los
     * bloques ya tokenizados del contenedor de sample objetivo). No toca
     * `DwpEngine` ni ningún otro archivo del Core.
     */
    fun applyEmbeddedAudio(
        nested: List<DwpBlock>,
        embeddedAudioBlock: DwpBlock,
        wav: WavDecoder.DecodedWav
    ): List<DwpBlock> {
        require(embeddedAudioBlock.tag == MonolithicDwpAudioBuilder.TAG_MONOLITHIC_AUDIO) {
            "embeddedAudioBlock debe tener tag 0x0206, tiene 0x${embeddedAudioBlock.tag.toString(16)}"
        }

        val terminatorCount = nested.count { it.tag == DwpBlock.TAG_SAMPLE_END }
        if (terminatorCount != 1) {
            throw MonolithicZoneStructureException(
                "El contenedor de sample tiene $terminatorCount bloque(s) 0x0004 (terminador); se requiere " +
                    "exactamente 1 para insertar audio embebido de forma determinista y segura."
            )
        }

        val existingEmbedded = nested.filter {
            it.tag == MonolithicDwpAudioBuilder.TAG_MONOLITHIC_AUDIO ||
                it.tag == MonolithicDwpAudioBuilder.TAG_MONOLITHIC_AUDIO_UNSUPPORTED_ALT
        }
        if (existingEmbedded.size > 1) {
            throw MonolithicZoneStructureException(
                "El contenedor de sample ya tiene ${existingEmbedded.size} bloques de audio embebido " +
                    "(0x0205/0x0206); es ambiguo cuál reemplazar, se rechaza la operación."
            )
        }

        val withoutOldEmbedded = nested.filter {
            it.tag != MonolithicDwpAudioBuilder.TAG_MONOLITHIC_AUDIO &&
                it.tag != MonolithicDwpAudioBuilder.TAG_MONOLITHIC_AUDIO_UNSUPPORTED_ALT
        }

        val audioFormatMatches = withoutOldEmbedded.filter { it.tag == DwpBlock.TAG_AUDIO_FORMAT }
        if (audioFormatMatches.size > 1) {
            throw MonolithicZoneStructureException(
                "El contenedor de sample tiene ${audioFormatMatches.size} bloques 0x01f7; es ambiguo, se rechaza."
            )
        }

        val result = ArrayList<DwpBlock>(withoutOldEmbedded.size + 1)
        for (block in withoutOldEmbedded) {
            when {
                block.tag == DwpBlock.TAG_AUDIO_FORMAT -> result.add(patchAudioFormatBlock(block, wav))
                block.tag == DwpBlock.TAG_SAMPLE_END -> {
                    // Inserción determinista: el bloque de audio embebido va
                    // inmediatamente antes del terminador (Doc/23 §27).
                    result.add(embeddedAudioBlock)
                    result.add(block)
                }
                else -> result.add(block)
            }
        }
        return result
    }

    /**
     * Igual que [applyEmbeddedAudio], pero opera directamente sobre el
     * payload serializado de un `0x0003` (tokenizándolo de forma estricta,
     * igual que hace el Core en `DwpEngine`, y re-serializando el
     * resultado). Punto de entrada usado por [MonolithicDwpBuilder].
     */
    fun applyToSampleContainer(container: DwpBlock, wav: WavDecoder.DecodedWav, embeddedAudioBlock: DwpBlock): DwpBlock {
        require(container.tag == DwpBlock.TAG_SAMPLE_CONTAINER) {
            "container debe tener tag 0x0003, tiene 0x${container.tag.toString(16)}"
        }
        val nested = DwpTokenizer.tokenizeStrict(container.payload, context = "contenedor de sample (Monolithic)")
        val patchedNested = applyEmbeddedAudio(nested, embeddedAudioBlock, wav)
        return DwpBlock(container.tag, container.reserved, DwpTokenizer.serialize(patchedNested))
    }

    /**
     * Actualiza los 5 campos de 0x01f7 ya confirmados (Doc/05, mismos
     * offsets que usa `DwpEngine.replaceSampleAudio`): frameCount(0),
     * canales(8), bytesPerSample(12), sampleRate float32(16), bits(36).
     * Deliberadamente independiente de `DwpEngine` -- Sección 8 del prompt
     * maestro: "no convertir el Core en un monolito"; este es un parche de
     * 5 campos, no una razón para acoplar este subsistema al de rename.
     */
    private fun patchAudioFormatBlock(block: DwpBlock, wav: WavDecoder.DecodedWav): DwpBlock {
        if (block.payload.size != AUDIO_FORMAT_PAYLOAD_SIZE) {
            throw MonolithicZoneStructureException(
                "0x01f7 tiene ${block.payload.size} bytes, se esperaban exactamente $AUDIO_FORMAT_PAYLOAD_SIZE; " +
                    "no coincide con el formato verificado, no se toca a ciegas."
            )
        }
        val p = block.payload.copyOf()
        DwpTokenizer.writeLE32(p, 0, wav.frameCount)
        DwpTokenizer.writeLE32(p, 8, wav.channelCount)
        DwpTokenizer.writeLE32(p, 12, wav.bytesPerSample)
        DwpTokenizer.writeLE32(p, 16, wav.sampleRateHz.toFloat().toRawBits())
        DwpTokenizer.writeLE32(p, 36, wav.bytesPerSample * 8)
        return DwpBlock(block.tag, block.reserved, p)
    }
}
