-- One bank transfer or cash receipt is evidence for one payment: a reference finance records when confirming a deposit,
-- or a bank reference when marking a withdrawal paid, may not be recorded for another one (any case). Money that reaches the payment
-- gateway for a reference that is none of ours is recorded for finance, not only logged.

-- ------------------------------------------------------------------------------------------- existing data
-- The indexes below cannot be built over duplicates. Stop with an explanation rather than a bare index error; finance
-- must correct such records first (they are evidence used twice).
DO $$
DECLARE
    deposits INT;
    payouts INT;
BEGIN
    SELECT count(*) INTO deposits FROM (
        SELECT 1 FROM payments
        WHERE method IN ('BANK_TRANSFER', 'OFFICE_CASH') AND external_reference IS NOT NULL
        GROUP BY method, upper(external_reference) HAVING count(*) > 1) d;
    SELECT count(*) INTO payouts FROM (
        SELECT 1 FROM withdrawals
        WHERE payout_method = 'BANK_TRANSFER' AND status = 'PAID' AND payout_reference IS NOT NULL
        GROUP BY payout_method, upper(payout_reference) HAVING count(*) > 1) d;
    IF deposits > 0 OR payouts > 0 THEN
        RAISE EXCEPTION 'Cannot make payment references unique: % deposit reference(s) and % withdrawal payout reference(s) '
            'are recorded more than once. Find them with: SELECT method, upper(external_reference), count(*) FROM payments '
            'WHERE method IN (''BANK_TRANSFER'',''OFFICE_CASH'') AND external_reference IS NOT NULL GROUP BY 1, 2 HAVING count(*) > 1; '
            'and: SELECT payout_method, upper(payout_reference), count(*) FROM withdrawals WHERE payout_method = ''BANK_TRANSFER'' '
            'AND status = ''PAID'' AND payout_reference IS NOT NULL GROUP BY 1, 2 HAVING count(*) > 1;', deposits, payouts;
    END IF;
END;
$$;

-- ------------------------------------------------------------------------------------------- deposits
-- Bank transfers and cash at the office are confirmed by finance with the bank reference (UTR) or receipt number.
CREATE UNIQUE INDEX payments_manual_reference_uk ON payments (method, upper(external_reference))
    WHERE method IN ('BANK_TRANSFER', 'OFFICE_CASH') AND external_reference IS NOT NULL;

-- ------------------------------------------------------------------------------------------- withdrawals
-- A paid withdrawal's bank reference (UTR): one transfer pays one withdrawal. Cash receipts are not unique here: one
-- receipt may cover several payouts an investor collects at once (the service refuses another investor's receipt).
CREATE UNIQUE INDEX withdrawals_payout_reference_uk ON withdrawals (upper(payout_reference))
    WHERE payout_method = 'BANK_TRANSFER' AND status = 'PAID' AND payout_reference IS NOT NULL;

-- ------------------------------------------------------------------------------------------- unmatched receipts
-- Money the gateway reports for an order or virtual account that is none of ours (e.g. one whose payment was never
-- saved). Recorded once per gateway payment, for finance to trace and refund; resolved with a note.
CREATE TABLE unmatched_gateway_receipts (
    id uuid NOT NULL,
    provider character varying(30) NOT NULL,
    provider_reference character varying(100) NOT NULL,
    gateway_payment_id character varying(100) NOT NULL,
    amount numeric(19,4),
    currency character varying(3),
    received_at timestamp with time zone NOT NULL,
    resolved_by uuid,
    resolved_at timestamp with time zone,
    resolution character varying(500),
    CONSTRAINT unmatched_gateway_receipts_pkey PRIMARY KEY (id),
    CONSTRAINT unmatched_gateway_receipts_uk UNIQUE (provider, gateway_payment_id),
    CONSTRAINT unmatched_gateway_receipts_resolved_by_fkey FOREIGN KEY (resolved_by) REFERENCES users (id),
    CONSTRAINT unmatched_gateway_receipts_resolved_chk CHECK ((resolved_at IS NULL) = (resolved_by IS NULL)
        AND (resolved_at IS NULL) = (resolution IS NULL))
);
CREATE INDEX unmatched_gateway_receipts_open_idx ON unmatched_gateway_receipts (received_at) WHERE resolved_at IS NULL;
