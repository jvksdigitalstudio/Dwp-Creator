package com.jvk.dwpcreator.dwp.io

import java.io.BufferedOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * `ZipOutputStream` escribe al flujo de destino en trozos de ~512 bytes; hacia
 * un archivo real (`ContentResolver.openOutputStream`) eso son cientos de
 * miles de llamadas al sistema para un instrumento de ~150 MB. Este búfer las
 * agrupa.
 */
private const val ZIP_WRITE_BUFFER_BYTES = 64 * 1024

/**
 * Un export ya validado y resincronizado, listo para escribirse: el `.dwp`
 * final ya está calculado en memoria (pequeño) y el audio de cada muestra
 * se referencia, no se copia. Se separa de [ZipProjectExporter.prepare] para que quien exporta
 * a un archivo real pueda hacer TODO lo que puede fallar (validación,
 * decodificación de cada WAV) ANTES de abrir/crear el archivo de destino,
 * y después volcar el zip directamente a él con [writeTo].
 */
class PreparedZipExport internal constructor(
    private val folderName: String,
    private val sampleNames: List<String>,
    private val audioByIndex: List<ByteArray>,
    private val dwpBytes: ByteArray
) {
    /**
     * Escribe el zip completo en [output] y lo CIERRA (igual que cualquier
     * `use { }` sobre él). Antes, exportar construía el zip entero en un
     * `ByteArrayOutputStream` -- que al crecer por duplicación reservaba
     * hasta ~2x su tamaño final -- y luego lo copiaba otra vez con
     * `toByteArray()`: para un instrumento de ~150 MB, más de 400 MB de
     * pico solo para exportar, sobre los ~150 MB de audio ya cargados. Ahora
     * cada muestra pasa del audio en memoria directamente al destino.
     */
    fun writeTo(output: OutputStream) {
        ZipOutputStream(BufferedOutputStream(output, ZIP_WRITE_BUFFER_BYTES)).use { zos ->
            zos.putNextEntry(ZipEntry("$folderName/$folderName.dwp"))
            zos.write(dwpBytes)
            zos.closeEntry()

            for (i in sampleNames.indices) {
                zos.putNextEntry(ZipEntry("$folderName/${sampleNames[i]}.wav"))
                zos.write(audioByIndex[i])
                zos.closeEntry()
            }
        }
    }
}
