-- Withdrawals an investor requests must be approved before they move on; investor balances are kept as running totals
-- instead of summed from every entry; money received for investments (and refunded) is posted to the ledger; and daily
-- reconciliation results are kept.

-- ------------------------------------------------------------------------------------------- withdrawal approvals
-- An investor's own request reaches APPROVED (and anything after it) only with its approvals, as the review service
-- already requires; now the database does too. Runs and office withdrawals are approved by how they are made.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM withdrawals
               WHERE origin = 'REQUESTED'
                 AND status NOT IN ('SUBMITTED', 'UNDER_REVIEW', 'ON_HOLD', 'PENDING_APPROVAL', 'REJECTED', 'CANCELLED')
                 AND NOT (first_approved_by IS NOT NULL AND (required_approvals = 1 OR second_approved_by IS NOT NULL))) THEN
        RAISE EXCEPTION 'Requested withdrawals past review without their approvals exist. Find them with: SELECT id, reference, '
            'status FROM withdrawals WHERE origin = ''REQUESTED'' AND status NOT IN (''SUBMITTED'', ''UNDER_REVIEW'', '
            '''ON_HOLD'', ''PENDING_APPROVAL'', ''REJECTED'', ''CANCELLED'') AND NOT (first_approved_by IS NOT NULL AND '
            '(required_approvals = 1 OR second_approved_by IS NOT NULL));';
    END IF;
END;
$$;
ALTER TABLE withdrawals DROP CONSTRAINT withdrawals_approved_chk;
ALTER TABLE withdrawals ADD CONSTRAINT withdrawals_approved_chk CHECK (
    status IN ('SUBMITTED', 'UNDER_REVIEW', 'ON_HOLD', 'PENDING_APPROVAL', 'REJECTED', 'CANCELLED')
    OR origin IN ('SCHEDULED', 'OFFICE')
    OR (first_approved_by IS NOT NULL AND (required_approvals = 1 OR second_approved_by IS NOT NULL)));

-- ------------------------------------------------------------------------------------------- investor balance totals
-- Debits and credits posted to each investor's account so far, kept by the database as entries are inserted (entries
-- are never changed or deleted). A balance is read from here, not summed from every entry. One row per side, so a
-- credit (rent, commission) does not wait for a spender that has debited the account and not yet committed.
-- Platform accounts are not kept here: every investor's postings touch them, and they are rarely read.
CREATE TABLE ledger_account_totals (
    account_id uuid NOT NULL,
    direction character varying(6) NOT NULL,
    total numeric(19,4) NOT NULL,
    CONSTRAINT ledger_account_totals_pkey PRIMARY KEY (account_id, direction),
    CONSTRAINT ledger_account_totals_account_fkey FOREIGN KEY (account_id) REFERENCES ledger_accounts (id),
    CONSTRAINT ledger_account_totals_direction_chk CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ledger_account_totals_total_chk CHECK (total >= 0)
);

CREATE FUNCTION ledger_account_totals_add() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM ledger_accounts WHERE id = NEW.account_id AND owner_user_id IS NOT NULL) THEN
        INSERT INTO ledger_account_totals (account_id, direction, total) VALUES (NEW.account_id, NEW.direction, NEW.amount)
        ON CONFLICT (account_id, direction) DO UPDATE SET total = ledger_account_totals.total + EXCLUDED.total;
    END IF;
    RETURN NULL;
END;
$$;

INSERT INTO ledger_account_totals (account_id, direction, total)
SELECT e.account_id, e.direction, sum(e.amount)
FROM ledger_entries e JOIN ledger_accounts a ON a.id = e.account_id
WHERE a.owner_user_id IS NOT NULL
GROUP BY e.account_id, e.direction;

CREATE TRIGGER ledger_entries_totals AFTER INSERT ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION ledger_account_totals_add();

-- ------------------------------------------------------------------------------------------- deposits in the ledger
-- Money received for investments: DEPOSITS_RECEIVED (asset) against INVESTOR_PRINCIPAL (it paid for units) or
-- DEPOSITS_TO_REFUND (it must go back); a refund pays DEPOSITS_TO_REFUND out of DEPOSITS_RECEIVED.
ALTER TABLE ledger_accounts DROP CONSTRAINT ledger_accounts_type_chk;
ALTER TABLE ledger_accounts ADD CONSTRAINT ledger_accounts_type_chk CHECK (account_type IN
    ('RENTAL_CASH', 'INVESTOR_EARNINGS', 'INVESTOR_CASH_EARNINGS', 'INVESTOR_REFERRAL_EARNINGS', 'PLATFORM_ADJUSTMENTS',
     'PLATFORM_REFERRAL_EXPENSE', 'WITHDRAWALS_IN_TRANSIT', 'PLATFORM_RENT_EXPENSE', 'PLATFORM_CAPITAL_RETURNS',
     'WITHDRAWAL_FEE_INCOME', 'TAX_WITHHELD', 'DEPOSITS_RECEIVED', 'INVESTOR_PRINCIPAL', 'DEPOSITS_TO_REFUND'));
ALTER TABLE ledger_transactions DROP CONSTRAINT ledger_transactions_type_chk;
ALTER TABLE ledger_transactions ADD CONSTRAINT ledger_transactions_type_chk CHECK (transaction_type IN
    ('INVESTOR_PAYOUT', 'ADJUSTMENT', 'REFERRAL_COMMISSION', 'WITHDRAWAL_RESERVE', 'WITHDRAWAL_RELEASE',
     'WITHDRAWAL_PAYOUT', 'DEPOSIT_RECEIVED', 'DEPOSIT_REFUNDED'));

-- ------------------------------------------------------------------------------------------- reconciliation
-- One row per daily reconciliation: what needed attention then. The live report is computed on request.
CREATE TABLE reconciliation_runs (
    id uuid NOT NULL,
    run_at timestamp with time zone NOT NULL,
    issues integer NOT NULL,
    report jsonb NOT NULL,
    CONSTRAINT reconciliation_runs_pkey PRIMARY KEY (id),
    CONSTRAINT reconciliation_runs_issues_chk CHECK (issues >= 0)
);
CREATE INDEX reconciliation_runs_run_at_idx ON reconciliation_runs (run_at DESC);
