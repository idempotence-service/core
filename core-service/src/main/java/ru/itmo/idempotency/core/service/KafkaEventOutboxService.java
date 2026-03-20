package ru.itmo.idempotency.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.idempotency.common.config.RouteModels;
import ru.itmo.idempotency.core.domain.KafkaEventOutboxEntity;
import ru.itmo.idempotency.core.domain.OutboxStatus;
import ru.itmo.idempotency.core.domain.ProcessingResult;
import ru.itmo.idempotency.core.repository.KafkaEventOutboxRepository;

@Service
@RequiredArgsConstructor
public class KafkaEventOutboxService {

    private final KafkaEventOutboxRepository kafkaEventOutboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public KafkaEventOutboxEntity save(String globalKey,
                                       RouteModels.RouteSnapshot snapshot,
                                       ProcessingResult result,
                                       String resultDescription) {
        return kafkaEventOutboxRepository.save(KafkaEventOutboxEntity.builder()
                .globalKey(globalKey)
                .serviceName(snapshot.service())
                .integrationName(snapshot.integration())
                .yamlSnapshot(objectMapper.valueToTree(snapshot))
                .status(OutboxStatus.NEW)
                .result(result)
                .resultDescription(DescriptionUtils.limit(resultDescription))
                .build());
    }

    @Transactional
    public KafkaEventOutboxEntity changeStatus(KafkaEventOutboxEntity entity, OutboxStatus status, String description) {
        entity.setStatus(status);
        entity.setStatusDescription(DescriptionUtils.limit(description));
        return kafkaEventOutboxRepository.save(entity);
    }

    @Transactional
    public KafkaEventOutboxEntity markAsError(KafkaEventOutboxEntity entity, String description) {
        return changeStatus(entity, OutboxStatus.ERROR, description);
    }
}
