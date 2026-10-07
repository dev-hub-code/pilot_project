# SeaLease — Container Investment Platform

Investors fund shipping-container rental assets, jointly (retail) or exclusively (HNI), and earn
rental income plus four-level referral income. Built as a **modular monolith** with financial
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
| `user`        | Profile, tax info, investor classification (append-only history), suspension, staff directory |
| `kyc`         | Identity submissions, reviewer queue, audited document access, four-eyes approval |
| `document`    | Encrypted file storage; type detected from magic bytes (PDF/JPEG/PNG only), SHA-256 integrity check |
| `bankaccount` | Payout accounts: encrypted numbers, IBAN checksum, duplicate/fraud fingerprinting, verification |
| `common.crypto` | AES-256-GCM field/file encryption bound to a context, key rotation, HMAC fingerprints |

Modules (Phase 4):

| Module        | Responsibility                                                                   |
|---------------|----------------------------------------------------------------------------------|
| `container`   | Physical assets: ISO 6346 numbers (check digit verified), specs, location, lifecycle, photos & documents with per-document investor visibility |
| `investment`  | Offerings (`CONT-10001`…): terms, DRAFT → OPEN → FUNDED lifecycle, investor eligibility, amount policy, capacity accounting |
| `marketplace` | Investor read side: listings, filters/sorting, detail with eligibility verdict, return projection, investor-visible documents |

Modules (Phase 5):

| Module    | Responsibility                                                                     |
|-----------|------------------------------------------------------------------------------------|
| `cart`    | Soft basket, one line per offering; re-validated on every read, holds no capacity  |
| `order`   | Checkout (idempotent), terms acceptance, capacity reservation, expiry, confirmation |
| `payment` | Provider abstraction (bank transfer, card via signed webhooks), settlement, refunds |
| `invoice` | Immutable, sequentially numbered invoices with seller/buyer snapshots              |
| `investment` | + holdings (confirmed investments) and the investor portfolio                   |
| `outbox`  | Transactional outbox → Kafka relay; consumer de-duplication (`processed_events`)   |

Modules (Phase 6):

| Module       | Responsibility                                                                   |
|--------------|----------------------------------------------------------------------------------|
| `investment` | + management fee in the terms, lease activation (`FUNDED → ACTIVE`), rental schedule, maturity |
| `earning`    | Rental receipts per period (record → four-eyes approval), distribution by ownership, investor earnings |
| `ledger`     | Append-only double-entry ledger: accounts per investor/platform and currency, balanced transactions, adjustments |

Modules (Phase 7):

| Module     | Responsibility                                                                     |
|------------|------------------------------------------------------------------------------------|
| `referral` | Referral codes, permanent referrer links made at sign-up, effective-dated rates for four levels, commissions on rental income, downline views |

Modules (Phase 8):

| Module       | Responsibility                                                                   |
|--------------|----------------------------------------------------------------------------------|
| `withdrawal` | Requests that reserve the balance, single/dual approval, payout batches per currency, bank file export, reconciliation |

Dependency direction is one-way (`payment` → `order` → `cart`, `invoice`, `investment` → `container`;
`earning` → `investment`, `ledger`; `referral` → `earning` (event), `ledger`, `user`; `auth` → `referral`;
`withdrawal` → `ledger`, `bankaccount`, `user`;
`marketplace` → `investment`; `kyc`, `bankaccount`, `invoice` → `user` → `audit`/`common`). Where a lower
module needs something from a higher one it declares an interface (`user.AuthorityGuard`, implemented
by `role`) or publishes an event (`UserStatusChangedEvent`, consumed by `auth` to end sessions).

### Conventions

