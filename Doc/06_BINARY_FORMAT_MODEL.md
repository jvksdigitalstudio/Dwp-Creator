# 06 — MODELO FÍSICO BINARIO DEL .dwp (verificado)

## Estructura de archivo completa

```
Offset 0                      Offset 90 (0x5A)                    EOF
│   MAGIC "DwPr" + preámbulo  │         Stream de bloques          │
│   (90 bytes, OPAQUE)        │   (tag/length/reserved/payload)*   │
└──────────────────────────────────────────────────────────────────┘
```

## Bloque genérico (nivel top y anidado — MISMA forma)

```
struct DwpBlock {
    u32 tag;        // little-endian
    u32 length;     // little-endian; longitud EXACTA del payload que sigue
    u32 reserved;   // little-endian; [BINARY] siempre 0 en el archivo de referencia (top-level)
    u8  payload[length];
};
```

`[BINARY]` No existen offsets absolutos en ningún punto del formato: todo es posicional/secuencial. Confirmado porque la tokenización recursiva, aplicada solo con longitudes relativas, reconstruye el 100% del archivo sin usar ningún puntero.

## Tags conocidos (nivel top)

| Tag (hex) | Repeticiones observadas | Contenido | Evidencia |
|---|---|---|---|
| `0x0066` | 1 | Nombre del instrumento (texto) | `[BINARY]` `"Instrument"` |
| `0x0067` | 1 | Ruta propia del `.dwp` (texto, doble backslash) | `[BINARY]` `"D:\\Instrument.dwp"` |
| `0x0068`–`0x006b` | 1 cada uno | Metadata opcional (autor/categoría/notas), vacía por defecto | `[BINARY]` todo-ceros en el archivo real, no imprimible |
| `0x006c` | **2** | Desconocido, ceros en el archivo real | `[BINARY]` confirmado duplicado |
| `0x006d` | **4** | Desconocido, ceros en el archivo real | `[BINARY]` confirmado duplicado |
| `0x006e` | 99 | Tabla fija de parámetros/knobs (13 bytes cada uno), independiente del nº de samples | `[BINARY]` longitud uniforme = 13 bytes, confirmado |
| `0x0003` | 48 (= nº de samples) | Contenedor anidado por sample | `[BINARY]`+`[CODE]` |
| `0x0002` | 1 | Terminador top-level | `[CODE]` (no se decodificó su payload) |

## Tags conocidos (anidados dentro de `0x0003`)

Ver tabla completa en `05_DWP_IMPLEMENTATION_AUDIT.md`. Resumen de los confirmados con certeza binaria:
- `0x01f4` (25 bytes): key range — **`byte[0]=rootKey, byte[1]=lowKey, byte[2]=highKey`** (corregido; ver `17_AUDIT_ERRATA.md` error E-01 — verificado contra las 48 muestras: `byte[0]` coincide con la nota propia de cada sample en 48/48 casos sin excepción, mientras que `byte[1]` solo coincide en 47/48, fallando exactamente en la muestra más grave, donde se extiende a 0 — patrón típico de extensión de **límite**, no de raíz). Bytes de velocidad/flags restantes (offsets 3-24) no decodificados individualmente — `[HYPOTHESIS]`.
- `0x01f5`: nombre de sample (texto).
- `0x01f6`: ruta completa del sample (texto, un solo backslash).
- `0x01f7` (40 bytes): formato de audio — offsets 0/8/12/16/36 confirmados (ver documento 05).
- `0x0004` (0 bytes): terminador de sample.

## Regla de oro para cualquier reingeniería futura

`[INFERENCE]` Como no hay offsets absolutos, **cualquier generador nuevo solo necesita**:
1. Construir cada payload correctamente (incluyendo, para contenedores, el payload = stream serializado de sus bloques hijos).
2. Calcular `length = payload.size` para cada bloque.
3. Concatenar en el orden correcto.

No existe una tabla de punteros que reparar, lo cual **simplifica enormemente** la futura inserción de un bloque de audio embebido (`0x0206` hipotético): basta con insertarlo como un bloque más en el payload del `0x0003` correspondiente, sin tocar ningún otro offset del archivo.

## Lo que este modelo NO resuelve todavía

`[UNKNOWN]` El preámbulo de 90 bytes se trata como opaco. Si en el futuro se necesita generar un `.dwp` **desde cero** (no partir de una plantilla existente), habrá que descifrar su contenido — hoy el proyecto no lo necesita porque siempre parte de un `.dwp` real como plantilla.
