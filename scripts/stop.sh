#!/bin/bash
# stop.sh — остановить одиночный API-сервер
docker stop taxoin-api 2>/dev/null && docker rm taxoin-api 2>/dev/null && echo "✓ taxoin-api остановлен" || echo "taxoin-api не запущен"
