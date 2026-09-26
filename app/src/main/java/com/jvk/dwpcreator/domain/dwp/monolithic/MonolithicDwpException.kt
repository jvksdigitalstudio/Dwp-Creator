package com.jvk.dwpcreator.domain.dwp.monolithic

/**
 * Errores propios del subsistema Monolithic DWP. Deliberadamente distintas
 * de [com.jvk.dwpcreator.domain.dwp.DwpFormatException] (esa es del Core,
 * no se reutiliza para no mezclar la semántica de "esto no es un .dwp
 * válido en absoluto" con "esto no puede convertirse a Monolithic por una
 * razón específica de esta fase") -- Sección 29 del prompt maestro de la
 * fase de especificación: fallar explícito, nunca silenciar.
 */
sealed class MonolithicDwpException(message: String) : Exception(message)

/**
 * La operación pedida requiere una decisión de formato que
 * Doc/23_MONOLITHIC_DWP_FLAC_IMPLEMENTATION_SPEC.md marca explícitamente
 * como 🔴 NO CONFIRMADO, y esta implementación se niega a inventarla
 * (Sección 3 del prompt maestro de esta fase: "detén esa parte concreta,
 * documenta qué falta, explica qué evidencia sería necesaria, no inventes
 * un formato").
 */
class UnsupportedMonolithicFormatException(message: String) : MonolithicDwpException(message)

/** El contenedor de sample objetivo no tiene la forma esperada para insertar/reemplazar audio embebido. */
class MonolithicZoneStructureException(message: String) : MonolithicDwpException(message)

/** El `.dwp` Monolithic construido no pasó la validación estructural o de audio post-construcción. */
class MonolithicDwpValidationException(message: String) : MonolithicDwpException(message)
