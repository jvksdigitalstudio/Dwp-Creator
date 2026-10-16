package com.jvk.dwpcreator.sampler.api.options

/**
 * Opción de un parámetro de elección (modo de filtro, forma de LFO...). Los
 * enums de opciones implementan esta interfaz y todo lo demás (etiquetas de la
 * UI, conversión índice <-> opción, persistencia como `Float`) sale de aquí, sin
 * repetirlo en cada enum.
 */
interface ChoiceOption {
    val label: String
}
