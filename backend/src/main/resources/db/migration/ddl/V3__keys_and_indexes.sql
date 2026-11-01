-- Primary, unique and foreign keys, and indexes.

-- -------------------- Primary and unique keys

ALTER TABLE ONLY agreement_templates
    ADD CONSTRAINT agreement_templates_pkey PRIMARY KEY (id);
ALTER TABLE ONLY agreement_templates
    ADD CONSTRAINT agreement_templates_version_uk UNIQUE (version);
ALTER TABLE ONLY audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);
ALTER TABLE ONLY auth_sessions
    ADD CONSTRAINT auth_sessions_pkey PRIMARY KEY (id);
ALTER TABLE ONLY bank_accounts
    ADD CONSTRAINT bank_accounts_pkey PRIMARY KEY (id);
ALTER TABLE ONLY cities
    ADD CONSTRAINT cities_pkey PRIMARY KEY (id);
ALTER TABLE ONLY company_bank_accounts
    ADD CONSTRAINT company_bank_accounts_pkey PRIMARY KEY (id);
ALTER TABLE ONLY containers
    ADD CONSTRAINT containers_number_uk UNIQUE (container_number);
ALTER TABLE ONLY containers
    ADD CONSTRAINT containers_pkey PRIMARY KEY (id);
ALTER TABLE ONLY countries
    ADD CONSTRAINT countries_pkey PRIMARY KEY (code);
ALTER TABLE ONLY demo_accounts
    ADD CONSTRAINT demo_accounts_pkey PRIMARY KEY (user_id);
ALTER TABLE ONLY demo_members
    ADD CONSTRAINT demo_members_pkey PRIMARY KEY (id);
ALTER TABLE ONLY demo_orders
    ADD CONSTRAINT demo_orders_pkey PRIMARY KEY (id);
ALTER TABLE ONLY email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_hash_uk UNIQUE (token_hash);
ALTER TABLE ONLY email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_pkey PRIMARY KEY (id);
ALTER TABLE ONLY holdings
    ADD CONSTRAINT holdings_order_item_uk UNIQUE (order_item_id);
ALTER TABLE ONLY holdings
    ADD CONSTRAINT holdings_pkey PRIMARY KEY (id);
ALTER TABLE ONLY investment_products
    ADD CONSTRAINT investment_products_code_uk UNIQUE (code);
ALTER TABLE ONLY investment_products
    ADD CONSTRAINT investment_products_pkey PRIMARY KEY (id);
ALTER TABLE ONLY invoice_lines
    ADD CONSTRAINT invoice_lines_number_uk UNIQUE (invoice_id, line_number);
ALTER TABLE ONLY invoice_lines
    ADD CONSTRAINT invoice_lines_pkey PRIMARY KEY (id);
ALTER TABLE ONLY invoices
    ADD CONSTRAINT invoices_number_uk UNIQUE (invoice_number);
ALTER TABLE ONLY invoices
    ADD CONSTRAINT invoices_order_uk UNIQUE (order_id);
ALTER TABLE ONLY invoices
    ADD CONSTRAINT invoices_pkey PRIMARY KEY (id);
ALTER TABLE ONLY kyc_documents
    ADD CONSTRAINT kyc_documents_pkey PRIMARY KEY (submission_id, document_id);
ALTER TABLE ONLY kyc_documents
    ADD CONSTRAINT kyc_documents_purpose_uk UNIQUE (submission_id, purpose);
ALTER TABLE ONLY kyc_follow_ups
    ADD CONSTRAINT kyc_follow_ups_pkey PRIMARY KEY (user_id);
ALTER TABLE ONLY kyc_submissions
    ADD CONSTRAINT kyc_submissions_pkey PRIMARY KEY (id);
ALTER TABLE ONLY lead_activities
    ADD CONSTRAINT lead_activities_pkey PRIMARY KEY (id);
ALTER TABLE ONLY leads
    ADD CONSTRAINT leads_pkey PRIMARY KEY (id);
ALTER TABLE ONLY leads
    ADD CONSTRAINT leads_reference_uk UNIQUE (reference);
ALTER TABLE ONLY lease_agreement_acceptances
    ADD CONSTRAINT lease_agreement_acceptances_pkey PRIMARY KEY (agreement_id);
ALTER TABLE ONLY lease_agreements
    ADD CONSTRAINT lease_agreements_holding_uk UNIQUE (holding_id);
ALTER TABLE ONLY lease_agreements
    ADD CONSTRAINT lease_agreements_number_uk UNIQUE (agreement_number);
ALTER TABLE ONLY lease_agreements
    ADD CONSTRAINT lease_agreements_pkey PRIMARY KEY (id);
ALTER TABLE ONLY ledger_accounts
    ADD CONSTRAINT ledger_accounts_pkey PRIMARY KEY (id);
ALTER TABLE ONLY ledger_entries
    ADD CONSTRAINT ledger_entries_pkey PRIMARY KEY (id);
ALTER TABLE ONLY ledger_transactions
    ADD CONSTRAINT ledger_transactions_pkey PRIMARY KEY (id);
ALTER TABLE ONLY ledger_transactions
    ADD CONSTRAINT ledger_transactions_reference_uk UNIQUE (transaction_type, reference);
ALTER TABLE ONLY network_investors
    ADD CONSTRAINT network_investors_pkey PRIMARY KEY (user_id);
