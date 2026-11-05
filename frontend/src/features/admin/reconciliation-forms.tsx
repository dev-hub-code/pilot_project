"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextArea } from "@/components/ui/text-area";
import type { FormState } from "@/validators/form-state";
import { resolveUnmatchedAction } from "./reconciliation-actions";

/** Closes an unmatched receipt with how it was traced: refunded (with the refund reference) or matched by hand. */
export function ResolveUnmatchedForm({ receiptId, amount }: { receiptId: string; amount: string }) {
  const [state, action] = useActionState<FormState, FormData>(resolveUnmatchedAction.bind(null, receiptId), {});
  if (state.success) return <FormFeedback state={state} />;
  return (
    <form action={action} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <TextArea label="How was it resolved?" name="reason" rows={2} maxLength={500} required
        placeholder="e.g. Refunded to the sender, Razorpay refund rfnd_…" defaultValue={state.values?.reason}
        error={state.fieldErrors?.reason} />
      <SubmitButton variant="secondary" confirm={`Mark ${amount} as resolved? This cannot be undone.`}>Mark resolved</SubmitButton>
    </form>
  );
}
