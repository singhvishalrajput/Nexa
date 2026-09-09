package com.nexa.api.ledger;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "JOURNAL_ENTRIES")
class JournalEntryEntity {

    @Id
    @Column(name = "ID", length = 26, nullable = false)
    private String id;

    @Column(name = "ENTRY_REFERENCE", length = 80, nullable = false, unique = true)
    private String entryReference;

    @Column(name = "ENTRY_TYPE", length = 40, nullable = false)
    private String entryType;

    @Column(name = "DESCRIPTION", length = 500, nullable = false)
    private String description;

    @Column(name = "POSTED_AT", nullable = false)
    private OffsetDateTime postedAt;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    protected JournalEntryEntity() {
    }

    JournalEntryEntity(
            String id,
            String entryReference,
            String entryType,
            String description,
            OffsetDateTime now) {
        this.id = id;
        this.entryReference = entryReference;
        this.entryType = entryType;
        this.description = description;
        this.postedAt = now;
        this.createdAt = now;
    }

    String getId() { return id; }
}
