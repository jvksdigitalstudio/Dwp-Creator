# Dwp Creator

App Android 100% nativa (Kotlin + Jetpack Compose) para renombrar y reempaquetar
instrumentos DirectWave (.dwp) creados en FL Studio Desktop, listos para FL Studio Mobile.

Este commit es el **Paso 1 de 7**: esqueleto del proyecto. Solo valida que el
pipeline de build (Gradle + GitHub Actions) genera un APK instalable.
Todavía no incluye el motor DWP, ni el motor de audio, ni MIDI — eso viene
en los siguientes pasos.

## Subir el proyecto desde Termux (tablet)

```bash
pkg install git -y
cd ruta/donde/tengas/el/proyecto/DwpCreator

git init
git add .
git commit -m "Paso 1: esqueleto del proyecto (build pipeline)"

git remote add origin https://github.com/TU_USUARIO/TU_REPO.git
git branch -M main
git push -u origin main
```

## Descargar el APK

1. Ve a tu repo en GitHub → pestaña **Actions**.
2. Entra al workflow "Build APK" que se ejecuta automáticamente al hacer push.
3. Cuando termine (ícono verde ✅), abre el run y baja hasta **Artifacts**.
4. Descarga `DwpCreator-debug-apk`, es un .zip que contiene `app-debug.apk`.
5. Instálalo en tu celular/tablet (activa "orígenes desconocidos" si te lo pide).

Si ves ✅ verde y el APK abre mostrando "DWP CREATOR — build pipeline OK",
el paso 1 quedó confirmado y seguimos con el motor DWP.
