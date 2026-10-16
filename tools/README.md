# tools/ — verificaciones de repositorio

| Script | Qué hace | Cuándo |
|---|---|---|
| `check_architecture.py` | Hace cumplir las reglas R1–R4 (dependencias entre módulos, sin ciclos, un tipo por archivo, un paquete = un módulo) | CI (primer paso) y antes de cada commit: `python3 tools/check_architecture.py` |

Solo requiere Python 3.8+, sin dependencias.
