# SeaLease — Container Investment Platform

Investors buy whole shipping containers under investment plans. Each container is assigned to its
investor by number once paid and leased for the plan's tenure, during which the investor is paid the
plan's monthly rent plus 100 ÷ tenure % of the price back every month (the whole price by the end); uplines earn four-level referral income on the rent. Built as a **modular monolith** with financial
correctness, auditability and idempotency as first-class concerns.

| Layer    | Stack                                                                         |
|----------|-------------------------------------------------------------------------------|
| Backend  | Java 25, Spring Boot 4.1.1, Spring Security, JPA/Hibernate 7, Flyway, Maven   |
| Data     | PostgreSQL 18                                                                 |
| Events   | Apache Kafka 4.3.1 (KRaft)                                                    |
| Frontend | Next.js 16.3.8 (App Router), React 19, TypeScript (strict), Tailwind CSS 4    |
| Reports  | JasperReports 7.0.8 (Phase 11)                                                |

## Repository layout

```text
backend/    Spring Boot API (com.sealease.backend)
frontend/   Next.js app — investor portal + admin console
docker-compose.yml   Local PostgreSQL + Kafka
.env.example         Local configuration template
```

## Getting started

```bash
cp .env.example .env                 # set DB_PASSWORD (and DB_PORT if 5432 is taken)
scripts/generate-jwt-keys.sh         # RS256 key pair -> .secrets/ (git-ignored); paste paths into .env
scripts/generate-encryption-keys.sh >> .env   # field-encryption keys (tax ids, KYC files, bank accounts)
#   optionally set BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD to create the first super-admin
docker compose up -d                 # PostgreSQL + Kafka

cd backend && ./mvnw spring-boot:run # http://localhost:8080 (profile: local)

cd frontend
cp .env.example .env.local
npm install && npm run dev           # http://localhost:3000
```

Useful local endpoints:

- `GET /actuator/health/liveness`, `/actuator/health/readiness` — public probes
- `/swagger-ui.html` — API docs (local profile only)
- `GET /api/v1/auth/jwks` — public token-verification key

### Tests

```bash
cd backend && ./mvnw test            # integration tests need a running Docker daemon (Testcontainers);
                                     # without one they are skipped, not failed
cd frontend && npm run typecheck && npm run lint
```

## Backend architecture

Root package `com.sealease.backend`. Each business module (`auth`, `user`, `investment`,
`container`, `order`, `earning`, `referral`, `withdrawal`, …) is added as a top-level package
with its own `controller / service / repository / entity / dto / mapper / event` sub-packages.
Modules talk to each other through service interfaces or domain events — never through each
other's repositories or entities — so any module can later be extracted into a service.

Shared foundation (Phase 1):

| Package         | Purpose                                                                     |
|-----------------|-----------------------------------------------------------------------------|
| `common.api`    | `ApiError` (standard error body), `PageResponse`, `ApiErrorFactory`         |
| `common.exception` | `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`               |
| `common.web`    | `CorrelationIdFilter` — `X-Correlation-Id` / `X-Request-Id` in MDC + headers |
| `common.money`  | `Money` value object — `BigDecimal`, scale 4, `HALF_UP`, no mixed currencies |
| `common.persistence` | `BaseEntity` — time-ordered UUID, `@Version`, audit timestamps          |
| `security`      | Stateless, deny-by-default filter chain, JSON 401/403, CORS, method security |
| `kafka`         | Topic catalogue, `<topic>.dlt` dead-letter routing, bounded retry backoff    |
| `config`        | `Clock`, JPA auditing, OpenAPI                                               |

Modules (Phase 2):

| Module       | Responsibility                                                                    |
|--------------|-----------------------------------------------------------------------------------|
| `auth`       | Register / login / refresh / logout, RS256 JWT issuance, sessions, JWKS, bootstrap |
| `user`       | Accounts, credential checks, lockout, password policy (Argon2id)                  |
| `role`       | Roles, user↔role grants, privilege-escalation guard                               |
| `permission` | Permission catalogue (`PermissionCode` enum ⇔ `permissions` table, verified at startup) |
| `audit`      | Append-only `audit_logs`, written in the caller's transaction                     |

Modules (Phase 3):

| Module        | Responsibility                                                                   |
|---------------|----------------------------------------------------------------------------------|
| `user`        | Profile, tax info, suspension, staff directory |
| `kyc`         | Identity submissions, reviewer queue, audited document access, four-eyes approval |
| `document`    | Encrypted file storage; type detected from magic bytes (PDF/JPEG/PNG only), SHA-256 integrity check |
| `bankaccount` | Payout accounts: encrypted numbers, IBAN checksum, duplicate/fraud fingerprinting, verification |
| `common.crypto` | AES-256-GCM field/file encryption bound to a context, key rotation, HMAC fingerprints |

Modules (Phase 4):

| Module        | Responsibility                                                                   |
|---------------|----------------------------------------------------------------------------------|
| `container`   | Physical assets: ISO 6346 numbers (check digit verified), specs, location, photos & documents with per-document investor visibility; inventory reserved for orders and leased to investors |
| `investment`  | Plans (`PLAN-10001`…): container type, price per container, monthly rent %, lease tenure (capital return = 100 ÷ tenure %); DRAFT → OPEN → CLOSED lifecycle, investor eligibility |
| `marketplace` | Investor read side: plans, filters/sorting, detail with eligibility verdict, payout projection, photos of the plan's container type |

Modules (Phase 5):

