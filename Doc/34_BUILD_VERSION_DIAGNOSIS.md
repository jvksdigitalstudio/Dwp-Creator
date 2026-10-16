# 34 — DIAGNÓSTICO: CONFUSIÓN DE BUILD VIEJA VS NUEVA + CORRECCIÓN DE VERSIÓN

**Fecha:** 2026-09-25
**Versión:** `0.6.1-effects-toggle-icon-only` (antes: `0.6.0-premium-piano-keys`, `versionCode` 6 -> 7)
**Punto de partida:** `Doc/33` (ícono suelto, sin `Row`/`IconButton` envolvente).
**Origen:** el usuario siguió viendo, en capturas repetidas, la misma "barra" alrededor de la flecha después de que `Doc/33` ya la había quitado del código.

---

## 1. Qué se investigó

Ante la insistencia del usuario de que "sigue el header", se releyó `EffectsPanel.kt` completo línea por línea (no solo se asumió que el fix de `Doc/33` seguía en pie): `EffectsPanelToggleButton` (líneas 71-92 del archivo) es, en efecto, únicamente un `Icon(...)` con `Modifier.clickable` -- sin `Row`, sin `IconButton`, sin `Surface`, exactamente como describe `Doc/33`. El único `Row`/`IconButton` que queda en el archivo pertenece a la cabecera **interna** del panel desplegable (el rótulo "SAMPLER"), que el usuario no cuestionó.

Con el código descartado como causa, se revisó `app/build.gradle.kts` y se encontró la causa real: **`versionCode`/`versionName` no se habían tocado en ninguna de las seis correcciones anteriores** (`Doc/29` a `Doc/33`). Android no tiene ninguna señal visible para el usuario de que una build es distinta de la anterior si su número de versión nunca cambia -- y sin esa señal, no hay forma de que el usuario (que compila e instala por su cuenta, como se acordó desde el inicio de este proyecto) confirme si de verdad está probando el APK corregido o uno de una compilación previa.

## 2. Corrección aplicada

`app/build.gradle.kts`:
```
versionCode = 6                                  ->  versionCode = 7
versionName = "0.6.0-premium-piano-keys"         ->  versionName = "0.6.1-effects-toggle-icon-only"
```

Ningún otro archivo se tocó en esta pasada -- en particular, **`EffectsPanel.kt` y `MainScreen.kt` quedan exactamente como los dejó `Doc/33`** (el usuario fue explícito: "no me quites el header de más abajo", refiriéndose a `DwpStatusBar`; tampoco se tocó esa pantalla ni ninguna otra, solo el número de versión de la app, que no es visible en ninguna pantalla de la UI, solo en Ajustes del sistema).

## 3. Cómo confirmar, desde el propio teléfono, que se está probando la build correcta

1. Desinstalar completamente la app anterior antes de instalar el nuevo APK (no basta con "instalar encima" si hubiera cualquier diferencia de firma o si el gestor de paquetes decide no reemplazar el APK).
2. Instalar el APK compilado a partir de este zip.
3. Ir a **Ajustes del sistema -> Apps -> DwpCreator -> Información de la app / Versión de la app** (el texto exacto varía por fabricante de Android) y confirmar que dice `0.6.1-effects-toggle-icon-only`.
4. Si dice `0.6.0-premium-piano-keys` (o cualquier versión anterior) después de instalar este zip, el problema es de instalación/caché del dispositivo, no de código -- y hay que desinstalar y reinstalar de nuevo antes de volver a juzgar la interfaz.

Esto es, deliberadamente, la única señal de verificación añadida -- no se agregó nada visible dentro de las pantallas de la app (ni en `DwpStatusBar` ni en ningún otro sitio), respetando la instrucción explícita del usuario de no tocar esa parte.

## 4. Por qué no se tocó nada del código de UI en esta pasada

El usuario fue explícito y específico: la flecha debe quedarse igual, y el header de más abajo (números/letras, `DwpStatusBar`) tampoco se toca. Dado que la revisión de código confirmó que `EffectsPanelToggleButton` ya cumple exactamente lo pedido desde `Doc/33`, **no había nada más que corregir en la UI** -- el único cambio real y necesario era el de versión, que es puramente de metadatos de build, no de interfaz.

## 5. Verificación realizada en esta pasada

- Relectura completa de `EffectsPanelToggleButton` en `EffectsPanel.kt`: confirmado, sin `Row`/`IconButton`/`Surface` envolvente, sin cambios respecto a `Doc/33`.
- `grep` de `versionCode`/`versionName` en `app/build.gradle.kts` antes y después del cambio, para confirmar que el único valor modificado es exactamente el esperado.
- Re-validación XML de los 5 archivos `.xml` del proyecto (repetida por disciplina, como en `Doc/33`): los 5 siguen dando `OK`.

**No realizado:** compilación real (sigue sin compilador Kotlin/Gradle en este entorno) -- pero este cambio en particular (dos literales de texto/número en un archivo Gradle, sin tocar Kotlin ni recursos) no tiene superficie de error de compilación razonable. **Conteo de tests sin cambios (244).**

## 6. Archivos tocados

Solo `app/build.gradle.kts` (dos líneas). Ningún archivo Kotlin ni XML.
