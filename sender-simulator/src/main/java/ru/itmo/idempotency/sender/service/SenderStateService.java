package ru.itmo.idempotency.sender.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SenderStateService {

    private final List<ObservedMessage> sentMessages = new CopyOnWriteArrayList<>();
    private final List<ObservedMessage> receivedReplies = new CopyOnWriteArrayList<>();

    public void recordSent(String integration, String uid, Map<String, Object> headers, JsonNode payload) {
        sentMessages.add(new ObservedMessage(integration, uid, null, headers, payload, OffsetDateTime.now(ZoneOffset.UTC)));
    }

    public void recordReply(String integration, String globalKey, Map<String, Object> headers, JsonNode payload) {
        receivedReplies.add(new ObservedMessage(integration, null, globalKey, headers, payload, OffsetDateTime.now(ZoneOffset.UTC)));
    }

    public List<ObservedMessage> sentMessages() {
        return new ArrayList<>(sentMessages);
    }

    public List<ObservedMessage> receivedReplies() {
        return new ArrayList<>(receivedReplies);
    }

    public void reset() {
        sentMessages.clear();
        receivedReplies.clear();
    }

    public record ObservedMessage(String integration,
                                  String uid,
                                  String globalKey,
                                  Map<String, Object> headers,
                                  JsonNode payload,
                                  OffsetDateTime timestamp) {
    }
}