| Module    | Responsibility                                                                     |
|-----------|------------------------------------------------------------------------------------|
| `cart`    | Soft basket: containers per plan; re-validated on every read, reserves nothing     |
| `order`   | Checkout (idempotent), terms acceptance, container reservation, expiry, allocation on payment |
| `payment` | Company bank accounts, bank payment with investor-submitted details, settlement, rejection, refunds |
| `invoice` | Immutable, sequentially numbered invoices with seller/buyer snapshots              |
| `investment` | + holdings (one allocated, leased container each) and the investor portfolio    |
| `outbox`  | Transactional outbox → Kafka relay; consumer de-duplication (`processed_events`)   |

Modules (Phase 6):

| Module       | Responsibility                                                                   |
|--------------|----------------------------------------------------------------------------------|
| `investment` | + holding maturity at the end of the tenure (the container returns to stock)     |
| `earning`    | Monthly payout schedule per holding (rent + capital returned), automatic crediting, investor earnings |
| `ledger`     | Append-only double-entry ledger: accounts per investor/platform and currency, balanced transactions, adjustments |

Modules (Phase 7):

| Module     | Responsibility                                                                     |
|------------|------------------------------------------------------------------------------------|
| `referral` | Referral codes, permanent referrer links made at sign-up, effective-dated rates for four levels, commissions on the rent paid to referred investors, downline views |

Modules (Phase 8):

| Module       | Responsibility                                                                   |
|--------------|----------------------------------------------------------------------------------|
| `withdrawal` | Requests that reserve the balance, single/dual approval, payout batches per currency, bank file export, reconciliation |

Modules (Phase 9):

| Module | Responsibility                                                                         |
|--------|----------------------------------------------------------------------------------------|
| `crm`  | Leads from staff and the public interest form, fixed pipeline, activity log, assignment, conversion to investors |

Modules (Phase 10):

| Module         | Responsibility                                                                 |
|----------------|--------------------------------------------------------------------------------|
| `helpdesk`     | Support tickets: conversation, internal notes, encrypted attachments, priority and first-response targets, assignment |
| `notification` | In-app notification centre (unread count, mark read) and `notification.created` events |

Dependency direction is one-way (`payment` → `order` → `cart`, `invoice`, `investment` → `container`;
`earning` → `investment`, `ledger`; `referral` → `earning` (event), `ledger`, `user`; `auth` → `referral`;
`withdrawal` → `ledger`, `bankaccount`, `user`; `crm` → `user`, `role`, `order` (event);
`helpdesk` → `notification`, `document`, `order`, `withdrawal`, `investment`, `role`;
`marketplace` → `investment`; `kyc`, `bankaccount`, `invoice` → `user` → `audit`/`common`). Where a lower
module needs something from a higher one it declares an interface (`user.AuthorityGuard`, implemented
by `role`) or publishes an event (`UserStatusChangedEvent`, consumed by `auth` to end sessions).

### Conventions

- **Money**: `Money`/`BigDecimal` only, stored as `NUMERIC(19,4)` + ISO-4217 `CHAR(3)`.
  Rounded to the currency's minor unit only at payout/presentation boundaries.
- **One currency: Indian rupees.** `app.money.default-currency` (`INR`) is the only currency accepted
  (`@PlatformCurrency` on every request that carries one); the web app does not ask for it. Amounts are shown
  with the symbol and Indian grouping, e.g. `₹1,23,456.50` (`Money.display()`, `formatMoney`), in messages,
  reports, CSV and PDFs alike.
- **Time**: inject `Clock`; store `TIMESTAMPTZ` in UTC.
- **Immutability**: ledger/audit tables attach the `forbid_mutation()` trigger (V1 migration);
  corrections are compensating entries, never updates.
- **Schema**: Flyway owns the schema (`ddl-auto: validate`). Migrations are never edited once merged.
- **Errors**: throw `BusinessException(ErrorCode, message)`; messages must be safe for clients.
- **Authorization**: permission-based — `@PreAuthorize("hasAuthority('WITHDRAWAL_APPROVE')")`.
- **Config**: secrets and environment values via environment variables; business-tunable values
  (referral rates, limits) will live in the database with effective-dated versions.

### Security model

```text
Browser ──cookies──▶ Next.js (BFF) ──Bearer JWT──▶ Spring Boot API ──▶ PostgreSQL
          HttpOnly    proxy.ts: CSP nonce,          verifies RS256 signature, iss, aud,
          SameSite    silent refresh, UX guards     exp, jti and that the session is live
```

- **Tokens.** Access tokens are RS256 JWTs (15 min) carrying only `sub`, `user_id`, `sid`,
  `roles`, `permissions`, `iat`, `exp`, `jti`, `iss`, `aud` — no PII. Refresh tokens are opaque,
  256-bit, single-use and stored as SHA-256 hashes; every refresh rotates them. Replaying a used
  refresh token outside a 10 s grace window revokes the whole session (theft detection).
- **Revocation.** Every login is an `auth_sessions` row whose id is the `sid` claim. The API
  rejects tokens of revoked sessions immediately, so logout, logout-all, password change and role
  changes take effect at once rather than when the access token expires.
- **Credentials.** Argon2id (19 MiB, t=2) behind a `DelegatingPasswordEncoder` for transparent
  upgrades; NIST-style length policy; lockout after 5 consecutive failures for 15 min; per-IP and
  per-email rate limits; unknown emails and wrong passwords are indistinguishable (message and timing).
- **Authorization.** Permission-based (`@PreAuthorize("hasAuthority('ROLE_MANAGE')")`); role names
  never become authorities. Admins may only grant/revoke roles whose permissions they hold
  themselves and can never change their own roles.
