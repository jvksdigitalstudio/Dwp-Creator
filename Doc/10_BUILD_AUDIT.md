# 10 — AUDITORÍA DE BUILD

## Configuración declarada (evidencia: archivos Gradle reales)

| Parámetro | Valor | Archivo |
|---|---|---|
| AGP | 8.3.2 | `build.gradle.kts` (root) |
| Kotlin | 1.9.23 | `build.gradle.kts` (root) |
| compileSdk | 34 | `app/build.gradle.kts` |
| minSdk | 26 | `app/build.gradle.kts` |
| targetSdk | 34 | `app/build.gradle.kts` |
| Java/JVM target | 17 | `app/build.gradle.kts` |
| Compose | habilitado, BOM `2024.05.00`, compilerExtension `1.5.11` | `app/build.gradle.kts` |
| Namespace / applicationId | `com.jvk.dwpcreator` | `app/build.gradle.kts` |
| versionName | `0.1.0-skeleton` | `app/build.gradle.kts` — **nota:** el sufijo "-skeleton" es otro rastro de documentación desactualizada (Sección 11) |
| minify release | deshabilitado | `app/build.gradle.kts` |
| NDK/JNI/ABI | no configurado, no usado | ausencia confirmada en todo el código |

## Dependencias (todas revisadas, ninguna sin uso evidente)

`[CODE]` Cada dependencia declarada tiene uso verificable en el código:
- `androidx.core:core-ktx`, `lifecycle-runtime-ktx/compose`, `lifecycle-viewmodel-compose`, `activity-compose` — base estándar de una app Compose+ViewModel.
- `kotlinx-coroutines-android` — usado extensivamente en `DwpCreatorViewModel` (`viewModelScope`, `Dispatchers.IO/Default`, `delay`).
- Compose BOM + `ui`/`ui-graphics`/`ui-tooling-preview`/`foundation`/`material3`/`material-icons-extended`/`animation` — todos usados (iconos de `Icons.Default.*`, `AlertDialog`, `LazyColumn`, animaciones de `PianoKeyBadge`/`ImportingOverlay`).
- `junit:junit:4.13.2` (testImplementation) — único framework de test, usado en los 4 archivos de test.

`[CODE]` **No hay dependencias duplicadas, ni JNI/NDK, ni librerías de audio/FLAC de terceros** (confirmando de nuevo que no hay ningún encoder/decoder FLAC en el proyecto, ni siquiera como dependencia sin usar).

`[UNKNOWN]` No se auditaron transitivamente las licencias de cada dependencia (fuera del alcance práctico sin acceso a red en este entorno); todas son librerías estándar de AndroidX/Jetpack/Kotlin/JUnit, de licencias permisivas conocidas (Apache 2.0/EPL), sin señales de riesgo.

## ¿Puede compilarse desde un entorno limpio?

`[UNKNOWN]` **No se pudo verificar de forma independiente en esta sesión de auditoría**, porque el entorno de trabajo usado para la auditoría no tiene acceso a red ni al Android SDK/Gradle instalados. Esto es una limitación explícita de esta auditoría, no una afirmación sobre el estado del proyecto — se declara honestamente como `[UNKNOWN]` en lugar de asumir que compila o que falla (Sección 60 del prompt de trabajo).

Lo que sí se puede afirmar por inspección estática:
- `[CODE]` **No existe `gradlew` ni `gradle/wrapper/gradle-wrapper.jar` en el repositorio.** El workflow de CI (`.github/workflows/build.yml`) no los usa tampoco — usa `gradle/actions/setup-gradle@v4` con `gradle-version: 8.6`, que instala un Gradle de sistema en el runner. **Esto significa que el proyecto SÍ es buildable en GitHub Actions tal como está configurado, pero NO tiene un wrapper reproducible para desarrollo local** (alguien que clone el repo necesitaría tener Gradle 8.6 instalado manualmente, o generar el wrapper). Clasificado como hallazgo de build, severidad LOW (no bloquea el flujo de trabajo actual descrito en el prompt maestro, que depende de GitHub Actions + Termux, no de build local).
- El workflow ejecuta `gradle testDebugUnitTest` **antes** de `gradle assembleDebug` — buen orden (falla rápido si los tests fallan, antes de gastar tiempo compilando el APK completo).
- No hay pasos de lint/detekt/ktlint en el CI — ninguna verificación estática de estilo o calidad más allá de la compilación y los tests unitarios.

## Empaquetado

`[CODE]` `packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"` — exclusión estándar y correcta para evitar conflictos de META-INF duplicados entre dependencias AndroidX, no indica ningún problema.

## AndroidManifest

`[CODE]` Mínimo y coherente con el código: declara `uses-feature android.software.midi` (no requerido, `required="false"`, correcto ya que `MidiInputManager.isMidiSupported` ya maneja el caso de ausencia de MIDI en runtime), una única `Activity` exportada como launcher, `allowBackup=true`. No se declaran permisos de almacenamiento explícitos — correcto, porque el acceso a archivos se hace vía `ActivityResultContracts.OpenDocument`/`CreateDocument` (Storage Access Framework), que no requiere permisos runtime.

## Recomendación (no ejecutada en esta fase, solo señalada)

`[INFERENCE]` Generar y commitear el Gradle Wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) sería una mejora de bajo riesgo y alto valor para reproducibilidad — pero, según la Sección 33 del prompt de trabajo, **no se debe corregir durante la Fase 0** salvo que sea estrictamente necesario para poder auditar. No lo era: el análisis se completó por lectura estática y verificación binaria independiente sin necesitar compilar.
