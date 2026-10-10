# 59 — CRÍTICO: el Doc/58 rompió `Build APK` (el pipeline de release real)

## Qué pasó
Al cambiar de estrategia para el Baseline Profile (Doc/58), se agregó
`useConnectedDevices = true` dentro del bloque `baselineProfile { }` de
`app/build.gradle.kts` -- el módulo **consumidor**. Esa propiedad NO existe
en ese bloque (solo existe en el bloque `baselineProfile { }` del módulo
**productor**, `:baselineprofile`). Resultado real, visto en el log de
`Build APK`:

```
Line 106: useConnectedDevices = true
          ^ Unresolved reference: useConnectedDevices
```

Esto es un error de compilación del propio script de Gradle -- tumbó
**todo** `:app:testDebugUnitTest` y `:app:assembleRelease`, es decir, el
pipeline que genera el APK real que el usuario instala en su tablet.

## Por qué pasó (auditoría honesta del propio error)
Al mover `useConnectedDevices` de `false` a `true` para reflejar el cambio
de mecanismo del Doc/58, se aplicó el mismo cambio en los DOS bloques
`baselineProfile { }` del proyecto (uno por módulo) sin verificar que la
propiedad existe en ambos. Solo existe en el del módulo productor
(`baselineprofile/build.gradle.kts`), donde ya estaba correcta desde antes
del Doc/58 y sigue estándolo.

## Corrección
`app/build.gradle.kts`: se retira `useConnectedDevices = true` de su
bloque `baselineProfile { }` -- ese bloque vuelve a tener solo
`automaticGenerationDuringBuild = false`, exactamente como estaba antes del
Doc/58 (que es, de nuevo, lo único que ese bloque necesita: desacoplar la
generación del perfil del build de release normal).
`baselineprofile/build.gradle.kts` no se tocó -- su `useConnectedDevices =
true` es válido ahí y sigue como se dejó en el Doc/58.

## Lección para el resto de esta sesión
Cualquier cambio en `app/build.gradle.kts` -- el módulo que sí determina el
APK real -- se revisa con el estándar más alto posible, sin importar que
venga "de paso" arreglando otra cosa (el Baseline Profile, que es un
extra). Un módulo aparte (`:baselineprofile`) existe precisamente para que
sus experimentos no toquen `:app` -- este error ocurrió por editar
`:app` directamente en vez de limitar el cambio al módulo aislado.

## Siguiente paso
Subir este cambio de inmediato y confirmar en Actions que `Build APK`
vuelve a compilar verde. Una vez confirmado eso, se puede retomar
`Generate Baseline Profile` (Doc/58) por separado -- sigue siendo un
workflow aparte que no afecta al APK real.
