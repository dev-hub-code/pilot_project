-- =====================================================================================
-- V11: Support tickets (messages, internal notes, attachments, response targets) and in-app
-- notifications.
--
-- Status: OPEN → (agent replies) WAITING_ON_CUSTOMER → (customer replies) OPEN … → RESOLVED
-- → CLOSED. A reply to a resolved ticket reopens it; CLOSED is final.
-- =====================================================================================

CREATE SEQUENCE ticket_number_seq START WITH 1;

CREATE TABLE support_tickets
(
    id                    UUID PRIMARY KEY,
    reference             VARCHAR(20)  NOT NULL,
    user_id               UUID         NOT NULL REFERENCES users (id),
    subject               VARCHAR(200) NOT NULL,
    category              VARCHAR(20)  NOT NULL,
    -- Optional link to one of the requester's own records.
    related_type          VARCHAR(20),
    related_id            UUID,
    related_label         VARCHAR(60),
    status                VARCHAR(25)  NOT NULL,
    priority              VARCHAR(10)  NOT NULL,
    assignee_id           UUID REFERENCES users (id),
    first_response_due_at TIMESTAMPTZ  NOT NULL,
    first_responded_at    TIMESTAMPTZ,
    last_message_at       TIMESTAMPTZ  NOT NULL,
    resolved_at           TIMESTAMPTZ,
    closed_at             TIMESTAMPTZ,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT support_tickets_reference_uk UNIQUE (reference),
    CONSTRAINT support_tickets_category_chk CHECK (category IN
        ('ACCOUNT', 'INVESTMENT', 'PAYMENT', 'EARNINGS', 'WITHDRAWAL', 'REFERRAL', 'OTHER')),
    CONSTRAINT support_tickets_related_chk CHECK (
        (related_type IS NULL AND related_id IS NULL AND related_label IS NULL)
            OR (related_type IN ('ORDER', 'WITHDRAWAL', 'HOLDING') AND related_id IS NOT NULL AND related_label IS NOT NULL)),
    CONSTRAINT support_tickets_status_chk CHECK (status IN ('OPEN', 'WAITING_ON_CUSTOMER', 'RESOLVED', 'CLOSED')),
    CONSTRAINT support_tickets_priority_chk CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT support_tickets_resolved_chk CHECK (status <> 'RESOLVED' OR resolved_at IS NOT NULL),
    CONSTRAINT support_tickets_closed_chk CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);
CREATE INDEX support_tickets_user_idx ON support_tickets (user_id, last_message_at DESC);
CREATE INDEX support_tickets_queue_idx ON support_tickets (status, first_response_due_at);
CREATE INDEX support_tickets_assignee_idx ON support_tickets (assignee_id, status);

-- Internal notes are visible to staff only.
CREATE TABLE ticket_messages
(
    id          UUID PRIMARY KEY,
    ticket_id   UUID          NOT NULL REFERENCES support_tickets (id),
    author_id   UUID          NOT NULL REFERENCES users (id),
    from_staff  BOOLEAN       NOT NULL,
    internal    BOOLEAN       NOT NULL,
    body        VARCHAR(8000) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ticket_messages_internal_chk CHECK (NOT internal OR from_staff)
);
CREATE INDEX ticket_messages_ticket_idx ON ticket_messages (ticket_id, created_at);
CREATE TRIGGER ticket_messages_immutable
    BEFORE UPDATE OR DELETE
    ON ticket_messages
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();

-- Files are stored (encrypted, type-checked) in stored_documents.
CREATE TABLE ticket_attachments
(
    id           UUID PRIMARY KEY,
    ticket_id    UUID         NOT NULL REFERENCES support_tickets (id),
    message_id   UUID         NOT NULL REFERENCES ticket_messages (id),
    document_id  UUID         NOT NULL REFERENCES stored_documents (id),
    filename     VARCHAR(200) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ticket_attachments_document_uk UNIQUE (document_id)
);
CREATE INDEX ticket_attachments_message_idx ON ticket_attachments (message_id);
CREATE TRIGGER ticket_attachments_immutable
    BEFORE UPDATE OR DELETE
    ON ticket_attachments
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();

-- ------------------------------------------------------------------- notifications --
CREATE TABLE notifications
(
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id),
    type       VARCHAR(40)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       VARCHAR(1000),
    link       VARCHAR(300),
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX notifications_user_idx ON notifications (user_id, created_at DESC);
CREATE INDEX notifications_unread_idx ON notifications (user_id) WHERE read_at IS NULL;
