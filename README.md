# SeaLease — Investment Platform

Investors put an amount of their choice into an investment plan for their investor type, under an investment agreement,
and are paid returns every month of the term. SeaLease runs a leasing business and pays returns from that income.
Holdings from before plans became returns-only also get 100 ÷ tenure % of the amount back each month. Uplines earn four-level referral income. Built as a **modular monolith** with financial
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
infra/lightsail/   Terraform + Compose: the low-cost development server (what runs now)
infra/ecs/   Terraform: the high-availability production cluster (kept for later; not deployed)
docker-compose.yml   Local PostgreSQL + Kafka + Mailpit (email)
.env.example         Local configuration template
```

## Getting started

```bash
cp .env.example .env                 # set DB_PASSWORD (and DB_PORT if 5432 is taken)
scripts/generate-jwt-keys.sh         # RS256 key pair -> .secrets/ (git-ignored); paste paths into .env
scripts/generate-encryption-keys.sh >> .env   # field-encryption keys (tax ids, KYC files, bank accounts)
#   optionally set BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD to create the first super-admin
docker compose up -d                 # PostgreSQL + Kafka + Mailpit

cd backend && ./mvnw spring-boot:run # http://localhost:8080 (profile: local)

cd frontend
cp .env.example .env.local
npm install && npm run dev           # http://localhost:3000
```

Useful local endpoints:

- `GET /actuator/health/liveness`, `/actuator/health/readiness` — public probes
- `/swagger-ui.html` — API docs (local profile only)
- `GET /api/v1/auth/jwks` — public token-verification key
- http://localhost:8025 — Mailpit inbox: every notification email sent locally

### Tests

```bash
cd backend && ./mvnw test            # integration tests need a running Docker daemon (Testcontainers);
                                     # without one they are skipped, not failed
cd frontend && npm run typecheck && npm run lint
```

## Development server (AWS Lightsail, low cost)

During development the site runs on Lightsail in Mumbai (`infra/lightsail`): one 8 GB, 2 vCPU server with Caddy
(HTTPS from Let's Encrypt), the web app, the API and Kafka in Docker Compose, and a managed Lightsail PostgreSQL 18
(2 GB RAM, 80 GB).
It costs about $74 a month: $44 for the server, $30 for the database (2 GB RAM, 80 GB), cents for S3. Email goes through SendGrid (free plan: 100 a day). An AWS
budget emails `budget_email` at 80% of $80 (actual) and when the month's forecast passes $80. There is no redundancy: if the server fails, the site is down until a new one is
built and redeployed; the server keeps no data of its own. The database has automatic daily backups (7 days, with
point-in-time restore), uploaded documents are in S3, and every night at 02:00 IST the database is also dumped to an
S3 bucket that keeps 14 days.

First time (AWS credentials configured, Docker running, `~/.ssh/sealease_lightsail` key pair present):

```bash
terraform -chdir=infra/lightsail init
terraform -chdir=infra/lightsail apply        # the server, its static IP, the database, S3 buckets
infra/lightsail/env.sh                        # .secrets/lightsail.env: new keys + AWS credentials. BACK IT UP.
# Email: add SENDGRID_API_KEY=SG.… (a SendGrid key with Mail Send access) to .secrets/lightsail.env and rerun env.sh.
# Online payments: add RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET and RAZORPAY_WEBHOOK_SECRET to .secrets/lightsail.env
# (env.sh keeps them), and on the Razorpay dashboard add the webhook https://www.sealease.in/api/v1/webhooks/razorpay
terraform -chdir=infra/lightsail output       # point GoDaddy's @ and www A records at static_ip
infra/lightsail/deploy.sh
```

**Email (SendGrid).** The API sends through SendGrid's SMTP relay (`smtp.sendgrid.net:587`, user `apikey`, the API
key as password) from `no-reply@sealease.in`. In SendGrid, authenticate the domain (Settings > Sender Authentication >
Authenticate Your Domain, DNS host GoDaddy) and add the CNAME records it shows in GoDaddy; keep the `_dmarc` record.

Every later deployment is `infra/lightsail/deploy.sh`. It always deploys `main` as pushed to GitHub (`origin/main`),
whatever branch is checked out: it builds the images locally from a clean temporary copy of that commit, copies them
over SSH (no registry), and restarts the containers. SSH in with the command in `terraform output ssh`; logs:
`cd /opt/sealease && docker compose logs -f backend`.

**Database.** The app's database is an Amazon RDS PostgreSQL (`rds.tf`) whose disk grows by itself (20 GB up to
200 GB). It replaced the first Lightsail database in November 2026; that database's final snapshot is
`sealease-dev-db-final`.

To stop all charges: `terraform -chdir=infra/lightsail destroy` (empty the two buckets first; it keeps a final database
snapshot, which is billed until you delete it).

## Production (AWS, high availability; not deployed now)

`infra/ecs` is the production setup for later: it runs production in one AWS region (Mumbai, `ap-south-1`), with every tier spread over two availability
zones, so it keeps serving if a task, a server or a whole zone fails. The defaults are the leanest setup that still
does that; each line below names the variable that buys more headroom.

| Tier | Service | Default | Larger |
|------|---------|---------|--------|
| Entry | Application Load Balancer (HTTPS, ACM certificate) | in both zones | |
| Web, API | ECS Fargate | at least 2 tasks each across zones, the first on-demand and the rest on Spot; CPU autoscaling up to 6; rolling deploys with automatic rollback | `fargate_spot = false`, `backend_cpu`/`frontend_cpu` |
| Database | RDS PostgreSQL Multi-AZ, `db.t4g.micro` | synchronous standby, automatic failover; 14 days of backups | `db_instance_class` |
| Events | Amazon MSK (Kafka) | a broker per zone, replication factor 2 | `az_count = 3` (3 brokers, replication 3) |
| Outbound | public IPs on the tasks; security groups admit nothing from the internet | | `nat_gateway = "per_az"` (private subnets) |
| Documents | S3 (versioned, encrypted, HTTPS only) | regional | |

Every background job is safe on several API tasks at once (row locks, `SKIP LOCKED`, unique constraints). Secrets
(database password, JWT and field-encryption keys) are generated by Terraform into Secrets Manager; the Terraform
state therefore holds them too: keep `infra/ecs/terraform.tfstate` safe and backed up.

First deployment (AWS credentials configured, Docker running, a clean checkout of `main`):

```bash
cd infra/ecs && terraform init
# 1. The image registry and the HTTPS certificate first.
terraform apply -target=aws_ecr_repository.app -target=aws_acm_certificate.site
terraform output godaddy_dns_records   # add these records in GoDaddy (the "www" one can wait until step 3), plus
                                       # SendGrid's domain-authentication CNAMEs (see the Lightsail section)