- **Money**: `Money`/`BigDecimal` only, stored as `NUMERIC(19,4)` + ISO-4217 `CHAR(3)`.
  Rounded to the currency's minor unit only at payout/presentation boundaries.
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
| `POST /api/v1/admin/users/{id}/classification`      | `INVESTOR_CLASSIFY`     | RETAIL ↔ HNI (HNI needs approved KYC) |
| `GET /api/v1/admin/kyc[/{id}]`, `/admin/users/{id}/kyc` | `KYC_REVIEW`        | Review queue, detail, history        |
| `GET /api/v1/admin/kyc/{id}/documents/{docId}`      | `KYC_REVIEW`            | Decrypted evidence (audited)         |
| `POST /api/v1/admin/kyc/{id}/approve` · `/reject`   | `KYC_REVIEW`            | Decision (not on own submission)     |
| `GET /api/v1/admin/bank-accounts`                   | `BANK_ACCOUNT_VERIFY`   | Verification queue                   |
| `GET /api/v1/admin/users/{id}/bank-accounts`        | `BANK_ACCOUNT_VERIFY` or `FINANCE_VIEW` | A user's accounts + shared-account flag |
| `POST /api/v1/admin/bank-accounts/{id}/verify` · `/reject` | `BANK_ACCOUNT_VERIFY` | Decision (owner KYC must be approved) |

### Marketplace rules

- **Offerings.** One container backs at most one live offering (partial unique index). `RETAIL`
  offerings are shared: minimum + increments, optional per-investor cap. `HNI` offerings are
  standalone: one investor buys the whole container (enforced by a DB `CHECK`).
- **Lifecycle.** `DRAFT → OPEN → FUNDED → ACTIVE → MATURED → CLOSED`, or `CANCELLED`. Terms are
  editable only in `DRAFT`. Publishing needs `INVESTMENT_APPROVE`, at least one investor-visible
  photo and an offer window that has not closed. Cancelling is allowed only while nothing is
  reserved or committed. An offering becomes `FUNDED` once it is fully committed. `ACTIVE` and later
  states come from the leasing phases.
- **Eligibility.** Account active, KYC approved, and HNI classification for standalone offerings,
  inside the offer window. The marketplace shows the verdict and its reasons.
- **Capacity.** `available = total − committed − reserved`. `CapacityService` is the only writer:
  it locks the offering row (`FOR UPDATE`) and records each change as an immutable
  `capacity_movements` row (`RESERVE`, then exactly one `RELEASE` or `COMMIT`), keyed by a
  caller reference. Replays are no-ops and a DB `CHECK` makes overselling impossible. Phase 5 carts
  and orders call it.
- **Projection.** Ownership % = amount ÷ price; rental share = rental × amount ÷ price, shown per
  payment, per year and over the term.

### Containers, offerings & marketplace API

