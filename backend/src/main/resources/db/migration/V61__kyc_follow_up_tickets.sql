-- =====================================================================================
-- V61: A support ticket for investors who have not verified their identity a day after registering.
--
-- The ticket is opened by the system, so its first message has no author (shown as SeaLease support).
-- kyc_follow_ups records the one ticket each investor gets, so it is never opened twice, and lets the ticket be
-- resolved once they submit their documents.
-- =====================================================================================

ALTER TABLE ticket_messages
    ALTER COLUMN author_id DROP NOT NULL;

CREATE TABLE kyc_follow_ups
(
    user_id   UUID PRIMARY KEY REFERENCES users (id),
    ticket_id UUID        NOT NULL REFERENCES support_tickets (id),
    opened_at TIMESTAMPTZ NOT NULL
);