cd ../.. && infra/ecs/deploy.sh --push --skip-checks # 2. build the images and push them to ECR
# 3. Everything else. Waits until GoDaddy serves the certificate records. The bootstrap variables create the first
#    super-admin; leave them out of later applies. TF_VAR_sendgrid_api_key is needed on every apply.
TF_VAR_sendgrid_api_key='SG.…' TF_VAR_bootstrap_admin_email=you@example.com TF_VAR_bootstrap_admin_password='…' \
  terraform -chdir=infra/ecs apply -var image_tag=$(git rev-parse --short=12 HEAD)
```

Then point `www` at the load balancer (the CNAME in `godaddy_dns_records`) and forward the bare domain to
`https://www.sealease.in`.

### Deploying

There is no CI/CD (GitHub Actions is off for this repository): production is deployed by hand from `main`.

```bash
git switch main && git pull --ff-only
infra/ecs/deploy.sh                  # checks, build, push, roll out
```

`deploy.sh` refuses to run unless the checkout is clean and exactly `origin/main` (`--any-branch` overrides, for
hotfixes). It runs the checks CI would: `./mvnw verify` (Testcontainers, so Docker must be running) and the frontend
typecheck, lint, build and production-dependency audit (`--skip-checks` skips them, e.g. when re-deploying a commit
already checked). It then builds the images, pushes them to ECR tagged with the commit, and `infra/ecs/rollout.sh`
rolls the API and then the web app onto them, failing if ECS rolls a service back. A deployment takes about 10
minutes plus the checks.

Every deployment records the live tag in SSM (`/sealease/image-tag`); `deploy.sh` prints the previous one. To roll
back, run `infra/ecs/rollout.sh <previous tag>` (its images are still in ECR: the last 20 are kept). Terraform runs the
recorded tag unless `-var image_tag=…` is given, so infrastructure applies never roll the app back.

## Backend architecture

Root package `com.sealease.backend`. Each business module (`auth`, `user`, `investment`,
`order`, `earning`, `referral`, `withdrawal`, …) is added as a top-level package
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
| `investment`  | Plans by investor type, with term limits; DRAFT → OPEN → CLOSED lifecycle |

Modules (Phase 5):

| Module    | Responsibility                                                                     |
|-----------|------------------------------------------------------------------------------------|
| `order`   | Checkout (idempotent), terms acceptance, expiry, holdings and agreements on payment |
| `payment` | Company bank accounts, bank payment with investor-submitted details, settlement, rejection, refunds |
| `invoice` | Immutable, sequentially numbered invoices with seller/buyer snapshots              |
| `agreement` | Investment agreements: admin-maintained, versioned wording with `{{placeholders}}`; each confirmed investment gets an immutable, numbered agreement (`AGR-2026-000042`) in the wording in force |
| `investment` | + holdings (one per plan in an order) and the investor portfolio |
| `outbox`  | Transactional outbox → Kafka relay; consumer de-duplication (`processed_events`)   |

Modules (Phase 6):

| Module       | Responsibility                                                                   |
|--------------|----------------------------------------------------------------------------------|
| `investment` | + holding maturity at the end of the term     |
| `earning`    | Monthly payout schedule per holding (rent; plus capital for holdings bought before plans became rent-only), automatic crediting, investor earnings |
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

Dependency direction is one-way (`payment` → `order` → `invoice`, `order` → `investment`;
`earning` → `investment`, `ledger`; `referral` → `earning` (event), `ledger`, `user`; `auth` → `referral`;
`withdrawal` → `ledger`, `bankaccount`, `user`; `crm` → `user`, `role`, `order` (event);
`helpdesk` → `notification`, `document`, `order`, `withdrawal`, `investment`, `role`;
`kyc`, `bankaccount`, `invoice` → `user` → `audit`/`common`). Where a lower
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
- **Schema**: Flyway owns the schema (`ddl-auto: validate`). Migrations live in `db/migration` as `sequences/`, `ddl/` and `dml/`, the locations loader is `V6__LoadLocations`, and the administrator is in `db/seed`. New changes go in new files with the next version; never edit one already applied.
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
  permissions. Staff sign in at `/platform/login` and work in the staff console at `/platform`; investor pages redirect them there.
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
| `GET/POST /api/v1/users/me/kyc`                     | authenticated           | Latest KYC / upload Aadhaar or PAN (multipart; review starts once both are in) |
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

