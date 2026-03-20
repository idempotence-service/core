package ru.itmo.idempotency.core.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.idempotency.core.domain.KafkaEventOutboxEntity;
import ru.itmo.idempotency.core.domain.OutboxStatus;
import ru.itmo.idempotency.core.repository.KafkaEventOutboxRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class KafkaEventOutboxSearchService {

    private final KafkaEventOutboxRepository kafkaEventOutboxRepository;

    @Transactional
    public Optional<KafkaEventOutboxEntity> acquireFirstNotLocked() {
        return kafkaEventOutboxRepository.lockFirstByStatus(OutboxStatus.NEW.name());
    }
}
