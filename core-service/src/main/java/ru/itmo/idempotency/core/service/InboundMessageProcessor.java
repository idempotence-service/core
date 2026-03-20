package ru.itmo.idempotency.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.idempotency.common.config.RouteModels;
import ru.itmo.idempotency.common.messaging.MessageModels;
import ru.itmo.idempotency.core.domain.ProcessingResult;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboundMessageProcessor {

    private final CoreJsonSupport coreJsonSupport;
    private final IdempotencyService idempotencyService;
    private final KafkaEventOutboxService kafkaEventOutboxService;
    private final EventAuditService eventAuditService;

    public void handle(RouteModels.RouteSnapshot route, String rawMessage) {
        MessageModels.MessageEnvelope envelope;
        try {
            envelope = coreJsonSupport.parseEnvelope(rawMessage);
        } catch (IllegalArgumentException exception) {
            saveInvalidInbound(route, rawMessage);
            return;
        }

        Map<String, Object> headers = envelope.headers();
        JsonNode payload = envelope.payload();
        String uid = headers != null && headers.get("uid") != null ? String.valueOf(headers.get("uid")) : null;
        if (uid == null || uid.isBlank() || payload == null || payload.isNull()) {
            saveInvalidInbound(route, rawMessage);
            return;
        }

        String globalKey = route.service() + ":" + route.integration() + ":" + uid;
        JsonNode userHeaders = coreJsonSupport.headersWithoutUid(headers);
        try {
            saveUniqueOperation(globalKey, uid, route, userHeaders, payload);
        } catch (DataIntegrityViolationException exception) {
            saveDuplicateOperation(globalKey, route, coreJsonSupport.toJsonNode(headers), payload);
        }
    }

    @Transactional
    protected void saveUniqueOperation(String globalKey,
                                       String uid,
                                       RouteModels.RouteSnapshot route,
                                       JsonNode userHeaders,
                                       JsonNode payload) {
        idempotencyService.save(globalKey, uid, route, userHeaders, payload);
        if (route.requestOut() != null) {
            kafkaEventOutboxService.save(globalKey, route, ProcessingResult.SUCCESS, "Событие успешно получено");
        }
        eventAuditService.save(globalKey, route, AuditReasons.IDEMPOTENCY_PASSED, userHeaders, payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void saveDuplicateOperation(String globalKey,
                                          RouteModels.RouteSnapshot route,
                                          JsonNode headers,
                                          JsonNode payload) {
        if (route.requestOut() != null) {
            kafkaEventOutboxService.save(globalKey, route, ProcessingResult.FAIL, AuditReasons.IDEMPOTENCY_FAILED);
        }
        eventAuditService.save(globalKey, route, AuditReasons.IDEMPOTENCY_FAILED, headers, payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void saveInvalidInbound(RouteModels.RouteSnapshot route, String rawMessage) {
        log.warn("Invalid inbound message received for route {}", route.integration());
        eventAuditService.save(null, route, AuditReasons.INVALID_INBOUND_EVENT, null, coreJsonSupport.safeRawPayload(rawMessage));
    }
}
