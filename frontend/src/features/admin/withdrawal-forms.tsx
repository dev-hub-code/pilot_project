"use client";

import { useActionState, useState } from "react";
import { FileField } from "@/components/ui/file-field";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import { ordinal } from "@/utils/format";
import type { FormState } from "@/validators/form-state";
import {
  handOverCashAction, reconcileBatchAction, resolveFailedAction, setReferralModeAction, settleBatchAction,
  updateWithdrawalDaysAction,
} from "./withdrawal-actions";

const DAYS = Array.from({ length: 27 }, (_, i) => ({ value: String(i + 2), label: ordinal(i + 2) }));

/** Finance hands the cash of a withdrawal made at the office to the investor, against a signed receipt. */
export function HandOverCashForm({ withdrawalId, amount }: { withdrawalId: string; amount: string }) {
  const [state, action] = useActionState<FormState, FormData>(handOverCashAction.bind(null, withdrawalId), {});
  return (
    <form action={action} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <TextField label="Cash receipt number" name="receiptNumber" autoComplete="off" error={state.fieldErrors?.receiptNumber} />
      <SubmitButton confirm={`Record that ${amount} was handed over in cash? This completes the withdrawal.`}>
        Record cash handed over
      </SubmitButton>
    </form>
  );
}

export function SettleBatchForm({ batchId, outstanding }: { batchId: string; outstanding: number }) {
  const [state, action] = useActionState<FormState, FormData>(settleBatchAction.bind(null, batchId), {});
  return (
    <form action={action} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <TextField label="Bank statement reference (optional)" name="payoutReference" autoComplete="off"
        error={state.fieldErrors?.payoutReference} />
      <SubmitButton confirm={`Mark all ${outstanding} outstanding payment(s) as paid?`}>Mark remaining as paid</SubmitButton>
    </form>
  );
}

export function WithdrawalDaysForm({ rentDay, referralDay }: { rentDay: number; referralDay: number }) {
  const [state, action] = useActionState<FormState, FormData>(updateWithdrawalDaysAction, {});
  return (
    <form action={action} className="space-y-4">
      <FormFeedback state={state} />
      <div className="flex flex-wrap items-end gap-4">
        <div className="w-full max-w-48">
          <SelectField label="Rent withdrawal day" name="rentWithdrawalDay" options={DAYS}
            defaultValue={String(rentDay)} error={state.fieldErrors?.rentWithdrawalDay} />
        </div>
        <div className="w-full max-w-48">
          <SelectField label="Referral withdrawal day" name="referralWithdrawalDay" options={DAYS}
            defaultValue={String(referralDay)} error={state.fieldErrors?.referralWithdrawalDay} />
        </div>
        <SubmitButton>Save days</SubmitButton>
      </div>
    </form>
  );
}

/** How a failed bank payment was settled: paid by hand, or returned to the investor's balance. */
export function ResolveFailedForm({ withdrawalId, amount }: { withdrawalId: string; amount: string }) {
  const [state, action] = useActionState<FormState, FormData>(resolveFailedAction.bind(null, withdrawalId), {});
  const [outcome, setOutcome] = useState<"PAID" | "RETURNED">("PAID");
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <fieldset className="space-y-2 text-sm">
        <legend className="sr-only">Outcome</legend>
        <label className="flex items-start gap-2">
          <input type="radio" name="outcome" value="PAID" checked={outcome === "PAID"} onChange={() => setOutcome("PAID")} className="mt-1" />
          <span>Paid by hand <span className="block text-xs text-muted">The bank payment has now been made.</span></span>
        </label>
        <label className="flex items-start gap-2">
          <input type="radio" name="outcome" value="RETURNED" checked={outcome === "RETURNED"} onChange={() => setOutcome("RETURNED")} className="mt-1" />
          <span>Return to balance <span className="block text-xs text-muted">It is paid with next month&apos;s withdrawal.</span></span>
        </label>
      </fieldset>
      <TextField label={outcome === "PAID" ? "Bank reference (UTR)" : "Note to the investor (optional)"} name="note"
        autoComplete="off" error={state.fieldErrors?.note} />
      <SubmitButton confirm={outcome === "PAID"
        ? `Record that ${amount} has been paid? The ticket is resolved.`
        : `Return ${amount} to the investor's balance? The ticket is resolved.`}>
        Resolve
      </SubmitButton>
    </form>
  );
}

/** Changes an investor's referral payout mode for this year (they choose once a year themselves). */
export function ReferralModeAdminForm({ userId, mode }: { userId: string; mode: "BANK_TRANSFER" | "OFFICE_CASH" }) {
  const [state, action] = useActionState<FormState, FormData>(setReferralModeAction.bind(null, userId), {});
  return (
    <form action={action} className="flex flex-wrap items-end gap-3" noValidate>
      <div className="w-full"><FormFeedback state={state} /></div>
      <div className="min-w-56">
        <SelectField label="Referral earnings paid" name="mode" defaultValue={mode}
          options={[{ value: "BANK_TRANSFER", label: "Into the bank account" }, { value: "OFFICE_CASH", label: "In cash at the office" }]} />
      </div>
      <SubmitButton variant="secondary" confirm="Change how this investor's referral earnings are paid this year?">Save</SubmitButton>
    </form>
  );
}

/** Upload the batch's Excel file back with the Status column filled in after paying it. */
export function ReconcileUploadForm({ batchId, reference }: { batchId: string; reference: string }) {
  const [state, action] = useActionState<FormState, FormData>(reconcileBatchAction.bind(null, batchId), {});
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <FileField label="Completed withdrawal file" name="file"
        accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" required error={state.fieldErrors?.file}
        hint={`The ${reference} Excel file with Status set to PAID or FAILED per row (blank rows stay outstanding). Remarks: the bank reference, or why it failed.`} />
      <SubmitButton confirm="Update every withdrawal in the file to the status it gives? Each failed one is held and gets a support ticket.">
        Upload and update
      </SubmitButton>
    </form>
  );
}
