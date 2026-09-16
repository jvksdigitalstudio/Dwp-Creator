package com.jvk.dwpcreator.midi

/**
 * Pure Kotlin/JVM MIDI 1.0 byte-stream decoder for the explicit subset
 * DwpCreator actually uses. No Android dependency (no `android.media.midi`),
 * fully deterministic, and directly unit-testable on the JVM --
 * `MidiInputManager` owns the Android device connection and simply forwards
 * every raw buffer it receives to an instance of this class.
 *
 * **A true incremental state machine** (Sección 3 del prompt maestro Pass
 * 3): a Note On/Off message split across multiple [parse] calls -- e.g.
 * status in one buffer, note in a second, velocity in a third -- is
 * correctly assembled and reported exactly once, from exactly the same
 * bytes a single unfragmented buffer would have produced. All the state
 * needed to resume mid-message lives in this instance between calls; no
 * bytes are ever silently dropped just because a message happened to
 * straddle a buffer boundary.
 *
 * **Supported MIDI subset** (Sección 7/8 del prompt maestro -- documented
 * here explicitly rather than left implicit):
 *  - Channel voice **Note On** (`0x9n`) / **Note Off** (`0x8n`), for any
 *    channel `n` (0-15); note and velocity in the full 0-127 range.
 *  - **Note On with velocity 0** is correctly reported as note-off
 *    (`MidiNoteEvent.isNoteOn = false`), per standard MIDI convention.
 *  - **Running status**, correctly scoped to channel voice messages only
 *    (status bytes `0x80`-`0xEF`) -- a stream may omit a repeated status
 *    byte across consecutive messages of the same type, very common for a
 *    controller sending a continuous stream of notes.
 *  - **System Realtime** bytes (`0xF8`-`0xFF`, e.g. MIDI Clock/Start/Stop)
 *    are recognized and skipped as single, transparent bytes that never
 *    affect running status *nor* a Note On/Off message currently being
 *    assembled -- exactly per spec, since these can legally appear injected
 *    in the middle of any other message without disturbing it.
 *  - **System Common** bytes (`0xF0`-`0xF7`) are recognized and correctly
 *    *cancel* any running status and abandon any Note On/Off message
 *    currently being assembled (per spec, a System Common message
 *    invalidates running status), but their own message bodies (SysEx,
 *    MTC, Song Position, etc) are **not decoded** -- out of scope,
 *    DwpCreator has no use for them today.
 *  - **A fresh channel-voice status byte always starts a new message**,
 *    abandoning any previous incomplete one -- e.g. `90 3C` (Note On,
 *    note started, velocity still pending) followed by a new `80 40 50`
 *    abandons the dangling `90 3C` and processes the Note Off normally.
 *
 * Deliberately **not** a general-purpose MIDI engine: non-Note channel
 * voice messages (CC, Program Change, Pitch Bend, Aftertouch) are
 * recognized only enough to update running status and are otherwise
 * skipped byte-by-byte without tracking their exact data length or
 * surviving fragmentation -- out of scope (Sección 6: "no conviertas esto
 * en un sistema MIDI completo"; Sección 3: "la prioridad es que el parser
 * de Note On/Off sea correcto y robusto").
 */
class MidiMessageParser {

    /**
     * The last channel-voice status byte seen (`0x80`-`0xEF`), or -1 if
     * none yet (or if it was cancelled by a System Common message). This is
     * what a running-status data byte is interpreted against.
     */
    private var runningStatus: Int = -1

    /**
     * The status byte of a Note On/Off message currently being assembled
     * (explicit or via running status), or -1 if none is in progress.
     * Distinct from [runningStatus]: this tracks *mid-message* assembly
     * state specifically, so it can be abandoned independently (e.g. by a
     * fresh status byte or a System Common message) without necessarily
     * discarding [runningStatus] itself in every case.
     */
    private var pendingNoteStatus: Int = -1

