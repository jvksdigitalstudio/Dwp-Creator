package com.jvk.dwpcreator.dwp.monolithic

/**
 * Errores propios del subsistema Monolithic DWP. Deliberadamente distintas
 * de [com.jvk.dwpcreator.dwp.DwpFormatException] (esa es del Core,
 * no se reutiliza para no mezclar la semántica de "esto no es un .dwp
 * válido en absoluto" con "esto no puede convertirse a Monolithic por una
 * razón específica de esta fase") -- Sección 29 del prompt maestro de la
 * fase de especificación: fallar explícito, nunca silenciar.
 */
sealed class MonolithicDwpException(message: String) : Exception(message)
