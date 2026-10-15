#!/usr/bin/env python3
"""Verifica las reglas de arquitectura del proyecto (se ejecuta en CI antes de compilar).

Reglas:
  R1  Un módulo solo importa símbolos de sí mismo o de módulos a los que declara depender
      (`project(":x")` en su build.gradle.kts, transitivamente a través de `api(...)`).
  R2  El grafo de dependencias entre módulos no tiene ciclos.
  R3  Un archivo de producción declara como máximo UN tipo público/internal de nivel superior
      (class / interface / object) y no lo mezcla con funciones o propiedades públicas sueltas:
      una responsabilidad por archivo.
  R4  Los paquetes de un módulo no se solapan con los de otro (un paquete pertenece a un solo módulo).

Uso:  python3 tools/check_architecture.py [--root RUTA]
Código de salida: 0 si todo cumple, 1 si hay incumplimientos.
"""
import argparse
import collections
import os
import re
import sys

BASE = "com.jvk.dwpcreator"
PROJECT_DEP = re.compile(r'\b(api|implementation)\s*\(\s*project\s*\(\s*"?:([\w:-]+)"?\s*\)\s*\)')
PACKAGE = re.compile(r"^package\s+([\w.]+)", re.M)
IMPORT = re.compile(r"^import\s+([\w.]+)", re.M)
TOP_TYPE = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|internal)\s+)?"
    r"(?:(?:data|sealed|enum|abstract|open|inline|value|annotation|fun)\s+)*(?:class|object|interface)\s+(\w+)"
)
TOP_MEMBER = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|internal)\s+)?"
    r"(?:(?:const|inline|tailrec|operator|infix|suspend)\s+)*(?:fun|val|var)\s+"
)


def discover_modules(root):
    """Módulos = carpetas con build.gradle.kts y src/main, a cualquier profundidad (p. ej. `core/codec`).

    El identificador de un módulo es su ruta relativa (`core/codec`), que se corresponde con su ruta de
    Gradle (`:core:codec`).
    """
    modules = {}
    for dp, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in ("build", "src", ".git", ".gradle", "node_modules")]
        rel = os.path.relpath(dp, root).replace(os.sep, "/")
        if rel in (".", "baselineprofile") or "build.gradle.kts" not in files:
            continue
        if not os.path.isdir(os.path.join(dp, "src", "main")):
            continue
        text = open(os.path.join(dp, "build.gradle.kts"), encoding="utf8").read()
        deps = [(kind, path.strip(":").replace(":", "/")) for kind, path in PROJECT_DEP.findall(text)]
        modules[rel] = {"dir": dp, "deps": deps}
    return dict(sorted(modules.items()))


def kotlin_files(module_dir, kind):
    base = os.path.join(module_dir, "src", kind)
    for dp, _, fs in os.walk(base):
        for f in sorted(fs):
            if f.endswith(".kt"):
                yield os.path.join(dp, f)


def visible_modules(modules, name):
    """Módulos visibles desde `name`: dependencias directas + lo que estas exponen con `api`."""
    seen, stack = set(), [dep for _, dep in modules[name]["deps"]]
    while stack:
        dep = stack.pop()
        if dep in seen or dep not in modules:
            continue
        seen.add(dep)
        stack.extend(d for kind, d in modules[dep]["deps"] if kind == "api")
    return seen


def find_cycles(modules):
    graph = {m: [d for _, d in info["deps"]] for m, info in modules.items()}
    state, cycles = {}, []

    def visit(node, path):
        state[node] = 1
        for nxt in graph.get(node, []):
            if state.get(nxt) == 1:
                cycles.append(path[path.index(nxt):] + [nxt] if nxt in path else [node, nxt])
            elif nxt not in state:
                visit(nxt, path + [nxt])
        state[node] = 2

    for m in graph:
        if m not in state:
            visit(m, [m])
    return cycles


def strip_comments(src):
    src = re.sub(r"/\*.*?\*/", lambda m: "\n" * m.group(0).count("\n"), src, flags=re.S)
    return re.sub(r"//[^\n]*", "", src)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    root = os.path.abspath(ap.parse_args().root)
    modules = discover_modules(root)
    errors = []

    # R2
    for cycle in find_cycles(modules):
        errors.append("R2 ciclo entre módulos: " + " -> ".join(":" + c.replace("/", ":") for c in cycle))

    # R4 + índice paquete -> módulo
    owner = {}
    for name, info in modules.items():
        for path in kotlin_files(info["dir"], "main"):
            pkg = PACKAGE.search(open(path, encoding="utf8").read()).group(1)
            owner.setdefault(pkg, set()).add(name)
    for pkg, mods in sorted(owner.items()):
        if len(mods) > 1:
            errors.append("R4 el paquete %s está repartido entre los módulos %s" % (pkg, [":" + m.replace("/", ":") for m in sorted(mods)]))

    def module_for_import(fqn):
        parts = fqn.split(".")
        for k in range(len(parts) - 1, 0, -1):
            mods = owner.get(".".join(parts[:k]))
            if mods:
                return next(iter(mods))
        return None

    files = types = 0
    for name, info in modules.items():
        allowed = {name} | visible_modules(modules, name)
        for kind in ("main", "test"):
            for path in kotlin_files(info["dir"], kind):
                src = open(path, encoding="utf8").read()
                rel = os.path.relpath(path, root)
                # R1
                for fqn in IMPORT.findall(src):
                    if not fqn.startswith(BASE + "."):
                        continue
                    target = module_for_import(fqn)
                    if target and target not in allowed:
                        errors.append("R1 %s: importa %s (módulo :%s) sin dependencia declarada desde :%s" % (rel, fqn, target.replace("/", ":"), name.replace("/", ":")))
                # R3 (solo producción)
                if kind == "main":
                    files += 1
                    code = strip_comments(src)
                    tops = [m.group(1) for line in code.split("\n") if (m := TOP_TYPE.match(line))]
                    members = [line for line in code.split("\n") if TOP_MEMBER.match(line) and not TOP_TYPE.match(line)]
                    types += len(tops)
                    if len(tops) > 1:
                        errors.append("R3 %s: declara %d tipos de nivel superior (%s); un tipo por archivo" % (rel, len(tops), ", ".join(tops)))
                    elif len(tops) == 1 and members:
                        errors.append("R3 %s: mezcla el tipo %s con %d declaraciones sueltas públicas" % (rel, tops[0], len(members)))

    print("Módulos: %s" % ", ".join(":" + m.replace("/", ":") for m in sorted(modules)))
    print("Archivos de producción revisados: %d (%d tipos de nivel superior)" % (files, types))
    if errors:
        print("\nINCUMPLIMIENTOS (%d):" % len(errors))
        for e in errors:
            print("  - " + e)
        return 1
    print("Arquitectura OK: dependencias entre módulos, sin ciclos y un tipo por archivo.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
