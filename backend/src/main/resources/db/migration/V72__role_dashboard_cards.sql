-- =====================================================================================
-- V72: Admins decide which dashboard cards each role shows.
--
-- A role without a row shows every card its permissions allow (the default). Someone with several roles gets every
-- card any of them allows. Permissions still decide what a card can hold; this only narrows what is shown.
-- =====================================================================================

CREATE TABLE role_dashboard_cards
(
    role_id    UUID PRIMARY KEY REFERENCES roles (id) ON DELETE CASCADE,
    cards      TEXT[]      NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID REFERENCES users (id)
);
