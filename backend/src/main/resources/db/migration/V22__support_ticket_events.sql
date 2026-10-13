-- Ticket history: every status change, reopening, assignment and priority change, so the customer can follow
-- their request (they see status changes, reopenings and who picked it up) and staff see everything.
CREATE TABLE ticket_events
(
    id              UUID PRIMARY KEY,
    ticket_id       UUID         NOT NULL REFERENCES support_tickets (id),
    actor_id        UUID REFERENCES users (id),
    actor_is_staff  BOOLEAN      NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    from_value      VARCHAR(25),
    to_value        VARCHAR(25),
    subject_user_id UUID REFERENCES users (id),
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ticket_events_type_chk CHECK (type IN
        ('OPENED', 'STATUS_CHANGED', 'REOPENED', 'ASSIGNED', 'UNASSIGNED', 'PRIORITY_CHANGED'))
);
CREATE INDEX ticket_events_ticket_idx ON ticket_events (ticket_id, created_at);
CREATE TRIGGER ticket_events_immutable
    BEFORE UPDATE OR DELETE
    ON ticket_events
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();

-- History for tickets opened before events were recorded: when they were opened, resolved and closed.
INSERT INTO ticket_events (id, ticket_id, actor_id, actor_is_staff, type, to_value, created_at)
SELECT gen_random_uuid(), id, user_id, FALSE, 'OPENED', 'OPEN', created_at FROM support_tickets;
INSERT INTO ticket_events (id, ticket_id, actor_is_staff, type, to_value, created_at)
SELECT gen_random_uuid(), id, TRUE, 'STATUS_CHANGED', 'RESOLVED', resolved_at FROM support_tickets WHERE resolved_at IS NOT NULL;
INSERT INTO ticket_events (id, ticket_id, actor_is_staff, type, to_value, created_at)
SELECT gen_random_uuid(), id, TRUE, 'STATUS_CHANGED', 'CLOSED', closed_at FROM support_tickets WHERE closed_at IS NOT NULL;
