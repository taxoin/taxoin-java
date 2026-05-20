#!/bin/bash
# ─────────────────────────────────────────────────────────────
# start-cluster.sh — запуск полного кластера Taxoin
# 3 валидатора (47701, 47702, 47703) + API (47780)
# Использование: ./scripts/start-cluster.sh
# Требования: ~1 GB свободной RAM
# ─────────────────────────────────────────────────────────────
set -e

COMPOSE_FILE="$(dirname "$0")/../docker-compose.yml"
IMAGE="taxoin-java:latest"

# Проверяем что образ собран
if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  echo "⚠ Образ $IMAGE не найден. Собираю (это займёт несколько минут)..."
  docker build -t "$IMAGE" "$(dirname "$0")/.."
fi

echo "🚀 Запускаю Taxoin кластер..."
docker compose -f "$COMPOSE_FILE" up -d

echo ""
echo "✓ Кластер запущен!"
echo "  Validator-1: http://localhost:47701/health"
echo "  Validator-2: http://localhost:47702/health"
echo "  Validator-3: http://localhost:47703/health"
echo "  API:         http://localhost:47780"
echo "  Wallet UI:   http://localhost:47780/web/wallet.html"
echo ""
echo "  Логи:  docker compose -f $COMPOSE_FILE logs -f"
echo "  Стоп:  $(dirname "$0")/stop-cluster.sh"
