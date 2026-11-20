# Rademacher Muelles · App Android del chófer

App nativa (Android 8.0+) que abre la vista del chófer del servidor de muelles (`/chofer`).

Qué añade respecto a usar el navegador:

- **Vibración y notificación** "¡ES SU TURNO!" o mensaje de oficina, también con la app en segundo plano.
- Pantalla siempre encendida mientras está abierta.
- Recuerda el código y el idioma del chófer.
- El botón Atrás no cierra la app (sigue escuchando el turno).

## Compilar

Lo hace GitHub Actions (`.github/workflows/rademacher-android.yml`): en cada pull request compila el APK
como comprobación; en `main` o con *Run workflow* publica una **Release** con el APK.

URL del servidor: variable de repositorio `RADEMACHER_SERVER_URL`. Si no existe, la app la pide en el
primer arranque (y permite cambiarla si no hay conexión).

Local (requiere Android SDK): `SERVER_URL=https://muelles.su-empresa.es ./gradlew assembleDebug`

## Limitación

Android puede suspender la app tras mucho tiempo en segundo plano (ahorro de batería). Para avisos 100 %
garantizados con el móvil bloqueado haría falta Firebase Cloud Messaging o SMS.
