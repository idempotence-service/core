package ru.itmo.idempotency.sender.web;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public final class SenderDtos {

    private SenderDtos() {
    }

    public record SendEventRequest(@NotBlank String integration,
                                   String uid,
                                   Map<String, Object> headers,
                                   JsonNode payload,
                                   @Min(1) int duplicates) {
    }
}
