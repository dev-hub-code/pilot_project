-- Every table, with its columns and check constraints.

-- -------------------- Tables

CREATE TABLE agreement_templates (
    id uuid NOT NULL,
    title character varying(200) NOT NULL,
    body text NOT NULL,
    version integer NOT NULL,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT agreement_templates_body_chk CHECK (((length(body) >= 1) AND (length(body) <= 50000))),
    CONSTRAINT agreement_templates_version_chk CHECK ((version > 0))
);

CREATE TABLE audit_logs (
    id uuid NOT NULL,
    actor_user_id uuid,
    action character varying(64) NOT NULL,
    entity_type character varying(64) NOT NULL,
    entity_id character varying(64),
    old_value jsonb,
    new_value jsonb,
    ip_address character varying(45),
    user_agent character varying(512),
    correlation_id character varying(64),
    created_at timestamp with time zone NOT NULL
);

CREATE TABLE auth_sessions (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    last_used_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    revoked_reason character varying(40),
    ip_address character varying(45),
    user_agent character varying(512),
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT auth_sessions_expiry_chk CHECK ((expires_at > created_at)),
    CONSTRAINT auth_sessions_revocation_chk CHECK (((revoked_at IS NULL) = (revoked_reason IS NULL)))
);

CREATE TABLE bank_accounts (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    account_holder_name character varying(140) NOT NULL,
    bank_name character varying(140),
    country character varying(2) NOT NULL,
    currency character varying(3) NOT NULL,
    account_number_encrypted text NOT NULL,
    account_number_last4 character varying(4) NOT NULL,
    account_fingerprint character varying(64) NOT NULL,
    routing_code_encrypted text NOT NULL,
    status character varying(25) NOT NULL,
    is_primary boolean DEFAULT false NOT NULL,
    verified_by uuid,
    verified_at timestamp with time zone,
    rejection_reason character varying(500),
    removed_at timestamp with time zone,
    replaces_account_id uuid,
    proof_type character varying(20),
    proof_document_id uuid,
    assigned_to uuid,
    assigned_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT bank_accounts_assignment_chk CHECK (((assigned_to IS NULL) = (assigned_at IS NULL))),
    CONSTRAINT bank_accounts_codes_chk CHECK ((((country)::text ~ '^[A-Z]{2}$'::text) AND ((currency)::text ~ '^[A-Z]{3}$'::text))),
    CONSTRAINT bank_accounts_primary_chk CHECK ((NOT (is_primary AND ((status)::text = 'REMOVED'::text)))),
    CONSTRAINT bank_accounts_proof_chk CHECK ((((proof_type IS NULL) = (proof_document_id IS NULL)) AND ((proof_type IS NULL) OR ((proof_type)::text = ANY ((ARRAY['BANK_STATEMENT'::character varying, 'CANCELLED_CHEQUE'::character varying])::text[]))))),
    CONSTRAINT bank_accounts_removed_chk CHECK ((((status)::text = 'REMOVED'::text) = (removed_at IS NOT NULL))),
    CONSTRAINT bank_accounts_replaces_chk CHECK (((replaces_account_id IS NULL) OR (replaces_account_id <> id))),
    CONSTRAINT bank_accounts_status_chk CHECK (((status)::text = ANY ((ARRAY['PENDING_VERIFICATION'::character varying, 'VERIFIED'::character varying, 'REJECTED'::character varying, 'REMOVED'::character varying])::text[]))),
    CONSTRAINT bank_accounts_verified_chk CHECK ((((status)::text <> 'VERIFIED'::text) OR (verified_by IS NOT NULL))),
    CONSTRAINT bank_accounts_verifier_chk CHECK (((verified_by IS NULL) OR (verified_by <> user_id)))
);

CREATE TABLE cities (
    id integer NOT NULL,
    state_id integer NOT NULL,
    name character varying(120) NOT NULL
);

CREATE TABLE company_bank_accounts (
    id uuid NOT NULL,
    account_name character varying(140) NOT NULL,
    bank_name character varying(140) NOT NULL,
    branch character varying(140),
    account_number character varying(34) NOT NULL,
    ifsc_code character varying(11) NOT NULL,
    upi_id character varying(100),
    active boolean DEFAULT true NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT company_bank_accounts_ifsc_chk CHECK (((ifsc_code)::text ~ '^[A-Z]{4}0[A-Z0-9]{6}$'::text)),
    CONSTRAINT company_bank_accounts_number_chk CHECK (((account_number)::text ~ '^[0-9]{6,18}$'::text))
);

CREATE TABLE containers (
    id uuid NOT NULL,
    container_number character varying(20) NOT NULL,
    container_type character varying(30) NOT NULL,
    condition character varying(20),
    status character varying(20) NOT NULL,
    capacity_cbm numeric(8,2),
    max_gross_kg integer,
    tare_kg integer,
    manufacture_year smallint,
    manufacturer character varying(100),
    current_location character varying(120),
    location_country character varying(2),
    acquisition_cost numeric(19,4),
    acquisition_currency character varying(3),
    notes character varying(1000),
    status_reason character varying(500),
    reserved_order_id uuid,
    version bigint DEFAULT 0 NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT containers_capacity_chk CHECK ((capacity_cbm > (0)::numeric)),
    CONSTRAINT containers_condition_chk CHECK (((condition)::text = ANY ((ARRAY['NEW'::character varying, 'CARGO_WORTHY'::character varying, 'WIND_WATERTIGHT'::character varying])::text[]))),
    CONSTRAINT containers_cost_chk CHECK ((((acquisition_cost IS NULL) AND (acquisition_currency IS NULL)) OR ((acquisition_cost >= (0)::numeric) AND ((acquisition_currency)::text ~ '^[A-Z]{3}$'::text)))),
    CONSTRAINT containers_country_chk CHECK (((location_country)::text ~ '^[A-Z]{2}$'::text)),
    CONSTRAINT containers_reserved_chk CHECK ((((status)::text = 'RESERVED'::text) = (reserved_order_id IS NOT NULL))),
    CONSTRAINT containers_status_chk CHECK (((status)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'RESERVED'::character varying, 'ON_LEASE'::character varying, 'MAINTENANCE'::character varying, 'RETIRED'::character varying])::text[]))),
    CONSTRAINT containers_type_chk CHECK (((container_type)::text = ANY ((ARRAY['DRY_10FT'::character varying, 'DRY_20FT'::character varying, 'DRY_40FT'::character varying, 'HIGH_CUBE_40FT'::character varying, 'HIGH_CUBE_45FT'::character varying, 'REEFER_20FT'::character varying, 'REEFER_40FT'::character varying, 'OPEN_TOP_20FT'::character varying, 'OPEN_TOP_40FT'::character varying, 'FLAT_RACK_20FT'::character varying, 'FLAT_RACK_40FT'::character varying, 'TANK_20FT'::character varying])::text[]))),
    CONSTRAINT containers_weights_chk CHECK (((tare_kg > 0) AND (max_gross_kg > tare_kg))),
    CONSTRAINT containers_year_chk CHECK (((manufacture_year >= 1960) AND (manufacture_year <= 2100)))
);

