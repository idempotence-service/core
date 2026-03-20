package ru.itmo.idempotency.receiver.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ReceiverStateService {

    private final List<ReceivedMessage> messages = new CopyOnWriteArrayList<>();
    private final Map<String, ReceiverMode> modes = new ConcurrentHashMap<>();
    private final Set<String> seenGlobalKeys = ConcurrentHashMap.newKeySet();

    public ReceivedMessage record(String integration, String globalKey, Map<String, Object> headers, JsonNode payload) {
        boolean duplicate = !seenGlobalKeys.add(globalKey);
        ReceivedMessage message = new ReceivedMessage(integration, globalKey, duplicate, headers, payload, OffsetDateTime.now(ZoneOffset.UTC));
        messages.add(message);
        return message;
    }

    public List<ReceivedMessage> messages() {
        return new ArrayList<>(messages);
    }

    public ReceiverMode modeFor(String integration) {
        return modes.getOrDefault(integration, ReceiverMode.AUTO_SUCCESS);
    }

    public void setMode(String integration, ReceiverMode mode) {
        modes.put(integration, mode);
    }

    public void reset() {
        messages.clear();
        modes.clear();
        seenGlobalKeys.clear();
    }

    public record ReceivedMessage(String integration,
                                  String globalKey,
                                  boolean duplicate,
                                  Map<String, Object> headers,
                                  JsonNode payload,
                                  OffsetDateTime timestamp) {
    }
}
