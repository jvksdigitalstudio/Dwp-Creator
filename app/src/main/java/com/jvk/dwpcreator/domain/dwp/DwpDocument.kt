package com.jvk.dwpcreator.domain.dwp

/**
 * A fully parsed DWP file: the version-specific preamble (global instrument
 * settings that never reference names/paths, copied through untouched --
 * 90 bytes for the only confirmed version, 0x26, see [DwpVersionProfile])
 * plus the flat stream of top-level [DwpBlock]s that follows it.
 */
data class DwpDocument(
    val preamble: ByteArray,
    val blocks: List<DwpBlock>
) {
    fun toBytes(): ByteArray = preamble + DwpTokenizer.serialize(blocks)

    companion object {
        private const val MAGIC = "DwPr"
        private const val VERSION_OFFSET = 4
        private const val MIN_HEADER_SIZE = VERSION_OFFSET + 4 // magic (4) + version (4)

        /**
         * Parses [bytes] following the flow in Sección 13 del prompt
         * maestro: read magic -> read version -> look up its
         * [DwpVersionProfile] -> use *that* profile's preamble size (never a
         * hardcoded universal constant) -> validate the file is large enough
         * -> tokenize blocks from the profile-defined offset -> **verify**
         * the block stream accounts for every remaining byte with zero
         * leftover/overshoot. Throws [DwpFormatException] at the first point
         * the file doesn't match what's actually been confirmed, rather than
         * silently producing a corrupt result or guessing at an unknown
         * version's layout.
         */
        fun parse(bytes: ByteArray): DwpDocument {
            if (bytes.size < MIN_HEADER_SIZE) {
                throw DwpFormatException("Archivo demasiado pequeño para contener siquiera firma y versión (${bytes.size} bytes).")
            }
            val magic = String(bytes.copyOfRange(0, 4), Charsets.US_ASCII)
            if (magic != MAGIC) {
                throw DwpFormatException("Falta la firma '$MAGIC' al inicio del archivo (encontrado: '$magic').")
            }

            val version = DwpTokenizer.readLE32(bytes, VERSION_OFFSET)
            val profile = DwpVersionProfile.forVersion(version)
                ?: throw DwpFormatException(
                    "Versión de .dwp no reconocida: 0x${version.toString(16)}. La única versión verificada " +
                        "hasta ahora es 0x26. No se va a inventar un layout de preámbulo para una versión " +
                        "sin evidencia real; se rechaza el archivo explícitamente."
                )

            if (bytes.size < profile.preambleSize + 12) {
                throw DwpFormatException(
                    "Archivo truncado antes de completar el preámbulo de la versión 0x${version.toString(16)} " +
                        "(se requieren ${profile.preambleSize} bytes de preámbulo + 12 de header de bloque; el archivo mide ${bytes.size})."
                )
            }

            val preamble = bytes.copyOfRange(0, profile.preambleSize)
            val result = DwpTokenizer.tokenize(bytes, profile.preambleSize)

            if (result.stopReason != DwpTokenizer.StopReason.CLEAN_EOF || result.endOffset != bytes.size) {
                val detail = when (result.stopReason) {
                    DwpTokenizer.StopReason.TRAILING_BYTES -> "quedaron ${bytes.size - result.endOffset} byte(s) sueltos al final, insuficientes para otro header de bloque"
                    DwpTokenizer.StopReason.INVALID_LENGTH -> "un bloque en offset ${result.endOffset} declara una longitud negativa/inválida"
                    DwpTokenizer.StopReason.PAYLOAD_TRUNCATED -> "un bloque en offset ${result.endOffset} declara un payload que excede el tamaño real del archivo"
                    DwpTokenizer.StopReason.MAX_BLOCKS_REACHED -> "se alcanzó el límite de seguridad de bloques antes de terminar de leer el archivo"
                    DwpTokenizer.StopReason.CLEAN_EOF -> "cursor final no coincide con el tamaño de archivo pese a reportar EOF limpio"
                }
                throw DwpFormatException(
                    "El stream de bloques no cuadra exacto con el fin de archivo " +
                        "(cursor final=${result.endOffset}, tamaño real=${bytes.size}): $detail. " +
                        "Este .dwp no coincide con el formato verificado; no se va a editar a ciegas."
                )
            }

            return DwpDocument(preamble, result.blocks)
        }
    }
}

class DwpFormatException(message: String) : Exception(message)
