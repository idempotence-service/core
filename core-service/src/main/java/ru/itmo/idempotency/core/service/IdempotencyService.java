package ru.itmo.idempotency.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.idempotency.common.config.RouteModels;
import ru.itmo.idempotency.core.domain.IdempotencyEntity;
import ru.itmo.idempotency.core.domain.IdempotencyStatus;
import ru.itmo.idempotency.core.repository.IdempotencyRepository;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public IdempotencyEntity save(String globalKey,
                                  String sourceUid,
                                  RouteModels.RouteSnapshot snapshot,
                                  JsonNode headers,
                                  JsonNode payload) {
        IdempotencyEntity entity = IdempotencyEntity.builder()
                .globalKey(globalKey)
                .sourceUid(sourceUid)
                .serviceName(snapshot.service())
                .integrationName(snapshot.integration())
                .yamlSnapshot(objectMapper.valueToTree(snapshot))
                .headers(headers)
                .payload(payload)
                .status(IdempotencyStatus.RESERVED)
                .build();
        return idempotencyRepository.saveAndFlush(entity);
    }

    @Transactional
    public IdempotencyEntity changeStatus(IdempotencyEntity entity, IdempotencyStatus status, String description) {
        entity.setStatus(status);
        entity.setStatusDescription(DescriptionUtils.limit(description));
        return idempotencyRepository.save(entity);
    }

    @Transactional
    public IdempotencyEntity markAsError(IdempotencyEntity entity, String description) {
        return changeStatus(entity, IdempotencyStatus.ERROR, description);
    }

    @Transactional
    public void delete(Collection<IdempotencyEntity> entities) {
        idempotencyRepository.deleteAllInBatch(entities);
    }
}