ALTER TABLE ONLY notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);
ALTER TABLE ONLY order_items
    ADD CONSTRAINT order_items_order_container_uk UNIQUE (order_id, container_id);
ALTER TABLE ONLY order_items
    ADD CONSTRAINT order_items_pkey PRIMARY KEY (id);
ALTER TABLE ONLY orders
    ADD CONSTRAINT orders_idempotency_uk UNIQUE (user_id, idempotency_key);
ALTER TABLE ONLY orders
    ADD CONSTRAINT orders_number_uk UNIQUE (order_number);
ALTER TABLE ONLY orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);
ALTER TABLE ONLY outbox_events
    ADD CONSTRAINT outbox_events_pkey PRIMARY KEY (id);
ALTER TABLE ONLY password_reset_codes
    ADD CONSTRAINT password_reset_codes_pkey PRIMARY KEY (id);
ALTER TABLE ONLY payments
    ADD CONSTRAINT payments_idempotency_uk UNIQUE (order_id, idempotency_key);
ALTER TABLE ONLY payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (id);
ALTER TABLE ONLY payments
    ADD CONSTRAINT payments_provider_reference_uk UNIQUE (provider, provider_reference);
ALTER TABLE ONLY permissions
    ADD CONSTRAINT permissions_code_uk UNIQUE (code);
ALTER TABLE ONLY permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);
ALTER TABLE ONLY processed_events
    ADD CONSTRAINT processed_events_pkey PRIMARY KEY (consumer, event_id);
ALTER TABLE ONLY referral_codes
    ADD CONSTRAINT referral_codes_code_uk UNIQUE (code);
ALTER TABLE ONLY referral_codes
    ADD CONSTRAINT referral_codes_pkey PRIMARY KEY (user_id);
ALTER TABLE ONLY referral_earnings
    ADD CONSTRAINT referral_earnings_pkey PRIMARY KEY (id);
ALTER TABLE ONLY referral_moves
    ADD CONSTRAINT referral_moves_pkey PRIMARY KEY (id);
ALTER TABLE ONLY referral_payout_modes
    ADD CONSTRAINT referral_payout_modes_pkey PRIMARY KEY (id);
ALTER TABLE ONLY referral_payout_modes
    ADD CONSTRAINT referral_payout_modes_user_year_uk UNIQUE (user_id, year);
ALTER TABLE ONLY referral_rate_versions
    ADD CONSTRAINT referral_rate_versions_pkey PRIMARY KEY (id);
ALTER TABLE ONLY referrals
    ADD CONSTRAINT referrals_pkey PRIMARY KEY (user_id);
ALTER TABLE ONLY refresh_tokens
    ADD CONSTRAINT refresh_tokens_hash_uk UNIQUE (token_hash);
ALTER TABLE ONLY refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);
ALTER TABLE ONLY rent_accruals
    ADD CONSTRAINT rent_accruals_holding_uk UNIQUE (holding_id);
ALTER TABLE ONLY rent_accruals
    ADD CONSTRAINT rent_accruals_pkey PRIMARY KEY (id);
ALTER TABLE ONLY rent_credit_settings
    ADD CONSTRAINT rent_credit_settings_pkey PRIMARY KEY (id);
ALTER TABLE ONLY rent_credits
    ADD CONSTRAINT rent_credits_from_uk UNIQUE (holding_id, from_at);
ALTER TABLE ONLY rent_credits
    ADD CONSTRAINT rent_credits_pkey PRIMARY KEY (id);
ALTER TABLE ONLY role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (role_id, permission_id);
ALTER TABLE ONLY roles
    ADD CONSTRAINT roles_name_uk UNIQUE (name);
ALTER TABLE ONLY roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);
ALTER TABLE ONLY states
    ADD CONSTRAINT states_pkey PRIMARY KEY (id);
ALTER TABLE ONLY stored_documents
    ADD CONSTRAINT stored_documents_pkey PRIMARY KEY (id);
ALTER TABLE ONLY support_tickets
    ADD CONSTRAINT support_tickets_pkey PRIMARY KEY (id);
ALTER TABLE ONLY support_tickets
    ADD CONSTRAINT support_tickets_reference_uk UNIQUE (reference);
ALTER TABLE ONLY terms_versions
    ADD CONSTRAINT terms_versions_pkey PRIMARY KEY (id);
ALTER TABLE ONLY terms_versions
    ADD CONSTRAINT terms_versions_version_uk UNIQUE (version);
ALTER TABLE ONLY ticket_attachments
    ADD CONSTRAINT ticket_attachments_document_uk UNIQUE (document_id);
ALTER TABLE ONLY ticket_attachments
    ADD CONSTRAINT ticket_attachments_pkey PRIMARY KEY (id);
ALTER TABLE ONLY ticket_events
    ADD CONSTRAINT ticket_events_pkey PRIMARY KEY (id);
ALTER TABLE ONLY ticket_messages
    ADD CONSTRAINT ticket_messages_pkey PRIMARY KEY (id);
ALTER TABLE ONLY user_profiles
    ADD CONSTRAINT user_profiles_pkey PRIMARY KEY (id);
ALTER TABLE ONLY user_profiles
    ADD CONSTRAINT user_profiles_user_uk UNIQUE (user_id);
