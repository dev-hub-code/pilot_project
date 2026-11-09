import type { Metadata } from "next";
import type { DepartmentSummary } from "@/types/admin-portal";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import {
  disableUserAction,
  enableUserAction,
  reactivateUserAction,
  assignBankAccountAction,
  rejectBankAccountAction,
  unassignBankAccountAction,
  suspendUserAction,
  verifyBankAccountAction,
} from "@/features/admin/actions";
import { ConfirmForm, ReasonForm } from "@/features/admin/decision-forms";
import { ReviewAssignment } from "@/features/admin/review-assignment";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { ReferralModeAdminForm } from "@/features/admin/withdrawal-forms";
import type { ReferralPayoutMode } from "@/types/withdrawal";
import { ResetPasswordForm, RolesForm } from "@/features/admin/staff-forms";
import type { PageResponse } from "@/types/api";
import type { Role } from "@/types/auth";
import type { AdminDownline, AdminReferralView } from "@/types/referral";
import { DownlineView } from "@/features/admin/downline-view";
import type { UserAuthorities } from "@/types/staff";
import { formatMoney } from "@/utils/money";
import type { AdminBankAccount, AdminUserDetail, KycSubmission } from "@/types/user";
import { kycNumbers } from "@/features/profile/kyc-labels";
import { formatDate, formatDateTime, formatDayMonthYear, humanize } from "@/utils/format";
import { EmailText } from "@/components/ui/email-text";
import { formatPhone } from "@/lib/phone";
import {
  AlertTriangle, CalendarDays, Clock, Flag, Globe, Landmark, LogIn, Mail, MapPin, Phone, ShieldAlert, UserRound,
} from "lucide-react";

