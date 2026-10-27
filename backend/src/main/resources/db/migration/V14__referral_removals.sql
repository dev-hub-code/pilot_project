-- Super administrators may also take a member (with everyone below them) out of a downline, leaving them without a
-- referrer, and later place someone without a referrer into a downline. A removal deletes the member's referrals row;
-- referral_moves keeps it, with no new referrer, and a placement, with no old one.
CREATE OR REPLACE FUNCTION referrals_only_referrer_changes()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

ALTER TABLE referral_moves
    ALTER COLUMN from_referrer_id DROP NOT NULL,
    ALTER COLUMN to_referrer_id DROP NOT NULL,
    DROP CONSTRAINT referral_moves_changes_chk,
    ADD CONSTRAINT referral_moves_changes_chk CHECK (
        (from_referrer_id IS NOT NULL OR to_referrer_id IS NOT NULL)
        AND from_referrer_id IS DISTINCT FROM to_referrer_id
        AND to_referrer_id IS DISTINCT FROM user_id);