ALTER TABLE ONLY user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id);
ALTER TABLE ONLY users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);
ALTER TABLE ONLY users
    ADD CONSTRAINT users_user_number_uk UNIQUE (user_number);
ALTER TABLE ONLY withdrawal_batch_files
    ADD CONSTRAINT withdrawal_batch_files_pkey PRIMARY KEY (id);
ALTER TABLE ONLY withdrawal_batches
    ADD CONSTRAINT withdrawal_batches_pkey PRIMARY KEY (id);
ALTER TABLE ONLY withdrawal_batches
    ADD CONSTRAINT withdrawal_batches_reference_uk UNIQUE (reference);
ALTER TABLE ONLY withdrawal_runs
    ADD CONSTRAINT withdrawal_runs_pkey PRIMARY KEY (id);
ALTER TABLE ONLY withdrawal_runs
    ADD CONSTRAINT withdrawal_runs_reference_uk UNIQUE (reference);
ALTER TABLE ONLY withdrawal_settings
    ADD CONSTRAINT withdrawal_settings_pkey PRIMARY KEY (id);
ALTER TABLE ONLY withdrawals
    ADD CONSTRAINT withdrawals_idempotency_uk UNIQUE (user_id, idempotency_key);
ALTER TABLE ONLY withdrawals
    ADD CONSTRAINT withdrawals_pkey PRIMARY KEY (id);
ALTER TABLE ONLY withdrawals
    ADD CONSTRAINT withdrawals_reference_uk UNIQUE (reference);

-- -------------------- Foreign keys

ALTER TABLE ONLY agreement_templates
    ADD CONSTRAINT agreement_templates_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE ONLY auth_sessions
    ADD CONSTRAINT auth_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY bank_accounts
    ADD CONSTRAINT bank_accounts_assigned_to_fkey FOREIGN KEY (assigned_to) REFERENCES users(id);
ALTER TABLE ONLY bank_accounts
    ADD CONSTRAINT bank_accounts_proof_document_id_fkey FOREIGN KEY (proof_document_id) REFERENCES stored_documents(id);
ALTER TABLE ONLY bank_accounts
    ADD CONSTRAINT bank_accounts_replaces_account_id_fkey FOREIGN KEY (replaces_account_id) REFERENCES bank_accounts(id);
ALTER TABLE ONLY bank_accounts
    ADD CONSTRAINT bank_accounts_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY bank_accounts
    ADD CONSTRAINT bank_accounts_verified_by_fkey FOREIGN KEY (verified_by) REFERENCES users(id);
ALTER TABLE ONLY cities
    ADD CONSTRAINT cities_state_id_fkey FOREIGN KEY (state_id) REFERENCES states(id);
ALTER TABLE ONLY company_bank_accounts
    ADD CONSTRAINT company_bank_accounts_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE ONLY containers
    ADD CONSTRAINT containers_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE ONLY containers
    ADD CONSTRAINT containers_reserved_order_id_fkey FOREIGN KEY (reserved_order_id) REFERENCES orders(id);
ALTER TABLE ONLY demo_accounts
    ADD CONSTRAINT demo_accounts_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE ONLY demo_accounts
    ADD CONSTRAINT demo_accounts_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY demo_members
    ADD CONSTRAINT demo_members_account_id_fkey FOREIGN KEY (account_id) REFERENCES demo_accounts(user_id) ON DELETE CASCADE;
ALTER TABLE ONLY demo_members
    ADD CONSTRAINT demo_members_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES demo_members(id) ON DELETE CASCADE;
ALTER TABLE ONLY demo_orders
    ADD CONSTRAINT demo_orders_account_id_fkey FOREIGN KEY (account_id) REFERENCES demo_accounts(user_id) ON DELETE CASCADE;
ALTER TABLE ONLY demo_orders
    ADD CONSTRAINT demo_orders_member_id_fkey FOREIGN KEY (member_id) REFERENCES demo_members(id) ON DELETE CASCADE;
ALTER TABLE ONLY demo_orders
    ADD CONSTRAINT demo_orders_product_id_fkey FOREIGN KEY (product_id) REFERENCES investment_products(id);
ALTER TABLE ONLY email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY holdings
    ADD CONSTRAINT holdings_assigned_by_fkey FOREIGN KEY (assigned_by) REFERENCES users(id);
ALTER TABLE ONLY holdings
    ADD CONSTRAINT holdings_container_id_fkey FOREIGN KEY (container_id) REFERENCES containers(id);
ALTER TABLE ONLY holdings
    ADD CONSTRAINT holdings_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id);
ALTER TABLE ONLY holdings
    ADD CONSTRAINT holdings_order_item_id_fkey FOREIGN KEY (order_item_id) REFERENCES order_items(id);
ALTER TABLE ONLY holdings
    ADD CONSTRAINT holdings_product_id_fkey FOREIGN KEY (product_id) REFERENCES investment_products(id);
ALTER TABLE ONLY holdings
    ADD CONSTRAINT holdings_task_assignee_id_fkey FOREIGN KEY (task_assignee_id) REFERENCES users(id);
ALTER TABLE ONLY holdings
    ADD CONSTRAINT holdings_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY investment_products
    ADD CONSTRAINT investment_products_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE ONLY investment_products
    ADD CONSTRAINT investment_products_published_by_fkey FOREIGN KEY (published_by) REFERENCES users(id);
