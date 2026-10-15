-- =====================================================================================
-- V44: Indexes for reading the audit log back (security dashboard, audit log search).
--
-- The log is searched newest first with no other filter, and failed sign-ins are grouped by the address
-- they came from; the existing indexes cover searches by entity, actor and action.
-- =====================================================================================
CREATE INDEX audit_logs_created_idx ON audit_logs (created_at DESC);
CREATE INDEX audit_logs_ip_idx ON audit_logs (ip_address, created_at DESC) WHERE ip_address IS NOT NULL;
