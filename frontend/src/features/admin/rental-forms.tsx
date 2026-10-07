"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/form-state";
import { activateLeaseAction } from "./investment-actions";
import { adjustBalanceAction, recordRentalAction } from "./rental-actions";

export function ActivateLeaseForm({ productId, code, defaultDate }: { productId: string; code: string; defaultDate: string }) {
  const [state, action] = useActionState<FormState, FormData>(activateLeaseAction.bind(null, productId), {});
  return (
    <form action={action} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <TextField label="Lease starts on (first day of period 1)" name="leaseStartsOn" type="date"
        defaultValue={state.values?.leaseStartsOn ?? defaultDate} error={state.fieldErrors?.leaseStartsOn} />
      <SubmitButton confirm={`Start the lease of ${code}? It stops taking investments.`}>Start lease</SubmitButton>
    </form>
  );
}

export function RecordRentalForm({ productId, periods, defaultPeriod, expected, currency, today }: {
  productId: string;
  periods: { value: string; label: string }[];
  defaultPeriod?: string;
  expected: string;
  currency: string;
  today: string;
}) {
  const [state, action] = useActionState<FormState, FormData>(recordRentalAction, {});
  const e = state.fieldErrors ?? {};
  const v = (name: string, fallback?: string) => state.values?.[name] ?? fallback;
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <input type="hidden" name="productId" value={productId} />
      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField label="Rental period" name="periodNumber" defaultValue={v("periodNumber", defaultPeriod)} options={periods} />
        <TextField label={`Amount received (${currency})`} name="amount" inputMode="decimal"
          defaultValue={v("amount", expected)} error={e.amount} />
        <TextField label="Received on" name="receivedOn" type="date" defaultValue={v("receivedOn", today)} error={e.receivedOn} />
        <TextField label="Bank reference" name="externalReference" autoComplete="off"
          defaultValue={v("externalReference")} error={e.externalReference} />
        <div className="sm:col-span-2">
          <TextField label={`Note (required if not exactly ${expected} ${currency})`} name="note" defaultValue={v("note")} error={e.note} />
        </div>
      </div>
      <SubmitButton>Record payment</SubmitButton>
      <p className="text-xs text-muted">Nothing is paid out yet: another finance user must approve the payment.</p>
    </form>
  );
}

export function AdjustmentForm({ idempotencyKey, currencies }: { idempotencyKey: string; currencies: string[] }) {
  const [state, action] = useActionState<FormState, FormData>(adjustBalanceAction.bind(null, idempotencyKey), {});
  const e = state.fieldErrors ?? {};
  const v = (name: string, fallback?: string) => state.values?.[name] ?? fallback;
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Investor user id" name="userId" autoComplete="off" defaultValue={v("userId")} error={e.userId} />
        <SelectField label="Currency" name="currency" defaultValue={v("currency", currencies[0])}
          options={currencies.map((c) => ({ value: c, label: c }))} />
        <SelectField label="Direction" name="direction" defaultValue={v("direction", "CREDIT")}
          options={[{ value: "CREDIT", label: "Credit (pay the investor more)" }, { value: "DEBIT", label: "Debit (recover an over-payment)" }]} />
        <TextField label="Amount" name="amount" inputMode="decimal" defaultValue={v("amount")} error={e.amount} />
        <div className="sm:col-span-2">
          <TextField label="Reason (shown on the statement)" name="reason" defaultValue={v("reason")} error={e.reason} />
        </div>
      </div>
      <SubmitButton variant="quiet" confirm="Post this adjustment? Ledger entries cannot be edited afterwards.">Post adjustment</SubmitButton>
    </form>
  );
}
