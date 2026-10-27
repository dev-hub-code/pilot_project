"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { Payment } from "@/types/order";
import type { FormState } from "@/validators/form-state";
import { submitReceiptAction } from "./actions";

/**
 * Cash at the office: once paid, the investor gives the receipt number they were handed. They may correct it until
 * finance has recorded the cash.
 */
export function ReceiptForm({ orderId, payment }: { orderId: string; payment: Payment }) {
  const [state, action] = useActionState<FormState, FormData>(submitReceiptAction.bind(null, orderId, payment.id), {});
  const e = state.fieldErrors ?? {};

  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <TextField id={`receipt-${payment.id}`} label="Receipt number" name="receiptNumber" autoComplete="off"
        defaultValue={state.values?.receiptNumber ?? payment.receiptNumber ?? undefined} error={e.receiptNumber} />
      <SubmitButton className="w-full" pendingLabel="Sending…">
        {payment.receiptNumber ? "Update receipt number" : "I have paid — submit receipt number"}
      </SubmitButton>
    </form>
  );
}
