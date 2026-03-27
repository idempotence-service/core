package ru.itmo.idempotency.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.itmo.idempotency.core.domain.KafkaEventOutboxEntity;

import java.util.Optional;

public interface KafkaEventOutboxRepository extends JpaRepository<KafkaEventOutboxEntity, Long> {

    @Query(value = """
            SELECT *
            FROM kafka_event_outbox
            WHERE status = :status
            ORDER BY create_date
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<KafkaEventOutboxEntity> lockFirstByStatus(@Param("status") String status);
}
