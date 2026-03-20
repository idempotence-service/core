package ru.itmo.idempotency.core.web;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

public final class ManualReviewDtos {

    private ManualReviewDtos() {
    }

    public record ErrorEventsQuery(
            @Min(0) int page,
            @Min(1) @Max(100) int limit,
            @NotBlank String sort
    ) {
    }

    public record RestartEventRequest(@NotBlank String globalKey) {
    }

    public record ErrorEventItem(String status, String globalKey, String service, String integration) {
    }

    public record EventDetails(String globalKey,
                               String sourceUid,
                               String service,
                               String integration,
                               String status,
                               String statusDescription,
                               OffsetDateTime createDate,
                               OffsetDateTime updateDate,
                               JsonNode payload,
                               JsonNode headers) {
    }
}
