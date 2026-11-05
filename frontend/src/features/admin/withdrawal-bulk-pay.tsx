"use client";

import { Landmark } from "lucide-react";
import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import type { FormState } from "@/validators/form-state";
import { initiateSelectedAction } from "./withdrawal-actions";

export const BULK_PAY_FORM = "bulk-pay";

/** The bar above the approved queue: pays the ticked rows (their checkboxes belong to this form). */
export function BulkPayBar({ count }: { count: number }) {
  const [state, action] = useActionState<FormState, FormData>(initiateSelectedAction, {});
  return (
    <form id={BULK_PAY_FORM} action={action} className="space-y-3 rounded-xl border border-gold/30 bg-gold/5 p-4">
      <FormFeedback state={state} />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="flex items-center gap-2 text-sm">
          <SelectAll />
          <Landmark className="size-4 text-gold-text" aria-hidden="true" />
          {count} approved withdrawal{count === 1 ? "" : "s"} waiting for payment. Tick the ones to pay now.
        </p>
        <SubmitButton pendingLabel="Initiating…"
          confirm={(form) => {
            const n = new FormData(form).getAll("withdrawalId").length;
            return `Initiate payment for ${n} withdrawal${n === 1 ? "" : "s"}? They go on a bank sheet and become finance tasks.`;
          }}>
          Initiate payment
        </SubmitButton>
      </div>
    </form>
  );
}

/** Ticks every row's checkbox of the bulk form. */
export function SelectAll() {
  return (
    <input type="checkbox" aria-label="Select all on this page" className="size-4"
      onChange={(e) => document.querySelectorAll<HTMLInputElement>(`input[form="${BULK_PAY_FORM}"]`)
        .forEach((box) => { box.checked = e.currentTarget.checked; })} />
  );
}
