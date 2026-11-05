import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { initiatePaymentAction, withdrawalReviewAction } from "@/features/admin/withdrawal-actions";
import { ResolveFailedForm, WithdrawalDecisionForm } from "@/features/admin/withdrawal-forms";
import { EARNINGS_BALANCE_LABEL } from "@/features/earnings/labels";
import { ActionButton } from "@/features/orders/action-button";
import { CANCELLABLE, WITHDRAWAL_STATUS_LABEL, WITHDRAWAL_TONE } from "@/features/withdrawals/labels";
import { Breakdown, ProgressStepper, Timeline, age, deduction } from "@/features/withdrawals/ui";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { WithdrawalAdminDetail } from "@/types/withdrawal";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Withdrawal" };

export default async function AdminWithdrawalPage({ params }: PageProps<"/admin/withdrawals/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  let detail: WithdrawalAdminDetail;
  try {
    detail = await authFetch<WithdrawalAdminDetail>(`/api/v1/admin/withdrawals/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const { withdrawal: w, investor, timeline, notes } = detail;
  const own = w.userId === session.userId;
  const cash = w.payoutMethod === "OFFICE_CASH";
  const approve = can(Permission.WITHDRAWAL_APPROVE) && !own;
  const reject = can(Permission.WITHDRAWAL_REJECT) && !own;
  const process = can(Permission.WITHDRAWAL_PROCESS) && !own;
  const mineToReview = w.reviewerId === session.userId;
  const approvedByMe = [w.firstApprovedBy, w.secondApprovedBy].includes(session.userId);
  const made = w.origin === "SCHEDULED" ? "By a withdrawal run" : w.origin === "OFFICE" ? "At the office" : "Requested by the investor";

  const canTake = approve && (w.status === "SUBMITTED" || (w.status === "UNDER_REVIEW" && !w.reviewerId));
  const canApprove = approve && !approvedByMe
    && ((w.status === "UNDER_REVIEW" && (mineToReview || w.approvals > 0)) || w.status === "PENDING_APPROVAL");
  const canHold = approve && ["SUBMITTED", "UNDER_REVIEW", "PENDING_APPROVAL"].includes(w.status);
  const canReject = reject && (CANCELLABLE.includes(w.status) || w.status === "APPROVED");
  const canPay = process && w.status === "APPROVED" && w.origin === "REQUESTED";

  return (
    <div className="space-y-6">
      <Link href="/admin/withdrawals" className="inline-flex items-center gap-1.5 text-sm text-muted hover:text-foreground">
        <ArrowLeft className="size-4" aria-hidden="true" /> Withdrawal management
      </Link>

      <section className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-6">
          <div className="space-y-1">
            <p className="font-mono text-sm text-muted">{w.reference} · {made}</p>
            <h1 className="font-display text-3xl font-semibold tracking-tight tabular-nums">{formatMoney(w.amount)}</h1>
            <p className="text-sm text-muted">
              Pays <span className="font-medium text-foreground">{formatMoney(w.net)}</span> to {cash ? "cash at the office" : `${w.bankHolderName} · ${w.bankName ?? ""} ${w.bankAccountMasked ?? ""}`}
            </p>
          </div>
          <div className="space-y-1 text-right">
            <StatusBadge tone={WITHDRAWAL_TONE[w.status]}>{WITHDRAWAL_STATUS_LABEL[w.status]}</StatusBadge>
            <p className="text-xs text-muted">for {age(w.statusChangedAt)} · since {formatDateTime(w.statusChangedAt)}</p>
            {w.requiredApprovals > 1 && <p className="text-xs text-muted">{w.approvals} of {w.requiredApprovals} approvals</p>}
          </div>
        </div>
        {!["REJECTED", "CANCELLED", "RETURNED"].includes(w.status) && (
          <div className="mt-8"><ProgressStepper status={w.status} timeline={timeline} /></div>
        )}
      </section>

      {own && <Notice tone="warning">This is your own withdrawal: someone else must review and pay it.</Notice>}
      {w.status === "ON_HOLD" && <Notice tone="warning">On hold{w.infoRequested ? ", waiting for the investor" : ""}: {w.holdReason}</Notice>}
      {w.status === "FAILED" && (
        <Notice tone="warning">
          The bank did not pay this withdrawal: {w.failureReason}. The money is held until it is resolved below.
          {w.ticketId && <> <Link href={`/admin/support/${w.ticketId}`} className="underline">Open the support ticket</Link></>}
        </Notice>
      )}

      <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
        <div className="space-y-6">
          <Card title="Investor">
            <dl className="grid gap-4 text-sm sm:grid-cols-2">
              <Item label="Name"><Link href={`/admin/users/${w.userId}`} className="text-gold-text hover:underline">{investor.name || "Investor"}</Link></Item>
              <Item label="Investor ID"><span className="font-mono">{investor.userCode ?? "—"}</span></Item>
              <Item label="Email">{investor.email}</Item>
              <Item label="Identity verification">{investor.kycStatus}</Item>
              <Item label="Wallet now">{EARNINGS_BALANCE_LABEL[w.balance]} · {formatMoney(detail.walletNow)}</Item>
            </dl>
          </Card>

          <Card title="Calculation">
            <Breakdown rows={[
              { label: "Amount withdrawn", value: formatMoney(w.amount) },
              { label: "Charges", value: deduction(w.fee), muted: true },
              { label: "Tax withheld (TDS)", value: deduction(w.tax), muted: true },
              { label: "Net payable", value: formatMoney(w.net), strong: true },
            ]} />
          </Card>

          <Card title="Beneficiary and payment">
            <dl className="grid gap-4 text-sm sm:grid-cols-2">
              <Item label="Paid by">{cash ? "Cash at the office" : `${w.bankName ?? "Account"} ${w.bankAccountMasked}`}</Item>
              {!cash && <Item label="Account holder">{w.bankHolderName}</Item>}
              <Item label="Approvals">{`${w.approvals} of ${w.requiredApprovals}`}</Item>
              {w.paymentInitiatedAt && <Item label="Payment initiated">{formatDateTime(w.paymentInitiatedAt)}{w.payoutProvider ? ` · ${w.payoutProvider}` : ""}</Item>}
              {w.batchId && (
                <Item label="Withdrawal file"><Link href={`/admin/withdrawals/batches/${w.batchId}`} className="text-gold-text hover:underline">{detail.batchReference ?? "Open file"}</Link></Item>
              )}
              {w.payoutReference && <Item label={cash ? "Cash receipt number" : "Bank reference (UTR)"}><span className="font-mono">{w.payoutReference}</span></Item>}
              {w.rejectionReason && <Item label="Rejected">{w.rejectionReason}</Item>}
              {w.failureReason && <Item label="Payment failed">{w.failureReason}</Item>}
              {w.closedAt && <Item label="Closed">{formatDateTime(w.closedAt)}</Item>}
              {w.status === "PAID" && (
                <Item label="Receipt"><a href={`/admin/withdrawals/${w.id}/receipt`} className="text-gold-text hover:underline">Download PDF</a></Item>
              )}
            </dl>
          </Card>

          <Card title="Timeline" description="Every step, who made it and why. Steps marked internal are not shown to the investor.">
            <Timeline events={timeline} staff />
          </Card>
        </div>

        <div className="space-y-6 lg:sticky lg:top-24 lg:self-start">
          {(canTake || canApprove || mineToReview || w.status === "ON_HOLD" && approve) && (
            <Card title="Review">
              <div className="space-y-3">
                {canTake && <ActionButton action={withdrawalReviewAction.bind(null, w.id, "review")} label="Take for review" />}
                {canApprove && (
                  <ActionButton action={withdrawalReviewAction.bind(null, w.id, "approve")}
                    label={w.requiredApprovals > 1 && w.approvals === 0 ? "Approve (first of two)" : "Approve"}
                    confirm={`Approve ${w.reference} for ${formatMoney(w.net)}?`} />
                )}
                {approve && w.status === "ON_HOLD" && <ActionButton action={withdrawalReviewAction.bind(null, w.id, "resume")} label="Resume review" variant="secondary" />}
                {mineToReview && w.status === "UNDER_REVIEW" && (
                  <ActionButton action={withdrawalReviewAction.bind(null, w.id, "review/release")} label="Give back to the queue" variant="quiet" />
                )}
                {approvedByMe && w.status === "PENDING_APPROVAL" && <p className="text-xs text-muted">You gave the first approval; a different approver must give the second.</p>}
              </div>
            </Card>
          )}
          {canHold && (
            <Card title="Hold or ask the investor">
              <div className="space-y-5">
                <WithdrawalDecisionForm withdrawalId={w.id} step="request-info" label="Information needed" field="What do you need from the investor?" button="Ask the investor" />
                <WithdrawalDecisionForm withdrawalId={w.id} step="hold" label="Hold reason" field="Why hold it?" button="Put on hold" variant="quiet" />
              </div>
            </Card>
          )}
          {canReject && (
            <Card title="Reject" description="The investor sees the reason; the amount goes back to their wallet.">
              <WithdrawalDecisionForm withdrawalId={w.id} step="reject" label="Rejection reason" field="Reason" button="Reject withdrawal"
                variant="danger" confirm={`Reject ${w.reference}? The amount goes back to the investor's wallet.`} />
            </Card>
          )}
          {canPay && (
            <Card title="Payment" description="Hands it to the payout provider. It is marked paid only with the bank's reference.">
              <ActionButton action={initiatePaymentAction.bind(null, [w.id])} label="Initiate payment"
                confirm={`Initiate payment of ${formatMoney(w.net)} for ${w.reference}?`} />
            </Card>
          )}
          {w.status === "FAILED" && process && (
            <Card title="Resolve the failed payment" description="Once the ticket is sorted out. The investor is told and the ticket resolved.">
              <ResolveFailedForm withdrawalId={w.id} amount={formatMoney(w.net)} />
            </Card>
          )}
          <Card title="Internal notes" description="Staff only. Never shown to the investor.">
            <div className="space-y-4">
              {notes.length === 0 ? <p className="text-sm text-muted">No notes yet.</p> : (
                <ul className="space-y-3 text-sm">
                  {notes.map((n) => (
                    <li key={n.id} className="rounded-lg bg-background p-3">
                      <p className="whitespace-pre-line">{n.body}</p>
                      <p className="mt-1 text-xs text-muted">{n.authorName} · {formatDateTime(n.createdAt)}</p>
                    </li>
                  ))}
                </ul>
              )}
              <WithdrawalDecisionForm withdrawalId={w.id} step="notes" label="Note" field="Add a note" button="Add note" variant="quiet" />
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}

function Item({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="mt-1 font-medium">{children}</dd>
    </div>
  );
}
