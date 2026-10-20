import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import {
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
import type { AdminReferralView } from "@/types/referral";
import type { UserAuthorities } from "@/types/staff";
import { formatMoney } from "@/utils/money";
import type { AdminBankAccount, AdminUserDetail, KycSubmission } from "@/types/user";
import { kycNumbers } from "@/features/profile/kyc-labels";
import { formatDateTime, formatDayMonthYear, humanize } from "@/utils/format";
import { EmailText } from "@/components/ui/email-text";
import { formatPhone } from "@/lib/phone";

export const metadata: Metadata = { title: "User" };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default async function UserDetailPage({ params, searchParams }: PageProps<"/admin/users/[id]">) {
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
  const [kyc, banks, referral] = await Promise.all([
    can(Permission.KYC_REVIEW) ? authFetch<KycSubmission[]>(`/api/v1/admin/users/${id}/kyc`) : null,
    can(Permission.BANK_ACCOUNT_VERIFY) || can(Permission.FINANCE_VIEW)
      ? authFetch<AdminBankAccount[]>(`/api/v1/admin/users/${id}/bank-accounts`)
      : null,
    authFetch<AdminReferralView>(`/api/v1/admin/users/${id}/referrals`),
  ]);
  const [authorities, allRoles] = can(Permission.ROLE_VIEW)
    ? await Promise.all([
      authFetch<UserAuthorities>(`/api/v1/admin/users/${id}/roles`),
      authFetch<PageResponse<Role>>("/api/v1/admin/roles?size=100"),
    ])
    : [null, null];
  const isStaffAccount = authorities?.permissions.some((p) => p !== Permission.INVESTOR_PORTAL) ?? false;
  // Withdrawals: how referral earnings are paid this year.
  const showWithdrawals = !fromBankVerification && !isStaffAccount && can(Permission.WITHDRAWAL_VIEW) && session.userId !== id;
  const referralMode = showWithdrawals ? await authFetch<ReferralPayoutMode>(`/api/v1/admin/users/${id}/referral-mode`) : null;
  const { summary, profile } = user;
  const isSelf = session.userId === id;

  return (
    <div className="space-y-6">
      {fromBankVerification && (
        <Link href="/admin/bank-accounts" className="text-sm text-muted hover:underline">← Back to bank verification</Link>
      )}
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">{summary.firstName} {summary.lastName}</h1>
        <span className="font-mono text-sm text-muted">{summary.userCode}</span>
        <StatusBadge tone={toneFor(summary.status)}>{humanize(summary.status)}</StatusBadge>
        {!summary.emailVerifiedAt && <StatusBadge tone="warning">Email not verified</StatusBadge>}
        <StatusBadge tone={toneFor(summary.kycStatus)}>KYC {humanize(summary.kycStatus).toLowerCase()}</StatusBadge>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Card title="Account">
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <Item label="Email">
                <EmailText>{summary.email}</EmailText>
                <span className="block text-xs text-muted">
                  {summary.emailVerifiedAt ? `Verified ${formatDateTime(summary.emailVerifiedAt)}` : "Not verified yet"}
                </span>
              </Item>
              <Item label="Phone">{profile.phone ? formatPhone(profile.phone) : "—"}</Item>
              <Item label="Date of birth">{formatDayMonthYear(profile.dateOfBirth) || "—"}</Item>
              <Item label="Nationality">{profile.nationality ?? "—"}</Item>
              <Item label="Address">
                {[profile.address.line1, profile.address.city, profile.address.postalCode, profile.address.country]
                  .filter(Boolean).join(", ") || "—"}
              </Item>
              <Item label="Joined">{formatDateTime(summary.createdAt)}</Item>
              <Item label="Last sign-in">{formatDateTime(summary.lastLoginAt)}</Item>
              {user.statusReason && (
                <Item label="Status reason">{user.statusReason} ({formatDateTime(user.statusChangedAt)})</Item>
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
                    <li key={s.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                      <Link href={`/admin/kyc/${s.id}`} className="text-brand hover:underline">
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
                  {banks.map(({ account, otherUsersWithSameAccount, assignee }) => (
                    <li key={account.id} className="space-y-3 border border-border p-4">
                      <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
                        <span className="font-medium">
                          {account.bankName} <span className="font-mono">{account.accountNumberMasked}</span> ·{" "}
                          {account.accountHolderName}
                        </span>
                        <span className="flex gap-2">
                          {account.primary && <StatusBadge tone="success">Primary</StatusBadge>}
                          <StatusBadge tone={toneFor(account.status)}>{humanize(account.status)}</StatusBadge>
                        </span>
                      </div>
                      {otherUsersWithSameAccount > 0 && (
                        <p className="text-sm font-medium text-rose-600 dark:text-rose-400">
                          Warning: this account is also registered by {otherUsersWithSameAccount} other user(s).
                        </p>
                      )}
                      {account.status === "PENDING_VERIFICATION" && can(Permission.BANK_ACCOUNT_VERIFY) && !isSelf && (
                        <ReviewAssignment assignee={assignee} viewerId={session.userId}
                          assign={assignBankAccountAction.bind(null, account.id)}
                          unassign={unassignBankAccountAction.bind(null, account.id)}>
                          <div className="grid gap-4 sm:grid-cols-2">
                            <ConfirmForm action={verifyBankAccountAction.bind(null, account.id)} submitLabel="Verify"
                              confirm={`Verify ${account.bankName} ${account.accountNumberMasked} for payouts?`} />
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
              {can(Permission.USER_ROLE_ASSIGN) && !isSelf && allRoles ? (
                <RolesForm userId={id} roles={allRoles.content} current={authorities.roles} />
              ) : (
                <p className="text-sm text-muted">{authorities.permissions.length} permission(s).</p>
              )}
            </Card>
          )}

          {!fromBankVerification && (
          <Card title="Referrals" description={referral.code ? `Referral code ${referral.code}` : "Has not opened their referral page yet."}>
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <Item label="Referred by">
                {referral.uplines.length === 0 ? "—" : (
                  <ol className="space-y-1">
                    {referral.uplines.map((u) => (
                      <li key={u.userId}>
                        <span className="text-muted">L{u.level}</span>{" "}
                        <Link href={`/admin/users/${u.userId}`} className="text-gold-text hover:underline">{u.name}</Link>
                        <span className="block text-xs text-muted"><EmailText>{u.email}</EmailText></span>
                      </li>
                    ))}
                  </ol>
                )}
              </Item>
              <Item label="Downline (levels 1–4)">{referral.downlineSize.join(" / ")}</Item>
              <Item label="Commission earned">
                {referral.totalEarned.length === 0 ? "—" : referral.totalEarned.map((m) => formatMoney(m)).join(" · ")}
              </Item>
            </dl>
          </Card>
          )}
        </div>

        <div className="space-y-6">
          {referralMode && (
            <Card title="Withdrawals" description="Rent on containers paid at the office is handed over in cash at the office (marked collected by hand); referral earnings are paid by bank sheet or in cash, as chosen for the year.">
              <div className="space-y-5 text-sm">
                <div className="space-y-2">
                  <p>
                    Referral earnings in {referralMode.year}:{" "}
                    <span className="font-medium">{referralMode.mode === "OFFICE_CASH" ? "cash" : "into the bank account"}</span>
                    <span className="text-muted">{referralMode.chosen ? " (chosen by the investor)" : " (default; not chosen yet)"}</span>
                  </p>
                  {can(Permission.WITHDRAWAL_PROCESS) && <ReferralModeAdminForm userId={id} mode={referralMode.mode} />}
                </div>
              </div>
            </Card>
          )}
          {isStaffAccount && can(Permission.USER_ROLE_ASSIGN) && !isSelf && (
            <Card title="Password" description="For an employee who forgot their password or whose temporary one expired. The new temporary password is emailed to them.">
              <ResetPasswordForm userId={id} />
            </Card>
          )}
          {can(Permission.USER_SUSPEND) && !isSelf && summary.status !== "DISABLED" && !fromBankVerification && (
            <Card title={summary.status === "ACTIVE" ? "Suspend account" : "Reactivate account"}
              description={summary.status === "ACTIVE" ? "Signs the user out everywhere immediately." : undefined}>
              {summary.status === "ACTIVE" ? (
                <ReasonForm action={suspendUserAction.bind(null, id)} label="Reason" submitLabel="Suspend"
                  confirm={`Suspend ${summary.email}? They will be signed out immediately.`} />
              ) : (
                <ReasonForm action={reactivateUserAction.bind(null, id)} label="Reason" submitLabel="Reactivate"
                  variant="primary" />
              )}
            </Card>
          )}

        </div>
      </div>
    </div>
  );
}

function Item({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-muted">{label}</dt>
      <dd className="mt-0.5 font-medium">{children}</dd>
    </div>
  );
}
