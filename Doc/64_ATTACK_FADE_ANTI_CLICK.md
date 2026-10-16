# 64 — "Clic" al pulsar una tecla: faltaba la rampa de ataque (anti-clic simétrico)

## Síntoma reportado
Al pulsar una tecla, la nota suena pero con "un pequeño clic que ensucia el
sonido" -- justo al empezar, no al soltar.

## Causa real (encontrada auditando `SamplePlayer.kt`)
El código ya tenía, documentado y deliberado, un fade-out corto
(`RELEASE_FADE_MS = 12`) precisamente para evitar un "clic/pop de
discontinuidad de forma de onda" **al soltar** la tecla. Pero al **pulsar**
no existía el equivalente: `startVoice` llamaba `track.play()` y escribía el
PCM crudo desde el frame 0 a ganancia plena de inmediato. Si el primer frame
real del archivo de la muestra no cae justo en un cruce por cero -- lo
normal en una muestra de instrumento real, casi nunca está editada para
empezar exactamente ahí --, ese salto instantáneo de silencio a esa
amplitud es una discontinuidad audible: el mismo tipo de clic que el
fade-out ya evitaba, pero sin protección en el otro extremo de la nota.

## Corrección
`SamplePlayer.kt`: nueva constante `ATTACK_FADE_MS = 3` (deliberadamente
mucho más corta que los 12 ms de salida -- suficiente para eliminar el
clic sin ablandar de forma audible el ataque/transiente real del
instrumento). `writeStereoChunk` ahora recibe la posición global del frame
(no relativa al trozo) y aplica una rampa lineal 0 -> 1 sobre los primeros
`attackFadeFrames`, multiplicada sobre las ganancias de pan/volumen ya
resueltas -- mismo mecanismo y mismo estilo de código que el fade-out de
salida, aplicado de forma simétrica a la entrada.

## Por qué 3 ms y no los mismos 12 ms del release
El release puede ser más largo porque corta una nota que ya estaba sonando
-- el oído no espera un corte exacto ahí. El ataque, en cambio, es la parte
más perceptible del timbre de un instrumento (el "golpe" real de la tecla);
una rampa larga ahí sí se notaría como un instrumento "más suave" de lo que
es. 3 ms es el mínimo real para matar la discontinuidad sin tocar el
carácter del sonido.

## No afecta
No cambia el formato del `.dwp` exportado ni la muestra original cacheada
-- es puro procesamiento de reproducción en tiempo real, igual que el
fade-out ya existente. No se tocó ninguna otra parte del pipeline de audio.

## Verificación pendiente (requiere el dispositivo real)
Instalar el APK y tocar varias teclas distintas, sueltas y en acorde,
comparando contra la sensación anterior -- debería percibirse limpio, sin
el clic al inicio de la nota.
