# 65 — El clic seguía: causa raíz real en el release (no solo faltaba el ataque)

## Por qué el Doc/64 no bastó
El Doc/64 agregó correctamente una rampa de ataque de 3 ms -- necesaria,
pero no era la causa principal del clic que el usuario seguía escuchando al
**soltar** la tecla (y, por el mecanismo de reciclaje de voces, también
contaminando la sensación de la nota siguiente al **pulsarla**). Auditoría
más profunda de `writeReleaseFadeTail`, pedida explícitamente por el
usuario ("audita bien todo el piano"), encontró el bug real.

## El bug real
```kotlin
track.write(out, 0, out.size, AudioTrack.WRITE_BLOCKING)
...
track.pause()
track.flush()
```
`AudioTrack.write()` en `MODE_STREAM` con `WRITE_BLOCKING` devuelve el
control en cuanto los bytes **entran al buffer interno** del track -- NO
cuando el hardware ya los reprodujo. El código pausaba y vaciaba (`flush()`)
el track **inmediatamente después** de escribir el fade-out, antes de que
el hardware llegara a sonarlo. Resultado real:
1. El fade-out que tanto cuidado puso el propio código en calcular **nunca
   llegaba a escucharse** -- se descartaba del buffer antes de sonar.
2. `pause()`/`flush()` sobre una posición de onda que no es un cruce por
   cero produce, por sí mismo, un "pop" -- el clic real que se escuchaba.
3. Esa misma voz se reciclaba de inmediato para la siguiente nota, así que
   el pop de corte y el ataque de la nota nueva quedaban pegados --
   percibido como "clic al pulsar y al soltar", aunque el origen real
   estaba solo en el release.

Lo llamativo: el código YA resolvía esto correctamente para el camino de
"la nota termina sola" (`awaitPlaybackDrained` antes de reciclar, con un
comentario explícito sobre por qué hace falta esperar) -- pero esa misma
lógica nunca se aplicó al camino de "se soltó la tecla antes de que
terminara", que es el camino que se ejecuta en el uso normal del
instrumento (soltar teclas es la norma, no la excepción).

## Corrección
`writeReleaseFadeTail` ahora recibe la posición global del frame donde
arranca la cola y el sample rate real de la voz, y -- antes de
`pause()`/`flush()` -- llama a `awaitPlaybackDrained(track, globalFrameOffset
+ framesEscritos, timeout)`, el mismo mecanismo exacto que ya usa el camino
del final natural, con el mismo cálculo de timeout basado en el tamaño real
del buffer de audio del dispositivo. Ahora el fade-out se espera a que
*realmente suene* antes de cortar la voz.

## Por qué esta vez sí
A diferencia del Doc/64 (una pieza real pero parcial), este es un bug
estructural de sincronización entre la API de `AudioTrack` y la lógica de
reciclaje de voces -- encaja exactamente con el síntoma reportado
("clic... al dar clic y al soltar... sonido cortante") y con el hecho de
que persistiera después de arreglar solo el ataque.

## No afecta
Mismo formato de exportación, mismo pipeline DWP/FLAC -- cambio aislado a
la reproducción en vivo dentro de `SamplePlayer`.

## Verificación pendiente (requiere el dispositivo real)
Instalar el APK y tocar teclas sueltas, en acorde, y soltándolas rápido --
el corte al soltar debería sentirse suave, no cortante.
