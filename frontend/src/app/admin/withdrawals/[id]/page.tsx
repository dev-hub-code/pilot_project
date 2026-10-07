import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { ConfirmForm, ReasonForm } from "@/features/admin/decision-forms";
import { approveWithdrawalAction, rejectWithdrawalAction } from "@/features/admin/withdrawal-actions";
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
  const alreadyApproved = w.firstApprovedBy === session.userId || w.secondApprovedBy === session.userId;
  const pending = w.status === "PENDING_APPROVAL";
  const rejectable = pending || w.status === "APPROVED";

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <p className="font-mono text-sm text-muted">{w.reference}</p>
          <h1 className="text-3xl font-semibold tracking-tight">{formatMoney(w.amount)} to {w.bankHolderName}</h1>
        </div>
        <StatusBadge tone={WITHDRAWAL_TONE[w.status]}>{WITHDRAWAL_STATUS_LABEL[w.status]}</StatusBadge>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
        <Card title="Details">
          <dl className="grid gap-4 text-sm sm:grid-cols-2">
            <Item label="Investor"><Link href={`/admin/users/${w.userId}`} className="text-gold-text hover:underline">View investor</Link></Item>
            <Item label="Bank account">{w.bankName} {w.bankAccountMasked}</Item>
            <Item label="Requested">{formatDateTime(w.createdAt)}</Item>
            <Item label="Approvals">{w.approvals} of {w.requiredApprovals}</Item>
            {w.firstApprovedAt && <Item label="First approval">{formatDateTime(w.firstApprovedAt)}</Item>}
            {w.secondApprovedAt && <Item label="Second approval">{formatDateTime(w.secondApprovedAt)}</Item>}
            {w.batchId && (
              <Item label="Payout batch"><Link href={`/admin/withdrawals/batches/${w.batchId}`} className="text-gold-text hover:underline">Open batch</Link></Item>
            )}
            {w.payoutReference && <Item label="Bank reference"><span className="font-mono">{w.payoutReference}</span></Item>}
            {w.rejectionReason && <Item label="Rejected">{w.rejectionReason}</Item>}
            {w.failureReason && <Item label="Failed">{w.failureReason}</Item>}
            {w.closedAt && <Item label="Closed">{formatDateTime(w.closedAt)}</Item>}
          </dl>
        </Card>

        {(pending || rejectable) && (can(Permission.WITHDRAWAL_APPROVE) || can(Permission.WITHDRAWAL_REJECT)) && (
          <div className="space-y-6">
            {own && <Notice tone="warning">This is your own withdrawal: someone else must decide on it.</Notice>}
            {!own && pending && can(Permission.WITHDRAWAL_APPROVE) && (
              <Card title="Approve" description={w.requiredApprovals === 2 ? "Above the threshold: two different approvers are needed." : undefined}>
                {alreadyApproved ? (
                  <Notice>You have approved this withdrawal. A second approver is needed.</Notice>
                ) : (
                  <ConfirmForm action={approveWithdrawalAction.bind(null, w.id)} submitLabel="Approve"
                    confirm={`Approve paying ${formatMoney(w.amount)} to ${w.bankHolderName}?`} />
                )}
              </Card>
            )}
            {!own && rejectable && can(Permission.WITHDRAWAL_REJECT) && (
              <Card title="Reject" description="The amount returns to the investor's balance.">
                <ReasonForm action={rejectWithdrawalAction.bind(null, w.id)} label="Reason (shown to the investor)"
                  submitLabel="Reject withdrawal" confirm={`Reject ${w.reference}?`} />
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
