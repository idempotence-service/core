package ru.itmo.idempotency.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.idempotency.common.config.RouteModels;
import ru.itmo.idempotency.common.messaging.MessageModels;
import ru.itmo.idempotency.core.domain.IdempotencyEntity;
import ru.itmo.idempotency.core.domain.IdempotencyStatus;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncReplyProcessor {

    private final CoreJsonSupport coreJsonSupport;
    private final EventAuditService eventAuditService;
    private final IdempotencySearchService idempotencySearchService;
    private final IdempotencyService idempotencyService;

    public void handle(RouteModels.RouteSnapshot route, String rawMessage) {
        MessageModels.MessageEnvelope envelope;
        try {
            envelope = coreJsonSupport.parseEnvelope(rawMessage);
        } catch (IllegalArgumentException exception) {
            saveInvalidReply(route, rawMessage);
            return;
        }

        Map<String, Object> headers = envelope.headers();
        JsonNode payload = envelope.payload();
        String globalKey = headers != null && headers.get("globalKey") != null ? String.valueOf(headers.get("globalKey")) : null;
        if (globalKey == null || globalKey.isBlank() || payload == null || payload.get("result") == null) {
            saveInvalidReply(route, rawMessage);
            return;
        }

        processReply(route, globalKey, payload, coreJsonSupport.toJsonNode(headers));
    }

    @Transactional
    protected void processReply(RouteModels.RouteSnapshot route, String globalKey, JsonNode payload, JsonNode headers) {
        IdempotencyEntity entity = idempotencySearchService.acquireUniqueWaitIfLocked(globalKey).orElse(null);
        if (entity == null || entity.getStatus() != IdempotencyStatus.WAITING_ASYNC_RESPONSE) {
            eventAuditService.save(globalKey, route, AuditReasons.ORPHAN_REPLY, headers, payload);
            return;
        }

        String result = payload.path("result").asText();
        String resultDescription = payload.path("resultDescription").asText(null);
        if ("SUCCESS".equalsIgnoreCase(result)) {
            idempotencyService.changeStatus(entity, IdempotencyStatus.COMMITTED, resultDescription);
            return;
        }

        if (payload.path("needResend").asBoolean(false)) {
            idempotencyService.changeStatus(
                    entity,
                    IdempotencyStatus.RESERVED,
                    "Система-получатель запросила повторную отправку события: " + resultDescription
            );
            return;
        }

        idempotencyService.markAsError(entity, "Ошибка обработки события системой-получателем: " + resultDescription);
    }

    @Transactional
    protected void saveInvalidReply(RouteModels.RouteSnapshot route, String rawMessage) {
        log.warn("Invalid async reply received for route {}", route.integration());
        eventAuditService.save(null, route, AuditReasons.INVALID_RECEIVER_REPLY, null, coreJsonSupport.safeRawPayload(rawMessage));
    }
}
