CREATE TABLE IF NOT EXISTS idempotency (
    global_key VARCHAR(512) PRIMARY KEY,
    source_uid VARCHAR(255) NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    integration_name VARCHAR(255) NOT NULL,
    yaml_snapshot JSONB NOT NULL,
    headers JSONB NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(24) NOT NULL,
    status_description VARCHAR(255),
    create_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_idempotency_status_create_date
    ON idempotency (status, create_date);

CREATE INDEX IF NOT EXISTS idx_idempotency_status_update_date
    ON idempotency (status, update_date);

CREATE TABLE IF NOT EXISTS kafka_event_outbox (
    id BIGSERIAL PRIMARY KEY,
    global_key VARCHAR(512),
    service_name VARCHAR(255),
    integration_name VARCHAR(255),
    yaml_snapshot JSONB NOT NULL,
    status VARCHAR(8) NOT NULL,
    status_description VARCHAR(255),
    result VARCHAR(8) NOT NULL,
    result_description VARCHAR(255),
    create_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kafka_event_outbox_status_create_date
    ON kafka_event_outbox (status, create_date);

CREATE TABLE IF NOT EXISTS event_audit (
    id BIGSERIAL PRIMARY KEY,
    global_key VARCHAR(512),
    service_name VARCHAR(255),
    integration_name VARCHAR(255),
    reason VARCHAR(255) NOT NULL,
    headers JSONB,
    payload JSONB,
    yaml_snapshot JSONB,
    create_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_event_audit_create_date
    ON event_audit (create_date);
