-- =====================================================================================
-- V71: Each employee arranges their own overview: which cards show, in what order.
--
-- cards are the shown cards in order, hidden the ones taken off (kept in the "add cards" tray). A card in neither
-- (one the employee could not see when they saved) shows at the end. What a card holds is still limited by
-- permissions: the layout only arranges cards.
-- =====================================================================================

CREATE TABLE dashboard_layouts
(
    user_id    UUID PRIMARY KEY REFERENCES users (id),
    cards      TEXT[]      NOT NULL,
    hidden     TEXT[]      NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
