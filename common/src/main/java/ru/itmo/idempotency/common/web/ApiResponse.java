package ru.itmo.idempotency.common.web;

import java.util.UUID;

public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, ApiError.empty());
    }

    public static <T> ApiResponse<T> failure(String title, String code, String text) {
        return new ApiResponse<>(false, null, new ApiError(UUID.randomUUID().toString(), title, code, text));
    }
}
