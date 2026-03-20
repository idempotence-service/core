package ru.itmo.idempotency.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.itmo.idempotency.core.config.CoreProperties;
import ru.itmo.idempotency.core.domain.IdempotencyEntity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupService {

    private final CoreProperties coreProperties;
    private final IdempotencySearchService idempotencySearchService;
    private final IdempotencyService idempotencyService;

    public int cleanupCommitted() {
        int deleted = 0;
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minus(coreProperties.getCleanup().getRetention());
        while (true) {
            List<IdempotencyEntity> batch = idempotencySearchService.findCommittedBatchForCleanup(
                    threshold,
                    coreProperties.getCleanup().getBatchSize()
            );
            if (batch.isEmpty()) {
                break;
            }
            idempotencyService.delete(batch);
            deleted += batch.size();
        }
        if (deleted > 0) {
            log.info("Deleted {} committed idempotency records", deleted);
        }
        return deleted;
    }
}
