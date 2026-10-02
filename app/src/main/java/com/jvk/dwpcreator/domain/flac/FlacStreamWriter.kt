package com.jvk.dwpcreator.domain.flac

import java.io.ByteArrayOutputStream

/**
 * Ensambla el stream FLAC completo: el marcador `fLaC`, el bloque de
 * metadata STREAMINFO ([FlacMetadataWriter]) y la secuencia de frames de
 * audio ([FlacFrameEncoder]), en ese orden -- exactamente la estructura que
 * la especificación pública de FLAC exige para un stream nativo. No conoce
 * nada de DWP: solo ensambla bytes ya producidos por las capas de más bajo
 * nivel.
 */
object FlacStreamWriter {

    private val MAGIC = byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte())

    fun assemble(streamInfoBlock: ByteArray, frames: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(streamInfoBlock)
        for (frame in frames) out.write(frame)
        return out.toByteArray()
    }
}
