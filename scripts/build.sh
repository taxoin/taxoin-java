#!/bin/bash
# ─────────────────────────────────────────────────────────────
# build.sh — собрать Docker-образ taxoin-java
# Использование: ./scripts/build.sh [TAG]
# По умолчанию TAG=latest
# ─────────────────────────────────────────────────────────────
set -e

TAG="${1:-latest}"
IMAGE="taxoin-java:${TAG}"
ROOT="$(dirname "$0")/.."

echo "🔨 Собираю $IMAGE ..."
echo "   (maven dependency download + mvn package — первый раз ~5 мин)"
echo ""

time docker build -t "$IMAGE" "$ROOT"

SIZE=$(docker image inspect "$IMAGE" --format '{{.Size}}' | numfmt --to=iec 2>/dev/null || echo "?")
echo ""
echo "✓ Образ готов: $IMAGE (${SIZE})"
echo ""
echo "Запуск:"
echo "  Одиночный:   ./scripts/start.sh"
echo "  Кластер:     ./scripts/start-cluster.sh"
