package com.jvk.dwpcreator.dwp.monolithic

/**
 * La operación pedida requiere una decisión de formato que
 * Doc/23_MONOLITHIC_DWP_FLAC_IMPLEMENTATION_SPEC.md marca explícitamente
 * como 🔴 NO CONFIRMADO, y esta implementación se niega a inventarla
 * (Sección 3 del prompt maestro de esta fase: "detén esa parte concreta,
 * documenta qué falta, explica qué evidencia sería necesaria, no inventes
 * un formato").
 */
class UnsupportedMonolithicFormatException(message: String) : MonolithicDwpException(message)
