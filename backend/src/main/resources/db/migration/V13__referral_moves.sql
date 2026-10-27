-- Super administrators may move a member of the referral hierarchy (with everyone below them) to another referrer.
-- The referrer is the only column of referrals that may change, and every move is kept in referral_moves.
INSERT INTO permissions (code, category, description)
VALUES ('REFERRAL_TREE_MANAGE', 'FINANCE', 'View any investor''s downline and move members to another referrer');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'REFERRAL_TREE_MANAGE'
WHERE r.name = 'SUPER_ADMIN';

CREATE OR REPLACE FUNCTION referrals_only_referrer_changes()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    IF TG_OP = 'DELETE' OR NEW.user_id <> OLD.user_id OR NEW.code <> OLD.code OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'Table referrals: only the referrer may change (%)', TG_OP
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$function$;

DROP TRIGGER referrals_immutable ON referrals;
CREATE TRIGGER referrals_only_referrer_changes BEFORE DELETE OR UPDATE ON referrals
    FOR EACH ROW EXECUTE FUNCTION referrals_only_referrer_changes();

-- One row per move: who was moved, from and to which referrer, by whom and why. Append-only.
CREATE TABLE referral_moves
(
    id               UUID PRIMARY KEY,
    user_id          UUID         NOT NULL,
    from_referrer_id UUID         NOT NULL,
    to_referrer_id   UUID         NOT NULL,
    moved_by         UUID         NOT NULL,
    reason           VARCHAR(500) NOT NULL,
    moved_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT referral_moves_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT referral_moves_from_referrer_id_fkey FOREIGN KEY (from_referrer_id) REFERENCES users (id),
    CONSTRAINT referral_moves_to_referrer_id_fkey FOREIGN KEY (to_referrer_id) REFERENCES users (id),
    CONSTRAINT referral_moves_moved_by_fkey FOREIGN KEY (moved_by) REFERENCES users (id),
    CONSTRAINT referral_moves_changes_chk CHECK (from_referrer_id <> to_referrer_id AND to_referrer_id <> user_id)
);
CREATE INDEX referral_moves_user_idx ON referral_moves (user_id, moved_at);
CREATE TRIGGER referral_moves_immutable BEFORE DELETE OR UPDATE ON referral_moves
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
