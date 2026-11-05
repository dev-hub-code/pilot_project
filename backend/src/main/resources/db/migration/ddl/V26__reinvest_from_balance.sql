-- Online investors can pay for an investment from their available balance (their earnings wallet). The payment's
-- method is WALLET; the ledger moves the amount from their earnings accounts to INVESTOR_PRINCIPAL as one REINVESTMENT
-- transaction, referenced by the payment id.

ALTER TABLE payments DROP CONSTRAINT payments_method_chk;
ALTER TABLE payments ADD CONSTRAINT payments_method_chk
    CHECK (method IN ('BANK_TRANSFER', 'OFFICE_CASH', 'CARD', 'GATEWAY', 'VIRTUAL_ACCOUNT', 'WALLET'));

ALTER TABLE ledger_transactions DROP CONSTRAINT ledger_transactions_type_chk;
ALTER TABLE ledger_transactions ADD CONSTRAINT ledger_transactions_type_chk CHECK (transaction_type IN
    ('INVESTOR_PAYOUT', 'ADJUSTMENT', 'REFERRAL_COMMISSION', 'WITHDRAWAL_RESERVE', 'WITHDRAWAL_RELEASE',
     'WITHDRAWAL_PAYOUT', 'DEPOSIT_RECEIVED', 'DEPOSIT_REFUNDED', 'REINVESTMENT'));

-- A balance payment is settled in the transaction that creates it, and only once its ledger posting exists.
CREATE FUNCTION payments_wallet_posted() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.method = 'WALLET' AND NEW.status = 'SUCCEEDED' AND NOT EXISTS (
            SELECT 1 FROM ledger_transactions WHERE transaction_type = 'REINVESTMENT' AND reference = NEW.id::text) THEN
        RAISE EXCEPTION 'Balance payment % has no REINVESTMENT ledger posting', NEW.id;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER payments_wallet_posted AFTER INSERT OR UPDATE ON payments
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION payments_wallet_posted();
