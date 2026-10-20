"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/form-state";
import { setMinimumOrderAction } from "./minimum-order-actions";

export function MinimumOrderForm({ amount }: { amount: string }) {
  const [state, action] = useActionState<FormState, FormData>(setMinimumOrderAction, {});
  return (
    <form action={action} className="flex flex-wrap items-end gap-3" noValidate>
      <div className="w-full"><FormFeedback state={state} /></div>
      <div className="w-56">
        <TextField label="Minimum order (₹)" name="amount" inputMode="decimal"
          defaultValue={state.values?.amount ?? String(Number(amount))} error={state.fieldErrors?.amount} />
      </div>
      <SubmitButton variant="secondary"
        confirm="Change the minimum order value? It applies to orders placed from now on.">Save</SubmitButton>
    </form>
  );
}
