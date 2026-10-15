package com.jvk.dwpcreator.codec.wav

/** Un archivo WAV es inválido, está truncado o usa una variante no soportada ([WavDecoder.decode]). */
class WavFormatException(message: String) : Exception(message)
