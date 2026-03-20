package ru.itmo.idempotency.receiver.service;

public enum ReceiverMode {
    AUTO_SUCCESS,
    AUTO_FAIL_RESEND,
    AUTO_FAIL_NO_RESEND,
    MANUAL
}
