package ru.itmo.idempotency.core.service;

public final class DescriptionUtils {

    private static final int MAX_LENGTH = 255;

    private DescriptionUtils() {
    }

    public static String limit(String value) {
        if (value == null || value.length() <= MAX_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_LENGTH - 3) + "...";
    }
}
