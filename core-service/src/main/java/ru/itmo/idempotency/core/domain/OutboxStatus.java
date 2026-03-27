package ru.itmo.idempotency.core.domain;

public enum OutboxStatus {
    NEW,
    DONE,
    ERROR
}
