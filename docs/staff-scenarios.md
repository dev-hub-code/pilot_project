# SeaLease staff and investor test scenarios

A cast of employees, one or two per job role, and the work each of them does. Run the scenarios in order: each one
leaves data the next one needs, and together they cover every staff page and every investor workflow.

The roles and permissions here are the defaults the database is seeded with (`V9__departments_roles_and_permissions.sql`
and later migrations). Admins can change them on **Roles**, so if a step is refused, check the role first.

Every person and email below is made up. Use your own test addresses (Mailpit locally) when you create the accounts.

---

## 1. The cast

### Platform Administration

| Person | Role | What they can do |
|---|---|---|
| **Arjun Mehta** | `SUPER_ADMIN` (protected) | Everything enforced. The only role that can send announcements, manage demo accounts, configure support and write knowledge-base articles. |
| **Neha Kulkarni** | `PLATFORM_ADMIN` | Users, staff, roles (`ROLE_MANAGE`), role assignment, departments, system settings, terms, audit log |
| **Vikram Rao** | `SECURITY_ADMIN` | Audit log, security dashboard, view roles and staff, suspend accounts |

### Investor Relations

| Person | Role | What they can do |
|---|---|---|
| **Priya Nair** | `INVESTOR_RELATIONS_MANAGER` | View, update and suspend investors, see full phone numbers, create network investors, view orders |
| **Rahul Joshi** | `INVESTOR_RELATIONS_EXECUTIVE` | View investors and orders (read only) |
| **Sneha Patil** | `INVESTOR_ONBOARDING_EXECUTIVE` | View investors, create network investors |
| **Karan Shah** | `INVESTOR_RELATIONS_SUPERVISOR` | View and update investors, full phone numbers, view orders |

### Investment Operations

| Person | Role | What they can do |
|---|---|---|
| **Meera Iyer** | `INVESTMENT_MANAGER` | View, create and update investment products, view orders |
| **Rohan Desai** | `PRODUCT_EXECUTIVE` | View, create and update investment products |
| **Anjali Gupta** | `ORDER_EXECUTIVE` | View products and orders, browse all orders (`RECORD_BROWSE`) |
| **Siddharth Menon** | `AGREEMENT_EXECUTIVE` | View orders, write the investment agreement wording |
| **Kavita Reddy** | `INVESTMENT_APPROVER` | View products, approve investments |

### Finance

| Person | Role | What they can do |
|---|---|---|
| **Amit Verma** | `FINANCE_MANAGER` | Finance overview, ledger, payouts view, payment search, withdrawals view, company bank accounts, reports, finance-team support tickets |
| **Pooja Singh** | `PAYMENT_EXECUTIVE` | Find payments by exact search, confirm or reject bank transfers, view company bank accounts |
| **Rajesh Kumar** | `ACCOUNTS_EXECUTIVE` | Ledger, post adjustments (each one needs a second person) |
| **Divya Pillai** | `PAYOUT_EXECUTIVE` | See and pay investor payouts that have fallen due |
| **Manish Agarwal** | `WITHDRAWAL_EXECUTIVE` | Withdrawals, generate payout runs, finance tasks (mark paid or failed) |
| **Sunita Bose** | `FINANCE_APPROVER` | Ledger, adjustments, approvals queue (decide), approve or reject withdrawals |

### Compliance

| Person | Role | What they can do |
|---|---|---|
| **Farhan Sheikh** | `COMPLIANCE_MANAGER` | KYC review, bank account verification, view investors, audit log |
| **Lakshmi Krishnan** | `KYC_EXECUTIVE` | KYC review only |
| **Deepak Yadav** | `BANK_VERIFICATION_EXECUTIVE` | Bank account verification only |
| **Ritu Malhotra** | `COMPLIANCE_AUDITOR` | Audit log, reports (read only) |

### Customer Support

| Person | Role | What they can do |
|---|---|---|
| **Imran Khan** | `SUPPORT_MANAGER` | View and manage tickets, view investors |
| **Tanvi Jain** | `SUPPORT_EXECUTIVE` | View and manage tickets |
| **Nikhil Bhat** | `SENIOR_SUPPORT_EXECUTIVE` | View and manage tickets, find payments by exact search |

### Sales & CRM

