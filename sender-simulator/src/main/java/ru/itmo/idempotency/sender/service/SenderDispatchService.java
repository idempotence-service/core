package ru.itmo.idempotency.sender.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.itmo.idempotency.common.config.RouteCatalog;
import ru.itmo.idempotency.common.config.RouteModels;
import ru.itmo.idempotency.common.kafka.KafkaJsonProducerRegistry;
import ru.itmo.idempotency.common.messaging.MessageModels;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SenderDispatchService {

    private final RouteCatalog routeCatalog;
    private final KafkaJsonProducerRegistry kafkaJsonProducerRegistry;
    private final SenderStateService senderStateService;

    public String send(String integration, String uid, Map<String, Object> headers, JsonNode payload, int duplicates) {
        RouteModels.RouteSnapshot route = routeCatalog.getRequiredRoute(integration);
        String resolvedUid = uid == null || uid.isBlank() ? UUID.randomUUID().toString() : uid;

        Map<String, Object> resolvedHeaders = new LinkedHashMap<>();
        if (headers != null) {
            resolvedHeaders.putAll(headers);
        }
        resolvedHeaders.put("uid", resolvedUid);

        MessageModels.MessageEnvelope envelope = new MessageModels.MessageEnvelope(resolvedHeaders, payload);
        for (int index = 0; index < Math.max(duplicates, 1); index++) {
            kafkaJsonProducerRegistry.send(route.inbound().bootstrapServers(), route.inbound().topic(), resolvedUid, envelope);
            senderStateService.recordSent(integration, resolvedUid, resolvedHeaders, payload);
        }
        return resolvedUid;
    }
}
