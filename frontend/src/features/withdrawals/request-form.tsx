"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/form-state";
import { requestWithdrawalAction } from "./actions";

export function WithdrawalRequestForm({ idempotencyKey, accounts }: {
  idempotencyKey: string;
  /** Verified accounts; the withdrawal is made in the chosen account's currency. */
  accounts: { value: string; label: string }[];
}) {
  const [state, action] = useActionState<FormState, FormData>(requestWithdrawalAction.bind(null, idempotencyKey), {});
  const e = state.fieldErrors ?? {};
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField label="Pay to" name="bankAccountId" defaultValue={state.values?.bankAccountId ?? accounts[0]?.value}
          options={accounts} error={e.bankAccountId} />
        <TextField label="Amount" name="amount" inputMode="decimal" defaultValue={state.values?.amount} error={e.amount} />
      </div>
      <SubmitButton confirm="Request this withdrawal? The amount is reserved from your balance until it is paid.">
        Request withdrawal
      </SubmitButton>
    </form>
  );
}
