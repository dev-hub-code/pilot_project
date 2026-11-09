import { BankAccountReview } from "./bank-account-review";
import { EmptyState } from "@/components/ui/empty-state";
import { Pagination } from "@/components/ui/pagination";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { AdminBankAccount } from "@/types/user";
import { Landmark } from "lucide-react";

/** Bank accounts awaiting verification, for staff who verify them but cannot open KYC reviews (which show them otherwise). */
export async function BankAccountQueue({ page }: { page: number }) {
  const session = await requireStaff();
  const queue = await authFetch<PageResponse<AdminBankAccount>>(`/api/v1/admin/bank-accounts?page=${page}&size=20`);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="flex items-start gap-2 rounded-xl bg-background/60 px-4 py-3 text-sm text-muted ring-1 ring-border">
          <Landmark aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-gold-text" />
          Assign an account to yourself, then compare the holder name with the verified identity before approving payouts.
        </p>
        {queue.totalElements > 0 && (
          <span className="rounded-full bg-amber-500/10 px-3 py-1 text-xs font-medium text-amber-700 ring-1 ring-amber-500/30 dark:text-amber-300">
            {queue.totalElements.toLocaleString("en-IN")} to verify
          </span>
        )}
      </div>
      {queue.content.length === 0 ? (
        <EmptyState title="No bank accounts awaiting verification" />
      ) : (
        <ul className="space-y-4">
          {queue.content.map((item) => (
            <li key={item.account.id} className="relative overflow-hidden rounded-2xl border border-border bg-surface p-5 shadow-sm transition-shadow hover:shadow-md sm:p-6">
              <span aria-hidden="true" className="absolute inset-y-0 left-0 w-1 bg-amber-500/70" />
              <BankAccountReview item={item} viewerId={session.userId} />
            </li>
          ))}
        </ul>
      )}
      <Pagination page={queue} basePath="/platform/verification" params={{}} />
    </div>
  );
}