export const metadata: Metadata = { title: "User" };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default async function UserDetailPage({ params, searchParams }: PageProps<"/platform/users/[id]">) {
  const { id } = await params;
  // Opened from bank verification: a focused view without account management (roles, referrals, suspension).
  const fromBankVerification = (await searchParams).from === "bank-verification";
  if (!UUID.test(id)) notFound();
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);

  let user: AdminUserDetail;
  try {
    user = await authFetch<AdminUserDetail>(`/api/v1/admin/users/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const [kyc, banks, referral, downline] = await Promise.all([
    can(Permission.KYC_REVIEW) ? authFetch<KycSubmission[]>(`/api/v1/admin/users/${id}/kyc`) : null,
    can(Permission.BANK_ACCOUNT_VERIFY) || can(Permission.FINANCE_VIEW)
      ? authFetch<AdminBankAccount[]>(`/api/v1/admin/users/${id}/bank-accounts`)
      : null,
    authFetch<AdminReferralView>(`/api/v1/admin/users/${id}/referrals`),
    fromBankVerification ? null : authFetch<AdminDownline>(`/api/v1/admin/users/${id}/downline`),
  ]);
  const [authorities, allRoles, departments] = can(Permission.ROLE_VIEW)
    ? await Promise.all([
      authFetch<UserAuthorities>(`/api/v1/admin/users/${id}/roles`),
      authFetch<PageResponse<Role>>("/api/v1/admin/roles?size=100"),
      // Only to group the roles in the roles form.
      can(Permission.DEPARTMENT_VIEW) || can(Permission.DEPARTMENT_MANAGE)
        ? authFetch<DepartmentSummary[]>("/api/v1/admin/departments").catch(() => [] as DepartmentSummary[])
        : Promise.resolve([] as DepartmentSummary[]),
    ])
    : [null, null, [] as DepartmentSummary[]];
  const isStaffAccount = authorities?.permissions.some((p) => p !== Permission.INVESTOR_PORTAL) ?? false;
  // Withdrawals: how returns and referral earnings are paid.
  const showWithdrawals = !fromBankVerification && !isStaffAccount && can(Permission.WITHDRAWAL_VIEW) && session.userId !== id;
  const referralMode = showWithdrawals ? await authFetch<ReferralPayoutMode>(`/api/v1/admin/users/${id}/referral-mode`) : null;
  const { summary, profile } = user;
  const isSelf = session.userId === id;

  return (
    <div className="space-y-6">
      {/* ------------------------------------------------------------------ banner */}
      <section aria-labelledby="user-title" className="relative isolate overflow-hidden rounded-3xl bg-ink text-on-ink shadow-xl shadow-ink/20">
        <div aria-hidden="true" className="absolute inset-0 -z-10 bg-[linear-gradient(120deg,var(--ink)_0%,var(--ink)_55%,color-mix(in_oklab,var(--ink)_70%,var(--gold))_100%)]" />
        <div aria-hidden="true" className="absolute -top-24 -right-24 -z-10 size-72 rounded-full bg-gold/25 blur-3xl" />
        <div className="flex flex-wrap items-center gap-5 p-6 sm:p-8">
          <span aria-hidden="true"
            className="flex size-16 shrink-0 items-center justify-center rounded-2xl bg-[linear-gradient(140deg,var(--gold),var(--gold-on-ink))] font-display text-2xl font-semibold text-on-gold shadow-lg ring-4 ring-on-ink/15 sm:size-20 sm:text-3xl">
            {(summary.firstName.charAt(0) + summary.lastName.charAt(0)).toUpperCase() || "?"}
          </span>
          <div className="min-w-0 flex-1 space-y-2">
            <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
              <h1 id="user-title" className="text-2xl font-semibold tracking-tight sm:text-3xl">{summary.firstName} {summary.lastName}</h1>
              <span className="font-mono text-sm text-on-ink/60">{summary.userCode}</span>
            </div>
            <p className="flex min-w-0 items-center gap-1.5 text-sm text-on-ink/70">
              <Mail aria-hidden="true" className="size-4 shrink-0" /><span className="truncate"><EmailText>{summary.email}</EmailText></span>
            </p>
            <div className="flex flex-wrap gap-2">
              <StatusBadge tone={toneFor(summary.status)}>{humanize(summary.status)}</StatusBadge>
              {!summary.emailVerifiedAt && <StatusBadge tone="warning">Email not verified</StatusBadge>}
              {summary.investorType && (
                <StatusBadge tone="neutral">{humanize(summary.investorType)} investor</StatusBadge>
              )}
              <StatusBadge tone={toneFor(summary.kycStatus)}>KYC {humanize(summary.kycStatus).toLowerCase()}</StatusBadge>
            </div>
          </div>
          <dl className="grid w-full grid-cols-2 gap-2 sm:w-auto sm:gap-3">
            <div className="rounded-2xl border border-on-ink/15 bg-on-ink/[0.08] px-4 py-3 backdrop-blur-md">
              <dt className="flex items-center gap-1.5 text-[11px] text-on-ink/60"><CalendarDays aria-hidden="true" className="size-3.5" /> Joined</dt>
              <dd className="mt-1 text-sm font-semibold">{formatDate(summary.createdAt)}</dd>
            </div>
            <div className="rounded-2xl border border-on-ink/15 bg-on-ink/[0.08] px-4 py-3 backdrop-blur-md">
              <dt className="flex items-center gap-1.5 text-[11px] text-on-ink/60"><LogIn aria-hidden="true" className="size-3.5" /> Last sign-in</dt>
              <dd className="mt-1 text-sm font-semibold">{summary.lastLoginAt ? formatDate(summary.lastLoginAt) : "Never"}</dd>
            </div>
          </dl>
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="min-w-0 space-y-6 lg:col-span-2">
          <Card title="Account">
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <Item label="Email" icon={<Mail />}>
                <EmailText>{summary.email}</EmailText>
                <span className="block text-xs text-muted">
                  {summary.emailVerifiedAt ? `Verified ${formatDateTime(summary.emailVerifiedAt)}` : "Not verified yet"}
                </span>
              </Item>
              <Item label="Phone" icon={<Phone />}>{profile.phone ? formatPhone(profile.phone) : "—"}</Item>
              <Item label="Date of birth" icon={<CalendarDays />}>{formatDayMonthYear(profile.dateOfBirth) || "—"}</Item>
              <Item label="Nationality" icon={<Flag />}>{profile.nationality ?? "—"}</Item>
              <Item label="Address" icon={<MapPin />}>
                {[profile.address.line1, profile.address.city, profile.address.postalCode, profile.address.country]
                  .filter(Boolean).join(", ") || "—"}
              </Item>
              <Item label="Joined" icon={<UserRound />}>{formatDateTime(summary.createdAt)}</Item>
              <Item label="Last sign-in" icon={<Clock />}>{formatDateTime(summary.lastLoginAt)}</Item>
              {user.statusReason && (
                <Item label="Status reason" icon={<AlertTriangle />}>{user.statusReason} ({formatDateTime(user.statusChangedAt)})</Item>
              )}
            </dl>
          </Card>

          {kyc && (
            <Card title="Identity verification history">
              {kyc.length === 0 ? (
                <p className="text-sm text-muted">No submissions.</p>
              ) : (
                <ul className="divide-y divide-border text-sm">
                  {kyc.map((s) => (
                    <li key={s.id} className="-mx-2 flex flex-wrap items-center justify-between gap-2 rounded-lg px-2 py-2.5 transition-colors hover:bg-background">
                      <Link href={`/platform/kyc/${s.id}`} className="text-brand hover:underline">
                        {kycNumbers(s)}
                      </Link>
                      <span className="text-muted">{formatDateTime(s.submittedAt)}</span>
                      <StatusBadge tone={toneFor(s.status)}>{humanize(s.status)}</StatusBadge>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          )}

          {banks && (
            <Card title="Bank accounts">
              {banks.length === 0 ? (
                <p className="text-sm text-muted">No bank accounts.</p>
              ) : (
                <ul className="space-y-4">
                  {banks.map(({ account, otherUsersWithSameAccount, assignee, ifsc, accountNumber }) => (
                    <li key={account.id} className="space-y-3 rounded-xl border border-border bg-background/40 p-4 transition-colors hover:border-gold/30">
                      <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
                        <span className="flex items-center gap-3 font-medium">
                          <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-gold/10 text-gold-text"><Landmark className="size-4" /></span>
                          <span>
                          {account.bankName} <span className="font-mono text-base font-bold tracking-wide">{accountNumber ?? account.accountNumberMasked}</span> ·{" "}
                          {account.accountHolderName}
                          </span>
                        </span>
                        <span className="flex gap-2">
                          {account.primary && <StatusBadge tone="success">Primary</StatusBadge>}
                          <StatusBadge tone={toneFor(account.status)}>{humanize(account.status)}</StatusBadge>
                        </span>
                      </div>
                      <p className="text-sm"><span className="text-muted">IFSC</span> <span className="font-mono">{ifsc.code}</span></p>
                      {otherUsersWithSameAccount > 0 && (
                        <p className="flex items-center gap-2 rounded-lg bg-rose-50 px-3 py-2 text-sm font-medium text-rose-700 dark:bg-rose-500/10 dark:text-rose-300">
                          <AlertTriangle aria-hidden="true" className="size-4 shrink-0" />
                          Warning: this account is also registered by {otherUsersWithSameAccount} other user(s).
                        </p>
                      )}
                      {account.status === "PENDING_VERIFICATION" && can(Permission.BANK_ACCOUNT_VERIFY) && !isSelf && (
                        <ReviewAssignment assignee={assignee} viewerId={session.userId}
                          assign={assignBankAccountAction.bind(null, account.id)}
                          unassign={unassignBankAccountAction.bind(null, account.id)}>
                          <div className="grid gap-4 sm:grid-cols-2">
                            <ConfirmForm action={verifyBankAccountAction.bind(null, account.id)} submitLabel="Verify"
                              confirm={`Verify ${account.bankName ?? "Account"} ${accountNumber ?? account.accountNumberMasked} for payouts?`} />
                            <ReasonForm action={rejectBankAccountAction.bind(null, account.id)} label="Rejection reason"
                              submitLabel="Reject" />
                          </div>
                        </ReviewAssignment>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          )}

          {authorities && !fromBankVerification && (
            <Card title="Roles" description={authorities.roles.length === 0 ? "No roles." : authorities.roles.join(", ")}>
              {/* Roles are changed only for employees; an investor's roles are shown, not edited. */}
              {isStaffAccount && can(Permission.USER_ROLE_ASSIGN) && !isSelf && allRoles ? (
                <RolesForm userId={id} roles={allRoles.content} current={authorities.roles} departments={departments.filter((d) => d.active)} />
              ) : (
                <p className="text-sm text-muted">{authorities.permissions.length} permission(s).</p>
              )}
            </Card>
          )}

          {!fromBankVerification && (
          <Card title="Referrals" description={referral.code ? `Referral code ${referral.code}` : "Has not opened their referral page yet."}>
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <Item label="Referral scheme" icon={<Globe />}>
                {referral.scheme === "NETWORK"
                  ? `Network: monthly on four levels${referral.signupsJoinNetwork ? "; their signups join the network" : ""}`
                  : "Online: once on level 1"}
              </Item>
              <Item label="Referred by" icon={<UserRound />}>
                {referral.uplines.length === 0 ? "—" : (
                  <ol className="space-y-1">
                    {referral.uplines.map((u) => (
                      <li key={u.userId}>
                        <span className="text-muted">L{u.level}</span>{" "}
                        <Link href={`/platform/users/${u.userId}`} className="inline-flex min-h-6 items-center text-gold-text hover:underline">{u.name}</Link>
                        <span className="block text-xs text-muted"><EmailText>{u.email}</EmailText></span>
                      </li>
                    ))}
                  </ol>
                )}
              </Item>
              <Item label="Downline (levels 1–4)" icon={<Flag />}>{referral.downlineSize.join(" / ")}</Item>
              <Item label="Commission earned" icon={<Landmark />}>
                {referral.totalEarned.length === 0 ? "—" : referral.totalEarned.map((m) => formatMoney(m)).join(" · ")}
              </Item>
            </dl>
            {downline && (
              <div className="mt-5 space-y-2 border-t border-border pt-4">
                <h3 className="text-xs font-medium tracking-[0.08em] text-muted uppercase">
                  Downline · {downline.members.length} {downline.members.length === 1 ? "member" : "members"}
                </h3>
                <DownlineView downline={downline} />
              </div>
            )}
          </Card>
          )}
        </div>

        <div className="space-y-6">
          {referralMode && (
            <Card title="Withdrawals" description="Online investors are always paid by bank. Network investors are paid by bank sheet or in cash at the office, as they choose; cash is marked collected by hand.">
              <div className="space-y-5 text-sm">
                <div className="space-y-2">
                  <p>
                    Returns and referral earnings:{" "}
                    <span className="font-medium">{referralMode.mode === "OFFICE_CASH" ? "cash" : "into the bank account"}</span>
                    <span className="text-muted">{!referralMode.changeable ? " (online investors are always paid by bank)" : referralMode.chosen ? " (chosen by the investor)" : " (default; not chosen yet)"}</span>
                  </p>
                  {referralMode.changeable && can(Permission.WITHDRAWAL_PROCESS) && <ReferralModeAdminForm userId={id} mode={referralMode.mode} />}
                </div>
              </div>
            </Card>
          )}
          {isStaffAccount && can(Permission.USER_ROLE_ASSIGN) && !isSelf && (
            <Card title="Password" description="For an employee who forgot their password or whose temporary one expired. The new temporary password is emailed to them.">
              <ResetPasswordForm userId={id} />
            </Card>
          )}
          {((can(Permission.USER_SUSPEND) && summary.status !== "DISABLED") || can(Permission.USER_DISABLE)) && !isSelf && !fromBankVerification && (
            <p className="flex items-center gap-2 pt-2 text-xs font-semibold tracking-[0.1em] text-muted uppercase">
              <ShieldAlert aria-hidden="true" className="size-4" /> Account actions
            </p>
          )}
          {can(Permission.USER_SUSPEND) && !isSelf && summary.status !== "DISABLED" && !fromBankVerification && (
            <Card className={summary.status === "ACTIVE" ? "border-amber-300/60 dark:border-amber-500/30" : ""}
              title={summary.status === "ACTIVE" ? "Suspend account" : "Reactivate account"}
              description={summary.status === "ACTIVE"
                ? "Signs the user out everywhere immediately. While suspended, their returns are held back (paid on reactivation) and they earn no referral commission."
                : "Returns held back while suspended is credited at the next run."}>
              {summary.status === "ACTIVE" ? (
                <ReasonForm action={suspendUserAction.bind(null, id)} label="Reason" submitLabel="Suspend"
                  confirm={`Suspend ${summary.email}? They will be signed out immediately.`} />
              ) : (
                <ReasonForm action={reactivateUserAction.bind(null, id)} label="Reason" submitLabel="Reactivate"
                  variant="primary" />
              )}
            </Card>
          )}
          {can(Permission.USER_DISABLE) && !isSelf && !fromBankVerification && (
            <Card className={summary.status === "DISABLED" ? "" : "border-rose-300/60 dark:border-rose-500/30"}
              title={summary.status === "DISABLED" ? "Enable account" : "Disable account"}
              description={summary.status === "DISABLED"
                ? "Lets the user sign in again. Returns held back while disabled is credited at the next run."
                : "Super administrators only. Signs the user out, holds back their returns and stops their referral commission until enabled again."}>
              {summary.status === "DISABLED" ? (
                <ReasonForm action={enableUserAction.bind(null, id)} label="Reason" submitLabel="Enable" variant="primary" />
              ) : (
                <ReasonForm action={disableUserAction.bind(null, id)} label="Reason" submitLabel="Disable"
                  confirm={`Disable ${summary.email}? They will be signed out and stop earning until enabled again.`} />
              )}
            </Card>
          )}

        </div>
      </div>
    </div>
  );
}

function Item({ label, icon, children }: { label: string; icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="flex min-w-0 gap-3 rounded-xl bg-background/60 p-3">
      <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-surface text-gold-text shadow-sm [&>svg]:size-4">{icon}</span>
      <div className="min-w-0">
        <dt className="text-xs text-muted">{label}</dt>
        <dd className="mt-0.5 font-medium break-words">{children}</dd>
      </div>
    </div>
  );
}
