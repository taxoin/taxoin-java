#!/bin/bash
# stop-cluster.sh — остановить весь кластер
COMPOSE_FILE="$(dirname "$0")/../docker-compose.yml"
docker compose -f "$COMPOSE_FILE" down
echo "✓ Кластер остановлен"
