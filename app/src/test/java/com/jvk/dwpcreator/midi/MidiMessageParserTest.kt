package com.jvk.dwpcreator.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [MidiMessageParser] -- no Android dependency, no
 * Robolectric needed. This is exactly the gap called out by Sección 6/9 del
 * prompt maestro: the MIDI running-status logic previously lived inline in
 * `MidiInputManager`, which requires `android.content.Context` and can't be
 * instantiated in a plain JVM test, so it had zero automated coverage
 * despite being real, non-trivial logic.
 */
class MidiMessageParserTest {

    private fun collectEvents(parser: MidiMessageParser, vararg bytes: Int): List<MidiNoteEvent> {
        val buf = ByteArray(bytes.size) { bytes[it].toByte() }
        val events = mutableListOf<MidiNoteEvent>()
        parser.parse(buf, 0, buf.size) { events += it }
        return events
    }

    @Test
    fun `Note On normal`() {
        val events = collectEvents(MidiMessageParser(), 0x90, 60, 100)
        assertEquals(listOf(MidiNoteEvent(60, 100, true)), events)
    }

    @Test
    fun `Note Off normal`() {
        val events = collectEvents(MidiMessageParser(), 0x80, 60, 64)
        assertEquals(listOf(MidiNoteEvent(60, 64, false)), events)
    }

    @Test
    fun `Note On velocity 0 is reported as note-off, per MIDI convention`() {
        val events = collectEvents(MidiMessageParser(), 0x90, 60, 0)
        assertEquals(listOf(MidiNoteEvent(60, 0, false)), events)
    }

    @Test
    fun `running status Note On -- second message omits the repeated status byte`() {
        // 0x90 60 100  (full message)  then just  61 110  (running status)
        val events = collectEvents(MidiMessageParser(), 0x90, 60, 100, 61, 110)
        assertEquals(
            listOf(MidiNoteEvent(60, 100, true), MidiNoteEvent(61, 110, true)),
            events
        )
    }

    @Test
    fun `running status Note Off -- second message omits the repeated status byte`() {
        val events = collectEvents(MidiMessageParser(), 0x80, 60, 64, 61, 70)
        assertEquals(
            listOf(MidiNoteEvent(60, 64, false), MidiNoteEvent(61, 70, false)),
            events
        )
    }

    @Test
    fun `multiple running-status messages in a single buffer`() {
        val events = collectEvents(
            MidiMessageParser(), 0x90, 40, 100,
            41, 101, 42, 102, 43, 103
        )
        assertEquals(4, events.size)
        assertEquals(MidiNoteEvent(40, 100, true), events[0])
        assertEquals(MidiNoteEvent(43, 103, true), events[3])
    }

    @Test
    fun `running status persists across separate parse calls on the same parser instance`() {
        // A real controller's running-status stream can span multiple
        // onSend deliveries -- the parser instance must remember the status
        // byte between calls, not just within one buffer.
        val parser = MidiMessageParser()
        val eventsA = collectEvents(parser, 0x90, 50, 90)
        val eventsB = mutableListOf<MidiNoteEvent>()
        val bufB = byteArrayOf(51, 91) // running status, no explicit 0x90 this time
        parser.parse(bufB, 0, bufB.size) { eventsB += it }

        assertEquals(listOf(MidiNoteEvent(50, 90, true)), eventsA)
        assertEquals(listOf(MidiNoteEvent(51, 91, true)), eventsB)
    }

    @Test
    fun `reset forgets running status -- must not leak across a device reconnection`() {
        val parser = MidiMessageParser()
        collectEvents(parser, 0x90, 50, 90) // establishes running status = 0x90
        parser.reset()

        val events = mutableListOf<MidiNoteEvent>()
        val buf = byteArrayOf(51, 91) // now a lone data byte with no known status
        parser.parse(buf, 0, buf.size) { events += it }

        assertTrue("Expected no events: running status was reset, so a bare data byte can't be interpreted", events.isEmpty())
    }

    @Test
    fun `note 0 and note 127 -- full range boundaries`() {
        val events = collectEvents(MidiMessageParser(), 0x90, 0, 100, 0x90, 127, 100)
        assertEquals(0, events[0].note)
        assertEquals(127, events[1].note)
    }

