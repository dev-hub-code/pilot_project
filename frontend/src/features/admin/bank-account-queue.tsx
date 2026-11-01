import { BankAccountReview } from "./bank-account-review";
import { EmptyState } from "@/components/ui/empty-state";
import { Pagination } from "@/components/ui/pagination";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { AdminBankAccount } from "@/types/user";

/** Bank accounts awaiting verification, for staff who verify them but cannot open KYC reviews (which show them otherwise). */
export async function BankAccountQueue({ page }: { page: number }) {
  const session = await requireStaff();
  const queue = await authFetch<PageResponse<AdminBankAccount>>(`/api/v1/admin/bank-accounts?page=${page}&size=20`);

  return (
    <div className="space-y-6">
      <p className="text-sm text-muted">Assign an account to yourself, then compare the holder name with the verified identity before approving payouts.</p>
      {queue.content.length === 0 ? (
        <EmptyState title="No bank accounts awaiting verification" />
      ) : (
        <ul className="space-y-4">
          {queue.content.map((item) => (
            <li key={item.account.id} className="border border-border bg-surface p-6">
              <BankAccountReview item={item} viewerId={session.userId} />
            </li>
          ))}
        </ul>
      )}
      <Pagination page={queue} basePath="/admin/verification" params={{}} />
    </div>
  );
}
