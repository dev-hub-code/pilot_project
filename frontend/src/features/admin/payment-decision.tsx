import { paymentTaskAction } from "@/features/admin/payment-actions";
import { ConfirmTransferForm, RejectPaymentForm } from "@/features/admin/payment-forms";
import { ReviewAssignment } from "@/features/admin/review-assignment";
import type { Payment } from "@/types/order";

/** Confirming or rejecting a bank or office payment, by the finance member who assigned it to themselves. */
export function PaymentDecision({ payment: p, viewerId }: { payment: Payment; viewerId: string }) {
  return (
    <ReviewAssignment assignee={p.assignee ?? null} viewerId={viewerId}
      assign={paymentTaskAction.bind(null, p.id, true)} unassign={paymentTaskAction.bind(null, p.id, false)}>
      <div className="grid gap-6 lg:grid-cols-2">
        <ConfirmTransferForm paymentId={p.id} amountDue={p.amount.amount} currency={p.amount.currency}
          defaultReference={p.deposit?.reference ?? p.receiptNumber ?? undefined} officeCash={p.method === "OFFICE_CASH"} />
        {p.status === "PENDING" && <RejectPaymentForm paymentId={p.id} />}
      </div>
    </ReviewAssignment>
  );
}
