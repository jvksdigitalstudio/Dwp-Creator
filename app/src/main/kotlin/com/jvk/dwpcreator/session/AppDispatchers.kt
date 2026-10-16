package com.jvk.dwpcreator.session

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Dispatchers que usa la capa de sesión. Inyectables para que las pruebas
 * puedan ejecutar todo de forma síncrona con `Dispatchers.Unconfined`.
 *
 * @property io lectura y escritura de archivos.
 * @property default trabajo de CPU (decodificar, empaquetar, codificar FLAC, disparar notas).
 */
data class AppDispatchers(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default
)
