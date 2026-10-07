-- =====================================================================================
-- V15: Company bank accounts and bank-payment details.
--
-- Finance maintains the accounts investors may pay into (replacing the single account from
-- configuration). An investor paying by bank chooses one of them and tells us how they paid:
-- online transfer (transaction id), cheque (cheque number) or cash deposit (deposit receipt
-- number). Finance checks the details against the bank statement and confirms or rejects.
-- =====================================================================================

-- --------------------------------------------------------------------- permissions --
INSERT INTO permissions (code, category, description)
VALUES ('COMPANY_BANK_ACCOUNT_MANAGE', 'FINANCE', 'Manage the company bank accounts investors pay into');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'COMPANY_BANK_ACCOUNT_MANAGE'
WHERE r.name IN ('SUPER_ADMIN', 'ADMIN', 'FINANCE');

-- ------------------------------------------------------------ company bank accounts --
-- Not secret: these details are shown to every investor paying by bank. Accounts that have been
-- paid into are never deleted, only deactivated, so past payments keep pointing at them.
CREATE TABLE company_bank_accounts
(
    id             UUID PRIMARY KEY,
    account_name   VARCHAR(140) NOT NULL,
    bank_name      VARCHAR(140) NOT NULL,
    branch         VARCHAR(140),
    account_number VARCHAR(34)  NOT NULL,
    ifsc_code      VARCHAR(11)  NOT NULL,
    upi_id         VARCHAR(100),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by     UUID         NOT NULL REFERENCES users (id),
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT company_bank_accounts_ifsc_chk CHECK (ifsc_code ~ '^[A-Z]{4}0[A-Z0-9]{6}$'),
    CONSTRAINT company_bank_accounts_number_chk CHECK (account_number ~ '^[0-9]{6,18}$')
);
CREATE UNIQUE INDEX company_bank_accounts_number_uk ON company_bank_accounts (ifsc_code, account_number);

-- ------------------------------------------------------------------ payment details --
ALTER TABLE payments
    ADD COLUMN company_bank_account_id UUID REFERENCES company_bank_accounts (id),
    ADD COLUMN deposit_mode            VARCHAR(20),
    ADD COLUMN deposit_reference       VARCHAR(100),
    ADD COLUMN deposit_submitted_at    TIMESTAMPTZ,
    ADD CONSTRAINT payments_deposit_mode_chk CHECK (deposit_mode IN ('ONLINE', 'CHEQUE', 'CASH_DEPOSIT')),
    -- All of the details, or none of them; and only for bank payments.
    ADD CONSTRAINT payments_deposit_complete_chk CHECK (
        (company_bank_account_id IS NULL AND deposit_mode IS NULL AND deposit_reference IS NULL
            AND deposit_submitted_at IS NULL)
        OR (company_bank_account_id IS NOT NULL AND deposit_mode IS NOT NULL AND deposit_reference IS NOT NULL
            AND deposit_submitted_at IS NOT NULL AND method = 'BANK_TRANSFER'));

