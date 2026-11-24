# Nexus para Android

Red social del conocimiento para Android 8.0 o superior. Funciona sin conexión y sin cuentas: todos
los datos se guardan en el móvil.

Combina dos versiones anteriores:

| Pestaña | De dónde viene | Qué hace |
|---|---|---|
| **Comunidad** | App WebView del zip (`assets/index.html`) | Preguntas y respuestas con votos, respuestas aceptadas, reputación e insignias, búsqueda, historia y proyectos |
| **Mis notas** | App Kotlin + Compose | Notas con categoría, etiquetas y favoritas; enlaces `[[Título]]` entre notas y lista «Mencionada en»; 23 conceptos de logística de ejemplo |
| **Repaso** | App Kotlin + Compose | Fichas (título = pregunta, contenido = respuesta) con nivel de dominio de 0 a 3; sale primero lo menos dominado |

## Descargar el APK

La compilación la hace GitHub Actions con el workflow `.github/workflows/nexus-android.yml`.

- **Desde una Release** (cada cambio en `main`): en la página del repositorio, **Releases** →
  `Nexus 2.0.N` → descarga `Nexus-2.0.N.apk` desde el móvil.
- **Desde un pull request o una ejecución manual**: pestaña **Actions** → ejecución «APK Nexus» →
  apartado **Artifacts** → `nexus-apk` (es un .zip con el APK dentro; descomprímelo).
- Ejecución manual: **Actions → APK Nexus → Run workflow**.

Al abrir el APK, Android pedirá permitir **instalar apps de origen desconocido**: actívalo solo
para esta instalación y pulsa **Instalar**.

## Actualizar sin desinstalar (opcional)

Sin clave propia, cada APK se firma con una clave de depuración distinta y, para instalar una
versión nueva, hay que desinstalar la anterior (se pierden los datos). Para evitarlo, crea una clave
una vez (necesitas Java o Android Studio):

```bash
keytool -genkeypair -v -keystore nexus.jks -alias nexus -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 nexus.jks > nexus.jks.b64      # en Windows: certutil -encode nexus.jks nexus.jks.b64
```

En GitHub: **Settings → Secrets and variables → Actions → New repository secret**:
`NEXUS_KEYSTORE_BASE64` (contenido de `nexus.jks.b64`), `NEXUS_KEYSTORE_PASSWORD`,
`NEXUS_KEY_ALIAS` (`nexus`) y `NEXUS_KEY_PASSWORD`. Guarda `nexus.jks` y sus contraseñas: sin
ellos no podrás publicar actualizaciones de la misma app.

## Compilar en tu ordenador (Android Studio)

1. **File → Open** → carpeta `nexus-android`. Espera a que termine «Gradle sync».
2. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. El APK queda en
   `app/build/outputs/apk/debug/app-debug.apk`.

## Qué hay dentro

| Fichero | Para qué |
|---|---|
| `app/src/main/assets/index.html` | La Comunidad (app web original, sin cambios) |
| `app/src/main/java/.../MainActivity.kt` | Arranque, colores de barras del sistema |
| `ui/NexusApp.kt` | Barra inferior con las tres pestañas y el botón Atrás |
| `ui/CommunityScreen.kt` | WebView de la Comunidad (enlaces externos al navegador) |
| `ui/NotesScreens.kt` | Lista, ficha y editor de notas |
| `ui/ReviewScreen.kt` | Repaso con fichas |
| `data/NoteStore.kt` | Guarda las notas en `nexus_notas.json` (privado de la app) |
| `data/Links.kt` | Enlaces `[[...]]`, búsqueda sin tildes y orden de repaso (con tests) |
| `data/SampleNotes.kt` | Conceptos de ejemplo: almacén, stock, KPIs, normativa, transporte, comercio internacional |

Las cifras normativas de los ejemplos (Reglamento 561/2006, 165/2014, CMR, etc.) son orientativas:
contrástalas en BOE y EUR-Lex antes de citarlas en un trabajo.