CREATE TABLE countries (
    code character varying(2) NOT NULL,
    name character varying(100) NOT NULL,
    iso3 character varying(3),
    phone_code character varying(20),
    CONSTRAINT countries_code_chk CHECK (((code)::text ~ '^[A-Z]{2}$'::text))
);

CREATE TABLE demo_accounts (
    user_id uuid NOT NULL,
    starts_on date NOT NULL,
    ends_on date NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT demo_accounts_frame_chk CHECK ((ends_on >= starts_on))
);

CREATE TABLE demo_members (
    id uuid NOT NULL,
    account_id uuid NOT NULL,
    parent_id uuid,
    name character varying(120) NOT NULL,
    joined_on date NOT NULL,
    created_at timestamp with time zone NOT NULL
);

CREATE TABLE demo_orders (
    id uuid NOT NULL,
    account_id uuid NOT NULL,
    member_id uuid,
    order_number character varying(20) NOT NULL,
    product_id uuid NOT NULL,
    plan_code character varying(20) NOT NULL,
    plan_title character varying(200) NOT NULL,
    container_type character varying(30),
    containers integer NOT NULL,
    price_per_container numeric(19,4) NOT NULL,
    currency character(3) NOT NULL,
    rent_percent numeric(12,8) NOT NULL,
    tenure_months integer NOT NULL,
    starts_on date NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT demo_orders_containers_chk CHECK (((containers >= 1) AND (containers <= 1000))),
    CONSTRAINT demo_orders_price_chk CHECK ((price_per_container > (0)::numeric)),
    CONSTRAINT demo_orders_rent_chk CHECK (((rent_percent >= (0)::numeric) AND (rent_percent <= (100)::numeric))),
    CONSTRAINT demo_orders_tenure_chk CHECK (((tenure_months >= 1) AND (tenure_months <= 600)))
);

CREATE TABLE email_verification_tokens (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    code_hash character varying(64),
    failed_attempts integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT email_verification_tokens_expiry_chk CHECK ((expires_at > created_at))
);

CREATE TABLE holdings (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    product_id uuid NOT NULL,
    order_id uuid NOT NULL,
    order_item_id uuid NOT NULL,
    container_id uuid,
    amount numeric(19,4) NOT NULL,
    currency character varying(3) NOT NULL,
    monthly_rent_percent numeric(12,8) NOT NULL,
    monthly_capital_return_percent numeric(9,4) NOT NULL,
    tenure_months integer NOT NULL,
    lease_starts_on date,
    lease_ends_on date,
    status character varying(20) NOT NULL,
    confirmed_at timestamp with time zone NOT NULL,
    matured_at timestamp with time zone,
    assigned_at timestamp with time zone,
    assigned_by uuid,
    paid_at_office boolean DEFAULT false NOT NULL,
    task_assignee_id uuid,
    task_assigned_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT holdings_amount_chk CHECK ((amount > (0)::numeric)),
    CONSTRAINT holdings_assignment_chk CHECK (((((status)::text = 'AWAITING_ACCEPTANCE'::text) = (lease_starts_on IS NULL)) AND ((lease_starts_on IS NULL) = (lease_ends_on IS NULL)))),
    CONSTRAINT holdings_lease_chk CHECK ((lease_ends_on > lease_starts_on)),
    CONSTRAINT holdings_matured_chk CHECK ((((status)::text = 'MATURED'::text) = (matured_at IS NOT NULL))),
    CONSTRAINT holdings_status_chk CHECK (((status)::text = ANY ((ARRAY['AWAITING_ACCEPTANCE'::character varying, 'ACTIVE'::character varying, 'MATURED'::character varying])::text[]))),
    CONSTRAINT holdings_task_assignment_chk CHECK (((task_assignee_id IS NULL) = (task_assigned_at IS NULL)))
);

CREATE TABLE investment_products (
    id uuid NOT NULL,
    code character varying(20) NOT NULL,
    container_type character varying(30),
    title character varying(140) NOT NULL,
    summary character varying(400) NOT NULL,
    currency character varying(3) NOT NULL,
    price numeric(19,4),
    monthly_capital_return_percent numeric(9,4) NOT NULL,
    risk_disclosure text,
    terms_and_conditions text,
    status character varying(20) NOT NULL,
    published_at timestamp with time zone,
    published_by uuid,
    closed_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    cancellation_reason character varying(500),
    tenure_years integer CONSTRAINT investment_products_min_tenure_years_not_null NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    audience character varying(20) NOT NULL,
    annual_return_percent numeric(5,2) CONSTRAINT investment_products_monthly_return_percent_not_null NOT NULL,
    unit_price numeric(19,4) NOT NULL,
    min_units integer NOT NULL,
    CONSTRAINT investment_products_annual_return_chk CHECK ((annual_return_percent > (0)::numeric)),
    CONSTRAINT investment_products_audience_chk CHECK (((audience)::text = ANY ((ARRAY['ONLINE'::character varying, 'NETWORK'::character varying])::text[]))),
    CONSTRAINT investment_products_cancelled_chk CHECK ((((status)::text = 'CANCELLED'::text) = (cancelled_at IS NOT NULL))),
    CONSTRAINT investment_products_capital_chk CHECK ((monthly_capital_return_percent = (0)::numeric)),
    CONSTRAINT investment_products_closed_chk CHECK ((((status)::text = 'CLOSED'::text) = (closed_at IS NOT NULL))),
    CONSTRAINT investment_products_container_type_chk CHECK (((container_type)::text = ANY ((ARRAY['DRY_10FT'::character varying, 'DRY_20FT'::character varying, 'DRY_40FT'::character varying, 'HIGH_CUBE_40FT'::character varying, 'HIGH_CUBE_45FT'::character varying, 'REEFER_20FT'::character varying, 'REEFER_40FT'::character varying, 'OPEN_TOP_20FT'::character varying, 'OPEN_TOP_40FT'::character varying, 'FLAT_RACK_20FT'::character varying, 'FLAT_RACK_40FT'::character varying, 'TANK_20FT'::character varying])::text[]))),
    CONSTRAINT investment_products_currency_chk CHECK (((currency)::text ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT investment_products_min_units_chk CHECK ((min_units >= 1)),
    CONSTRAINT investment_products_price_chk CHECK ((price > (0)::numeric)),
    CONSTRAINT investment_products_published_chk CHECK ((((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'CANCELLED'::character varying])::text[])) OR (published_at IS NOT NULL))),
    CONSTRAINT investment_products_status_chk CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'OPEN'::character varying, 'CLOSED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT investment_products_tenure_chk CHECK (((tenure_years >= 1) AND (tenure_years <= 30))),
    CONSTRAINT investment_products_unit_price_chk CHECK ((unit_price > (0)::numeric))
);

CREATE TABLE invoice_lines (
    id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    line_number integer NOT NULL,
    description character varying(300) NOT NULL,
    amount numeric(19,4) NOT NULL,
    CONSTRAINT invoice_lines_amount_chk CHECK ((amount > (0)::numeric))
);

