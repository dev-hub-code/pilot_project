"use client";

import { useActionState, useRef, useState, type ChangeEvent } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { CompanyBankAccount } from "@/types/order";
import type { IfscLookup } from "@/types/user";
import type { FormState } from "@/validators/form-state";
import { currencyLabel, formatMoney } from "@/utils/money";
import { upperCase } from "@/utils/input";
import { confirmTransferAction, lookupCompanyIfscAction, recordRefundAction, rejectPaymentAction, saveCompanyBankAccountAction } from "./payment-actions";

/** @param defaultReference the investor's transaction id, cheque or receipt number, when they gave one */
/** Records a bank payment that arrived, or (with {@code officeCash}) cash received at the office with its receipt number. */
export function ConfirmTransferForm({ paymentId, amountDue, currency, defaultReference, officeCash = false }: {
  paymentId: string;
  amountDue: string;
  currency: string;
  defaultReference?: string;
  officeCash?: boolean;
}) {
  const [state, action] = useActionState<FormState, FormData>(confirmTransferAction.bind(null, paymentId), {});
  const e = state.fieldErrors ?? {};
  return (
    <form action={action} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField id={`ref-${paymentId}`} label={officeCash ? "Cash receipt number" : "Bank reference"} name="externalReference" autoComplete="off"
          defaultValue={state.values?.externalReference ?? defaultReference} error={e.externalReference} />
        <TextField id={`amt-${paymentId}`} label={currencyLabel("Amount received")} name="amountReceived" inputMode="decimal"
          defaultValue={state.values?.amountReceived} placeholder={amountDue} error={e.amountReceived} />
      </div>
      <SubmitButton variant="quiet" confirm={(form) => {
        const entered = String(new FormData(form).get("amountReceived") ?? "").trim();
        return officeCash
          ? `Record that ${formatMoney({ amount: entered || amountDue, currency })} was received in cash at the office? This confirms the order.`
          : `Record that ${formatMoney({ amount: entered || amountDue, currency })} arrived? This confirms the order.`;
      }}>
        {officeCash ? "Confirm cash received" : "Confirm payment received"}
      </SubmitButton>
    </form>
  );
}

/** The money never arrived (or the cheque bounced): the investor is told why and can pay again. */
export function RejectPaymentForm({ paymentId }: { paymentId: string }) {
  const [state, action] = useActionState<FormState, FormData>(rejectPaymentAction.bind(null, paymentId), {});
  return (
    <form action={action} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <TextField id={`reject-${paymentId}`} label="Reason shown to the investor" name="reason"
        placeholder="e.g. Cheque returned unpaid" defaultValue={state.values?.reason} error={state.fieldErrors?.reason} />
      <SubmitButton variant="danger" confirm="Reject this payment? The investor is told the reason and can pay again.">
        Reject payment
      </SubmitButton>
    </form>
  );
}

const IFSC = /^[A-Z]{4}0[A-Z0-9]{6}$/;
const IFSC_SHAPE = "Check this code: 4 letters, then the number 0 (not the letter O), then 6 letters or digits";

/** Adds a company bank account, or edits {@code account} when given. */
export function CompanyBankAccountForm({ account }: { account?: CompanyBankAccount }) {
  const [bank, setBank] = useState(account?.bankName ?? "");
  // What the directory said about the code as typed; null until a whole code is typed.
  const [ifsc, setIfsc] = useState<IfscLookup | "checking" | null>(null);
  const latestCode = useRef("");
  // The name the directory filled in: it goes when the code changes, unlike a name typed by hand.
  const filledIn = useRef("");
  // What is wrong with the code as typed; null shows the last submit's error, until the code is edited.
  const [typedError, setTypedError] = useState<string | null>(null);
  const [state, action] = useActionState<FormState, FormData>(async (previous, formData) => {
    const result = await saveCompanyBankAccountAction(account?.id ?? null, previous, formData);
    if (result.success && !account) { // The form clears after adding.
      setBank("");
      setIfsc(null);
    }
    setTypedError(null);
    return result;
  }, {});
  const e = state.fieldErrors ?? {};
  const v = (name: keyof CompanyBankAccount) => state.values?.[name] ?? (account?.[name] as string | null | undefined) ?? undefined;
  const key = account?.id ?? "new";

  async function findBank(event: ChangeEvent<HTMLInputElement>) {
    upperCase(event);
    const code = event.currentTarget.value;
    latestCode.current = code;
    const stale = filledIn.current;
    filledIn.current = "";
    if (stale) setBank((current) => (current === stale ? "" : current));
    setTypedError(code.length === 11 && !IFSC.test(code) ? IFSC_SHAPE : "");
    if (!IFSC.test(code)) {
      setIfsc(null);
      return;
    }
    setIfsc("checking");
    const result = await lookupCompanyIfscAction(code);
    // Ignore an answer for a code that has since been changed.
    if (latestCode.current !== code) return;
    setIfsc(result);
    if (result.bank) {
      filledIn.current = result.bank;
      setBank(result.bank);
    }
  }

  const missed = ifsc !== null && ifsc !== "checking" && ifsc.status !== "FOUND" ? ifsc.status : null;
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField id={`${key}-ifsc`} label="IFSC" name="ifscCode" placeholder="HDFC0001234" autoComplete="off"
          autoCapitalize="characters" spellCheck={false} onChange={findBank} maxLength={11}
          defaultValue={v("ifscCode")} error={typedError || (typedError === null ? e.ifscCode : undefined)} />
        <div className="space-y-1">
          {/* Filled in from the code, but still editable: the directory can be down or not know a new branch. */}
          <TextField id={`${key}-bank`} label="Bank name" name="bankName" value={bank} error={e.bankName}
            placeholder={ifsc === "checking" ? "Checking the IFSC code…" : "Filled in from the IFSC code"}
            onChange={(event) => setBank(event.target.value)} />
          {missed && (
            <p className="text-xs text-muted">
              {missed === "NOT_FOUND" ? "We couldn't find this IFSC code. Check it, or enter the bank yourself."
                : "We couldn't look up this code just now. Enter the bank yourself."}
            </p>
          )}
        </div>
        <TextField id={`${key}-number`} label="Account number" name="accountNumber" inputMode="numeric" autoComplete="off"
          defaultValue={v("accountNumber")} error={e.accountNumber} />
        <TextField id={`${key}-name`} label="Account holder name" name="accountName" defaultValue={v("accountName")} error={e.accountName} />
        <TextField id={`${key}-branch`} label="Branch (optional)" name="branch" defaultValue={v("branch")} error={e.branch} />
        <TextField id={`${key}-upi`} label="UPI ID (optional)" name="upiId" placeholder="name@bank" autoComplete="off"
          defaultValue={v("upiId")} error={e.upiId} />
      </div>
      <SubmitButton variant={account ? "quiet" : "primary"}>{account ? "Save changes" : "Add account"}</SubmitButton>
    </form>
  );
}

export function RefundForm({ paymentId }: { paymentId: string }) {
  const [state, action] = useActionState<FormState, FormData>(recordRefundAction.bind(null, paymentId), {});
  const e = state.fieldErrors ?? {};
  return (
    <form action={action} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField id={`rref-${paymentId}`} label="Refund transfer reference" name="reference" autoComplete="off"
          defaultValue={state.values?.reference} error={e.reference} />
        <TextField id={`rreason-${paymentId}`} label="Reason" name="reason" defaultValue={state.values?.reason} error={e.reason} />
      </div>
      <SubmitButton variant="quiet" confirm="Record that this money was returned to the investor?">Record refund</SubmitButton>
    </form>
  );
}
