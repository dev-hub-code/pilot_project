import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { SubmitButton } from "@/components/ui/submit-button";
import { makePrimaryBankAccountAction, removeBankAccountAction } from "@/features/profile/actions";
import { BankAccountForm } from "@/features/profile/bank-account-form";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireSession } from "@/lib/server/auth/session";
import type { BankAccount, Profile } from "@/types/user";
import { humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Bank accounts" };

export default async function BankAccountsPage() {
  const session = await requireSession();
  if (!hasPermission(session.permissions, Permission.INVESTOR_PORTAL)) {
    return <Notice>Payout accounts are only available to investor accounts.</Notice>;
  }
  const [accounts, profile] = await Promise.all([
    authFetch<BankAccount[]>("/api/v1/users/me/bank-accounts"),
    authFetch<Profile>("/api/v1/users/me"),
  ]);

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="space-y-4 lg:col-span-2">
        {profile.kycStatus !== "APPROVED" && (
          <Notice tone="warning">Accounts can be verified once your identity verification is approved.</Notice>
        )}
        {accounts.length === 0 ? (
          <EmptyState title="No bank accounts yet" description="Add the account you want withdrawals paid into." />
        ) : (
          <ul className="space-y-3">
            {accounts.map((account) => (
              <li key={account.id} className="border border-border bg-surface p-6">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="font-medium">
                      {account.bankName} <span className="font-mono text-sm">{account.accountNumberMasked}</span>
                    </p>
                    <p className="text-sm text-muted">
                      {account.accountHolderName} · {account.country}
                    </p>
                    {account.rejectionReason && (
                      <p className="mt-1 text-sm text-rose-600 dark:text-rose-400">Rejected: {account.rejectionReason}</p>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    {account.primary && <StatusBadge tone="success">Primary</StatusBadge>}
                    <StatusBadge tone={toneFor(account.status)}>{humanize(account.status)}</StatusBadge>
                  </div>
                </div>
                <div className="mt-4 flex flex-wrap gap-2">
                  {!account.primary && account.status !== "REJECTED" && (
                    <form action={makePrimaryBankAccountAction.bind(null, account.id)}>
                      <SubmitButton variant="quiet" pendingLabel="Updating…">Make primary</SubmitButton>
                    </form>
                  )}
                  <form action={removeBankAccountAction.bind(null, account.id)}>
                    <SubmitButton variant="quiet" pendingLabel="Removing…"
                      confirm={`Remove ${account.bankName} ${account.accountNumberMasked}?`}>
                      Remove
                    </SubmitButton>
                  </form>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
      <Card title="Add a bank account">
        <BankAccountForm defaultHolder={`${profile.firstName} ${profile.lastName}`} />
      </Card>
    </div>
  );
}
