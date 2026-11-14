#!/usr/bin/env bash
# Builds and starts the full stack locally:
#   infra (Postgres, Redis, RabbitMQ) in Docker, services as JVM processes, storefront via Next.js.
#
#   scripts/run-local.sh          start everything
#   scripts/run-local.sh stop     stop services and storefront (infra keeps running)
#
# Env toggles: SKIP_BUILD=1 (reuse jars), SKIP_STOREFRONT=1 (backend only)
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$(pwd)
RUN="$ROOT/.run"
mkdir -p "$RUN"
SERVICES="catalog-service inventory-service cart-service checkout-service order-service cod-service payment-service notification-service api-gateway"

if [ "${1:-}" = "stop" ]; then
  for s in $SERVICES storefront; do
    if [ -s "$RUN/$s.pid" ]; then
      kill "$(cat "$RUN/$s.pid")" 2>/dev/null || true
      : > "$RUN/$s.pid"
    fi
  done
  echo "stopped"
  exit 0
fi

[ -f .env ] || scripts/dev-env.sh
set -a; source .env; set +a

docker compose --env-file .env -f infra/compose.yaml up -d --wait
if [ "${SKIP_BUILD:-0}" != "1" ]; then mvn -q -B -DskipTests package; fi

for s in $SERVICES; do
  jar=$(ls services/$s/target/$s-*.jar | grep -v plain | head -1)
  nohup java -XX:+UseSerialGC -Xmx256m -jar "$jar" > "$RUN/$s.log" 2>&1 &
  echo $! > "$RUN/$s.pid"
done

echo -n "waiting for services"
for port in 8081 8082 8083 8084 8085 8086 8087 8088 8080; do
  for _ in $(seq 1 120); do
    curl -fs "http://localhost:$port/actuator/health" >/dev/null 2>&1 && break
    sleep 1
  done
  curl -fs "http://localhost:$port/actuator/health" >/dev/null || { echo; echo "port $port not healthy; see .run/*.log"; exit 1; }
  echo -n "."
done
echo " backend ready on http://localhost:8080"

if [ "${SKIP_STOREFRONT:-0}" != "1" ]; then
  cd apps/storefront
  [ -d node_modules ] || pnpm install --frozen-lockfile
  pnpm build
  nohup pnpm start -p 3000 > "$RUN/storefront.log" 2>&1 &
  echo $! > "$RUN/storefront.pid"
  echo "storefront starting on http://localhost:3000"
fi
