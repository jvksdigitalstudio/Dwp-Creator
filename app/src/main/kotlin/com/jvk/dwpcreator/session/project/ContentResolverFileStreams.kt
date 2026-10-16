package com.jvk.dwpcreator.session.project

import android.content.ContentResolver
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream

/** [FileStreams] sobre el [ContentResolver] de Android. */
class ContentResolverFileStreams(private val resolver: ContentResolver) : FileStreams {

    override fun openInput(uri: Uri): InputStream =
        resolver.openInputStream(uri)
            ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado.")

    override fun openOutput(uri: Uri): OutputStream =
        resolver.openOutputStream(uri)
            ?: throw IllegalStateException("No se pudo escribir el archivo de salida.")
}
