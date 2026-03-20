package ru.itmo.idempotency.core.domain;

public enum IdempotencyStatus {
    RESERVED,
    WAITING_ASYNC_RESPONSE,
    COMMITTED,
    ERROR
}
