package ru.itmo.idempotency.core.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
@Table(name = "event_audit")
public class EventAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "global_key", length = 512)
    private String globalKey;

    @Column(name = "service_name", length = 255)
    private String serviceName;

    @Column(name = "integration_name", length = 255)
    private String integrationName;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", columnDefinition = "jsonb")
    private JsonNode headers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private JsonNode payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "yaml_snapshot", columnDefinition = "jsonb")
    private JsonNode yamlSnapshot;

    @Column(name = "create_date", nullable = false)
    private OffsetDateTime createDate;

    @PrePersist
    void prePersist() {
        createDate = createDate == null ? OffsetDateTime.now(ZoneOffset.UTC) : createDate;
    }
}
