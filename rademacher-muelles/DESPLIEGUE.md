# Guía de puesta en marcha · Rademacher Muelles

Procedimiento recomendado, en orden. Tiempo estimado: 1–2 horas la primera vez.

## Paso 1 · Decidir dónde se aloja el servidor

| Opción | Ventajas | Inconvenientes | Recomendado si… |
|---|---|---|---|
| **VPS en la nube** (p. ej. proveedor europeo, ~5 €/mes) | Accesible desde el móvil del chófer con datos 4G/5G, HTTPS sencillo | Coste mensual, depende de internet | **Opción recomendada** |
| Servidor/PC en la oficina + wifi de invitados | Sin coste mensual | Chóferes deben conectarse a la wifi; HTTPS más complejo | No hay presupuesto para VPS |

Para cumplir el RGPD, elija un proveedor con centro de datos en la UE.

## Paso 2 · Preparar el dominio

1. Elija un subdominio, p. ej. `muelles.su-empresa.es`.
2. En el panel DNS del dominio cree un registro **A** que apunte a la IP pública del VPS.
3. Compruebe con `ping muelles.su-empresa.es` que responde la IP correcta (puede tardar unos minutos).

## Paso 3 · Instalar en el servidor (Docker + HTTPS automático)

En el VPS (Ubuntu/Debian):

```bash
# 3.1 Instalar Docker
curl -fsSL https://get.docker.com | sh

# 3.2 Descargar la aplicación
git clone https://github.com/nunezmichael74-ctrl/Nexus-android.-1.git
cd Nexus-android.-1/rademacher-muelles/deploy

# 3.3 Configurar
cp .env.example .env
nano .env        # DOMAIN=muelles.su-empresa.es  y  OFFICE_PIN=un PIN de 6+ cifras

# 3.4 Arrancar
docker compose up -d --build
```

Caddy obtiene el certificado HTTPS de Let's Encrypt automáticamente (puertos 80 y 443 abiertos en el cortafuegos del VPS).

Comprobación: abra `https://muelles.su-empresa.es/api/health` → debe mostrar `{"ok":true,...}`.

> Alternativa sin Docker: `deploy/rademacher-muelles.service` (systemd) con Node.js 18+ y un proxy HTTPS delante.

## Paso 4 · Configurar la oficina

1. En el PC de oficina abra `https://muelles.su-empresa.es/oficina` e introduzca el PIN.
2. Configure una **impresora de tickets** (o normal) como predeterminada: el botón *Imprimir* del ticket saca código + QR.
3. Si hay sala de chóferes, conecte una TV con navegador a `https://muelles.su-empresa.es/pantalla` en pantalla completa (F11).

## Paso 5 · App Android para los chóferes (opcional)

Los chóferes **no necesitan la app**: basta con escanear el QR del ticket. La app aporta vibración y aviso aunque el móvil esté en segundo plano.

1. En GitHub: **Settings → Secrets and variables → Actions → Variables** → cree `RADEMACHER_SERVER_URL` = `https://muelles.su-empresa.es`.
2. **Actions → APK Rademacher Muelles → Run workflow**.
3. En **Releases** aparece `RademacherMuelles-1.0.X.apk`. Ponga un QR de esa descarga en la oficina.
4. (Recomendado) Firma propia para que las actualizaciones se instalen sin desinstalar: secretos `RADEMACHER_KEYSTORE_BASE64`, `RADEMACHER_KEYSTORE_PASSWORD`, `RADEMACHER_KEY_ALIAS`, `RADEMACHER_KEY_PASSWORD`.

Si no se configura la variable del paso 1, la app pide la dirección del servidor la primera vez que se abre.

## Paso 6 · Prueba piloto (1 semana)

1. Empezar con 2–3 muelles y el turno de mañana.
2. Medir antes/después: **tiempo medio de espera** y **tiempo medio en muelle** (KPIs de la oficina).
3. Recoger incidencias de chóferes por idioma (traducciones a revisar con nativos).
4. Ampliar a los 14 muelles.

## Paso 7 · Mantenimiento

| Tarea | Cómo |
|---|---|
| Actualizar versión | `git pull && docker compose up -d --build` |
| Copia de seguridad | `docker compose cp app:/data/state.json backup-$(date +%F).json` (diaria, p. ej. con cron) |
| Cambiar PIN | Editar `.env` y `docker compose up -d` |
| Ver registros | `docker compose logs -f app` |

## Protección de datos

- Colocar en oficina un cartel informativo (responsable, finalidad: gestión de accesos a muelles, plazo de conservación 7 días, derechos ARSOPL).
- Solo la matrícula es obligatoria; nombre y teléfono son opcionales.
