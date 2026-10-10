package com.jvk.dwpcreator.domain.dwp

/**
 * Reglas que debe cumplir el nombre nuevo de un instrumento ("RENOMBRAR")
 * para que el proyecto siga siendo legible y exportable. Kotlin puro, sin
 * Android -- testeable en JVM.
 *
 * **Por qué solo ASCII imprimible (0x20..0x7E).** Este es el mismo
 * conjunto que [DwpBlock.isTextPayload] exige para tratar un payload como
 * texto: cualquier bloque de nombre/ruta con un byte fuera de ese rango
 * ("Acústico", "Piano ñ", un emoji...) deja de decodificar como texto, con
 * consecuencias en cadena:
 *  1. [DwpEngine.listSamples] devuelve el nombre de reserva `sample_N` para
 *     TODAS las muestras y una nota `?`;
 *  2. [DwpEngine.detectInstrumentBaseName] devuelve `null`;
 *  3. al exportar, `ZipProjectExporter` reescribe el nombre y la ruta de cada
 *     muestra dentro del `.dwp` con ese `sample_N` de reserva -- es decir, el
 *     archivo exportado pierde los nombres reales de las 48 muestras.
 * Como además el nombre se codifica en ISO-8859-1, los caracteres no
 * representables se convertirían en `?` sin aviso. Rechazar el nombre en el
 * origen es la única forma de que nada de esto ocurra en silencio.
 *
 * **Caracteres de nombre de archivo.** El nombre acaba siendo carpeta y
 * prefijo de cada `.wav` dentro del ZIP exportado, y aparece dentro de las
 * rutas estilo Windows del `.dwp`; `/ \ : * ? " < > |` no son válidos en
 * nombres de archivo de Windows (donde el ZIP acabará abriéndose) y `/`/`\`
 * además los rechaza `ZipProjectExporter` por seguridad (path traversal).
 */
object InstrumentNameValidator {

    private val FORBIDDEN_FILENAME_CHARS = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    private const val PRINTABLE_ASCII_MIN = 0x20
    private const val PRINTABLE_ASCII_MAX = 0x7E

    /**
     * Devuelve `null` si [name] es válido, o un mensaje en español, listo
     * para mostrar al usuario, que explica la PRIMERA regla incumplida.
     */
    fun validate(name: String): String? {
        if (name.isBlank()) {
            return "El nombre no puede estar vacío."
        }
        if (name != name.trim()) {
            return "El nombre no puede empezar ni terminar con espacios."
        }
        if (name.endsWith('.')) {
            return "El nombre no puede terminar en punto."
        }
        name.firstOrNull { it.code !in PRINTABLE_ASCII_MIN..PRINTABLE_ASCII_MAX }?.let { bad ->
            return "El carácter '$bad' no está permitido: usa solo letras sin acento (A-Z), " +
                "números, espacios y signos básicos. Los acentos, la ñ y los emojis no se pueden " +
                "guardar en el .dwp y corromperían los nombres de las muestras."
        }
        name.firstOrNull { it in FORBIDDEN_FILENAME_CHARS }?.let { bad ->
            return "El carácter '$bad' no está permitido en un nombre de archivo."
        }
        return null
    }
}
