-- A failed bank payment that finance then pays by hand keeps its failure reason (V41 allowed it only while FAILED
-- or RETURNED). A FAILED withdrawal still always has one.
ALTER TABLE withdrawals
    DROP CONSTRAINT withdrawals_failed_chk,
    ADD CONSTRAINT withdrawals_failed_chk CHECK (
        (status <> 'FAILED' OR failure_reason IS NOT NULL)
            AND (failure_reason IS NULL OR status IN ('FAILED', 'RETURNED', 'PAID')));
