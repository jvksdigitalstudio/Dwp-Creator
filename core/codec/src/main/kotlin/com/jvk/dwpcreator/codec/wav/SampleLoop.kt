package com.jvk.dwpcreator.codec.wav

/**
 * Región de bucle de una muestra, en frames de la propia muestra: de
 * [startFrame] (incluido) a [endFrame] (**excluido**).
 */
data class SampleLoop(val startFrame: Int, val endFrame: Int)
