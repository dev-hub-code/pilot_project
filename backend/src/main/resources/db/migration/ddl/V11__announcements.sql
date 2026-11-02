-- Announcements: a message an administrator sends at once to everyone, every investor, the online or the network
-- investors, or all staff. Each one is delivered as an ordinary notification to every active account in the audience;
-- this table keeps what was sent, by whom and to how many.
CREATE TABLE announcements (
    id uuid NOT NULL,
    title character varying(200) NOT NULL,
    body character varying(1000) NOT NULL,
    link character varying(300),
    audience character varying(20) NOT NULL,
    recipients integer NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT announcements_pkey PRIMARY KEY (id),
    CONSTRAINT announcements_created_by_fkey FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT announcements_audience_chk CHECK (audience IN ('EVERYONE', 'INVESTORS', 'ONLINE_INVESTORS', 'NETWORK_INVESTORS', 'STAFF')),
    CONSTRAINT announcements_recipients_chk CHECK (recipients >= 0)
);

CREATE INDEX announcements_created_at_idx ON announcements (created_at DESC, id DESC);
