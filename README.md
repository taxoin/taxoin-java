# Taxoin Ⓣ — Java Port

> Blockchain on Git — Java 21 + Quarkus 3.x + JGit + Bouncy Castle  
> Полный порт [taxoin (Python)](https://github.com/taxoin/taxoin) на Java.

---

## Быстрый старт

### Требования

| Инструмент | Версия |
|-----------|--------|
| Docker    | 20+    |
| RAM       | ≥ 512 MB (одиночный) / ≥ 1 GB (кластер) |

Java и Maven **не нужны** — всё собирается внутри Docker.

---

## Запуск

### Шаг 1 — Собрать образ

```bash
./scripts/build.sh
```

Первый раз ~5 минут (Maven скачивает зависимости).  
Повторные сборки — секунды (Docker кеш).

### Шаг 2а — Одиночный сервер (минимум RAM)

```bash
./scripts/start.sh           # порт 47780 (по умолчанию)
./scripts/start.sh 8080      # кастомный порт
```

### Шаг 2б — Полный кластер (3 валидатора + API)

```bash
./scripts/start-cluster.sh
```

Порты: `47701` `47702` `47703` (валидаторы) + `47780` (API)

### Проверить статус

```bash
./scripts/status.sh
```

### Остановить

```bash
./scripts/stop.sh          # одиночный
./scripts/stop-cluster.sh  # кластер
```

---

## API Reference

Базовый URL: `http://localhost:47780`

| Метод | Путь | Описание |
|-------|------|----------|
| GET  | `/health` | Проверка жизнеспособности |
| GET  | `/q/health/live` | Quarkus health probe |
| GET  | `/api/status` | Статус сети |
| POST | `/api/wallet` | Создать кошелёк (secp256k1) |
| GET  | `/api/balance/{address}` | Баланс адреса |
| POST | `/api/tx/send` | Отправить attested транзакцию |
| GET  | `/api/services` | Список сервисов (`?service_type=sms&min_rating=4.0`) |
| POST | `/api/service/register` | Зарегистрировать сервис |
| GET  | `/api/reputation/{address}` | Рейтинг провайдера |
| GET  | `/api/validators` | Список веток/валидаторов |
| POST | `/api/testnet/faucet` | Получить 100 Ⓣ (testnet) |

### Примеры

```bash
# Создать кошелёк
curl -X POST http://localhost:47780/api/wallet

# Получить тестовые монеты
curl -X POST http://localhost:47780/api/testnet/faucet \
  -H "Content-Type: application/json" \
  -d '{"address":"0xВАШ_АДРЕС"}'

# Проверить баланс
curl http://localhost:47780/api/balance/0xВАШ_АДРЕС

# Зарегистрировать сервис
curl -X POST http://localhost:47780/api/service/register \
  -H "Content-Type: application/json" \
  -d '{
    "provider":     "0xВАШ_АДРЕС",
    "serviceType":  "sms",
    "pricePerUnit": 0.1,
    "description":  "SMS gateway",
    "endpoint":     "https://sms.example.com"
  }'

# Отправить транзакцию (обе подписи = pending)
curl -X POST http://localhost:47780/api/tx/send \
  -H "Content-Type: application/json" \
  -d '{
    "consumer":    "0xalice",
    "provider":    "0xbob",
    "amount":      0.1,
    "serviceRef":  "sms:0xbob",
    "consumerSig": "ПОДПИСЬ_ALICE",
    "providerSig": "ПОДПИСЬ_BOB"
  }'
```

### Веб-кошелёк

```
http://localhost:47780/web/wallet.html
```

---

## CLI (внутри контейнера)

```bash
# Войти в контейнер
docker exec -it taxoin-api sh

# Команды
java -cp "quarkus-run.jar:app/*:lib/*" com.taxoin.cli.Main --help
java -cp "quarkus-run.jar:app/*:lib/*" com.taxoin.cli.Main wallet new
java -cp "quarkus-run.jar:app/*:lib/*" com.taxoin.cli.Main wallet address
java -cp "quarkus-run.jar:app/*:lib/*" com.taxoin.cli.Main branch list
java -cp "quarkus-run.jar:app/*:lib/*" com.taxoin.cli.Main validator init
java -cp "quarkus-run.jar:app/*:lib/*" com.taxoin.cli.Main service list
```

---

## Архитектура

```
┌────────────────────────────────────────────────────┐
│           TAXOIN JAVA (Quarkus 3.35.4)             │
├──────────────┬─────────────────┬───────────────────┤
│  REST API    │  CLI (Picocli)  │  Web UI           │
│  @Path /api  │  taxoin <cmd>   │  /web/wallet.html │
├──────────────┴─────────────────┴───────────────────┤
│              Application Services                   │
│  BranchManager  │  ValidatorNetwork  │  Consensus  │
│  GenesisRegistry│  ServiceRegistry   │  Reputation │
├─────────────────────────────────────────────────────┤
│              Storage Layer                          │
│  JGitBlockchain (JGit 6.9)  │  JsonStore (Jackson) │
├─────────────────────────────────────────────────────┤
│              Crypto                                 │
│  CryptoUtils — Bouncy Castle secp256k1             │
└─────────────────────────────────────────────────────┘
```

### Стек

| Компонент | Библиотека | Версия |
|-----------|-----------|--------|
| REST API  | Quarkus REST + Jackson | 3.35.4 |
| Git backend | JGit | 6.9 |
| Криптография | Bouncy Castle | 1.78.1 |
| CLI | Picocli | 4.7.6 |
| HTTP клиент | OkHttp | 4.11.0 |
| Java | Eclipse Temurin JRE Alpine | 21 |

---

## Тесты

**224 теста**, все зелёные.

```bash
# Запустить через Docker (без локальной Java)
docker volume create taxoin-mvn-cache
docker run --rm \
  -v $(pwd):/workspace \
  -v taxoin-mvn-cache:/root/.m2 \
  -w /workspace \
  maven:3.9-eclipse-temurin-21 \
  mvn test
```

| Пакет | Тестов | Что проверяет |
|-------|--------|---------------|
| `core` | 8 | Hash, Tx, Block, UTXO |
| `crypto` | 12 | ECDSA sign/verify, PEM, адреса |
| `storage` | 27 | JGit (17 ops), JsonStore |
| `branch` | 57 | BranchState, ConflictDetector, BranchManager |
| `consensus` | 18 | Tendermint PROPOSE→COMMIT |
| `validator` | 27 | ValidatorSet, GossipProtocol |
| `contrib` | 34 | Genesis, Services, AttestedTx, Reputation |
| `api` | 16 | REST endpoints (@QuarkusTest + REST Assured) |
| `cli` | 25 | Picocli команды in-process |

---

## Совместимость с Python-версией

| Аспект | |
|--------|---------|
| JSON формат блоков | ✅ байт-в-байт (sort_keys=true) |
| ECDSA подписи | ✅ DER hex (SHA256withECDSA) |
| Адреса | ✅ `0x` + SHA256(pubkey)[-40:] |
| API порт | ✅ 47780 |
| Git структура | ✅ `.taxoin/block.json` в коммитах |
| Wallet JSON | ✅ `{address, private_key, public_key}` |

---

## Переменные окружения

| Переменная | По умолчанию | Описание |
|-----------|-------------|----------|
| `TAXOIN_DIR` | `/app/.taxoin` | Путь к данным |
| `TAXOIN_VALIDATOR` | — | Номер валидатора (1–7) |
| `TAXOIN_PORT` | `47780` | Порт ноды |
