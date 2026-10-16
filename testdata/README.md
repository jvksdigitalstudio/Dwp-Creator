# testdata/ — archivos reales para las pruebas

| Archivo | Descripción | Lo usan |
|---|---|---|
| `Instrument.dwp` | Instrumento real exportado desde FL Studio Desktop (48 muestras cromáticas, C3–D6, velocity 127, 47 307 bytes): la verdad de campo contra la que se verifica el formato DWP | `:core:dwp`, `:app` |

No lo modifiques: varias pruebas asertan sobre su contenido exacto. Se añade como recurso de pruebas desde
cada módulo con `rootProject.file("testdata")`.