CREATE TABLE invoices (
    id uuid NOT NULL,
    invoice_number character varying(30) NOT NULL,
    order_id uuid NOT NULL,
    user_id uuid NOT NULL,
    payment_id uuid NOT NULL,
    currency character varying(3) NOT NULL,
    total_amount numeric(19,4) NOT NULL,
    issuer_name character varying(200) NOT NULL,
    issuer_address character varying(500) NOT NULL,
    issuer_tax_id character varying(50),
    buyer_name character varying(200) NOT NULL,
    buyer_email character varying(320) NOT NULL,
    buyer_address character varying(500),
    notes character varying(1000),
    issued_at timestamp with time zone NOT NULL,
    CONSTRAINT invoices_total_chk CHECK ((total_amount > (0)::numeric))
);

CREATE TABLE kyc_documents (
    submission_id uuid NOT NULL,
    document_id uuid NOT NULL,
    purpose character varying(40) NOT NULL
);

CREATE TABLE kyc_follow_ups (
    user_id uuid NOT NULL,
    ticket_id uuid NOT NULL,
    opened_at timestamp with time zone NOT NULL
);

CREATE TABLE kyc_submissions (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    legal_first_name character varying(100) NOT NULL,
    legal_last_name character varying(100) NOT NULL,
    date_of_birth date NOT NULL,
    nationality character varying(2) NOT NULL,
    document_type character varying(20),
    document_number_encrypted text,
    document_number_last4 character varying(4),
    document_issuing_country character varying(2),
    document_expiry_date date,
    submitted_at timestamp with time zone NOT NULL,
    reviewed_by uuid,
    reviewed_at timestamp with time zone,
    rejection_reason character varying(500),
    aadhaar_number_encrypted text,
    aadhaar_number_last4 character varying(4),
    pan_number_encrypted text,
    pan_number_last4 character varying(4),
    aadhaar_fingerprint character varying(64),
    pan_fingerprint character varying(64),
    assigned_to uuid,
    assigned_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT kyc_submissions_assignment_chk CHECK (((assigned_to IS NULL) = (assigned_at IS NULL))),
    CONSTRAINT kyc_submissions_document_type_chk CHECK (((document_type)::text = ANY ((ARRAY['PASSPORT'::character varying, 'NATIONAL_ID'::character varying, 'DRIVING_LICENSE'::character varying])::text[]))),
    CONSTRAINT kyc_submissions_documents_chk CHECK ((((aadhaar_number_encrypted IS NULL) = (aadhaar_number_last4 IS NULL)) AND ((pan_number_encrypted IS NULL) = (pan_number_last4 IS NULL)) AND (((document_type IS NULL) AND ((status)::text = 'DRAFT'::text) AND ((aadhaar_number_encrypted IS NOT NULL) OR (pan_number_encrypted IS NOT NULL))) OR ((document_type IS NULL) AND ((status)::text <> 'DRAFT'::text) AND (aadhaar_number_encrypted IS NOT NULL) AND (pan_number_encrypted IS NOT NULL)) OR ((document_type IS NOT NULL) AND (aadhaar_number_encrypted IS NULL) AND (pan_number_encrypted IS NULL) AND (document_number_encrypted IS NOT NULL) AND (document_number_last4 IS NOT NULL) AND (document_issuing_country IS NOT NULL) AND (document_expiry_date IS NOT NULL))))),
    CONSTRAINT kyc_submissions_rejection_chk CHECK ((((status)::text = 'REJECTED'::text) = (rejection_reason IS NOT NULL))),
    CONSTRAINT kyc_submissions_review_chk CHECK (((((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING'::character varying])::text[])) AND (reviewed_by IS NULL) AND (reviewed_at IS NULL)) OR (((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'REJECTED'::character varying])::text[])) AND (reviewed_by IS NOT NULL) AND (reviewed_at IS NOT NULL)))),
    CONSTRAINT kyc_submissions_reviewer_chk CHECK (((reviewed_by IS NULL) OR (reviewed_by <> user_id))),
    CONSTRAINT kyc_submissions_status_chk CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);

CREATE TABLE lead_activities (
    id uuid NOT NULL,
    lead_id uuid NOT NULL,
    activity_type character varying(20) NOT NULL,
    body character varying(4000) NOT NULL,
    actor_id uuid,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT lead_activities_type_chk CHECK (((activity_type)::text = ANY ((ARRAY['NOTE'::character varying, 'CALL'::character varying, 'EMAIL'::character varying, 'MEETING'::character varying, 'STAGE_CHANGE'::character varying, 'ASSIGNMENT'::character varying, 'SYSTEM'::character varying])::text[])))
);

CREATE TABLE leads (
    id uuid NOT NULL,
    reference character varying(20) NOT NULL,
    first_name character varying(100) NOT NULL,
    last_name character varying(100),
    email character varying(254),
    phone character varying(40),
    country character varying(2),
    source character varying(20) NOT NULL,
    interest character varying(20) NOT NULL,
    estimated_amount numeric(19,4),
    estimated_currency character varying(3),
    message character varying(2000),
    stage character varying(20) NOT NULL,
    lost_reason character varying(500),
    owner_id uuid,
    user_id uuid,
    won_amount numeric(19,4),
    won_currency character varying(3),
    won_order_id uuid,
    closed_at timestamp with time zone,
    next_follow_up_at timestamp with time zone,
    consent_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    phone_country character varying(2),
    CONSTRAINT leads_closed_chk CHECK ((((stage)::text = ANY ((ARRAY['WON'::character varying, 'LOST'::character varying])::text[])) = (closed_at IS NOT NULL))),
    CONSTRAINT leads_consent_chk CHECK ((((source)::text <> 'WEBSITE'::text) OR ((consent_at IS NOT NULL) AND (email IS NOT NULL)))),
    CONSTRAINT leads_contact_chk CHECK (((email IS NOT NULL) OR (phone IS NOT NULL))),
    CONSTRAINT leads_country_chk CHECK (((country IS NULL) OR ((country)::text ~ '^[A-Z]{2}$'::text))),
    CONSTRAINT leads_email_chk CHECK (((email IS NULL) OR ((email)::text = lower((email)::text)))),
    CONSTRAINT leads_estimate_chk CHECK ((((estimated_amount IS NULL) AND (estimated_currency IS NULL)) OR ((estimated_amount > (0)::numeric) AND ((estimated_currency)::text ~ '^[A-Z]{3}$'::text)))),
    CONSTRAINT leads_interest_chk CHECK (((interest)::text = ANY ((ARRAY['STANDALONE'::character varying, 'UNSURE'::character varying])::text[]))),
    CONSTRAINT leads_lost_chk CHECK ((((stage)::text = 'LOST'::text) = (lost_reason IS NOT NULL))),
    CONSTRAINT leads_source_chk CHECK (((source)::text = ANY ((ARRAY['WEBSITE'::character varying, 'STAFF'::character varying])::text[]))),
    CONSTRAINT leads_stage_chk CHECK (((stage)::text = ANY ((ARRAY['NEW'::character varying, 'CONTACTED'::character varying, 'QUALIFIED'::character varying, 'PROPOSAL'::character varying, 'WON'::character varying, 'LOST'::character varying])::text[]))),
    CONSTRAINT leads_won_chk CHECK ((((won_amount IS NULL) AND (won_currency IS NULL)) OR (((stage)::text = 'WON'::text) AND (won_amount > (0)::numeric) AND ((won_currency)::text ~ '^[A-Z]{3}$'::text))))
);

CREATE TABLE lease_agreement_acceptances (
    agreement_id uuid NOT NULL,
    user_id uuid NOT NULL,
    accepted_at timestamp with time zone NOT NULL,
    lease_starts_on date NOT NULL,
    lease_ends_on date NOT NULL,
    ip_address character varying(45),
    user_agent character varying(512),
    CONSTRAINT lease_agreement_acceptances_lease_chk CHECK ((lease_ends_on > lease_starts_on))
);

CREATE TABLE lease_agreements (
    id uuid NOT NULL,
    agreement_number character varying(30) NOT NULL,
    holding_id uuid NOT NULL,
    order_id uuid NOT NULL,
    user_id uuid NOT NULL,
    container_number character varying(20),
    template_id uuid NOT NULL,
    title character varying(200) NOT NULL,
    body text NOT NULL,
    generated_by uuid,
    generated_at timestamp with time zone NOT NULL,
    acceptance_required boolean DEFAULT false NOT NULL
);

CREATE TABLE ledger_accounts (
    id uuid NOT NULL,
    account_type character varying(30) NOT NULL,
    owner_user_id uuid,
    currency character varying(3) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ledger_accounts_currency_chk CHECK (((currency)::text ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT ledger_accounts_owner_chk CHECK ((((account_type)::text = ANY ((ARRAY['INVESTOR_EARNINGS'::character varying, 'INVESTOR_CASH_EARNINGS'::character varying, 'INVESTOR_REFERRAL_EARNINGS'::character varying])::text[])) = (owner_user_id IS NOT NULL))),
    CONSTRAINT ledger_accounts_type_chk CHECK (((account_type)::text = ANY ((ARRAY['RENTAL_CASH'::character varying, 'INVESTOR_EARNINGS'::character varying, 'INVESTOR_CASH_EARNINGS'::character varying, 'INVESTOR_REFERRAL_EARNINGS'::character varying, 'PLATFORM_ADJUSTMENTS'::character varying, 'PLATFORM_REFERRAL_EXPENSE'::character varying, 'WITHDRAWALS_IN_TRANSIT'::character varying, 'PLATFORM_RENT_EXPENSE'::character varying, 'PLATFORM_CAPITAL_RETURNS'::character varying])::text[])))
);

CREATE TABLE ledger_entries (
    id uuid NOT NULL,
    transaction_id uuid NOT NULL,
    account_id uuid NOT NULL,
    direction character varying(6) NOT NULL,
    amount numeric(19,4) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ledger_entries_amount_chk CHECK ((amount > (0)::numeric)),
    CONSTRAINT ledger_entries_direction_chk CHECK (((direction)::text = ANY ((ARRAY['DEBIT'::character varying, 'CREDIT'::character varying])::text[])))
);

CREATE TABLE ledger_transactions (
    id uuid NOT NULL,
    transaction_type character varying(30) NOT NULL,
    reference character varying(100) NOT NULL,
    currency character varying(3) NOT NULL,
    description character varying(300) NOT NULL,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ledger_transactions_type_chk CHECK (((transaction_type)::text = ANY ((ARRAY['INVESTOR_PAYOUT'::character varying, 'ADJUSTMENT'::character varying, 'REFERRAL_COMMISSION'::character varying, 'WITHDRAWAL_RESERVE'::character varying, 'WITHDRAWAL_RELEASE'::character varying, 'WITHDRAWAL_PAYOUT'::character varying])::text[])))
);

CREATE TABLE network_investors (
    user_id uuid NOT NULL,
    signups_join_network boolean NOT NULL,
    created_by uuid,
    created_at timestamp with time zone NOT NULL
);

CREATE TABLE notifications (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    type character varying(40) NOT NULL,
    title character varying(200) NOT NULL,
    body character varying(1000),
    link character varying(300),
    read_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL
);

CREATE TABLE order_items (
    id uuid NOT NULL,
    order_id uuid NOT NULL,
    product_id uuid NOT NULL,
    product_code character varying(20) NOT NULL,
    product_title character varying(140) NOT NULL,
    container_type character varying(30),
    container_id uuid,
    amount numeric(19,4) NOT NULL,
    currency character varying(3) NOT NULL,
    monthly_rent_percent numeric(12,8) NOT NULL,
    monthly_capital_return_percent numeric(9,4) NOT NULL,
    tenure_months integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    units integer,
    unit_price numeric(19,4),
    CONSTRAINT order_items_amount_chk CHECK ((amount > (0)::numeric)),
    CONSTRAINT order_items_units_chk CHECK ((((units IS NULL) = (unit_price IS NULL)) AND ((units IS NULL) OR (units >= 1))))
);

CREATE TABLE orders (
    id uuid NOT NULL,
    order_number character varying(20) NOT NULL,
    user_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    currency character varying(3) NOT NULL,
    total_amount numeric(19,4) NOT NULL,
    idempotency_key character varying(100) NOT NULL,
    request_hash character varying(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    confirmed_at timestamp with time zone,
    closed_at timestamp with time zone,
    close_reason character varying(500),
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT orders_closed_chk CHECK ((((status)::text = ANY ((ARRAY['EXPIRED'::character varying, 'CANCELLED'::character varying])::text[])) = (closed_at IS NOT NULL))),
    CONSTRAINT orders_confirmed_chk CHECK ((((status)::text = 'CONFIRMED'::text) = (confirmed_at IS NOT NULL))),
    CONSTRAINT orders_currency_chk CHECK (((currency)::text ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT orders_status_chk CHECK (((status)::text = ANY ((ARRAY['PENDING_PAYMENT'::character varying, 'CONFIRMED'::character varying, 'EXPIRED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT orders_total_chk CHECK ((total_amount > (0)::numeric))
);

CREATE TABLE outbox_events (
    id uuid NOT NULL,
    topic character varying(100) NOT NULL,
    event_type character varying(100) NOT NULL,
    aggregate_type character varying(50) NOT NULL,
    aggregate_id character varying(100) NOT NULL,
    payload jsonb NOT NULL,
    correlation_id character varying(100),
    published_at timestamp with time zone,
    attempts integer DEFAULT 0 NOT NULL,
    last_error character varying(1000),
    created_at timestamp with time zone NOT NULL
);

CREATE TABLE password_reset_codes (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    code_hash character varying(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    failed_attempts integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT password_reset_codes_expiry_chk CHECK ((expires_at > created_at))
);

CREATE TABLE payments (
    id uuid NOT NULL,
    order_id uuid NOT NULL,
    user_id uuid NOT NULL,
    method character varying(20) NOT NULL,
    provider character varying(20) NOT NULL,
    provider_reference character varying(100) NOT NULL,
    amount numeric(19,4) NOT NULL,
    currency character varying(3) NOT NULL,
    status character varying(20) NOT NULL,
    idempotency_key character varying(100) NOT NULL,
    failure_reason character varying(500),
    external_reference character varying(100),
    confirmed_by uuid,
    settled_at timestamp with time zone,
    refunded_by uuid,
    refunded_at timestamp with time zone,
    refund_reference character varying(100),
    refund_reason character varying(500),
    company_bank_account_id uuid,
    deposit_mode character varying(20),
    deposit_reference character varying(100),
    deposit_submitted_at timestamp with time zone,
    task_assignee_id uuid,
    task_assigned_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    receipt_number character varying(100),
    receipt_submitted_at timestamp with time zone,
    CONSTRAINT payments_amount_chk CHECK ((amount > (0)::numeric)),
    CONSTRAINT payments_deposit_complete_chk CHECK ((((company_bank_account_id IS NULL) AND (deposit_mode IS NULL) AND (deposit_reference IS NULL) AND (deposit_submitted_at IS NULL)) OR ((company_bank_account_id IS NOT NULL) AND (deposit_mode IS NOT NULL) AND (deposit_reference IS NOT NULL) AND (deposit_submitted_at IS NOT NULL) AND ((method)::text = 'BANK_TRANSFER'::text)))),
    CONSTRAINT payments_deposit_mode_chk CHECK (((deposit_mode)::text = ANY ((ARRAY['ONLINE'::character varying, 'CHEQUE'::character varying, 'CASH_DEPOSIT'::character varying])::text[]))),
    CONSTRAINT payments_method_chk CHECK (((method)::text = ANY ((ARRAY['BANK_TRANSFER'::character varying, 'OFFICE_CASH'::character varying, 'CARD'::character varying])::text[]))),
    CONSTRAINT payments_receipt_complete_chk CHECK ((((receipt_number IS NULL) AND (receipt_submitted_at IS NULL)) OR ((receipt_number IS NOT NULL) AND (receipt_submitted_at IS NOT NULL) AND ((method)::text = 'OFFICE_CASH'::text)))),
    CONSTRAINT payments_refunded_chk CHECK ((((status)::text = 'REFUNDED'::text) = (refunded_at IS NOT NULL))),
    CONSTRAINT payments_settled_chk CHECK ((((status)::text = ANY ((ARRAY['SUCCEEDED'::character varying, 'REFUND_REQUIRED'::character varying, 'REFUNDED'::character varying])::text[])) = (settled_at IS NOT NULL))),
    CONSTRAINT payments_status_chk CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'REFUND_REQUIRED'::character varying, 'REFUNDED'::character varying])::text[]))),
    CONSTRAINT payments_task_assignment_chk CHECK (((task_assignee_id IS NULL) = (task_assigned_at IS NULL)))
);

CREATE TABLE permissions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(64) NOT NULL,
    category character varying(32) NOT NULL,
    description character varying(255) NOT NULL
);

CREATE TABLE processed_events (
    consumer character varying(100) NOT NULL,
    event_id uuid NOT NULL,
    processed_at timestamp with time zone NOT NULL
);

CREATE TABLE referral_codes (
    user_id uuid NOT NULL,
    code character varying(12) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT referral_codes_format_chk CHECK (((code)::text ~ '^[2-9A-HJ-NP-Z]{8}$'::text))
);

CREATE TABLE referral_earnings (
    id uuid NOT NULL,
    source_user_id uuid NOT NULL,
    beneficiary_user_id uuid NOT NULL,
    product_id uuid NOT NULL,
    level smallint NOT NULL,
    base_amount numeric(19,4) NOT NULL,
    rate_percent numeric(5,3) NOT NULL,
    amount numeric(19,4) NOT NULL,
    currency character varying(3) NOT NULL,
    rate_version_id uuid NOT NULL,
    source_credit_id uuid,
    source_holding_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    kind character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    CONSTRAINT referral_earnings_amount_chk CHECK (((amount > (0)::numeric) AND (base_amount > (0)::numeric))),
    CONSTRAINT referral_earnings_kind_chk CHECK (((kind)::text = ANY ((ARRAY['MONTHLY'::character varying, 'ONE_TIME'::character varying])::text[]))),
    CONSTRAINT referral_earnings_level_chk CHECK (((level >= 1) AND (level <= 4))),
    CONSTRAINT referral_earnings_not_self_chk CHECK ((source_user_id <> beneficiary_user_id))
);

CREATE TABLE referral_moves (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    from_referrer_id uuid,
    to_referrer_id uuid,
    moved_by uuid NOT NULL,
    reason character varying(500) NOT NULL,
    moved_at timestamp with time zone NOT NULL,
    CONSTRAINT referral_moves_changes_chk CHECK ((((from_referrer_id IS NOT NULL) OR (to_referrer_id IS NOT NULL)) AND (from_referrer_id IS DISTINCT FROM to_referrer_id) AND (to_referrer_id IS DISTINCT FROM user_id)))
);

CREATE TABLE referral_payout_modes (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    year smallint NOT NULL,
    mode character varying(20) NOT NULL,
    chosen_by uuid NOT NULL,
    chosen_at timestamp with time zone NOT NULL,
    CONSTRAINT referral_payout_modes_mode_chk CHECK (((mode)::text = ANY ((ARRAY['BANK_TRANSFER'::character varying, 'OFFICE_CASH'::character varying])::text[])))
);

CREATE TABLE referral_rate_versions (
    id uuid NOT NULL,
    effective_from timestamp with time zone NOT NULL,
    level1_percent numeric(5,3) NOT NULL,
    level2_percent numeric(5,3) NOT NULL,
    level3_percent numeric(5,3) NOT NULL,
    level4_percent numeric(5,3) NOT NULL,
    reason character varying(500) NOT NULL,
    cancelled_at timestamp with time zone,
    cancelled_by uuid,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    one_time_percent numeric(5,3) NOT NULL,
    CONSTRAINT referral_rate_versions_cancelled_chk CHECK (((cancelled_at IS NULL) = (cancelled_by IS NULL))),
    CONSTRAINT referral_rate_versions_one_time_chk CHECK (((one_time_percent >= (0)::numeric) AND (one_time_percent <= (10)::numeric))),
    CONSTRAINT referral_rate_versions_rates_chk CHECK (((level1_percent >= (0)::numeric) AND (level1_percent <= (10)::numeric) AND ((level2_percent >= (0)::numeric) AND (level2_percent <= (10)::numeric)) AND ((level3_percent >= (0)::numeric) AND (level3_percent <= (10)::numeric)) AND ((level4_percent >= (0)::numeric) AND (level4_percent <= (10)::numeric)) AND ((((level1_percent + level2_percent) + level3_percent) + level4_percent) <= (20)::numeric)))
);

CREATE TABLE referrals (
    user_id uuid NOT NULL,
    referrer_id uuid NOT NULL,
    code character varying(12) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT referrals_not_self_chk CHECK ((user_id <> referrer_id))
);

CREATE TABLE refresh_tokens (
    id uuid NOT NULL,
    session_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    issued_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    replaced_by uuid,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT refresh_tokens_expiry_chk CHECK ((expires_at > issued_at))
);

CREATE TABLE rent_accruals (
    id uuid NOT NULL,
    holding_id uuid NOT NULL,
    user_id uuid NOT NULL,
    product_id uuid NOT NULL,
    currency character varying(3) NOT NULL,
    monthly_rent numeric(19,4) NOT NULL,
    capital_total numeric(19,4) DEFAULT 0 NOT NULL,
    tenure_months integer NOT NULL,
    starts_at timestamp with time zone NOT NULL,
    ends_at timestamp with time zone NOT NULL,
    credited_until timestamp with time zone NOT NULL,
    credited_rent numeric(19,4) DEFAULT 0 NOT NULL,
    credited_capital numeric(19,4) DEFAULT 0 NOT NULL,
    completed_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT rent_accruals_amounts_chk CHECK (((monthly_rent >= (0)::numeric) AND (capital_total >= (0)::numeric) AND (tenure_months > 0) AND ((monthly_rent + capital_total) > (0)::numeric))),
    CONSTRAINT rent_accruals_credited_chk CHECK (((credited_until >= starts_at) AND (credited_until <= ends_at) AND (credited_rent >= (0)::numeric) AND (credited_rent <= (monthly_rent * (tenure_months)::numeric)) AND (credited_capital >= (0)::numeric) AND (credited_capital <= capital_total) AND ((completed_at IS NOT NULL) = (credited_until = ends_at)))),
    CONSTRAINT rent_accruals_lease_chk CHECK ((starts_at < ends_at))
);

CREATE TABLE rent_credit_settings (
    id smallint DEFAULT 1 NOT NULL,
    credit_interval_minutes integer,
    updated_by uuid,
    updated_at timestamp with time zone,
    CONSTRAINT rent_credit_settings_interval_chk CHECK (((credit_interval_minutes IS NULL) OR ((credit_interval_minutes >= 5) AND (credit_interval_minutes <= 1440) AND ((1440 % credit_interval_minutes) = 0)))),
    CONSTRAINT rent_credit_settings_single_row_chk CHECK ((id = 1))
);

CREATE TABLE rent_credits (
    id uuid NOT NULL,
    holding_id uuid NOT NULL,
    user_id uuid NOT NULL,
    day date NOT NULL,
    rent_amount numeric(19,4) NOT NULL,
    capital_amount numeric(19,4) NOT NULL,
    currency character varying(3) NOT NULL,
    ledger_transaction_id uuid NOT NULL,
    from_at timestamp with time zone NOT NULL,
    to_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT rent_credits_amounts_chk CHECK (((rent_amount >= (0)::numeric) AND (capital_amount >= (0)::numeric))),
    CONSTRAINT rent_credits_hours_chk CHECK ((from_at < to_at))
);

CREATE TABLE role_permissions (
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL
);

CREATE TABLE roles (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(64) NOT NULL,
    description character varying(255) NOT NULL,
    system boolean DEFAULT false NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT roles_name_format_chk CHECK (((name)::text ~ '^[A-Z][A-Z0-9_]{1,63}$'::text))
);

CREATE TABLE states (
    id integer NOT NULL,
    country_code character varying(2) NOT NULL,
    name character varying(120) NOT NULL,
    code character varying(10)
);

CREATE TABLE stored_documents (
    id uuid NOT NULL,
    owner_user_id uuid NOT NULL,
    purpose character varying(40) NOT NULL,
    content_type character varying(100) NOT NULL,
    size_bytes integer NOT NULL,
    sha256 character varying(64) NOT NULL,
    content bytea,
    created_at timestamp with time zone NOT NULL,
    storage_key character varying(200),
    CONSTRAINT stored_documents_location_chk CHECK (((content IS NULL) <> (storage_key IS NULL))),
    CONSTRAINT stored_documents_size_chk CHECK ((size_bytes > 0)),
    CONSTRAINT stored_documents_type_chk CHECK (((content_type)::text = ANY ((ARRAY['application/pdf'::character varying, 'image/jpeg'::character varying, 'image/png'::character varying])::text[])))
);

CREATE TABLE support_tickets (
    id uuid NOT NULL,
    reference character varying(20) NOT NULL,
    user_id uuid NOT NULL,
    subject character varying(200) NOT NULL,
    category character varying(20) NOT NULL,
    related_type character varying(20),
    related_id uuid,
    related_label character varying(60),
    status character varying(25) NOT NULL,
    priority character varying(10) NOT NULL,
    assignee_id uuid,
    first_response_due_at timestamp with time zone NOT NULL,
    first_responded_at timestamp with time zone,
    last_message_at timestamp with time zone NOT NULL,
    resolved_at timestamp with time zone,
    closed_at timestamp with time zone,
    team character varying(20) DEFAULT 'SUPPORT'::character varying NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT support_tickets_category_chk CHECK (((category)::text = ANY ((ARRAY['ACCOUNT'::character varying, 'INVESTMENT'::character varying, 'PAYMENT'::character varying, 'EARNINGS'::character varying, 'WITHDRAWAL'::character varying, 'REFERRAL'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT support_tickets_closed_chk CHECK ((((status)::text = 'CLOSED'::text) = (closed_at IS NOT NULL))),
    CONSTRAINT support_tickets_priority_chk CHECK (((priority)::text = ANY ((ARRAY['LOW'::character varying, 'NORMAL'::character varying, 'HIGH'::character varying, 'URGENT'::character varying])::text[]))),
    CONSTRAINT support_tickets_related_chk CHECK ((((related_type IS NULL) AND (related_id IS NULL) AND (related_label IS NULL)) OR (((related_type)::text = ANY ((ARRAY['ORDER'::character varying, 'WITHDRAWAL'::character varying, 'HOLDING'::character varying])::text[])) AND (related_id IS NOT NULL) AND (related_label IS NOT NULL)))),
    CONSTRAINT support_tickets_resolved_chk CHECK ((((status)::text <> 'RESOLVED'::text) OR (resolved_at IS NOT NULL))),
    CONSTRAINT support_tickets_status_chk CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'WAITING_ON_CUSTOMER'::character varying, 'RESOLVED'::character varying, 'CLOSED'::character varying])::text[]))),
    CONSTRAINT support_tickets_team_chk CHECK (((team)::text = ANY ((ARRAY['SUPPORT'::character varying, 'FINANCE'::character varying])::text[])))
);

CREATE TABLE terms_versions (
    id uuid NOT NULL,
    version integer NOT NULL,
    title character varying(200) NOT NULL,
    body text NOT NULL,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT terms_versions_body_chk CHECK (((length(body) >= 1) AND (length(body) <= 100000))),
    CONSTRAINT terms_versions_version_chk CHECK ((version > 0))
);

CREATE TABLE ticket_attachments (
    id uuid NOT NULL,
    ticket_id uuid NOT NULL,
    message_id uuid NOT NULL,
    document_id uuid NOT NULL,
    filename character varying(200) NOT NULL,
    content_type character varying(100) NOT NULL,
    size_bytes bigint NOT NULL,
    created_at timestamp with time zone NOT NULL
);

CREATE TABLE ticket_events (
    id uuid NOT NULL,
    ticket_id uuid NOT NULL,
    actor_id uuid,
    actor_is_staff boolean NOT NULL,
    type character varying(20) NOT NULL,
    from_value character varying(25),
    to_value character varying(25),
    subject_user_id uuid,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ticket_events_type_chk CHECK (((type)::text = ANY ((ARRAY['OPENED'::character varying, 'STATUS_CHANGED'::character varying, 'REOPENED'::character varying, 'ASSIGNED'::character varying, 'UNASSIGNED'::character varying, 'PRIORITY_CHANGED'::character varying, 'TEAM_CHANGED'::character varying])::text[])))
);

CREATE TABLE ticket_messages (
    id uuid NOT NULL,
    ticket_id uuid NOT NULL,
    author_id uuid,
    from_staff boolean NOT NULL,
    internal boolean NOT NULL,
    body character varying(8000) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ticket_messages_internal_chk CHECK (((NOT internal) OR from_staff))
);

CREATE TABLE users (
    id uuid NOT NULL,
    email character varying(254) NOT NULL,
    password_hash character varying(255) NOT NULL,
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    status character varying(20) NOT NULL,
    failed_login_attempts integer DEFAULT 0 NOT NULL,
    locked_until timestamp with time zone,
    last_login_at timestamp with time zone,
    password_changed_at timestamp with time zone NOT NULL,
    email_verified_at timestamp with time zone,
    status_reason character varying(500),
    status_changed_at timestamp with time zone,
    status_changed_by uuid,
    mfa_enabled boolean DEFAULT false NOT NULL,
    must_change_password boolean DEFAULT false NOT NULL,
    temporary_password_expires_at timestamp with time zone,
    user_number bigint NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    terms_version integer,
    terms_accepted_at timestamp with time zone,
    CONSTRAINT users_email_normalised_chk CHECK (((email)::text = lower(btrim((email)::text)))),
    CONSTRAINT users_failed_attempts_chk CHECK ((failed_login_attempts >= 0)),
    CONSTRAINT users_status_chk CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying, 'DISABLED'::character varying])::text[]))),
    CONSTRAINT users_temporary_password_chk CHECK ((must_change_password = (temporary_password_expires_at IS NOT NULL)))
);

CREATE TABLE user_profiles (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    phone character varying(20),
    date_of_birth date,
    nationality character varying(2),
    address_line1 character varying(200),
    address_line2 character varying(200),
    city character varying(100),
    state_region character varying(100),
    postal_code character varying(20),
    country character varying(2),
    tax_residency_country character varying(2),
    tax_id_encrypted text,
    tax_id_last4 character varying(4),
    kyc_status character varying(20) DEFAULT 'NOT_SUBMITTED'::character varying NOT NULL,
    email_notifications boolean DEFAULT true NOT NULL,
    sms_notifications boolean DEFAULT false NOT NULL,
    phone_key character varying(20),
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    phone_country character varying(2),
    CONSTRAINT user_profiles_kyc_status_chk CHECK (((kyc_status)::text = ANY ((ARRAY['NOT_SUBMITTED'::character varying, 'PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT user_profiles_tax_id_chk CHECK (((tax_id_encrypted IS NULL) = (tax_id_last4 IS NULL)))
);

CREATE TABLE user_roles (
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,
    assigned_by uuid,
    assigned_at timestamp with time zone NOT NULL
);

CREATE TABLE withdrawal_batch_files (
    id uuid NOT NULL,
    batch_id uuid NOT NULL,
    kind character varying(20) NOT NULL,
    filename character varying(200) NOT NULL,
    content_encrypted text NOT NULL,
    size_bytes integer NOT NULL,
    uploaded_by uuid,
    paid_count integer,
    failed_count integer,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT withdrawal_batch_files_kind_chk CHECK (((kind)::text = ANY ((ARRAY['PAYMENT'::character varying, 'RECONCILIATION'::character varying])::text[])))
);

CREATE TABLE withdrawal_batches (
    id uuid NOT NULL,
    reference character varying(20) NOT NULL,
    currency character varying(3) NOT NULL,
    status character varying(20) NOT NULL,
    item_count integer NOT NULL,
    total_amount numeric(19,4) NOT NULL,
    sent_by uuid,
    sent_at timestamp with time zone,
    closed_at timestamp with time zone,
    period character varying(7),
    kind character varying(20),
    payout_method character varying(20) DEFAULT 'BANK_TRANSFER'::character varying NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT withdrawal_batches_closed_chk CHECK ((((status)::text = 'CLOSED'::text) = (closed_at IS NOT NULL))),
    CONSTRAINT withdrawal_batches_currency_chk CHECK (((currency)::text ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT withdrawal_batches_kind_chk CHECK (((kind IS NULL) OR ((kind)::text = ANY ((ARRAY['RENT'::character varying, 'REFERRAL'::character varying])::text[])))),
    CONSTRAINT withdrawal_batches_payout_method_chk CHECK (((payout_method)::text = ANY ((ARRAY['BANK_TRANSFER'::character varying, 'OFFICE_CASH'::character varying])::text[]))),
    CONSTRAINT withdrawal_batches_period_chk CHECK (((period IS NULL) OR ((period)::text ~ '^[0-9]{4}-[0-9]{2}$'::text))),
    CONSTRAINT withdrawal_batches_sent_chk CHECK ((((status)::text = ANY ((ARRAY['SENT'::character varying, 'CLOSED'::character varying])::text[])) = (sent_at IS NOT NULL))),
    CONSTRAINT withdrawal_batches_status_chk CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'SENT'::character varying, 'CLOSED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT withdrawal_batches_totals_chk CHECK (((item_count > 0) AND (total_amount > (0)::numeric)))
);

CREATE TABLE withdrawal_runs (
    id uuid NOT NULL,
    reference character varying(20) NOT NULL,
    currency character varying(3) NOT NULL,
    run_at timestamp with time zone NOT NULL,
    triggered_by uuid NOT NULL,
    bank_batch_id uuid,
    cash_batch_id uuid,
    bank_count integer DEFAULT 0 NOT NULL,
    bank_total numeric(19,4) DEFAULT 0 NOT NULL,
    cash_count integer DEFAULT 0 NOT NULL,
    cash_total numeric(19,4) DEFAULT 0 NOT NULL,
    skipped jsonb DEFAULT '[]'::jsonb NOT NULL,
    kind character varying(20),
    CONSTRAINT withdrawal_runs_counts_chk CHECK (((bank_count >= 0) AND (cash_count >= 0) AND ((bank_count > 0) = (bank_batch_id IS NOT NULL)) AND ((cash_count > 0) = (cash_batch_id IS NOT NULL)))),
    CONSTRAINT withdrawal_runs_kind_chk CHECK (((kind IS NULL) OR ((kind)::text = ANY ((ARRAY['RENT'::character varying, 'REFERRAL'::character varying])::text[]))))
);

CREATE TABLE withdrawal_settings (
    id smallint DEFAULT 1 NOT NULL,
    minimum_amount numeric(19,4),
    updated_by uuid,
    updated_at timestamp with time zone,
    CONSTRAINT withdrawal_settings_minimum_chk CHECK (((minimum_amount IS NULL) OR (minimum_amount > (0)::numeric))),
    CONSTRAINT withdrawal_settings_single_row_chk CHECK ((id = 1))
);

CREATE TABLE withdrawals (
    id uuid NOT NULL,
    reference character varying(20) NOT NULL,
    user_id uuid NOT NULL,
    amount numeric(19,4) NOT NULL,
    currency character varying(3) NOT NULL,
    status character varying(20) NOT NULL,
    idempotency_key character varying(100) NOT NULL,
    bank_account_id uuid,
    bank_holder_name character varying(140),
    bank_name character varying(140),
    bank_account_last4 character varying(4),
    required_approvals smallint NOT NULL,
    first_approved_by uuid,
    first_approved_at timestamp with time zone,
    second_approved_by uuid,
    second_approved_at timestamp with time zone,
    rejected_by uuid,
    rejection_reason character varying(500),
    batch_id uuid,
    payout_reference character varying(100),
    failure_reason character varying(500),
    closed_at timestamp with time zone,
    origin character varying(20) DEFAULT 'INVESTOR'::character varying NOT NULL,
    payout_method character varying(20) DEFAULT 'BANK_TRANSFER'::character varying NOT NULL,
    balance character varying(20) DEFAULT 'RENT_BANK'::character varying NOT NULL,
    ticket_id uuid,
    task_assignee_id uuid,
    task_assigned_at timestamp with time zone,
    task_done_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT withdrawals_amount_chk CHECK ((amount > (0)::numeric)),
    CONSTRAINT withdrawals_approvals_chk CHECK ((required_approvals = ANY (ARRAY[1, 2]))),
    CONSTRAINT withdrawals_approved_chk CHECK ((((status)::text = ANY ((ARRAY['PENDING_APPROVAL'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying])::text[])) OR ((origin)::text = ANY ((ARRAY['SCHEDULED'::character varying, 'OFFICE'::character varying, 'REQUESTED'::character varying])::text[])) OR ((first_approved_by IS NOT NULL) AND ((required_approvals = 1) OR (second_approved_by IS NOT NULL))))),
    CONSTRAINT withdrawals_balance_chk CHECK (((balance)::text = ANY ((ARRAY['RENT_BANK'::character varying, 'RENT_CASH'::character varying, 'REFERRAL'::character varying])::text[]))),
    CONSTRAINT withdrawals_batched_chk CHECK ((((((payout_method)::text = 'BANK_TRANSFER'::text) OR ((origin)::text = ANY ((ARRAY['SCHEDULED'::character varying, 'REQUESTED'::character varying])::text[]))) AND (((status)::text = ANY ((ARRAY['BATCHED'::character varying, 'PROCESSING'::character varying, 'PAID'::character varying, 'FAILED'::character varying, 'RETURNED'::character varying])::text[])) = (batch_id IS NOT NULL))) OR (((payout_method)::text = 'OFFICE_CASH'::text) AND ((origin)::text <> ALL ((ARRAY['SCHEDULED'::character varying, 'REQUESTED'::character varying])::text[])) AND (batch_id IS NULL) AND ((status)::text = ANY ((ARRAY['PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'PAID'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying])::text[]))))),
    CONSTRAINT withdrawals_cash_origin_chk CHECK ((((payout_method)::text = 'BANK_TRANSFER'::text) OR ((origin)::text = ANY ((ARRAY['INVESTOR'::character varying, 'OFFICE'::character varying, 'SCHEDULED'::character varying, 'REQUESTED'::character varying])::text[])))),
    CONSTRAINT withdrawals_closed_chk CHECK ((((status)::text = ANY ((ARRAY['PAID'::character varying, 'RETURNED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying])::text[])) = (closed_at IS NOT NULL))),
    CONSTRAINT withdrawals_currency_chk CHECK (((currency)::text ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT withdrawals_failed_chk CHECK (((((status)::text <> 'FAILED'::text) OR (failure_reason IS NOT NULL)) AND ((failure_reason IS NULL) OR ((status)::text = ANY ((ARRAY['FAILED'::character varying, 'RETURNED'::character varying, 'PAID'::character varying])::text[]))))),
    CONSTRAINT withdrawals_not_own_chk CHECK ((((first_approved_by IS NULL) OR (first_approved_by <> user_id)) AND ((second_approved_by IS NULL) OR ((second_approved_by <> user_id) AND (second_approved_by <> first_approved_by))) AND ((rejected_by IS NULL) OR (rejected_by <> user_id)))),
    CONSTRAINT withdrawals_office_chk CHECK (((((origin)::text = 'OFFICE'::text) = (created_by IS NOT NULL)) AND (((origin)::text <> 'OFFICE'::text) OR ((payout_method)::text = 'OFFICE_CASH'::text)))),
    CONSTRAINT withdrawals_origin_chk CHECK (((origin)::text = ANY ((ARRAY['INVESTOR'::character varying, 'SCHEDULED'::character varying, 'OFFICE'::character varying, 'REQUESTED'::character varying])::text[]))),
    CONSTRAINT withdrawals_payout_bank_chk CHECK ((((payout_method)::text = 'BANK_TRANSFER'::text) = ((bank_account_id IS NOT NULL) AND (bank_holder_name IS NOT NULL) AND (bank_account_last4 IS NOT NULL)))),
    CONSTRAINT withdrawals_payout_method_chk CHECK (((payout_method)::text = ANY ((ARRAY['BANK_TRANSFER'::character varying, 'OFFICE_CASH'::character varying])::text[]))),
    CONSTRAINT withdrawals_rejected_chk CHECK ((((status)::text = 'REJECTED'::text) = (rejection_reason IS NOT NULL))),
    CONSTRAINT withdrawals_status_chk CHECK (((status)::text = ANY ((ARRAY['PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'BATCHED'::character varying, 'PROCESSING'::character varying, 'PAID'::character varying, 'FAILED'::character varying, 'RETURNED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT withdrawals_task_assignment_chk CHECK (((task_assignee_id IS NULL) = (task_assigned_at IS NULL)))
);

-- -------------------- Sequence ownership and defaults

ALTER SEQUENCE user_number_seq OWNED BY users.user_number;
ALTER TABLE ONLY users ALTER COLUMN user_number SET DEFAULT nextval('user_number_seq'::regclass);
