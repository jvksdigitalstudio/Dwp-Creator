# platform/ — integraciones con Android

Módulos de biblioteca Android que conectan `core/` con el dispositivo (audio y MIDI). Aquí no hay lógica de
negocio: solo adaptadores a APIs de Android que implementan contratos definidos en `core/`.

| Módulo | Responsabilidad |
|---|---|
| [`sampler-android`](sampler-android/README.md) | Salida de audio (`AudioTrack`), `SamplePlayer`, persistencia de ajustes |
| [`midi`](midi/README.md) | Entrada MIDI de Android y decodificación de mensajes |
