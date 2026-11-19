#!/usr/bin/env bash
# Seeds the demo user through the public API and prints a smoke-test report.
# Products/categories are seeded automatically by product-service Flyway V2,
# inventory by docker/postgres/init/02-inventory-schema.sql.
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

DOMAIN="${CADDY_DOMAIN:-}"
if [[ -z "${DOMAIN}" ]]; then
  echo "ERROR: CADDY_DOMAIN is not set in .env" >&2
  exit 1
fi

BASE="https://${DOMAIN}"
DEMO_EMAIL="demo@ecommerce.local"
DEMO_PASSWORD="Demo@12345"

echo "==> Registering demo user (ignore 'already registered' on re-runs)"
REGISTER_CODE="$(curl -sS -o /tmp/ecommerce-register.json -w '%{http_code}' \
  -X POST "${BASE}/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${DEMO_EMAIL}\",\"password\":\"${DEMO_PASSWORD}\",\"fullName\":\"Demo Visitor\"}")"
echo "    register -> HTTP ${REGISTER_CODE}"
cat /tmp/ecommerce-register.json 2>/dev/null || true
echo

echo "==> Logging in"
LOGIN_JSON="$(curl -fsS -X POST "${BASE}/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${DEMO_EMAIL}\",\"password\":\"${DEMO_PASSWORD}\"}")"
TOKEN="$(echo "${LOGIN_JSON}" | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
if [[ -z "${TOKEN}" ]]; then
  echo "ERROR: login failed. Response:" >&2
  echo "${LOGIN_JSON}" >&2
  exit 1
fi
echo "    login -> OK (access token received)"

echo "==> Browsing products"
curl -fsS "${BASE}/api/products" -H "Authorization: Bearer ${TOKEN}" \
  -o /tmp/ecommerce-products.json
echo "    products -> $(grep -o '"id"' /tmp/ecommerce-products.json | wc -l) product records"

echo "==> Browsing categories"
curl -fsS "${BASE}/api/categories" -H "Authorization: Bearer ${TOKEN}" \
  -o /tmp/ecommerce-categories.json
echo "    categories -> $(grep -o '"id"' /tmp/ecommerce-categories.json | wc -l) category records"

echo
echo "DEMO CREDENTIALS"
echo "    Email   : ${DEMO_EMAIL}"
echo "    Password: ${DEMO_PASSWORD}"
echo "    API     : ${BASE}"
