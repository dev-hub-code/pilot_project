-- Reporting: a history of report runs (exports), and indexes for the reports' period filters. Additive only: no
-- existing table, role, permission or role-permission mapping is changed.
CREATE TABLE report_executions (
    id uuid NOT NULL,
    report_key character varying(64) NOT NULL,
    report_title character varying(200) NOT NULL,
    executed_by uuid NOT NULL,
    executed_at timestamp with time zone NOT NULL,
    format character varying(10) NOT NULL,
    -- The filters as asked (period, choices, search, amounts): no report data, no personal details beyond a search term.
    filters jsonb NOT NULL,
    row_count integer,
    duration_ms integer,
    status character varying(20) NOT NULL,
    failure character varying(500),
    CONSTRAINT report_executions_pkey PRIMARY KEY (id),
    CONSTRAINT report_executions_executed_by_fk FOREIGN KEY (executed_by) REFERENCES users (id),
    CONSTRAINT report_executions_format_chk CHECK (format IN ('CSV', 'XLSX', 'PDF')),
    CONSTRAINT report_executions_status_chk CHECK (status IN ('COMPLETED', 'FAILED')),
    CONSTRAINT report_executions_failure_chk CHECK ((status = 'FAILED') = (failure IS NOT NULL))
);

CREATE INDEX report_executions_executed_at_idx ON report_executions (executed_at DESC);
CREATE INDEX report_executions_user_idx ON report_executions (executed_by, executed_at DESC);

-- Period filters and running balances.
CREATE INDEX IF NOT EXISTS ledger_entries_account_created_idx ON ledger_entries (account_id, created_at, id);
CREATE INDEX IF NOT EXISTS ledger_entries_created_idx ON ledger_entries (created_at);
CREATE INDEX IF NOT EXISTS rent_credits_day_idx ON rent_credits (day);
CREATE INDEX IF NOT EXISTS holdings_confirmed_idx ON holdings (confirmed_at);
CREATE INDEX IF NOT EXISTS payments_created_idx ON payments (created_at);
CREATE INDEX IF NOT EXISTS payments_settled_idx ON payments (settled_at) WHERE settled_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS withdrawals_created_idx ON withdrawals (created_at);
CREATE INDEX IF NOT EXISTS referral_earnings_created_idx ON referral_earnings (created_at);
CREATE INDEX IF NOT EXISTS orders_created_idx ON orders (created_at);
CREATE INDEX IF NOT EXISTS leads_created_idx ON leads (created_at);
CREATE INDEX IF NOT EXISTS support_tickets_created_idx ON support_tickets (created_at);