- **Investors or staff, never both.** Only the `INVESTOR` role grants `INVESTOR_PORTAL` (not even
  `SUPER_ADMIN` holds it). A user's roles, and a role's permissions, cannot combine it with staff
  permissions. Staff see only their profile and the admin area; investor pages redirect them to `/admin`.
- **Browser.** Tokens live only in `HttpOnly`, `SameSite=Lax` cookies (`__Host-` prefixed and
  `Secure` in production). The API is cookie-free and therefore CSRF-immune; Next.js Server Actions
  reject cross-origin posts. Every page gets a nonce-based Content-Security-Policy.
- **Sensitive data.** Tax ids, identity-document numbers, bank account/routing numbers and KYC
  files are AES-256-GCM encrypted at rest, each bound to its column (and, for files, to the owner)
  so ciphertext cannot be moved between records. APIs return only the last four characters.
  Bank and KYC data sit behind their own permissions (`BANK_ACCOUNT_VERIFY`/`FINANCE_VIEW`,
  `KYC_REVIEW`), not general `USER_VIEW`. Every KYC document view is audited.
- **Separation of duties.** Nobody may review their own KYC or bank account, change their own
  roles, suspend themselves, or act on an account holding permissions they lack.
- **Frontend guards are UX only** — the backend authorizes every call.

**Deployment requirements:** run the API on a private network reachable only by Next.js; put an
ingress in front of Next.js that *appends* the client IP to `X-Forwarded-For` (the API trusts
that header only from internal proxies and takes the right-most untrusted address); supply JWT keys
from a secret manager; keep `COOKIE_SECURE` on.

### Auth API

| Method & path                         | Access            | Purpose                                   |
|---------------------------------------|-------------------|-------------------------------------------|
| `POST /api/v1/auth/register`          | public            | Create investor account, returns tokens   |
| `POST /api/v1/auth/login`             | public            | Returns access + refresh tokens           |
| `POST /api/v1/auth/refresh`           | public            | Rotate refresh token, new access token    |
| `POST /api/v1/auth/logout`            | public (token)    | Revoke the session owning a refresh token |
| `POST /api/v1/auth/logout-all`        | authenticated     | Revoke every session of the caller        |
| `POST /api/v1/auth/password`          | authenticated     | Change password, revoke other sessions    |
| `GET  /api/v1/auth/me`                | authenticated     | Identity, roles, permissions              |
| `GET  /api/v1/auth/jwks`              | public            | Public verification key (JWK Set)         |
| `GET  /api/v1/admin/permissions`      | `ROLE_VIEW`       | Permission catalogue                      |
| `GET/POST/PUT/DELETE /api/v1/admin/roles[/{id}]` | `ROLE_VIEW` / `ROLE_MANAGE` | Role management    |
| `GET/PUT /api/v1/admin/users/{id}/roles` | `ROLE_VIEW` / `USER_ROLE_ASSIGN` | Replace a user's roles |

### Users, KYC & bank accounts API

| Method & path                                       | Access                  | Purpose                              |
|-----------------------------------------------------|-------------------------|--------------------------------------|
| `GET/PUT /api/v1/users/me`                          | authenticated           | Own profile (tax id masked)          |
| `PUT /api/v1/users/me/tax`                          | authenticated           | Tax residency + tax id (encrypted)   |
| `GET/POST /api/v1/users/me/kyc`                     | authenticated           | Latest KYC / submit (multipart)      |
| `GET/POST /api/v1/users/me/bank-accounts`           | `INVESTOR_PORTAL`       | List / add payout account            |
| `DELETE /…/bank-accounts/{id}`, `PUT /…/{id}/primary` | `INVESTOR_PORTAL`     | Remove / choose primary              |
| `GET /api/v1/admin/users[/{id}]`                    | `USER_VIEW`             | Directory search, detail             |
| `POST /api/v1/admin/users/{id}/suspend` · `/reactivate` | `USER_SUSPEND`      | Ends sessions immediately; reason required |
| `GET /api/v1/admin/kyc[/{id}]`, `/admin/users/{id}/kyc` | `KYC_REVIEW`        | Review queue, detail, history        |
| `GET /api/v1/admin/kyc/{id}/documents/{docId}`      | `KYC_REVIEW`            | Decrypted evidence (audited)         |
| `POST /api/v1/admin/kyc/{id}/approve` · `/reject`   | `KYC_REVIEW`            | Decision (not on own submission)     |
| `GET /api/v1/admin/bank-accounts`                   | `BANK_ACCOUNT_VERIFY`   | Verification queue                   |
| `GET /api/v1/admin/users/{id}/bank-accounts`        | `BANK_ACCOUNT_VERIFY` or `FINANCE_VIEW` | A user's accounts + shared-account flag |
| `POST /api/v1/admin/bank-accounts/{id}/verify` · `/reject` | `BANK_ACCOUNT_VERIFY` | Decision (owner KYC must be approved) |

### Marketplace rules

- **Plans.** A plan names a container type, a price per container, a monthly rent % (above 0, at
  most 20) and a lease tenure in months (1–120). The monthly capital return is not set by hand: it is
  100 ÷ tenure % of the price (16 months → 6.25%, 12 months → 8.3333%), so the whole price comes back
  over the lease. What the investor is paid every month is that capital return % plus the rent %. Every investor buys whole containers; there are
  no shared or fractional holdings, and investors are not classified.
- **Lifecycle.** `DRAFT → OPEN → CLOSED`, or `CANCELLED` while a draft. Terms are editable only in
  `DRAFT`. Publishing and closing need `INVESTMENT_APPROVE`; closing stops new sales while sold
  containers keep their lease and payouts.
