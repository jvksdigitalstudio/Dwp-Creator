package com.jvk.dwpcreator.domain.dwp.monolithic

import com.jvk.dwpcreator.domain.audio.WavDecoder
import com.jvk.dwpcreator.domain.dwp.DwpBlock
import com.jvk.dwpcreator.domain.flac.FlacEncoder
import com.jvk.dwpcreator.domain.flac.FlacPcmAudio

/**
 * Construye el o los [DwpBlock] que transportan el audio embebido de una
 * zona Monolithic: normaliza el WAV de entrada ([PcmNormalizer]), lo
 * codifica a FLAC ([FlacEncoder]) y empaqueta el stream resultante como el
 * payload de un bloque DWP con tag [TAG_MONOLITHIC_AUDIO] (`0x0206`).
 *
 * **Soporte de tags** (Sección 12 del prompt maestro de esta fase):
 * ```
 * SUPPORTED:      0x0206 (payload = stream FLAC completo, sin envoltorio adicional)
 * NOT IMPLEMENTED: 0x0205
 * ```
 * `0x0205` no tiene ninguna evidencia asociada en Doc/00-23 ni en el
 * fixture real disponible (ver Doc/23_MONOLITHIC_DWP_FLAC_IMPLEMENTATION_SPEC.md
 * §10); se documenta aquí como constante para que un futuro implementador
 * lo encuentre, pero **no se escribe ningún writer para él**.
 *
 * La estructura exacta del payload de `0x0206` (¿stream FLAC nativo crudo,
 * tal como aquí, o con algún envoltorio/cabecera adicional específico de
 * DirectWave?) es, en sí misma, 🔴 NO CONFIRMADA (Doc/23 §11) -- esta clase
 * implementa la hipótesis de menor riesgo documentada allí (payload =
 * stream FLAC nativo sin envolver), no un hecho verificado.
 */
object MonolithicDwpAudioBuilder {

    /** `0x0205` -- rama alternativa hipotética, NO implementada en esta fase. Ver KDoc de la clase. */
    const val TAG_MONOLITHIC_AUDIO_UNSUPPORTED_ALT = 0x0205

    /** `0x0206` -- audio embebido FLAC. Única rama que esta implementación soporta. */
    const val TAG_MONOLITHIC_AUDIO = 0x0206

    data class EmbeddedAudio(
        val block: DwpBlock,
        val flacBytes: ByteArray,
        val pcm: FlacPcmAudio
    )
    // Nota: `flacBytes` es un ByteArray sin equals/hashCode estructural
    // (identidad de referencia); esta clase no se compara por equals() en
    // ningún punto actual del código. Si en el futuro se necesita comparar
    // dos instancias, aplicar el mismo patrón que FlacPcmAudio/DwpBlock/
    // FlacMetadataWriter.StreamInfo (contentEquals/contentHashCode).

    /**
     * Construye el bloque `0x0206` para [wav]. Lanza
     * [UnsupportedMonolithicFormatException] si el formato de [wav] no está
     * soportado por [PcmNormalizer] (float32, PCM de 32 bits), o
     * `FlacEncodingException` (com.jvk.dwpcreator.domain.flac) si la
     * codificación FLAC en sí falla (p. ej. audio sin muestras).
     */
    fun build(wav: WavDecoder.DecodedWav, flacBlockSize: Int = FlacEncoder.DEFAULT_BLOCK_SIZE): EmbeddedAudio {
        val pcm = PcmNormalizer.normalize(wav)
        val flacBytes = FlacEncoder.encode(pcm, flacBlockSize)
        val block = DwpBlock(tag = TAG_MONOLITHIC_AUDIO, reserved = 0, payload = flacBytes)
        return EmbeddedAudio(block, flacBytes, pcm)
    }
}
