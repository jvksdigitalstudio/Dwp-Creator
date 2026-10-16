package com.jvk.dwpcreator.session.project

import android.net.Uri
import java.io.InputStream
import java.io.OutputStream

/** Acceso a los documentos que elige el usuario (Storage Access Framework). */
interface FileStreams {

    /** @throws IllegalStateException si el documento no se puede abrir para lectura. */
    fun openInput(uri: Uri): InputStream

    /** @throws IllegalStateException si el documento no se puede abrir para escritura. */
    fun openOutput(uri: Uri): OutputStream
}
