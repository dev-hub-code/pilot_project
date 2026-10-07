"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/form-state";
import { createBatchAction, settleBatchAction } from "./withdrawal-actions";

export function CreateBatchForm({ currencies }: { currencies: { value: string; label: string }[] }) {
  const [state, action] = useActionState<FormState, FormData>(createBatchAction, {});
  return (
    <form action={action} className="flex flex-wrap items-end gap-3" noValidate>
      <div className="w-full"><FormFeedback state={state} /></div>
      <div className="min-w-56"><SelectField label="Currency" name="currency" options={currencies} /></div>
      <SubmitButton>Create payout batch</SubmitButton>
    </form>
  );
}

export function SettleBatchForm({ batchId, outstanding }: { batchId: string; outstanding: number }) {
  const [state, action] = useActionState<FormState, FormData>(settleBatchAction.bind(null, batchId), {});
  return (
    <form action={action} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <TextField label="Bank statement reference (optional)" name="payoutReference" autoComplete="off"
        error={state.fieldErrors?.payoutReference} />
      <SubmitButton confirm={`Mark all ${outstanding} outstanding payment(s) as paid?`}>Mark remaining as paid</SubmitButton>
    </form>
  );
}