| Person | Role | What they can do |
|---|---|---|
| **Aditya Chopra** | `SALES_MANAGER` | View, create, update and assign leads |
| **Shreya Das** | `SALES_EXECUTIVE` | View, create and update leads |
| **Gaurav Saxena** | `CRM_ADMINISTRATOR` | View, create, update and assign leads |

### Partner Operations

| Person | Role | What they can do |
|---|---|---|
| **Harish Pandey** | `PARTNER_MANAGER` | Finance overview, any investor's downline, move members to another referrer |
| **Nisha Thakur** | `REFERRAL_EXECUTIVE` | Finance overview (referral commissions) |
| **Varun Kapoor** | `REFERRAL_CONFIGURATION_MANAGER` | Schedule and cancel referral rates |

### Business Intelligence

| Person | Role | What they can do |
|---|---|---|
| **Swati Mishra** | `BI_MANAGER` | View and generate reports, statements, report history |
| **Rakesh Nanda** | `FINANCIAL_ANALYST` | Reports, finance overview |
| **Pallavi Ghosh** | `BUSINESS_ANALYST` | Reports (read only) |

### Investors (the customers in the story)

| Person | Type | How they arrive |
|---|---|---|
| **Suresh Patel** | Network investor | Created by Sneha (onboarding). Earns referral commission every month on four levels. Can pay in cash at the office. |
| **Asha Patel** | Network investor | Suresh's downline (level 1) |
| **Ravi Patel** | Network investor | Asha's downline (level 2 under Suresh) |
| **Zoya Ali** | Online investor | Signs up on the website. Pays online. Earns a one-time commission on the first order of each person she refers directly. |
| **Kabir Ali** | Online investor | Signs up through Zoya's referral link |
| **Demo Investor** | Network investor | Turned into a demo account by Arjun |

---

## 2. Setting up (Day 0)

### S0.1 Super admin prepares the platform (Arjun)

1. Sign in as the bootstrap super admin.
2. **Departments**: check that the nine seeded departments are active. Create a test department, then deactivate it.
3. **Roles**: open each department section and check the seeded roles and their permission counts. Create a custom
   role `FIELD_AGENT` (Sales & CRM: `LEAD_VIEW`, `LEAD_UPDATE`) and confirm it shows the "Custom" badge.
   Try to edit `SUPER_ADMIN`: it is protected and must refuse.
4. **Permissions**: filter by Sensitive and by Reserved, search "withdraw", and jump between categories.
5. **Staff Management → New employee**: create every employee in section 1. For each one, pick the role from its
   department tab. Copy or email the temporary password shown on the credentials card.
6. Sign in once as each employee. Each one must change the temporary password, and the menu shows only their pages.

7. **Staff → an employee** (Neha, `USER_ROLE_ASSIGN`): change Tanvi's roles from Support executive to Senior support
   executive. Her menu changes the next time she signs in, and the audit log has "Roles changed". Neha cannot change
   her own roles.
8. **Investor Management → an employee → Password** (Neha): issue a new temporary password for an employee who forgot
   theirs. It is emailed to them, and they must change it at their next sign-in.
9. **Departments → Finance** (Neha, `DEPARTMENT_MANAGE`):
   - **Assign an employee** to the department, then check they appear under its employees.
   - Set **Sunita** as **Department head**.
   - Edit the department's **Details**, then check the change in the department's history.
   - Check the department's roles list shows the six finance roles.

**Check:** a role chip and a department chip appear on each staff row. **Audit log → Access control** shows one
"Staff account created" and one "Roles changed" entry per employee.

### S0.2 Platform configuration (Neha, Arjun)

1. **Platform config → Overview**: every card shows live values. A card with an amber "Needs a look" edge
   (no API keys, no webhook secret, no active company account, no referral rates) must be fixed before going on.
2. **Company accounts** (Amit): add two company bank accounts. The IFSC fills in the bank name. Deactivate one, then
   reactivate it. With none active, a warning appears and investors cannot pay by bank.
3. **Returns credit** (Neha): pick an interval (for example every hour) and save. The banner shows the new interval
   and "Last changed … by Neha".
4. **Withdrawals** (Manish): set the minimum, flat and % charges, TDS, the second-approver threshold and the
   processing days shown to investors.
5. **Referral rates** (Varun): schedule levels 1 to 4 and the online one-time rate, starting now. Schedule a second
   change for next week, then cancel it. The history shows one "In force" and one "Cancelled".
