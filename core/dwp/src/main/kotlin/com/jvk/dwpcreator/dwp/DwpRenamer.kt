package com.jvk.dwpcreator.dwp

/**
 * Renombrado de un instrumento dentro de un [DwpDocument].
 *
 * Es deliberadamente estrecho: solo reescribe el payload de las etiquetas de
 * [RENAMABLE_TAGS] (nombre/ruta del instrumento y nombre/ruta de cada muestra),
 * cuya semántica está confirmada. Nunca toca un bloque solo porque decodifique
 * como texto imprimible: una versión anterior lo hacía y se marcó como riesgo
 * estructural (un campo binario podía ser, por casualidad, todo bytes
 * imprimibles y corromperse con una sustitución que no le correspondía). Los
 * bloques desconocidos u opacos se copian intactos, sea cual sea su contenido.
 */
object DwpRenamer {

    /**
     * The only tags [renameInstrument] is allowed to rewrite. Deliberately a
     * closed allow-list, not "any block that looks like text": each of these
     * four tags has confirmed, demonstrated semantics (see [DwpBlock]'s KDoc
     * and Doc/05_DWP_IMPLEMENTATION_AUDIT.md) as an instrument/sample name or
     * path. No other tag is eligible for text substitution, no matter what
     * its payload decodes as.
     */
    private val RENAMABLE_TAGS = setOf(
        DwpBlock.TAG_INSTRUMENT_NAME,
        DwpBlock.TAG_PROJECT_PATH,
        DwpBlock.TAG_SAMPLE_NAME,
        DwpBlock.TAG_SAMPLE_PATH
    )

    /**
     * Replaces every textual occurrence of [oldName] with [newName], but
     * *only* inside [RENAMABLE_TAGS] (the instrument name, the dwp's own
     * saved path, and every sample's name + path, recursing into each sample
     * container). Each rewritten block gets its own length recomputed; every
     * other block -- including any block that happens to decode as
     * printable text but isn't one of the four known name/path tags -- is
     * copied through byte-for-byte untouched.
     */
    fun renameInstrument(doc: DwpDocument, oldName: String, newName: String): DwpDocument {
        if (oldName.isEmpty() || oldName == newName) return doc
        return DwpDocument(doc.preamble, doc.blocks.map { renameRecursive(it, oldName, newName) })
    }

    private fun renameRecursive(block: DwpBlock, oldName: String, newName: String): DwpBlock {
        if (block.tag == DwpBlock.TAG_SAMPLE_CONTAINER) {
            val nested = DwpTokenizer.tokenizeStrict(block.payload, context = "contenedor de sample")
            val renamedNested = nested.map { renameRecursive(it, oldName, newName) }
            return DwpBlock(block.tag, block.reserved, DwpTokenizer.serialize(renamedNested))
        }
        if (block.tag !in RENAMABLE_TAGS) return block
        val text = block.textOrNull() ?: return block
        if (!text.contains(oldName)) return block
        return DwpBlock(block.tag, block.reserved, text.replace(oldName, newName).toByteArray(Charsets.ISO_8859_1))
    }
}
