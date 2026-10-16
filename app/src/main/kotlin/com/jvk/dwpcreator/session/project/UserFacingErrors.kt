package com.jvk.dwpcreator.session.project

/** Textos de error que ve el usuario, compartidos por todas las operaciones sobre el proyecto. */
internal object UserFacingErrors {

    const val OUT_OF_MEMORY =
        "El dispositivo se quedó sin memoria procesando este instrumento. Cierra otras apps e " +
            "inténtalo de nuevo, o usa un instrumento con menos audio."

    /** Mensaje de [e] o, si no trae ninguno, el nombre de su clase. */
    fun describe(e: Exception): String = e.message ?: e::class.simpleName ?: "error desconocido"
}
