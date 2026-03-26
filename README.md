# core

Сервис, который гарантирует “одна бизнес-операция - один эффект” при повторных запросах / ретраях.

## Что есть в проекте

- `postgres` - база данных, проброшена наружу по порту
- `kafka` - пустой single-node Kafka в KRaft режиме, проброшен наружу по порту
- `kafka-ui` - веб-интерфейс для Kafka
- `backend` - Java 25 backend в Docker
- `frontend` - минимальный React/Vite frontend в Docker
- `scripts/compose-up.sh` - локальный запуск текущего checkout через Docker Compose
- `scripts/deploy-main.sh` - автодеплой сервера из ветки `main`
- `scripts/deploy.sh` - ручной деплой с выбором ветки или тега из удалённого репозитория

## Два режима работы

### 1. Основной режим - автодеплой из `main`

Логика такая:

1. пушь изменений в `main` ветку
2. GitHub Actions запускает workflow `.github/workflows/deploy.yml`
3. workflow заходит по SSH на сервер
4. на сервере выполняется `./scripts/deploy-main.sh`
5. сервер делает `git pull` ветки `main`
6. затем выполняет `docker compose up -d --build --remove-orphans`

То есть любые изменения, попавшие в `main`, автоматически подтянутся на сервер и будут пересобраны.

### 2. Ручной режим - поднять конкретные ветки или теги

Если нужно не `main`, а, например, `release` или `tag`, остаётся ручной сценарий:

```bash
./up.sh
```

Он читает `.env`, отдельно забирает backend/frontend из удалённого репозитория и поднимает стек из выбранных ref.

## Быстрый старт локально

Скопируй конфиг:

```bash
cp .env.example .env
```

Подними стек из текущей папки:

```bash
./scripts/compose-up.sh
```

Или напрямую:

```bash
docker compose up -d --build
```

## Что нужно на сервере для автодеплоя

На сервере должны быть:

- Docker
- Docker Compose plugin
- Git
- SSH-ключ у GitHub Actions для входа на сервер
- сам репозиторий, уже один раз склонированный на сервер
- заполненный `.env` рядом с `docker-compose.yml`

Пример первичной подготовки на сервере:

```bash
git clone git@github.com:your-org/your-repo.git /opt/idem-core
cd /opt/idem-core
cp .env.example .env
nano .env
chmod +x scripts/*.sh up.sh down.sh
./scripts/compose-up.sh
```

После этого автодеплой сможет просто обновлять `main` и делать пересборку.

## Какие secrets нужны в GitHub

Для `.github/workflows/deploy.yml` нужны secrets:

- `DEPLOY_HOST` - IP или домен сервера
- `DEPLOY_USER` - пользователь для SSH
- `DEPLOY_SSH_KEY` - приватный ключ, которым GitHub Actions зайдёт на сервер
- `DEPLOY_PATH` - путь до проекта на сервере, например `/opt/idem-core`

## Куда подключаться

После старта будут доступны:

- frontend: `http://SERVER_HOST:3000`
- backend: `http://SERVER_HOST:8080`
- backend healthcheck: `http://SERVER_HOST:8080/health`
- Kafka UI: `http://SERVER_HOST:8085`
- Postgres from DBeaver: `jdbc:postgresql://SERVER_HOST:5432/idem_core`
- Kafka bootstrap server: `SERVER_HOST:9094`

### Параметры Postgres для DBeaver

- host: `SERVER_HOST`
- port: `5432`
- database: значение `POSTGRES_DB`
- user: значение `POSTGRES_USER`
- password: значение `POSTGRES_PASSWORD`

## Как работает ручной режим по ветке или тегу

Важно: чистый `docker compose up -d` **не умеет сам** сходить в удалённый Git-репозиторий, понять что в ветке появились новые коммиты, сделать checkout нужного тега/ветки и потом пересобрать backend/frontend.

Поэтому ручной режим сделан через:

```bash
./up.sh
```

Есть общий ref:

```env
GLOBAL_REF=main
```

И отдельные переопределения:

```env
BACKEND_REF=release/01.001.00
FRONTEND_REF=main
```

Логика такая:

- если `BACKEND_REF` пустой, backend берёт `GLOBAL_REF`
- если `FRONTEND_REF` пустой, frontend берёт `GLOBAL_REF`
- если указаны отдельные значения, они имеют приоритет

Пример 1 - поднять всё из одного тега:

```env
GLOBAL_REF=v1.2.0
BACKEND_REF=
FRONTEND_REF=
```

Пример 2 - backend из release, frontend из main:

```env
GLOBAL_REF=main
BACKEND_REF=release/01.001.00
FRONTEND_REF=main
```

Пример 3 - backend и frontend из разных реп:

```env
BACKEND_REPO_URL=git@github.com:org/backend.git
FRONTEND_REPO_URL=git@github.com:org/frontend.git
BACKEND_REF=main
FRONTEND_REF=develop
```

Запуск:

```bash
./up.sh
```

## CI/CD

### CI

Добавлен workflow `.github/workflows/backend-ci.yml`:

- запускается на push / pull request
- собирает backend через Gradle
- гоняет тесты

### CD

Добавлен workflow `.github/workflows/deploy.yml`:

- запускается автоматически на push в `main`
- можно запускать вручную
- заходит по SSH на сервер
- на сервере делает `git pull` ветки `main`
- затем выполняет `docker compose up -d --build --remove-orphans`

## Сброс данных Kafka/Postgres

Если нужно полностью снести данные и поднять пустые Kafka/Postgres:

```bash
docker compose down -v
docker compose up -d --build
```

## Остановка

```bash
./down.sh
```