    /** How many data bytes of the in-progress Note On/Off have been collected: 0 or 1. */
    private var pendingDataCount: Int = 0

    /** The first data byte (note number) collected so far, valid only when [pendingDataCount] == 1. */
    private var pendingNote: Int = 0

    /** Forgets all state: running status and any message currently being assembled. Call whenever a MIDI connection is (re)established. */
    fun reset() {
        runningStatus = -1
        pendingNoteStatus = -1
        pendingDataCount = 0
    }

    /**
     * Feeds [count] more bytes of [msg] starting at [offset] into the state
     * machine, invoking [onEvent] for each fully-assembled Note On/Off.
     * Safe -- indeed, designed -- to call repeatedly across multiple
     * buffers from the same ongoing connection, in arbitrarily small
     * pieces (a single call with 1 byte at a time works identically to one
     * call with the whole message): all assembly state persists in this
     * instance between calls. Call [reset] first if this represents a
     * new/different connection, so a stale partial message or running
     * status from a previous device doesn't leak in.
     */
    fun parse(msg: ByteArray, offset: Int, count: Int, onEvent: (MidiNoteEvent) -> Unit) {
        val end = offset + count
        for (i in offset until end) {
            val byte0 = msg[i].toInt() and 0xFF

            when {
                byte0 in 0xF8..0xFF -> {
                    // System Realtime: transparent single byte. Must NOT
                    // touch running status, and must NOT disturb a Note
                    // On/Off currently being assembled -- per spec these
                    // can be injected mid-message.
                }

                byte0 in 0xF0..0xF7 -> {
                    // System Common: cancels running status and abandons
                    // any in-progress Note On/Off assembly. Body not decoded.
                    runningStatus = -1
                    pendingNoteStatus = -1
                    pendingDataCount = 0
                }

                (byte0 and 0x80) != 0 -> {
                    // A fresh channel voice status byte (0x80-0xEF, since
                    // 0xF0+ was handled above). Always starts a brand new
                    // message, abandoning any previous incomplete one.
                    runningStatus = byte0
                    val command = byte0 and 0xF0
                    if (command == 0x90 || command == 0x80) {
                        pendingNoteStatus = byte0
                        pendingDataCount = 0
                    } else {
                        // Non-Note channel voice message: running status is
                        // updated (above) but we don't assemble these.
                        pendingNoteStatus = -1
                        pendingDataCount = 0
                    }
                }

                else -> {
                    // A data byte (high bit clear). Which message it
                    // belongs to depends on whether we're already
                    // mid-Note-assembly, or need to start a new one
                    // implicitly via running status.
                    val activeNoteStatus = if (pendingNoteStatus >= 0) {
                        pendingNoteStatus
                    } else if (runningStatus >= 0 && (runningStatus and 0xF0).let { it == 0x90 || it == 0x80 }) {
                        // Starting a new Note On/Off under running status --
                        // this data byte is its first (the note number).
                        pendingNoteStatus = runningStatus
                        pendingDataCount = 0
                        runningStatus
                    } else {
                        -1
                    }

                    if (activeNoteStatus < 0) {
                        // Either no usable status at all, or running status
                        // currently points at a non-Note message type we
                        // don't assemble -- this data byte is simply consumed.
                    } else if (pendingDataCount == 0) {
                        pendingNote = byte0
                        pendingDataCount = 1
                    } else {
                        val command = activeNoteStatus and 0xF0
                        val note = pendingNote and 0x7F
                        val velocity = byte0 and 0x7F
                        val isNoteOn = command == 0x90 && velocity > 0
                        onEvent(MidiNoteEvent(note, velocity, isNoteOn))
                        // Ready for the next pair under the same running
                        // status (Sección 3: "múltiples mensajes bajo
                        // running status" without seeing 0x90/0x80 again).
                        pendingDataCount = 0
                    }
                }
            }
        }
    }
}
