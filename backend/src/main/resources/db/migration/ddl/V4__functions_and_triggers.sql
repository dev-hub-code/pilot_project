-- Rules the database enforces itself: append-only tables and balanced ledger transactions.

-- -------------------- Functions

CREATE FUNCTION forbid_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only: % is not permitted', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE FUNCTION ledger_check_balanced() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    imbalance NUMERIC;
    foreign_entries INT;
BEGIN
    SELECT coalesce(sum(CASE e.direction WHEN 'DEBIT' THEN e.amount ELSE -e.amount END), 0),
           count(*) FILTER (WHERE a.currency <> t.currency)
    INTO imbalance, foreign_entries
    FROM ledger_entries e
             JOIN ledger_accounts a ON a.id = e.account_id
             JOIN ledger_transactions t ON t.id = e.transaction_id
    WHERE e.transaction_id = NEW.transaction_id;
    IF imbalance <> 0 THEN
        RAISE EXCEPTION 'Ledger transaction % is unbalanced by %', NEW.transaction_id, imbalance
            USING ERRCODE = 'check_violation';
    END IF;
    IF foreign_entries > 0 THEN
        RAISE EXCEPTION 'Ledger transaction % mixes currencies', NEW.transaction_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$;

CREATE FUNCTION referrals_only_referrer_changes() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    IF NEW.user_id <> OLD.user_id OR NEW.code <> OLD.code OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'Table referrals: only the referrer may change (%)', TG_OP
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;

-- -------------------- Triggers

CREATE TRIGGER agreement_templates_immutable BEFORE DELETE OR UPDATE ON agreement_templates FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER audit_logs_immutable BEFORE DELETE OR UPDATE ON audit_logs FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER invoice_lines_immutable BEFORE DELETE OR UPDATE ON invoice_lines FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER invoices_immutable BEFORE DELETE OR UPDATE ON invoices FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER lead_activities_immutable BEFORE DELETE OR UPDATE ON lead_activities FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER lease_agreement_acceptances_immutable BEFORE DELETE OR UPDATE ON lease_agreement_acceptances FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER lease_agreements_immutable BEFORE DELETE OR UPDATE ON lease_agreements FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER ledger_accounts_immutable BEFORE DELETE OR UPDATE ON ledger_accounts FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE CONSTRAINT TRIGGER ledger_entries_balanced AFTER INSERT ON ledger_entries DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION ledger_check_balanced();
CREATE TRIGGER ledger_entries_immutable BEFORE DELETE OR UPDATE ON ledger_entries FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER ledger_transactions_immutable BEFORE DELETE OR UPDATE ON ledger_transactions FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER order_items_immutable BEFORE DELETE OR UPDATE ON order_items FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER referral_codes_immutable BEFORE DELETE OR UPDATE ON referral_codes FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER referral_earnings_immutable BEFORE DELETE OR UPDATE ON referral_earnings FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER referral_moves_immutable BEFORE DELETE OR UPDATE ON referral_moves FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER referrals_only_referrer_changes BEFORE DELETE OR UPDATE ON referrals FOR EACH ROW EXECUTE FUNCTION referrals_only_referrer_changes();
CREATE TRIGGER terms_versions_immutable BEFORE DELETE OR UPDATE ON terms_versions FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER ticket_attachments_immutable BEFORE DELETE OR UPDATE ON ticket_attachments FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER ticket_events_immutable BEFORE DELETE OR UPDATE ON ticket_events FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER ticket_messages_immutable BEFORE DELETE OR UPDATE ON ticket_messages FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
