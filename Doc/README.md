# Documentación técnica

Historial completo de auditorías, decisiones y correcciones del proyecto, **numerado en orden cronológico**.
Los nombres de archivo no se renombran: el código y los demás documentos los citan por número (p. ej. «Doc/45»).

* **Estado vigente:** `18_CANONICAL_STATE.md` (con su adenda sobre la arquitectura multimódulo).
* **Arquitectura actual:** `72` (diseño y decisiones) y `73` (auditoría del código tocado).
* Los documentos anteriores al 72 citan rutas y paquetes de la estructura antigua (`domain.*`, `audio.*`);
  el mapa antiguo→nuevo está en `72` §4.

## Empezar por aquí (estado actual y arquitectura)

| N.º | Documento |
|---|---|
| [15](15_AUDIT_CONCLUSIONS.md) | CONCLUSIONES DE LA AUDITORÍA Y PROPUESTA DE REINGENIERÍA |
| [18](18_CANONICAL_STATE.md) | ESTADO CANÓNICO ACTUAL DEL PROYECTO |
| [72](72_MODULAR_ARCHITECTURE_REFACTOR.md) | Refactorización arquitectónica: módulos, desacoplamiento y una responsabilidad por archivo |
| [73](73_REFACTOR_AUDIT.md) | Auditoría del código tocado por la refactorización modular |

## Auditoría inicial del proyecto

| N.º | Documento |
|---|---|
| [00](00_PROJECT_AUDIT.md) | AUDITORÍA INTEGRAL DEL PROYECTO (Resumen Ejecutivo) |
| [01](01_PROJECT_STRUCTURE.md) | ESTRUCTURA REAL DEL PROYECTO |
| [02](02_REAL_ARCHITECTURE.md) | ARQUITECTURA REAL (reconstruida, no idealizada) |
| [03](03_CODE_INVENTORY.md) | INVENTARIO TÉCNICO DE CÓDIGO |
| [04](04_EXECUTION_FLOW.md) | FLUJO DE EJECUCIÓN (paso a paso, con nombres de función reales) |
| [09](09_TEST_AUDIT.md) | AUDITORÍA DE TESTS |
| [10](10_BUILD_AUDIT.md) | AUDITORÍA DE BUILD |
| [11](11_DOCUMENTATION_AUDIT.md) | AUDITORÍA DE DOCUMENTACIÓN EXISTENTE (vs. implementación real) |
| [12](12_KNOWN_ISSUES.md) | MATRIZ DE PROBLEMAS CONOCIDOS |
| [13](13_RISK_REGISTER.md) | REGISTRO DE RIESGOS |
| [14](14_KEEP_REFACTOR_REPLACE.md) | MATRIZ KEEP / REFACTOR / REPLACE / REMOVE |
| [17](17_AUDIT_ERRATA.md) | ERRATA DE LA AUDITORÍA ANTERIOR (auditoría de la auditoría) |

## Formato DWP, audio WAV/FLAC y DWP monolítico

| N.º | Documento |
|---|---|
| [05](05_DWP_IMPLEMENTATION_AUDIT.md) | AUDITORÍA ESPECÍFICA DE LA IMPLEMENTACIÓN DWP |
| [06](06_BINARY_FORMAT_MODEL.md) | MODELO FÍSICO BINARIO DEL .dwp (verificado) |
| [07](07_AUDIO_PIPELINE_AUDIT.md) | AUDITORÍA DEL PIPELINE DE AUDIO |
| [08](08_MONOLITHIC_DWP_ANALYSIS.md) | ANÁLISIS: DWP MONOLÍTICO (objetivo final vs. estado actual) |
| [16](16_PHASE1_CHANGES.md) | FASE 1: FUNDACIÓN BINARIA (cambios implementados) |
| [19](19_PHASE2_CORE_HARDENING.md) | FASE 2: AUDITORÍA, CORRECCIÓN Y CONSOLIDACIÓN DEL CORE |
| [20](20_CORE_STABILIZATION_PASS2.md) | CORE STABILIZATION PASS 2 |
| [21](21_CORE_STABILIZATION_PASS3.md) | CORE STABILIZATION PASS 3 |
| [22](22_CORE_STABILIZATION_FINAL_VERIFICATION.md) | Core Stabilization — Final Verification |
| [23](23_MONOLITHIC_DWP_FLAC_IMPLEMENTATION_SPEC.md) | ESPECIFICACIÓN DE IMPLEMENTACIÓN: DWP MONOLITHIC + FLAC |
| [24](24_MONOLITHIC_DWP_FLAC_IMPLEMENTATION.md) | IMPLEMENTACIÓN: DWP MONOLITHIC + FLAC (primera versión) |
| [25](25_PRE_CI_AUDIT_AND_DOCUMENTATION_PASS.md) | PASS PRE-CI: AUDITORÍA Y CONSISTENCIA DOCUMENTAL |
| [26](26_PHASE1_FLAC_MONOLITHIC_INTEGRATION_REPORT.md) | FASE 1: INTEGRACIÓN, VALIDACIÓN Y ENDURECIMIENTO DE FLAC + MONOLITHIC DWP |
| [27](27_PHASE2_APP_INTEGRATION_REPORT.md) | FASE 2: INTEGRACIÓN REAL DE MONOLITHIC DWP EN EL FLUJO DE APLICACIÓN |
| [28](28_PHASE2_1_FLOAT32_DWP_AUDIT.md) | FASE 2.1: AUDITORÍA FORENSE DE FLOAT32 Y REPRESENTACIÓN DE AUDIO EN DWP |

