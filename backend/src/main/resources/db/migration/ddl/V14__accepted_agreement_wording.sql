-- An agreement not yet accepted follows the wording in force; on acceptance, the wording the investor accepted is
-- recorded here. Earlier acceptances have none: they accepted the agreement's issued wording.
ALTER TABLE lease_agreement_acceptances
    ADD COLUMN template_id uuid,
    ADD COLUMN title character varying(200),
    ADD COLUMN body text,
    ADD CONSTRAINT lease_agreement_acceptances_wording_chk
        CHECK (((template_id IS NULL) AND (title IS NULL) AND (body IS NULL))
            OR ((template_id IS NOT NULL) AND (title IS NOT NULL) AND (body IS NOT NULL))),
    ADD CONSTRAINT lease_agreement_acceptances_template_fk
        FOREIGN KEY (template_id) REFERENCES agreement_templates (id);