### Plan rules

- **Plans.** A plan is one tenure (1–30 whole years) for one investor type (`audience`: `ONLINE` or `NETWORK`), with its
  annual return % and minimum units. One unit costs ₹500 on every plan (`PlanRules.UNIT_PRICE`). Investors see, and can invest in, only the plans for their type; visitors
  and the open API see online plans. Terms are editable only while `DRAFT`; once published they are frozen.
- **Investing.** The investor buys whole units of a plan, at least its minimum. The amount is units × unit price, and the
  units, price, return and tenure are fixed on the order. The investment is not paid back: returns are the monthly payments
  over the tenure. Holdings from before keep their terms.
- **After payment.** Confirming the order makes one holding per plan, `AWAITING_ACCEPTANCE`, and issues its investment
  agreement. Nothing is assigned to the investor. Accepting the agreement starts the term at the next full hour.
- **Internal names.** Code, API fields and database tables keep the older words `rent` and `lease`
  (`monthlyRentPercent`, `lease_agreements`); only text people read says returns, term and investment.
- **Quote.** For a number of units: amount = units × unit price; monthly returns = amount × annual return % ÷ 12 (rounded half-up to
  the paisa; orders keep the monthly rate to 8 decimals); over the tenure, monthly returns × 12 × tenure.

### Plans API

| Method & path                                              | Access                | Purpose                                 |
|------------------------------------------------------------|-----------------------|-----------------------------------------|
| `GET /api/v1/admin/investment-products[/{id}]`             | `INVESTMENT_VIEW`     | Search, detail                          |
| `POST /api/v1/admin/investment-products`, `PUT /…/{id}`    | `INVESTMENT_CREATE` / `INVESTMENT_UPDATE` | Create / edit draft      |
| `POST /…/investment-products/{id}/publish` · `/close` · `/reopen` · `/cancel` | `INVESTMENT_APPROVE` | Open to investors / stop sales / resume sales / cancel a draft |
| `GET /api/v1/plans`, `/plans/{id}`                         | `INVESTOR_PORTAL` or `INVESTMENT_VIEW` | Plans for the viewer's type, shortest tenure first; detail with eligibility verdict |
| `GET /api/v1/plans/{id}/quote?units=`                      | 〃                    | What the units cost and pay             |
| `GET /api/v1/public/plans`, `/api/v1/open/plans[/{code}]`  | Public                | Open online plans                       |

Seed company bank accounts and plans into a **local** API (runs as the bootstrap admin, safe to re-run):

```bash
python3 scripts/seed-demo-data.py
```

### Orders, payments & the outbox

```text
units of a plan ──▶ order PENDING_PAYMENT ──payment succeeded──▶ CONFIRMED
                    pay within 30 min          (one transaction)   holdings, investment agreements, invoice, events
                          └──expired / cancelled──▶ pending payments cancelled
```

- **Placing an order** (`{productId, units, acceptTerms}`) needs an `Idempotency-Key` header: a retry returns the original
  order, even when both arrive together, and reusing the key for a different request is a 409. The investor must accept the
  plan's terms. There is one `order_items` row per order, with the terms frozen (units, unit price, amount, return %, tenure).
- **Online investors pay through Razorpay** (`payment/gateway`), once `RAZORPAY_KEY_ID`,
  `RAZORPAY_KEY_SECRET` and `RAZORPAY_WEBHOOK_SECRET` are set; until then they pay as network investors do.
  - `GATEWAY`: Razorpay's checkout (UPI or net banking; cards off). The order is reserved for 30 minutes and
    confirmed when the checkout's signed result has been checked with Razorpay's API.
  - `VIRTUAL_ACCOUNT`: a Smart Collect account number, IFSC and UPI ID for this one payment, paid by NEFT, RTGS, IMPS
    or UPI. The order is reserved for 3 days and confirmed when the money lands; the account then closes.
  - The webhook `POST /api/v1/webhooks/razorpay` (events `payment.authorized`, `payment.captured`,
    `virtual_account.credited`; signed with the webhook secret) confirms payments even if the browser closes.
    Reporting a payment twice changes nothing. A wrong amount, or money for an order already paid or closed, becomes
    `REFUND_REQUIRED`: finance refunds it on the Razorpay dashboard and records the refund.
- **Network investors pay by bank or at the office**, confirmed by finance (below). Card payments are not accepted
  (earlier card payments, if any, remain readable as `CARD`). One attempt in flight per order, and money is taken
  at most once (partial unique indexes). Finance maintains the company
  bank accounts investors may pay into (account number, IFSC, optional UPI ID;
  `COMPANY_BANK_ACCOUNT_MANAGE`, deactivated rather than deleted). The investor chooses one of the
  active accounts, quotes a generated reference, pays online (NEFT/RTGS/IMPS/UPI) or by cheque
  (cash deposits at the bank are not accepted), and submits the transaction ID or cheque number. From then
  on the order is held for verification (`APP_BANK_PAYMENT_VERIFICATION_WINDOW`, default 7 days)
  instead of lapsing after 30 minutes, and the investor may correct the details until finance
  decides. Finance confirms receipt (`PAYMENT_CONFIRM`; the amount
  must match exactly) or rejects the payment with a reason, after which the investor can pay again
  while the order is open. Nobody may decide on a payment for their own order.
