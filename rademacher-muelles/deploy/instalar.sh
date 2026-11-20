#!/usr/bin/env bash
# Instalador de Rademacher Muelles para un servidor Ubuntu/Debian recién creado.
# Uso (como root):
#   curl -fsSL https://raw.githubusercontent.com/nunezmichael74-ctrl/Nexus-android.-1/main/rademacher-muelles/deploy/instalar.sh | bash
#
# Qué hace:
#  1. Instala Docker (si no está).
#  2. Descarga o actualiza la aplicación en /opt/rademacher.
#  3. Si no existe configuración, crea una con:
#       - DOMAIN: dirección gratuita basada en la IP (p. ej. 1-2-3-4.sslip.io) o la indicada en DOMAIN=...
#       - OFFICE_PIN: PIN aleatorio de 6 cifras (o el indicado en OFFICE_PIN=...)
#  4. Arranca la aplicación con HTTPS automático y muestra la dirección y el PIN.
# Se puede volver a ejecutar para actualizar: conserva la configuración y los datos.
set -euo pipefail

REPO_URL="https://github.com/nunezmichael74-ctrl/Nexus-android.-1.git"
DIR="${RADEMACHER_DIR:-/opt/rademacher}"
APP="$DIR/rademacher-muelles"

say() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }

if [ "$(id -u)" -ne 0 ]; then
  echo "Ejecute este script como root (o con sudo)." >&2
  exit 1
fi

say "1/4 Instalando Docker y git"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
if ! command -v git >/dev/null 2>&1; then
  apt-get update -y && apt-get install -y git
fi

say "2/4 Descargando la aplicación"
if [ -d "$DIR/.git" ]; then
  git -C "$DIR" pull --ff-only
else
  git clone --depth 1 "$REPO_URL" "$DIR"
fi

say "3/4 Configuración"
ENV_FILE="$APP/deploy/.env"
if [ -f "$ENV_FILE" ]; then
  echo "Se mantiene la configuración existente ($ENV_FILE)."
else
  if [ -z "${DOMAIN:-}" ]; then
    IP="$(curl -fsS4 https://api.ipify.org || curl -fsS4 https://ifconfig.me)"
    DOMAIN="${IP//./-}.sslip.io"
  fi
  if [ -z "${OFFICE_PIN:-}" ]; then
    OFFICE_PIN="$(shuf -i 100000-999999 -n 1)"
  fi
  umask 077
  cat > "$ENV_FILE" <<EOF
DOMAIN=$DOMAIN
OFFICE_PIN=$OFFICE_PIN
RETENTION_DAYS=7
DOCKS=14
EOF
fi
# shellcheck disable=SC1090
. "$ENV_FILE"

# Cortafuegos: si ufw está activo, abrir web y mantener SSH
if command -v ufw >/dev/null 2>&1 && ufw status | grep -q "Status: active"; then
  ufw allow 22/tcp >/dev/null; ufw allow 80/tcp >/dev/null; ufw allow 443/tcp >/dev/null
fi

say "4/4 Arrancando (la primera vez tarda 1-2 minutos)"
cd "$APP/deploy"
docker compose up -d --build

# Esperar a que el certificado HTTPS esté listo
printf 'Esperando al certificado HTTPS'
for _ in $(seq 1 60); do
  if curl -fsS "https://$DOMAIN/api/health" >/dev/null 2>&1; then OK=1; break; fi
  printf '.'; sleep 3
done
echo

cat <<EOF

============================================================
  Rademacher Muelles instalado
============================================================
  Oficina:    https://$DOMAIN/oficina
  PIN:        $OFFICE_PIN   (guárdelo; está en $ENV_FILE)
  Pantalla:   https://$DOMAIN/pantalla
  Chóferes:   https://$DOMAIN/chofer
  App Android (dirección del servidor): https://$DOMAIN
============================================================
EOF
if [ "${OK:-0}" != "1" ]; then
  echo "AVISO: aún no responde por HTTPS. Espere unos minutos y revise: docker compose -f $APP/deploy/docker-compose.yml logs caddy"
fi
