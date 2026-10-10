package com.jvk.dwpcreator.domain.dwp

/**
 * Describes the per-version layout facts a [DwpDocument] needs to parse a
 * file correctly: today, just the preamble size. Exists so that a preamble
 * size is never treated as a universal constant baked into the parser --
 * it's an observation about **one** confirmed version (0x26), looked up
 * explicitly, not assumed for every file [DwpDocument.parse] is ever handed.
 *
 * [version] is read from the 4 bytes immediately after the `"DwPr"` magic
 * (`[BINARY]` verified against the real fixture: bytes 4-7, little-endian,
 * decode to exactly `0x26`).
 */
data class DwpVersionProfile(
    val version: Int,
    val preambleSize: Int
) {
    companion object {
        /**
         * The only version profile this project has ever verified against a
         * real file: `Instrument.dwp`, a chromatic 48-sample instrument
         * exported from FL Studio Desktop. `preambleSize = 0x5a` (90) is
         * confirmed for this version specifically, not for DWP in general.
         */
        val V0x26 = DwpVersionProfile(version = 0x26, preambleSize = 0x5a)

        private val KNOWN_PROFILES = listOf(V0x26)

        /**
         * Looks up the profile for [version], or `null` if it isn't one of
         * the versions this project has confirmed. Deliberately does **not**
         * fall back to guessing a preamble size for an unrecognized version
         * (Sección 12 del prompt maestro: "no inventar automáticamente su
         * preámbulo... la política debe ser explícita, preferentemente
         * unsupported/unknown version con error claro").
         */
        fun forVersion(version: Int): DwpVersionProfile? = KNOWN_PROFILES.firstOrNull { it.version == version }
    }
}
