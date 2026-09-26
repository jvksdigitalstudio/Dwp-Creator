package com.jvk.dwpcreator.domain.dwp.monolithic

import com.jvk.dwpcreator.domain.audio.WavDecoder
import com.jvk.dwpcreator.domain.flac.FlacPcmAudio

/**
 * Convierte un [WavDecoder.DecodedWav] (PCM crudo tal como llega del .wav)
 * en un [FlacPcmAudio] (canales deinterleaved, enteros con signo) listo
 * para [com.jvk.dwpcreator.domain.flac.FlacEncoder]. Es el único componente
 * que conoce ambos mundos (WAV y FLAC) -- ni [WavDecoder] ni
 * `FlacEncoder` se modifican ni se acoplan entre sí (Sección 7/10 del
 * prompt maestro de esta fase).
 *
 * Deliberadamente **sin pérdida** para los formatos que soporta: extrae
 * cada muestra en su ancho de bits original, sin recortar precisión --
 * a diferencia de [com.jvk.dwpcreator.domain.audio.PcmConverter], cuyo
 * propósito es justamente el opuesto (reducir a 16-bit para reproducción
 * en [android.media.AudioTrack]) y que por eso no se reutiliza aquí.
 *
 * PCM_32 (entero de 32 bits) y FLOAT_32 se rechazan explícitamente en esta
 * fase -- ver [decode] -- porque Doc/23_MONOLITHIC_DWP_FLAC_IMPLEMENTATION_SPEC.md
 * §15 marca 🔴 NO CONFIRMADO qué profundidades de bits acepta DirectWave
 * embebidas, y mapear cualquiera de los dos a un ancho de bits FLAC
 * requeriría elegir arbitrariamente una conversión (¿24? ¿32? ¿reinterpretar
 * los mismos bits?) sin ninguna evidencia que la respalde. Inventar esa
 * decisión violaría la Sección 3 del prompt maestro de esa fase.
 *
 * FASE 2.1 (Doc/28, auditoría forense): la afirmación "el único fixture real
 * disponible (Instrument.dwp) es 32-bit float", presente en versiones
 * anteriores de este comentario, se corrige aquí porque sobrestimaba su
 * propia certeza. La evidencia real, en capas, es:
 * - El propio `0x01F7` del fixture (`app/src/test/resources/Instrument.dwp`,
 *   48 muestras) declara de forma uniforme 2 canales, 4 bytes/muestra,
 *   32 bits/muestra, 44100 Hz -- **pero ese campo por sí solo no distingue
 *   PCM entero de 32 bits de IEEE float de 32 bits**: ambos ocupan 4 bytes.
 * - [WavDecoder]'s propio comentario de cabecera SÍ afirma, como hecho
 *   histórico, que el archivo `.wav` real (una exportación real de FL
 *   Studio Desktop/DirectWave) examinado durante la auditoría binaria
 *   original era IEEE float de 32 bits -- una observación más fuerte que
 *   inferir desde `0x01F7`, pero hecha en una fase anterior de este mismo
 *   proyecto con acceso a un archivo que **ya no existe en este
 *   repositorio** (no re-verificable en esta sesión ni en futuras).
 * - Ningún test de este proyecto, incluido este mismo subsistema
 *   Monolithic, ejercita hoy el pipeline con un `.wav` real que coincida
 *   con el formato que el propio `0x01F7` de `Instrument.dwp` declara.
 *
 * Conclusión de Doc/28: 🟡 **PARTIALLY CONFIRMED**, no 🟢 CONFIRMED -- la
 * hipótesis float32 tiene respaldo histórico real, pero no es
 * re-verificable con los archivos disponibles hoy. El comportamiento de
 * esta clase (rechazar explícitamente, sin inventar una conversión) sigue
 * siendo la decisión correcta precisamente por esta incertidumbre, no a
 * pesar de ella.
 */
object PcmNormalizer {

    /**
     * Normaliza [wav] a [FlacPcmAudio]. Lanza [UnsupportedMonolithicFormatException]
     * para formatos cuya conversión sin inventar una decisión de diseño no
     * es posible en esta fase (ver KDoc de la clase).
     */
    fun normalize(wav: WavDecoder.DecodedWav): FlacPcmAudio {
        val bitsPerSample = when (wav.format) {
            WavDecoder.SampleFormat.PCM_8 -> 8
            WavDecoder.SampleFormat.PCM_16 -> 16
            WavDecoder.SampleFormat.PCM_24 -> 24
            WavDecoder.SampleFormat.PCM_32 -> throw UnsupportedMonolithicFormatException(
                "PCM entero de 32 bits: no soportado en esta fase. " +
                    "Doc/23 §15 marca 🔴 NO CONFIRMADO el conjunto de profundidades de bits que DirectWave " +
                    "acepta embebidas en un Monolithic DWP; extenderlo requiere evidencia real (un fixture " +
                    "monolítico de referencia), no una decisión inventada aquí."
            )
            WavDecoder.SampleFormat.FLOAT_32 -> throw UnsupportedMonolithicFormatException(
                "PCM float de 32 bits: no soportado en esta fase. FLAC es un códec de enteros; mapear " +
                    "float32 a un ancho de bits entero (24? 32?) requeriría elegir arbitrariamente esa " +
                    "profundidad sin evidencia (Doc/23 §15, 🔴 NO CONFIRMADO), lo cual está prohibido por " +
                    "la Sección 3 del prompt maestro de esa fase ('no inventes un formato'). El fixture real " +
                    "disponible (Instrument.dwp) es compatible con este formato -- 32 bits/4 bytes por " +
                    "muestra, según su propio 0x01F7 -- pero esa metadata no distingue por sí sola PCM " +
                    "entero de IEEE float (Doc/28, auditoría forense de FASE 2.1)."
            )
        }

        val frameCount = wav.frameCount
        val channelCount = wav.channelCount
        val bytesPerSample = wav.bytesPerSample
        val channels = Array(channelCount) { IntArray(frameCount) }

        for (frame in 0 until frameCount) {
            val frameBase = frame * wav.bytesPerFrame
            for (ch in 0 until channelCount) {
                val sampleBase = frameBase + ch * bytesPerSample
                channels[ch][frame] = readSample(wav.pcmData, sampleBase, wav.format)
            }
        }
        return FlacPcmAudio(channels, wav.sampleRateHz, bitsPerSample)
    }

    private fun readSample(data: ByteArray, offset: Int, format: WavDecoder.SampleFormat): Int = when (format) {
        WavDecoder.SampleFormat.PCM_8 -> {
            // WAV de 8 bits es sin signo (0..255, centro en 128); FLAC de 8 bits
            // es con signo (-128..127). Conversión 1:1, sin pérdida.
            (data[offset].toInt() and 0xFF) - 128
        }
        WavDecoder.SampleFormat.PCM_16 -> {
            (data[offset].toInt() and 0xFF) or (data[offset + 1].toInt() shl 8)
        }
        WavDecoder.SampleFormat.PCM_24 -> {
            (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                (data[offset + 2].toInt() shl 16) // el byte alto conserva el signo
        }
        WavDecoder.SampleFormat.PCM_32, WavDecoder.SampleFormat.FLOAT_32 ->
            error("readSample() no debe llamarse para PCM_32/FLOAT_32; normalize() los rechaza antes.")
    }
}
