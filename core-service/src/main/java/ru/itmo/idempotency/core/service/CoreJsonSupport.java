package ru.itmo.idempotency.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import ru.itmo.idempotency.common.config.RouteModels;
import ru.itmo.idempotency.common.messaging.MessageModels;

import java.util.Map;

@Component
public class CoreJsonSupport {

    private final ObjectMapper objectMapper;

    public CoreJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MessageModels.MessageEnvelope parseEnvelope(String rawMessage) {
        try {
            return objectMapper.readValue(rawMessage, MessageModels.MessageEnvelope.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid message payload", exception);
        }
    }

    public JsonNode toJsonNode(Object value) {
        return objectMapper.valueToTree(value);
    }

    public RouteModels.RouteSnapshot parseSnapshot(JsonNode snapshot) {
        return objectMapper.convertValue(snapshot, RouteModels.RouteSnapshot.class);
    }

    public ObjectNode headersWithoutUid(Map<String, Object> headers) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        if (headers == null) {
            return node;
        }
        headers.forEach((key, value) -> {
            if (!"uid".equals(key)) {
                node.putPOJO(key, value);
            }
        });
        return node;
    }

    public JsonNode safeRawPayload(String rawMessage) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("raw", rawMessage);
        return node;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, Map.class);
    }
}
