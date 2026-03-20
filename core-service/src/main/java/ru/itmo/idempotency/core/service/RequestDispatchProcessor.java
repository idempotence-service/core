package ru.itmo.idempotency.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.itmo.idempotency.common.config.RouteModels;
import ru.itmo.idempotency.common.kafka.KafkaJsonProducerRegistry;
import ru.itmo.idempotency.common.messaging.MessageModels;
import ru.itmo.idempotency.core.domain.KafkaEventOutboxEntity;
import ru.itmo.idempotency.core.domain.OutboxStatus;

import java.util.Map;

@Slf4j
@Service
public class RequestDispatchProcessor {

    private final KafkaEventOutboxSearchService kafkaEventOutboxSearchService;
    private final KafkaEventOutboxService kafkaEventOutboxService;
    private final CoreJsonSupport coreJsonSupport;
    private final KafkaJsonProducerRegistry kafkaJsonProducerRegistry;
    private final TransactionTemplate transactionTemplate;

    public RequestDispatchProcessor(KafkaEventOutboxSearchService kafkaEventOutboxSearchService,
                                    KafkaEventOutboxService kafkaEventOutboxService,
                                    CoreJsonSupport coreJsonSupport,
                                    KafkaJsonProducerRegistry kafkaJsonProducerRegistry,
                                    PlatformTransactionManager transactionManager) {
        this.kafkaEventOutboxSearchService = kafkaEventOutboxSearchService;
        this.kafkaEventOutboxService = kafkaEventOutboxService;
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
        KafkaEventOutboxEntity outboxEntity = kafkaEventOutboxSearchService.acquireFirstNotLocked().orElse(null);
        if (outboxEntity == null) {
            return false;
        }

        RouteModels.RouteSnapshot snapshot = coreJsonSupport.parseSnapshot(outboxEntity.getYamlSnapshot());
        if (snapshot.requestOut() == null) {
            kafkaEventOutboxService.markAsError(outboxEntity, "Не найден канал request_out в сохраненном yaml_snapshot");
            return true;
        }

        try {
            kafkaJsonProducerRegistry.send(
                    snapshot.requestOut().bootstrapServers(),
                    snapshot.requestOut().topic(),
                    outboxEntity.getGlobalKey(),
                    new MessageModels.MessageEnvelope(
                            Map.of("globalKey", outboxEntity.getGlobalKey()),
                            coreJsonSupport.toJsonNode(new MessageModels.TechnicalResponsePayload(
                                    outboxEntity.getResult().name(),
                                    outboxEntity.getResultDescription()
                            ))
                    )
            );
            kafkaEventOutboxService.changeStatus(outboxEntity, OutboxStatus.DONE, null);
        } catch (Exception exception) {
            kafkaEventOutboxService.markAsError(
                    outboxEntity,
                    "Произошла ошибка при отправке ответа в " + snapshot.requestOut().topic() + ": " + exception.getMessage()
            );
            log.warn("Failed to dispatch technical response for {}", outboxEntity.getGlobalKey(), exception);
        }
        return true;
    }
}
