import type { Metadata } from "next";
import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import {
  assignBankAccountAction, rejectBankAccountAction, unassignBankAccountAction, verifyBankAccountAction,
} from "@/features/admin/actions";
import { ConfirmForm, ReasonForm } from "@/features/admin/decision-forms";
import { ReviewAssignment } from "@/features/admin/review-assignment";
import { BANK_PROOF_LABEL } from "@/features/profile/bank-labels";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { AdminBankAccount } from "@/types/user";
import { formatDateTime, humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Bank accounts" };

export default async function BankAccountQueuePage({ searchParams }: PageProps<"/admin/bank-accounts">) {
  const params = await searchParams;
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const session = await requireStaff();
  const queue = await authFetch<PageResponse<AdminBankAccount>>(`/api/v1/admin/bank-accounts?page=${page}&size=20`);

  return (
    <div className="space-y-6">
      <PageHeader title="Bank accounts to verify"
        description="Assign an account to yourself, then compare the holder name with the verified identity before approving payouts." />
      {queue.content.length === 0 ? (
        <EmptyState title="No bank accounts awaiting verification" />
      ) : (
        <ul className="space-y-4">
          {queue.content.map(({ userId, account, otherUsersWithSameAccount, replaces, assignee, ifsc }) => (
            <li key={account.id} className="space-y-4 border border-border bg-surface p-6">
              <div className="flex flex-wrap items-start justify-between gap-3 text-sm">
                <div>
                  <p className="font-medium">
                    {account.bankName} <span className="font-mono">{account.accountNumberMasked}</span>
                  </p>
                  <p className="text-muted">
                    {account.accountHolderName} · {account.country} · added {formatDateTime(account.createdAt)}
                  </p>
                  <Link href={`/admin/users/${userId}?from=bank-verification`} className="text-brand hover:underline">View account owner</Link>
                </div>
                <div className="flex items-center gap-2">
                  {account.replacesAccountId && <StatusBadge tone="warning">Change of account</StatusBadge>}
                  <StatusBadge tone={toneFor(account.status)}>{humanize(account.status)}</StatusBadge>
                </div>
              </div>
              <p className="text-sm"><span className="text-muted">IFSC</span> <span className="font-mono">{ifsc.code}</span></p>
              {replaces && (
                <p className="border-l-4 border-gold bg-background px-4 py-3 text-sm">
                  Replaces <span className="font-medium">{replaces.bankName}</span>{" "}
                  <span className="font-mono">{replaces.accountNumberMasked}</span> ({replaces.accountHolderName},{" "}
                  {humanize(replaces.status).toLowerCase()}{replaces.primary ? ", primary" : ""}). Verifying retires that
                  account and moves payouts to this one; rejecting leaves it in use.
                </p>
              )}
              <p className="text-sm">
                {account.proofType ? (
                  <a href={`/admin/bank-accounts/${account.id}/proof`} target="_blank" rel="noopener noreferrer"
                    className="text-brand hover:underline">
                    Open {BANK_PROOF_LABEL[account.proofType].toLowerCase()} (each view is recorded)
                  </a>
                ) : (
                  <span className="font-medium text-rose-600 dark:text-rose-400">
                    No bank statement or cancelled cheque: reject so the investor can add the account again with one.
                  </span>
                )}
              </p>
              {otherUsersWithSameAccount > 0 && (
                <p className="text-sm font-medium text-rose-600 dark:text-rose-400">
                  Warning: also registered by {otherUsersWithSameAccount} other user(s).
                </p>
              )}
              {userId !== session.userId && (
                <ReviewAssignment assignee={assignee} viewerId={session.userId}
                  assign={assignBankAccountAction.bind(null, account.id)}
                  unassign={unassignBankAccountAction.bind(null, account.id)}>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <ConfirmForm action={verifyBankAccountAction.bind(null, account.id)} submitLabel="Verify"
                      confirm={replaces
                        ? `Verify ${account.bankName} ${account.accountNumberMasked}? It replaces ${replaces.bankName} ${replaces.accountNumberMasked}, which is retired.`
                        : `Verify ${account.bankName} ${account.accountNumberMasked}?`} />
                    <ReasonForm action={rejectBankAccountAction.bind(null, account.id)} label="Rejection reason"
                      submitLabel="Reject" />
                  </div>
                </ReviewAssignment>
              )}
            </li>
          ))}
        </ul>
      )}
      <Pagination page={queue} basePath="/admin/bank-accounts" params={{}} />
    </div>
  );
}
