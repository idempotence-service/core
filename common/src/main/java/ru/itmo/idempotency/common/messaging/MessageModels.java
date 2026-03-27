package ru.itmo.idempotency.common.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public final class MessageModels {

    private MessageModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MessageEnvelope(Map<String, Object> headers, JsonNode payload) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TechnicalResponsePayload(String result, String resultDescription) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AsyncReplyPayload(String result, Boolean needResend, String resultDescription) {
    }
}
