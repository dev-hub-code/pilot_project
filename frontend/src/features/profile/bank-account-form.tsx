"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import { COUNTRY_OPTIONS } from "@/lib/countries";
import { CURRENCY } from "@/lib/currency";
import type { FormState } from "@/validators/form-state";
import { addBankAccountAction } from "./actions";

export function BankAccountForm({ defaultHolder }: { defaultHolder: string }) {
  const [state, action] = useActionState<FormState, FormData>(addBankAccountAction, {});
  const e = state.fieldErrors ?? {};
  const v = (name: string, fallback = "") => state.values?.[name] ?? fallback;

  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <TextField label="Account holder" name="accountHolderName" defaultValue={v("accountHolderName", defaultHolder)}
        error={e.accountHolderName} />
      <TextField label="Bank name" name="bankName" defaultValue={v("bankName")} error={e.bankName} />
      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField label="Bank country" name="country" options={COUNTRY_OPTIONS} placeholder="Select…"
          defaultValue={v("country")} error={e.country} />
        <input type="hidden" name="currency" value={CURRENCY} />
      </div>
      <TextField label="Account number or IBAN" name="accountNumber" autoComplete="off" error={e.accountNumber} />
      <TextField label="SWIFT/BIC, IFSC, routing or sort code" name="routingCode" defaultValue={v("routingCode")}
        autoComplete="off" error={e.routingCode} />
      <p className="text-xs text-muted">The account must be in your own name. Numbers are stored encrypted.</p>
      <SubmitButton>Add bank account</SubmitButton>
    </form>
  );
}
