# 46 — Migración a Kotlin 2.0/K2 + andamiaje de Baseline Profiles

## Pedido del usuario

Tras confirmar que la build de release (Doc/45) resolvió el lag de forma
perceptible, pidió explícitamente "todo lo necesario para la máxima
fluidez, todo" -- el nivel más alto disponible, no solo lo que ya se aplicó.

## Aplicado en este cambio (verificado contra el código real, bajo riesgo)

### 1. Kotlin 2.0.21 + plugin oficial del compilador de Compose (K2)
`build.gradle.kts` (raíz) y `app/build.gradle.kts`:
- Kotlin `1.9.23` -> `2.0.21`.
- Se retira `composeOptions { kotlinCompilerExtensionVersion = "1.5.11" }`
  -- mecanismo antiguo que ataba a mano la versión del compilador de
  Compose a un artefacto Maven aparte de la versión de Kotlin.
- Se añade el plugin `org.jetbrains.kotlin.plugin.compose` (misma versión
  que Kotlin, `2.0.21`) -- con Kotlin 2.0+ el compilador de Compose vive
  dentro del propio compilador K2, este plugin solo lo activa.
- El flag experimental de Strong Skipping Mode del Doc/45
  (`-P plugin:...:experimentalStrongSkipping=true`, mecanismo de K1) se
  sustituye por la opción oficial y estable del nuevo plugin:
  `composeCompiler { enableStrongSkippingMode.set(true) }`. Mismo efecto,
  API estable en vez de un flag experimental.
- `androidx.compose:compose-bom` se deja en `2024.05.00` (sin cambios):
  las librerías de Compose son bytecode Kotlin normal, con compatibilidad
  binaria fuerte entre compiladores -- no necesitan subir de versión solo
  porque el compilador de la app subió a K2. Se evita así introducir una
  coordenada de versión nueva sin poder verificarla aquí.

### 2. `androidx.profileinstaller` (runtime, real desde ya)
Añadido a `app/build.gradle.kts`. Es la librería que de verdad instala en
el dispositivo, en segundo plano, el perfil de referencia que trae el
APK (`app/src/main/baseline-prof.txt`) para que ART compile por adelantado
(AOT) los métodos marcados, en los primeros arranques -- funciona también
para un APK instalado fuera de Play Store (como se distribuye este
proyecto, vía GitHub Actions), no es exclusivo de una instalación desde la
tienda.

## Deliberadamente NO aplicado en este cambio (y por qué)

### Contenido real del Baseline Profile
`app/src/main/baseline-prof.txt` se dejó como placeholder documentado, solo
con comentarios -- **sin reglas inventadas**. El formato real
(`HSPLcom/paquete/Clase;->metodo(firma)V` por cada método, con el flag
H/S/P correcto) depende de firmas de bytecode exactas que solo se obtienen
perfilando la app de verdad con Macrobenchmark en un dispositivo/emulador
real ejecutando el recorrido de usuario (abrir, cargar instrumento,
scrollear la lista) -- exactamente el tipo de "reemplazo sin verificar" que
este proyecto pidió evitar desde el principio. Rellenarlo a mano con
firmas adivinadas podía, en el peor caso, romper el empaquetado del
release que recién quedó funcionando (Doc/45).

### Módulo `:baselineprofile` (Macrobenchmark) + emulador en CI
El camino real para generar el contenido de arriba es un módulo de test
instrumentado (`androidx.benchmark.macro.junit4.BaselineProfileRule`) que
corre en un emulador -- en GitHub Actions eso requiere el plugin
`androidx.baselineprofile` y una acción de emulador
(p. ej. `reactivecircus/android-emulator-runner`), cada uno con su propia
coordenada de versión. **Este entorno no tiene acceso a red para
confirmar cuáles son las versiones actuales exactas de esos plugins.**
Adivinarlas e incrustarlas en un módulo nuevo, acoplado además al mismo
pipeline de CI que genera el APK de release que el usuario ya confirmó que
funciona, es exactamente el riesgo que no vale la pena correr sin poder
verificarlo -- una versión mal escrita ahí puede tumbar el build entero,
y el usuario no tiene forma de depurar un error de resolución de Gradle
por su cuenta (no trabaja con Android Studio).

## Plan real para cerrar esto (próximo paso, explícito)

1. Hacer push de este cambio tal cual está y confirmar en GitHub Actions
   que `assembleRelease` sigue compilando limpio con Kotlin 2.0.21/K2 (es
   el riesgo real de este cambio: una migración de versión de compilador,
   no el Baseline Profile en sí, que no toca nada del build actual).
2. Si compila limpio: siguiente sesión aparte, dedicada, para el módulo
   `:baselineprofile` -- ahí sí con margen para iterar sobre el log real
   de GitHub Actions si una versión de plugin no resuelve, en vez de
   intentar acertarla a ciegas en el mismo cambio que ya se está probando.
3. Si `assembleRelease` falla tras este push: pegar aquí el log completo
   del job de GitHub Actions -- con Kotlin 2.0 el error más probable (si
   lo hay) es un mensaje explícito de incompatibilidad de versión, fácil
   de identificar y corregir con el log real delante, no a ciegas.
