import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { SubmitButton } from "@/components/ui/submit-button";
import { makePrimaryBankAccountAction, removeBankAccountAction } from "@/features/profile/actions";
import { BankAccountForm } from "@/features/profile/bank-account-form";
import { BANK_PROOF_LABEL } from "@/features/profile/bank-labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireSession } from "@/lib/server/auth/session";
import { requireEmailVerified } from "@/lib/server/onboarding";
import type { BankAccount, Profile } from "@/types/user";
import { humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Bank accounts" };

export default async function BankAccountsPage({ searchParams }: PageProps<"/profile/bank-accounts">) {
  await requireEmailVerified();
  const fromIdentity = (await searchParams).step === "identity";
  const session = await requireSession();
  if (!hasPermission(session.permissions, Permission.INVESTOR_PORTAL)) {
    return <Notice>Payout accounts are only available to investor accounts.</Notice>;
  }
  const [accounts, profile] = await Promise.all([
    authFetch<BankAccount[]>("/api/v1/users/me/bank-accounts"),
    authFetch<Profile>("/api/v1/users/me"),
  ]);
  // Bank accounts are always held in the investor's registered name.
  const holderName = `${profile.firstName} ${profile.lastName}`.trim();
  const verifiedCount = accounts.filter((a) => a.status === "VERIFIED").length;
  // Account setup goes in order: a bank account is added once identity verification has been submitted.
  const identitySubmitted = profile.kycStatus === "PENDING" || profile.kycStatus === "APPROVED";

  return (
    <div className="space-y-6">
      <div className="space-y-4">
        {fromIdentity && (
          <Notice tone="success">
            Your Aadhaar and PAN were sent for review. Last step: add the bank account you want payouts paid into.
          </Notice>
        )}
        {identitySubmitted && profile.kycStatus !== "APPROVED" && (
          <Notice tone="warning">Accounts can be verified once your identity verification is approved.</Notice>
        )}
        {accounts.length === 0 ? (
          <EmptyState title="No bank accounts yet" description="Add the account you want withdrawals paid into." />
        ) : (
          <ul className="space-y-3">
            {accounts.map((account) => {
              // An investor always keeps a verified account: the primary one and the last verified one stay.
              const verified = account.status === "VERIFIED";
              const keepReason = !verified ? null
                : verifiedCount <= 1 ? "Your only verified account can't be removed. Add another account first."
                : account.primary ? "To remove this account, make another verified account primary first."
                : null;
              const pendingChange = accounts.find((a) => a.replacesAccountId === account.id && a.status === "PENDING_VERIFICATION");
              const replaces = account.replacesAccountId ? accounts.find((a) => a.id === account.replacesAccountId) : undefined;
              const isPendingChange = account.status === "PENDING_VERIFICATION" && replaces !== undefined;
              return (
                <li key={account.id} className="border border-border bg-surface p-6">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <p className="font-medium">
                        {account.bankName} <span className="font-mono text-sm">{account.accountNumberMasked}</span>
                      </p>
                      <p className="text-sm text-muted">
                        {account.accountHolderName} · {account.country}
                        {account.proofType && ` · ${BANK_PROOF_LABEL[account.proofType]} uploaded`}
                      </p>
                      {isPendingChange && replaces && (
                        <p className="mt-1 text-sm text-muted">
                          New details for {replaces.bankName} {replaces.accountNumberMasked}. Once verified, this account
                          replaces it.
                        </p>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      {isPendingChange && <StatusBadge tone="warning">Change requested</StatusBadge>}
                      {account.primary && <StatusBadge tone="success">Primary</StatusBadge>}
                      <StatusBadge tone={toneFor(account.status)}>{humanize(account.status)}</StatusBadge>
                    </div>
                  </div>
                  {pendingChange && (
                    <div className="mt-4">
                      <Notice>
                        Change to {pendingChange.bankName} {pendingChange.accountNumberMasked} is waiting for verification.
                        Payouts keep going to this account until then.
                      </Notice>
                    </div>
                  )}
                  <div className="mt-4 flex flex-wrap gap-2">
                    {/* Payouts go to the primary account, so only a verified account can become it. */}
                    {!account.primary && account.status === "VERIFIED" && (
                      <form action={makePrimaryBankAccountAction.bind(null, account.id)}>
                        <SubmitButton variant="quiet" pendingLabel="Updating…">Make primary</SubmitButton>
                      </form>
                    )}
                    {keepReason ? (
                      <p className="self-center text-xs text-muted">{keepReason}</p>
                    ) : (
                      <form action={removeBankAccountAction.bind(null, account.id)}>
                        <SubmitButton variant="quiet" pendingLabel="Removing…"
                          confirm={isPendingChange
                            ? `Cancel the change to ${account.bankName} ${account.accountNumberMasked}? Your current account stays as it is.`
                            : `Remove ${account.bankName} ${account.accountNumberMasked}?`}>
                          {isPendingChange ? "Cancel change" : "Remove"}
                        </SubmitButton>
                      </form>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>
      <Card title="Add a bank account">
        {identitySubmitted ? <BankAccountForm holderName={holderName} /> : (
          <div className="space-y-4">
            <Notice tone="warning">
              First verify your identity. Your bank account is checked against your verified name.
            </Notice>
            <LinkButton href="/onboarding">Continue account setup</LinkButton>
          </div>
        )}
      </Card>
    </div>
  );
}
