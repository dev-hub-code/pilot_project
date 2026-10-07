"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/form-state";
import { confirmTransferAction, recordRefundAction } from "./payment-actions";

export function ConfirmTransferForm({ paymentId, amountDue, currency }: { paymentId: string; amountDue: string; currency: string }) {
  const [state, action] = useActionState<FormState, FormData>(confirmTransferAction.bind(null, paymentId), {});
  const e = state.fieldErrors ?? {};
  return (
    <form action={action} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField id={`ref-${paymentId}`} label="Bank reference" name="externalReference" autoComplete="off"
          defaultValue={state.values?.externalReference} error={e.externalReference} />
        <TextField id={`amt-${paymentId}`} label={`Amount received (${currency})`} name="amountReceived" inputMode="decimal"
          defaultValue={state.values?.amountReceived} placeholder={amountDue} error={e.amountReceived} />
      </div>
      <SubmitButton variant="quiet" confirm={(form) => {
        const entered = String(new FormData(form).get("amountReceived") ?? "").trim();
        return `Record that ${entered || amountDue} ${currency} arrived? This confirms the order.`;
      }}>
        Record transfer
      </SubmitButton>
    </form>
  );
}

export function RefundForm({ paymentId }: { paymentId: string }) {
  const [state, action] = useActionState<FormState, FormData>(recordRefundAction.bind(null, paymentId), {});
  const e = state.fieldErrors ?? {};
  return (
    <form action={action} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField id={`rref-${paymentId}`} label="Refund transfer reference" name="reference" autoComplete="off"
          defaultValue={state.values?.reference} error={e.reference} />
        <TextField id={`rreason-${paymentId}`} label="Reason" name="reason" defaultValue={state.values?.reason} error={e.reason} />
      </div>
      <SubmitButton variant="quiet" confirm="Record that this money was returned to the investor?">Record refund</SubmitButton>
    </form>
  );
}
