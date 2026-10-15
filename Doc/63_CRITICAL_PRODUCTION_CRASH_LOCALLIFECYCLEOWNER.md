# 63 — CRÍTICO: crash real en producción por `collectAsStateWithLifecycle`

## Evidencia real
Captura de la propia `CrashDiagnosticScreen` del usuario, en su tablet, con
la traza completa y legible (gracias a `-dontobfuscate`, Doc/52):

```
java.lang.IllegalStateException: CompositionLocal LocalLifecycleOwner not present
    at androidx.lifecycle.compose.LocalLifecycleOwnerKt$LocalLifecycleOwner$1.invoke
    ...
    at ...collectAsStateWithLifecycle(...)
    at ...DwpCreatorApp(...)
    at com.jvk.dwpcreator.MainActivity$onCreate$1$1.invoke
```//
(Los nombres de clase intermedios aparecen mezclados -- `kotlin.ResultKt`,
`kotlin.TuplesKt`, `material3.FabPosition` -- porque R8 sigue fusionando
clases aunque `-dontobfuscate` impida renombrarlas; los nombres de método y
los números de línea sí son reales y se pudieron seguir).

## Causa
El Doc/52 cambió `collectAsState()` por `collectAsStateWithLifecycle()` en
los seis flujos de `DwpCreatorApp()`, como mejora de eficiencia (pausar la
recolección en segundo plano). `collectAsStateWithLifecycle()` necesita que
el `CompositionLocal LocalLifecycleOwner` (del paquete
`androidx.lifecycle.compose`) esté provisto en ese punto de la composición.
En este proyecto, con esta combinación de versiones de Compose/lifecycle,
no lo está -- y en vez de degradar con normalidad, lanza esta excepción,
tumbando la app en el primer arranque real en un dispositivo físico. Este
bug **nunca se manifestó en ninguna corrida de CI** (ni en `Build APK` ni en
el intento de Baseline Profile) porque ninguna de las dos ejecuta la app de
verdad en un dispositivo -- solo la compila o corre un test que nunca llega
a este punto de la composición real.

## Corrección
`MainActivity.kt`: los seis `collectAsStateWithLifecycle()` vuelven a
`collectAsState()`. No depende de ningún `CompositionLocal`, así que no
tiene este riesgo. La única diferencia real frente a la versión con
lifecycle es que sigue recolectando (sin pausar) con la app en segundo
plano -- un costo de eficiencia menor y conocido, no un bug. No se vuelve a
intentar la versión con lifecycle sin poder probarla antes contra un build
y un dispositivo reales.

## Lección para el resto del proyecto
Esta sesión tuvo dos tipos de "fallo" muy distintos que no deben
confundirse:
- Los fallos del Baseline Profile (Docs 54-62): ocurrían SOLO en un
  workflow de CI aislado, sin tocar nunca el APK real del usuario -- fallar
  ahí, agotar hipótesis y cerrar la investigación fue la decisión correcta.
- Este crash (Doc/63): ocurrió en el APK real, en la tablet real del
  usuario -- es exactamente el tipo de problema que SÍ había que corregir
  de inmediato al tener evidencia real, sin importar que la "mejora" en
  cuestión sonara razonable en el momento de aplicarla.

La causa raíz exacta (por qué `LocalLifecycleOwner` no está disponible ahí
--probablemente una incompatibilidad de versiones entre `compose-bom` y
`androidx.lifecycle:lifecycle-runtime-compose:2.8.0`-- no se investigó a
fondo, porque revertir al código ya probado y funcional es la corrección
correcta y suficiente: no hace falta entender el mecanismo exacto de una
librería para dejar de usar la función que demostrablemente crashea.

## Siguiente paso
Subir este cambio, compilar e instalar el APK, y confirmar en la tablet que
la app abre con normalidad (sin la pantalla de crash) en un arranque limpio.
