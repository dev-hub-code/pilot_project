-- =====================================================================================
-- V62: When a finance task was done (marked paid or failed), for the finance dashboard's "completed" counts.
--
-- closed_at cannot serve: a failed payment stays open (held) until resolved. Earlier tasks take the time they were
-- first marked from the audit log, else their closing time.
-- =====================================================================================

ALTER TABLE withdrawals
    ADD COLUMN task_done_at TIMESTAMPTZ;

UPDATE withdrawals w
SET task_done_at = COALESCE((SELECT min(a.created_at)
                             FROM audit_logs a
                             WHERE a.action IN ('WITHDRAWAL_PAID', 'WITHDRAWAL_FAILED')
                               AND a.entity_id = w.id::text), w.closed_at)
WHERE w.batch_id IS NOT NULL
  AND w.status IN ('PAID', 'FAILED', 'RETURNED');

CREATE INDEX withdrawals_task_done_idx ON withdrawals (task_done_at) WHERE task_done_at IS NOT NULL;
