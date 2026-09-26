package com.jvk.dwpcreator.domain.dwp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cobertura de [InstrumentNameValidator]. El caso más importante es el de
 * los caracteres no ASCII: aceptar un nombre como "Acústico" corrompe los
 * nombres de todas las muestras (ver el KDoc de la clase), y el último test
 * lo demuestra contra el motor real en vez de darlo por sentado.
 */
class InstrumentNameValidatorTest {

    @Test
    fun `accepts ordinary names`() {
        assertNull(InstrumentNameValidator.validate("Piano"))
        assertNull(InstrumentNameValidator.validate("Didas 2"))
        assertNull(InstrumentNameValidator.validate("Strings_Soft-v1.2"))
        assertNull(InstrumentNameValidator.validate("Lead (wide) #3"))
    }

    @Test
    fun `rejects blank and whitespace-only names`() {
        assertNotNull(InstrumentNameValidator.validate(""))
        assertNotNull(InstrumentNameValidator.validate("   "))
    }

    @Test
    fun `rejects leading or trailing spaces`() {
        assertNotNull(InstrumentNameValidator.validate(" Piano"))
        assertNotNull(InstrumentNameValidator.validate("Piano "))
    }

    @Test
    fun `rejects a trailing dot and the reserved dot segments`() {
        assertNotNull(InstrumentNameValidator.validate("Piano."))
        assertNotNull(InstrumentNameValidator.validate("."))
        assertNotNull(InstrumentNameValidator.validate(".."))
    }

    @Test
    fun `rejects non-ASCII characters`() {
        assertNotNull(InstrumentNameValidator.validate("Acústico"))
        assertNotNull(InstrumentNameValidator.validate("Piñata"))
        assertNotNull(InstrumentNameValidator.validate("Piano 🎹"))
        assertNotNull(InstrumentNameValidator.validate("ピアノ"))
    }

    @Test
    fun `rejects control characters`() {
        assertNotNull(InstrumentNameValidator.validate("Pia\tno"))
        assertNotNull(InstrumentNameValidator.validate("Pia\nno"))
    }

    @Test
    fun `rejects every filename-forbidden character`() {
        for (c in listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')) {
            assertNotNull("'$c' debería rechazarse", InstrumentNameValidator.validate("Pi${c}ano"))
        }
    }

    @Test
    fun `the message names the offending character`() {
        val message = InstrumentNameValidator.validate("Acústico")
        assertNotNull(message)
        assertTrue(message!!.contains("'ú'"))
    }

    @Test
    fun `renaming the real fixture to a non-ASCII name is exactly the corruption the validator prevents`() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("Instrument.dwp")!!.readBytes()
        val doc = DwpDocument.parse(bytes)

        // Un nombre válido: el motor sigue leyendo las 48 muestras con su nombre real.
        val ok = DwpEngine.renameInstrument(doc, "Instrument", "Didas")
        assertEquals("Didas_C3_127", DwpEngine.listSamples(ok)[0].name)
        assertEquals("Didas", DwpEngine.detectInstrumentBaseName(ok))

        // Un nombre no ASCII: los nombres dejan de ser legibles -> por eso se rechaza antes.
        assertNotNull(InstrumentNameValidator.validate("Acústico"))
        val bad = DwpEngine.renameInstrument(doc, "Instrument", "Acústico")
        assertNull(DwpEngine.detectInstrumentBaseName(bad))
        assertEquals("sample_0", DwpEngine.listSamples(bad)[0].name)
    }
}
