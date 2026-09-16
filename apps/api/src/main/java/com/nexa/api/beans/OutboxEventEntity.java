package com.nexa.api.beans;


import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** Transactional-outbox record for reliable future publication to other services. */
@Entity
@Table(name = "OUTBOX_EVENTS")
class OutboxEventEntity {
    @Id @Column(name = "ID", length = 26) private String id;
    @Column(name = "AGGREGATE_TYPE", length = 80, nullable = false) private String aggregateType;
    @Column(name = "AGGREGATE_ID", length = 26, nullable = false) private String aggregateId;
    @Column(name = "EVENT_TYPE", length = 120, nullable = false) private String eventType;
    @Lob @Column(name = "PAYLOAD", nullable = false) private String payload;
    @Column(name = "OCCURRED_AT", nullable = false) private OffsetDateTime occurredAt;
    @Column(name = "PUBLISHED_AT") private OffsetDateTime publishedAt;
    @Column(name = "ATTEMPTS", nullable = false) private Integer attempts;
    protected OutboxEventEntity() { }
}