    @Test
    fun `velocity 0 and velocity 127 -- full range boundaries`() {
        val events = collectEvents(MidiMessageParser(), 0x90, 60, 0, 0x90, 60, 127)
        assertEquals(0, events[0].velocity)
        assertEquals(false, events[0].isNoteOn) // velocity 0 => note-off
        assertEquals(127, events[1].velocity)
        assertEquals(true, events[1].isNoteOn)
    }

    @Test
    fun `channel 0 and channel 15 -- both decode identically as far as MidiNoteEvent is concerned`() {
        // MidiNoteEvent doesn't currently expose channel (DwpCreator treats
        // every channel the same for preview purposes), but the parser must
        // still correctly recognize Note On/Off across the full 0x80-0xEF
        // channel voice status range, not just channel 0.
        val channel0 = collectEvents(MidiMessageParser(), 0x90, 60, 100) // 0x90 = Note On, channel 0
        val channel15 = collectEvents(MidiMessageParser(), 0x9F, 60, 100) // 0x9F = Note On, channel 15
        assertEquals(listOf(MidiNoteEvent(60, 100, true)), channel0)
        assertEquals(listOf(MidiNoteEvent(60, 100, true)), channel15)
    }

    // --- Sección 3 del prompt maestro Pass 3: fragmentación entre buffers.
    // Los tres casos deben producir EXACTAMENTE el mismo evento.

    @Test
    fun `fragmentation -- one byte per buffer -- 90, then 3C, then 64`() {
        val parser = MidiMessageParser()
        val events = mutableListOf<MidiNoteEvent>()
        parser.parse(byteArrayOf(0x90.toByte()), 0, 1) { events += it }
        assertTrue("No event expected yet -- status byte alone", events.isEmpty())
        parser.parse(byteArrayOf(60), 0, 1) { events += it }
        assertTrue("No event expected yet -- note byte alone, velocity still pending", events.isEmpty())
        parser.parse(byteArrayOf(100), 0, 1) { events += it }
        assertEquals(listOf(MidiNoteEvent(60, 100, true)), events)
    }

    @Test
    fun `fragmentation -- status+note together, then velocity separately -- 90 3C, then 64`() {
        val parser = MidiMessageParser()
        val events = mutableListOf<MidiNoteEvent>()
        parser.parse(byteArrayOf(0x90.toByte(), 60), 0, 2) { events += it }
        assertTrue(events.isEmpty())
        parser.parse(byteArrayOf(100), 0, 1) { events += it }
        assertEquals(listOf(MidiNoteEvent(60, 100, true)), events)
    }

    @Test
    fun `fragmentation -- status alone, then note+velocity together -- 90, then 3C 64`() {
        val parser = MidiMessageParser()
        val events = mutableListOf<MidiNoteEvent>()
        parser.parse(byteArrayOf(0x90.toByte()), 0, 1) { events += it }
        assertTrue(events.isEmpty())
        parser.parse(byteArrayOf(60, 100), 0, 2) { events += it }
        assertEquals(listOf(MidiNoteEvent(60, 100, true)), events)
    }

    @Test
    fun `all three fragmentation strategies produce the identical event as a single unfragmented buffer`() {
        val unfragmented = collectEvents(MidiMessageParser(), 0x90, 60, 100)

        val perByte = mutableListOf<MidiNoteEvent>()
        MidiMessageParser().also { p ->
            p.parse(byteArrayOf(0x90.toByte()), 0, 1) { perByte += it }
            p.parse(byteArrayOf(60), 0, 1) { perByte += it }
            p.parse(byteArrayOf(100), 0, 1) { perByte += it }
        }

        val statusNoteThenVelocity = mutableListOf<MidiNoteEvent>()
        MidiMessageParser().also { p ->
            p.parse(byteArrayOf(0x90.toByte(), 60), 0, 2) { statusNoteThenVelocity += it }
            p.parse(byteArrayOf(100), 0, 1) { statusNoteThenVelocity += it }
        }

        val statusThenNoteVelocity = mutableListOf<MidiNoteEvent>()
        MidiMessageParser().also { p ->
            p.parse(byteArrayOf(0x90.toByte()), 0, 1) { statusThenNoteVelocity += it }
            p.parse(byteArrayOf(60, 100), 0, 2) { statusThenNoteVelocity += it }
        }

        assertEquals(unfragmented, perByte)
        assertEquals(unfragmented, statusNoteThenVelocity)
        assertEquals(unfragmented, statusThenNoteVelocity)
    }