- **Money is never silently kept.** A payment arriving for an expired, cancelled or already paid
  order becomes `REFUND_REQUIRED`, and finance records the refund (`FINANCE_ADJUST`).
- **Locking.** Order row, then payment row. Payment settlement,
  cancellation and the expiry sweep therefore serialise without deadlocks.
- **Outbox.** Events are inserted in the business transaction. A relay (every 1 s, `FOR UPDATE
  SKIP LOCKED`, safe on several instances) publishes them with `eventId`/`eventType` headers, keyed
  by aggregate. Delivery is at-least-once: consumers call `ProcessedEvents.markProcessed` in their
  transaction. Topics: `investment.created` (order placed), `payment.success`, `payment.failed`,
  `investment.confirmed`, `invoice.generated`.
- **Invoices** (`INV-2026-000042`) are append-only; corrections will be credit notes. The portal
  renders them for print, and offers a JasperReports PDF (see *Reporting*).
- **Investment agreements.** Admins write the wording (*Admin → Agreement*) with placeholders such as `{{investorName}}`,
  `{{investmentAmount}}`, `{{monthlyReturns}}` and `{{endDate}}`; unknown placeholders are refused on save. A blank line
  separates paragraphs and a line starting with `# ` is a heading. Each save is a new version (`agreement_templates`,
  append-only). Payment confirmation issues the holding's agreement in the same transaction: the wording in force, stored
  word for word (`lease_agreements`, append-only), so later changes never alter it. The shipped wording is a draft for legal review.
- **Acceptance starts earning.** The investor reads the agreement (*Portfolio → Review and accept*) and accepts it
  (`POST /api/v1/agreements/{holdingId}/accept`); in one transaction, under the holding's row lock, the term starts at the
  next full hour, the returns schedule is created and the acceptance is stored append-only.

### Orders & payments API

| Method & path                                         | Access              | Purpose                                     |
|-------------------------------------------------------|---------------------|---------------------------------------------|
| `POST /api/v1/orders` (`Idempotency-Key`)             | `INVESTOR_PORTAL`   | Buy units of a plan                         |
| `GET /api/v1/orders[/{id}]`, `POST /…/{id}/cancel`    | `INVESTOR_PORTAL`   | Own orders; cancel while awaiting payment   |
| `GET·POST /api/v1/orders/{id}/payments` (`Idempotency-Key`) | `INVESTOR_PORTAL` | Payment attempts / start a bank payment (`BANK_TRANSFER`) |
| `POST /api/v1/payments/{id}/deposit`                  | `INVESTOR_PORTAL`   | Bank payment details: company account, mode (`ONLINE`, `CHEQUE`), reference |
| `GET /api/v1/orders/{id}/invoice`                     | `INVESTOR_PORTAL`   | Invoice of a confirmed order                |
| `GET /api/v1/portfolio`                               | `INVESTOR_PORTAL`   | Holdings and totals per currency            |
| `GET /api/v1/admin/orders[/{id}[/holdings·/invoice]]` | `ORDER_VIEW`        | Search, detail, holdings, invoice           |
| `GET /api/v1/admin/payments`, `/admin/orders/{id}/payments` | `FINANCE_VIEW` (or `ORDER_VIEW`) | Payment queue / per order |
| `POST /api/v1/admin/payments/{id}/confirm`            | `PAYMENT_CONFIRM`   | Record a received bank payment              |
| `POST /api/v1/admin/payments/{id}/reject`             | `PAYMENT_CONFIRM`   | Reject a bank payment that never arrived (reason) |
| `GET·POST /api/v1/admin/company-bank-accounts`, `PUT /…/{id}`, `POST /…/{id}/activate·deactivate` | `COMPANY_BANK_ACCOUNT_MANAGE` (list: or `FINANCE_VIEW`) | Company bank accounts investors pay into |
| `POST /api/v1/admin/payments/{id}/refund`             | `FINANCE_ADJUST`    | Record a refund                             |
| `GET·PUT /api/v1/admin/agreement-template`, `POST /…/preview`, `GET /…/sample` | `AGREEMENT_TEMPLATE_MANAGE` | Lease agreement wording: read, save as a new version, preview / sample PDF with example values |
| `GET /api/v1/agreements`, `/agreements/{holdingId}[/pdf]` | `INVESTOR_PORTAL` | Own lease agreements, with wording / as PDF |
| `POST /api/v1/agreements/{holdingId}/accept`          | `INVESTOR_PORTAL`   | Accept the agreement: starts the lease and rent |
| `GET /api/v1/admin/agreements?orderId`, `/admin/agreements/{holdingId}/pdf` | `ORDER_VIEW` | An order's lease agreements |

### Payouts & the ledger

```text
payment confirmed ──▶ holding per unit, AWAITING_ACCEPTANCE (no lease, no payouts)
agreement accepted by the investor ──▶ ACTIVE, lease from then for the chosen tenure (T months)
                      one installment per calendar month of the lease (a part month at each end)
every day that ends ──payout job (hourly) or POST /admin/payouts/run──▶ that day's rent credited to the wallet
last day of a month credited ──▶ installment PAID, referral commission on it paid
last installment PAID ──▶ holding MATURED
```

