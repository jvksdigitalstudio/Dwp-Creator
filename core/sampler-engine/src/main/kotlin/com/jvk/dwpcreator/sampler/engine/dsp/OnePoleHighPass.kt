package com.jvk.dwpcreator.sampler.engine.dsp

/** Pasa-altos de un polo: la entrada menos su propio pasa-bajos. */
internal class OnePoleHighPass {
    private val low = OnePoleLowPass()

    fun process(x: Float, coef: Float): Float = x - low.process(x, coef)

    fun clear() = low.clear()
}