6. **Payment gateway** (Neha): check the key and webhook status, turn online payments on, set the checkout limit and
   the holds.
7. **Terms** (Neha): save a new version of the terms. The version number goes up, and "View page" opens `/terms`.
8. **Agreement** (Siddharth): edit the investment agreement wording using the placeholders, then download the sample PDF.

**Negative checks:** Rahul (read-only investor relations) cannot see Platform config at all. Amit can view the
returns interval but not change it.

---

## 3. Products and onboarding (Day 1)

### S1.1 Create and publish an investment product (Rohan, Meera, Kavita)

1. Rohan creates a plan: who it is offered to (online or network investors), name, summary, tenure in years, annual
   return %, unit price and minimum units. It starts as **Draft**.
2. Meera reviews the draft and edits the return % with **Edit draft**. Only drafts can be edited.
3. Kavita (`INVESTMENT_APPROVE`) opens the plan and clicks **Publish plan** (**Open**). The terms become binding.
   Rohan and Meera do not see the Publish button.
4. An online plan appears on the landing page carousel and on `/plans`, and **View as investor** opens it as an
   investor sees it. A network plan is offered only to network investors.

**Check:** the Investment Products list shows the plan as Open. The audit log has "Product created", then
"Product updated", then "Product published".

### S1.1b Plan life cycle (Rohan, Kavita)

1. Rohan creates a second draft. Kavita **cancels** it with a reason. It shows as **Cancelled**, with the reason under
   Terms, and cannot be published.
2. After S3, Kavita **closes** the first plan to new investors (**Closed**). Investors who already invested keep their
   term and keep earning returns. New investors can no longer order it.
3. Kavita **reopens** the closed plan with **Reopen plan** (**Open** again, on the same terms). New investors can
   order it again. Rohan and Meera do not see the button.

### S1.2 Invite and create a network investor (Sneha, Priya)

1. Sneha uses **Invite Investor** to send Suresh an invitation. Suresh opens the email and accepts the invitation,
   setting his password.
2. Sneha creates Asha as a network investor with Suresh as her referrer, then Ravi with Asha as his referrer.
3. Priya opens Suresh in **Investor Management**. She sees his full phone number (`PHONE_NUMBER_VIEW`) and updates
   his address. Rahul opening the same page sees only the last four digits of the phone number, and no edit buttons.

### S1.3 Online sign-up through a referral (Zoya, Kabir)

1. Zoya registers on the website, accepts the terms, verifies her email with the code, and signs in.
2. Zoya copies her referral link from her account. Kabir opens it and registers.
3. **Referral Commissions** (Nisha) shows Zoya with Kabir as a direct referral on the online scheme.

**Negative check:** Zoya mistypes her password at sign-in five times. The account is locked, Vikram sees it on
**Security → Locked accounts**, and it unlocks by itself at the time shown.

### S1.4 A lead becomes an investor (Shreya, Aditya, Gaurav)

1. A website enquiry arrives as a **New** lead (source: Website). Shreya creates another lead by hand (source: Staff).
2. Aditya assigns both leads to Shreya. Gaurav reassigns one of them to himself.
3. Shreya logs a call, sets a follow-up for tomorrow and moves the lead through the stages:
   **Contacted → Qualified → Proposal**. The next day, the overdue follow-up chip shows on the Leads list.
4. The lead signs up, and Shreya marks it **Won**. The other lead is marked **Lost** with a reason.

**Check:** the stage cards show the counts moving. The "Mine" and "Due" filters work.

---

## 4. Verification (Day 1 to 2)

### S2.1 KYC (Zoya, Lakshmi, Farhan)

1. Zoya opens **Profile → Verification**. She uploads her signature first and leaves. The submission is saved as a
   **Draft** (a draft may start with just the signature). She comes back, fills in her personal details, uploads PAN
   and Aadhaar ("3 of 3 uploaded"), and submits (**Pending**).
   - Try a 3 MB image first: the upload is refused with the 2 MB limit, and **everything she typed and picked stays
     filled in**.
2. Lakshmi opens **KYC & Bank Verification** and **takes** the submission from the Pending tab, so nobody else works
   on it at the same time ("KYC review taken" in the audit log). She opens each document and **rejects** it:
   "Aadhaar photo is blurred".
