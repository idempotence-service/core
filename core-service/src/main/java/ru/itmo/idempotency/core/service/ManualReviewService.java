package ru.itmo.idempotency.core.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.idempotency.core.domain.IdempotencyEntity;
import ru.itmo.idempotency.core.domain.IdempotencyStatus;
import ru.itmo.idempotency.core.repository.IdempotencyRepository;
import ru.itmo.idempotency.core.web.ManualReviewDtos;

@Service
@RequiredArgsConstructor
public class ManualReviewService {

    private final IdempotencyRepository idempotencyRepository;
    private final IdempotencySearchService idempotencySearchService;
    private final IdempotencyService idempotencyService;

    @Transactional(readOnly = true)
    public Page<ManualReviewDtos.ErrorEventItem> getErrorEvents(int page, int limit, Sort.Direction direction) {
        return idempotencyRepository.findByStatus(
                IdempotencyStatus.ERROR,
                PageRequest.of(page, limit, Sort.by(direction, "createDate"))
        ).map(this::toErrorEventItem);
    }

    @Transactional
    public String restart(String globalKey) {
        IdempotencyEntity entity = idempotencySearchService.acquireUniqueWaitIfLocked(globalKey)
                .orElseThrow(() -> new EventNotFoundException("Задача с данными " + globalKey + " не найдена"));
        if (entity.getStatus() != IdempotencyStatus.ERROR) {
            return "Задача уже перезапущена";
        }
        idempotencyService.changeStatus(entity, IdempotencyStatus.RESERVED, null);
        return "Задача успешно перезапущена";
    }

    @Transactional(readOnly = true)
    public ManualReviewDtos.EventDetails getByGlobalKey(String globalKey) {
        IdempotencyEntity entity = idempotencyRepository.findById(globalKey)
                .orElseThrow(() -> new EventNotFoundException("Событие с данными " + globalKey + " не найдено"));
        return new ManualReviewDtos.EventDetails(
                entity.getGlobalKey(),
                entity.getSourceUid(),
                entity.getServiceName(),
                entity.getIntegrationName(),
                entity.getStatus().name(),
                entity.getStatusDescription(),
                entity.getCreateDate(),
                entity.getUpdateDate(),
                entity.getPayload(),
                entity.getHeaders()
        );
    }

    private ManualReviewDtos.ErrorEventItem toErrorEventItem(IdempotencyEntity entity) {
        return new ManualReviewDtos.ErrorEventItem(
                entity.getStatus().name(),
                entity.getGlobalKey(),
                entity.getServiceName(),
                entity.getIntegrationName()
        );
    }
}
