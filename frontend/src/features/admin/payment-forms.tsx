"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { CompanyBankAccount } from "@/types/order";
import type { FormState } from "@/validators/form-state";
import { currencyLabel, formatMoney } from "@/utils/money";
import { confirmTransferAction, recordRefundAction, rejectPaymentAction, saveCompanyBankAccountAction } from "./payment-actions";

/** @param defaultReference the investor's transaction id, cheque or receipt number, when they gave one */
export function ConfirmTransferForm({ paymentId, amountDue, currency, defaultReference }: {
  paymentId: string;
  amountDue: string;
  currency: string;
  defaultReference?: string;
}) {
  const [state, action] = useActionState<FormState, FormData>(confirmTransferAction.bind(null, paymentId), {});
  const e = state.fieldErrors ?? {};
  return (
    <form action={action} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField id={`ref-${paymentId}`} label="Bank reference" name="externalReference" autoComplete="off"
          defaultValue={state.values?.externalReference ?? defaultReference} error={e.externalReference} />
        <TextField id={`amt-${paymentId}`} label={currencyLabel("Amount received")} name="amountReceived" inputMode="decimal"
          defaultValue={state.values?.amountReceived} placeholder={amountDue} error={e.amountReceived} />
      </div>
      <SubmitButton variant="quiet" confirm={(form) => {
        const entered = String(new FormData(form).get("amountReceived") ?? "").trim();
        return `Record that ${formatMoney({ amount: entered || amountDue, currency })} arrived? This confirms the order.`;
      }}>
        Confirm payment received
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

/** Adds a company bank account, or edits {@code account} when given. */
export function CompanyBankAccountForm({ account }: { account?: CompanyBankAccount }) {
  const [state, action] = useActionState<FormState, FormData>(
    saveCompanyBankAccountAction.bind(null, account?.id ?? null), {});
  const e = state.fieldErrors ?? {};
  const v = (name: keyof CompanyBankAccount) => state.values?.[name] ?? (account?.[name] as string | null | undefined) ?? undefined;
  const key = account?.id ?? "new";
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField id={`${key}-name`} label="Account holder name" name="accountName" defaultValue={v("accountName")} error={e.accountName} />
        <TextField id={`${key}-bank`} label="Bank" name="bankName" placeholder="e.g. HDFC Bank" defaultValue={v("bankName")} error={e.bankName} />
        <TextField id={`${key}-number`} label="Account number" name="accountNumber" inputMode="numeric" autoComplete="off"
          defaultValue={v("accountNumber")} error={e.accountNumber} />
        <TextField id={`${key}-ifsc`} label="IFSC" name="ifscCode" placeholder="HDFC0001234" autoComplete="off"
          defaultValue={v("ifscCode")} error={e.ifscCode} />
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
