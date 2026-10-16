# 35 — ÍCONO DE TOGGLE MÁS GRANDE (32dp -> 56dp) + BUMP DE VERSIÓN

**Fecha:** 2026-09-25
**Versión:** `0.6.2-effects-toggle-icon-56dp` (`versionCode` 7 -> 8)
**Punto de partida:** `Doc/34` (`versionCode` 7, ícono a 32dp).

## Cambio

Único cambio de código: `EffectsPanelToggleIconSize` en `EffectsPanel.kt`, de `32.dp` a `56.dp` -- casi el doble, para que la diferencia sea inconfundible en pantalla sin lugar a ambigüedad. Nada más se tocó: sigue siendo solo el `Icon` con `Modifier.clickable`, sin `Row`/`IconButton`/`Surface` (`Doc/33`), centrado con los mismos modifiers de alineación en `MainScreen.kt` (`Doc/33`), sin tocar `DwpStatusBar`.

`versionCode`/`versionName` subidos de nuevo (7 -> 8, `"0.6.1-effects-toggle-icon-only"` -> `"0.6.2-effects-toggle-icon-56dp"`) para que este cambio también sea verificable desde **Ajustes -> Apps -> DwpCreator -> versión** tras una instalación limpia (desinstalar la build anterior primero).

## Verificación

Balance de paréntesis/llaves/corchetes de `EffectsPanel.kt`: `(` 56/`)` 56, `{` 9/`}` 9, `[` 3/`]` 3 -- correcto. Único valor cambiado confirmado con `grep`. Sin compilación real posible en este entorno (limitación de siempre); cambio de un solo literal numérico sin superficie de error razonable. **Conteo de tests sin cambios (244).**

## Archivos tocados

`ui/components/EffectsPanel.kt` (una constante), `app/build.gradle.kts` (versión).