- **Stock.** A plan sells the `AVAILABLE` containers of its type (shared by plans of that type).
  Containers move `AVAILABLE → RESERVED` (checkout) `→ ON_LEASE` (payment confirmed) `→ AVAILABLE`
  (lease ended), or back to `AVAILABLE` when an order lapses. Those two statuses are never set by
  hand. Checkout locks the oldest available containers with `FOR UPDATE SKIP LOCKED`, so concurrent
  buyers never block each other or get the same container, and a DB index allows one active holding
  per container.
- **Eligibility.** Account active and KYC approved, and the plan open. The marketplace shows the verdict and its reasons.
- **Projection.** For *n* containers: price × n; monthly rent = price × rent %, capital back =
  price ÷ tenure (each rounded half-up to the paisa) × n; over the tenure, all the rent plus the whole price.

### Containers, plans & marketplace API

| Method & path                                              | Access                | Purpose                                 |
|------------------------------------------------------------|-----------------------|-----------------------------------------|
| `GET /api/v1/admin/containers[/{id}]`                      | `INVESTMENT_VIEW`     | Search (number, status, type), detail   |
| `POST /api/v1/admin/containers`, `PUT /…/{id}`             | `INVESTMENT_CREATE` / `INVESTMENT_UPDATE` | Register / edit          |
| `POST /api/v1/admin/containers/{id}/status`                | `INVESTMENT_UPDATE`   | Status change with reason (not to or from `RESERVED` / `ON_LEASE`) |
| `POST /…/containers/{id}/documents` (multipart), `PATCH /…/documents/{docId}` | `INVESTMENT_UPDATE` | Upload photo/survey/lease/insurance/memorandum; toggle investor visibility |
| `GET /…/containers/{id}/documents/{docId}`                 | `INVESTMENT_VIEW`     | Document content                        |
| `GET /api/v1/admin/investment-products[/{id}]`             | `INVESTMENT_VIEW`     | Search, detail                          |
| `POST /api/v1/admin/investment-products`, `PUT /…/{id}`    | `INVESTMENT_CREATE` / `INVESTMENT_UPDATE` | Create / edit draft      |
| `POST /…/investment-products/{id}/publish` · `/close` · `/cancel` | `INVESTMENT_APPROVE` | Open to investors / stop sales / cancel a draft |
| `GET /api/v1/marketplace`                                  | `INVESTOR_PORTAL` or `INVESTMENT_VIEW` | Plans: container type, risk, status; sort `NEWEST`, `HIGHEST_RETURN`, `LOWEST_PRICE` |
| `GET /api/v1/marketplace/{id}`                             | 〃                    | Detail, containers in stock, eligibility verdict |
| `GET /api/v1/marketplace/{id}/projection?containers=`      | 〃                    | Validated payout projection             |
| `GET /api/v1/marketplace/{id}/documents/{docId}`           | 〃                    | Investor-visible photos of the plan's container type |

Seed company bank accounts, container stock and plans into a **local** API (runs as the bootstrap admin, safe to re-run):

```bash
python3 scripts/seed-demo-data.py
```

### Orders, payments & the outbox

```text
cart ──checkout──▶ order PENDING_PAYMENT ──payment succeeded──▶ CONFIRMED
 (nothing held)     containers RESERVED        (one transaction)   containers ON_LEASE to the investor,
                    pay within 30 min                              holdings + payout schedules, invoice, events
                          └──expired / cancelled──▶ containers back to AVAILABLE, pending payments cancelled
```

- **Checkout** needs an `Idempotency-Key` header: a retry returns the original order, and reusing
  the key for a different request is a 409. The investor accepts the terms of each plan in
  the cart, or checkout fails. The cart is locked, so concurrent checkouts of one cart place one
  order. There is one `order_items` row per container, with the plan terms frozen (price, rent %,
  capital return %, tenure) and the container reserved for it. Investors see each
  container's number once the order is paid; staff see the reserved containers.
- **Payments.** Bank payment is the only method; card payments are not accepted (earlier card
  payments, if any, remain readable as `CARD`). One attempt in flight per order, and money is taken
  at most once (partial unique indexes). Finance maintains the company
  bank accounts investors may pay into (account number, IFSC, optional UPI ID;
  `COMPANY_BANK_ACCOUNT_MANAGE`, deactivated rather than deleted). The investor chooses one of the
  active accounts, quotes a generated reference, pays online (NEFT/RTGS/IMPS/UPI), by cheque or by
  cash deposit, and submits the transaction ID, cheque number or deposit receipt number. From then
  on the order is held for verification (`APP_BANK_PAYMENT_VERIFICATION_WINDOW`, default 7 days)
  instead of lapsing after 30 minutes, and the investor may correct the details until finance
  decides. Finance confirms receipt (`PAYMENT_CONFIRM`; the amount
  must match exactly) or rejects the payment with a reason, after which the investor can pay again
  while the order is open. Nobody may decide on a payment for their own order.
- **Money is never silently kept.** A payment arriving for an expired, cancelled or already paid
  order becomes `REFUND_REQUIRED`, and finance records the refund (`FINANCE_ADJUST`).
- **Locking.** Order row, then payment row, then container rows. Payment settlement,
  cancellation and the expiry sweep therefore serialise without deadlocks.
- **Outbox.** Events are inserted in the business transaction. A relay (every 1 s, `FOR UPDATE
  SKIP LOCKED`, safe on several instances) publishes them with `eventId`/`eventType` headers, keyed
  by aggregate. Delivery is at-least-once: consumers call `ProcessedEvents.markProcessed` in their
  transaction. Topics: `investment.created` (order placed), `payment.success`, `payment.failed`,
  `investment.confirmed`, `invoice.generated`.
