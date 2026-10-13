-- =====================================================================================
-- V37: Cash at the company's office, for paying orders and for collecting withdrawals.
--
-- Orders: a payment of method OFFICE_CASH is paid in cash at the office; finance confirms it with the
-- receipt number, as it confirms bank transfers.
-- Withdrawals: payout_method OFFICE_CASH is collected in cash at the office instead of being paid into a
-- bank account. It has no bank account, never joins a bank batch, and is marked paid when the cash is
-- handed over (payout_reference holds the receipt number).
-- =====================================================================================

ALTER TABLE payments
    DROP CONSTRAINT payments_method_chk,
    ADD CONSTRAINT payments_method_chk CHECK (method IN ('BANK_TRANSFER', 'OFFICE_CASH', 'CARD'));

ALTER TABLE withdrawals
    ADD COLUMN payout_method VARCHAR(20) NOT NULL DEFAULT 'BANK_TRANSFER',
    ALTER COLUMN bank_account_id DROP NOT NULL,
    ALTER COLUMN bank_holder_name DROP NOT NULL,
    ALTER COLUMN bank_name DROP NOT NULL,
    ALTER COLUMN bank_account_last4 DROP NOT NULL,
    ADD CONSTRAINT withdrawals_payout_method_chk CHECK (payout_method IN ('BANK_TRANSFER', 'OFFICE_CASH')),
    -- A bank withdrawal names its account; a cash one has none, and the monthly run pays bank accounts only.
    ADD CONSTRAINT withdrawals_payout_bank_chk CHECK (
        (payout_method = 'BANK_TRANSFER') = (bank_account_id IS NOT NULL AND bank_holder_name IS NOT NULL
            AND bank_name IS NOT NULL AND bank_account_last4 IS NOT NULL)),
    ADD CONSTRAINT withdrawals_cash_origin_chk CHECK (payout_method = 'BANK_TRANSFER' OR origin = 'INVESTOR'),
    -- Bank withdrawals are in a batch from BATCHED on; cash withdrawals never are, and go APPROVED -> PAID.
    DROP CONSTRAINT withdrawals_batched_chk,
    ADD CONSTRAINT withdrawals_batched_chk CHECK (
        (payout_method = 'BANK_TRANSFER' AND (status IN ('BATCHED', 'PROCESSING', 'PAID', 'FAILED')) = (batch_id IS NOT NULL))
            OR (payout_method = 'OFFICE_CASH' AND batch_id IS NULL
                AND status IN ('PENDING_APPROVAL', 'APPROVED', 'PAID', 'REJECTED', 'CANCELLED')));