3. Zoya sees the rejection reason, replaces the Aadhaar image, and resubmits.
4. Lakshmi approves. If the name on the documents does not match the account name, a mismatch card warns her first.
5. Farhan checks the Approved tab and returns one of his own taken reviews to the queue.

**Check:** **Security → Staff access to personal data** counts Lakshmi's opened KYC documents. The audit log has
"KYC submission opened", "KYC rejected", "KYC submitted" and "KYC approved".

### S2.2 Bank account (Zoya, Deepak)

1. Zoya adds a bank account with a passbook or cheque photo as proof.
2. Deepak opens it from the bank-to-verify chip on the verification page, checks the holder name, number and IFSC
   against the proof, and **verifies** it.
3. Zoya adds a second account, and Deepak **rejects** it with a reason. Zoya then requests a change of her primary
   account. It goes back to Deepak, who verifies the new account.

**Negative check:** Lakshmi (KYC only) cannot open bank verification, and Deepak cannot decide KYC.

Repeat S2.1 and S2.2 for Suresh, Asha, Ravi and Kabir so that every investor can withdraw later.

---

## 5. Orders and payments (Day 2 to 3)

### S3.1 Online payment (Zoya)

1. Zoya opens **Invest**, picks the plan and a quantity, and pays through Razorpay checkout.
2. The order goes from **Pending payment** to **Confirmed** when the payment succeeds, and an invoice is issued.
3. The investment agreement is issued. Zoya reads and **accepts** it, and the holding goes from **Awaiting acceptance**
   to **Active**. The term starts at the full hour after she accepts.

### S3.1b Bank transfer into a virtual account (Zoya, Neha)

Needs Smart Collect activated on the Razorpay account and **Bank transfer into a Razorpay virtual account** switched
on in Platform config → Payment gateway.

1. Zoya orders and chooses bank transfer. She is shown her own virtual account number and IFSC, and the order is held
   for the bank-transfer hold (in hours).
2. She transfers the amount from her bank. Razorpay's signed notification arrives, the payment is matched to the
   order without anyone confirming it, and the order becomes **Confirmed**.
3. She transfers less than the order total on a second order. Check that the order is **not** confirmed and that
   finance can see the short payment (on Payments or Unmatched Receipts). Note what happens: this case is not
   documented in the code comments, so record the actual behaviour.
4. Neha switches the virtual account option off. New orders no longer offer it, and orders already started still
   complete.

### S3.1c Gateway money with no matching order (Pooja)

