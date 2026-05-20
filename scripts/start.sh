#!/bin/bash
# ─────────────────────────────────────────────────────────────
# start.sh — запуск одного Taxoin API-сервера
# Использование: ./scripts/start.sh [PORT]
# По умолчанию: PORT=47780
# ─────────────────────────────────────────────────────────────
set -e

PORT="${1:-47780}"
IMAGE="taxoin-java:latest"
NAME="taxoin-api"
DATA_DIR="$(pwd)/.taxoin-data"

# Проверяем что образ собран
if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  echo "⚠ Образ $IMAGE не найден. Собираю..."
  docker build -t "$IMAGE" "$(dirname "$0")/.."
fi

# Останавливаем старый контейнер если есть
docker rm -f "$NAME" 2>/dev/null && echo "→ Старый контейнер остановлен"

mkdir -p "$DATA_DIR"

echo "🚀 Запускаю Taxoin API на порту $PORT..."
docker run -d \
  --name "$NAME" \
  -p "${PORT}:47780" \
  -v "${DATA_DIR}:/app/.taxoin" \
  -e TAXOIN_DIR=/app/.taxoin \
  --restart unless-stopped \
  "$IMAGE"

echo ""
echo "✓ Taxoin API запущен!"
echo "  API:    http://localhost:${PORT}"
echo "  Health: http://localhost:${PORT}/health"
echo "  Wallet: http://localhost:${PORT}/web/wallet.html"
echo "  Docs:   http://localhost:${PORT}/q/swagger-ui"
echo ""
echo "  Логи:   docker logs -f $NAME"
echo "  Стоп:   docker stop $NAME"
