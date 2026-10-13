-- =====================================================================================
-- V38: Three earnings balances per investor, each withdrawn its own way.
--
--   INVESTOR_EARNINGS          rent on containers paid by bank (and adjustments) -> bank account only
--   INVESTOR_CASH_EARNINGS     rent on containers paid in cash at the office     -> cash at the office only
--   INVESTOR_REFERRAL_EARNINGS referral commission                               -> bank or cash, investor's choice
--
-- A holding records whether its order was paid in cash at the office, which decides where its rent goes.
-- A withdrawal records the balance it draws from; one may be in progress per balance. Existing balances
-- and withdrawals are bank rent.
-- =====================================================================================

ALTER TABLE ledger_accounts
    DROP CONSTRAINT ledger_accounts_type_chk,
    ADD CONSTRAINT ledger_accounts_type_chk CHECK (account_type IN
        ('RENTAL_CASH', 'INVESTOR_EARNINGS', 'INVESTOR_CASH_EARNINGS', 'INVESTOR_REFERRAL_EARNINGS',
         'PLATFORM_ADJUSTMENTS', 'PLATFORM_REFERRAL_EXPENSE', 'WITHDRAWALS_IN_TRANSIT', 'PLATFORM_RENT_EXPENSE',
         'PLATFORM_CAPITAL_RETURNS')),
    DROP CONSTRAINT ledger_accounts_owner_chk,
    ADD CONSTRAINT ledger_accounts_owner_chk CHECK (
        (account_type IN ('INVESTOR_EARNINGS', 'INVESTOR_CASH_EARNINGS', 'INVESTOR_REFERRAL_EARNINGS'))
            = (owner_user_id IS NOT NULL));

ALTER TABLE holdings ADD COLUMN paid_at_office BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE withdrawals
    ADD COLUMN balance VARCHAR(20) NOT NULL DEFAULT 'RENT_BANK',
    ADD CONSTRAINT withdrawals_balance_chk CHECK (balance IN ('RENT_BANK', 'RENT_CASH', 'REFERRAL')),
    -- Bank rent goes to a bank account, cash rent is collected at the office; referral commission either way.
    ADD CONSTRAINT withdrawals_balance_method_chk CHECK (
        balance = 'REFERRAL'
            OR (balance = 'RENT_BANK' AND payout_method = 'BANK_TRANSFER')
            OR (balance = 'RENT_CASH' AND payout_method = 'OFFICE_CASH'));
DROP INDEX withdrawals_one_open_uk;
CREATE UNIQUE INDEX withdrawals_one_open_uk ON withdrawals (user_id, currency, balance)
    WHERE status IN ('PENDING_APPROVAL', 'APPROVED', 'BATCHED', 'PROCESSING');
