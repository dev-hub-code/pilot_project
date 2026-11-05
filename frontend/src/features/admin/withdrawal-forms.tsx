"use client";

import { useActionState, useState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/form-state";
import {
  resolveFailedAction, saveWithdrawalSettingsAction, setReferralModeAction,
  updateFinanceTaskAction, withdrawalDecisionAction,
} from "./withdrawal-actions";
import { TextArea } from "@/components/ui/text-area";
import type { WithdrawalSettings } from "@/types/withdrawal";



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

/** Every withdrawal setting; the backend works out charges and tax from these for each new request. */
export function WithdrawalSettingsForm({ settings }: { settings: WithdrawalSettings }) {
  const [state, action] = useActionState<FormState, FormData>(saveWithdrawalSettingsAction, {});
  const e = state.fieldErrors ?? {};
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <TextField label="Minimum withdrawal (₹)" name="minimumAmount" inputMode="decimal" defaultValue={String(settings.minimumAmount)} error={e.minimumAmount} />
        <TextField label="Flat charge per withdrawal (₹)" name="feeFlat" inputMode="decimal" defaultValue={String(settings.feeFlat)} error={e.feeFlat} />
        <TextField label="Charge (% of amount)" name="feePercent" inputMode="decimal" defaultValue={String(settings.feePercent)} error={e.feePercent} />
        <TextField label="Tax withheld, TDS (%)" name="taxPercent" inputMode="decimal" defaultValue={String(settings.taxPercent)} error={e.taxPercent} />
        <TextField label="Second approver above (₹)" name="dualApprovalThreshold" inputMode="decimal" defaultValue={String(settings.dualApprovalThreshold)} error={e.dualApprovalThreshold} />
        <TextField label="Processing time shown (working days)" name="processingDays" inputMode="numeric" defaultValue={settings.processingDays == null ? "" : String(settings.processingDays)} error={e.processingDays} />
      </div>
      <p className="text-xs text-muted">Charges and tax come out of the amount withdrawn and are fixed on each withdrawal when it is requested. Leave processing time empty to hide it.</p>
      <SubmitButton confirm="Save the withdrawal settings? New requests use them from now on.">Save settings</SubmitButton>
    </form>
  );
}

/** A decision that needs a reason (reject, hold, ask for information) or an internal note. */
export function WithdrawalDecisionForm({ withdrawalId, step, label, field, button, confirm, variant = "secondary" }: {
  withdrawalId: string;
  step: "reject" | "hold" | "request-info" | "notes";
  label: string;
  field: string;
  button: string;
  confirm?: string;
  variant?: "primary" | "secondary" | "quiet" | "danger";
}) {
  const [state, action] = useActionState<FormState, FormData>(withdrawalDecisionAction.bind(null, withdrawalId, step), {});
  return (
    <form action={action} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <TextArea label={field} name="reason" rows={2} maxLength={1000} required defaultValue={state.success ? "" : state.values?.reason}
        error={state.fieldErrors?.reason} aria-label={label} key={state.success ?? "draft"} />
      <SubmitButton variant={variant} confirm={confirm}>{button}</SubmitButton>
    </form>
  );
}