- **Amounts.** Per unit per month: rent = price × the plan's rent %, rounded half-up to the paisa and
  fixed when the schedule is created; a part month gets its days ÷ the month's days of it. Holdings bought
  before plans became rent-only (their recorded capital return % is above 0) also get capital = price ÷ tenure a
  month; the last installment's capital takes up the rounding.
- **Daily crediting (V40).** Each day of an installment is credited the day after, one ledger transaction and one
  `rent_credits` row per day: the month's amount ÷ its days, rounded so the days add up to exactly the month's
  amount. The job (`app.payouts.sweep-interval`, hourly) catches up any days missed, each installment in its own
  transaction under a row lock, so runs can overlap and repeat safely; a day is never credited twice (unique per
  installment and day). Finance can run it at once (`PAYOUT_PROCESS`).
- **Credit interval (V75).** Rent accrues continuously and is credited once each *credit interval* ends: hourly
  by default (`app.payouts.credit-interval`), changeable by admins on **Admin → Rent** (`PAYOUT_PROCESS` or
  `SYSTEM_SETTINGS_MANAGE`; audited as `RENT_CREDIT_INTERVAL_CHANGED`) to 5 min – 1 day, any whole minutes dividing a
  day. Intervals are counted in UTC from the epoch, so daily credits land at midnight UTC. Crediting is cumulative
  (owed up to the boundary less already credited), so a change applies from the next run and loses or repeats
  nothing. Leases still start at the full hour after acceptance (the interval's step if it is under an hour). The
  website and portal read the wording ("every hour", "every day") from `GET /api/v1/public/rent-credit`.
- **Ledger.** Every payout, commission and adjustment is one transaction of debit/credit entries. A deferred
  constraint trigger rejects any transaction that does not balance or mixes currencies, and accounts,
  transactions and entries are append-only. A day's rent debits *Rent paid to investors* (and *Capital returned to
  investors*) and credits the investor's rent balance (bank or cash, by how the order was paid), which withdrawals draw on.
- **Adjustments.** `FINANCE_ADJUST` credits or debits an investor's balance against *Adjustments*, with a reason and an
  `Idempotency-Key`. A debit cannot overdraw the balance, and nobody may adjust their own.
- **Events.** `earning.created` per installment fully credited, via the outbox.

### Payouts, earnings & ledger API

| Method & path                                         | Access              | Purpose                                     |
|-------------------------------------------------------|---------------------|---------------------------------------------|
| `GET /api/v1/admin/payouts?status&userId&holdingId&dueBy`, `/payouts/due` | `FINANCE_VIEW` or `PAYOUT_PROCESS` | Payout schedule; due and unpaid totals |
| `POST /api/v1/admin/payouts/run`                      | `PAYOUT_PROCESS`    | Pay everything due now                      |
| `GET /api/v1/admin/ledger/accounts[/{id}[/entries]]`, `/trial-balance` | `FINANCE_VIEW` | Balances, statements, trial balance |
| `POST /api/v1/admin/ledger/adjustments` (`Idempotency-Key`) | `FINANCE_ADJUST` | Correct an investor's balance        |
| `GET /api/v1/earnings?status`, `/earnings/summary`    | `INVESTOR_PORTAL`   | Own payouts (paid or scheduled), wallet, rent and capital received, next payout |
| `GET /api/v1/earnings/monthly?from&to` (`yyyy-MM`, ≤ 36 months) | `INVESTOR_PORTAL` | Rent and capital per month by due date, paid and scheduled (dashboard chart) |

### Referrals

- **Linking.** Every investor has an 8-character code (no 0/O/1/I/L), created the first time they open their
  referral page and shared as `/register?ref=CODE`. A code entered at sign-up permanently records the
  referrer, in the registration transaction. An unknown code, or the code of an inactive account, fails registration.
  Links are append-only and always point to an existing account, so the hierarchy has no cycles.
- **Rates.** Levels 1–4 each take 0–10%, and at most 20% together. The seeded rates are 2 / 1 / 0.5 / 0.25%. The online scheme's one-time rate is versioned with them (0–10%, 2% on versions from before it existed). A change is a
  new version starting now or later (`REFERRAL_CONFIG_MANAGE`). Versions in force are never edited, and a scheduled
  version can be cancelled before it starts. Each commission records the version and rate that produced it.
- **Commissions.** When a monthly payout is credited, the earnings module publishes `PayoutPaidEvent` in-process,
  and commissions are paid in the same database transaction. The *rent* part of the referred investor's payout (not
  the capital returned) earns their uplines, nearest first, up to four levels: rent × rate, rounded down to the minor
  unit. The platform pays (*Referral commissions* expense → each upline's *Investor earnings* account, one
  `REFERRAL_COMMISSION` ledger transaction per payout). The referred investor's own payout is unchanged. An upline whose account is not
  active, or whose identity is not verified, forfeits that level; it is not passed further up.
- **Schemes.** How someone earns depends on their own scheme. *Network* investors are made by an administrator
  (`POST /api/v1/admin/investors`, `INVESTOR_ACCOUNT_CREATE`) and listed in `network_investors`: they earn the monthly
  level rates. Everyone who registers online is on the *online* scheme: nothing monthly and nothing below level 1, only
  the version's `one_time_percent` of the whole first order of each person they referred directly, once (unique per
  referred person), when a lease of that order starts. An online upline's level is skipped, so network uplines above
  them still earn theirs. A network investor's `signups_join_network` setting decides whether people signing up with
  their code join the network (and pass the setting on) or the online scheme.