    @Test
    fun `truncation -- 90, then 90 3C -- never throws and keeps the pending state coherent`() {
        // Sección 3: "El parser no debe lanzar excepción ante: 90 / 90 3C.
        // Debe conservar el estado pendiente si corresponde."
        val parser = MidiMessageParser()
        val events = mutableListOf<MidiNoteEvent>()
        parser.parse(byteArrayOf(0x90.toByte()), 0, 1) { events += it } // status only
        parser.parse(byteArrayOf(0x90.toByte(), 60), 0, 2) { events += it } // fresh status + note, velocity still pending
        assertTrue("No event yet -- velocity still hasn't arrived", events.isEmpty())
        // Complete it now, to prove the pending state after the re-sent
        // status byte is coherent (not corrupted by the truncation before it).
        parser.parse(byteArrayOf(100), 0, 1) { events += it }
        assertEquals(listOf(MidiNoteEvent(60, 100, true)), events)
    }

    @Test
    fun `change of status -- a fresh valid status abandons a dangling incomplete message per the implemented rule`() {
        // Sección 3: "Verificar que un nuevo status válido finalice
        // correctamente el estado anterior según las reglas implementadas."
        // Implemented rule: a fresh channel-voice status byte always starts
        // a brand new message, abandoning any previous incomplete one --
        // the dangling "90 3C" (Note On, note=60, velocity still pending)
        // never fires an event; only the new Note Off does.
        val events = collectEvents(
            MidiMessageParser(),
            0x90, 60,       // Note On started, note=60, velocity pending...
            0x80, 64, 80    // ...abandoned by this fresh Note Off, which fires normally
        )
        assertEquals(listOf(MidiNoteEvent(64, 80, false)), events)
    }

    // --- Sección 3: casos explícitos de running status pedidos por el prompt.

    @Test
    fun `running status -- 90 3C 64 3D 70 generates exactly two Note On`() {
        val events = collectEvents(MidiMessageParser(), 0x90, 0x3C, 0x64, 0x3D, 0x70)
        assertEquals(2, events.size)
        assertEquals(MidiNoteEvent(0x3C, 0x64, true), events[0])
        assertEquals(MidiNoteEvent(0x3D, 0x70, true), events[1])
    }

    @Test
    fun `running status after Note Off -- second message under the same running status also decodes correctly`() {
        val events = collectEvents(MidiMessageParser(), 0x80, 0x3C, 0x40, 0x3D, 0x50)
        assertEquals(2, events.size)
        assertEquals(MidiNoteEvent(0x3C, 0x40, false), events[0])
        assertEquals(MidiNoteEvent(0x3D, 0x50, false), events[1])
    }

    @Test
    fun `velocity zero -- 90 3C 00 generates a Note Off`() {
        val events = collectEvents(MidiMessageParser(), 0x90, 0x3C, 0x00)
        assertEquals(listOf(MidiNoteEvent(0x3C, 0, false)), events)
    }

    // --- Comportamiento previo, re-verificado bajo la nueva máquina de
    // estados: un mensaje incompleto al final de un buffer ya NO se
    // descarta -- queda pendiente y se completa si llegan más bytes
    // (comportamiento corregido en Pass 3; ver los tests de fragmentación
    // arriba para la demostración completa).

    @Test
    fun `an incomplete message at the end of a buffer produces no event yet, but is not lost`() {
        val parser = MidiMessageParser()
        val events = mutableListOf<MidiNoteEvent>()
        parser.parse(byteArrayOf(0x90.toByte(), 60), 0, 2) { events += it } // missing velocity
        assertTrue("No event yet -- this is expected, not a bug", events.isEmpty())
        // Prove it wasn't silently dropped: completing it now must still fire.
        parser.parse(byteArrayOf(100), 0, 1) { events += it }
        assertEquals(listOf(MidiNoteEvent(60, 100, true)), events)
    }

    @Test
    fun `an incomplete running-status message at the end of a buffer produces no event yet, but is not lost`() {
        val parser = MidiMessageParser()
        collectEvents(parser, 0x90, 60, 100) // establish running status
        val events = mutableListOf<MidiNoteEvent>()
        parser.parse(byteArrayOf(61), 0, 1) { events += it } // only the note byte, velocity missing
        assertTrue(events.isEmpty())
        parser.parse(byteArrayOf(101), 0, 1) { events += it }
        assertEquals(listOf(MidiNoteEvent(61, 101, true)), events)
    }

