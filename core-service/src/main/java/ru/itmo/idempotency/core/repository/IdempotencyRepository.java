package ru.itmo.idempotency.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.itmo.idempotency.core.domain.IdempotencyEntity;
import ru.itmo.idempotency.core.domain.IdempotencyStatus;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface IdempotencyRepository extends JpaRepository<IdempotencyEntity, String> {

    @Query(value = """
            SELECT *
            FROM idempotency
            WHERE status = :status
            ORDER BY create_date
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<IdempotencyEntity> lockFirstByStatus(@Param("status") String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entity from IdempotencyEntity entity where entity.globalKey = :globalKey")
    Optional<IdempotencyEntity> findByGlobalKeyForUpdate(@Param("globalKey") String globalKey);

    Page<IdempotencyEntity> findByStatus(IdempotencyStatus status, Pageable pageable);

    List<IdempotencyEntity> findByStatusAndUpdateDateBefore(IdempotencyStatus status,
                                                            OffsetDateTime updateDate,
                                                            Pageable pageable);
}
