package com.jvk.dwpcreator.domain.dwp

import java.io.ByteArrayOutputStream

/**
 * Generic reader/writer for the DWP block stream described in [DwpBlock].
 *
 * This is intentionally "dumb": it does not know or care what any tag means.
 * It only knows the envelope shape (tag/length/reserved/payload) and walks
 * it mechanically. That is exactly what makes it safe — the same function
 * parses the top-level stream *and* the nested payload of every sample
 * container, because both follow the identical shape (verified in the audit).
 */
object DwpTokenizer {

    /**
     * Why [tokenize] stopped reading. Distinguishing these (Sección 6/14 del
     * prompt maestro) matters because a caller needs to know whether it
     * reached a normal, expected end or ran into corruption:
     * - [CLEAN_EOF]: consumed every byte exactly; nothing left to read.
     * - [TRAILING_BYTES]: stopped with fewer than 12 bytes left over --
     *   too little for even one more block header, but not zero either.
     * - [INVALID_LENGTH]: a block declared a negative length after being
     *   read as a signed 32-bit int (i.e. the raw LE32 value had its high
     *   bit set -- either genuine corruption or a size field DWP does not
     *   actually use as a plain length).
     * - [PAYLOAD_TRUNCATED]: a block's declared length is non-negative but
     *   its payload would extend past the end of the buffer.
     * - [MAX_BLOCKS_REACHED]: the [tokenize] call's safety cap on block
     *   count was hit before running out of bytes -- almost always a sign
     *   of misinterpreted/corrupt data causing runaway small "blocks", not
     *   a legitimately huge file.
     */
    enum class StopReason { CLEAN_EOF, TRAILING_BYTES, INVALID_LENGTH, PAYLOAD_TRUNCATED, MAX_BLOCKS_REACHED }

    /** Result of tokenizing a byte range: the parsed blocks, where parsing stopped, and why. */
    data class TokenizeResult(val blocks: List<DwpBlock>, val endOffset: Int, val stopReason: StopReason)

    /**
     * Tokenizes [buf] starting at [startOffset] until it can no longer read a
     * full 12-byte block header, until a block's length is invalid/would
     * overflow the buffer, or until [maxBlocks] is reached as a safety
     * bound. Does not throw on a malformed tail; it simply stops and reports
     * how far it got *and why* via [TokenizeResult], so callers can verify a
     * clean, exact parse (`stopReason == CLEAN_EOF && endOffset == buf.size`)
     * before trusting the result.
     */
    fun tokenize(buf: ByteArray, startOffset: Int = 0, maxBlocks: Int = 100_000): TokenizeResult {
        val blocks = ArrayList<DwpBlock>()
        var cursor = startOffset
        val n = buf.size

        while (true) {
            if (blocks.size >= maxBlocks) {
                return TokenizeResult(blocks, cursor, StopReason.MAX_BLOCKS_REACHED)
            }
            if (cursor + 12 > n) {
                val reason = if (cursor == n) StopReason.CLEAN_EOF else StopReason.TRAILING_BYTES
                return TokenizeResult(blocks, cursor, reason)
            }

            val tag = readLE32(buf, cursor)
            val length = readLE32(buf, cursor + 4)
            val reserved = readLE32(buf, cursor + 8)
            val payloadStart = cursor + 12

            if (length < 0) {
                return TokenizeResult(blocks, cursor, StopReason.INVALID_LENGTH)
            }
            // Long arithmetic here is deliberate: payloadStart + length as Int
            // could overflow and wrap to a small/negative value for a
            // maliciously or accidentally huge `length`, which would make an
            // out-of-bounds payload pass an `Int`-only bounds check.
            if (payloadStart.toLong() + length.toLong() > n.toLong()) {
                return TokenizeResult(blocks, cursor, StopReason.PAYLOAD_TRUNCATED)
            }

            val payload = buf.copyOfRange(payloadStart, payloadStart + length)
            blocks.add(DwpBlock(tag, reserved, payload))
            cursor = payloadStart + length
        }
    }

    /**
     * Like [tokenize], but **verifies** the block stream accounts for every
     * byte of [buf] from [startOffset] to the end, with zero leftover and
     * zero overshoot, before returning. Throws [DwpFormatException] instead
     * of silently returning a partial result.
     *
     * [tokenize] alone only reports where and why it stopped via
     * [TokenizeResult]; it's the caller's job to check that against the
     * expected size. [DwpDocument.parse] already does this for the
     * top-level stream. This helper applies the same check to a nested
     * payload (e.g. the body of a `0x0003` sample container), which is
     * exactly as important: if a nested stream doesn't consume its payload
     * exactly, re-serializing it silently drops the unconsumed tail bytes.
     *
     * [context] is a short human-readable label (e.g. "sample container #3")
     * used only to make the resulting exception message actionable.
     */
    fun tokenizeStrict(buf: ByteArray, startOffset: Int = 0, context: String = "bloque"): List<DwpBlock> {
        val result = tokenize(buf, startOffset)
        if (result.stopReason == StopReason.CLEAN_EOF && result.endOffset == buf.size) {
            return result.blocks
        }
        val detail = when (result.stopReason) {
            StopReason.TRAILING_BYTES -> "quedaron ${buf.size - result.endOffset} byte(s) sueltos, insuficientes para otro header de bloque (12 bytes)"
            StopReason.INVALID_LENGTH -> "un bloque en offset ${result.endOffset} declara una longitud negativa/inválida tras leerla como entero de 32 bits"
            StopReason.PAYLOAD_TRUNCATED -> "un bloque en offset ${result.endOffset} declara un payload que excede el tamaño real del buffer"
            StopReason.MAX_BLOCKS_REACHED -> "se alcanzó el límite de seguridad de bloques antes de terminar de leer el buffer"
            StopReason.CLEAN_EOF -> "cursor final ${result.endOffset} no coincide con el tamaño del payload ${buf.size} pese a reportar EOF limpio (inconsistencia interna)"
        }
        throw DwpFormatException(
            "El stream de bloques de '$context' no cuadra exacto con el fin de su payload " +
                "(cursor final=${result.endOffset}, tamaño de payload=${buf.size}): $detail. " +
                "No se va a reserializar a ciegas: se perderían ${buf.size - result.endOffset} byte(s)."
        )
    }

    /** Serializes [blocks] back into the exact tag/length/reserved/payload byte shape. */
    fun serialize(blocks: List<DwpBlock>): ByteArray {
        val out = ByteArrayOutputStream()
        for (block in blocks) {
            writeLE32(out, block.tag)
            writeLE32(out, block.length)
            writeLE32(out, block.reserved)
            out.write(block.payload)
        }
        return out.toByteArray()
    }

    fun readLE32(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF) or
            ((buf[offset + 1].toInt() and 0xFF) shl 8) or
            ((buf[offset + 2].toInt() and 0xFF) shl 16) or
            ((buf[offset + 3].toInt() and 0xFF) shl 24)
    }

    fun writeLE32(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    /** In-place LE32 write into an existing array (used to patch a single field, e.g. frame count). */
    fun writeLE32(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        data[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        data[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}
