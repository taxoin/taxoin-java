#!/bin/bash
# status.sh — проверить состояние Taxoin
echo "═══════════════════════════════════════"
echo "  Taxoin Status"
echo "═══════════════════════════════════════"

# Контейнеры
echo ""
echo "Docker контейнеры:"
docker ps --filter "name=taxoin" --format "  {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null \
  || echo "  (нет запущенных taxoin-контейнеров)"

# Проверяем API если запущен
for PORT in 47780 47701 47702 47703; do
  if curl -sf "http://localhost:${PORT}/health" >/dev/null 2>&1; then
    STATUS=$(curl -s "http://localhost:${PORT}/api/status" 2>/dev/null)
    echo ""
    echo "  ✓ :${PORT} → $(echo $STATUS | python3 -c 'import json,sys; d=json.load(sys.stdin); print(f"blocks={d.get(\"blocks\",0)} services={d.get(\"services\",0)}")' 2>/dev/null || echo 'UP')"
  fi
done

echo ""
echo "Образ taxoin-java:latest:"
docker image inspect taxoin-java:latest \
  --format '  Размер: {{.Size | printf "%.0f"}} bytes  Создан: {{.Created}}' 2>/dev/null \
  || echo "  (образ не найден — запусти ./scripts/build.sh)"
echo ""
