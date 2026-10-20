"use client";

import { useActionState, useState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/form-state";
import {
  resolveFailedAction, setReferralModeAction, setWithdrawalMinimumAction, updateFinanceTaskAction,
} from "./withdrawal-actions";



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
          options={[{ value: "BANK_TRANSFER", label: "Into the bank account" }, { value: "OFFICE_CASH", label: "Cash" }]} />
      </div>
      <SubmitButton variant="secondary" confirm="Change how this investor's referral earnings are paid this year?">Save</SubmitButton>
    </form>
  );
}

/** The minimum withdrawal, in rupees: rows below it stay in the investor's wallets until a later run. */
export function WithdrawalMinimumForm({ minimumAmount }: { minimumAmount: number }) {
  const [state, action] = useActionState<FormState, FormData>(setWithdrawalMinimumAction, {});
  return (
    <form action={action} className="flex flex-wrap items-end gap-3" noValidate>
      <div className="w-full"><FormFeedback state={state} /></div>
      <div className="w-48">
        <TextField label="Minimum withdrawal (₹)" name="minimumAmount" inputMode="decimal" autoComplete="off"
          defaultValue={String(minimumAmount)} error={state.fieldErrors?.minimumAmount} />
      </div>
      <SubmitButton variant="secondary" confirm="Change the minimum withdrawal? It applies from the next run.">Save</SubmitButton>
    </form>
  );
}

/**
 * Finance's update of an open task: paid (with the bank reference, or the receipt for cash collected at the office),
 * or - behind a toggle - failed with the reason.
 */
export function FinanceTaskForm({ withdrawalId, reference, amount, cash }: {
  withdrawalId: string;
  reference: string;
  amount: string;
  cash: boolean;
}) {
  const [paidState, paid] = useActionState<FormState, FormData>(updateFinanceTaskAction.bind(null, withdrawalId, "PAID"), {});
  const [failedState, failed] = useActionState<FormState, FormData>(updateFinanceTaskAction.bind(null, withdrawalId, "FAILED"), {});
  return (
    <div className="min-w-64 space-y-2">
      <form action={paid} className="space-y-2" noValidate>
        <FormFeedback state={paidState} />
        <TextField label={cash ? "Receipt number (optional)" : "Bank reference / UTR (optional)"} name="reference"
          autoComplete="off" error={paidState.fieldErrors?.reference} />
        <SubmitButton confirm={cash ? `Record that the investor collected ${amount} in cash for ${reference}?`
          : `Record that ${amount} for ${reference} has been paid into the investor's account?`}>
          {cash ? "Collected" : "Paid"}
        </SubmitButton>
      </form>
      <details>
        <summary className="cursor-pointer text-xs text-muted">{cash ? "Not collected…" : "Bank could not pay it…"}</summary>
        <form action={failed} className="mt-2 space-y-2" noValidate>
          <FormFeedback state={failedState} />
          <TextField label="Why" name="reason" autoComplete="off" error={failedState.fieldErrors?.reason} />
          <SubmitButton variant="danger" confirm={`Mark ${reference} as ${cash ? "not collected" : "failed"}? The amount is held and a support ticket is opened for you.`}>
            {cash ? "Not collected" : "Failed"}
          </SubmitButton>
        </form>
      </details>
    </div>
  );
}
