"use client";

import { useActionState } from "react";
import { FileField } from "@/components/ui/file-field";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import { COUNTRY_OPTIONS } from "@/lib/countries";
import { CURRENCY } from "@/lib/currency";
import type { FormState } from "@/validators/form-state";
import { ACCEPTED_FILE_TYPES } from "@/validators/profile";
import { upperCase } from "@/utils/input";
import { addBankAccountAction } from "./actions";

const PROOF_TYPES = [
  { value: "BANK_STATEMENT", label: "Bank statement" },
  { value: "CANCELLED_CHEQUE", label: "Cancelled cheque" },
];

/**
 * Adds an account. A verified account's details cannot be changed: the investor adds another account and
 * makes it primary once verified. The holder is always the investor's registered name (the backend sets
 * it), so it is shown read-only and not sent.
 */
export function BankAccountForm({ holderName }: { holderName: string }) {
  const [state, action] = useActionState<FormState, FormData>(addBankAccountAction, {});
  const e = state.fieldErrors ?? {};
  const v = (name: string, fallback = "") => state.values?.[name] ?? fallback;

  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      {/* Laid out in rows: three fields across on wide screens, two on tablets, one on phones. */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <TextField label="Account holder (your registered name)" name="accountHolderName" value={holderName}
          readOnly />
        <TextField label="Bank name" name="bankName" defaultValue={v("bankName")} error={e.bankName} />
        <SelectField label="Bank country" name="country" options={COUNTRY_OPTIONS} placeholder="Select…"
          defaultValue={v("country", "IN")} error={e.country} />
        <TextField label="Account number" name="accountNumber" autoComplete="off" inputMode="numeric"
          pattern="[0-9]{8,15}" minLength={8} maxLength={15} placeholder="8 to 15 digits" error={e.accountNumber} />
        <TextField label="IFSC code" name="routingCode" defaultValue={v("routingCode")} autoComplete="off"
          autoCapitalize="characters" spellCheck={false} onChange={upperCase} pattern="[A-Z]{4}0[A-Z0-9]{6}"
          minLength={11} maxLength={11} placeholder="e.g. HDFC0001234" error={e.routingCode} />
        <SelectField label="Proof of account" name="proofType" placeholder="Select…" options={PROOF_TYPES}
          defaultValue={v("proofType")} error={e.proofType} />
        <div className="sm:col-span-2 lg:col-span-3">
          <FileField label="Bank statement or cancelled cheque" name="proof" accept={ACCEPTED_FILE_TYPES} required
            hint="Showing your name, the account number and the IFSC. PDF, JPEG or PNG, up to 5 MB (re-select after an error)"
            error={e.proof} />
        </div>
      </div>
      <input type="hidden" name="currency" value={CURRENCY} />
      <p className="text-xs text-muted">
        The account must be in your own name. Verified accounts cannot be edited: to have payouts sent elsewhere, add
        the new account and make it primary once it is verified. Numbers and documents are stored encrypted.
      </p>
      <SubmitButton pendingLabel="Sending…">Add bank account</SubmitButton>
    </form>
  );
}