1. A Razorpay payment arrives for an order or virtual account that is not ours (for example a stale checkout).
   It is recorded once on **Unmatched Receipts**, even if Razorpay sends the notification again ("Payment
   unmatched received" in the audit log).
2. Pooja traces it and resolves it: matched to an order, or marked for refund.

### S3.2 Bank transfer (Kabir, Pooja)

1. Kabir orders and chooses bank transfer. He picks one of the active company accounts and enters his transaction ID.
2. Pooja searches for the order number in **Payments**. She can find it only by an exact search, not by browsing.
   She compares the amount with the bank statement and **confirms** the payment.
3. Kabir places a second order and enters a wrong transaction ID. Pooja **rejects** that payment with a reason.

**Negative checks** when Pooja confirms a transfer:
- She enters an amount received that differs from the amount due. It is refused: partial or excess transfers must
  be returned to the investor.
- She enters a bank reference already recorded for another payment. It is refused, naming the other payment.
- Rahul (no `PAYMENT_CONFIRM`) cannot open Payments at all.
4. Zoya's first order earns her the one-time online referral commission for Kabir (**Referral Commissions**).

### S3.3 Cash at the office (Suresh, Asha, Ravi)

1. Suresh, Asha and Ravi place orders and pay in cash at the office. Pooja confirms each payment.
2. Each one accepts their agreement, and their holdings become Active.

### S3.4 Order that expires (Kabir)

1. Kabir places an order and does not pay before the hold ends (set in Platform config → Payment gateway). The order
   becomes **Expired** and no longer reserves anything on the plan.

### S3.5 Unmatched receipt (Pooja, Amit)

1. A bank credit arrives that matches no order. It appears on **Unmatched Receipts**.
2. Pooja matches it to the right order, or Amit marks it resolved with a note.

### S3.6 Refund that needs a second person (Pooja, Sunita)

1. A payment that was taken twice is marked "refund required". A refund request is created.
2. The request appears on **Approvals → Pending** as a "Payment refund". Whoever made the request sees
   "You made this request, so someone else must decide it".
3. Sunita approves it ("Approve and run"), so the refund runs. A second refund request is rejected with a reason,
   and the reason shows on the Rejected tab.

### S3.7 Orders desk (Anjali, Siddharth, Rahul)

1. Anjali browses all orders, filters by status (including Cancelled), and opens an order and its invoice.
2. Siddharth opens a confirmed order and checks the agreement issued and accepted.
3. Rahul can view orders but not change anything.

---

## 6. Earnings and referrals (Day 3 to 30)

### S4.1 Returns credited (Zoya, Suresh)

1. When each interval ends, returns are credited to the investor's wallet (see S0.2 step 3).
2. Zoya's **Dashboard** and **Earnings** pages show the returns earned, upcoming returns and term progress.
3. **Portfolio** lists each holding with its term. **Orders** lists each order with its invoice.

### S4.2 Network commission (Suresh, Harish, Nisha)

1. As Asha and Ravi earn returns, Suresh earns level 1 and level 2 commission every term month.
2. Nisha checks **Referral Commissions**: the in-force rates are shown, and the commission list includes Suresh.
3. Harish opens **Referral Management**, finds Suresh, and drags Ravi to sit directly under Suresh.
   The tree updates, and the audit log has "Referrer changed".

### S4.3 Monthly payouts (Divya, Amit)

1. Payouts that have fallen due show on **Investor Payouts** (a green or amber panel showing what is due).
2. Divya pays them. Amit can view them but not pay.

### S4.3b A holding reaches maturity (Suresh, Divya)

Use a plan with a one-year tenure, or a demo account, to reach the end of a term.

1. At the end of the term, the last payout falls due, including the capital (the Capital column shows on **Investor
   Payouts** only when a payout includes capital).
2. Divya pays it. The holding becomes **Matured** ("Holding matured" in the audit log).
3. Suresh's **Portfolio** shows the holding as matured, with no more returns to come.

### S4.4 Reinvest from balance (Zoya)

1. Zoya uses the reinvest button on her dashboard to place a new order paid from her wallet balance.

---

## 7. Withdrawals (Day 30)

### S5.1 Online withdrawal request (Zoya, Manish, Sunita)

1. Zoya requests a withdrawal of her returns. Charges and TDS are shown before she confirms.
   - Try an amount below the minimum: it is refused.
2. Manish opens **Withdrawals → Submitted**, starts a review (**Under review**), puts it **On hold**, and asks Zoya
   for information.
3. Zoya provides the information, and Manish resumes the review.
4. Sunita **approves** it. If the amount is above the second-approver threshold, it waits as **Pending approval**
   until a second, different approver also approves.
5. Manish batches it. The withdrawal becomes **Batched** and appears on **Finance Tasks**.
6. Manish initiates the payment (**Processing**), then marks it **Paid**. A second test withdrawal is marked
   **Failed** with a reason, and a third is marked **Returned** by the bank.
7. Zoya sees each status change and downloads her receipt.

**Negative checks:** Sunita rejects one request with a reason, and Zoya cancels another while it is still Submitted.

### S5.1b How a network investor is paid (Suresh, Asha, Manish)

1. Asha has no verified bank account and has not chosen a payout method, so by default she is paid **in cash** at the
   office. Suresh, who has a verified primary account, is paid **by bank** by default.
2. Suresh opens **Withdrawals** on his account and chooses cash at the office ("Referral payout mode chosen" in the
   audit log). The choice applies to withdrawals made from then on.
3. Manish (`WITHDRAWAL_PROCESS`) opens Asha in **Investor Management → Withdrawals** and sets her to bank on her
   behalf, after her bank account is verified.
4. Zoya (online) sees that she is always paid by bank and cannot change it. Staff cannot change it for her either.

### S5.1c Payout provider notification (Manish, Amit)

1. Manish initiates a withdrawal payment through the payout provider (**Processing**).
2. The provider's signed notification arrives and settles it as **Paid** ("Payout webhook received" in the audit
   log). Nothing is marked paid on a staff member's word alone: only a verified notification, a reconciled bank
   sheet, or the bank reference entered by finance does that.
