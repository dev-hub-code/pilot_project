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

Dependency direction is one-way (`marketplace` → `investment` → `container`; `kyc`, `bankaccount` → `user` → `audit`/`common`). Where a lower
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

## Delivery phases

1. ✅ Project setup & base architecture
2. ✅ Authentication — registration, login, RS256 JWT, refresh-token rotation, roles & permissions
3. ✅ Users — profile, KYC, bank details, investor classification
4. ✅ Marketplace — containers, investment products, availability
5. Cart, orders, payments, investment confirmation, invoices (+ transactional outbox)
6. Earnings — rental income, ownership distribution, ledger
7. Referrals — four-level hierarchy, configuration, earnings, downline tree
8. Withdrawals — validation, approval, batch processing, reconciliation
9. Sales CRM · 10. Support · 11. Reporting · 12. Production hardening
