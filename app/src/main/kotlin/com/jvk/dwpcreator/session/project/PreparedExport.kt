package com.jvk.dwpcreator.session.project

import java.io.OutputStream

/**
 * Exportación ya calculada y validada, lista para volcarse al destino.
 *
 * Todo lo que puede fallar (validar, decodificar, codificar) ocurre al
 * prepararla, antes de abrir el archivo de salida; así un fallo nunca deja un
 * archivo a medias.
 */
fun interface PreparedExport {

    /** Escribe el resultado completo en [output] y lo cierra. */
    fun writeTo(output: OutputStream)
}
