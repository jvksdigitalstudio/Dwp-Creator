package com.jvk.dwpcreator.dwp.io

/** El zip del proyecto no se pudo leer o viola un límite defensivo ([ZipProjectLoader.load]). */
class ZipLoadException(message: String) : Exception(message)
