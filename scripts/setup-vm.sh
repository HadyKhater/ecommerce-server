#!/usr/bin/env bash
# Run once on the Oracle VM after cloning the backend repo.
# Detects the public IP, writes a gitignored .env, generates all secrets/keys,
# and starts the production stack. Safe to re-run (never rotates existing secrets).
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> Detecting public IPv4 address"
PUBLIC_IP="$(curl -fsS --max-time 10 https://api.ipify.org || true)"
if [[ -z "${PUBLIC_IP}" ]]; then
  PUBLIC_IP="$(curl -fsS --max-time 10 https://ifconfig.me/ip || true)"
fi
if [[ -z "${PUBLIC_IP}" ]]; then
  echo "ERROR: could not detect public IP. Set it manually: export PUBLIC_IP=1.2.3.4" >&2
  exit 1
fi

DOMAIN="$(echo "${PUBLIC_IP}" | tr '.' '-').sslip.io"
echo "    Public IP : ${PUBLIC_IP}"
echo "    API domain: ${DOMAIN}"

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "==> Created .env from .env.example"
fi

set_env() {
  local key="$1" value="$2"
  if grep -q "^${key}=" .env; then
    sed -i "s|^${key}=.*|${key}=${value}|" .env
  else
    echo "${key}=${value}" >> .env
  fi
}

set_env CADDY_DOMAIN "${DOMAIN}"

# Only generate secrets that are still placeholders.
if grep -q 'CHANGE_ME' .env; then
  echo "==> Generating Postgres password and gateway internal secret"
  set_env POSTGRES_PASSWORD "$(openssl rand -hex 32)"
  set_env GATEWAY_INTERNAL_SECRET "$(openssl rand -hex 32)"
fi

if grep -q 'CHANGE_ME_WITH_BASE64' .env; then
  echo "==> Generating RSA-2048 JWT keypair"
  TMPDIR_KEYS="$(mktemp -d)"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "${TMPDIR_KEYS}/private.pem" 2>/dev/null
  openssl pkcs8 -topk8 -nocrypt -in "${TMPDIR_KEYS}/private.pem" -out "${TMPDIR_KEYS}/private-pkcs8.pem"
  openssl rsa -in "${TMPDIR_KEYS}/private.pem" -pubout -out "${TMPDIR_KEYS}/public.pem" 2>/dev/null
  set_env JWT_PRIVATE_KEY "$(base64 -w0 "${TMPDIR_KEYS}/private-pkcs8.pem")"
  set_env JWT_PUBLIC_KEY "$(base64 -w0 "${TMPDIR_KEYS}/public.pem")"
  rm -rf "${TMPDIR_KEYS}"
fi

echo
echo "==> .env is ready (never commit it)"
echo "    CADDY_DOMAIN=${DOMAIN}"
echo
echo "NEXT STEPS:"
echo "  1. Set FRONTEND_URL and CORS_ALLOWED_ORIGINS in .env after deploying the frontend."
echo "  2. Start the stack:  docker compose -f docker-compose.prod.yml up -d --build"
echo "  3. Follow logs:      docker compose -f docker-compose.prod.yml logs -f --tail=100"
echo "  4. Health check:     curl -fsS https://${DOMAIN}/health"
echo "  5. Seed demo data:   bash scripts/seed-demo.sh"
