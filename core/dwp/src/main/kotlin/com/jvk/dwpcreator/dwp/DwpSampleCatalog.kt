package com.jvk.dwpcreator.dwp

/**
 * Lectura de solo lectura de las muestras de un [DwpDocument]: resumen para la
 * UI, nombre base del instrumento y conteo de contenedores.
 *
 * Usa deliberadamente el tokenizador tolerante [DwpTokenizer.tokenize] (no
 * [DwpTokenizer.tokenizeStrict]): estas funciones solo *leen* el flujo anidado
 * y nunca lo reserializan, así que un contenedor con forma inesperada no puede
 * perder bytes en silencio (como sí podría pasar en [DwpRenamer] o
 * [DwpAudioPatcher], que reserializan y por eso usan la variante estricta). El
 * peor caso aquí es un [SampleInfo] incompleto para esa muestra, nunca pérdida
 * de datos.
 */
object DwpSampleCatalog {

    private val SAMPLE_NAME_REGEX = Regex("""^(.*)_([A-G]#?-?\d+)_(\d+)$""")

    /** Extrae un resumen legible de cada contenedor de muestra, para la lista de la UI. */
    fun listSamples(doc: DwpDocument): List<SampleInfo> {
        val result = mutableListOf<SampleInfo>()
        var index = 0
        for (top in doc.blocks) {
            if (top.tag != DwpBlock.TAG_SAMPLE_CONTAINER) continue
            val nested = DwpTokenizer.tokenize(top.payload).blocks
            val byTag = nested.groupBy { it.tag }

            val name = byTag[DwpBlock.TAG_SAMPLE_NAME]?.firstOrNull()?.textOrNull() ?: "sample_$index"
            val path = byTag[DwpBlock.TAG_SAMPLE_PATH]?.firstOrNull()?.textOrNull() ?: ""
            val keyRange = byTag[DwpBlock.TAG_KEY_RANGE]?.firstOrNull()?.payload
            val audioFormat = byTag[DwpBlock.TAG_AUDIO_FORMAT]?.firstOrNull()?.payload

            // Physical order of 0x01f4 is [rootKey, lowKey, highKey] -- verified
            // against all 48 samples of the real fixture (see Doc/05, error E-01):
            // offset 0 matches each sample's own pitch in 48/48 cases with zero
            // exceptions; offset 1 matches in 47/48, deviating only for the
            // lowest sample (extended down to MIDI note 0, a boundary
            // extension -- never the root). Previously these two were swapped
            // here, which broke MIDI-triggered preview for the lowest sample.
            val rootKey = keyRange?.getOrNull(0)?.let { it.toInt() and 0xFF } ?: -1
            val lowKey = keyRange?.getOrNull(1)?.let { it.toInt() and 0xFF } ?: -1
            val highKey = keyRange?.getOrNull(2)?.let { it.toInt() and 0xFF } ?: -1
            // listSamples() is a read-only diagnostic operation and must never
            // throw just because a malformed .dwp has a 0x01f7 payload shorter
            // than 4 bytes -- readLE32 would otherwise throw
            // ArrayIndexOutOfBoundsException reading its offset-0 frame count.
            // Reading tolerantly here does NOT relax the strict exactly-40-bytes
            // requirement enforced by DwpAudioPatcher for the MUTATING
            // operations: "lectura tolerante, mutación estricta" is deliberate.
            val frameCount = if (audioFormat != null && audioFormat.size >= 4) {
                DwpTokenizer.readLE32(audioFormat, 0)
            } else {
                -1
            }

            val m = SAMPLE_NAME_REGEX.find(name)
            result.add(
                SampleInfo(
                    index = index,
                    name = name,
                    note = m?.groupValues?.get(2) ?: "?",
                    velocity = m?.groupValues?.get(3)?.toIntOrNull() ?: -1,
                    lowKey = lowKey,
                    rootKey = rootKey,
                    highKey = highKey,
                    frameCount = frameCount,
                    dwpPath = path
                )
            )
            index++
        }
        return result
    }

    /** Detects the common "<name>_<note>_<velocity>" prefix shared by every sample. */
    fun detectInstrumentBaseName(doc: DwpDocument): String? {
        val matches = listSamples(doc).mapNotNull { SAMPLE_NAME_REGEX.find(it.name) }
        if (matches.isEmpty()) return null
        return matches.groupingBy { it.groupValues[1] }.eachCount().maxByOrNull { it.value }?.key
    }

    /** Number of top-level `0x0003` sample containers in [doc]. */
    fun sampleCount(doc: DwpDocument): Int = doc.blocks.count { it.tag == DwpBlock.TAG_SAMPLE_CONTAINER }
}
