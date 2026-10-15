package com.jvk.dwpcreator.dwp.monolithic

import com.jvk.dwpcreator.dwp.DwpDocument

/**
 * Paso final de ensamblado binario: [DwpDocument] -> bytes reales de un
 * `.dwp`. Deliberadamente delgado -- toda la serialización real ya vive en
 * [DwpDocument.toBytes]/[com.jvk.dwpcreator.dwp.DwpTokenizer.serialize]
 * (Core, sin modificar). Existe como componente explícito y nombrado
 * (Sección 7 del prompt maestro: "DWP Binary Assembly" en el diagrama de
 * arquitectura objetivo) para que el punto exacto donde un `DwpDocument`
 * Monolithic se convierte en bytes sea un lugar único, localizable y
 * futuramente extensible (p. ej. para escribir a un `OutputStream` en vez
 * de mantener el array completo en memoria), sin que
 * [MonolithicDwpBuilder] tenga que conocer ese detalle.
 */
object DwpBinaryAssembler {
    fun assemble(document: DwpDocument): ByteArray = document.toBytes()
}
