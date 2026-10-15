# Reglas de R8 para DwpCreator -- ver Doc/45 (raíz del cambio: el pipeline
# real de este proyecto solo construía `assembleDebug`, nunca pasaba por R8).
#
# Auditoría real hecha antes de activar minify (no reglas "por si acaso"
# copiadas de una plantilla): se revisó todo `app/src/main/java` buscando
# reflexión (`Class.forName`, `::class.java` reflexivo), serialización
# (kotlinx.serialization, Gson) y `Parcelable` -- no hay ninguno. El único
# API de plataforma "especial" que usa la app es `android.media.midi.*`
# (ver `midi/MidiInputManager.kt`), consumido de forma normal (no reflejado),
# así que R8 puede razonar sobre esas clases sin ayuda extra.
#
# Por eso este archivo es deliberadamente corto: las reglas por defecto de
# AGP (`proguard-android-optimize.txt`, ya referenciado en
# `app/build.gradle.kts`) más las reglas consumidoras que ya traen empaquetadas
# las librerías de Compose/AndroidX usadas (`androidx.compose.*`,
# `androidx.lifecycle:lifecycle-viewmodel-compose`, etc.) son suficientes.
# Si una futura versión de la app introduce reflexión, serialización o un
# modelo de datos expuesto a una librería externa, la regla correspondiente
# debe añadirse aquí explicando el motivo real -- no copiarse "por si acaso".

# Trazas de crash legibles (Doc/52). `DwpCreatorApplication` +
# `CrashDiagnosticScreen` muestran el stack trace COMPLETO en pantalla para
# poder diagnosticar un cierre inesperado desde una sola captura, sin adb ni
# logcat. Con la ofuscación de R8 activa (release, Doc/45) esa traza saldría
# como `a.b.c()`, ilegible, y sin un `mapping.txt` a mano no serviría de nada.
# Por eso se conserva el shrink y la optimización de R8 (que es lo que aporta
# el rendimiento y el menor tamaño) pero se desactiva SOLO el renombrado de
# clases y métodos: los nombres reales siguen apareciendo en la traza.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