## Sampler: motor de audio, efectos y síntesis

| N.º | Documento |
|---|---|
| [64](64_ATTACK_FADE_ANTI_CLICK.md) | "Clic" al pulsar una tecla: faltaba la rampa de ataque (anti-clic simétrico) |
| [65](65_RELEASE_CLICK_REAL_ROOT_CAUSE_DRAIN_FIX.md) | El clic seguía: causa raíz real en el release (no solo faltaba el ataque) |
| [66](66_PROFESSIONAL_SAMPLER_FX_ENGINE.md) | Motor de mezcla profesional + bus de efectos del sampler |
| [70](70_FX_PREMIUM_DSP_AND_PRESETS.md) | Módulos de efectos: DSP de calidad comercial y presets |
| [71](71_VOICE_SYNTHESIS.md) | Síntesis por voz del sampler (muestra, filtro, envolventes, LFO) |

## Interfaz de usuario

| N.º | Documento |
|---|---|
| [29](29_PIANO_KEYS_UI_AND_STABILITY_AUDIT.md) | TECLAS DE PIANO SIN COLOR POR OCTAVA + AUDITORÍA DE ESTABILIDAD DE LA APP |
| [30](30_OCTAVE_MARK_LATERAL_CORRECTION.md) | CORRECCIÓN: MARCA DE OCTAVA AL BORDE LATERAL IZQUIERDO + ETIQUETAS CENTRADAS |
| [31](31_SAMPLER_PANEL_PLACEHOLDER.md) | PANEL DEL SAMPLER (PLACEHOLDER PROVISIONAL): BOTÓN + VENTANA DESPLEGABLE |
| [32](32_CI_FIX_VECTOR_XML_COMMENT.md) | CORRECCIÓN DE CI: XML INVÁLIDO EN `ic_triangle_up.xml` (BUILD FAILED) |
| [33](33_TOGGLE_ICON_ONLY_NO_HEADER.md) | CORRECCIÓN: SOLO EL ÍCONO DE LA FLECHA, SIN CABECERA ENVOLVENTE |
| [34](34_BUILD_VERSION_DIAGNOSIS.md) | DIAGNÓSTICO: CONFUSIÓN DE BUILD VIEJA VS NUEVA + CORRECCIÓN DE VERSIÓN |
| [35](35_TOGGLE_ICON_56DP.md) | ÍCONO DE TOGGLE MÁS GRANDE (32dp -> 56dp) + BUMP DE VERSIÓN |
| [36](36_TOGGLE_ICON_NO_EXTRA_HEIGHT.md) | CORRECCIÓN DE FONDO: EL ÍCONO NO RESERVA ALTURA, FLOTA SOBRE EL FOOTER |
| [37](37_TOGGLE_ICON_FLUSH_AND_AUTOHIDE.md) | FLECHA PEGADA AL FOOTER + AUTO-OCULTADO CON EL PANEL ABIERTO |
| [38](38_EFFECTS_PANEL_TOUCH_BLOCK.md) | EL PANEL DEL SAMPLER YA NO TRASPASA EL TOQUE AL TECLADO/LISTA |
| [39](39_HELD_NOTE_AND_OCTAVE_COLOR_FIX.md) | Nota sostenida real (audio + luz) y coloreado de tecla activa por octava |
| [40](40_REMOVE_PREVIEW_MENU_AND_ROW_DIVIDER.md) | Eliminación del menú "Preview" y separador entre filas |
| [41](41_SAMPLE_MIXER_STRIP.md) | Mini-mezclador por muestra: Mute, Solo, Pan y Volumen |
| [42](42_MIXER_STRIP_LEFT_SIDE_PREMIUM_REDESIGN.md) | Mezclador al lado izquierdo (junto al nombre) + rediseño premium |
| [43](43_NAME_COLUMN_ALIGNMENT_AND_DIVIDER.md) | Columna de nombre calculada (no adivinada) + separador vertical nombre/mezclador |
| [44](44_SCROLL_LAG_DYNAMIC_SHADOWS_TO_SOFT_GLOW.md) | Lag/retardo real al scrollear la lista: sombras dinámicas -> halo dibujado a mano |
| [45](45_SCROLL_LAG_RECOMPOSITION_AUDIT_STRONG_SKIPPING.md) | Lag persistente al scrollear tras el Doc/44: auditoría de recomposición y Strong Skipping Mode |

