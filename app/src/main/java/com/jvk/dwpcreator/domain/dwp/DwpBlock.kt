package com.jvk.dwpcreator.domain.dwp

/**
 * A single DWP block, as verified by binary audit of a real FL Studio Desktop
 * DirectWave export (48-sample chromatic instrument, 47,307 bytes).
 *
 * Every DWP file, after a small fixed 90-byte preamble, is a **flat, gapless
 * sequence** of blocks with this exact shape — confirmed by tokenizing the
 * entire file and landing the cursor exactly on EOF with zero overshoot:
 *
 * ```
 * [tag: 4 bytes LE][length: 4 bytes LE][reserved: 4 bytes, always 0][payload: `length` bytes]
 * ```
 *
 * Some payloads are themselves a nested stream of blocks in this same shape
 * (e.g. tag 0x0003, the per-sample container — see [DwpTokenizer.tokenize]
 * applied recursively). There are **no absolute byte offsets anywhere** in
 * the format; everything is positional/sequential. That single fact is what
 * makes safe, surgical editing possible: change one block's payload and
 * length, re-emit the stream, and every other block downstream lands exactly
 * where it should — no pointer table to repair.
 *
 * Known top-level tags (from the audit of Instrument.dwp):
 * - `0x0066` instrument name (string)
 * - `0x0067` the .dwp's own path (string, escaped double-backslash)
 * - `0x0068`-`0x006d` optional metadata fields (author/category/notes), empty by default
 * - `0x006e` × 99, fixed parameter/knob table, independent of sample count
 * - `0x0003` × N, one per sample — a nested container, see [SampleBlock]
 * - `0x0002` top-level terminator
 *
 * Known nested tags inside a `0x0003` sample container:
 * - `0x01f4` (25 bytes) key range: lowKey, rootKey, highKey + velocity/flags
 * - `0x01f5` sample name (string)
 * - `0x01f6` full sample path (string, single backslash)
 * - `0x01f7` (40 bytes) audio format blob; **first 4 bytes = exact frame count**
 *            (verified against the real .wav `data` chunk size / bytes-per-frame)
 * - `0x01f8`-`0x0204` envelope/loop/tuning parameters, zero by default
 * - `0x0004` block terminator (length 0)
 */
data class DwpBlock(
    val tag: Int,
    val reserved: Int,
    val payload: ByteArray
) {
    val length: Int get() = payload.size

    /** True if [payload] decodes cleanly as printable ASCII/Latin-1 text (a name or path field). */
    fun isTextPayload(): Boolean {
        if (payload.isEmpty()) return false
        return payload.all { b -> val v = b.toInt() and 0xFF; v in 0x20..0x7E }
    }

    fun textOrNull(): String? = if (isTextPayload()) String(payload, Charsets.ISO_8859_1) else null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DwpBlock) return false
        return tag == other.tag && reserved == other.reserved && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = tag
        result = 31 * result + reserved
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        const val TAG_INSTRUMENT_NAME = 0x0066
        const val TAG_PROJECT_PATH = 0x0067
        const val TAG_PARAMETER_SLOT = 0x006e
        const val TAG_SAMPLE_CONTAINER = 0x0003
        const val TAG_TOP_LEVEL_END = 0x0002

        // Nested tags inside a TAG_SAMPLE_CONTAINER payload
        const val TAG_KEY_RANGE = 0x01f4
        const val TAG_SAMPLE_NAME = 0x01f5
        const val TAG_SAMPLE_PATH = 0x01f6
        const val TAG_AUDIO_FORMAT = 0x01f7
        const val TAG_SAMPLE_END = 0x0004
    }
}