ALTER TABLE ONLY invoice_lines
    ADD CONSTRAINT invoice_lines_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES invoices(id);
ALTER TABLE ONLY invoices
    ADD CONSTRAINT invoices_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id);
ALTER TABLE ONLY invoices
    ADD CONSTRAINT invoices_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES payments(id);
ALTER TABLE ONLY invoices
    ADD CONSTRAINT invoices_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY kyc_documents
    ADD CONSTRAINT kyc_documents_document_id_fkey FOREIGN KEY (document_id) REFERENCES stored_documents(id);
ALTER TABLE ONLY kyc_documents
    ADD CONSTRAINT kyc_documents_submission_id_fkey FOREIGN KEY (submission_id) REFERENCES kyc_submissions(id);
ALTER TABLE ONLY kyc_follow_ups
    ADD CONSTRAINT kyc_follow_ups_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES support_tickets(id);
ALTER TABLE ONLY kyc_follow_ups
    ADD CONSTRAINT kyc_follow_ups_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY kyc_submissions
    ADD CONSTRAINT kyc_submissions_assigned_to_fkey FOREIGN KEY (assigned_to) REFERENCES users(id);
ALTER TABLE ONLY kyc_submissions
    ADD CONSTRAINT kyc_submissions_reviewed_by_fkey FOREIGN KEY (reviewed_by) REFERENCES users(id);
ALTER TABLE ONLY kyc_submissions
    ADD CONSTRAINT kyc_submissions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY lead_activities
    ADD CONSTRAINT lead_activities_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES users(id);
ALTER TABLE ONLY lead_activities
    ADD CONSTRAINT lead_activities_lead_id_fkey FOREIGN KEY (lead_id) REFERENCES leads(id);
ALTER TABLE ONLY leads
    ADD CONSTRAINT leads_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE ONLY leads
    ADD CONSTRAINT leads_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users(id);
ALTER TABLE ONLY leads
    ADD CONSTRAINT leads_phone_country_fkey FOREIGN KEY (phone_country) REFERENCES countries(code);
ALTER TABLE ONLY leads
    ADD CONSTRAINT leads_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY leads
    ADD CONSTRAINT leads_won_order_id_fkey FOREIGN KEY (won_order_id) REFERENCES orders(id);
ALTER TABLE ONLY lease_agreement_acceptances
    ADD CONSTRAINT lease_agreement_acceptances_agreement_id_fkey FOREIGN KEY (agreement_id) REFERENCES lease_agreements(id);
ALTER TABLE ONLY lease_agreement_acceptances
    ADD CONSTRAINT lease_agreement_acceptances_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY lease_agreements
    ADD CONSTRAINT lease_agreements_generated_by_fkey FOREIGN KEY (generated_by) REFERENCES users(id);
ALTER TABLE ONLY lease_agreements
    ADD CONSTRAINT lease_agreements_holding_id_fkey FOREIGN KEY (holding_id) REFERENCES holdings(id);
ALTER TABLE ONLY lease_agreements
    ADD CONSTRAINT lease_agreements_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id);
ALTER TABLE ONLY lease_agreements
    ADD CONSTRAINT lease_agreements_template_id_fkey FOREIGN KEY (template_id) REFERENCES agreement_templates(id);
ALTER TABLE ONLY lease_agreements
    ADD CONSTRAINT lease_agreements_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY ledger_accounts
    ADD CONSTRAINT ledger_accounts_owner_user_id_fkey FOREIGN KEY (owner_user_id) REFERENCES users(id);
ALTER TABLE ONLY ledger_entries
    ADD CONSTRAINT ledger_entries_account_id_fkey FOREIGN KEY (account_id) REFERENCES ledger_accounts(id);
ALTER TABLE ONLY ledger_entries
    ADD CONSTRAINT ledger_entries_transaction_id_fkey FOREIGN KEY (transaction_id) REFERENCES ledger_transactions(id);
ALTER TABLE ONLY ledger_transactions
    ADD CONSTRAINT ledger_transactions_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE ONLY network_investors
    ADD CONSTRAINT network_investors_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE ONLY network_investors
    ADD CONSTRAINT network_investors_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY notifications
    ADD CONSTRAINT notifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY order_items
    ADD CONSTRAINT order_items_container_id_fkey FOREIGN KEY (container_id) REFERENCES containers(id);
ALTER TABLE ONLY order_items
    ADD CONSTRAINT order_items_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id);
ALTER TABLE ONLY order_items
    ADD CONSTRAINT order_items_product_id_fkey FOREIGN KEY (product_id) REFERENCES investment_products(id);
ALTER TABLE ONLY orders
    ADD CONSTRAINT orders_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY password_reset_codes
    ADD CONSTRAINT password_reset_codes_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY payments
    ADD CONSTRAINT payments_company_bank_account_id_fkey FOREIGN KEY (company_bank_account_id) REFERENCES company_bank_accounts(id);
ALTER TABLE ONLY payments
    ADD CONSTRAINT payments_confirmed_by_fkey FOREIGN KEY (confirmed_by) REFERENCES users(id);
ALTER TABLE ONLY payments
    ADD CONSTRAINT payments_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id);
