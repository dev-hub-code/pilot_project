-- A ledger transaction has entries. The balance check runs per entry (one entry alone is already unbalanced, as
-- amounts are positive), so only a transaction inserted with no entries at all could pass it: checked at commit,
-- whatever the code path.
CREATE FUNCTION ledger_check_has_entries() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM ledger_entries WHERE transaction_id = NEW.id) THEN
        RAISE EXCEPTION 'Ledger transaction % has no entries', NEW.id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ledger_transactions_have_entries AFTER INSERT ON ledger_transactions
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION ledger_check_has_entries();