3. A notification that is unclear (still processing, unknown, or with an amount that does not match) is recorded and
   left for Amit to reconcile. It is never retried automatically, so nothing is paid twice.

### S5.2 Network payout run (Manish, Amit)

1. Manish opens **Platform config → Withdrawals** and clicks **Generate returns payout**. Every eligible network
   investor's returns move into withdrawal in progress. Bank payees go on a bank sheet, and cash payees go on a cash
   batch handed over at the office.
2. He runs **Generate referral payout** separately.
3. The run appears in **Runs**, with the bank sheet, the cash batch and "N left out" (for example an investor
   without a verified bank account), each with a reason.
4. Under **Withdrawals → Withdrawal files**, Amit downloads the Excel bank sheet to pay from.
5. Manish works **Finance Tasks**: Open → mark each one paid, or failed with the reason. Done shows the outcomes,
   and **All** shows both.

---

## 8. Ledger and reconciliation (Day 30)

### S6.1 Ledger adjustment (Rajesh, Sunita)

1. Rajesh opens **Financial Ledger**. He checks that the trial balance balances and filters by Investors, Platform,
   and Cash and deposits.
2. He posts an adjustment to correct a wrong credit. It goes to **Approvals** as a "Ledger adjustment".
3. Rajesh cannot approve his own request. Sunita approves it, and the entry posts.

### S6.2 Reconciliation (Amit)

1. Amit opens **Reconciliation**. Sections with no issues are compact, and sections with issues have an amber edge
   and action links. He follows each link and clears the issue.

---

## 9. Support (any day)

### S7.1 Ticket life cycle (Kabir, Tanvi, Nikhil, Imran)

1. Kabir opens a ticket, **Payments → Payment pending**, with a screenshot attached.
2. Tanvi takes it (**Open → In progress**), replies, and asks for the transaction ID (**Waiting on customer**).
3. Kabir replies, and the ticket goes back to In progress. Tanvi adds an internal note that Kabir cannot see.
4. Tanvi escalates it to Nikhil (**Escalated**). Nikhil finds the payment by exact search and hands the ticket to the
   finance team (**Waiting on internal**). Amit, who holds `FINANCE_TICKET_HANDLE`, picks it up.
5. The ticket is **Resolved**. Kabir rates it, and the star rating shows on the ticket.
6. Kabir reopens it (**Reopened**), and Tanvi resolves it again and then **closes** it.

**Check:** the SLA bar on the ticket shows the time left. Imran uses the queue tabs, search, "More filters" and the
bulk actions to reassign or close several tickets at once.

### S7.2 Support configuration (Arjun)

These permissions are given to no role by default. Give them to Imran through a custom role, or test them as Arjun.

1. **Support Settings** (`SUPPORT_CONFIG_MANAGE`): SLA policies, business hours, categories, auto-assignment and
   escalation.
2. **Knowledge Base** (`KNOWLEDGE_BASE_MANAGE`): write an article, publish it, then archive it. Kabir finds it when he
   searches before opening a ticket.
3. **Support dashboard and feedback** (`SUPPORT_ANALYTICS_VIEW`): change the period and check the ticket volumes and
   ratings.

### S7.3 Announcements (Arjun)

1. Send an announcement to each audience in turn: everyone, investors, online investors, network investors, and
   staff. The "Before you send" card is shown. The right people see each announcement in their notifications.

---

## 10. Reports (Day 31)

### S8.1 Reports and statements (Swati, Rakesh, Pallavi, Ritu)

1. Swati opens **Reports**. She changes the period (pill presets or dates), opens a report, and changes its filters,
   columns and page. She downloads it as CSV and as Excel or PDF.
2. Under **Documents**, she downloads an investor's statement.
3. **History** lists every download, with who made it and when.
4. Rakesh and Pallavi can view reports. Pallavi cannot download (no `REPORT_GENERATE`).
5. Ritu (auditor) can view reports and the audit log, and nothing else.

**Check:** each download counts under **Security → Staff access to personal data → Reports**.

---

## 11. Security and audit (every day)

### S9.1 Security dashboard (Vikram)

1. Vikram opens **Security** and switches between 24 hours, 7 days, 30 days and 90 days.
2. The banner shows "All quiet", or lists the issues: failed sign-ins (amber), locked accounts or stolen-token
   alerts (red).
3. He opens the failed sign-ins by account and by IP, the locked accounts, the staff access to personal data, the
   access and settings changes, and the security events.
