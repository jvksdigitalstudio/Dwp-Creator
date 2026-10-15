# 31 — PANEL DEL SAMPLER (PLACEHOLDER PROVISIONAL): BOTÓN + VENTANA DESPLEGABLE

**Fecha:** 2026-09-25
**Versión:** `0.4.2-sampler-panel-placeholder`
**Punto de partida:** estado de `Doc/30` (marca de octava lateral corregida).
**Origen del pedido:** feedback directo del usuario, con capturas anotadas a mano, pidiendo un punto de entrada para futuras opciones del sampler (reverb, delay, etc.), explícitamente marcado como **provisional**.

> **Limitación de verificación (leer primero).** Igual que en `Doc/25`–`Doc/30`: este entorno no tiene compilador Kotlin, Gradle ni Android SDK. Verificado por lectura de código real, comprobación de balance de llaves/paréntesis, y contraste de cada símbolo de Compose/Material3 nuevo contra la versión del BOM del proyecto (`compose-bom:2024.05.00`). **No compilado ni ejecutado en esta pasada.**

---

## 1. Pedido exacto

Encima de la barra inferior (justo arriba de "© 2026 by YeiViKas Digital Company"), un icono que al tocarlo despliegue una ventana **hacia arriba**, cubriendo "hasta la mitad, un poquito menos" de la zona de teclado + lista de muestras, **superpuesta** sobre ambos (no empujándolos). Por ahora la ventana va **vacía** -- ahí irán a futuro los controles del sampler (reverb, delay, etc.). Encima de esa ventana, dentro de ella, debe existir además una opción para ocultarla momentáneamente. Explícitamente pedido como "solo provisional, ya luego se perfecciona". El usuario proporcionó el icono a usar: un SVG "triangle-up" (triángulo sólido apuntando hacia arriba, `viewBox 0 0 500 500`).

## 2. Qué se construyó

### 2.1 Icono (`res/drawable/ic_triangle_up.xml`)
Vector drawable de Android, geometría idéntica al SVG entregado (mismo `viewBox`/mismos puntos del triángulo: `4.5,372.5 → 494.5,372.5 → 249.5,127.5`). El `fillColor` del `<path>` se deja en blanco puro a propósito: `Icon(...)` de Compose tiñe cualquier vector con el color que se le pase en `tint`, así que el color real (`TextDim` en reposo, `NeonPurple` con el panel abierto) lo decide cada composable que lo usa, no el asset -- mismo criterio ya aplicado en el resto de la paleta del proyecto (`ui/theme/Color.kt`), donde ningún elemento lleva un color "quemado" fuera de sus constantes centrales.

### 2.2 `ui/components/EffectsPanel.kt` (nuevo)
Dos composables:

- **`EffectsPanelToggleButton`**: el botón/icono pedido "justo encima de © 2026...". Reutiliza `ic_triangle_up` y lo **rota 180°** cuando el panel está abierto (con `animateFloatAsState`, transición suave) -- la misma flecha comunica "desplegar hacia arriba" cerrada y "el panel está arriba, toca para cerrar" abierta, sin necesitar un segundo dibujo. El color también anima entre `TextDim` (reposo) y `NeonPurple` (abierto).
- **`EffectsPanel`**: el propio panel, con `AnimatedVisibility` (desliza desde abajo + fade, 220 ms al abrir / 180 ms al cerrar) y una altura de `fillMaxHeight(0.45f)` sobre el contenedor donde se coloque -- "hasta la mitad, un poquito menos" tal como se pidió. Contenido:
  - Cabecera con el rótulo "SAMPLER" y **un segundo botón para ocultar el panel**, con el mismo icono rotado 180° -- este es el control que el usuario pidió explícitamente que fuera "encima de esta ventana" (es decir, dentro de su propia cabecera), deliberadamente redundante con el botón externo de `EffectsPanelToggleButton` para que haya siempre una forma de cerrarlo visible sin buscarla.
  - Un divisor sutil (`HorizontalDivider`, Material3 -- disponible desde la versión del BOM del proyecto).
  - El cuerpo: un único `Text` centrado, `"Reverb, Delay y más efectos -- próximamente"`. **Esto es intencionalmente el único contenido** -- el usuario pidió expresamente que, por ahora, la ventana fuera vacía.

