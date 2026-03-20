# Idempotency Core

Репозиторий теперь содержит полный учебный стенд:

- `core-service` - ядро идемпотентности с PostgreSQL, Flyway, Spring Security, manual review REST API и Kafka-конвейером;
- `sender-simulator` - имитация внешней системы-отправителя, умеет слать дубликаты и читать технические ответы;
- `receiver-simulator` - имитация системы-получателя, умеет фиксировать дубликаты и отправлять асинхронные ответы;
- `config/routes.yaml` - маршруты в формате, который обсуждался в use cases;
- `docker-compose.yml` - полный локальный стенд на Postgres + 2 Redpanda-кластерах.

## Что реализовано

- идемпотентная обработка входящих Kafka-событий по `uid` -> `globalKey`;
- таблицы `idempotency`, `kafka_event_outbox`, `event_audit` и миграции Flyway;
- статусы `RESERVED`, `WAITING_ASYNC_RESPONSE`, `COMMITTED`, `ERROR`;
- outbox-обработчик технических ответов системе-отправителю;
- обработчик уникальной доставки в систему-получатель;
- обработчик асинхронных ответов от системы-получателя;
- manual review API:
  - `GET /get-error-events`
  - `POST /restart-event`
  - `GET /get-event-by-id`
- Spring Security с bearer token и бизнес-ответом `TECHNICAL_ERROR_01` при отсутствии доступа;
- шедулер очистки старых `COMMITTED` записей;
- динамический конструктор Kafka producer/consumer по YAML-маршрутам;
- sender/receiver симуляторы для ручного и e2e тестирования.

## Маршрут из YAML

Файл `config/routes.yaml` использует ту же модель, что и в постановке:

- `sender.producer` -> входной канал `inbound`;
- `sender.consumer` -> технический ответ `request_out`;
- `receiver.producer` -> уникальное событие `reply_in`;
- `receiver.consumer` -> асинхронный ответ `reply_out`.

## Сборка и тесты

```bash
./gradlew test
```

Основной e2e-набор лежит в `core-service/src/test/java/ru/itmo/idempotency/core/CoreServiceIntegrationTest.java`.

Он покрывает:

- дедупликацию входящих дубликатов;
- отправку технических ответов и уникального события в С2;
- переход в `ERROR`, ручной перезапуск и финальный `COMMITTED`;
- security-сценарий без `Authorization`;
- очистку старых `COMMITTED` записей.

## Запуск через Docker Compose

```bash
docker compose up --build
```

После старта будут доступны:

- core API: `http://localhost:8080`
- sender simulator: `http://localhost:8081`
- receiver simulator: `http://localhost:8082`
- Kafka sender cluster: `localhost:19092`
- Kafka receiver cluster: `localhost:29092`

Bearer token для manual review API по умолчанию: `operator-token`

## Быстрый сценарий ручной проверки

1. Отправить событие с дубликатами:

```bash
curl -X POST http://localhost:8081/api/sender/send \
  -H 'Content-Type: application/json' \
  -d '{
    "integration": "system1-to-system2",
    "payload": {"orderId": 1, "amount": 100},
    "headers": {"source": "manual-test"},
    "duplicates": 2
  }'
```

2. Посмотреть технические ответы отправителю:

```bash
curl http://localhost:8081/api/sender/replies
```

3. Посмотреть, что реально дошло до системы-получателя:

```bash
curl http://localhost:8082/api/receiver/events
```

4. Получить список ошибок manual review:

```bash
curl 'http://localhost:8080/get-error-events?limit=20&sort=asc' \
  -H 'Authorization: Bearer operator-token'
```

5. Перезапустить задачу вручную:

```bash
curl -X POST http://localhost:8080/restart-event \
  -H 'Authorization: Bearer operator-token' \
  -H 'Content-Type: application/json' \
  -d '{"globalKey":"sender-service:system1-to-system2:<uid>"}'
```

## Управление receiver simulator

По умолчанию receiver отвечает `AUTO_SUCCESS`.

Можно переключить режим:

```bash
curl -X POST http://localhost:8082/api/receiver/mode \
  -H 'Content-Type: application/json' \
  -d '{"integration":"system1-to-system2","mode":"AUTO_FAIL_NO_RESEND"}'
```

Доступные режимы:

- `AUTO_SUCCESS`
- `AUTO_FAIL_RESEND`
- `AUTO_FAIL_NO_RESEND`
- `MANUAL`