- **Returns by investor type.** Each plan is for one investor type and sets its own monthly return; an order keeps the return of its plan when placed. The website shows the online bands; signed-in investors read theirs from `GET /api/v1/rent-tiers`.
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

Network investors do not request withdrawals: they are made by a monthly schedule (V41), or by finance at the office.
Online investors can also request one at any time (below).

```text
rent file day (the day before the rent withdrawal day, default 9th) ──▶ every investor's bank-rent balance:
    withdrawal BATCHED into this month's "Rent-<Month>-<Year>.xlsx" (balance → in transit)
referral file day (default 14th) ──▶ likewise referral balances of investors paid by bank this year: "Referral-…xlsx"
finance pays the file by hand on the day, uploads it back with Status per row ──▶ PROCESSING ──▶ PAID
                                                                                       └──▶ FAILED: held + support ticket
FAILED ──finance resolves──▶ PAID (paid by hand)  or  RETURNED (money back to the balance, paid next month)
cash at the office (from the withdrawal day) ──finance makes it in front of the investor──▶ APPROVED ──hand over──▶ PAID
```

- **How investors are paid.** Online investors are always paid by bank and cannot change it. Network investors choose
  bank or cash at the office, at any time; one choice (`referral_payout_modes`, latest row) covers returns and referral
  earnings, and until they choose it is bank if they have a verified primary bank account, else cash. Finance can change a
  network investor's choice on request. A change applies to withdrawals made from then on.
- **Runs are for network investors.** Online investors are left out of every run, and not listed as skipped: they request
  their own withdrawals.
- **Monthly runs.** `app.withdrawals.schedule-interval` (hourly) makes each run once its file day has come; each month
  and kind runs once (`scheduled_withdrawal_runs`). The whole balance (at least `app.withdrawals.minimum-amount`) is
  withdrawn to the verified primary bank account. Skipped, with the reason kept: inactive or unverified accounts, no
  verified primary account, or an earlier failed withdrawal still unresolved. Finance can run a month early.
  The withdrawal days are set by an admin (`SYSTEM_SETTINGS_MANAGE`), 2–28.
- **The file.** Excel, kept encrypted with the batch: Serial No, User ID, Name, Account Number, IFSC Code, Amount, and
  empty Status and Remarks columns. Finance uploads it back: rows are matched by User ID; Status PAID (Remarks: the
  bank reference), FAILED (Remarks: why) or blank (still outstanding). One bad row refuses the whole file; uploading
  it again changes nothing. Every download and upload is audited and kept.
- **Failures.** A failed payment is held (still *in transit*) and a support ticket (category Withdrawal, high priority)
  is opened for the investor, assigned to the finance user who recorded it (FINANCE can now work tickets). Resolving
  the withdrawal resolves the ticket with a note to the investor.
- **Cash at the office.** On the investor's admin page, finance withdraws a whole rent (cash) or referral balance (if
  the investor chose cash) in front of them, once a month from the withdrawal day, then records the hand-over with the
  receipt number. One not handed over can be cancelled; the money goes back.
- **Requests by online investors (V22).** An online investor withdraws any amount from the minimum up to what one
  wallet holds, in five steps: wallet, amount (the backend quotes charges, tax, what is paid and what stays), a verified
  bank account (masked), review, then submit confirmed with their password (a wrong one counts towards the sign-in
  lockout). Only the amount is taken from the client. The gross amount moves from the wallet to *in transit* in the same
  transaction as the withdrawal is saved, under a lock on the wallet; one open withdrawal per wallet; the
  `Idempotency-Key` makes a retry return the same withdrawal. Principal is never withdrawable: the overview shows, per
  investment, what it has earned (credited to the wallets) and what is locked and why.

```text
SUBMITTED ──take──▶ UNDER_REVIEW ──approve──▶ APPROVED            (above the threshold: PENDING_APPROVAL, then a
    │                  │  ▲                       │                 second, different approver)
    │                  ▼  │ resume / investor answers                ──initiate payment──▶ BATCHED ──▶ PROCESSING
    └──────────────▶ ON_HOLD (reason; optionally "information needed")                        │
REJECTED (reason, money back) · CANCELLED by the investor until approved          PAID ◀──────┴──▶ FAILED (held + ticket)
```

  Brief's names: BATCHED = payment initiated, PROCESSING = payment pending, FAILED = payment failed. The quote plays the
  part of a draft. Transitions are enforced in `WithdrawalStatus`; nobody reviews, approves or pays their own
  withdrawal (also a database check).
- **Charges and tax.** `withdrawal_settings`: flat charge, percentage charge, TDS percentage (all zero by default), the
  second-approval threshold and the processing days shown. Rounded half-up to the paisa and fixed on each withdrawal.
  When paid, the ledger posts: debit *in transit* (gross), credit client money (net), credit `WITHDRAWAL_FEE_INCOME`
  (charges) and `TAX_WITHHELD` (tax). Release on reject/cancel/return gives back the gross amount. Bank sheets, batch
  totals and finance tasks pay the net amount.
- **Paid only on evidence.** Payment is initiated by staff through a `PayoutProvider` (`BankSheetPayoutProvider` today:
  a batch with its Excel sheet, then finance tasks). PAID comes from the reconciled sheet, finance's entry *with the
  bank's reference (UTR)* (required for bank transfers), or a verified webhook; never from an approval.
