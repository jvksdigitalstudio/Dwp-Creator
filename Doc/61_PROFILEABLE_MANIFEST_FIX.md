# 61 — Causa real (con alta confianza): faltaba `<profileable>` en el manifiesto

## El patrón que llevó hasta aquí
Cinco intentos, cinco entornos y configuraciones completamente distintas,
**el mismo error idéntico** cada vez:
`java.lang.IllegalStateException: Unable to confirm activity launch completion`.

1. GMD, imagen ATD (API 30) -> colgado (no llegó a este error).
2. GMD, imagen `aosp` (API 34) -> este error, 3 min (Doc/54).
3. GMD, `aosp` + animaciones del sistema en 0 -> el mismo error (Doc/57).
4. `reactivecircus` + GPU real (`swiftshader_indirect`) + animaciones en 0
   -> el mismo error, letra por letra (Doc/58).
5. Lo anterior + `reportFullyDrawn()` en la app -> el mismo error otra vez,
   confirmado por el usuario con captura (esta sesión).

Cinco variables distintas cambiadas (imagen del emulador, GPU, animaciones,
código de la app) y el resultado no varió ni un carácter. Esa invariancia es
la pista real: el problema nunca estuvo en el entorno de CI ni en cómo
dibuja Compose -- tenía que ser algo constante en las cinco corridas.

## Lo constante en las cinco corridas
Las cinco probaron la variante **release no minificada** de `:app`
(`nonMinifiedRelease`) -- una build NO depurable. Eso es justo lo único que
nunca cambió.

## La causa real
Documentado por el propio Android: para que herramientas de perfilado como
Macrobenchmark puedan inspeccionar una app en una build no depurable
(release), la app tiene que declararse explícitamente "perfilable por shell"
en el manifiesto con `<profileable android:shell="true" />`. Sin esa
etiqueta, el sistema le niega a Macrobenchmark la introspección que necesita
sobre el proceso -- coincide exactamente con un fallo al "confirmar" el
estado de la app, sin importar cómo se renderice la pantalla.

## Corrección
`app/src/main/AndroidManifest.xml`: `<profileable android:shell="true" />`
dentro de `<application>`. Requiere API 29+ para tener efecto; con
`minSdk = 26` el elemento simplemente se ignora en versiones anteriores, sin
romper nada -- es la práctica recomendada independientemente del `minSdk`.

## Por qué esta vez hay más confianza que en los intentos anteriores
Los intentos 1-4 apuntaban a la infraestructura (emulador, GPU, animaciones)
sin evidencia de que esa fuera la causa -- eran hipótesis razonables pero no
confirmadas. Este es el primer intento basado en un patrón real observado
(invariancia total del error a través de cinco entornos) más un requisito
documentado que coincide exactamente con el síntoma.

## No afecta
`Build APK` (el release real) no se ve afectado -- `<profileable>` no
cambia el comportamiento de la app para el usuario final, solo habilita
introspección vía shell/adb local.

## Siguiente paso
Disparar "Generate Baseline Profile" de nuevo. Si el error desaparece,
confirma la causa real después de cinco intentos. Si persiste, es una señal
fuerte de que el problema está en otro lugar constante entre las cinco
corridas -- probablemente la propia versión de `androidx.benchmark` usada
(`1.3.3`, sin verificar contra release notes reales) -- y esa sería la
siguiente hipótesis a perseguir.