- **Invoices** (`INV-2026-000042`) are append-only; corrections will be credit notes. The portal
  renders them for print, and offers a JasperReports PDF (see *Reporting*).

### Cart, orders & payments API

| Method & path                                         | Access              | Purpose                                     |
|-------------------------------------------------------|---------------------|---------------------------------------------|
| `GET /api/v1/cart`, `PUT·DELETE /api/v1/cart/items/{productId}` | `INVESTOR_PORTAL` | View (with per-line problems) / set / remove |
| `POST /api/v1/orders` (`Idempotency-Key`)             | `INVESTOR_PORTAL`   | Check out the cart with accepted terms      |
| `GET /api/v1/orders[/{id}]`, `POST /…/{id}/cancel`    | `INVESTOR_PORTAL`   | Own orders; cancel while awaiting payment   |
| `GET·POST /api/v1/orders/{id}/payments` (`Idempotency-Key`) | `INVESTOR_PORTAL` | Payment attempts / start a bank payment (`BANK_TRANSFER`) |
| `POST /api/v1/payments/{id}/deposit`                  | `INVESTOR_PORTAL`   | Bank payment details: company account, mode (`ONLINE`, `CHEQUE`, `CASH_DEPOSIT`), reference |
| `GET /api/v1/orders/{id}/invoice`                     | `INVESTOR_PORTAL`   | Invoice of a confirmed order                |
| `GET /api/v1/portfolio`                               | `INVESTOR_PORTAL`   | Holdings and totals per currency            |
| `GET /api/v1/admin/orders[/{id}[/holdings·/invoice]]` | `ORDER_VIEW`        | Search, detail, holdings, invoice           |
| `GET /api/v1/admin/payments`, `/admin/orders/{id}/payments` | `FINANCE_VIEW` (or `ORDER_VIEW`) | Payment queue / per order |
| `POST /api/v1/admin/payments/{id}/confirm`            | `PAYMENT_CONFIRM`   | Record a received bank payment              |
| `POST /api/v1/admin/payments/{id}/reject`             | `PAYMENT_CONFIRM`   | Reject a bank payment that never arrived (reason) |
| `GET·POST /api/v1/admin/company-bank-accounts`, `PUT /…/{id}`, `POST /…/{id}/activate·deactivate` | `COMPANY_BANK_ACCOUNT_MANAGE` (list: or `FINANCE_VIEW`) | Company bank accounts investors pay into |
| `POST /api/v1/admin/payments/{id}/refund`             | `FINANCE_ADJUST`    | Record a refund                             |

### Payouts & the ledger

```text
payment confirmed ──▶ holding per container, lease from today for the plan's tenure (T months)
                      T installments: n due on start + n months (in arrears), each rent + capital
installment due ──payout job (hourly) or POST /admin/payouts/run──▶ PAID: investor wallet credited (one ledger transaction)
last installment paid ──▶ holding MATURED, container back to AVAILABLE
```

- **Amounts.** Per container per month: rent = price × the plan's rent %, capital = price ÷ tenure, each
  rounded half-up to the paisa and fixed when the schedule is created. The last installment's capital takes
  up the rounding, so the capital returned adds up to exactly the price.
- **Paying.** A scheduled job (`app.payouts.sweep-interval`, hourly) pays every due installment, each in
  its own transaction under a row lock, so runs can overlap and repeat safely. Finance can run it at once
  (`PAYOUT_PROCESS`). Paid installments stay `PAID`; nothing is edited afterwards.
- **Ledger.** Every payout, commission and adjustment is one transaction of debit/credit entries. A deferred
  constraint trigger rejects any transaction that does not balance or mixes currencies, and accounts,
  transactions and entries are append-only. A payout debits *Rent paid to investors* and *Capital returned to
  investors* and credits the investor's *Investor earnings* account (their wallet), which withdrawals draw on.
- **Adjustments.** `FINANCE_ADJUST` credits or debits an investor's balance against *Adjustments*, with a reason and an
  `Idempotency-Key`. A debit cannot overdraw the balance, and nobody may adjust their own.
- **Events.** `earning.created` per payout, via the outbox.

### Payouts, earnings & ledger API

| Method & path                                         | Access              | Purpose                                     |
|-------------------------------------------------------|---------------------|---------------------------------------------|
| `GET /api/v1/admin/payouts?status&userId&holdingId&dueBy`, `/payouts/due` | `FINANCE_VIEW` or `PAYOUT_PROCESS` | Payout schedule; due and unpaid totals |
| `POST /api/v1/admin/payouts/run`                      | `PAYOUT_PROCESS`    | Pay everything due now                      |
| `GET /api/v1/admin/ledger/accounts[/{id}[/entries]]`, `/trial-balance` | `FINANCE_VIEW` | Balances, statements, trial balance |
| `POST /api/v1/admin/ledger/adjustments` (`Idempotency-Key`) | `FINANCE_ADJUST` | Correct an investor's balance        |
| `GET /api/v1/earnings?status`, `/earnings/summary`    | `INVESTOR_PORTAL`   | Own payouts (paid or scheduled), wallet, rent and capital received, next payout |

### Referrals

- **Linking.** Every investor has an 8-character code (no 0/O/1/I/L), created the first time they open their
  referral page and shared as `/register?ref=CODE`. A code entered at sign-up permanently records the
  referrer, in the registration transaction. An unknown code, or the code of an inactive account, fails registration.
  Links are append-only and always point to an existing account, so the hierarchy has no cycles.