- **Payout webhook.** `POST /api/v1/webhooks/payouts`, header `X-Payout-Signature` = hex HMAC-SHA256 of the raw body with
  `APP_PAYOUT_WEBHOOK_SECRET` (blank: the endpoint answers 404). Body
  `{"id","type":"payout.paid|payout.failed|payout.processing","withdrawalReference","providerReference","amount","currency","reason"}`.
  Each event id is recorded once in `payout_webhook_events` (a redelivery answers `DUPLICATE`). Paid needs the provider
  reference and exactly the net amount; anything doubtful (`AMOUNT_MISMATCH`, `NEEDS_RECONCILIATION`, unknown types) is
  recorded and left for finance, never retried automatically.
- **Timeline, notes, notifications.** Every status change goes in `withdrawal_events` (immutable) with who and why;
  investors see their steps without staff names. Internal notes (`withdrawal_notes`, immutable) are staff-only.
  Investors are notified (in-app and email) on submitted, under review, information needed, on hold, approved,
  rejected, cancelled, payment initiated, paid, failed and returned, once per change. Paid withdrawals have a PDF receipt.
- **Export.** The admin list links to the *Withdrawal register* report (CSV/PDF).
- **Events.** `withdrawal.approved`, `.batched` and `.processing` (per batch), `.completed`, and `.failed` (also for
  returns and cancellations, with event types `WithdrawalReturned` and `WithdrawalRejected`).

### Withdrawals API

