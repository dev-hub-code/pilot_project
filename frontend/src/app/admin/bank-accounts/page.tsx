import type { Metadata } from "next";
import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { rejectBankAccountAction, verifyBankAccountAction } from "@/features/admin/actions";
import { ConfirmForm, ReasonForm } from "@/features/admin/decision-forms";
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
        description="Compare the holder name with the verified identity before approving payouts." />
      {queue.content.length === 0 ? (
        <EmptyState title="No bank accounts awaiting verification" />
      ) : (
        <ul className="space-y-4">
          {queue.content.map(({ userId, account, otherUsersWithSameAccount }) => (
            <li key={account.id} className="space-y-4 border border-border bg-surface p-6">
              <div className="flex flex-wrap items-start justify-between gap-3 text-sm">
                <div>
                  <p className="font-medium">
                    {account.bankName} <span className="font-mono">{account.accountNumberMasked}</span>
                  </p>
                  <p className="text-muted">
                    {account.accountHolderName} · {account.country} · {account.currency} · added {formatDateTime(account.createdAt)}
                  </p>
                  <Link href={`/admin/users/${userId}`} className="text-brand hover:underline">View account owner</Link>
                </div>
                <StatusBadge tone={toneFor(account.status)}>{humanize(account.status)}</StatusBadge>
              </div>
              {otherUsersWithSameAccount > 0 && (
                <p className="text-sm font-medium text-rose-600 dark:text-rose-400">
                  Warning: also registered by {otherUsersWithSameAccount} other user(s).
                </p>
              )}
              {userId !== session.userId && (
                <div className="grid gap-4 sm:grid-cols-2">
                  <ConfirmForm action={verifyBankAccountAction.bind(null, account.id)} submitLabel="Verify"
                    confirm={`Verify ${account.bankName} ${account.accountNumberMasked}?`} />
                  <ReasonForm action={rejectBankAccountAction.bind(null, account.id)} label="Rejection reason"
                    submitLabel="Reject" />
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      <Pagination page={queue} basePath="/admin/bank-accounts" params={{}} />
    </div>
  );
}