## Rendimiento, Baseline Profile y CI

| N.º | Documento |
|---|---|
| [46](46_KOTLIN_2_K2_MIGRATION_AND_BASELINE_PROFILE_SCAFFOLDING.md) | Migración a Kotlin 2.0/K2 + andamiaje de Baseline Profiles |
| [47](47_BASELINE_PROFILE_MODULE.md) | Módulo `:baselineprofile`: generación real del Baseline Profile |
| [48](48_BASELINE_PROFILE_FIRST_RUN_429_RATE_LIMIT_FIX.md) | Primer intento real del workflow de Baseline Profile: 429 de Maven Central, no una versión mal adivinada |
| [49](49_BASELINE_PROFILE_FIRST_SUCCESS_ARTIFACT_PATH_FIX.md) | Baseline Profile: primer run exitoso y corrección de la ruta del artefacto |
| [50](50_BASELINE_PROFILE_RUN3_ARTIFACT_PATH_DIAGNOSTIC.md) | Baseline Profile, run #3: generación OK, ruta del artefacto incorrecta |
| [51](51_BASELINE_PROFILE_RUN4_NO_TEST_OUTPUT_FOUND.md) | Baseline Profile, run #4: verde, pero sin evidencia de perfil generado por el test |
| [53](53_BASELINE_PROFILE_TEST_NEVER_RAN_RUNNER_FIX.md) | Baseline Profile: el test nunca se ejecutaba (ejecutor de pruebas equivocado) |
| [54](54_BASELINE_PROFILE_ATD_IMAGE_FIX.md) | Baseline Profile: "Unable to confirm activity launch completion" -> imagen ATD |
| [55](55_BASELINE_PROFILE_ATD_HANG_TIMEOUT_FIX.md) | Baseline Profile: el run #8 se colgó 45 minutos, no falló limpio |
| [56](56_BASELINE_PROFILE_REQUIRE64BIT_FIX.md) | Baseline Profile: cuelgue dentro de Gradle -> require64Bit |
| [57](57_BASELINE_PROFILE_REVERT_TO_ASOP_PLUS_DISABLE_ANIMATIONS.md) | Baseline Profile: se abandona la imagen ATD, vuelta a "aosp" + animaciones en 0 |
| [58](58_BASELINE_PROFILE_REACTIVECIRCUS_EMULATOR_RUNNER.md) | Baseline Profile: se abandona Gradle Managed Devices, se usa reactivecircus/android-emulator-runner |
| [59](59_CRITICAL_FIX_BROKEN_RELEASE_BUILD.md) | CRÍTICO: el Doc/58 rompió `Build APK` (el pipeline de release real) |
| [60](60_REPORT_FULLY_DRAWN_FIX.md) | Causa real encontrada: falta `reportFullyDrawn()`, no el renderizado del emulador |
| [61](61_PROFILEABLE_MANIFEST_FIX.md) | Causa real (con alta confianza): faltaba `<profileable>` en el manifiesto |
| [62](62_BASELINE_PROFILE_INVESTIGATION_CLOSED.md) | Cierre de la investigación del Baseline Profile |
| [67](67_BASELINE_PROFILE_STATUS_AND_LAZY_AUDIO_START.md) | Estado del Baseline Profile y arranque diferido del audio |
| [68](68_BASELINE_PROFILE_FIRST_REAL_PROFILE.md) | Primer Baseline Profile real instalado |
| [69](69_STARTUP_BENCHMARK_WITH_VS_WITHOUT_PROFILE.md) | Benchmark de arranque: sin perfil vs con Baseline Profile |

## Auditorías completas y correcciones críticas

| N.º | Documento |
|---|---|
| [52](52_FULL_CODE_AUDIT_AND_FIXES.md) | Auditoría completa del código real y correcciones |
| [63](63_CRITICAL_PRODUCTION_CRASH_LOCALLIFECYCLEOWNER.md) | CRÍTICO: crash real en producción por `collectAsStateWithLifecycle` |
