package com.jvk.dwpcreator.domain.dwp

/**
 * A fully parsed DWP file: the fixed 90-byte preamble (global instrument
 * settings that never reference names/paths, copied through untouched) plus
 * the flat stream of top-level [DwpBlock]s that follows it.
 */
data class DwpDocument(
    val preamble: ByteArray,
    val blocks: List<DwpBlock>
) {
    fun toBytes(): ByteArray = preamble + DwpTokenizer.serialize(blocks)

    companion object {
        /**
         * Verified constant: the first 90 bytes of a DirectWave .dwp exported
         * from FL Studio Desktop are fixed global settings, independent of
         * instrument name or sample count. The very first block (tag
         * [DwpBlock.TAG_INSTRUMENT_NAME]) starts immediately after.
         */
        const val PREAMBLE_SIZE = 0x5a

        /**
         * Parses [bytes] and **verifies** the block stream accounts for
         * every remaining byte with zero leftover/overshoot — the same
         * check used during the original audit. Throws [DwpFormatException]
         * if the file doesn't match the verified format, rather than
         * silently producing a corrupt result.
         */
        fun parse(bytes: ByteArray): DwpDocument {
            if (bytes.size < PREAMBLE_SIZE + 12) {
                throw DwpFormatException("Archivo demasiado pequeño para ser un .dwp válido (${bytes.size} bytes).")
            }
            val magic = String(bytes.copyOfRange(0, 4), Charsets.US_ASCII)
            if (magic != "DwPr") {
                throw DwpFormatException("Falta la firma 'DwPr' al inicio del archivo (encontrado: '$magic').")
            }

            val preamble = bytes.copyOfRange(0, PREAMBLE_SIZE)
            val result = DwpTokenizer.tokenize(bytes, PREAMBLE_SIZE)

            if (result.endOffset != bytes.size) {
                throw DwpFormatException(
                    "El stream de bloques no cuadra exacto con el fin de archivo " +
                        "(cursor final=${result.endOffset}, tamaño real=${bytes.size}). " +
                        "Este .dwp no coincide con el formato verificado; no se va a editar a ciegas."
                )
            }

            return DwpDocument(preamble, result.blocks)
        }
    }
}

class DwpFormatException(message: String) : Exception(message)
