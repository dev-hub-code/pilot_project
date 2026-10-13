import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { ReasonForm } from "@/features/admin/decision-forms";
import { rejectWithdrawalAction } from "@/features/admin/withdrawal-actions";
import { HandOverCashForm, ResolveFailedForm } from "@/features/admin/withdrawal-forms";
import { EARNINGS_BALANCE_LABEL } from "@/features/earnings/labels";
import { WITHDRAWAL_STATUS_LABEL, WITHDRAWAL_TONE } from "@/features/withdrawals/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { Withdrawal } from "@/types/withdrawal";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Withdrawal" };

export default async function AdminWithdrawalPage({ params }: PageProps<"/admin/withdrawals/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  let w: Withdrawal;
  try {
    w = await authFetch<Withdrawal>(`/api/v1/admin/withdrawals/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const own = w.userId === session.userId;
  const cash = w.payoutMethod === "OFFICE_CASH";
  const process = can(Permission.WITHDRAWAL_PROCESS) && !own;
  const toHandOver = cash && w.status === "APPROVED" && process;
  const cancellable = cash && w.status === "APPROVED" && can(Permission.WITHDRAWAL_REJECT) && !own;
  const toResolve = w.status === "FAILED" && process;
  const made = w.origin === "SCHEDULED" ? "By the monthly withdrawal run" : w.origin === "OFFICE" ? "At the office" : "Requested by the investor";

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <p className="font-mono text-sm text-muted">{w.reference}</p>
          <h1 className="text-3xl font-semibold tracking-tight">
            {formatMoney(w.amount)} {cash ? "in cash at the office" : `to ${w.bankHolderName}`}
          </h1>
        </div>
        <StatusBadge tone={WITHDRAWAL_TONE[w.status]}>{WITHDRAWAL_STATUS_LABEL[w.status]}</StatusBadge>
      </div>

      {w.status === "FAILED" && (
        <Notice tone="warning">
          The bank did not pay this withdrawal: {w.failureReason}. The money is held until it is resolved below.
          {w.ticketId && <> <Link href={`/admin/support/${w.ticketId}`} className="underline">Open the support ticket</Link></>}
        </Notice>
      )}

      <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
        <Card title="Details">
          <dl className="grid gap-4 text-sm sm:grid-cols-2">
            <Item label="Investor"><Link href={`/admin/users/${w.userId}`} className="text-gold-text hover:underline">View investor</Link></Item>
            <Item label="From">{EARNINGS_BALANCE_LABEL[w.balance]}</Item>
            <Item label="Paid by">{cash ? "Cash collected at the office" : `${w.bankName} ${w.bankAccountMasked}`}</Item>
            <Item label="Made">{made} · {formatDateTime(w.createdAt)}</Item>
            {w.origin === "INVESTOR" && <Item label="Approvals">{`${w.approvals} of ${w.requiredApprovals}`}</Item>}
            {w.batchId && (
              <Item label="Withdrawal file"><Link href={`/admin/withdrawals/batches/${w.batchId}`} className="text-gold-text hover:underline">Open file</Link></Item>
            )}
            {w.payoutReference && (
              <Item label={cash ? "Cash receipt number" : "Bank reference"}><span className="font-mono">{w.payoutReference}</span></Item>
            )}
            {w.rejectionReason && <Item label="Cancelled">{w.rejectionReason}</Item>}
            {w.failureReason && <Item label="Payment failed">{w.failureReason}</Item>}
            {w.ticketId && (
              <Item label="Support ticket"><Link href={`/admin/support/${w.ticketId}`} className="text-gold-text hover:underline">Open ticket</Link></Item>
            )}
            {w.closedAt && <Item label="Closed">{formatDateTime(w.closedAt)}</Item>}
          </dl>
        </Card>

        {(toHandOver || cancellable || toResolve || (own && (cash || w.status === "FAILED"))) && (
          <div className="space-y-6">
            {own && <Notice tone="warning">This is your own withdrawal: someone else must handle it.</Notice>}
            {toHandOver && (
              <Card title="Hand over cash" description="Check the investor's photo ID, hand over the cash and have them sign the receipt.">
                <HandOverCashForm withdrawalId={w.id} amount={formatMoney(w.amount)} />
              </Card>
            )}
            {cancellable && (
              <Card title="Cancel" description="If the cash is not handed over (e.g. the investor left), the amount returns to their balance.">
                <ReasonForm action={rejectWithdrawalAction.bind(null, w.id)} label="Reason (shown to the investor)"
                  submitLabel="Cancel withdrawal" confirm={`Cancel ${w.reference}? The amount returns to the investor's balance.`} />
              </Card>
            )}
            {toResolve && (
              <Card title="Resolve the failed payment" description="Once the ticket is sorted out. The investor is told and the ticket resolved.">
                <ResolveFailedForm withdrawalId={w.id} amount={formatMoney(w.amount)} />
              </Card>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function Item({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">{label}</dt>
      <dd className="mt-1 font-medium">{children}</dd>
    </div>
  );
}
