package com.nexa.api.shared.audit;

import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** Immutable audit record, owned by the platform service boundary. */
@Entity
@Table(name = "AUDIT_EVENTS")
class AuditEventEntity {
    @Id @Column(name = "ID", length = 26) private String id;
    @Column(name = "ACTOR_USER_ID", length = 26) private String actorUserId;
    @Column(name = "AGGREGATE_TYPE", length = 80, nullable = false) private String aggregateType;
    @Column(name = "AGGREGATE_ID", length = 26, nullable = false) private String aggregateId;
    @Column(name = "EVENT_TYPE", length = 120, nullable = false) private String eventType;
    @Column(name = "CORRELATION_ID", length = 100) private String correlationId;
    @Lob @Column(name = "PAYLOAD") private String payload;
    @Column(name = "OCCURRED_AT", nullable = false) private OffsetDateTime occurredAt;
    protected AuditEventEntity() { }
}
