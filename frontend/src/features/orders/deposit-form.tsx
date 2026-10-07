"use client";

import { useActionState, useState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { DepositDetails, DepositMode, Payment } from "@/types/order";
import type { FormState } from "@/validators/form-state";
import { formatMoney } from "@/utils/money";
import { submitDepositAction } from "./actions";
import { DEPOSIT_MODE_HINT, DEPOSIT_MODE_LABEL, DEPOSIT_REFERENCE_LABEL } from "./labels";

const MODES = Object.keys(DEPOSIT_MODE_LABEL) as DepositMode[];

/**
 * Bank payment: the investor picks the company account they paid into, says how they paid and
 * gives the transaction ID, cheque number or deposit receipt number. They may correct it until
 * finance has verified the money.
 */
export function DepositForm({ orderId, payment }: { orderId: string; payment: Payment }) {
  const instructions = payment.bankTransfer!;
  const submitted: DepositDetails | null = payment.deposit;
  const [state, action] = useActionState<FormState, FormData>(submitDepositAction.bind(null, orderId, payment.id), {});
  const e = state.fieldErrors ?? {};
  const initialAccount = state.values?.companyBankAccountId ?? submitted?.companyBankAccountId
    ?? (instructions.accounts.length === 1 ? instructions.accounts[0]!.id : undefined);
  const [mode, setMode] = useState<DepositMode>((state.values?.mode as DepositMode | undefined) ?? submitted?.mode ?? "ONLINE");

  if (instructions.accounts.length === 0) {
    return <p className="text-sm text-muted">Bank payment is not available right now. Please contact support.</p>;
  }

  return (
    <form action={action} className="space-y-5" noValidate>
      <FormFeedback state={state} />
      <div className="space-y-1 text-sm">
        <p>Pay exactly <strong>{formatMoney(instructions.amount)}</strong> into one of our accounts, quoting the reference</p>
        <p className="font-mono text-base font-semibold">{instructions.reference}</p>
      </div>

      <fieldset className="space-y-2">
        <legend className="mb-2 text-xs font-medium uppercase tracking-[0.08em] text-muted">1. Account you paid into</legend>
        {instructions.accounts.map((a) => (
          <label key={a.id}
            className="flex cursor-pointer gap-3 border border-border p-3 text-sm has-checked:border-gold has-checked:bg-gold/5">
            <input type="radio" name="companyBankAccountId" value={a.id} defaultChecked={a.id === initialAccount}
              className="mt-1 accent-gold" />
            <span className="grid flex-1 gap-1">
              <span className="font-semibold">{a.bankName}{a.branch && <span className="font-normal text-muted"> · {a.branch}</span>}</span>
              <Detail label="Account name" value={a.accountName} />
              <Detail label="Account no." value={a.accountNumber} mono />
              <Detail label="IFSC" value={a.ifscCode} mono />
              {a.upiId && <Detail label="UPI ID" value={a.upiId} mono />}
            </span>
          </label>
        ))}
        {e.companyBankAccountId && <p className="text-xs text-rose-600 dark:text-rose-400">{e.companyBankAccountId}</p>}
      </fieldset>

      <fieldset className="space-y-2">
        <legend className="mb-2 text-xs font-medium uppercase tracking-[0.08em] text-muted">2. How you paid</legend>
        <div className="grid grid-cols-3 gap-2">
          {MODES.map((m) => (
            <label key={m}
              className="flex cursor-pointer items-center justify-center border border-border px-2 py-2 text-center text-sm has-checked:border-gold has-checked:bg-gold/5 has-checked:font-semibold has-focus-visible:ring-1 has-focus-visible:ring-gold">
              <input type="radio" name="mode" value={m} defaultChecked={m === mode} onChange={() => setMode(m)} className="sr-only" />
              {DEPOSIT_MODE_LABEL[m]}
            </label>
          ))}
        </div>
        <p className="text-xs text-muted">{DEPOSIT_MODE_HINT[mode]}</p>
        {e.mode && <p className="text-xs text-rose-600 dark:text-rose-400">{e.mode}</p>}
      </fieldset>

      <TextField id={`deposit-ref-${payment.id}`} label={`3. ${DEPOSIT_REFERENCE_LABEL[mode]}`} name="reference" autoComplete="off"
        defaultValue={state.values?.reference ?? submitted?.reference} error={e.reference} />

      <SubmitButton className="w-full" pendingLabel="Sending…">
        {submitted ? "Update payment details" : "I have paid — submit details"}
      </SubmitButton>
    </form>
  );
}

function Detail({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <span className="flex justify-between gap-3">
      <span className="shrink-0 text-muted">{label}</span>
      <span className={`text-right ${mono ? "font-mono" : ""}`}>{value}</span>
    </span>
  );
}
