"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import { CURRENCY } from "@/lib/currency";
import { currencyLabel } from "@/utils/money";
import type { FormState } from "@/validators/form-state";
import { adjustBalanceAction } from "./ledger-actions";

export function AdjustmentForm({ idempotencyKey }: { idempotencyKey: string }) {
  const [state, action] = useActionState<FormState, FormData>(adjustBalanceAction.bind(null, idempotencyKey), {});
  const e = state.fieldErrors ?? {};
  const v = (name: string, fallback?: string) => state.values?.[name] ?? fallback;
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Investor user id" name="userId" autoComplete="off" defaultValue={v("userId")} error={e.userId} />
        <input type="hidden" name="currency" value={CURRENCY} />
        <SelectField label="Direction" name="direction" defaultValue={v("direction", "CREDIT")}
          options={[{ value: "CREDIT", label: "Credit (pay the investor more)" }, { value: "DEBIT", label: "Debit (recover an over-payment)" }]} />
        <TextField label={currencyLabel("Amount")} name="amount" inputMode="decimal" defaultValue={v("amount")} error={e.amount} />
        <div className="sm:col-span-2">
          <TextField label="Reason (shown on the statement)" name="reason" defaultValue={v("reason")} error={e.reason} />
        </div>
      </div>
      <SubmitButton variant="quiet" confirm="Post this adjustment? Ledger entries cannot be edited afterwards.">Post adjustment</SubmitButton>
    </form>
  );
}
