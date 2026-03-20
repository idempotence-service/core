package ru.itmo.idempotency.core.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "idempotency")
public class IdempotencyEntity {

    @Id
    @Column(name = "global_key", nullable = false, length = 512)
    private String globalKey;

    @Column(name = "source_uid", nullable = false, length = 255)
    private String sourceUid;

    @Column(name = "service_name", nullable = false, length = 255)
    private String serviceName;

    @Column(name = "integration_name", nullable = false, length = 255)
    private String integrationName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "yaml_snapshot", nullable = false, columnDefinition = "jsonb")
    private JsonNode yamlSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", nullable = false, columnDefinition = "jsonb")
    private JsonNode headers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private IdempotencyStatus status;

    @Column(name = "status_description", length = 255)
    private String statusDescription;

    @Column(name = "create_date", nullable = false)
    private OffsetDateTime createDate;

    @Column(name = "update_date", nullable = false)
    private OffsetDateTime updateDate;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        createDate = createDate == null ? now : createDate;
        updateDate = updateDate == null ? now : updateDate;
    }

    @PreUpdate
    void preUpdate() {
        updateDate = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
