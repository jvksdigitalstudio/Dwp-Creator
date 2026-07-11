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

    /** Result of tokenizing a byte range: the parsed blocks plus where parsing stopped. */
    data class TokenizeResult(val blocks: List<DwpBlock>, val endOffset: Int)

    /**
     * Tokenizes [buf] starting at [startOffset] until it can no longer read a
     * full 12-byte block header, or until [maxBlocks] is reached as a safety
     * bound. Does not throw on a malformed tail; it simply stops and reports
     * how far it got via [TokenizeResult.endOffset] so callers can verify a
     * clean, exact parse (endOffset == buf.size) before trusting the result.
     */
    fun tokenize(buf: ByteArray, startOffset: Int = 0, maxBlocks: Int = 100_000): TokenizeResult {
        val blocks = ArrayList<DwpBlock>()
        var cursor = startOffset
        val n = buf.size

        while (cursor + 12 <= n && blocks.size < maxBlocks) {
            val tag = readLE32(buf, cursor)
            val length = readLE32(buf, cursor + 4)
            val reserved = readLE32(buf, cursor + 8)
            val payloadStart = cursor + 12

            if (length < 0 || payloadStart + length > n) {
                // Cannot safely read this block's payload; stop here rather
                // than risk misinterpreting binary data as a valid block.
                break
            }

            val payload = buf.copyOfRange(payloadStart, payloadStart + length)
            blocks.add(DwpBlock(tag, reserved, payload))
            cursor = payloadStart + length
        }

        return TokenizeResult(blocks, cursor)
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
