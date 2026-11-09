-- Sign-in events (logins, logouts, failed logins, lockouts, password resets and changes, email verification) are kept
-- for a limited time and then deleted by the retention job. Every other audit entry stays append-only.
--
-- The audit log still refuses every UPDATE, and every DELETE except one the retention job makes: it switches on
-- sealease.audit_retention for its own transaction, and may then delete only these actions.
CREATE FUNCTION audit_logs_guard() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE'
       AND current_setting('sealease.audit_retention', true) = 'on'
       AND OLD.action IN ('LOGIN', 'LOGIN_FAILED', 'LOGOUT', 'LOGOUT_ALL', 'ACCOUNT_LOCKED',
                          'TOKEN_REFRESH_REUSE_DETECTED', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET',
                          'PASSWORD_CHANGE', 'EMAIL_VERIFIED', 'EMAIL_VERIFICATION_SENT') THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'Table % is append-only: % is not permitted', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

DROP TRIGGER audit_logs_immutable ON audit_logs;
CREATE TRIGGER audit_logs_immutable BEFORE DELETE OR UPDATE ON audit_logs FOR EACH ROW EXECUTE FUNCTION audit_logs_guard();