| Method & path                                              | Access                | Purpose                                 |
|------------------------------------------------------------|-----------------------|-----------------------------------------|
| `GET /api/v1/admin/containers[/{id}]`                      | `INVESTMENT_VIEW`     | Search (number, status, type), detail   |
| `POST /api/v1/admin/containers`, `PUT /…/{id}`             | `INVESTMENT_CREATE` / `INVESTMENT_UPDATE` | Register / edit          |
| `POST /api/v1/admin/containers/{id}/status`                | `INVESTMENT_UPDATE`   | Status change with reason (no retiring a live offering's container) |
| `POST /…/containers/{id}/documents` (multipart), `PATCH /…/documents/{docId}` | `INVESTMENT_UPDATE` | Upload photo/survey/lease/insurance/memorandum; toggle investor visibility |
| `GET /…/containers/{id}/documents/{docId}`                 | `INVESTMENT_VIEW`     | Document content                        |
| `GET /api/v1/admin/investment-products[/{id}]`             | `INVESTMENT_VIEW`     | Search, detail                          |
| `GET /…/investment-products/{id}/capacity-movements`       | `INVESTMENT_VIEW`     | Latest 50 capacity movements            |
| `POST /api/v1/admin/investment-products`, `PUT /…/{id}`    | `INVESTMENT_CREATE` / `INVESTMENT_UPDATE` | Create / edit draft      |
| `POST /…/investment-products/{id}/publish` · `/cancel`     | `INVESTMENT_APPROVE`  | Open to investors / cancel with reason  |
| `GET /api/v1/marketplace`                                  | `INVESTOR_PORTAL` or `INVESTMENT_VIEW` | Listings: type, container type, risk, status; sort `NEWEST`, `HIGHEST_YIELD`, `MOST_AVAILABLE`, `LOWEST_MINIMUM` |
| `GET /api/v1/marketplace/{id}`                             | 〃                    | Detail, availability, eligibility verdict |
| `GET /api/v1/marketplace/{id}/projection?amount=`          | 〃                    | Validated return projection             |
| `GET /api/v1/marketplace/{id}/documents/{docId}`           | 〃                    | Investor-visible documents only         |

Seed demo containers and offerings into a **local** API (runs as the bootstrap admin, safe to re-run):

```bash
python3 scripts/seed-demo-data.py
```

### Orders, payments & the outbox

```text
cart ──checkout──▶ order PENDING_PAYMENT ──payment succeeded──▶ CONFIRMED
 (no capacity)      capacity RESERVED          (one transaction)   capacity COMMITTED, holdings,
                    pay within 30 min                              invoice, outbox events
                          └──expired / cancelled──▶ capacity RELEASED, pending payments cancelled
```

- **Checkout** needs an `Idempotency-Key` header: a retry returns the original order, and reusing
  the key for a different request is a 409. The investor accepts each offering's current terms
  version, or checkout fails. The cart is locked, so concurrent checkouts of one cart place one
  order. Item terms (amount, ownership, rental share, terms version) are frozen in `order_items`.
- **Payments.** One attempt in flight per order. Starting another method cancels it, and money
  is taken at most once (partial unique indexes). *Bank transfer*: the investor quotes a generated
  reference, and finance records receipt (`PAYMENT_CONFIRM`); the amount must match exactly and
  nobody may confirm their own order. *Card*: a `WebhookPaymentProvider` reports outcomes by
  HMAC-signed webhook (`POST /api/v1/payments/webhooks/{provider}`, public, signature required).
  Redeliveries are ignored (`payment_events` unique on provider event id). Locally, a simulator
  stands in for the gateway and goes through the same signed-webhook path; it is disabled in
  production.
- **Money is never silently kept.** A payment arriving for an expired, cancelled or already paid
  order becomes `REFUND_REQUIRED`, and finance records the refund (`FINANCE_ADJUST`).
- **Locking.** Order row, then payment row, then offering rows (in id order). Payment settlement,
  cancellation and the expiry sweep therefore serialise without deadlocks.
- **Outbox.** Events are inserted in the business transaction. A relay (every 1 s, `FOR UPDATE
  SKIP LOCKED`, safe on several instances) publishes them with `eventId`/`eventType` headers, keyed
  by aggregate. Delivery is at-least-once: consumers call `ProcessedEvents.markProcessed` in their
  transaction. Topics: `investment.created` (order placed), `payment.success`, `payment.failed`,
  `investment.confirmed`, `invoice.generated`.
- **Invoices** (`INV-2026-000042`) are append-only; corrections will be credit notes. The portal
  renders them for print / PDF; JasperReports PDFs arrive with reporting (Phase 11).

### Cart, orders & payments API

| Method & path                                         | Access              | Purpose                                     |
|-------------------------------------------------------|---------------------|---------------------------------------------|
| `GET /api/v1/cart`, `PUT·DELETE /api/v1/cart/items/{productId}` | `INVESTOR_PORTAL` | View (with per-line problems) / set / remove |
| `POST /api/v1/orders` (`Idempotency-Key`)             | `INVESTOR_PORTAL`   | Check out the cart with accepted terms      |
| `GET /api/v1/orders[/{id}]`, `POST /…/{id}/cancel`    | `INVESTOR_PORTAL`   | Own orders; cancel while awaiting payment   |
| `GET·POST /api/v1/orders/{id}/payments` (`Idempotency-Key`) | `INVESTOR_PORTAL` | Payment attempts / start (`BANK_TRANSFER`, `CARD`) |
| `POST /api/v1/payments/{id}/simulate`                 | `INVESTOR_PORTAL`   | Simulator only: approve or decline a card   |
| `GET /api/v1/orders/{id}/invoice`                     | `INVESTOR_PORTAL`   | Invoice of a confirmed order                |
| `GET /api/v1/portfolio`                               | `INVESTOR_PORTAL`   | Holdings and totals per currency            |
| `POST /api/v1/payments/webhooks/{provider}`           | public, signed      | Gateway callbacks                           |
| `GET /api/v1/admin/orders[/{id}[/holdings·/invoice]]` | `ORDER_VIEW`        | Search, detail, holdings, invoice           |
| `GET /api/v1/admin/payments`, `/admin/orders/{id}/payments` | `FINANCE_VIEW` (or `ORDER_VIEW`) | Payment queue / per order |
| `POST /api/v1/admin/payments/{id}/confirm`            | `PAYMENT_CONFIRM`   | Record a received bank transfer             |
| `POST /api/v1/admin/payments/{id}/refund`             | `FINANCE_ADJUST`    | Record a refund                             |

### Leases, rental income & the ledger

```text
offering FUNDED ──activate lease──▶ ACTIVE ──rent recorded──▶ RECORDED ──approved (other user)──▶ DISTRIBUTED
 (or OPEN, settled)   sales stop,      per period,              │            earnings + ledger + outbox, one transaction
                      periods start    expected vs received     └─void──▶ REJECTED (record again)
                                       after the last period is distributed ──▶ offering & holdings MATURED
```

- **Fee.** `managementFeePercent` (0–50) is part of the offering's terms, so it is frozen once published. Listed
  yields, projections and each order line's rental share are net of it.
- **Lease.** `INVESTMENT_APPROVE` starts the lease of a `FUNDED` offering, or of an `OPEN` one with confirmed
  investors and nothing awaiting payment. Starting the lease ends sales. Period *n* runs from `start + (n−1)` periods to
  `start + n` periods, computed from the start date so month-ends do not drift. Rent is due in arrears, and a trailing
  part-period is not a period.
- **Receipts.** Finance (`RENTAL_RECORD`) records what the lessee actually paid for one period, with the bank
  reference. A note is required when the amount differs from the expected rent. A period is paid at most once.
  Someone else (`RENTAL_APPROVE`) approves, and the database rejects a self-approved distribution. The recorder or an
  approver can void a mistaken entry. Periods that have ended without a receipt are listed as due or overdue.
- **Distribution.** Each holding gets gross = receipt × holding amount ÷ price, rounded *down* to the minor unit.
  Then fee = gross × fee % (half-up) and net = gross − fee. The unsold share and rounding remainders are *retained* by the
  platform, so `receipt = Σ net + fees + retained` exactly. Recorded receipts show this split as a preview.
- **Ledger.** Every distribution and adjustment is one transaction of debit/credit entries. A deferred constraint
  trigger rejects any transaction that does not balance or mixes currencies, and accounts, transactions and entries
  are append-only. Distribution: debit *Rental cash*; credit each *Investor earnings* account (net), *Fee
  revenue* and *Retained*. Investor earnings balances are what Phase 8 withdrawals will draw on.
- **Adjustments.** `FINANCE_ADJUST` credits or debits an investor's balance against *Adjustments*, with a reason and an
  `Idempotency-Key`. A debit cannot overdraw the balance, and nobody may adjust their own.
- **Events.** `rental.generated` per distributed receipt and `earning.created` per investor share, via the outbox.

### Rentals, earnings & ledger API

| Method & path                                         | Access              | Purpose                                     |
|-------------------------------------------------------|---------------------|---------------------------------------------|
| `POST /api/v1/admin/investment-products/{id}/activate` | `INVESTMENT_APPROVE` | Start the lease (`leaseStartsOn`)          |
| `GET /api/v1/admin/rentals[/{id}]`, `/rentals/due`    | `FINANCE_VIEW`, `RENTAL_RECORD` or `RENTAL_APPROVE` | Receipts (with split), periods due |
| `POST /api/v1/admin/rentals`                          | `RENTAL_RECORD`     | Record a lessee payment for one period      |
| `POST /api/v1/admin/rentals/{id}/approve`             | `RENTAL_APPROVE`    | Distribute (not the recorder)               |
| `POST /api/v1/admin/rentals/{id}/reject`              | `RENTAL_APPROVE`, or the recorder | Void a recorded payment       |
| `GET /api/v1/admin/ledger/accounts[/{id}[/entries]]`, `/trial-balance` | `FINANCE_VIEW` | Balances, statements, trial balance |
| `POST /api/v1/admin/ledger/adjustments` (`Idempotency-Key`) | `FINANCE_ADJUST` | Correct an investor's balance        |
| `GET /api/v1/earnings`, `/earnings/summary`           | `INVESTOR_PORTAL`   | Own rental income history, balance, per-holding totals |

### Referrals

- **Linking.** Every investor has an 8-character code (no 0/O/1/I/L), created the first time they open their
  referral page and shared as `/register?ref=CODE`. A code entered at sign-up permanently records the
  referrer, in the registration transaction. An unknown code, or the code of an inactive account, fails registration.
  Links are append-only and always point to an existing account, so the hierarchy has no cycles.
- **Rates.** Levels 1–4 each take 0–10%, and at most 20% together. The seeded rates are 2 / 1 / 0.5 / 0.25%. A change is a
  new version starting now or later (`REFERRAL_CONFIG_MANAGE`). Versions in force are never edited, and a scheduled
  version can be cancelled before it starts. Each commission records the version and rate that produced it.
- **Commissions.** When rent is distributed, the earnings module publishes `RentalDistributedEvent` in-process,
  and commissions are paid in the same database transaction. Each referred investor's *gross* share earns
  their uplines, nearest first, up to four levels: share × rate, rounded down to the minor unit. The platform pays
  (*Referral commissions* expense → each upline's *Investor earnings* account, one `REFERRAL_COMMISSION` ledger
  transaction per receipt). The referred investor's own income is unchanged. An upline whose account is not
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
  The account must be active and KYC-approved. The amount must be at least `app.withdrawals.minimum-amount` (default 50) and within the
  balance, and only one withdrawal per currency can be open (partial unique index). The request needs an `Idempotency-Key`.
  Under the lock on the investor's ledger account (the same lock adjustments take), the amount moves from
  *Investor earnings* to *Withdrawals in transit*, so the same balance can never be withdrawn twice.
- **Approval.** `WITHDRAWAL_APPROVE` approves. Above `dual-approval-threshold` (default 10 000) a second, different
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

## Delivery phases

1. ✅ Project setup & base architecture
2. ✅ Authentication — registration, login, RS256 JWT, refresh-token rotation, roles & permissions
3. ✅ Users — profile, KYC, bank details, investor classification
4. ✅ Marketplace — containers, investment products, availability
5. ✅ Cart, orders, payments, investment confirmation, invoices (+ transactional outbox)
6. ✅ Earnings — leases, rental income, ownership distribution, double-entry ledger
7. ✅ Referrals — four-level hierarchy, effective-dated rates, commissions on rental income, downline tree
8. ✅ Withdrawals — reserved balances, single/dual approval, payout batches, bank file, reconciliation
9. Sales CRM · 10. Support · 11. Reporting · 12. Production hardening