- **Rates.** Levels 1–4 each take 0–10%, and at most 20% together. The seeded rates are 2 / 1 / 0.5 / 0.25%. A change is a
  new version starting now or later (`REFERRAL_CONFIG_MANAGE`). Versions in force are never edited, and a scheduled
  version can be cancelled before it starts. Each commission records the version and rate that produced it.
- **Commissions.** When a monthly payout is credited, the earnings module publishes `PayoutPaidEvent` in-process,
  and commissions are paid in the same database transaction. The *rent* part of the referred investor's payout (not
  the capital returned) earns their uplines, nearest first, up to four levels: rent × rate, rounded down to the minor
  unit. The platform pays (*Referral commissions* expense → each upline's *Investor earnings* account, one
  `REFERRAL_COMMISSION` ledger transaction per payout). The referred investor's own payout is unchanged. An upline whose account is not
  active, or whose identity is not verified, forfeits that level; it is not passed further up.
- **Privacy.** Investors see their downline by first name and last initial, with opaque node ids. Staff
  see full names and account links.
- **Events.** `referral.earning.created` per commission, via the outbox.

### Referrals API

| Method & path                                         | Access                   | Purpose                                     |
|-------------------------------------------------------|--------------------------|---------------------------------------------|
| `POST /api/v1/auth/register` (`referralCode`)         | public                   | Sign up with an optional referral code      |
| `GET /api/v1/referrals/me`, `/downline`, `/earnings`  | `INVESTOR_PORTAL`        | Code, levels & rates, network tree, commission history |
| `GET /api/v1/admin/referral-rates`                    | `REFERRAL_CONFIG_MANAGE` or `FINANCE_VIEW` | Rate versions (in force, scheduled, …) |
| `POST /api/v1/admin/referral-rates`, `/{id}/cancel`   | `REFERRAL_CONFIG_MANAGE` | Schedule new rates / cancel scheduled ones  |
| `GET /api/v1/admin/referral-earnings`                 | `FINANCE_VIEW`           | Commissions paid, by beneficiary or source  |
| `GET /api/v1/admin/users/{id}/referrals`              | `USER_VIEW`              | Upline chain, downline size per level, totals |

### Withdrawals

```text
request ──▶ PENDING_APPROVAL ──approve (×2 above threshold)──▶ APPROVED ──batch──▶ BATCHED ──file sent──▶ PROCESSING ──▶ PAID
 balance → in transit     │ cancel (investor) / reject                 │ reject      (batch cancel → APPROVED)    └──▶ FAILED
                          └──────────── money returns to the balance ──┘                                              (money returns)
```

- **Request.** The investor chooses one of their *verified* bank accounts, and the withdrawal is in its currency.
  The account must be active and KYC-approved. The amount must be at least `app.withdrawals.minimum-amount` (default ₹500) and within the
  balance, and only one withdrawal per currency can be open (partial unique index). The request needs an `Idempotency-Key`.
  Under the lock on the investor's ledger account (the same lock adjustments take), the amount moves from
  *Investor earnings* to *Withdrawals in transit*, so the same balance can never be withdrawn twice.
- **Approval.** `WITHDRAWAL_APPROVE` approves. Above `dual-approval-threshold` (default ₹1,00,000) a second, different
  approver is required (enforced by the database too), and nobody decides on their own withdrawal. The final approval re-checks
  the investor and their bank account. `WITHDRAWAL_REJECT` rejects before batching, and the investor can cancel while it awaits
  approval. Both return the money (`WITHDRAWAL_RELEASE`).
- **Payout.** `WITHDRAWAL_PROCESS` batches the oldest approved withdrawals of a currency (up to `max-batch-size`)
  and downloads the bank file. The CSV carries full account numbers, so every download is audited, and fields are quoted with
  formula characters neutralised. The file is refused if a payee's account is no longer verified. Before it is sent, a batch can be cancelled;
  once marked sent, its items are *processing*.
- **Reconciliation.** Each item is marked paid (in transit → cash, `WITHDRAWAL_PAYOUT`) or failed (money returned), or
  "mark remaining paid" settles the rest. A batch closes when nothing is outstanding, and *Withdrawals in transit*
  then nets to zero for it.
- **Events.** `withdrawal.requested`, `.approved`, `.batched` and `.processing` (per batch), `.completed`, and `.failed`
  (also used for rejections and cancellations, with event types `WithdrawalRejected` and `WithdrawalCancelled`).

### Withdrawals API

| Method & path                                                  | Access               | Purpose                                     |
|----------------------------------------------------------------|----------------------|---------------------------------------------|
| `GET /api/v1/withdrawals/policy`, `GET /api/v1/withdrawals`    | `INVESTOR_PORTAL`    | Limits; own withdrawals                     |
| `POST /api/v1/withdrawals` (`Idempotency-Key`), `/{id}/cancel` | `INVESTOR_PORTAL`    | Request; cancel while awaiting approval     |
| `GET /api/v1/admin/withdrawals[/{id}]`                         | `WITHDRAWAL_VIEW`    | Queue by status/currency/user, detail       |
| `POST /api/v1/admin/withdrawals/{id}/approve` · `/reject`      | `WITHDRAWAL_APPROVE` / `WITHDRAWAL_REJECT` | Decide                 |
| `GET /api/v1/admin/withdrawal-batches[/{id}]`                  | `WITHDRAWAL_VIEW`    | Batches with paid/failed/outstanding counts |
| `POST /api/v1/admin/withdrawal-batches`, `/{id}/cancel`, `/{id}/sent` | `WITHDRAWAL_PROCESS` | Create, cancel, mark sent            |
| `GET /api/v1/admin/withdrawal-batches/{id}/file`               | `WITHDRAWAL_PROCESS` | Bank payment file (CSV, audited)            |
| `POST /…/withdrawal-batches/{id}/items/{wid}/paid` · `/failed`, `/{id}/settle` | `WITHDRAWAL_PROCESS` | Reconcile             |

