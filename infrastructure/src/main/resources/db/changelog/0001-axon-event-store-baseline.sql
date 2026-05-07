-- ============================================================================
-- Axon Framework 4.9.x JPA event-store baseline
-- ----------------------------------------------------------------------------
-- This file captures the schema that Hibernate produced under ddl-auto=update
-- against PostgreSQL 15+ with the custom ByteaEnforcedPostgresSQLDialect.
--
-- This DDL was reconstructed from Axon 4.9.2's published JPA entity classes
-- (org.axonframework.eventhandling.AbstractDomainEventEntry,
--  org.axonframework.eventsourcing.eventstore.jpa.{DomainEventEntry,SnapshotEventEntry},
--  org.axonframework.eventhandling.tokenstore.jpa.TokenEntry,
--  org.axonframework.modelling.saga.repository.jpa.{SagaEntry,AssociationValueEntry}).
--
-- The integration test (SkyEndToEndIT) is the verification gate: it runs
-- Liquibase against an empty PostgreSQL Testcontainer, then Hibernate
-- `validate` against the runtime entity model. If this DDL drifts from what
-- Hibernate expects, the test fails immediately. Regenerate per the
-- instructions in tasks.md §2 if that happens.
-- ============================================================================

-- Domain events --------------------------------------------------------------
-- Hibernate 7 expects table-named sequences (`<table>_seq`) for
-- @GeneratedValue Long ids, not implicit BIGSERIAL backing sequences.
CREATE SEQUENCE domain_event_entry_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE domain_event_entry (
    global_index           BIGINT       PRIMARY KEY,
    event_identifier       VARCHAR(255) NOT NULL,
    meta_data              BYTEA,
    payload                BYTEA        NOT NULL,
    payload_revision       VARCHAR(255),
    payload_type           VARCHAR(255) NOT NULL,
    time_stamp             VARCHAR(255) NOT NULL,
    aggregate_identifier   VARCHAR(255) NOT NULL,
    sequence_number        BIGINT       NOT NULL,
    type                   VARCHAR(255),
    CONSTRAINT uk_domain_event_entry_event_identifier      UNIQUE (event_identifier),
    CONSTRAINT uk_domain_event_entry_aggregate_identifier  UNIQUE (aggregate_identifier, sequence_number)
);

-- Snapshots ------------------------------------------------------------------
CREATE TABLE snapshot_event_entry (
    aggregate_identifier   VARCHAR(255) NOT NULL,
    sequence_number        BIGINT       NOT NULL,
    type                   VARCHAR(255) NOT NULL,
    event_identifier       VARCHAR(255) NOT NULL,
    meta_data              BYTEA,
    payload                BYTEA        NOT NULL,
    payload_revision       VARCHAR(255),
    payload_type           VARCHAR(255) NOT NULL,
    time_stamp             VARCHAR(255) NOT NULL,
    PRIMARY KEY (aggregate_identifier, sequence_number, type),
    CONSTRAINT uk_snapshot_event_entry_event_identifier UNIQUE (event_identifier)
);

-- Tracking-token store -------------------------------------------------------
CREATE TABLE token_entry (
    processor_name VARCHAR(255) NOT NULL,
    segment        INTEGER      NOT NULL,
    owner          VARCHAR(255),
    timestamp      VARCHAR(255) NOT NULL,
    token          BYTEA,
    token_type     VARCHAR(255),
    PRIMARY KEY (processor_name, segment)
);

-- Saga store -----------------------------------------------------------------
CREATE TABLE saga_entry (
    saga_id         VARCHAR(255) NOT NULL,
    revision        VARCHAR(255),
    saga_type       VARCHAR(255),
    serialized_saga BYTEA,
    PRIMARY KEY (saga_id)
);

CREATE SEQUENCE association_value_entry_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE association_value_entry (
    id                BIGINT       PRIMARY KEY,
    association_key   VARCHAR(255) NOT NULL,
    association_value VARCHAR(255),
    saga_id           VARCHAR(255) NOT NULL,
    saga_type         VARCHAR(255)
);

CREATE INDEX ix_association_value_entry_saga
    ON association_value_entry (saga_id, saga_type);

CREATE INDEX ix_association_value_entry_lookup
    ON association_value_entry (saga_type, association_key, association_value);

-- Dead-letter queue ----------------------------------------------------------
-- DeadLetterEntry + the @Embedded DeadLetterEventEntry collapse into a single
-- table. Required even if the application doesn't actively use a JPA-backed
-- dead-letter queue, because Axon's @EntityScan picks up the entity class.
CREATE TABLE dead_letter_entry (
    dead_letter_id        VARCHAR(255) NOT NULL PRIMARY KEY,
    processing_group      VARCHAR(255) NOT NULL,
    sequence_identifier   VARCHAR(255) NOT NULL,
    sequence_index        BIGINT       NOT NULL,
    enqueued_at           TIMESTAMP    NOT NULL,
    last_touched          TIMESTAMP,
    processing_started    TIMESTAMP,
    cause_type            VARCHAR(255),
    cause_message         VARCHAR(1023),
    diagnostics           BYTEA,
    -- Embedded DeadLetterEventEntry columns:
    message_type          VARCHAR(255),
    event_identifier      VARCHAR(255),
    time_stamp            VARCHAR(255),
    payload_type          VARCHAR(255),
    payload_revision      VARCHAR(255),
    payload               BYTEA,
    meta_data             BYTEA,
    type                  VARCHAR(255),
    aggregate_identifier  VARCHAR(255),
    sequence_number       BIGINT,
    token_type            VARCHAR(255),
    token                 BYTEA
);

CREATE UNIQUE INDEX ix_dead_letter_entry_sequence
    ON dead_letter_entry (processing_group, sequence_identifier, sequence_index);
