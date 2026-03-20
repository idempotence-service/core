package ru.itmo.idempotency.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.RetriableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.itmo.idempotency.common.config.RouteModels;
import ru.itmo.idempotency.common.kafka.KafkaJsonProducerRegistry;
import ru.itmo.idempotency.common.messaging.MessageModels;
import ru.itmo.idempotency.core.domain.IdempotencyEntity;
import ru.itmo.idempotency.core.domain.IdempotencyStatus;

import java.util.Iterator;
import java.util.Map;

@Slf4j
@Service
public class ReceiverDispatchProcessor {

    private final IdempotencySearchService idempotencySearchService;
    private final IdempotencyService idempotencyService;
    private final CoreJsonSupport coreJsonSupport;
    private final KafkaJsonProducerRegistry kafkaJsonProducerRegistry;
    private final TransactionTemplate transactionTemplate;

    public ReceiverDispatchProcessor(IdempotencySearchService idempotencySearchService,
                                     IdempotencyService idempotencyService,
                                     CoreJsonSupport coreJsonSupport,
                                     KafkaJsonProducerRegistry kafkaJsonProducerRegistry,
                                     PlatformTransactionManager transactionManager) {
        this.idempotencySearchService = idempotencySearchService;
        this.idempotencyService = idempotencyService;
        this.coreJsonSupport = coreJsonSupport;
        this.kafkaJsonProducerRegistry = kafkaJsonProducerRegistry;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public int processBatch(int batchSize) {
        int processed = 0;
        while (processed < batchSize) {
            Boolean currentProcessed = transactionTemplate.execute(status -> processSingle());
            if (!Boolean.TRUE.equals(currentProcessed)) {
                break;
            }
            processed++;
        }
        return processed;
    }

    private boolean processSingle() {
        IdempotencyEntity entity = idempotencySearchService.acquireFirstNotLocked(IdempotencyStatus.RESERVED).orElse(null);
        if (entity == null) {
            return false;
        }

        RouteModels.RouteSnapshot snapshot = coreJsonSupport.parseSnapshot(entity.getYamlSnapshot());
        if (snapshot.replyIn() == null) {
            idempotencyService.markAsError(entity, "Не найден канал reply_in в сохраненном yaml_snapshot");
            return true;
        }

        try {
            kafkaJsonProducerRegistry.send(
                    snapshot.replyIn().bootstrapServers(),
                    snapshot.replyIn().topic(),
                    entity.getGlobalKey(),
                    new MessageModels.MessageEnvelope(mergeHeaders(entity), entity.getPayload())
            );

            if (snapshot.replyOut() == null) {
                idempotencyService.changeStatus(entity, IdempotencyStatus.COMMITTED, null);
            } else {
                idempotencyService.changeStatus(entity, IdempotencyStatus.WAITING_ASYNC_RESPONSE, null);
            }
        } catch (Exception exception) {
            if (isRetriable(exception)) {
                log.warn("Temporary delivery issue for {}", entity.getGlobalKey(), exception);
                return false;
            }

            idempotencyService.markAsError(
                    entity,
                    "Произошла ошибка при отправке события системе-получателю в "
                            + snapshot.replyIn().topic() + ": " + exception.getMessage()
            );
            log.warn("Permanent delivery issue for {}", entity.getGlobalKey(), exception);
        }

        return true;
    }

    private Map<String, Object> mergeHeaders(IdempotencyEntity entity) {
        ObjectNode headers = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        headers.put("globalKey", entity.getGlobalKey());
        headers.put("uid", entity.getSourceUid());
        headers.put("service", entity.getServiceName());
        headers.put("integration", entity.getIntegrationName());

        Iterator<Map.Entry<String, JsonNode>> fields = entity.getHeaders().fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            headers.set(field.getKey(), field.getValue());
        }

        return coreJsonSupport.toMap(headers);
    }

    private boolean isRetriable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof RetriableException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