    @Test
    fun `a lone data byte with no prior status is safely ignored, not treated as invalid state crash`() {
        // Sección 7: "byte inesperado ... debe producir comportamiento seguro."
        val events = collectEvents(MidiMessageParser(), 60, 100) // no status byte at all
        assertTrue(events.isEmpty())
    }

    @Test
    fun `an unsupported channel voice status (Control Change) is skipped without crashing or misfiring a note`() {
        // 0xB0 = Control Change, channel 0; 2 data bytes (controller, value).
        // Followed by a real Note On to confirm parsing resynchronizes correctly.
        val events = collectEvents(MidiMessageParser(), 0xB0, 7, 100, 0x90, 60, 100)
        assertEquals(listOf(MidiNoteEvent(60, 100, true)), events)
    }

    // --- Sección 8 del prompt maestro: running status solo debe aplicarse a
    // mensajes de canal (0x80-0xEF). Mensajes de sistema NUNCA deben
    // convertirse en running status, ni deben interpretarse como mensajes
    // de canal.

    @Test
    fun `System Realtime bytes (e_g_ MIDI Clock) never corrupt running status`() {
        // A real device sends 0xF8 (MIDI Clock) continuously, often
        // interleaved with note data. It must be skipped as a single
        // transparent byte and must NOT overwrite the current running
        // status the way a naive "any byte >= 0x80 is a status" check would.
        val parser = MidiMessageParser()
        val events = mutableListOf<MidiNoteEvent>()
        // 0x90 60 100  0xF8(clock)  61 101(running status)  0xF8(clock)  62 102(running status)
        val buf = byteArrayOf(
            0x90.toByte(), 60, 100,
            0xF8.toByte(),
            61, 101,
            0xF8.toByte(),
            62, 102
        )
        parser.parse(buf, 0, buf.size) { events += it }
        assertEquals(3, events.size)
        assertEquals(MidiNoteEvent(60, 100, true), events[0])
        assertEquals(MidiNoteEvent(61, 101, true), events[1])
        assertEquals(MidiNoteEvent(62, 102, true), events[2])
    }

    @Test
    fun `System Realtime injected mid-message does not disturb a pending Note On assembly`() {
        // Sección 3: "Los mensajes System Real-Time no deben destruir
        // innecesariamente el estado pendiente de un mensaje Channel Voice."
        // 0x90 60 (note started) then 0xF8 (clock, injected mid-message)
        // then 100 (velocity) -- must still complete as ONE Note On.
        val events = collectEvents(MidiMessageParser(), 0x90, 60, 0xF8, 100)
        assertEquals(listOf(MidiNoteEvent(60, 100, true)), events)
    }

    @Test
    fun `System Common bytes cancel running status, per spec`() {
        val parser = MidiMessageParser()
        val events = mutableListOf<MidiNoteEvent>()
        // 0x90 60 100 establishes running status; 0xF1 (System Common,
        // MTC Quarter Frame) must cancel it; the trailing "61 101" is now
        // just two orphaned data bytes with no valid status, so no event.
        val buf = byteArrayOf(0x90.toByte(), 60, 100, 0xF1.toByte(), 0, 61, 101)
        parser.parse(buf, 0, buf.size) { events += it }
        assertEquals(1, events.size)
        assertEquals(MidiNoteEvent(60, 100, true), events[0])
    }

    @Test
    fun `System Common abandons a pending Note On assembly, not just running status`() {
        // 0x90 60 (note started, velocity pending) then 0xF1 (System Common)
        // must abandon the pending note -- the trailing "100" is now just an
        // orphaned data byte, so NO event should fire.
        val events = collectEvents(MidiMessageParser(), 0x90, 60, 0xF1, 0, 100)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `invalid status recovers on the next genuine status byte`() {
        // A stray/unexpected byte sequence must not permanently wedge the
        // parser -- it should resynchronize as soon as a real status byte
        // reappears.
        val events = collectEvents(MidiMessageParser(), 0xFF, 0x90, 60, 100)
        assertEquals(listOf(MidiNoteEvent(60, 100, true)), events)
    }
}