### Sales CRM

- **Leads.** Staff enter leads (`LEAD_CREATE`), with an email or a phone number. The landing page's *Talk to us* form
  posts through a Next.js server action to `POST /api/v1/public/leads`. That endpoint is unauthenticated and requires consent. It is
  rate-limited per client IP (`app.crm.public-form-per-ip`, default 5/hour) and protected by a honeypot field, and it always answers
  `202`, so it reveals nothing. There is one open lead per email (partial unique index). A repeat enquiry is logged on the existing lead.
- **Pipeline.** `NEW → CONTACTED → QUALIFIED → PROPOSAL → WON / LOST`. Lost needs a reason and can be reopened; won is
  final. Every stage change, assignment, note, call, email and meeting goes to an append-only activity log. Reps set
  follow-up dates, and *Follow-up due* lists the overdue ones.
- **Visibility.** Holders of `LEAD_ASSIGN` (managers) see and change everything, and assign leads to anyone holding
  `LEAD_UPDATE`. Other `LEAD_VIEW` holders see their own and unassigned leads, change only their own, and claim
  unassigned ones. Leads outside a viewer's scope answer 404.
- **Conversion.** A lead is linked to an account when its email registers (`UserRegisteredEvent`) or when
  it is created for an existing account. That account's first confirmed investment (`OrderConfirmedEvent`) marks the lead
  WON with the amount. Both listeners run *after* commit, in their own transaction, and log rather than throw, so the
  CRM can never fail a sign-up or a payment.
- **Events.** `lead.created` and `lead.updated` (stage, assignment, registration, won). Payloads carry ids and stages,
  never contact details.

### Sales CRM API

| Method & path                                         | Access         | Purpose                                        |
|-------------------------------------------------------|----------------|------------------------------------------------|
| `POST /api/v1/public/leads`                           | public         | Website interest form (consent, honeypot, rate-limited) |
| `GET /api/v1/admin/leads` (`q`, `stage`, `source`, `owner=me·unassigned·{id}`, `due`) | `LEAD_VIEW` | Leads in the viewer's scope |
| `GET /api/v1/admin/leads/pipeline`, `/{id}`           | `LEAD_VIEW`    | Counts and value per stage; detail with activity |
| `POST /api/v1/admin/leads`                            | `LEAD_CREATE`  | Create (owned by the creator, or assigned by a manager) |
| `PUT /…/leads/{id}`, `POST /…/{id}/stage`, `/activities`, `/claim` | `LEAD_UPDATE` | Edit, move, log, claim        |
| `GET /…/leads/assignees`, `POST /…/{id}/assign`       | `LEAD_ASSIGN`  | Staff who can work leads; (re)assign           |

### Support

- **Tickets.** Investors open requests with a subject, a category and, optionally, a link to one of their *own* orders,
  withdrawals or holdings (checked, and shown to agents as a link). Status: `OPEN ⇄ WAITING_ON_CUSTOMER → RESOLVED →
  CLOSED`. A staff reply hands the ticket to the customer, and a customer reply puts it back in the queue, reopening a
  resolved ticket. Closed is final.
- **Agents.** `SUPPORT_TICKET_VIEW` reads the queue. `SUPPORT_TICKET_MANAGE` replies, adds **internal notes** (never
  shown to the customer, nor their attachments), sets priority, assigns to another agent, and resolves or closes. Nobody
  answers their own ticket.
- **Response targets.** Each priority has a first-response target (`app.support.response-targets`, defaults: low 48h, normal 24h,
  high 8h, urgent 2h). A ticket with no staff reply past its target is *overdue* and gets its own queue. Changing the
  priority moves the target.
- **Attachments.** Up to 3 per message, stored encrypted in the document store, which checks the real file type
  (PDF/JPEG/PNG, 5 MB). Filenames are stripped of paths, and staff downloads are audited.
- **Notifications.** Replies, status changes, assignments and customer replies (to the assignee) create in-app
  notifications, in the same transaction, and publish `notification.created`, ready for an email/SMS sender that
  honours profile preferences. The header bell shows the unread count.

### Support API

| Method & path                                                    | Access                  | Purpose                              |
|------------------------------------------------------------------|-------------------------|--------------------------------------|
| `GET·POST /api/v1/support/tickets` (multipart), `GET /{id}`      | `INVESTOR_PORTAL`       | Own tickets; open with files         |
| `POST /…/tickets/{id}/messages` (multipart), `/{id}/close`, `GET /{id}/attachments/{aid}` | `INVESTOR_PORTAL` | Reply, close, download |
| `GET /api/v1/admin/support/tickets` (`status`, `priority`, `assignee`, `overdue`, `q`), `/{id}`, `/{id}/attachments/{aid}` | `SUPPORT_TICKET_VIEW` | Queue, detail, audited downloads |
| `POST /…/tickets/{id}/messages` (multipart, `internal`), `/status`, `/priority`, `/assign`; `GET /…/agents` | `SUPPORT_TICKET_MANAGE` | Handle tickets |
| `GET /api/v1/notifications`, `/unread-count`; `POST /{id}/read`, `/read-all` | signed in       | Own notifications                    |

