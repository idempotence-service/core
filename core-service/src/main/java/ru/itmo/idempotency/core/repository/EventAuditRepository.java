package ru.itmo.idempotency.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itmo.idempotency.core.domain.EventAuditEntity;

public interface EventAuditRepository extends JpaRepository<EventAuditEntity, Long> {
}
