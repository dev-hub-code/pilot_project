-- Any employee can invite a client by email to open a network investor account. The email carries a one-time link
-- (only its SHA-256 is kept); following it, the client fills in their own details and password. A newer invitation to
-- the same address retires the earlier ones. An invited client has no referrer.
CREATE TABLE investor_invitations (
    id uuid NOT NULL,
    email character varying(254) NOT NULL,
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    signups_join_network boolean NOT NULL,
    token_hash character varying(64) NOT NULL,
    invited_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    accepted_at timestamp with time zone,
    accepted_user_id uuid,
    revoked_at timestamp with time zone,
    CONSTRAINT investor_invitations_pkey PRIMARY KEY (id),
    CONSTRAINT investor_invitations_token_uk UNIQUE (token_hash),
    CONSTRAINT investor_invitations_invited_by_fk FOREIGN KEY (invited_by) REFERENCES users (id),
    CONSTRAINT investor_invitations_accepted_user_fk FOREIGN KEY (accepted_user_id) REFERENCES users (id),
    CONSTRAINT investor_invitations_expiry_chk CHECK (expires_at > created_at),
    CONSTRAINT investor_invitations_accepted_chk CHECK ((accepted_at IS NULL) = (accepted_user_id IS NULL)),
    CONSTRAINT investor_invitations_one_outcome_chk CHECK (accepted_at IS NULL OR revoked_at IS NULL)
);

CREATE INDEX investor_invitations_email_idx ON investor_invitations (email);
CREATE INDEX investor_invitations_invited_by_idx ON investor_invitations (invited_by, created_at DESC);