| Method & path                                                           | Access               | Purpose                                        |
|-------------------------------------------------------------------------|----------------------|------------------------------------------------|
| `GET /api/v1/withdrawals/policy`, `GET /api/v1/withdrawals`             | `INVESTOR_PORTAL`    | Schedule and office; own withdrawals           |
| `GET /api/v1/withdrawals/overview`                                     | `INVESTOR_PORTAL`    | Totals, wallets, per-investment earned/locked, verified accounts (masked) |
| `GET /api/v1/withdrawals/quote?balance=&amount=`                        | `INVESTOR_PORTAL`    | Charges, tax, net and remaining for an amount  |
| `POST /api/v1/withdrawals` (`Idempotency-Key`)                         | `INVESTOR_PORTAL`    | Online investors: `{balance, amount, bankAccountId?, password}` |
| `GET /api/v1/withdrawals/{id}`, `/{id}/timeline`, `/{id}/receipt`       | `INVESTOR_PORTAL`    | Own withdrawal, its tracking steps, PDF receipt once paid |
| `POST /api/v1/withdrawals/{id}/cancel`, `/{id}/respond`                 | `INVESTOR_PORTAL`    | Cancel while under review; answer an information request |
| `GET`, `PUT /api/v1/withdrawals/referral-mode`                          | `INVESTOR_PORTAL`    | How returns and referral are paid; network investors change it at any time |
| `GET /api/v1/admin/withdrawals` (`status`, `q`, `from`, `to`, `minAmount`, `maxAmount`, `origin`, `mine`, `sort`, paged) | `WITHDRAWAL_VIEW` | Search, server-side |
| `GET /api/v1/admin/withdrawals/dashboard`, `/{id}`, `/{id}/receipt`     | `WITHDRAWAL_VIEW`    | Queue metrics; detail with investor, timeline, notes; receipt |
| `POST /api/v1/admin/withdrawals/{id}/notes`                             | `WITHDRAWAL_VIEW`    | Internal note                                  |
| `POST /…/withdrawals/{id}/review` · `/review/release` · `/approve` · `/hold` · `/request-info` · `/resume` | `WITHDRAWAL_APPROVE` | Review and approve (maker-checker) |
| `POST /api/v1/admin/withdrawals/{id}/reject` (`{reason}`)               | `WITHDRAWAL_REJECT`  | Reject with a reason; money back               |
| `POST /api/v1/admin/withdrawals/initiate-payment` (`{withdrawalIds}`)   | `WITHDRAWAL_PROCESS` | Hand approved requests to the payout provider  |
| `POST /api/v1/webhooks/payouts`                                         | signed (HMAC)        | Payout provider status updates                 |
| `GET`, `POST /api/v1/admin/withdrawals/office`                          | `WITHDRAWAL_PROCESS` | Cash at the office: what is allowed; make one  |
| `POST /api/v1/admin/withdrawals/{id}/hand-over` · `/reject`             | `WITHDRAWAL_PROCESS` / `WITHDRAWAL_REJECT` | Cash handed over; cancel |
| `POST /api/v1/admin/withdrawals/{id}/resolve`                           | `WITHDRAWAL_PROCESS` | Failed payment: `PAID` or `RETURNED`           |
| `GET`, `PUT /api/v1/admin/users/{id}/referral-mode`                     | `WITHDRAWAL_VIEW` / `_PROCESS` | An investor's mode; change on request |
| `GET`, `POST /api/v1/admin/withdrawal-runs?kind=RENT\|REFERRAL`         | `WITHDRAWAL_VIEW` / `_PROCESS` | Runs for network investors; online investors request their own |
| `GET`, `PUT /api/v1/admin/settings/withdrawals`                         | `WITHDRAWAL_VIEW` / `SYSTEM_SETTINGS_MANAGE` | Minimum, charges, tax, second-approval threshold, processing days |
| `GET`, `PUT /api/v1/admin/settings/rent-credit`                         | `FINANCE_VIEW` / `PAYOUT_PROCESS` | How often rent is credited (`intervalMinutes`) |
| `GET /api/v1/admin/withdrawal-batches[/{id}]`, `/{id}/files`            | `WITHDRAWAL_VIEW`    | Files with paid/failed/outstanding counts      |
| `GET /api/v1/admin/withdrawal-batches/{id}/file`, `/files/{fileId}`     | `WITHDRAWAL_PROCESS` | The Excel file, kept uploads (audited)         |
| `POST /api/v1/admin/withdrawal-batches/{id}/reconciliation` (multipart) | `WITHDRAWAL_PROCESS` | Upload the file back with Status per row       |
| `POST /…/withdrawal-batches/{id}/sent`, `/items/{wid}/paid` · `/failed`, `/{id}/settle` | `WITHDRAWAL_PROCESS` | Settle by hand |

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
  CLOSED`. A staff reply hands the ticket to the customer, and a customer reply puts it back in the queue. A resolved
  ticket can be reopened by its customer (with a reason, or by replying) for `app.support.auto-close-after` (24 hours),
  after which it closes by itself (checked every `app.support.auto-close-interval`, 15 minutes). Closed is final: it
  cannot be reopened or replied to; the customer opens a new request.
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
- **UI.** *Staff → New employee*. Investors and employees are listed apart: *Investors* and *Staff*. The user page shows roles, lets `USER_ROLE_ASSIGN` holders edit them, and offers
  *Issue temporary password* for staff. Everyone can change their password from *Profile → Password*.

### Departments, job roles & permissions

- **Departments** (`/api/v1/admin/departments`) organise staff. Assignment grants no permission, so authorization stays
  on roles alone. An employee has one primary department and any number of additional ones. Changes are kept in
  `department_history` (append-only) and audited. `DEPARTMENT_VIEW` alone shows only the departments you belong to;
  `DEPARTMENT_MANAGE` shows all. Investors cannot join, and inactive departments take no new employees.
- **Job roles** are ordinary, editable roles linked to a department. Each saved change adds a row to `role_revisions`
  (`GET /api/v1/admin/roles/{id}/revisions`). `SUPER_ADMIN` has `protected_role` set: it cannot be edited or deleted,
  and its last holder cannot lose it.
- **Old roles.** `V10` removed `ADMIN`, `ADMINISTRATION`, `FINANCE`, `REPORT_VIEWER`, `SALES`, `SUPPORT` and `VERIFICATION`,
  and stops if any account still holds one. `SUPER_ADMIN` and `INVESTOR` remain. Integration tests keep those names as
  test-only permission bundles in `src/test/resources/db/test-roles`.
- **Permissions** carry `sensitive` (the editor asks before adding one) and `reserved`. A reserved permission has no
  feature behind it yet: no endpoint checks it, so granting one changes nothing. `SUPER_ADMIN` does not hold them yet, which also
  keeps the access token small. The migration that adds such a feature clears the flag and grants the permission.
- **Menu.** `ADMIN_NAV` in `frontend/src/components/layout/admin-nav.ts` is the one registry of admin pages. It follows
  permissions for convenience only; the API enforces them.

### Approvals (maker-checker)

- `app.approvals.required` (`APP_APPROVALS_REQUIRED`; default `LEDGER_ADJUSTMENT,PAYMENT_REFUND`) lists operations whose
  direct endpoint is closed. It is deployment configuration, so nobody can switch off the second person from the admin panel.
- `POST /api/v1/admin/approvals` records the request; `POST .../{id}/approve` or `.../reject` decides it. The checker needs
  `APPROVAL_DECIDE` and the operation's own permission, and cannot be the maker (also a database constraint).
- A decision locks its row and runs the operation in the same transaction, so a request runs once. If the operation
  fails, the decision rolls back and the request stays pending.
- Withdrawals keep their existing dual approval.

### Reporting

- **One document model, three formats.** Every report is built once as a `ReportDocument` (title, subtitle, sections of
  four-column rows) and rendered as JSON (on-screen preview), CSV or PDF, so all formats always show the same figures.
  PDFs come from a single JasperReports template (`reports/document.jrxml`, compiled once) with fonts embedded.
- **Reports.** *Investor statement*: per currency, opening and closing earnings balance and every entry in the period,
  plus holdings and withdrawals requested. *Financial summary*: per currency, rent paid and capital returned to investors,
  referral commissions, adjustments, withdrawals requested, returned and paid,
  and what is owed / in transit at period end — straight from the ledger. *Plans & payouts*: units sold per
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

`format` is `json` (default for reports), `csv` or `pdf`; csv and pdf need `REPORT_GENERATE` on staff reports.

## Delivery phases

1. ✅ Project setup & base architecture
2. ✅ Authentication — registration, login, RS256 JWT, refresh-token rotation, roles & permissions
3. ✅ Users — profile, KYC, bank details
4. ✅ Plans — investment plans by tenure and unit
5. ✅ Orders, payments, investment confirmation, invoices (+ transactional outbox)
6. ✅ Earnings — leases per unit, monthly payouts (rent + capital back over the tenure), double-entry ledger
7. ✅ Referrals — four-level hierarchy, effective-dated rates, commissions on rent paid, downline tree
8. ✅ Withdrawals — reserved balances, single/dual approval, payout batches, bank file, reconciliation
9. ✅ Sales CRM — leads from staff and the website, pipeline, activity log, assignment, conversion to investors
10. ✅ Support — tickets, internal notes, attachments, response targets, in-app notifications
11. ✅ Reporting — statements, financial summary, plans & payouts, invoice PDFs (JasperReports), CSV exports, admin overview
12. Production hardening
