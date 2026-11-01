"use client";

import { useActionState, useRef, useState, type ChangeEvent } from "react";
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
import type { IfscLookup } from "@/types/user";
import { addBankAccountAction, lookupIfscAction } from "./actions";

const IFSC = /^[A-Z]{4}0[A-Z0-9]{6}$/;
const IFSC_SHAPE = "Check this code: 4 letters, then the number 0 (not the letter O), then 6 letters or digits";

const PROOF_TYPES = [
  { value: "BANK_STATEMENT", label: "Bank statement" },
  { value: "CANCELLED_CHEQUE", label: "Cancelled cheque" },
];

/**
 * Adds an account. A verified account's details cannot be changed: the investor adds another account and
 * makes it primary once verified. The holder is always the investor's registered name (the backend sets
 * it), so it is shown read-only and not sent. So is the bank's name, filled in from the IFSC code: the backend
 * looks it up again when saving, and an account is still accepted when the code cannot be found.
 */
export function BankAccountForm({ holderName }: { holderName: string }) {
  // The IFSC as looked up: null until a whole code is typed.
  const [ifsc, setIfsc] = useState<IfscLookup | "checking" | null>(null);
  const latestCode = useRef("");
  // What is wrong with the code as typed; null shows the last submit's error, until the code is edited.
  const [typedError, setTypedError] = useState<string | null>(null);
  const [state, action] = useActionState<FormState, FormData>(async (previous, formData) => {
    const result = await addBankAccountAction(previous, formData);
    if (result.success) setIfsc(null); // The form clears after saving.
    setTypedError(null);
    return result;
  }, {});
  const e = state.fieldErrors ?? {};
  const v = (name: string, fallback = "") => state.values?.[name] ?? fallback;

  async function findBank(event: ChangeEvent<HTMLInputElement>) {
    upperCase(event);
    const code = event.currentTarget.value;
    latestCode.current = code;
    setTypedError(code.length === 11 && !IFSC.test(code) ? IFSC_SHAPE : "");
    if (!IFSC.test(code)) {
      setIfsc(null);
      return;
    }
    setIfsc("checking");
    const result = await lookupIfscAction(code);
    // Ignore an answer for a code that has since been changed.
    if (latestCode.current === code) setIfsc(result);
  }

  const looked = ifsc !== null && ifsc !== "checking" ? ifsc : null;

  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      {/* Laid out in rows: three fields across on wide screens, two on tablets, one on phones. */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <TextField label="Account holder (your registered name)" name="accountHolderName" value={holderName}
          readOnly />
        <SelectField label="Bank country" name="country" options={COUNTRY_OPTIONS} placeholder="Select…"
          defaultValue={v("country", "IN")} error={e.country} />
        <TextField label="IFSC code" name="routingCode" defaultValue={v("routingCode")} autoComplete="off"
          autoCapitalize="characters" spellCheck={false} onChange={findBank} pattern="[A-Z]{4}0[A-Z0-9]{6}"
          minLength={11} maxLength={11} placeholder="e.g. HDFC0001234"
          error={typedError === null ? e.routingCode : typedError || undefined} />
        <div className="space-y-1.5">
          <TextField label="Bank name" name="bankName" value={looked?.bank ?? ""} readOnly
            placeholder={ifsc === "checking" ? "Checking the IFSC code…" : "Filled in from the IFSC code"} />
          {looked && !looked.bank && (
            <p aria-live="polite" className="text-xs text-muted">
              We could not find the bank for this code. You can still add the account; our team checks it.
            </p>
          )}
        </div>
        <TextField label="Account number" name="accountNumber" autoComplete="off" inputMode="numeric"
          pattern="[0-9]{8,15}" minLength={8} maxLength={15} placeholder="8 to 15 digits" error={e.accountNumber} />
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
