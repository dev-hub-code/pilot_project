-- =====================================================================================
-- V3: Append-only audit log for security-sensitive and financial operations.
-- =====================================================================================
CREATE TABLE audit_logs
(
    id             UUID PRIMARY KEY,
    actor_user_id  UUID,
    action         VARCHAR(64)  NOT NULL,
    entity_type    VARCHAR(64)  NOT NULL,
    entity_id      VARCHAR(64),
    old_value      JSONB,
    new_value      JSONB,
    ip_address     VARCHAR(45),
    user_agent     VARCHAR(512),
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX audit_logs_entity_idx ON audit_logs (entity_type, entity_id, created_at DESC);
CREATE INDEX audit_logs_actor_idx ON audit_logs (actor_user_id, created_at DESC);
CREATE INDEX audit_logs_action_idx ON audit_logs (action, created_at DESC);

CREATE TRIGGER audit_logs_immutable
    BEFORE UPDATE OR DELETE
    ON audit_logs
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();