ALTER TABLE ONLY payments
    ADD CONSTRAINT payments_refunded_by_fkey FOREIGN KEY (refunded_by) REFERENCES users(id);
ALTER TABLE ONLY payments
    ADD CONSTRAINT payments_task_assignee_id_fkey FOREIGN KEY (task_assignee_id) REFERENCES users(id);
ALTER TABLE ONLY payments
    ADD CONSTRAINT payments_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY referral_codes
    ADD CONSTRAINT referral_codes_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY referral_earnings
    ADD CONSTRAINT referral_earnings_beneficiary_user_id_fkey FOREIGN KEY (beneficiary_user_id) REFERENCES users(id);
ALTER TABLE ONLY referral_earnings
    ADD CONSTRAINT referral_earnings_product_id_fkey FOREIGN KEY (product_id) REFERENCES investment_products(id);
ALTER TABLE ONLY referral_earnings
    ADD CONSTRAINT referral_earnings_rate_version_id_fkey FOREIGN KEY (rate_version_id) REFERENCES referral_rate_versions(id);
ALTER TABLE ONLY referral_earnings
    ADD CONSTRAINT referral_earnings_source_credit_id_fkey FOREIGN KEY (source_credit_id) REFERENCES rent_credits(id);
ALTER TABLE ONLY referral_earnings
    ADD CONSTRAINT referral_earnings_source_holding_id_fkey FOREIGN KEY (source_holding_id) REFERENCES holdings(id);
ALTER TABLE ONLY referral_earnings
    ADD CONSTRAINT referral_earnings_source_user_id_fkey FOREIGN KEY (source_user_id) REFERENCES users(id);
ALTER TABLE ONLY referral_moves
    ADD CONSTRAINT referral_moves_from_referrer_id_fkey FOREIGN KEY (from_referrer_id) REFERENCES users(id);
ALTER TABLE ONLY referral_moves
    ADD CONSTRAINT referral_moves_moved_by_fkey FOREIGN KEY (moved_by) REFERENCES users(id);
ALTER TABLE ONLY referral_moves
    ADD CONSTRAINT referral_moves_to_referrer_id_fkey FOREIGN KEY (to_referrer_id) REFERENCES users(id);
ALTER TABLE ONLY referral_moves
    ADD CONSTRAINT referral_moves_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY referral_payout_modes
    ADD CONSTRAINT referral_payout_modes_chosen_by_fkey FOREIGN KEY (chosen_by) REFERENCES users(id);
ALTER TABLE ONLY referral_payout_modes
    ADD CONSTRAINT referral_payout_modes_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY referral_rate_versions
    ADD CONSTRAINT referral_rate_versions_cancelled_by_fkey FOREIGN KEY (cancelled_by) REFERENCES users(id);
ALTER TABLE ONLY referral_rate_versions
    ADD CONSTRAINT referral_rate_versions_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE ONLY referrals
    ADD CONSTRAINT referrals_referrer_id_fkey FOREIGN KEY (referrer_id) REFERENCES users(id);
ALTER TABLE ONLY referrals
    ADD CONSTRAINT referrals_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY refresh_tokens
    ADD CONSTRAINT refresh_tokens_replaced_by_fkey FOREIGN KEY (replaced_by) REFERENCES refresh_tokens(id);
ALTER TABLE ONLY refresh_tokens
    ADD CONSTRAINT refresh_tokens_session_id_fkey FOREIGN KEY (session_id) REFERENCES auth_sessions(id);
ALTER TABLE ONLY rent_accruals
    ADD CONSTRAINT rent_accruals_holding_id_fkey FOREIGN KEY (holding_id) REFERENCES holdings(id);
ALTER TABLE ONLY rent_accruals
    ADD CONSTRAINT rent_accruals_product_id_fkey FOREIGN KEY (product_id) REFERENCES investment_products(id);
ALTER TABLE ONLY rent_accruals
    ADD CONSTRAINT rent_accruals_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY rent_credit_settings
    ADD CONSTRAINT rent_credit_settings_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES users(id);
ALTER TABLE ONLY rent_credits
    ADD CONSTRAINT rent_credits_holding_id_fkey FOREIGN KEY (holding_id) REFERENCES holdings(id);
ALTER TABLE ONLY rent_credits
    ADD CONSTRAINT rent_credits_ledger_transaction_id_fkey FOREIGN KEY (ledger_transaction_id) REFERENCES ledger_transactions(id);
ALTER TABLE ONLY rent_credits
    ADD CONSTRAINT rent_credits_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY role_permissions
    ADD CONSTRAINT role_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES permissions(id);
ALTER TABLE ONLY role_permissions
    ADD CONSTRAINT role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE;
ALTER TABLE ONLY states
    ADD CONSTRAINT states_country_code_fkey FOREIGN KEY (country_code) REFERENCES countries(code);
ALTER TABLE ONLY stored_documents
    ADD CONSTRAINT stored_documents_owner_user_id_fkey FOREIGN KEY (owner_user_id) REFERENCES users(id);
ALTER TABLE ONLY support_tickets
    ADD CONSTRAINT support_tickets_assignee_id_fkey FOREIGN KEY (assignee_id) REFERENCES users(id);
