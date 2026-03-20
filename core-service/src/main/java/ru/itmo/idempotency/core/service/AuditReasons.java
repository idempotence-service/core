package ru.itmo.idempotency.core.service;

public final class AuditReasons {

    public static final String INVALID_INBOUND_EVENT = "Некорректное входящее событие";
    public static final String ROUTE_NOT_FOUND = "Не найден маршрут для входящего события";
    public static final String IDEMPOTENCY_PASSED = "Событие успешно прошло проверку на идемпотентность";
    public static final String IDEMPOTENCY_FAILED = "Событие не прошло проверку на идемпотентность";
    public static final String INVALID_RECEIVER_REPLY = "Некорректный ответ от системы-получателя";
    public static final String ORPHAN_REPLY = "Получен ответ без ожидающей операции";

    private AuditReasons() {
    }
}