### Staff accounts

- **Creating staff.** Holders of `USER_ROLE_ASSIGN` create staff accounts with name, work email and roles
  (`POST /api/v1/admin/staff`). Account and roles are created in one transaction, under the same escalation guard as
  role changes (you can only grant permissions you hold). Investor roles are refused, because investors register themselves.
- **Temporary password.** The response carries a random temporary password (`XXXX-XXXX-XXXX-XXXX`, 80 bits) exactly once,
  with `Cache-Control: no-store`. Only its hash is stored. It must be replaced at first sign-in, before it expires
  (`app.users.staff.temporary-password-validity`, default 72h). After expiry, sign-in fails with
  `TEMPORARY_PASSWORD_EXPIRED`.
- **Enforcement.** Tokens issued for such a sign-in carry `pwd_change`. A security filter then answers
  `403 PASSWORD_CHANGE_REQUIRED` to every API call except `POST /auth/password`, `GET /auth/me`, refresh and sign-out.
  The web app redirects to `/change-password`. Changing the password clears the flag, and the next token no longer carries it.
- **Resets.** `POST /api/v1/admin/users/{id}/temporary-password` (`USER_ROLE_ASSIGN`, staff accounts only, never your
  own, and only if you hold every permission the target holds) issues a new temporary password and ends all of the
  person's sessions. Both actions are audited (`STAFF_ACCOUNT_CREATED`, `TEMPORARY_PASSWORD_ISSUED`).
- **UI.** *Users → New staff member*. The user page shows roles, lets `USER_ROLE_ASSIGN` holders edit them, and offers
  *Issue temporary password* for staff. Everyone can change their password from *Profile → Password*.

### Reporting

- **One document model, three formats.** Every report is built once as a `ReportDocument` (title, subtitle, sections of
  four-column rows) and rendered as JSON (on-screen preview), CSV or PDF, so all formats always show the same figures.
  PDFs come from a single JasperReports template (`reports/document.jrxml`, compiled once) with fonts embedded.
- **Reports.** *Investor statement*: per currency, opening and closing earnings balance and every entry in the period,
  plus holdings and withdrawals requested. *Financial summary*: per currency, rent paid and capital returned to investors,
  referral commissions, adjustments, withdrawals requested, returned and paid,
  and what is owed / in transit at period end — straight from the ledger. *Plans & payouts*: containers sold and in stock per
  published plan, and payouts due but not yet paid. *Invoices* as PDF.
- **Periods** are inclusive dates in UTC, at most 366 days.
- **Access.** Investors get their own statement and invoices. Staff previews need `REPORT_VIEW`; PDF/CSV downloads need
  `REPORT_GENERATE` and are audited (`REPORT_GENERATED`, with report, format and parameters). Staff invoice PDFs need
  `ORDER_VIEW`. Downloads are sent `Cache-Control: no-store`.
- **CSV safety.** Every field is quoted, and values starting with `= + - @` are prefixed with `'` (CSV injection),
  except plain amounts. Payout bank files use the same helper.
- **Admin overview.** `/admin` shows key figures (investors, capital invested, paid to investors this month, owed to investors,
  withdrawals in progress, support waiting, open leads). Each tile is computed only for viewers holding the permission
  that guards its data.

### Reporting API

| Method & path                                                                 | Access                             | Purpose                             |
|-------------------------------------------------------------------------------|------------------------------------|-------------------------------------|
| `GET /api/v1/reports/statement?from&to&format`                                | `INVESTOR_PORTAL`                  | Own statement (json, csv, pdf)      |
| `GET /api/v1/reports/invoices/{orderId}?format`                               | `INVESTOR_PORTAL`                  | Own invoice (pdf by default)        |
| `GET /api/v1/admin/reports/statement?userId&from&to&format`                   | `REPORT_VIEW` / `REPORT_GENERATE`  | Any investor's statement            |
| `GET /api/v1/admin/reports/financial-summary?from&to&format`                  | `REPORT_VIEW` / `REPORT_GENERATE`  | Ledger summary for a period         |
| `GET /api/v1/admin/reports/offerings?format`                                  | `REPORT_VIEW` / `REPORT_GENERATE`  | Plans, stock, payouts outstanding   |
| `GET /api/v1/admin/reports/invoices/{orderId}?format`                         | `ORDER_VIEW`                       | Any invoice as PDF                  |
| `GET /api/v1/admin/dashboard`                                                 | signed in                          | Overview tiles the viewer may see   |

`format` is `json` (default for reports), `csv` or `pdf`; csv and pdf need `REPORT_GENERATE` on staff reports.

## Delivery phases

1. ✅ Project setup & base architecture
2. ✅ Authentication — registration, login, RS256 JWT, refresh-token rotation, roles & permissions
3. ✅ Users — profile, KYC, bank details
4. ✅ Marketplace — containers, investment plans, container stock
5. ✅ Cart, orders, payments, investment confirmation, invoices (+ transactional outbox)
6. ✅ Earnings — leases per container, monthly payouts (rent + capital back over the tenure), double-entry ledger
7. ✅ Referrals — four-level hierarchy, effective-dated rates, commissions on rent paid, downline tree
8. ✅ Withdrawals — reserved balances, single/dual approval, payout batches, bank file, reconciliation
9. ✅ Sales CRM — leads from staff and the website, pipeline, activity log, assignment, conversion to investors
10. ✅ Support — tickets, internal notes, attachments, response targets, in-app notifications
11. ✅ Reporting — statements, financial summary, plans & payouts, invoice PDFs (JasperReports), CSV exports, admin overview
12. Production hardening