ALTER TABLE ONLY support_tickets
    ADD CONSTRAINT support_tickets_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY terms_versions
    ADD CONSTRAINT terms_versions_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE ONLY ticket_attachments
    ADD CONSTRAINT ticket_attachments_document_id_fkey FOREIGN KEY (document_id) REFERENCES stored_documents(id);
ALTER TABLE ONLY ticket_attachments
    ADD CONSTRAINT ticket_attachments_message_id_fkey FOREIGN KEY (message_id) REFERENCES ticket_messages(id);
ALTER TABLE ONLY ticket_attachments
    ADD CONSTRAINT ticket_attachments_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES support_tickets(id);
ALTER TABLE ONLY ticket_events
    ADD CONSTRAINT ticket_events_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES users(id);
ALTER TABLE ONLY ticket_events
    ADD CONSTRAINT ticket_events_subject_user_id_fkey FOREIGN KEY (subject_user_id) REFERENCES users(id);
ALTER TABLE ONLY ticket_events
    ADD CONSTRAINT ticket_events_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES support_tickets(id);
ALTER TABLE ONLY ticket_messages
    ADD CONSTRAINT ticket_messages_author_id_fkey FOREIGN KEY (author_id) REFERENCES users(id);
ALTER TABLE ONLY ticket_messages
    ADD CONSTRAINT ticket_messages_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES support_tickets(id);
ALTER TABLE ONLY user_profiles
    ADD CONSTRAINT user_profiles_phone_country_fkey FOREIGN KEY (phone_country) REFERENCES countries(code);
ALTER TABLE ONLY user_profiles
    ADD CONSTRAINT user_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY user_roles
    ADD CONSTRAINT user_roles_assigned_by_fkey FOREIGN KEY (assigned_by) REFERENCES users(id);
ALTER TABLE ONLY user_roles
    ADD CONSTRAINT user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES roles(id);
ALTER TABLE ONLY user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY users
    ADD CONSTRAINT users_status_changed_by_fkey FOREIGN KEY (status_changed_by) REFERENCES users(id);
ALTER TABLE ONLY withdrawal_batch_files
    ADD CONSTRAINT withdrawal_batch_files_batch_id_fkey FOREIGN KEY (batch_id) REFERENCES withdrawal_batches(id);
ALTER TABLE ONLY withdrawal_batch_files
    ADD CONSTRAINT withdrawal_batch_files_uploaded_by_fkey FOREIGN KEY (uploaded_by) REFERENCES users(id);
ALTER TABLE ONLY withdrawal_batches
    ADD CONSTRAINT withdrawal_batches_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE ONLY withdrawal_batches
    ADD CONSTRAINT withdrawal_batches_sent_by_fkey FOREIGN KEY (sent_by) REFERENCES users(id);
ALTER TABLE ONLY withdrawal_runs
    ADD CONSTRAINT withdrawal_runs_bank_batch_id_fkey FOREIGN KEY (bank_batch_id) REFERENCES withdrawal_batches(id);
ALTER TABLE ONLY withdrawal_runs
    ADD CONSTRAINT withdrawal_runs_cash_batch_id_fkey FOREIGN KEY (cash_batch_id) REFERENCES withdrawal_batches(id);
ALTER TABLE ONLY withdrawal_runs
    ADD CONSTRAINT withdrawal_runs_triggered_by_fkey FOREIGN KEY (triggered_by) REFERENCES users(id);
ALTER TABLE ONLY withdrawal_settings
    ADD CONSTRAINT withdrawal_settings_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES users(id);
ALTER TABLE ONLY withdrawals
    ADD CONSTRAINT withdrawals_bank_account_id_fkey FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id);
ALTER TABLE ONLY withdrawals
    ADD CONSTRAINT withdrawals_batch_id_fkey FOREIGN KEY (batch_id) REFERENCES withdrawal_batches(id);
ALTER TABLE ONLY withdrawals
    ADD CONSTRAINT withdrawals_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE ONLY withdrawals
    ADD CONSTRAINT withdrawals_first_approved_by_fkey FOREIGN KEY (first_approved_by) REFERENCES users(id);
ALTER TABLE ONLY withdrawals
    ADD CONSTRAINT withdrawals_rejected_by_fkey FOREIGN KEY (rejected_by) REFERENCES users(id);
ALTER TABLE ONLY withdrawals
    ADD CONSTRAINT withdrawals_second_approved_by_fkey FOREIGN KEY (second_approved_by) REFERENCES users(id);
ALTER TABLE ONLY withdrawals
    ADD CONSTRAINT withdrawals_task_assignee_id_fkey FOREIGN KEY (task_assignee_id) REFERENCES users(id);
ALTER TABLE ONLY withdrawals
    ADD CONSTRAINT withdrawals_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES support_tickets(id);
ALTER TABLE ONLY withdrawals
    ADD CONSTRAINT withdrawals_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);


--

-- -------------------- Indexes

