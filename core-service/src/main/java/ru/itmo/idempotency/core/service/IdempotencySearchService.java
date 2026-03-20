package ru.itmo.idempotency.core.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.idempotency.core.domain.IdempotencyEntity;
import ru.itmo.idempotency.core.domain.IdempotencyStatus;
import ru.itmo.idempotency.core.repository.IdempotencyRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencySearchService {

    private final IdempotencyRepository idempotencyRepository;

    @Transactional
    public Optional<IdempotencyEntity> acquireFirstNotLocked(IdempotencyStatus status) {
        return idempotencyRepository.lockFirstByStatus(status.name());
    }

    @Transactional
    public Optional<IdempotencyEntity> acquireUniqueWaitIfLocked(String globalKey) {
        return idempotencyRepository.findByGlobalKeyForUpdate(globalKey);
    }

    @Transactional(readOnly = true)
    public List<IdempotencyEntity> findCommittedBatchForCleanup(OffsetDateTime threshold, int batchSize) {
        return idempotencyRepository.findByStatusAndUpdateDateBefore(
                IdempotencyStatus.COMMITTED,
                threshold,
                org.springframework.data.domain.PageRequest.of(0, batchSize, org.springframework.data.domain.Sort.by("updateDate").ascending())
        );
    }
}