4. He suspends a staff account that should not be signing in, then Neha reactivates it.

### S9.2 Audit log (Farhan, Ritu)

1. They filter by category, by who did it, by action, by IP, by record and by dates. Each active filter shows as a
   chip, and removing a chip removes that filter.
2. They open an entry to see the old and new values, the IP and the device.
3. Farhan downloads the filtered log as CSV. The download itself is audited ("Audit log exported").

---

## 12. Demo accounts (Arjun)

1. **Demo accounts**: find "Demo Investor" and make them a demo account with a one-year time frame.
2. Add two dummy orders of real plans, three dummy downline members on two levels, and one order for a member.
3. Check the month-by-month figures and the payouts list. Sign in as the demo investor: their pages show the dummy
   figures.
4. Change the time frame, remove a member (which also removes everyone below them), then **Stop the demo**. Their
   pages show their real figures again.

**Check:** the network payout run in S5.2 leaves demo accounts out.

---

## 13. Account housekeeping (any day)

1. **Forgot password** (Kabir): request a reset, open the email, and set a new password. Vikram sees
   "Password reset" on the security dashboard.
2. **Change password** (any staff member): change it from the profile.
3. **Sign out everywhere** (Zoya): every other session ends.
4. **Disable and enable** (Neha): disable a user who has left, then enable them again.
5. **Profile** (Suresh): update his profile and tax information. Only Priya and Karan can edit his details from the
   staff side.

---

## 14. Coverage: page → scenario

| Page | Scenarios |
|---|---|
| Dashboard (staff) | every scenario, menu per role in S0.1 |
| Invite Investor | S1.2 |
| Investor Management (incl. payout method, staff password) | S0.1, S1.2, S5.1b, S9.1, section 13 |
| Investment Products | S1.1, S1.1b |
| Orders & Agreements | S3.1 to S3.7, S4.3b |
| Payments, Unmatched Receipts | S3.1b, S3.1c, S3.2, S3.3, S3.5, S3.6 |
| Investor Payouts | S4.3, S4.3b |
| Withdrawals, Withdrawal files | S5.1, S5.1b, S5.1c, S5.2 |
| Finance Tasks | S5.1, S5.2 |
| Financial Ledger | S6.1 |
| Reconciliation | S6.2 |
| KYC & Bank Verification | S2.1, S2.2 |
| CRM & Leads | S1.4 |
| Support Tickets, Knowledge Base, Support Settings | S7.1, S7.2 |
| Announcements | S7.3 |
| Referral Management, Referral Commissions | S1.3, S4.2 |
| Reports | S8.1 |
| Staff Management (list, new, employee page), Departments (list, department page), Roles, Permissions | S0.1 |
| Approvals | S3.6, S6.1 |
| Demo Accounts | section 12 |
| Platform config (all tabs, Terms, Agreement) | S0.2, S5.2 |
| Security Dashboard, Audit Logs | S9.1, S9.2 |
| Investor pages (dashboard, invest, portfolio, earnings, orders, profile, verification, support) | S1.3, S2.1, S3.1, S4.1, S4.4, S5.1, S7.1 |

---

## 15. Gaps to know before testing

- **Only the super admin** holds `ANNOUNCEMENT_MANAGE`, `DEMO_ACCOUNT_MANAGE`, `SUPPORT_CONFIG_MANAGE`,
  `KNOWLEDGE_BASE_MANAGE` and `SUPPORT_ANALYTICS_VIEW` by default. To test them as another employee, give them a
  custom role first.
- **Publishing, closing and cancelling a plan** need `INVESTMENT_APPROVE` (Kavita). The product team can create
  and edit drafts but not publish them.
- **Reserved permissions** (shown as "Reserved" on Permissions) have no feature behind them yet. Granting them
  changes nothing, so they need no scenario.
- **Retired actions.** The audit log filter still lists these for old entries, but no feature records them any
  more, so they need no scenario: plan photos added or removed, rent rate tiers, minimum order, investor classified,
  agreement review assigned, returned or completed, product lease activated, product matured, rental recorded,
  distributed or rejected, payout day changed and withdrawal day changed.
- **Two-person rules:** the person who requests a ledger adjustment or refund can never decide it. Withdrawals above
  the threshold need two different approvers. Each test needs at least two finance people signed in.