CREATE INDEX audit_logs_action_idx ON audit_logs USING btree (action, created_at DESC);
CREATE INDEX audit_logs_actor_idx ON audit_logs USING btree (actor_user_id, created_at DESC);
CREATE INDEX audit_logs_created_idx ON audit_logs USING btree (created_at DESC);
CREATE INDEX audit_logs_entity_idx ON audit_logs USING btree (entity_type, entity_id, created_at DESC);
CREATE INDEX audit_logs_ip_idx ON audit_logs USING btree (ip_address, created_at DESC) WHERE (ip_address IS NOT NULL);
CREATE INDEX auth_sessions_user_active_idx ON auth_sessions USING btree (user_id) WHERE (revoked_at IS NULL);
CREATE INDEX bank_accounts_fingerprint_idx ON bank_accounts USING btree (account_fingerprint);
CREATE UNIQUE INDEX bank_accounts_no_duplicates_uk ON bank_accounts USING btree (user_id, account_fingerprint) WHERE ((status)::text = ANY ((ARRAY['PENDING_VERIFICATION'::character varying, 'VERIFIED'::character varying])::text[]));
CREATE UNIQUE INDEX bank_accounts_one_open_change_uk ON bank_accounts USING btree (replaces_account_id) WHERE ((status)::text = 'PENDING_VERIFICATION'::text);
CREATE UNIQUE INDEX bank_accounts_one_primary_uk ON bank_accounts USING btree (user_id) WHERE is_primary;
CREATE INDEX bank_accounts_status_idx ON bank_accounts USING btree (status, created_at);
CREATE INDEX cities_state_idx ON cities USING btree (state_id, name);
CREATE UNIQUE INDEX company_bank_accounts_number_uk ON company_bank_accounts USING btree (ifsc_code, account_number);
CREATE INDEX containers_allocation_idx ON containers USING btree (container_type, status, created_at);
CREATE INDEX containers_reserved_order_idx ON containers USING btree (reserved_order_id) WHERE (reserved_order_id IS NOT NULL);
CREATE INDEX containers_status_idx ON containers USING btree (status);
CREATE INDEX demo_members_account_idx ON demo_members USING btree (account_id);
CREATE INDEX demo_orders_account_idx ON demo_orders USING btree (account_id);
CREATE INDEX email_verification_tokens_user_idx ON email_verification_tokens USING btree (user_id);
CREATE UNIQUE INDEX holdings_active_container_uk ON holdings USING btree (container_id) WHERE ((status)::text = ANY ((ARRAY['AWAITING_ACCEPTANCE'::character varying, 'ACTIVE'::character varying])::text[]));
CREATE INDEX holdings_product_idx ON holdings USING btree (product_id);
CREATE INDEX holdings_user_idx ON holdings USING btree (user_id, confirmed_at DESC);
CREATE INDEX investment_products_listing_idx ON investment_products USING btree (status, published_at DESC);
CREATE INDEX invoices_user_idx ON invoices USING btree (user_id, issued_at DESC);
CREATE UNIQUE INDEX kyc_submissions_active_aadhaar_uk ON kyc_submissions USING btree (aadhaar_fingerprint) WHERE (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying])::text[])) AND (aadhaar_fingerprint IS NOT NULL));
CREATE UNIQUE INDEX kyc_submissions_active_pan_uk ON kyc_submissions USING btree (pan_fingerprint) WHERE (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying])::text[])) AND (pan_fingerprint IS NOT NULL));
CREATE UNIQUE INDEX kyc_submissions_one_draft_uk ON kyc_submissions USING btree (user_id) WHERE ((status)::text = 'DRAFT'::text);
CREATE UNIQUE INDEX kyc_submissions_one_pending_uk ON kyc_submissions USING btree (user_id) WHERE ((status)::text = 'PENDING'::text);
CREATE INDEX kyc_submissions_status_idx ON kyc_submissions USING btree (status, submitted_at);
CREATE INDEX kyc_submissions_user_idx ON kyc_submissions USING btree (user_id, submitted_at DESC);
CREATE INDEX lead_activities_lead_idx ON lead_activities USING btree (lead_id, created_at DESC);
CREATE INDEX leads_follow_up_idx ON leads USING btree (next_follow_up_at) WHERE ((stage)::text <> ALL (ARRAY['WON'::text, 'LOST'::text]));
CREATE UNIQUE INDEX leads_open_email_uk ON leads USING btree (email) WHERE ((email IS NOT NULL) AND ((stage)::text <> ALL (ARRAY['WON'::text, 'LOST'::text])));
CREATE INDEX leads_owner_idx ON leads USING btree (owner_id, stage);
CREATE INDEX leads_stage_idx ON leads USING btree (stage, created_at DESC);
CREATE INDEX leads_user_idx ON leads USING btree (user_id);
CREATE INDEX lease_agreements_order_idx ON lease_agreements USING btree (order_id);
CREATE INDEX lease_agreements_user_idx ON lease_agreements USING btree (user_id, generated_at DESC);
CREATE UNIQUE INDEX ledger_accounts_investor_uk ON ledger_accounts USING btree (account_type, owner_user_id, currency) WHERE (owner_user_id IS NOT NULL);
CREATE UNIQUE INDEX ledger_accounts_platform_uk ON ledger_accounts USING btree (account_type, currency) WHERE (owner_user_id IS NULL);
CREATE INDEX ledger_entries_account_idx ON ledger_entries USING btree (account_id, created_at DESC);
CREATE INDEX ledger_entries_transaction_idx ON ledger_entries USING btree (transaction_id);
CREATE INDEX ledger_transactions_created_idx ON ledger_transactions USING btree (created_at DESC);
CREATE INDEX notifications_unread_idx ON notifications USING btree (user_id) WHERE (read_at IS NULL);
CREATE INDEX notifications_user_idx ON notifications USING btree (user_id, created_at DESC);
CREATE INDEX order_items_order_idx ON order_items USING btree (order_id);
CREATE INDEX orders_pending_expiry_idx ON orders USING btree (expires_at) WHERE ((status)::text = 'PENDING_PAYMENT'::text);
CREATE INDEX orders_user_idx ON orders USING btree (user_id, created_at DESC);
CREATE INDEX outbox_events_unpublished_idx ON outbox_events USING btree (created_at) WHERE (published_at IS NULL);
CREATE INDEX password_reset_codes_user_idx ON password_reset_codes USING btree (user_id);
CREATE UNIQUE INDEX payments_one_pending_uk ON payments USING btree (order_id) WHERE ((status)::text = 'PENDING'::text);
CREATE UNIQUE INDEX payments_one_succeeded_uk ON payments USING btree (order_id) WHERE ((status)::text = 'SUCCEEDED'::text);
CREATE INDEX payments_order_idx ON payments USING btree (order_id, created_at);
CREATE INDEX payments_status_idx ON payments USING btree (status, created_at);
CREATE INDEX referral_earnings_beneficiary_idx ON referral_earnings USING btree (beneficiary_user_id, created_at DESC);
CREATE UNIQUE INDEX referral_earnings_credit_level_uk ON referral_earnings USING btree (source_credit_id, level) WHERE (source_credit_id IS NOT NULL);
CREATE INDEX referral_earnings_holding_idx ON referral_earnings USING btree (source_holding_id, level, beneficiary_user_id);
CREATE UNIQUE INDEX referral_earnings_one_time_uk ON referral_earnings USING btree (source_user_id) WHERE ((kind)::text = 'ONE_TIME'::text);
CREATE INDEX referral_moves_user_idx ON referral_moves USING btree (user_id, moved_at);
CREATE UNIQUE INDEX referral_rate_versions_effective_uk ON referral_rate_versions USING btree (effective_from) WHERE (cancelled_at IS NULL);
CREATE INDEX referrals_referrer_idx ON referrals USING btree (referrer_id, created_at);
CREATE INDEX refresh_tokens_session_idx ON refresh_tokens USING btree (session_id);
CREATE INDEX rent_accruals_running_idx ON rent_accruals USING btree (credited_until) WHERE (completed_at IS NULL);
CREATE INDEX rent_accruals_user_idx ON rent_accruals USING btree (user_id, starts_at);
CREATE INDEX rent_credits_holding_idx ON rent_credits USING btree (holding_id, from_at);
CREATE INDEX rent_credits_user_idx ON rent_credits USING btree (user_id, day DESC);
CREATE INDEX states_country_idx ON states USING btree (country_code, name);
CREATE INDEX stored_documents_owner_idx ON stored_documents USING btree (owner_user_id);
CREATE INDEX support_tickets_assignee_idx ON support_tickets USING btree (assignee_id, status);
CREATE INDEX support_tickets_queue_idx ON support_tickets USING btree (status, first_response_due_at);
CREATE INDEX support_tickets_team_idx ON support_tickets USING btree (team, last_message_at DESC);
CREATE INDEX support_tickets_user_idx ON support_tickets USING btree (user_id, last_message_at DESC);
CREATE INDEX ticket_attachments_message_idx ON ticket_attachments USING btree (message_id);
CREATE INDEX ticket_events_ticket_idx ON ticket_events USING btree (ticket_id, created_at);
CREATE INDEX ticket_messages_ticket_idx ON ticket_messages USING btree (ticket_id, created_at);
CREATE INDEX user_profiles_kyc_status_idx ON user_profiles USING btree (kyc_status);
CREATE UNIQUE INDEX user_profiles_phone_key_uk ON user_profiles USING btree (phone_key) WHERE (phone_key IS NOT NULL);
CREATE INDEX user_roles_role_idx ON user_roles USING btree (role_id);
CREATE UNIQUE INDEX users_email_uk ON users USING btree (email);
CREATE INDEX withdrawal_batch_files_batch_idx ON withdrawal_batch_files USING btree (batch_id, created_at);
CREATE UNIQUE INDEX withdrawal_batch_files_payment_uk ON withdrawal_batch_files USING btree (batch_id) WHERE ((kind)::text = 'PAYMENT'::text);
CREATE INDEX withdrawal_batches_status_idx ON withdrawal_batches USING btree (status, created_at DESC);
CREATE INDEX withdrawal_runs_run_at_idx ON withdrawal_runs USING btree (run_at DESC);
CREATE INDEX withdrawals_batch_idx ON withdrawals USING btree (batch_id);
CREATE UNIQUE INDEX withdrawals_one_open_uk ON withdrawals USING btree (user_id, currency, balance) WHERE ((status)::text = ANY ((ARRAY['PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'BATCHED'::character varying, 'PROCESSING'::character varying, 'FAILED'::character varying])::text[]));
CREATE INDEX withdrawals_status_idx ON withdrawals USING btree (status, currency, created_at);
CREATE INDEX withdrawals_task_done_idx ON withdrawals USING btree (task_done_at) WHERE (task_done_at IS NOT NULL);
CREATE INDEX withdrawals_user_idx ON withdrawals USING btree (user_id, created_at DESC);