### 2.3 `ui/screens/MainScreen.kt` (integración)
- Nuevo estado `effectsPanelExpanded` (`remember { mutableStateOf(false) }`), con el mismo alcance/vida que `listState`: vive solo mientras la pantalla `Loaded` está compuesta. **Deliberadamente no se sube al `ViewModel`** todavía -- no hay nada que persistir mientras el panel esté vacío, y el usuario pidió expresamente un andamiaje provisional, no una función terminada.
- `EffectsPanel` se coloca **dentro del mismo `Box` que la `LazyColumn` y el overlay de gestos del teclado** (`Modifier.align(Alignment.BottomCenter)`), y **último** en el orden de composición de ese `Box` -- por eso queda dibujado por encima de ambos y recibe el toque primero mientras está desplegado, sin robarle superficie de toque al teclado cuando está cerrado (`AnimatedVisibility` no ocupa espacio ni intercepta toques cuando `visible = false`).
- `EffectsPanelToggleButton` se coloca **fuera** de ese `Box`, justo antes de `DwpStatusBar` -- exactamente "encima de © 2026...", y **solo quando no hay una exportación en curso** (mismo criterio que ya usa `DwpTopBar` con `isBusy` para LOAD/RENOMBRAR/EXPORT en `Doc/29` §H-07): si `exportProgress != null`, la rama que sustituye la lista por `ExportProgressOverlay` no dibuja el botón.

## 3. Qué NO se hizo (y por qué, para que quede explícito)

- **Ningún control de audio real** (reverb, delay, ni ningún DSP): el pedido fue expresamente "por el momento vacía". Añadir controles no pedidos habría sido inventar alcance no solicitado, además de tocar la capa de audio (`SamplePlayer`/`AudioVoicePool`) sin especificación real de qué efecto ni con qué parámetros -- exactamente el tipo de cosa que el propio historial de este proyecto (`Doc/29` §3, "qué no se tocó y por qué") trata con cuidado.
- **Sin persistencia de si el panel estaba abierto** entre sesiones o recomposiciones más allá de la pantalla actual: al ser un placeholder vacío, no hay nada útil que recordar todavía.
- **Sin gesto de arrastre para redimensionar el panel** (solo abrir/cerrar con el botón): el pedido fue "una opción... que al dar click se despliegue", no un panel arrastrable; añadir eso ahora sería sobre-construir sobre un placeholder que "ya luego se perfecciona" por indicación explícita del propio usuario.

## 4. Archivos tocados / nuevos

**Nuevo:** `res/drawable/ic_triangle_up.xml`, `ui/components/EffectsPanel.kt`.
**Modificado:** `ui/screens/MainScreen.kt` (nuevo estado local + integración del botón y el panel).
**Sin cambios:** todo lo demás -- en particular `DwpStatusBar`, `SampleRow`, `PianoKeyBadge` y toda la capa de audio/MIDI/DWP.

## 5. Verificación realizada en esta pasada

- Balance de paréntesis/llaves/corchetes: `EffectsPanel.kt` → `(` 57/`)` 57, `{` 11/`}` 11, `[` 3/`]` 3. `MainScreen.kt` (con la integración) → `(` 115/`)` 115, `{` 47/`}` 47, `[` 7/`]` 7. Ambos correctos.
- `HorizontalDivider` confirmado disponible en Material3 para `compose-bom:2024.05.00` (introducido en Material3 1.2.0, incluido en esa versión del BOM; el proyecto no usaba ni `Divider` ni `HorizontalDivider` antes, así que se verificó el símbolo contra la versión real del catálogo de dependencias en vez de asumirlo).
- Todos los imports de `EffectsPanel.kt` verificados uno a uno como efectivamente usados en el cuerpo del archivo (ninguno sobrante).
- Namespace de recursos confirmado (`namespace = "com.jvk.dwpcreator"` en `app/build.gradle.kts`) antes de escribir `import com.jvk.dwpcreator.R`.
- Revisado que `AnimatedVisibility` con `visible = false` no reserva espacio de layout ni intercepta toques -- así que con el panel cerrado, el overlay de gestos del teclado (`PianoKeyGestureOverlay`, `Doc/29` §H-04) sigue recibiendo todos los toques exactamente igual que antes de este cambio.

**No realizado:** compilación, ejecución de tests, prueba en dispositivo. Cambio puramente de UI (un nuevo composable de presentación + un booleano de estado local), sin lógica de negocio nueva testeable en JVM -- **conteo de tests sin cambios (244)**.

## 6. Prueba en dispositivo recomendada

20. El icono aparece centrado, justo encima del texto "© 2026 by YeiViKas Digital Company".
21. Al tocarlo, una ventana morada se despliega desde abajo, cubriendo un poco menos de la mitad del área de teclado + lista, con el texto "SAMPLER" y el mensaje de placeholder.
22. La ventana queda **encima** del teclado y la lista (no los reduce ni los empuja) -- las filas siguen en su sitio detrás de ella.
23. Tocar el botón dentro de la cabecera del panel (a la derecha de "SAMPLER") lo cierra. Tocar de nuevo el icono de abajo también lo cierra -- ambos caminos funcionan.
24. Con el panel cerrado, tocar/deslizar sobre las teclas del piano sigue sonando con normalidad (el overlay de gestos no quedó bloqueado por el nuevo composable).
25. Durante una exportación, el icono de abajo desaparece (igual que LOAD/RENOMBRAR/EXPORT quedan desactivados).
