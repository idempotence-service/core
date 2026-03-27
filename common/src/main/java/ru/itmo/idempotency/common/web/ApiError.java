package ru.itmo.idempotency.common.web;

public record ApiError(String errorUid, String title, String code, String text) {

    public static ApiError empty() {
        return new ApiError(null, null, null, null);
    }
}
