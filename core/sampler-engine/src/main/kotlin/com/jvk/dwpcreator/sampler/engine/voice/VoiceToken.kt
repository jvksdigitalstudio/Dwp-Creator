package com.jvk.dwpcreator.sampler.engine.voice

/**
 * Identidad de una nota, creada por quien la dispara. [released] se
 * escribe desde cualquier hilo para pedir la liberación (tecla soltada);
 * el hilo de audio la lee en cada bloque. [finished] lo escribe el hilo de
 * audio cuando la voz terminó o se descartó, para que quien lleva la
 * contabilidad de notas pueda olvidarla. Dos `@Volatile` en vez de un
 * mensaje por la cola: así soltar una tecla **antes** de que su
 * `noteOn` haya llegado al motor (muestra aún decodificándose) sigue
 * funcionando, sin condiciones de carrera ni notas pegadas.
 */
class VoiceToken {
    @Volatile
    var released: Boolean = false

    @Volatile
    var finished: Boolean = false
}
